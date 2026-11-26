// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.vcontrol;

import java.util.Collections;
import java.util.Map;

/**
 * One message of the phone (stock com.xgimi.common.entity.VcontrolCmd), only the fields Lumen answers. Fields are
 * read as stock's Gson binds them: an int field takes a number or a numeric string, a String field takes a string,
 * a number (its text) or a boolean, commonCmd keeps raw values (stock only uses its string values). A message stock
 * could not bind is {@link #parse} null: stock drops it without a reply.
 */
public final class Request {
    public final int action;
    public final String msgid;
    public final int controlMode;
    public final int controlType;
    public final int controlTime;
    public final String controlData;
    public final boolean hasControl;
    /** commonCmd was in the message (stock dereferences it without a check for 50036: no answer without it). */
    public final boolean hasCommon;
    public final Map<String, Object> common;
    /** authCode.authState, null without authCode. */
    public final Integer authState;
    public final String phoneUid;
    /** status.state (the phone's answer to an auth code), 0 without status. */
    public final int statusState;

    private Request(Map<String, Object> m) throws Json.BadJson {
        action = intOf(m.get("action"), 0);
        msgid = stringOf(m.get("msgid"));
        Map<String, Object> c = objectOf(m.get("controlCmd"));
        hasControl = c != null;
        controlMode = c == null ? 0 : intOf(c.get("mode"), 0);
        controlType = c == null ? 0 : intOf(c.get("type"), 0);
        controlTime = c == null ? 0 : intOf(c.get("time"), 0);
        controlData = c == null ? null : stringOf(c.get("data"));
        Map<String, Object> cc = objectOf(m.get("commonCmd"));
        hasCommon = cc != null;
        common = cc == null ? Collections.<String, Object>emptyMap() : cc;
        Map<String, Object> auth = objectOf(m.get("authCode"));
        authState = auth == null ? null : intOf(auth.get("authState"), 0);
        phoneUid = auth == null ? null : stringOf(auth.get("phoneUid"));
        Map<String, Object> st = objectOf(m.get("status"));
        statusState = st == null ? 0 : intOf(st.get("state"), 0);
    }

    /** The message of a packet or a frame body, null when stock's Gson would have failed on it. */
    public static Request parse(String text) {
        try {
            Object v = Json.parse(text);
            if (!(v instanceof Map)) return null;
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) v;
            return new Request(m);
        } catch (Json.BadJson e) {
            return null;
        }
    }

    /** A string value of commonCmd; stock ignores a value of another type (Gson reads JSON numbers as Double). */
    public String commonString(String key) {
        Object v = common.get(key);
        return v instanceof String ? (String) v : null;
    }

    private static int intOf(Object v, int absent) throws Json.BadJson {
        if (v == null) return absent;
        String t = v instanceof Json.Num ? ((Json.Num) v).text : v instanceof String ? (String) v : null;
        if (t == null) throw new Json.BadJson("not an int: " + v);
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(t);
                if (d == (int) d) return (int) d;
            } catch (NumberFormatException ignored) {
                // not a number at all
            }
            throw new Json.BadJson("not an int: " + t);
        }
    }

    private static String stringOf(Object v) throws Json.BadJson {
        if (v == null || v instanceof String) return (String) v;
        if (v instanceof Json.Num || v instanceof Boolean) return v.toString();
        throw new Json.BadJson("not a string: " + v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectOf(Object v) throws Json.BadJson {
        if (v == null) return null;
        if (v instanceof Map) return (Map<String, Object>) v;
        throw new Json.BadJson("not an object: " + v);
    }
}
