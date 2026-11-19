// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The user picture modes as the stock menu keeps them (newsettings CustomizeDataManager, the same on HB G0130 and
 * the X50 family): a list of name and vendor id in Settings.System {@link #SETTING} as
 * {@code [{"pictureName":"..","value":N}]}. The vendor keeps each mode's picture values under its id; this list only
 * names them. Immutable: every change returns a new list.
 */
public final class CustomModes {
    /** Settings.System key of the stock list (CustomizeDataManager.CUSTOMIZE_DATA_KEY). */
    public static final String SETTING = "customize_data_key";
    /** The "Custom" entry of the mode list shows while fewer modes exist (BottomHorizontalListCell.updateData). */
    public static final int MAX = 10;
    /** The name field's limit (PopWindowDialog type 254: ChinaInputFilter(editText, 24)). */
    public static final int NAME_MAX = 24;
    /** picture.mode token of a user mode: the prefix and its vendor id. */
    public static final String TOKEN = "custom_";

    public static final CustomModes NONE = new CustomModes(Collections.<CustomMode>emptyList());

    private final List<CustomMode> modes;

    private CustomModes(List<CustomMode> modes) {
        this.modes = Collections.unmodifiableList(modes);
    }

    public List<CustomMode> all() { return modes; }

    public int size() { return modes.size(); }

    /** No more modes can be made (the mode list drops its "Custom" entry). */
    public boolean full() { return modes.size() >= MAX; }

    public CustomMode of(int value) {
        for (CustomMode m : modes) if (m.value == value) return m;
        return null;
    }

    /** The mode of a picture.mode token, null for a stock mode or an id the list does not name. */
    public CustomMode of(String token) {
        Integer v = value(token);
        return v == null ? null : of(v);
    }

    /** Stock order of the name checks: an empty name, then a name already in the list. */
    public NameCheck check(String name) {
        if (name == null || name.isEmpty()) return NameCheck.EMPTY;
        for (CustomMode m : modes) if (m.name.equals(name)) return NameCheck.TAKEN;
        return NameCheck.OK;
    }

    /** The list with {@code m} added at the end; unchanged when its id or its name is already there (stock). */
    public CustomModes plus(CustomMode m) {
        if (m == null || m.value < 0 || of(m.value) != null || check(m.name) != NameCheck.OK) return this;
        List<CustomMode> l = new ArrayList<>(modes);
        l.add(m);
        return new CustomModes(l);
    }

    public CustomModes minus(int value) {
        List<CustomMode> l = new ArrayList<>(modes);
        l.removeIf(m -> m.value == value);
        return l.size() == modes.size() ? this : new CustomModes(l);
    }

    public static String token(int value) { return TOKEN + value; }

    /** The vendor id of a user mode token, null for any other token. */
    public static Integer value(String token) {
        if (token == null || !token.startsWith(TOKEN) || token.length() == TOKEN.length()) return null;
        String n = token.substring(TOKEN.length());
        if (n.length() > 9 || !n.chars().allMatch(Character::isDigit)) return null;
        return Integer.valueOf(n);
    }

    /**
     * The stock name length (ChinaInputFilter.filter): over the UTF-8 bytes, a byte below 0x80 counts 1, any other
     * counts 3 and skips the two bytes after it.
     */
    public static int nameLength(CharSequence s) {
        byte[] b = s.toString().getBytes(StandardCharsets.UTF_8);
        int n = 0;
        for (int i = 0; i < b.length; i++) {
            if (b[i] < 0) {
                n += 3;
                i += 2;
            } else {
                n++;
            }
        }
        return n;
    }

    /** Whether the name field takes this text (stock refuses the key press that would pass {@link #NAME_MAX}). */
    public static boolean fits(CharSequence s) { return nameLength(s) <= NAME_MAX; }

    /** The stock list from its setting; an empty or broken value is an empty list (stock getAllCustomizeData). */
    public static CustomModes parse(String json) {
        if (json == null || json.trim().isEmpty()) return NONE;
        try {
            Json p = new Json(json);
            List<CustomMode> l = new ArrayList<>();
            p.expect('[');
            if (!p.take(']')) {
                do {
                    CustomMode m = p.mode();
                    if (m != null) l.add(m);
                } while (p.take(','));
                p.expect(']');
            }
            CustomModes out = NONE;
            for (CustomMode m : l) out = out.plus(m);
            return out;
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /**
     * The menu values of these modes over {@code st}: the user modes as picture.mode options after the stock ones
     * ({@code profileModes} or the device's runtime subset), their names as labels, the based-on and delete rows shown
     * only while one of them is in use (stock isCustomizeImageMode), the "Custom" entry only while one more fits.
     */
    public MenuState rows(List<String> profileModes, MenuState st) {
        MenuState.Builder b = MenuState.builder();
        List<String> runtime = st.options(FeatureId.PICTURE_MODE);
        List<String> opts = new ArrayList<>(runtime != null ? runtime : profileModes);
        for (CustomMode m : modes) {
            String t = token(m.value);
            opts.add(t);
            b.label(FeatureId.PICTURE_MODE, t, m.name);
        }
        b.options(FeatureId.PICTURE_MODE, opts);
        boolean inUse = of(st.get(FeatureId.PICTURE_MODE)) != null;
        b.hideIf(FeatureId.PICTURE_CUSTOM_BASE, !inUse);
        b.hideIf(FeatureId.PICTURE_CUSTOM_DELETE, !inUse);
        b.hideIf(FeatureId.PICTURE_CUSTOM_NEW, full());
        return b.build();
    }

    /** The setting's value, the stock gson form. */
    public String json() {
        StringBuilder b = new StringBuilder("[");
        for (CustomMode m : modes) {
            if (b.length() > 1) b.append(',');
            b.append("{\"pictureName\":");
            Json.quote(b, m.name);
            b.append(",\"value\":").append(m.value).append('}');
        }
        return b.append(']').toString();
    }

    @Override public boolean equals(Object o) { return o instanceof CustomModes && ((CustomModes) o).modes.equals(modes); }

    @Override public int hashCode() { return modes.hashCode(); }

    @Override public String toString() { return modes.toString(); }

    /** The few JSON forms the list uses: an array of objects with a string and an integer. */
    private static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        boolean take(char c) {
            skip();
            if (i < s.length() && s.charAt(i) == c) { i++; return true; }
            return false;
        }

        void expect(char c) {
            if (!take(c)) throw new IllegalArgumentException("expected " + c + " at " + i);
        }

        /** One object; null when it lacks a name or a value (skipped like a broken gson entry). */
        CustomMode mode() {
            expect('{');
            String name = null;
            Integer value = null;
            if (!take('}')) {
                do {
                    String k = string();
                    expect(':');
                    skip();
                    if ("pictureName".equals(k)) name = string();
                    else if ("value".equals(k)) value = number();
                    else other();
                } while (take(','));
                expect('}');
            }
            return name == null || value == null ? null : new CustomMode(name, value);
        }

        private void other() {
            skip();
            if (i < s.length() && s.charAt(i) == '"') string();
            else number();
        }

        int number() {
            skip();
            int a = i;
            if (i < s.length() && s.charAt(i) == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            try {
                return Integer.parseInt(s.substring(a, i));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("number at " + a);
            }
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new IllegalArgumentException("escape at " + i);
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: b.append(e);
                }
            }
            throw new IllegalArgumentException("open string");
        }

        static void quote(StringBuilder b, String v) {
            b.append('"');
            for (int k = 0; k < v.length(); k++) {
                char c = v.charAt(k);
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                else b.append(c);
            }
            b.append('"');
        }
    }
}
