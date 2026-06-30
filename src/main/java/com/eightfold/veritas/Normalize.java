package com.eightfold.veritas;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, dependency-free normalizers.
 *
 * <p>Every normalizer returns {@code null} when it cannot confidently produce a
 * value. That is a load-bearing rule: a wrong-but-confident value pollutes
 * hiring decisions, so "unknown" must always beat "invented".
 */
public final class Normalize {

    private Normalize() {}

    private static final Map<String, String> COUNTRY = new HashMap<>();
    private static final Map<String, String> SKILL_ALIASES = new HashMap<>();
    private static final Map<String, String> MONTHS = new HashMap<>();

    static {
        for (String[] kv : new String[][]{
                {"united states", "US"}, {"united states of america", "US"}, {"usa", "US"},
                {"u.s.a.", "US"}, {"u.s.", "US"}, {"america", "US"},
                {"united kingdom", "GB"}, {"uk", "GB"}, {"u.k.", "GB"}, {"great britain", "GB"},
                {"england", "GB"}, {"india", "IN"}, {"canada", "CA"}, {"germany", "DE"},
                {"deutschland", "DE"}, {"france", "FR"}, {"ireland", "IE"}, {"netherlands", "NL"},
                {"australia", "AU"}, {"singapore", "SG"}, {"brazil", "BR"}, {"japan", "JP"}})
            COUNTRY.put(kv[0], kv[1]);

        for (String[] kv : new String[][]{
                {"js", "JavaScript"}, {"javascript", "JavaScript"}, {"ecmascript", "JavaScript"},
                {"ts", "TypeScript"}, {"typescript", "TypeScript"},
                {"py", "Python"}, {"python", "Python"}, {"python3", "Python"},
                {"golang", "Go"}, {"go", "Go"},
                {"reactjs", "React"}, {"react.js", "React"}, {"react", "React"},
                {"node", "Node.js"}, {"nodejs", "Node.js"}, {"node.js", "Node.js"},
                {"postgres", "PostgreSQL"}, {"postgresql", "PostgreSQL"}, {"psql", "PostgreSQL"},
                {"k8s", "Kubernetes"}, {"kubernetes", "Kubernetes"},
                {"ml", "Machine Learning"}, {"machine learning", "Machine Learning"},
                {"c++", "C++"}, {"cpp", "C++"}, {"cplusplus", "C++"},
                {"sql", "SQL"}, {"java", "Java"}, {"rust", "Rust"}, {"scala", "Scala"},
                {"aws", "AWS"}, {"gcp", "GCP"}, {"docker", "Docker"},
                {"spark", "Apache Spark"}, {"apache spark", "Apache Spark"},
                {"kafka", "Apache Kafka"}, {"apache kafka", "Apache Kafka"},
                {"tensorflow", "TensorFlow"}, {"pytorch", "PyTorch"},
                {"django", "Django"}, {"flask", "Flask"}})
            SKILL_ALIASES.put(kv[0], kv[1]);

        String[] mo = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < mo.length; i++) MONTHS.put(mo[i], String.format("%02d", i + 1));
        MONTHS.put("sept", "09");
    }

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern ALPHA2 = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern NON_DIGIT = Pattern.compile("\\D");
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern DATE_YM = Pattern.compile("(\\d{4})-(\\d{1,2})");
    private static final Pattern DATE_MY = Pattern.compile("(\\d{1,2})/(\\d{4})");
    private static final Pattern DATE_MON = Pattern.compile("([a-z]{3,4})\\.?\\s+(\\d{4})");
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");

    public static String email(Object raw) {
        if (!(raw instanceof String)) return null;
        String s = ((String) raw).trim().toLowerCase();
        return EMAIL.matcher(s).matches() ? s : null;
    }

    /** Best-effort E.164. Conservative: returns null rather than guess badly. */
    public static String phoneE164(Object raw) {
        if (!(raw instanceof String || raw instanceof Number)) return null;
        String s = String.valueOf(raw).trim();
        boolean hadPlus = s.startsWith("+") || s.startsWith("00");
        String digits = NON_DIGIT.matcher(s).replaceAll("");
        if (s.startsWith("00") && digits.length() >= 2) digits = digits.substring(2);
        if (digits.isEmpty() || digits.length() < 7 || digits.length() > 15) return null;
        if (hadPlus) return "+" + digits;
        if (digits.length() == 10) return "+1" + digits;                 // bare US/CA local
        if (digits.length() == 11 && digits.charAt(0) == '1') return "+" + digits;
        return "+" + digits;                                             // trust as-is
    }

    public static String country(Object raw) {
        if (!(raw instanceof String)) return null;
        String s = ((String) raw).trim();
        if (ALPHA2.matcher(s).matches()) return s.toUpperCase();
        return COUNTRY.get(s.toLowerCase());
    }

    public static String skill(Object raw) {
        if (!(raw instanceof String)) return null;
        String s = ((String) raw).trim();
        String key = s.toLowerCase();
        while (key.endsWith(".")) key = key.substring(0, key.length() - 1);
        if (key.isEmpty()) return null;
        String alias = SKILL_ALIASES.get(key);
        if (alias != null) return alias;
        return s.isEmpty() ? null : s;   // keep niche skills as-is, never drop
    }

    /** Normalize many date spellings to {@code YYYY-MM} (or {@code YYYY}). */
    public static String dateYm(Object raw) {
        if (!(raw instanceof String)) return null;
        String s = ((String) raw).trim().toLowerCase();
        if (s.equals("present") || s.equals("current") || s.equals("now") || s.equals("ongoing"))
            return "present";
        Matcher m = DATE_YM.matcher(s);
        if (m.matches()) return m.group(1) + "-" + String.format("%02d", Integer.parseInt(m.group(2)));
        m = DATE_MY.matcher(s);
        if (m.matches()) return m.group(2) + "-" + String.format("%02d", Integer.parseInt(m.group(1)));
        m = DATE_MON.matcher(s);
        if (m.matches() && MONTHS.containsKey(m.group(1).substring(0, Math.min(3, m.group(1).length()))))
            return m.group(2) + "-" + MONTHS.get(m.group(1).substring(0, Math.min(3, m.group(1).length())));
        m = YEAR.matcher(s);
        if (m.matches()) return m.group(1);
        return null;
    }

    public static String name(Object raw) {
        if (!(raw instanceof String)) return null;
        String s = WS.matcher((String) raw).replaceAll(" ").trim();
        return s.isEmpty() ? null : s;
    }

    public static String text(Object raw) {
        if (!(raw instanceof String)) return null;
        String s = WS.matcher((String) raw).replaceAll(" ").trim();
        return s.isEmpty() ? null : s;
    }

    /** Registry so the projection layer can apply a normalizer by name from config. */
    public static final Map<String, Function<Object, Object>> REGISTRY = new HashMap<>();
    static {
        REGISTRY.put("email", Normalize::email);
        REGISTRY.put("E164", Normalize::phoneE164);
        REGISTRY.put("country", Normalize::country);
        REGISTRY.put("canonical", Normalize::skill);   // config calls skill canon. "canonical"
        REGISTRY.put("date", Normalize::dateYm);
        REGISTRY.put("name", Normalize::name);
        REGISTRY.put("text", Normalize::text);
    }
}
