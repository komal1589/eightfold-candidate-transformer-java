package com.eightfold.veritas;

import com.eightfold.veritas.model.CanonicalProfile;
import com.eightfold.veritas.model.Claim;
import com.eightfold.veritas.model.FieldResolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Conflict resolution + confidence scoring -- the reduction over the ledger.
 *
 * <p>Winner policy for a <i>scalar</i> field (deterministic, in order):
 * <ol>
 *   <li>highest effective trust;</li>
 *   <li>tie -> most recent {@code observedAt};</li>
 *   <li>tie -> earliest manifest position ({@code order});</li>
 *   <li>tie -> lexically smallest normalized value.</li>
 * </ol>
 * Steps 3-4 guarantee identical output on every run regardless of map ordering.
 *
 * <p>Confidence combines authority (winner trust), agreement (corroboration),
 * conflict (penalty when sources disagree) and a small penalty if the winning
 * value never normalized cleanly.
 */
public final class Merge {

    private Merge() {}

    private static final List<String> SCALAR_FIELDS = List.of(
            "full_name", "headline", "years_experience",
            "location.city", "location.region", "location.country",
            "links.linkedin", "links.github", "links.portfolio");
    private static final List<String> MULTI_FIELDS = List.of("emails", "phones", "links.other");

    public static CanonicalProfile merge(String candidateId, List<Claim> ledger) {
        Map<String, List<Claim>> byField = new LinkedHashMap<>();
        for (Claim c : ledger) byField.computeIfAbsent(c.fieldPath(), k -> new ArrayList<>()).add(c);

        CanonicalProfile prof = new CanonicalProfile(candidateId);
        prof.ledger = ledger;
        List<Map<String, Object>> provenance = new ArrayList<>();

        for (String f : SCALAR_FIELDS) {
            if (!byField.containsKey(f)) continue;
            FieldResolution fr = resolveScalar(byField.get(f));
            prof.fields.put(f, fr);
            if (fr.value != null) provenance.add(prov(f, fr.winningSource, fr.method));
        }

        for (String f : MULTI_FIELDS) {
            if (!byField.containsKey(f)) continue;
            DedupResult dr = dedupMulti(byField.get(f));
            switch (f) {
                case "emails" -> prof.emails = dr.values;
                case "phones" -> prof.phones = dr.values;
                case "links.other" -> prof.linksOther = dr.values;
            }
            for (String[] p : dr.provenance) provenance.add(prov(f, p[0], p[1]));
        }

        if (byField.containsKey("skills")) {
            SkillResult sr = mergeSkills(byField.get("skills"));
            prof.skills = sr.skills;
            provenance.addAll(sr.provenance);
        }
        if (byField.containsKey("experience"))
            prof.experience = mergeRecords(byField.get("experience"),
                    new String[]{"company", "title"}, "experience", provenance);
        if (byField.containsKey("education"))
            prof.education = mergeRecords(byField.get("education"),
                    new String[]{"institution", "degree"}, "education", provenance);

        prof.provenance = provenance;
        prof.overallConfidence = overall(prof);
        return prof;
    }

    // --------------------------------------------------------------- scalar
    private static final Comparator<Claim> SCALAR_ORDER = Comparator
            .comparingDouble((Claim c) -> -c.trust())
            .thenComparing(c -> negDate(c.observedAt()))
            .thenComparingInt(Claim::order)
            .thenComparing(c -> c.value() == null ? "￿" : String.valueOf(c.value()));

    static FieldResolution resolveScalar(List<Claim> claims) {
        List<Claim> usable = claims.stream().filter(c -> c.value() != null).toList();
        if (usable.isEmpty())
            return new FieldResolution(null, 0.0, null, null, claims, 0, 0);
        Claim winner = usable.stream().min(SCALAR_ORDER).orElseThrow();
        Set<String> agree = new LinkedHashSet<>(), conflict = new LinkedHashSet<>();
        for (Claim c : usable) (c.value().equals(winner.value()) ? agree : conflict).add(c.sourceId());
        double conf = confidence(winner, agree.size(), conflict.size());
        return new FieldResolution(winner.value(), conf, winner.sourceId(), winner.method(),
                claims, agree.size(), conflict.size());
    }

    private static double confidence(Claim winner, int agreed, int conflicted) {
        double authority = winner.trust();
        double agreementBonus = 0.0;
        for (int i = 0; i < Math.max(agreed - 1, 0); i++) agreementBonus += 0.12 * Math.pow(0.6, i);
        double conflictPenalty = 0.08 * conflicted;
        double normalizePenalty = winner.value() != null ? 0.0 : 0.15;
        double score = authority + agreementBonus - conflictPenalty - normalizePenalty;
        return round3(Math.max(0.0, Math.min(1.0, score)));
    }

    /** Invert each char so larger (more recent) dates sort first under natural order. */
    private static String negDate(String d) {
        if (d == null) d = "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length(); i++) b.append((char) (255 - d.charAt(i)));
        return b.toString();
    }

    // ---------------------------------------------------------------- multi
    private record DedupResult(List<String> values, List<String[]> provenance) {}

    private static DedupResult dedupMulti(List<Claim> claims) {
        List<Claim> sorted = new ArrayList<>(claims);
        sorted.sort(Comparator.comparingDouble((Claim c) -> -c.trust()).thenComparingInt(Claim::order));
        Map<Object, Set<String>> seen = new LinkedHashMap<>();
        List<String> values = new ArrayList<>();
        List<String[]> prov = new ArrayList<>();
        for (Claim c : sorted) {
            if (c.value() == null) continue;
            Object key = c.value() instanceof String s ? s.toLowerCase() : c.value();
            if (seen.containsKey(key)) { seen.get(key).add(c.sourceId()); continue; }
            Set<String> src = new LinkedHashSet<>(); src.add(c.sourceId());
            seen.put(key, src);
            values.add(String.valueOf(c.value()));
            prov.add(new String[]{c.sourceId(), c.method()});
        }
        return new DedupResult(values, prov);
    }

    // --------------------------------------------------------------- skills
    private record SkillResult(List<Map<String, Object>> skills, List<Map<String, Object>> provenance) {}

    private static SkillResult mergeSkills(List<Claim> claims) {
        // Group case-insensitively: free text says "distributed systems", ATS says
        // "Distributed Systems" -- fold them; highest-trust source picks the casing.
        Map<String, List<Claim>> groups = new TreeMap<>();
        for (Claim c : claims)
            if (c.value() != null)
                groups.computeIfAbsent(String.valueOf(c.value()).toLowerCase(), k -> new ArrayList<>()).add(c);
        List<Map<String, Object>> skills = new ArrayList<>();
        List<Map<String, Object>> prov = new ArrayList<>();
        for (Map.Entry<String, List<Claim>> e : groups.entrySet()) {
            List<Claim> cs = e.getValue();
            String display = cs.stream()
                    .min(Comparator.comparingDouble((Claim c) -> -c.trust())
                            .thenComparing(c -> String.valueOf(c.value())))
                    .map(c -> String.valueOf(c.value())).orElseThrow();
            Set<String> sources = new java.util.TreeSet<>();
            double best = 0;
            for (Claim c : cs) { sources.add(c.sourceId()); best = Math.max(best, c.trust()); }
            double conf = best;
            for (int i = 0; i < sources.size() - 1; i++) conf += 0.1 * Math.pow(0.6, i);
            Map<String, Object> sk = new LinkedHashMap<>();
            sk.put("name", display);
            sk.put("confidence", round3(Math.min(1.0, conf)));
            sk.put("sources", new ArrayList<>(sources));
            skills.add(sk);
            prov.add(prov("skills:" + display, sources.iterator().next(), cs.get(0).method()));
        }
        return new SkillResult(skills, prov);
    }

    // ----------------------------------------------------- experience/education
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mergeRecords(
            List<Claim> claims, String[] keys, String field, List<Map<String, Object>> provenance) {
        List<Claim> sorted = new ArrayList<>(claims);
        sorted.sort(Comparator.comparingDouble((Claim c) -> -c.trust()).thenComparingInt(Claim::order));
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Claim c : sorted) {
            if (!(c.value() instanceof Map)) continue;
            Map<String, Object> v = (Map<String, Object>) c.value();
            StringBuilder kb = new StringBuilder();
            for (String k : keys) {
                Object kv = v.get(k);
                kb.append(kv instanceof String s ? s.toLowerCase() : String.valueOf(kv)).append('\u0001');
            }
            String key = kb.toString();
            if (merged.containsKey(key)) {
                Map<String, Object> tgt = merged.get(key);
                for (Map.Entry<String, Object> en : v.entrySet())   // backfill blanks only
                    if ((tgt.get(en.getKey()) == null || "".equals(tgt.get(en.getKey())))
                            && en.getValue() != null && !"".equals(en.getValue()))
                        tgt.put(en.getKey(), en.getValue());
            } else {
                merged.put(key, new LinkedHashMap<>(v));
                provenance.add(prov(field, c.sourceId(), c.method()));
            }
        }
        return new ArrayList<>(merged.values());
    }

    // ------------------------------------------------------------- overall
    private static double overall(CanonicalProfile prof) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("full_name", 3.0); weights.put("emails", 2.5); weights.put("phones", 1.5);
        weights.put("headline", 1.0); weights.put("skills", 1.5); weights.put("experience", 1.5);
        weights.put("education", 1.0); weights.put("location.country", 0.8);
        weights.put("years_experience", 0.8);
        double totalW = 0, score = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            Double c = fieldConfidence(prof, e.getKey());
            if (c != null) { score += e.getValue() * c; totalW += e.getValue(); }
        }
        return totalW == 0 ? 0.0 : round3(score / totalW);
    }

    private static Double fieldConfidence(CanonicalProfile prof, String f) {
        if (prof.fields.containsKey(f) && prof.fields.get(f).value != null)
            return prof.fields.get(f).confidence;
        switch (f) {
            case "emails": return prof.emails.isEmpty() ? null : 0.9;
            case "phones": return prof.phones.isEmpty() ? null : 0.7;
            case "skills":
                if (prof.skills.isEmpty()) return null;
                double s = 0; for (Map<String, Object> sk : prof.skills) s += (double) sk.get("confidence");
                return round3(s / prof.skills.size());
            case "experience": return prof.experience.isEmpty() ? null : 0.8;
            case "education": return prof.education.isEmpty() ? null : 0.8;
            default: return null;
        }
    }

    private static Map<String, Object> prov(String field, String source, String method) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", field); m.put("source", source); m.put("method", method);
        return m;
    }

    static double round3(double d) { return Math.round(d * 1000.0) / 1000.0; }
}
