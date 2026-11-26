// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings backup file (Slava 2026-11-22: testers go back to stock and reinstall, then want their menus and Home
 * back): the quick menu, the customised All settings and Home's layout in one small JSON, saved on a USB drive or in
 * Downloads and read back after a reinstall. Only the Settings.Secure values named in {@link #KEYS} ever travel; a part
 * that is null in the file means "as it was by default" (nothing stored). Pure; fail closed: a file that is not one,
 * is of a newer version, too big or carries a part that is not text is refused as a whole.
 */
public final class BackupFile {
    public static final String NAME = "lumen-settings.json";
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 256 * 1024, MAX_PART = 64 * 1024;
    /** The quick menu (lumen-menu QuickLayoutJson), All settings (lumen-menu FullLayout), Home ({@link HomeLayout}). */
    public static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
            "lumen_quick_menu", "lumen_full_menu", HomeLayout.KEY));

    private BackupFile() {}

    /** A read backup: when it was made (ms), the model it came from, its parts (key to value, null = default). */
    public static final class Backup {
        public final long made;
        public final String model;
        public final Map<String, String> parts;

        Backup(long made, String model, Map<String, String> parts) {
            this.made = made;
            this.model = model;
            this.parts = Collections.unmodifiableMap(parts);
        }
    }

    /** The file text for the current values ({@code values}: key to value, a missing key or null = default). */
    public static String write(Map<String, String> values, String model, long made) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lumen_backup", (long) VERSION);
        m.put("made", made);
        m.put("model", model == null ? "" : model);
        Map<String, Object> parts = new LinkedHashMap<>();
        for (String k : KEYS) {
            String v = values.get(k);
            parts.put(k, v == null || v.length() > MAX_PART ? null : v);
        }
        m.put("parts", parts);
        return Json.write(m);
    }

    /** The backup in {@code text}, or null when it is not a Lumen settings backup this Home can read. */
    public static Backup read(String text) {
        if (text == null || text.length() > MAX_BYTES) return null;
        Object o;
        try {
            o = Json.parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(o instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) o;
        long v = Json.num(m, "lumen_backup", -1);
        if (v < 1 || v > VERSION || !(m.get("parts") instanceof Map)) return null;
        Map<?, ?> pm = (Map<?, ?>) m.get("parts");
        Map<String, String> parts = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : pm.entrySet()) {
            String k = String.valueOf(e.getKey());
            Object val = e.getValue();
            if (!KEYS.contains(k)) continue;                      // never a key we do not own
            if (val != null && !(val instanceof String)) return null;
            if (val != null && ((String) val).length() > MAX_PART) return null;
            parts.put(k, (String) val);
        }
        if (parts.isEmpty()) return null;
        return new Backup(Math.max(0, Json.num(m, "made", 0)), Json.str(m, "model", ""), parts);
    }
}
