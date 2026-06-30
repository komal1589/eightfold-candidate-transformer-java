package com.eightfold.veritas.adapters;

import com.eightfold.veritas.Json;
import com.eightfold.veritas.Normalize;
import com.eightfold.veritas.adapters.Adapter.ClaimDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Concrete source adapters + the registry that maps a manifest "type" to an
 * adapter. Each adapter normalizes here so the merge layer only ever sees clean,
 * comparable values (plus the raw, for the audit trail). None may raise on bad
 * input -- the pipeline wraps them, but they also guard internally.
 */
public final class Sources {

    private Sources() {}

    public static final Map<String, Adapter> REGISTRY = new LinkedHashMap<>();
    static {
        for (Adapter a : new Adapter[]{
                new RecruiterCsv(), new AtsJson(), new GitHubApi(),
                new LinkedIn(), new Resume(), new RecruiterNotes()})
            REGISTRY.put(a.typeName(), a);
    }

    private static ClaimDraft c(String f, Object raw, Object v, String m) {
        return ClaimDraft.of(f, raw, v, m);
    }

    // ===================================================================== //
    // STRUCTURED 1: Recruiter CSV export                                    //
    // ===================================================================== //
    public static final class RecruiterCsv implements Adapter {
        public String typeName() { return "recruiter_csv"; }

        public List<ClaimDraft> extract(String raw) {
            List<ClaimDraft> out = new ArrayList<>();
            List<String> lines = raw.lines().filter(l -> !l.isBlank()).toList();
            if (lines.size() < 2) return out;
            List<String> header = parseCsvLine(lines.get(0));
            List<String> row = parseCsvLine(lines.get(1));   // one candidate per file
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < header.size() && i < row.size(); i++)
                m.put(header.get(i).trim().toLowerCase(), row.get(i).trim());

            String name = m.getOrDefault("name", "");
            if (!name.isEmpty()) out.add(c("full_name", name, Normalize.name(name), "csv_column:name"));
            String email = m.getOrDefault("email", "");
            if (!email.isEmpty()) out.add(c("emails", email, Normalize.email(email), "csv_column:email"));
            String phone = m.getOrDefault("phone", "");
            if (!phone.isEmpty()) out.add(c("phones", phone, Normalize.phoneE164(phone), "csv_column:phone"));
            String comp = m.getOrDefault("current_company", "");
            String title = m.getOrDefault("title", "");
            if (!comp.isEmpty() || !title.isEmpty()) {
                Map<String, Object> exp = new LinkedHashMap<>();
                exp.put("company", comp.isEmpty() ? null : Normalize.text(comp));
                exp.put("title", title.isEmpty() ? null : Normalize.text(title));
                exp.put("start", null); exp.put("end", "present"); exp.put("summary", null);
                out.add(c("experience", m, exp, "csv_row:current_role"));
                if (!title.isEmpty()) out.add(c("headline", title, Normalize.text(title), "csv_column:title"));
            }
            String loc = m.getOrDefault("location", "");
            if (!loc.isEmpty()) out.addAll(locationClaims(loc, "csv_column:location"));
            return out;
        }
    }

    // ===================================================================== //
    // STRUCTURED 2: ATS JSON blob (field names do NOT match ours)           //
    // ===================================================================== //
    public static final class AtsJson implements Adapter {
        public String typeName() { return "ats_json"; }

        @SuppressWarnings("unchecked")
        public List<ClaimDraft> extract(String raw) {
            List<ClaimDraft> out = new ArrayList<>();
            Object j = Json.parse(raw);
            if (!(j instanceof Map)) return out;
            Map<String, Object> d = (Map<String, Object>) j;

            Object full = first(d, "candidateName", "fullName", "name");
            if (full instanceof String s && !s.isBlank())
                out.add(c("full_name", s, Normalize.name(s), "ats:candidateName"));
            for (Object em : asList(first(d, "contactEmails", "emails", "email")))
                if (em != null) out.add(c("emails", em, Normalize.email(em), "ats:contactEmails"));
            for (Object ph : asList(first(d, "contactPhones", "phones", "phone")))
                if (ph != null) out.add(c("phones", ph, Normalize.phoneE164(ph), "ats:contactPhones"));
            Object head = first(d, "currentHeadline", "headline", "summary");
            if (head instanceof String s && !s.isBlank())
                out.add(c("headline", s, Normalize.text(s), "ats:currentHeadline"));
            Object yrs = first(d, "yearsOfExperience", "experienceYears", "yoe");
            if (yrs instanceof Number n) out.add(c("years_experience", yrs, n.doubleValue(), "ats:yearsOfExperience"));
            Object loc = first(d, "geo", "location");
            if (loc instanceof Map) {
                Map<String, Object> g = (Map<String, Object>) loc;
                Object city = first(g, "city", "town"), region = first(g, "state", "region"),
                        country = first(g, "country", "countryCode");
                if (city != null) out.add(c("location.city", city, Normalize.text(city), "ats:geo.city"));
                if (region != null) out.add(c("location.region", region, Normalize.text(region), "ats:geo.state"));
                if (country != null) out.add(c("location.country", country, Normalize.country(country), "ats:geo.country"));
            } else if (loc instanceof String s) {
                out.addAll(locationClaims(s, "ats:location"));
            }
            for (Object sk : asList(first(d, "skillTags", "skills")))
                if (sk instanceof String s && !s.isBlank())
                    out.add(c("skills", sk, Normalize.skill(sk), "ats:skillTags"));
            for (Object li : asList(first(d, "profileUrls", "links")))
                out.addAll(linkClaims(li, "ats:profileUrls"));
            return out;
        }
    }

    // ===================================================================== //
    // UNSTRUCTURED 1: GitHub public API response (offline-mocked JSON)       //
    // ===================================================================== //
    public static final class GitHubApi implements Adapter {
        public String typeName() { return "github_api"; }

        @SuppressWarnings("unchecked")
        public List<ClaimDraft> extract(String raw) {
            List<ClaimDraft> out = new ArrayList<>();
            Object j = Json.parse(raw);
            if (!(j instanceof Map)) return out;
            Map<String, Object> d = (Map<String, Object>) j;

            if (d.get("name") != null) out.add(c("full_name", d.get("name"), Normalize.name(d.get("name")), "github_api:name"));
            if (d.get("login") != null) {
                String url = "https://github.com/" + d.get("login");
                out.add(c("links.github", url, url, "github_api:login"));
            }
            if (d.get("bio") != null) out.add(c("headline", d.get("bio"), Normalize.text(d.get("bio")), "github_api:bio"));
            if (d.get("blog") != null) out.addAll(linkClaims(d.get("blog"), "github_api:blog"));
            if (d.get("location") != null) out.addAll(locationClaims(d.get("location"), "github_api:location"));
            for (Object lang : asList(d.get("languages")))
                if (lang instanceof String s && !s.isBlank())
                    out.add(c("skills", lang, Normalize.skill(lang), "github_api:languages"));
            return out;
        }
    }

    // ===================================================================== //
    // UNSTRUCTURED 2: LinkedIn profile fields                                //
    // ===================================================================== //
    public static final class LinkedIn implements Adapter {
        public String typeName() { return "linkedin"; }

        @SuppressWarnings("unchecked")
        public List<ClaimDraft> extract(String raw) {
            List<ClaimDraft> out = new ArrayList<>();
            Object j = Json.parse(raw);
            if (!(j instanceof Map)) return out;
            Map<String, Object> d = (Map<String, Object>) j;

            if (d.get("name") != null) out.add(c("full_name", d.get("name"), Normalize.name(d.get("name")), "linkedin:name"));
            if (d.get("headline") != null) out.add(c("headline", d.get("headline"), Normalize.text(d.get("headline")), "linkedin:headline"));
            if (d.get("profileUrl") != null) {
                String u = ((String) d.get("profileUrl")).trim();
                out.add(c("links.linkedin", d.get("profileUrl"), u, "linkedin:profileUrl"));
            }
            if (d.get("location") != null) out.addAll(locationClaims(d.get("location"), "linkedin:location"));
            for (Object e : asList(d.get("experience"))) {
                if (!(e instanceof Map)) continue;
                Map<String, Object> em = (Map<String, Object>) e;
                Map<String, Object> exp = new LinkedHashMap<>();
                exp.put("company", textOrNull(em.get("company")));
                exp.put("title", textOrNull(em.get("title")));
                exp.put("start", Normalize.dateYm(str(em.get("start"))));
                Object end = em.get("end");
                exp.put("end", (end == null || str(end).isBlank()) ? "present" : Normalize.dateYm(str(end)));
                exp.put("summary", textOrNull(em.get("summary")));
                out.add(c("experience", e, exp, "linkedin:experience"));
            }
            for (Object e : asList(d.get("education"))) {
                if (!(e instanceof Map)) continue;
                Map<String, Object> ed = (Map<String, Object>) e;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("institution", textOrNull(ed.containsKey("school") ? ed.get("school") : ed.get("institution")));
                rec.put("degree", textOrNull(ed.get("degree")));
                rec.put("field", textOrNull(ed.get("field")));
                rec.put("end_year", year(ed.containsKey("end") ? ed.get("end") : ed.get("end_year")));
                out.add(c("education", e, rec, "linkedin:education"));
            }
            for (Object sk : asList(d.get("skills")))
                if (sk instanceof String s && !s.isBlank())
                    out.add(c("skills", sk, Normalize.skill(sk), "linkedin:skills"));
            return out;
        }
    }

    // ===================================================================== //
    // UNSTRUCTURED 3: Resume prose (.txt extracted from PDF/DOCX)            //
    // ===================================================================== //
    public static final class Resume implements Adapter {
        public String typeName() { return "resume"; }

        private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
        private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s().-]{7,}\\d");
        private static final Pattern NAME_LINE = Pattern.compile("[A-Za-z][A-Za-z.'\\- ]{2,40}");
        private static final Pattern EXP = Pattern.compile(
                "(.+?),\\s*(.+?)\\s*\\((\\d{4}(?:-\\d{2})?)\\s*[-–to]+\\s*(present|\\d{4}(?:-\\d{2})?)\\)",
                Pattern.CASE_INSENSITIVE);
        private static final Pattern EDU = Pattern.compile(
                "(.+?)\\s+in\\s+(.+?),\\s*(.+?),\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);

        public List<ClaimDraft> extract(String text) {
            List<ClaimDraft> out = new ArrayList<>();
            if (text == null || text.isBlank()) return out;
            java.util.Set<String> emails = new java.util.LinkedHashSet<>();
            Matcher em = EMAIL.matcher(text);
            while (em.find()) emails.add(em.group());
            for (String e : emails) out.add(c("emails", e, Normalize.email(e), "resume:regex_email"));
            java.util.Set<String> phones = new java.util.LinkedHashSet<>();
            Matcher pm = PHONE.matcher(text);
            while (pm.find()) phones.add(pm.group());
            for (String p : phones) {
                String v = Normalize.phoneE164(p);
                if (v != null) out.add(c("phones", p, v, "resume:regex_phone"));
            }
            List<String> lines = text.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (!lines.isEmpty() && NAME_LINE.matcher(lines.get(0)).matches())
                out.add(c("full_name", lines.get(0), Normalize.name(lines.get(0)), "resume:first_line"));

            Map<String, String> sec = splitSections(text);
            for (String sk : parseSkillLine(sec.getOrDefault("skills", "")))
                out.add(c("skills", sk, Normalize.skill(sk), "resume:skills_section"));
            for (String line : sec.getOrDefault("experience", "").split("\n")) {
                Matcher m = EXP.matcher(line.strip().replaceAll("^[•\\-\\s]+", ""));
                if (m.find()) {
                    Map<String, Object> exp = new LinkedHashMap<>();
                    exp.put("title", Normalize.text(m.group(1)));
                    exp.put("company", Normalize.text(m.group(2)));
                    exp.put("start", Normalize.dateYm(m.group(3)));
                    exp.put("end", Normalize.dateYm(m.group(4)));
                    exp.put("summary", null);
                    out.add(c("experience", line, exp, "resume:experience_section"));
                }
            }
            for (String line : sec.getOrDefault("education", "").split("\n")) {
                Matcher m = EDU.matcher(line.strip().replaceAll("^[•\\-\\s]+", ""));
                if (m.find()) {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("degree", Normalize.text(m.group(1)));
                    rec.put("field", Normalize.text(m.group(2)));
                    rec.put("institution", Normalize.text(m.group(3)));
                    rec.put("end_year", Long.parseLong(m.group(4)));
                    out.add(c("education", line, rec, "resume:education_section"));
                }
            }
            return out;
        }
    }

    // ===================================================================== //
    // UNSTRUCTURED 4: Recruiter notes (free text)                            //
    // ===================================================================== //
    public static final class RecruiterNotes implements Adapter {
        public String typeName() { return "recruiter_notes"; }

        private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s().-]{7,}\\d");
        private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
        private static final Pattern SKILLS = Pattern.compile(
                "(?:skills?|strong in|expertise in)[:\\s]+([A-Za-z0-9+#.,/ &-]+)", Pattern.CASE_INSENSITIVE);

        public List<ClaimDraft> extract(String text) {
            List<ClaimDraft> out = new ArrayList<>();
            if (text == null || text.isBlank()) return out;
            java.util.Set<String> phones = new java.util.LinkedHashSet<>();
            Matcher pm = PHONE.matcher(text);
            while (pm.find()) phones.add(pm.group());
            for (String p : phones) {
                String v = Normalize.phoneE164(p);
                if (v != null) out.add(c("phones", p, v, "notes:regex_phone"));
            }
            java.util.Set<String> emails = new java.util.LinkedHashSet<>();
            Matcher em = EMAIL.matcher(text);
            while (em.find()) emails.add(em.group());
            for (String e : emails) out.add(c("emails", e, Normalize.email(e), "notes:regex_email"));
            Matcher sm = SKILLS.matcher(text);
            if (sm.find()) {
                for (String sk : sm.group(1).split(",|/| and ")) {
                    // strip trailing punctuation AND leading free-text filler words
                    // ("also Kafka" -> "Kafka") so prose doesn't leak into skill names
                    sk = sk.strip().replaceAll("[.\\s]+$", "")
                            .replaceFirst("(?i)^(also|and|or|the|with|in)\\s+", "").strip();
                    if (!sk.isEmpty() && sk.length() <= 30)
                        out.add(c("skills", sk, Normalize.skill(sk), "notes:skill_phrase"));
                }
            }
            return out;
        }
    }

    // ===================================================================== //
    // Shared helpers                                                        //
    // ===================================================================== //
    private static Object first(Map<String, Object> d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            if (v != null && !"".equals(v) && !(v instanceof List<?> l && l.isEmpty())) return v;
        }
        return null;
    }

    private static List<Object> asList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List) return (List<Object>) v;
        List<Object> l = new ArrayList<>(); l.add(v); return l;
    }

    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }

    private static String textOrNull(Object v) {
        if (v == null) return null;
        String s = Normalize.text(v);
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Long year(Object v) {
        if (v == null) return null;
        Matcher m = Pattern.compile("\\d{4}").matcher(String.valueOf(v));
        return m.find() ? Long.parseLong(m.group()) : null;
    }

    /** Split "City, Region, Country" / "City, Country" into canonical parts. */
    static List<ClaimDraft> locationClaims(Object raw, String method) {
        List<ClaimDraft> out = new ArrayList<>();
        if (!(raw instanceof String) || ((String) raw).isBlank()) return out;
        List<String> parts = new ArrayList<>();
        for (String p : ((String) raw).split(",")) if (!p.isBlank()) parts.add(p.strip());
        if (parts.size() == 1) {
            String cc = Normalize.country(parts.get(0));
            if (cc != null) out.add(c("location.country", parts.get(0), cc, method));
            else out.add(c("location.city", parts.get(0), Normalize.text(parts.get(0)), method));
        } else if (parts.size() == 2) {
            out.add(c("location.city", parts.get(0), Normalize.text(parts.get(0)), method));
            String cc = Normalize.country(parts.get(1));
            out.add(c(cc != null ? "location.country" : "location.region", parts.get(1),
                    cc != null ? cc : Normalize.text(parts.get(1)), method));
        } else {
            out.add(c("location.city", parts.get(0), Normalize.text(parts.get(0)), method));
            out.add(c("location.region", parts.get(1), Normalize.text(parts.get(1)), method));
            String last = parts.get(parts.size() - 1);
            String cc = Normalize.country(last);
            out.add(c("location.country", last, cc != null ? cc : Normalize.text(last), method));
        }
        return out;
    }

    static List<ClaimDraft> linkClaims(Object raw, String method) {
        if (!(raw instanceof String) || ((String) raw).isBlank()) return List.of();
        String u = ((String) raw).strip();
        String low = u.toLowerCase();
        if (low.contains("linkedin.com")) return List.of(c("links.linkedin", u, u, method));
        if (low.contains("github.com")) return List.of(c("links.github", u, u, method));
        return List.of(c("links.other", u, u, method));
    }

    private static Map<String, String> splitSections(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        String current = "_preamble";
        StringBuilder buf = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String h = line.strip().toLowerCase().replaceAll(":$", "");
            if (h.equals("skills") || h.equals("technical skills") || h.equals("experience")
                    || h.equals("work experience") || h.equals("employment") || h.equals("education")) {
                sections.put(current, buf.toString());
                buf = new StringBuilder();
                current = h.contains("skill") ? "skills"
                        : (h.contains("exper") || h.contains("employ")) ? "experience" : "education";
            } else {
                buf.append(line).append("\n");
            }
        }
        sections.put(current, buf.toString());
        return sections;
    }

    private static List<String> parseSkillLine(String block) {
        List<String> out = new ArrayList<>();
        for (String line : block.split("\n"))
            for (String tok : line.split(",|/|•|\\|")) {
                String t = tok.strip().replaceAll("^[.\\-\\s]+|[.\\-\\s]+$", "");
                if (!t.isEmpty() && t.length() <= 30) out.add(t);
            }
        return out;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuotes = false;
                } else cur.append(ch);
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                out.add(cur.toString()); cur = new StringBuilder();
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
