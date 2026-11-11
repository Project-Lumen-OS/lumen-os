// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict, tiny JSON reader for menu.json (no library on the device side): objects keep their key order
 * (LinkedHashMap), arrays are Lists, numbers are Long or Double, plus String, Boolean and null. Duplicate keys,
 * trailing commas and anything after the value are errors.
 */
public final class MenuJson {
    private final String s;
    private int i;

    private MenuJson(String s) { this.s = s; }

    public static Object parse(String text) {
        MenuJson p = new MenuJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("text after the value");
        return v;
    }

    private IllegalArgumentException err(String what) {
        int line = 1;
        for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
        return new IllegalArgumentException("menu.json line " + line + ": " + what);
    }

    private void ws() {
        while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
    }

    private Object value() {
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        throw err("unexpected '" + c + "'");
    }

    private Map<String, Object> object() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw err("expected a key");
            String k = string();
            ws();
            if (i >= s.length() || s.charAt(i) != ':') throw err("expected ':' after \"" + k + "\"");
            i++;
            ws();
            if (m.containsKey(k)) throw err("duplicate key \"" + k + "\"");
            m.put(k, value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            throw err("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        ArrayList<Object> a = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return a; }
        while (true) {
            ws();
            a.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == ']') { i++; return a; }
            throw err("expected ',' or ']'");
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw err("unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                if (i >= s.length()) throw err("bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': case '\\': case '/': b.append(e); break;
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("bad \\u escape");
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw err("bad escape \\" + e);
                }
            } else if (c < 0x20) {
                throw err("control character in a string");
            } else {
                b.append(c);
            }
        }
    }

    private Object number() {
        int st = i;
        if (s.charAt(i) == '-') i++;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(st, i);
        try {
            if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) return Long.valueOf(n);
            return Double.valueOf(n);
        } catch (NumberFormatException e) {
            throw err("bad number " + n);
        }
    }
}
