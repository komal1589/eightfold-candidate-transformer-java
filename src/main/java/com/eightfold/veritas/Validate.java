package com.eightfold.veritas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lightweight structural validation of the default canonical output.
 *
 * <p>Dependency-free on purpose. It checks shape and types, not business rules --
 * the engine already guarantees "unknown -> null". Returns a list of
 * human-readable problems (empty == valid).
 */
public final class Validate {

    private Validate() {}

    @SuppressWarnings("unchecked")
    public static List<String> canonical(Map<String, Object> doc) {
        List<String> errs = new ArrayList<>();

        if (!(doc.get("candidate_id") instanceof String)) errs.add("candidate_id must be a string");
        for (String f : List.of("full_name", "headline"))
            if (!strOrNull(doc.get(f))) errs.add(f + " must be string|null");
        for (String f : List.of("emails", "phones")) {
            Object v = doc.get(f);
            if (!(v instanceof List) || !((List<Object>) v).stream().allMatch(x -> x instanceof String))
                errs.add(f + " must be string[]");
        }
        Object ye = doc.get("years_experience");
        if (!(ye == null || ye instanceof Number)) errs.add("years_experience must be number|null");

        Object loc = doc.get("location");
        if (!(loc instanceof Map)) errs.add("location must be an object");
        else for (String k : List.of("city", "region", "country"))
            if (!strOrNull(((Map<String, Object>) loc).get(k))) errs.add("location." + k + " must be string|null");

        Object links = doc.get("links");
        if (!(links instanceof Map)) errs.add("links must be an object");
        else {
            Map<String, Object> lm = (Map<String, Object>) links;
            for (String k : List.of("linkedin", "github", "portfolio"))
                if (!strOrNull(lm.get(k))) errs.add("links." + k + " must be string|null");
            if (!(lm.getOrDefault("other", List.of()) instanceof List)) errs.add("links.other must be string[]");
        }

        for (Object s : (List<Object>) doc.getOrDefault("skills", List.of()))
            if (!(s instanceof Map) || !((Map<String, Object>) s).containsKey("name")) {
                errs.add("each skill must be an object with a name"); break;
            }

        for (String f : List.of("experience", "education")) {
            Object v = doc.getOrDefault(f, List.of());
            if (!(v instanceof List)) { errs.add(f + " must be an array"); continue; }
            for (Object rec : (List<Object>) v)
                if (!(rec instanceof Map)) { errs.add("each " + f + " entry must be an object"); break; }
        }

        Object oc = doc.get("overall_confidence");
        if (oc != null && !(oc instanceof Number n && n.doubleValue() >= 0.0 && n.doubleValue() <= 1.0))
            errs.add("overall_confidence must be a number in [0,1]");
        return errs;
    }

    private static boolean strOrNull(Object v) {
        return v == null || v instanceof String;
    }
}
