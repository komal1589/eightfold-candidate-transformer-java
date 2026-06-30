package com.eightfold.veritas;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.List;
import java.util.Map;

/**
 * A tiny wrapper around <b>Gson</b> (a well-known JSON library). It gives the
 * rest of the program one small, obvious place to read and write JSON.
 *
 * <p>Why keep a wrapper instead of calling Gson everywhere?
 * <ol>
 *   <li><b>Reading:</b> every other file just calls {@code Json.parse(...)} /
 *       {@code Json.parseObject(...)} and never has to know which library we use.
 *       (This replaced a ~120-line hand-written parser — Gson does that job now.)</li>
 *   <li><b>Writing:</b> we keep a very small, easy-to-read pretty-printer so that
 *       whole numbers print as {@code 12}, not {@code 12.0}. Gson's default would
 *       print {@code 12.0}, which is uglier in the output profile.</li>
 * </ol>
 *
 * <p>Representation after parsing (Gson's defaults for an untyped parse):
 * objects -> {@link Map}, arrays -> {@link List}, strings -> String,
 * numbers -> Double, booleans -> Boolean, null -> {@code null}.
 */
public final class Json {

    private Json() {}

    /** Gson is thread-safe and cheap to reuse, so we keep one shared instance. */
    private static final Gson GSON = new Gson();

    /** Thrown when text is not valid JSON (so callers can sandbox a bad source). */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message, Throwable cause) { super(message, cause); }
    }

    // ----------------------------------------------------------------- read
    /** Parse any JSON text into Maps / Lists / String / Double / Boolean / null. */
    public static Object parse(String text) {
        try {
            return GSON.fromJson(text, Object.class);
        } catch (JsonSyntaxException e) {
            throw new JsonException("invalid JSON", e);
        }
    }

    /** Parse JSON expected to be an object; returns {@code null} if it is not one. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        return (value instanceof Map) ? (Map<String, Object>) value : null;
    }

    // ----------------------------------------------------------------- write
    /** Pretty-print with two-space indentation and stable (insertion) key order. */
    public static String write(Object value) {
        StringBuilder b = new StringBuilder();
        writeValue(value, b, 0);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object v, StringBuilder b, int indent) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof String) { writeString((String) v, b); return; }
        if (v instanceof Boolean) { b.append(v.toString()); return; }
        if (v instanceof Double) { b.append(formatDouble((Double) v)); return; }
        if (v instanceof Number) { b.append(v.toString()); return; }
        if (v instanceof Map) { writeObject((Map<String, Object>) v, b, indent); return; }
        if (v instanceof List) { writeArray((List<Object>) v, b, indent); return; }
        writeString(String.valueOf(v), b);
    }

    private static void writeObject(Map<String, Object> m, StringBuilder b, int indent) {
        if (m.isEmpty()) { b.append("{}"); return; }
        b.append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            pad(b, indent + 1);
            writeString(e.getKey(), b);
            b.append(": ");
            writeValue(e.getValue(), b, indent + 1);
            if (++i < m.size()) b.append(',');
            b.append('\n');
        }
        pad(b, indent); b.append('}');
    }

    private static void writeArray(List<Object> a, StringBuilder b, int indent) {
        if (a.isEmpty()) { b.append("[]"); return; }
        b.append("[\n");
        for (int i = 0; i < a.size(); i++) {
            pad(b, indent + 1);
            writeValue(a.get(i), b, indent + 1);
            if (i < a.size() - 1) b.append(',');
            b.append('\n');
        }
        pad(b, indent); b.append(']');
    }

    private static void writeString(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\t': b.append("\\t"); break;
                case '\r': b.append("\\r"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    /** Render whole-valued doubles as integers (9.0 -> "9") for clean output. */
    private static String formatDouble(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
        return Double.toString(d);
    }

    private static void pad(StringBuilder b, int indent) {
        for (int i = 0; i < indent; i++) b.append("  ");
    }
}
