package com.eightfold.veritas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The projection layer -- the assignment's "required twist".
 *
 * <p>Same engine, no code changes: a runtime config (a pure data document)
 * reshapes the canonical record. This class is a small, total interpreter for
 * that config. It can select a subset of fields, rename/remap from a canonical
 * path ("from"), apply per-field normalization, toggle provenance/confidence,
 * choose a missing-value policy (null|omit|error), and type-validate the result.
 *
 * <p>Path mini-language for "from":
 * <pre>
 *   full_name           dotted scalar
 *   emails[0]           nth list element
 *   location.country    nested scalar
 *   skills[].name       pluck `.name` from each element -> a list
 *   links.other         a whole list
 * </pre>
 */
public final class Projection {

    private Projection() {}

    /** Sentinel distinguishing "absent" from a present null. */
    public static final Object MISSING = new Object();

    public static final class ProjectionException extends RuntimeException {
        public ProjectionException(String m) { super(m); }
    }

    // ----------------------------------------------------------- path resolve
    private static final Pattern PART = Pattern.compile("([A-Za-z_][\\w]*)((?:\\[\\d*\\])*)");
    private static final Pattern IDX = Pattern.compile("\\[(\\d*)\\]");

    /** Resolve a mini-language path; returns {@link #MISSING} if any hop is absent. */
    @SuppressWarnings("unchecked")
    public static Object resolvePath(Object doc, String path) {
        Object cur = doc;
        List<Object> tokens = tokenize(path);
        for (int i = 0; i < tokens.size(); i++) {
            Object tok = tokens.get(i);
            if (tok.equals("[]")) {
                if (!(cur instanceof List)) return MISSING;
                return pluckRemainder((List<Object>) cur, path);
            }
            if (tok instanceof Integer idx) {
                if (!(cur instanceof List)) return MISSING;
                List<Object> l = (List<Object>) cur;
                int n = idx < 0 ? l.size() + idx : idx;
                if (n < 0 || n >= l.size()) return MISSING;
                cur = l.get(n);
            } else {
                if (!(cur instanceof Map)) return MISSING;
                Map<String, Object> m = (Map<String, Object>) cur;
                if (!m.containsKey((String) tok)) return MISSING;
                cur = m.get((String) tok);
            }
            if (cur == null) return null;
        }
        return cur;
    }

    private static List<Object> tokenize(String path) {
        List<Object> tokens = new ArrayList<>();
        for (String part : path.split("\\.")) {
            Matcher m = PART.matcher(part);
            if (!m.matches()) { tokens.add(part); continue; }
            tokens.add(m.group(1));
            Matcher im = IDX.matcher(m.group(2));
            while (im.find())
                tokens.add(im.group(1).isEmpty() ? "[]" : Integer.parseInt(im.group(1)));
        }
        return tokens;
    }

    private static List<Object> pluckRemainder(List<Object> list, String fullPath) {
        String after = fullPath.split("\\[\\]", 2)[1];
        if (after.startsWith(".")) after = after.substring(1);
        List<Object> out = new ArrayList<>();
        for (Object el : list) {
            if (after.isEmpty()) { out.add(el); continue; }
            Object v = resolvePath(el, after);
            if (v != MISSING && v != null) out.add(v);
        }
        return out;
    }

    // ----------------------------------------------------------- type checks
    @SuppressWarnings("unchecked")
    private static boolean typeOk(Object value, String typ) {
        if (typ == null) return true;
        switch (typ) {
            case "string": return value instanceof String;
            case "number": return value instanceof Number;
            case "boolean": return value instanceof Boolean;
            case "object": return value instanceof Map;
            default:
                if (typ.endsWith("[]")) {
                    if (!(value instanceof List)) return false;
                    String inner = typ.substring(0, typ.length() - 2);
                    for (Object v : (List<Object>) value) if (!typeOk(v, inner)) return false;
                    return true;
                }
                return true;   // unknown asserted type -> don't block
        }
    }

    private static Object coerce(Object value, String typ) {
        if ("number".equals(typ) && value instanceof String s) {
            try { return s.contains(".") ? Double.parseDouble(s) : Long.parseLong(s); }
            catch (NumberFormatException e) { return value; }
        }
        if ("string".equals(typ) && value instanceof Number) return String.valueOf(value);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object applyNormalize(Object value, String name) {
        Function<Object, Object> fn = Normalize.REGISTRY.get(name);
        if (fn == null) return value;
        if (value instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object v : (List<Object>) value) out.add(fn.apply(v));
            return out;
        }
        return fn.apply(value);
    }

    // ---------------------------------------------------------------- project
    @SuppressWarnings("unchecked")
    public static Map<String, Object> project(Map<String, Object> canonical, Map<String, Object> config) {
        String onMissing = (String) config.getOrDefault("on_missing", "null");
        if (!List.of("null", "omit", "error").contains(onMissing))
            throw new ProjectionException("on_missing must be null|omit|error, got " + onMissing);
        boolean includeConf = boolOf(config.get("include_confidence"), true);
        boolean includeProv = boolOf(config.get("include_provenance"), false);

        Map<String, Object> result = new LinkedHashMap<>();
        List<Object> fields = (List<Object>) config.getOrDefault("fields", List.of());
        for (Object specObj : fields) {
            Map<String, Object> spec = (Map<String, Object>) specObj;
            String outPath = (String) spec.get("path");
            String srcPath = (String) spec.getOrDefault("from", outPath);
            String typ = (String) spec.get("type");
            boolean required = boolOf(spec.get("required"), false);

            Object value = resolvePath(canonical, srcPath);
            if (isMissing(value)) {
                if (required && onMissing.equals("error"))
                    throw new ProjectionException("required field '" + outPath + "' missing (from '" + srcPath + "')");
                if (onMissing.equals("omit")) continue;
                setPath(result, outPath, null);
                continue;
            }
            if (spec.containsKey("normalize")) {
                value = applyNormalize(value, (String) spec.get("normalize"));
                if (isMissing(value)) {
                    if (required && onMissing.equals("error"))
                        throw new ProjectionException("required field '" + outPath + "' empty after normalize");
                    if (onMissing.equals("omit")) continue;
                    setPath(result, outPath, null);
                    continue;
                }
            }
            if (typ != null) {
                value = coerce(value, typ);
                if (!typeOk(value, typ))
                    throw new ProjectionException("field '" + outPath + "' = " + value + " is not of type '" + typ + "'");
            }
            setPath(result, outPath, value);
        }

        if (includeConf && canonical.containsKey("overall_confidence"))
            result.put("overall_confidence", canonical.get("overall_confidence"));
        if (includeProv && canonical.containsKey("provenance"))
            result.put("provenance", canonical.get("provenance"));
        return result;
    }

    private static boolean isMissing(Object v) {
        return v == MISSING || v == null || "".equals(v) || (v instanceof List<?> l && l.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static void setPath(Map<String, Object> doc, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = doc;
        for (int i = 0; i < parts.length - 1; i++)
            cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
        cur.put(parts[parts.length - 1], value);
    }

    private static boolean boolOf(Object v, boolean dflt) {
        return v instanceof Boolean b ? b : dflt;
    }
}
