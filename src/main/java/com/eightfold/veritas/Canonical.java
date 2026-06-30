package com.eightfold.veritas;

import com.eightfold.veritas.model.CanonicalProfile;
import com.eightfold.veritas.model.FieldResolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render a {@link CanonicalProfile} into the assignment's default schema map.
 *
 * <p>This is the <b>canonical record boundary</b>. Everything downstream
 * (projection, validation, CLI) consumes this plain map -- never the engine
 * internals -- which keeps "internal canonical record" and "projection layer"
 * cleanly split.
 */
public final class Canonical {

    private Canonical() {}

    public static Map<String, Object> toMap(CanonicalProfile p,
                                            boolean includeConfidence, boolean includeProvenance) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidate_id", p.candidateId);
        out.put("full_name", p.scalar("full_name"));
        out.put("emails", new ArrayList<>(p.emails));
        out.put("phones", new ArrayList<>(p.phones));

        Map<String, Object> loc = new LinkedHashMap<>();
        loc.put("city", p.scalar("location.city"));
        loc.put("region", p.scalar("location.region"));
        loc.put("country", p.scalar("location.country"));
        out.put("location", loc);

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("linkedin", p.scalar("links.linkedin"));
        links.put("github", p.scalar("links.github"));
        links.put("portfolio", p.scalar("links.portfolio"));
        links.put("other", new ArrayList<>(p.linksOther));
        out.put("links", links);

        out.put("headline", p.scalar("headline"));
        out.put("years_experience", p.scalar("years_experience"));
        out.put("skills", skills(p.skills, includeConfidence));
        out.put("experience", new ArrayList<>(p.experience));
        out.put("education", new ArrayList<>(p.education));

        if (includeProvenance) out.put("provenance", new ArrayList<>(p.provenance));
        if (includeConfidence) {
            out.put("overall_confidence", p.overallConfidence);
            Map<String, Object> fc = new LinkedHashMap<>();
            for (Map.Entry<String, FieldResolution> e : p.fields.entrySet())
                if (e.getValue().value != null) fc.put(e.getKey(), e.getValue().confidence);
            out.put("field_confidence", fc);
        }
        return out;
    }

    private static List<Map<String, Object>> skills(List<Map<String, Object>> skills, boolean includeConfidence) {
        if (includeConfidence) return new ArrayList<>(skills);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> s : skills) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.get("name"));
            m.put("sources", s.get("sources"));
            out.add(m);
        }
        return out;
    }
}
