package com.eightfold.veritas;

import com.eightfold.veritas.Pipeline.RunReport;
import com.eightfold.veritas.Pipeline.SourceLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Veritas CLI -- the thin input/output surface.
 *
 * <p>Point it at a manifest (which lists the candidate's sources) and,
 * optionally, a runtime output config. It prints (or writes) schema-valid JSON.
 *
 * <pre>
 *   java -cp bin com.eightfold.veritas.Cli --manifest samples/inputs/manifest.json
 *   java -cp bin com.eightfold.veritas.Cli --manifest samples/inputs/manifest.json \
 *        --config samples/configs/compact.json --out out.json --explain
 * </pre>
 */
public final class Cli {

    public static void main(String[] args) throws Exception {
        String manifest = null, config = null, out = null;
        boolean explain = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manifest" -> manifest = args[++i];
                case "--config" -> config = args[++i];
                case "--out" -> out = args[++i];
                case "--explain" -> explain = true;
                case "-h", "--help" -> { usage(); return; }
                default -> { System.err.println("unknown arg: " + args[i]); usage(); System.exit(2); }
            }
        }
        if (manifest == null) { usage(); System.exit(2); return; }

        Map<String, Object> cfg = null;
        if (config != null)
            cfg = Json.parseObject(new String(Files.readAllBytes(Paths.get(config)), StandardCharsets.UTF_8));

        RunReport report;
        try {
            report = Pipeline.run(manifest, cfg);
        } catch (Projection.ProjectionException e) {
            System.err.println("projection/validation error: " + e.getMessage());
            System.exit(2); return;
        } catch (java.io.IOException e) {
            System.err.println("input error: " + e.getMessage());
            System.exit(2); return;
        }

        if (explain) {
            System.err.println("── source extraction log ────────");
            for (SourceLog s : report.sourceLog) {
                String status = s.ok() ? String.format("OK  %2d claims", s.claims())
                                       : "SKIP (" + s.error() + ")";
                System.err.printf("  %-16s %s%n", s.type(), status);
            }
            System.err.println("  ledger: " + report.ledgerSize + " claims");
            System.err.println(report.validationErrors.isEmpty()
                    ? "  canonical schema: VALID"
                    : "  VALIDATION ERRORS: " + report.validationErrors);
            System.err.println("──────────────────");
        }

        String text = Json.write(report.output);
        if (out != null) {
            Files.write(Paths.get(out), (text + "\n").getBytes(StandardCharsets.UTF_8));
            System.err.println("wrote " + out);
        } else {
            System.out.println(text);
        }
        System.exit(report.validationErrors.isEmpty() ? 0 : 1);
    }

    private static void usage() {
        System.err.println("""
            Multi-source candidate data transformer (Veritas).

            Usage:
              java -cp bin com.eightfold.veritas.Cli --manifest <path> [--config <path>] [--out <path>] [--explain]

            Options:
              --manifest  path to the sources manifest JSON (required)
              --config    runtime output config JSON (the projection layer)
              --out       write the result here instead of stdout
              --explain   print the per-source extraction log to stderr
            """);
    }

    private Cli() {}
}
