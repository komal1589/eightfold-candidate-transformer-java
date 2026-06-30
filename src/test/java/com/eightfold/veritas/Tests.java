package com.eightfold.veritas;

import com.eightfold.veritas.Pipeline.RunReport;
import com.eightfold.veritas.Pipeline.SourceLog;

import java.util.List;
import java.util.Map;

/**
 * Zero-dependency test harness (no JUnit needed -- keeps the project buildable
 * with a bare JDK). Run:  java -cp bin com.eightfold.veritas.Tests
 *
 * <p>These double as the "gold-profile comparison" the assignment mentions: a
 * fixed manifest must always produce a known profile, and several edge cases
 * (corrupt source, conflict resolution, on_missing=error) are asserted directly.
 */
public final class Tests {

    static final String MANIFEST = "samples/inputs/manifest.json";
    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        run("phone_e164", Tests::phoneE164);
        run("country_and_skill_canonicalization", Tests::countryAndSkill);
        run("date_normalization", Tests::dateNorm);
        run("pipeline_runs_and_validates", Tests::pipelineValidates);
        run("robust_to_corrupt_source", Tests::robustCorrupt);
        run("conflict_resolution_picks_highest_trust", Tests::conflict);
        run("emails_deduped_case_insensitive", Tests::emailsDedup);
        run("skills_merged_with_confidence", Tests::skills);
        run("provenance_traces_every_present_field", Tests::provenance);
        run("determinism", Tests::determinism);
        run("path_mini_language", Tests::pathLanguage);
        run("projection_rename_and_normalize", Tests::projectionRename);
        run("on_missing_error_raises", Tests::onMissingError);
        run("on_missing_omit_drops_field", Tests::onMissingOmit);
        run("projection_type_validation", Tests::typeValidation);

        System.out.printf("%n%d/%d tests passed%n", passed, passed + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    // ----------------------------------------------------------- normalizers
    static void phoneE164() {
        eq("+14155550142", Normalize.phoneE164("(415) 555-0142"));
        eq("+14155550142", Normalize.phoneE164("+1 415 555 0142"));
        eq("+447911123456", Normalize.phoneE164("00447911123456"));
        eq(null, Normalize.phoneE164("123"));            // too short -> null, not invented
        eq(null, Normalize.phoneE164(null));
    }

    static void countryAndSkill() {
        eq("US", Normalize.country("United States"));
        eq("US", Normalize.country("usa"));
        eq(null, Normalize.country("Atlantis"));         // unknown -> null
        eq("Go", Normalize.skill("golang"));
        eq("Kubernetes", Normalize.skill("k8s"));
        eq("JavaScript", Normalize.skill("JS"));
    }

    static void dateNorm() {
        eq("2017-06", Normalize.dateYm("Jun 2017"));
        eq("2020-01", Normalize.dateYm("01/2020"));
        eq("2021-03", Normalize.dateYm("2021-3"));
        eq("present", Normalize.dateYm("present"));
        eq(null, Normalize.dateYm("garbage"));
    }

    // ----------------------------------------------------------- end-to-end
    static void pipelineValidates() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        check(r.validationErrors.isEmpty(), "expected no validation errors, got " + r.validationErrors);
        eq("cand_ada_lovelace_001", r.output.get("candidate_id"));
    }

    static void robustCorrupt() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        SourceLog corrupt = r.sourceLog.stream()
                .filter(s -> "corrupt_ats.json".equals(s.path())).findFirst().orElseThrow();
        check(!corrupt.ok() && corrupt.claims() == 0, "corrupt source should be skipped, not crash");
        check(r.output.get("full_name") != null, "other 6 sources should still produce a profile");
    }

    @SuppressWarnings("unchecked")
    static void conflict() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        eq("Ada B. Lovelace", r.output.get("full_name"));            // CSV (0.90) wins
        Map<String, Object> fc = (Map<String, Object>) r.output.get("field_confidence");
        check(((Number) fc.get("full_name")).doubleValue() < 1.0, "conflict should keep confidence < 1.0");
    }

    @SuppressWarnings("unchecked")
    static void emailsDedup() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        List<String> emails = (List<String>) r.output.get("emails");
        for (String e : emails) eq(e.toLowerCase(), e);              // all normalized
        check(emails.size() == emails.stream().distinct().count(), "no duplicate emails");
        check(emails.contains("ada.lovelace@example.com"), "primary email present");
    }

    @SuppressWarnings("unchecked")
    static void skills() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        Map<String, Map<String, Object>> byName = new java.util.HashMap<>();
        List<String> names = new java.util.ArrayList<>();
        for (Map<String, Object> s : (List<Map<String, Object>>) r.output.get("skills")) {
            byName.put((String) s.get("name"), s);
            names.add((String) s.get("name"));
        }
        check(byName.containsKey("Go"), "Go skill present");
        check(((Number) byName.get("Go").get("confidence")).doubleValue() >= 0.9, "Go agreed by many -> high conf");
        check(!names.contains("distributed systems"), "case variants must be folded");
    }

    @SuppressWarnings("unchecked")
    static void provenance() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        boolean nameTraced = false;
        for (Map<String, Object> p : (List<Map<String, Object>>) r.output.get("provenance")) {
            check(p.get("source") != null && p.get("method") != null, "provenance never anonymous");
            if ("full_name".equals(p.get("field"))) nameTraced = true;
        }
        check(nameTraced, "full_name must be traced in provenance");
    }

    static void determinism() throws Exception {
        Map<String, Object> a = Pipeline.run(MANIFEST, null).output;
        Map<String, Object> b = Pipeline.run(MANIFEST, null).output;
        eq(Json.write(a), Json.write(b));
    }

    // ----------------------------------------------------------- projection
    static void pathLanguage() {
        Object doc = Json.parse("""
            {"emails":["a@b.com","c@d.com"],
             "skills":[{"name":"Go"},{"name":"Python"}],
             "location":{"country":"US"}}""");
        eq("a@b.com", Projection.resolvePath(doc, "emails[0]"));
        eq(List.of("Go", "Python"), Projection.resolvePath(doc, "skills[].name"));
        eq("US", Projection.resolvePath(doc, "location.country"));
        check(Projection.resolvePath(doc, "phones[0]") == Projection.MISSING, "absent hop -> MISSING");
    }

    @SuppressWarnings("unchecked")
    static void projectionRename() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        Map<String, Object> cfg = (Map<String, Object>) Json.parse("""
            {"fields":[
               {"path":"full_name","from":"full_name","type":"string","required":true},
               {"path":"primary_email","from":"emails[0]","type":"string","required":true},
               {"path":"phone","from":"phones[0]","type":"string","normalize":"E164"},
               {"path":"skills","from":"skills[].name","type":"string[]"}],
             "on_missing":"null"}""");
        Map<String, Object> out = Projection.project(r.canonical, cfg);
        eq("ada.lovelace@example.com", out.get("primary_email"));
        check(((String) out.get("phone")).startsWith("+1"), "phone normalized to E164");
        check(out.get("skills") instanceof List, "skills projected to string[]");
    }

    @SuppressWarnings("unchecked")
    static void onMissingError() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        Map<String, Object> cfg = (Map<String, Object>) Json.parse("""
            {"fields":[{"path":"fax","from":"fax_number","type":"string","required":true}],
             "on_missing":"error"}""");
        try {
            Projection.project(r.canonical, cfg);
            check(false, "expected ProjectionException");
        } catch (Projection.ProjectionException expected) { /* ok */ }
    }

    @SuppressWarnings("unchecked")
    static void onMissingOmit() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        Map<String, Object> cfg = (Map<String, Object>) Json.parse("""
            {"fields":[{"path":"full_name","type":"string"},
                       {"path":"fax","from":"fax_number","type":"string"}],
             "on_missing":"omit"}""");
        Map<String, Object> out = Projection.project(r.canonical, cfg);
        check(out.containsKey("full_name") && !out.containsKey("fax"), "missing field omitted");
    }

    @SuppressWarnings("unchecked")
    static void typeValidation() throws Exception {
        RunReport r = Pipeline.run(MANIFEST, null);
        Map<String, Object> cfg = (Map<String, Object>) Json.parse("""
            {"fields":[{"path":"emails","from":"emails","type":"string"}],"on_missing":"null"}""");
        try {
            Projection.project(r.canonical, cfg);   // emails is a list, not a string
            check(false, "expected type validation failure");
        } catch (Projection.ProjectionException expected) { /* ok */ }
    }

    // --------------------------------------------------------------- harness
    private interface TestBody { void run() throws Exception; }

    private static void run(String name, TestBody body) {
        try {
            body.run();
            System.out.println("  ok  " + name);
            passed++;
        } catch (Throwable t) {
            System.out.println("  FAIL " + name + " -> " + t.getMessage());
            failed++;
        }
    }

    private static void eq(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private Tests() {}
}
