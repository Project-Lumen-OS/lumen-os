// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON of the stock protocol, read and written the way stock vcontrol's Gson does: reading is lenient (single
 * quotes and bare words, as Gson.fromJson reads them; the open Home Assistant client sends Python's str(dict)), a
 * number keeps its text ({@link Num}), and the whole text must be one value. Writing keeps the field order it is
 * given, leaves out nulls and escapes like Gson's default writer (HTML characters as \\u00XX). Nesting deeper than
 * {@link #MAX_DEPTH} is refused: the reader recurses, and any LAN host could otherwise end the projector app's
 * process with a deep enough message.
 */
public final class Json {
    /** Stock messages nest 3 deep. */
    static final int MAX_DEPTH = 32;

    private final String s;
    private int i;
    private int depth;

    private Json(String s) {
        this.s = s;
    }

    /** A number of the text, kept as written (stock coerces it per field: int, String or Double). */
    public static final class Num {
        public final String text;

        Num(String text) {
            this.text = text;
        }

        @Override public String toString() {
            return text;
        }
    }

    /** Thrown for text that is not one JSON value; stock then drops the packet. */
    public static final class BadJson extends Exception {
        private static final long serialVersionUID = 1L;

        BadJson(String why) {
            super(why);
        }
    }

    /** The one value of the text: Map, List, String, Num, Boolean or null. */
    public static Object parse(String text) throws BadJson {
        if (text == null) throw new BadJson("null");
        Json j = new Json(text);
        Object v = j.value();
        j.space();
        if (j.i != j.s.length()) throw new BadJson("text after the value at " + j.i);
        return v;
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() throws BadJson {
        space();
        if (i >= s.length()) throw new BadJson("end of text");
        char c = s.charAt(i);
        if (c != '{' && c != '[') return c == '"' || c == '\'' ? quoted() : bare();
        if (++depth > MAX_DEPTH) throw new BadJson("nested deeper than " + MAX_DEPTH);
        Object v = c == '{' ? object() : array();
        depth--;
        return v;
    }

    private Map<String, Object> object() throws BadJson {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        space();
        if (peek('}')) return m;
        while (true) {
            space();
            String name = peekQuote() ? quoted() : word();
            space();
            expect(':');
            m.put(name, value());
            space();
            if (peek('}')) return m;
            expect(',');
        }
    }

    private List<Object> array() throws BadJson {
        List<Object> l = new ArrayList<>();
        i++;
        space();
        if (peek(']')) return l;
        while (true) {
            l.add(value());
            space();
            if (peek(']')) return l;
            expect(',');
        }
    }

    private boolean peekQuote() {
        return i < s.length() && (s.charAt(i) == '"' || s.charAt(i) == '\'');
    }

    private boolean peek(char c) {
        if (i < s.length() && s.charAt(i) == c) {
            i++;
            return true;
        }
        return false;
    }

    private void expect(char c) throws BadJson {
        if (!peek(c)) throw new BadJson("want '" + c + "' at " + i);
    }

    private String quoted() throws BadJson {
        char q = s.charAt(i++);
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == q) return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length()) break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new BadJson("short \\u escape");
                    try {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException x) {
                        throw new BadJson("bad \\u escape");
                    }
                    i += 4;
                    break;
                default: b.append(e);
            }
        }
        throw new BadJson("unterminated string");
    }

    /** A bare word: a name, true, false, null, a number or (lenient) a bare string. */
    private String word() throws BadJson {
        int a = i;
        while (i < s.length() && "{}[]:,\"' \t\r\n".indexOf(s.charAt(i)) < 0) i++;
        if (a == i) throw new BadJson("empty word at " + a);
        return s.substring(a, i);
    }

    private Object bare() throws BadJson {
        String w = word();
        switch (w) {
            case "true": return Boolean.TRUE;
            case "false": return Boolean.FALSE;
            case "null": return null;
            default: return isNumber(w) ? new Num(w) : w;
        }
    }

    private static boolean isNumber(String w) {
        return w.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    }

    /** An object written in the order its fields are put; a null field is left out, as Gson leaves it out. */
    public static final class Obj {
        private final StringBuilder b = new StringBuilder("{");
        private boolean first = true;

        public Obj put(String name, Object value) {
            if (value == null) return this;
            b.append(first ? "" : ",");
            first = false;
            quote(b, name);
            b.append(':');
            if (value instanceof String) quote(b, (String) value);
            else b.append(value instanceof Obj ? value.toString() : String.valueOf(value));
            return this;
        }

        @Override public String toString() {
            return b + "}";
        }
    }

    /** Gson's default string escape (JsonWriter with HTML-safe replacement characters). */
    static void quote(StringBuilder b, String v) {
        b.append('"');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\f': b.append("\\f"); break;
                case '<': case '>': case '&': case '=': case '\'': case '\u2028': case '\u2029':
                    b.append(String.format("\\u%04x", (int) c));
                    break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
