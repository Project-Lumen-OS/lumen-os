// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The small JSON of Lumen Home's layout and of the settings backup file: objects (insertion ordered maps), arrays, strings,
 * whole numbers (Long), booleans and null. Plain Java, no Android, so the host tests run it as it is. A broken text
 * throws IllegalArgumentException; nothing here guesses.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** The value of {@code text}: Map, List, String, Long, Boolean or null. */
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("no text");
        Json p = new Json(text);
        Object v = p.value(0);
        p.space();
        if (p.i != p.s.length()) throw p.bad("text after the value");
        return v;
    }

    private static final int MAX_DEPTH = 32;

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw bad("too deep");
        space();
        if (i >= s.length()) throw bad("end of text");
        char c = s.charAt(i);
        if (c == '{') return object(depth);
        if (c == '[') return array(depth);
        if (c == '"') return string();
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        throw bad("unexpected " + c);
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        space();
        if (peek() == '}') { i++; return m; }
        while (true) {
            space();
            if (peek() != '"') throw bad("name expected");
            String k = string();
            space();
            if (peek() != ':') throw bad("colon expected");
            i++;
            m.put(k, value(depth + 1));
            space();
            char c = peek();
            i++;
            if (c == '}') return m;
            if (c != ',') throw bad("comma expected");
        }
    }

    private List<Object> array(int depth) {
        List<Object> l = new ArrayList<>();
        i++;
        space();
        if (peek() == ']') { i++; return l; }
        while (true) {
            l.add(value(depth + 1));
            space();
            char c = peek();
            i++;
            if (c == ']') return l;
            if (c != ',') throw bad("comma expected");
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw bad("open string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw bad("open escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"': case '\\': case '/': b.append(e); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw bad("short escape");
                    try {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException x) {
                        throw bad("bad escape");
                    }
                    i += 4;
                    break;
                default: throw bad("bad escape");
            }
        }
    }

    private Long number() {
        int st = i;
        if (peek() == '-') i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i < s.length() && (s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E')) throw bad("whole numbers only");
        try {
            return Long.parseLong(s.substring(st, i));
        } catch (NumberFormatException x) {
            throw bad("bad number");
        }
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private IllegalArgumentException bad(String why) {
        return new IllegalArgumentException("json at " + i + ": " + why);
    }

    // ------------------------------------------------------------------ writing

    /** {@code v} (Map, List, String, Number, Boolean or null) as compact JSON. */
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v);
        return b.toString();
    }

    private static void write(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String) {
            quote(b, (String) v);
        } else if (v instanceof Number || v instanceof Boolean) {
            b.append(v);
        } else if (v instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object o : (List<?>) v) {
                if (!first) b.append(',');
                first = false;
                write(b, o);
            }
            b.append(']');
        } else {
            throw new IllegalArgumentException("cannot write " + v.getClass().getSimpleName());
        }
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c == '\t') b.append("\\t");
            else if (c == '\r') b.append("\\r");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        b.append('"');
    }

    // ------------------------------------------------------------------ reading helpers

    /** The string under {@code key} of map {@code m}, or {@code def}. */
    public static String str(Map<?, ?> m, String key, String def) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String ? (String) v : def;
    }

    public static boolean bool(Map<?, ?> m, String key, boolean def) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public static long num(Map<?, ?> m, String key, long def) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Long ? (Long) v : def;
    }

    /** The strings of the list under {@code key} (other entries skipped), empty when there is none. */
    public static List<String> strings(Map<?, ?> m, String key) {
        List<String> out = new ArrayList<>();
        Object v = m == null ? null : m.get(key);
        if (v instanceof List) for (Object o : (List<?>) v) if (o instanceof String) out.add((String) o);
        return out;
    }
}
