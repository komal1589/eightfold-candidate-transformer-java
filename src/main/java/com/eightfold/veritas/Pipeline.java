package com.eightfold.veritas;

import com.eightfold.veritas.adapters.Adapter;
import com.eightfold.veritas.adapters.Sources;
import com.eightfold.veritas.adapters.Trust;
import com.eightfold.veritas.model.CanonicalProfile;
import com.eightfold.veritas.model.Claim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * End-to-end orchestration:
 *
 * <pre>detect -> extract -> normalize -> merge -> confidence -> project -> validate</pre>
 *
 * {@code detect} reads a manifest naming each source; {@code extract} runs the
 * matching adapter inside a sandbox so a malformed or exploding source
 * contributes zero claims instead of taking down the run (robustness).
 */
public final class Pipeline {

    private Pipeline() {}

    /** Per-source extraction outcome, surfaced to the CLI's {@code --explain}. */
    public record SourceLog(String type, String path, boolean ok, int claims, String error) {}

    public static final class RunReport {
        public final String candidateId;
        public final Map<String, Object> canonical;
        public final Map<String, Object> output;
        public final List<String> validationErrors;
        public final List<SourceLog> sourceLog;
        public final int ledgerSize;

        RunReport(String candidateId, Map<String, Object> canonical, Map<String, Object> output,
                  List<String> validationErrors, List<SourceLog> sourceLog, int ledgerSize) {
            this.candidateId = candidateId;
            this.canonical = canonical;
            this.output = output;
            this.validationErrors = validationErrors;
            this.sourceLog = sourceLog;
            this.ledgerSize = ledgerSize;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> detect(String manifestPath) throws IOException {
        Path mp = Paths.get(manifestPath).toAbsolutePath();
        Map<String, Object> manifest = Json.parseObject(read(mp));
        if (manifest == null) throw new IOException("manifest is not a JSON object: " + manifestPath);
        manifest.put("_base", mp.getParent().toString());
        manifest.putIfAbsent("candidate_id", "unknown");
        manifest.putIfAbsent("sources", new ArrayList<>());
        return manifest;
    }

    @SuppressWarnings("unchecked")
    public static ExtractResult extract(Map<String, Object> manifest) {
        List<Claim> ledger = new ArrayList<>();
        List<SourceLog> log = new ArrayList<>();
        String base = (String) manifest.get("_base");
        int order = 0;
        for (Object srcObj : (List<Object>) manifest.get("sources")) {
            Map<String, Object> src = (Map<String, Object>) srcObj;
            String stype = (String) src.get("type");
            String path = (String) src.get("path");
            Adapter adapter = Sources.REGISTRY.get(stype);
            if (adapter == null) {
                log.add(new SourceLog(stype, path, false, 0, "unknown source type '" + stype + "'"));
                continue;
            }
            String raw;
            try {
                raw = read(Paths.get(base, path));
            } catch (Exception e) {                       // missing/unreadable file
                log.add(new SourceLog(stype, path, false, 0, "read failed: " + e.getMessage()));
                continue;
            }
            List<Adapter.ClaimDraft> drafts;
            try {
                drafts = adapter.extract(raw);            // sandbox: garbage -> zero claims
            } catch (Exception e) {
                log.add(new SourceLog(stype, path, false, 0, "extract failed: " + e.getMessage()));
                continue;
            }
            double baseTrust = numberOf(src.get("trust"), Trust.DEFAULT.getOrDefault(stype, 0.5));
            String observedAt = String.valueOf(src.getOrDefault("timestamp", "1970-01-01"));
            int n = 0;
            for (Adapter.ClaimDraft d : drafts) {
                ledger.add(new Claim(d.fieldPath(), d.raw(), d.value(), d.method(), stype,
                        Trust.forField(stype, baseTrust, d.fieldPath()), observedAt, order));
                n++;
            }
            order++;
            log.add(new SourceLog(stype, path, true, n, null));
        }
        return new ExtractResult(ledger, log);
    }

    public record ExtractResult(List<Claim> ledger, List<SourceLog> log) {}

    public static RunReport run(String manifestPath, Map<String, Object> config) throws IOException {
        Map<String, Object> manifest = detect(manifestPath);
        ExtractResult ex = extract(manifest);
        CanonicalProfile profile = Merge.merge((String) manifest.get("candidate_id"), ex.ledger());

        boolean includeConf = config == null || boolOf(config.get("include_confidence"), true);
        boolean includeProv = config == null || boolOf(config.get("include_provenance"), true);
        Map<String, Object> canonical = Canonical.toMap(profile, includeConf, includeProv);
        List<String> errors = Validate.canonical(canonical);

        Map<String, Object> output = config == null ? canonical : Projection.project(canonical, config);
        return new RunReport((String) manifest.get("candidate_id"), canonical, output,
                errors, ex.log(), ex.ledger().size());
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static double numberOf(Object v, double dflt) {
        return v instanceof Number n ? n.doubleValue() : dflt;
    }

    private static boolean boolOf(Object v, boolean dflt) {
        return v instanceof Boolean b ? b : dflt;
    }
}
