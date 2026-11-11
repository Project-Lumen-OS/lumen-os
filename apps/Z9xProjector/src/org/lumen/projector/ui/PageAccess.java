package org.lumen.projector.ui;

import android.util.Log;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Z9X only (not a synced copy, not in apps/common/projector/FILES): lets the quick panel layout D
 * (org.lumen.projector.panel.QpdPanel), which keeps its own page stack beside the tile card, run the same page hooks
 * PagedPanel runs (Page.shown / hidden and the remembered focus row are package-private here), and read what a
 * shared row shows, so layout D can draw the rows in its own look (panel/QpdRowFrame) while the row objects keep
 * every key, action and vendor call. The synced rows have no getter for a few of these values (NavRow chevron,
 * ChoiceRow options and placeholder, SliderRow formatter, HeaderRow and TextRow text): they are read by field name,
 * and test/qpd/run.sh checks that every name below still exists in the synced sources. A read that fails gives the
 * safe default, never an exception.
 */
public final class PageAccess {
    private static final String TAG = "Z9xPanel";

    private PageAccess() {}

    public static void shown(Page p) { if (p != null) p.shown(); }

    public static void hidden(Page p) { if (p != null) p.hidden(); }

    public static int focusIndex(Page p) { return p == null ? -1 : p.focusIndex; }

    public static void setFocusIndex(Page p, int i) { if (p != null) p.focusIndex = i; }

    // ------------------------------------------------------------------ what a shared row shows
    /** NavRow: chevron shown (true when unknown). */
    public static boolean navChevron(NavRow r) {
        Object v = field(r, NavRow.class, "chevron");
        return !(v instanceof Boolean) || (Boolean) v;
    }

    /** ChoiceRow: the text between the arrows (the option or the placeholder); null while unknown. */
    public static CharSequence choiceLabel(ChoiceRow r) {
        CharSequence[] o = choiceOptions(r);
        int i = r.getSelected();
        if (i >= 0 && i < o.length) return o[i];
        Object p = field(r, ChoiceRow.class, "placeholder");
        return p instanceof CharSequence ? (CharSequence) p : null;
    }

    /** ChoiceRow: number of options (0 when unknown). */
    public static int choiceCount(ChoiceRow r) { return choiceOptions(r).length; }

    private static CharSequence[] choiceOptions(ChoiceRow r) {
        Object o = field(r, ChoiceRow.class, "options");
        return o instanceof CharSequence[] ? (CharSequence[]) o : new CharSequence[0];
    }

    /** SliderRow: the value text as the row draws it (its formatter or the number); null while unknown. */
    public static CharSequence sliderLabel(SliderRow r) {
        if (!r.isKnown()) return null;
        Object f = field(r, SliderRow.class, "formatter");
        if (f instanceof SliderRow.Formatter) {
            try {
                CharSequence t = ((SliderRow.Formatter) f).format(r.getValue());
                if (t != null) return t;
            } catch (Throwable t) {
                Log.w(TAG, "slider label: " + t);
            }
        }
        return Integer.toString(r.getValue());
    }

    public static CharSequence headerText(HeaderRow h) {
        Object v = field(h, HeaderRow.class, "text");
        return v instanceof CharSequence ? (CharSequence) v : "";
    }

    public static CharSequence text(TextRow t) {
        Object v = field(t, TextRow.class, "text");
        return v instanceof CharSequence ? (CharSequence) v : "";
    }

    /** Field names read above (test/qpd/run.sh greps the synced sources for each). */
    static final String[] FIELDS = {"NavRow.chevron", "ChoiceRow.options", "ChoiceRow.placeholder",
            "SliderRow.formatter", "HeaderRow.text", "TextRow.text"};

    private static final Map<String, Field> CACHE = new HashMap<>();

    private static Object field(Object o, Class<?> c, String name) {
        if (o == null) return null;
        try {
            Field f;
            synchronized (CACHE) {
                String k = c.getName() + "#" + name;
                f = CACHE.get(k);
                if (f == null) {
                    f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    CACHE.put(k, f);
                }
            }
            return f.get(o);
        } catch (Throwable t) {
            Log.w(TAG, "row field " + c.getSimpleName() + "." + name + ": " + t);
            return null;
        }
    }
}
