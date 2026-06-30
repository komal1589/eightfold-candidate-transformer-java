package com.eightfold.veritas.adapters;

import java.util.HashMap;
import java.util.Map;

/**
 * Source trust tiers that anchor conflict resolution -- the "winner policy" the
 * design doc defends.
 *
 * <p>Structured, owner-curated sources outrank scraped / free-text ones. A
 * source entry in the manifest may override its own base trust; this table is
 * only the fallback. Per-field overrides let a source be authoritative for some
 * fields and weak for others (GitHub is gold for skills, useless for phones).
 */
public final class Trust {

    private Trust() {}

    public static final Map<String, Double> DEFAULT = new HashMap<>();
    private static final Map<String, Map<String, Double>> FIELD_OVERRIDES = new HashMap<>();

    static {
        DEFAULT.put("recruiter_csv", 0.90);
        DEFAULT.put("ats_json", 0.85);
        DEFAULT.put("linkedin", 0.75);
        DEFAULT.put("github_api", 0.60);
        DEFAULT.put("resume", 0.55);
        DEFAULT.put("recruiter_notes", 0.40);

        FIELD_OVERRIDES.put("github_api", Map.of("skills", 0.95, "links.github", 0.99, "phones", 0.0));
        FIELD_OVERRIDES.put("linkedin", Map.of("headline", 0.90, "links.linkedin", 0.99));
        FIELD_OVERRIDES.put("resume", Map.of("experience", 0.80, "education", 0.85, "skills", 0.70));
        FIELD_OVERRIDES.put("recruiter_notes", Map.of("phones", 0.55, "skills", 0.45));
    }

    /** Effective trust of {@code sourceId} for a specific {@code fieldPath}. */
    public static double forField(String sourceId, double baseTrust, String fieldPath) {
        Map<String, Double> over = FIELD_OVERRIDES.get(sourceId);
        if (over != null) {
            if (over.containsKey(fieldPath)) return over.get(fieldPath);
            String top = fieldPath.split("\\.")[0];
            if (over.containsKey(top)) return over.get(top);
        }
        return baseTrust;
    }
}
