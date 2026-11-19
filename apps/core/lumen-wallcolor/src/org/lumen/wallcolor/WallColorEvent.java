// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

/**
 * One wall colour message of the vendor: focus event 11001 (stock SystemUI WallColorCore.TYPE), a flat JSON object in
 * the formats libxgimi AC_Process writes: {"cmd":"show","x":..,"y":..,"w":..,"h":..,"trans":..,"points":[..]},
 * {"cmd":"close"}, {"cmd":"result","status":true|false,"code":N}. Fields the stock reads are kept; a missing number
 * is 0 and a missing status false, as the stock Gson read gives them.
 */
public final class WallColorEvent {
    /** The focus event type of the wall colour messages. */
    public static final int TYPE = 11001;
    public static final String SHOW = "show", SHOW_DETECT = "show_detect", CLOSE = "close", RESULT = "result";
    /** The largest pattern opacity the vendor sends (stock WallColorCoreKt.getData: trans clamped to 0..100). */
    public static final int TRANS_MAX = 100;

    public final String cmd;
    /** Opacity of the pattern, 0..100 as sent (clamped by {@link #alpha}). */
    public final int trans;
    public final boolean status;
    public final int code;
    /** True when the message carries the four corner points of the projected area (stock: the keystone window). */
    public final boolean points;

    private WallColorEvent(String cmd, int trans, boolean status, int code, boolean points) {
        this.cmd = cmd;
        this.trans = trans;
        this.status = status;
        this.code = code;
        this.points = points;
    }

    /** The pattern opacity 0..1 (stock: trans clamped to 0..100, times 0.01). */
    public float alpha() {
        return Math.max(0, Math.min(TRANS_MAX, trans)) * 0.01f;
    }

    /** The message, or null when it is not a wall colour object (no cmd). */
    public static WallColorEvent parse(String json) {
        if (json == null) return null;
        String cmd = text(json, "cmd");
        if (cmd == null) return null;
        return new WallColorEvent(cmd, number(json, "trans"), "true".equals(word(json, "status")), number(json, "code"),
                hasPoints(json));
    }

    /** The raw value after "key": (string without quotes, number or word), or null. */
    private static String word(String json, String key) {
        int i = json.indexOf('"' + key + '"');
        if (i < 0) return null;
        i = json.indexOf(':', i + key.length() + 2);
        if (i < 0) return null;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int end = i;
        while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) end++;
        String v = json.substring(i, end).trim();
        return v.isEmpty() ? null : v;
    }

    private static String text(String json, String key) {
        String v = word(json, key);
        if (v == null || v.length() < 2 || v.charAt(0) != '"' || v.charAt(v.length() - 1) != '"') return null;
        return v.substring(1, v.length() - 1);
    }

    private static int number(String json, String key) {
        String v = word(json, key);
        if (v == null) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean hasPoints(String json) {
        int i = json.indexOf("\"points\"");
        if (i < 0) return false;
        int open = json.indexOf('[', i);
        if (open < 0) return false;
        for (int k = open + 1; k < json.length(); k++) {
            char ch = json.charAt(k);
            if (Character.isWhitespace(ch)) continue;
            return ch == '[';
        }
        return false;
    }

    @Override public String toString() {
        return "WallColorEvent(" + cmd + ", trans=" + trans + ", status=" + status + ", code=" + code + ", points=" + points + ")";
    }
}
