// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WFD "text/parameters" bodies (WFD 1.0 6.1): one parameter per line, "name: value" in SET_PARAMETER
 * and in GET_PARAMETER replies, a bare "name" per line in a GET_PARAMETER request.
 */
public final class WfdParams {
    private WfdParams() {}

    /** Ordered name to value; a line without ':' gives the name with value "". Names are trimmed, kept as sent. */
    public static Map<String, String> parse(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        if (body == null) return out;
        for (String raw : body.split("\r?\n")) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            int c = l.indexOf(':');
            if (c < 0) {
                out.put(l, "");
            } else {
                out.put(l.substring(0, c).trim(), l.substring(c + 1).trim());
            }
        }
        return out;
    }

    /** Names requested by a GET_PARAMETER body, in order, without duplicates. */
    public static List<String> names(String body) {
        return new ArrayList<>(parse(body).keySet());
    }

    /** "name: value\r\n" lines in map order. */
    public static String format(Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : values.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        return sb.toString();
    }
}
