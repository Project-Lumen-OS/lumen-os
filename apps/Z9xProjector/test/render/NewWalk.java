package org.lumen.projector.panel;

import org.lumen.menu.Feature;
import org.lumen.menu.MenuJson;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures.Confirm;
import org.lumen.menu.ProjectorFeatures.Note;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Render walk of the Z9X on the SHARED menu (lumen-menu MenuSpec / MenuProfile / MenuLabels tables + Z9xMenuRules):
 * every tile, popup, page row, option, value and note text, per locale and scenario, in the format of OldWalk.
 * Mirrors MenuController's build and apply rules (which rows exist, their kind, labels, visibility, enabled).
 * java NewWalk PROFILE MENULABELS_JAVA RES_DIR SCENARIOS PROBES
 */
public final class NewWalk {
    static Map<String, Map<String, String>> labels = new HashMap<>();
    static WalkLib.Res res;
    static MenuProfile prof;
    static MenuSpec spec;

    static String lab(String fn, String key) {
        Map<String, String> m = labels.get(fn);
        String v = m == null ? null : m.get(key);
        if (v == null && (fn.equals("page") || fn.equals("category"))) return lab("section", key);   // default: section(id)
        return v;
    }

    static String s(String name) { return name == null ? "" : res.get(name); }

    static void parseLabels(String src) {
        Matcher fm = Pattern.compile("static int (\\w+)\\(String[^)]*\\) \\{(.*?)\\n    \\}", Pattern.DOTALL).matcher(src);
        while (fm.find()) {
            Map<String, String> m = new HashMap<>();
            Matcher cm = Pattern.compile("case \"([^\"]+)\": return R\\.\\w+\\.(\\w+);").matcher(fm.group(2));
            while (cm.find()) m.put(cm.group(1), cm.group(2));
            labels.put(fm.group(1), m);
        }
        // boostSummary is a ternary in the source
        Matcher b = Pattern.compile("\"brighter\"\\.equals\\(token\\) \\? R\\.string\\.(\\w+) : R\\.string\\.(\\w+)").matcher(src);
        if (b.find()) { Map<String, String> m = new HashMap<>(); m.put("brighter", b.group(1)); m.put("paler", b.group(2)); labels.put("boostSummary", m); }
    }

    /** MenuController.INLINE_RADIOS (run.sh checks the source still says so). */
    static final java.util.Set<String> INLINE_RADIOS = java.util.Collections.singleton(Feature.PROJECTION_MODE);

    static Map<String, Object> env;
    static Z9xMenuRules.In in;
    static MenuState st;

    static boolean has(String f) {
        if (!prof.has(f)) return false;
        if (Feature.SETTINGS_LAUNCHER.equals(f)) return Boolean.TRUE.equals(env.get("launcher"));
        if (Feature.KEYSTONE_FIT_SCREEN.equals(f)) return Boolean.TRUE.equals(env.get("fit"));
        return Z9xFeatures_FEATURES_contains(f);
    }

    static java.util.Set<String> implemented;

    static boolean Z9xFeatures_FEATURES_contains(String f) { return implemented.contains(f); }

    static boolean switchChoice(String f) {
        return Feature.def(f).kind == Feature.Kind.CHOICE && prof.options(f).equals(Arrays.asList("off", "on"));
    }

    static String optionLabel(String f, String token) {
        String rt = st == null ? null : st.label(f, token);
        if (rt != null) return rt;
        String r = lab("option", f + "|" + token);
        return r == null ? token : s(r);
    }

    static List<String> shownOptions(String f) {
        return st == null || st.options(f) == null ? prof.options(f) : st.options(f);
    }

    /** One section page as MenuController.buildSection + apply draw it. */
    static List<WalkLib.Row> section(String sec) {
        List<WalkLib.Row> out = new ArrayList<>();
        boolean ok = st != null;
        String topNote = ok ? st.note(sec) : null;
        if (topNote != null) out.add(new WalkLib.Row("text", topNote));
        for (MenuSpec.Group g : spec.sections.get(sec)) {
            List<String> present = new ArrayList<>();
            for (String f : g.rows) if (has(f)) present.add(f);
            if (present.isEmpty()) continue;
            if (g.header != null) {
                String h = lab("header", g.header);
                WalkLib.Row hr = new WalkLib.Row("header", h != null ? s(h) : g.header.toUpperCase());
                if (ok) {
                    boolean any = false;
                    for (String f : present) any |= !st.hidden(f);
                    hr.visible = any;
                }
                out.add(hr);
            }
            for (String f : present) row(out, f);
        }
        if (MenuSpec.GENERAL.equals(sec)) out.add(new WalkLib.Row("text", s(Z9xMenuRules.FOOTER_GENERAL)));
        return out;
    }

    static void row(List<WalkLib.Row> out, String f) {
        Feature.Def d = Feature.def(f);
        boolean ok = st != null;
        if (Feature.BOOST.equals(f)) { out.add(new WalkLib.Row("text", s(lab("boostSummary", prof.boostSummary())))); return; }
        String title = s(lab("title", f));
        String val = ok ? st.get(f) : null;
        WalkLib.Row r = null;
        switch (d.kind) {
            case TOGGLE: {
                r = new WalkLib.Row("toggle", title);
                Boolean on = ok ? st.on(f) : null;
                r.value = on == null ? null : on ? "on" : "off";
                r.enabled = ok && !st.disabled(f) && on != null;
                break;
            }
            case CHOICE: {
                if (switchChoice(f)) {
                    r = new WalkLib.Row("toggle", title);
                    Boolean on = val == null ? null : "on".equals(val);
                    r.value = on == null ? null : on ? "on" : "off";
                    r.enabled = ok && !st.disabled(f) && on != null;
                    break;
                }
                if (INLINE_RADIOS.contains(f)) {
                    for (String o : prof.options(f)) {
                        WalkLib.Row cr = new WalkLib.Row("radio", optionLabel(f, o));
                        cr.value = val != null && o.equals(val) ? "[x]" : "[ ]";
                        cr.enabled = val != null;
                        if (ok && st.hidden(f)) cr.visible = false;
                        out.add(cr);
                    }
                    return;
                }
                r = new WalkLib.Row("choice", title);
                List<String> shown = shownOptions(f);
                r.opts = new ArrayList<>();
                for (String o : shown) r.opts.add(optionLabel(f, o));
                int i = val == null ? -1 : shown.indexOf(val);
                r.value = i >= 0 ? r.opts.get(i) : null;
                r.placeholder = ok ? st.placeholder(f) : null;
                r.enabled = ok && !st.disabled(f) && val != null;
                if (ok && st.hidden(f)) r.visible = false;
                out.add(r);
                String note = ok ? st.note(f) : null;
                if (note != null) out.add(new WalkLib.Row("text", note));
                summaryAndTip(out, f);
                return;
            }
            case SLIDER:
                if (Feature.PICTURE_PQ.equals(f)) {
                    for (String item : prof.items(f)) {
                        String id = f + "." + item;
                        WalkLib.Row sr = new WalkLib.Row("slider", s(lab("pq", item)));
                        Integer n = ok ? st.number(id) : null;
                        sr.value = n == null ? null : Integer.toString(n);
                        sr.enabled = ok && !st.disabled(id) && n != null;
                        if (ok && st.hidden(id)) sr.visible = false;
                        out.add(sr);
                    }
                    return;
                }
                if (Feature.LAMP.equals(f)) { out.add(new WalkLib.Row("text", s(lab("summary", f)))); return; }
                r = new WalkLib.Row("slider", title);
                break;
            case ACTION: r = new WalkLib.Row("action", title); break;
            case SCREEN:
                r = new WalkLib.Row("nav", title);
                r.value = val;
                r.enabled = st == null || !st.disabled(f);
                break;
            case INFO: r = new WalkLib.Row("info", title); r.value = val; break;
            default: return;
        }
        if (ok && st.hidden(f)) r.visible = false;
        out.add(r);
        summaryAndTip(out, f);
    }

    static void summaryAndTip(List<WalkLib.Row> out, String f) {
        String sum = lab("summary", f);
        if (sum != null) out.add(new WalkLib.Row("text", s(sum)));
        String h = prof.hint(f);
        if (h != null && lab("tipText", h) != null) out.add(new WalkLib.Row("hint", s(lab("tipText", h))));
    }

    static List<WalkLib.Row> radios(String f, String moreRes) {
        List<WalkLib.Row> out = new ArrayList<>();
        String cur = st == null ? null : st.get(f);
        List<String> opts = Feature.PICTURE_MODE.equals(f) ? shownOptions(f) : prof.options(f);
        for (String o : opts) {
            WalkLib.Row r = new WalkLib.Row("radio", optionLabel(f, o));
            r.value = o.equals(cur) ? "[x]" : "[ ]";
            out.add(r);
        }
        out.add(new WalkLib.Row("nav", s(moreRes)));
        String h = prof.hint(f);
        if (h != null && lab("tipText", h) != null) out.add(new WalkLib.Row("hint", s(lab("tipText", h))));
        return out;
    }

    static List<WalkLib.Row> gamePopup() {
        List<WalkLib.Row> full = section(MenuSpec.GAME), out = new ArrayList<>();
        String gm = st == null ? null : st.get(Feature.GAME_MODE);
        String modeTitle = s(lab("title", Feature.GAME_MODE));
        for (WalkLib.Row r : full) {
            if ("choice".equals(r.kind) && modeTitle.equals(r.title)) {
                for (String o : prof.options(Feature.GAME_MODE)) {
                    WalkLib.Row cr = new WalkLib.Row("radio", optionLabel(Feature.GAME_MODE, o));
                    cr.value = o.equals(gm) ? "[x]" : "[ ]";
                    cr.visible = r.visible;
                    out.add(cr);
                }
            } else {
                out.add(r);
            }
        }
        return out;
    }

    static String tileLine(String size, String id) {
        String l = lab("tile", id);
        String title = l != null ? s(l) : s(lab("title", id));
        String value = null;
        boolean ok = st != null;
        if (Feature.KEYSTONE_AK_NOW.equals(id)) value = s("lumen_qpd_autocorrect_value");
        else if (MenuSpec.PICTURE.equals(id)) {
            String m = ok ? st.get(Feature.PICTURE_MODE) : null;
            String over = ok ? st.tile(MenuSpec.PICTURE) : null;
            String tv = m == null ? null : lab("tileValue", Feature.PICTURE_MODE + "|" + m);
            value = over != null ? over : m == null ? null : tv != null ? s(tv) : optionLabel(Feature.PICTURE_MODE, m);
        } else if (Feature.INPUT.equals(id) || MenuSpec.GAME.equals(id)) value = ok ? st.tile(id) : null;
        String icon = MenuSpec.EYE.equals(id) && ok && "off".equals(st.get(Feature.EYE)) ? " (icon eye off)" : "";
        return size + " " + title + (value == null ? "" : " = " + value) + icon;
    }

    static String noteText(Note n, String f) {
        if (n == null) return null;
        if (n.res == null && n.text == null) return "(silent)";
        String text = n.text != null ? n.text.toString() : null;
        if (text == null) {
            Object[] a = new Object[n.args.length];
            for (int i = 0; i < a.length; i++) a[i] = n.args[i] instanceof String && ((String) n.args[i]).startsWith("@") ? s(((String) n.args[i]).substring(1)) : n.args[i];
            text = res.get(n.res, a);
        }
        String title = null;
        if (Note.ROW_TITLE.equals(n.title)) title = f.startsWith(Feature.PICTURE_PQ + ".") ? s(lab("pq", f.substring(11)))
                : lab("title", f) != null ? s(lab("title", f)) : s(lab("section", f));
        else if (n.title != null && n.title.startsWith("@")) title = s(n.title.substring(1));
        return (n.toast ? "toast " : "note ") + (title == null ? "" : title + " | ") + text;
    }

    public static void main(String[] a) throws Exception {
        prof = MenuProfile.parse(new String(Files.readAllBytes(new File(a[0]).toPath()), StandardCharsets.UTF_8));
        if (!prof.ok()) throw new IllegalStateException("profile: " + prof.problems());
        spec = MenuSpec.of(prof);
        implemented = Z9xFeatures_FEATURES();
        parseLabels(new String(Files.readAllBytes(new File(a[1]).toPath()), StandardCharsets.UTF_8));
        res = new WalkLib.Res(new File(a[2]));
        @SuppressWarnings("unchecked") List<Object> scen = (List<Object>) MenuJson.parse(new String(Files.readAllBytes(new File(a[3]).toPath()), StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked") List<Object> probes = (List<Object>) MenuJson.parse(new String(Files.readAllBytes(new File(a[4]).toPath()), StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (String loc : res.locales()) {
            res.locale = loc;
            for (Object so : scen) {
                @SuppressWarnings("unchecked") Map<String, Object> sc = (Map<String, Object>) so;
                @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) sc.get("env");
                env = e;
                in = toIn(sc.get("in"));
                st = Z9xMenuRules.state(in, (name, args) -> res.get(name, args));
                out.append("### ").append(loc).append(' ').append(sc.get("name")).append('\n');
                walk(out);
                for (Object po : probes) {
                    @SuppressWarnings("unchecked") List<Object> p = (List<Object>) po;
                    String f = (String) p.get(0), v = (String) p.get(1);
                    boolean present = f.startsWith(Feature.PICTURE_PQ + ".") ? has(Feature.PICTURE_PQ)
                            : Feature.known(f) ? has(f) : spec.hasSection(f);
                    if (!present) continue;
                    Note n = Z9xMenuRules.blocked(f, v, in);
                    String line;
                    if (n != null) line = noteText(n, f);
                    else {
                        Confirm c = Z9xMenuRules.confirm(f, v, in);
                        line = c == null ? "go" : "confirm " + s(c.title) + " | " + s(c.message) + " | " + s(c.ok);
                    }
                    out.append("probe ").append(f).append('=').append(v).append(": ").append(line).append('\n');
                }
            }
        }
        System.out.print(out);
    }

    static java.util.Set<String> Z9xFeatures_FEATURES() { return prof.features(); }

    static void walk(StringBuilder out) {
        out.append("card\n");
        for (MenuSpec.Tile t : spec.round) out.append("  ").append(tileLine("round", t.id)).append('\n');
        for (List<MenuSpec.Tile> row : spec.rows) {
            for (MenuSpec.Tile t : row) {
                String id = t.id;
                if (Feature.MIRRORING.equals(id) || Feature.USB_FILES.equals(id)) {
                    boolean mir = has(Feature.MIRRORING) && Boolean.TRUE.equals(env.get("mirroring"));
                    boolean usb = has(Feature.USB_FILES) && Boolean.TRUE.equals(env.get("usb"));
                    id = mir ? Feature.MIRRORING : usb ? Feature.USB_FILES : null;
                    if (id == null) continue;
                } else if (Feature.known(id) ? !has(id) : !spec.hasSection(id)) continue;
                out.append("  ").append(tileLine(MenuSpec.WIDE.equals(t.size) ? "wide" : "small", id)).append('\n');
            }
        }
        boolean boost = st != null && Boolean.TRUE.equals(st.on(Feature.BOOST));
        Integer lamp = st == null ? null : st.number(Feature.LAMP);
        out.append("  slider ").append(s(lab("title", Feature.LAMP))).append(" = ")
                .append(boost ? s("lumen_m_panel_boost") : lamp == null ? "-" : lamp.toString()).append('\n');
        WalkLib.page(out, "popup " + s("lumen_m_panel_picture_mode"), radios(Feature.PICTURE_MODE, "lumen_qpd_picture_more"));
        WalkLib.page(out, "popup " + s("lumen_m_panel_sound_mode"), radios(Feature.SOUND_MODE, "lumen_qpd_sound_more"));
        WalkLib.page(out, "popup " + s(lab("section", MenuSpec.GAME)), gamePopup());
        for (String sec : new String[]{MenuSpec.KEYSTONE, MenuSpec.FOCUS, MenuSpec.PICTURE, MenuSpec.SOUND, MenuSpec.PROJECTION,
                MenuSpec.GAME, MenuSpec.EYE, MenuSpec.GENERAL}) {
            WalkLib.page(out, "page " + s(lab("page", sec)), section(sec));
        }
        StringBuilder cats = new StringBuilder("all settings:");
        for (String c : spec.allSettings) cats.append(' ').append(s(lab("category", c))).append(',');
        out.append(cats).append('\n');
    }

    static Z9xMenuRules.In toIn(Object o) throws Exception {
        if (o == null) return null;
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        Z9xMenuRules.In in = new Z9xMenuRules.In();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Field f = Z9xMenuRules.In.class.getDeclaredField(e.getKey());
            f.setAccessible(true);
            Object v = e.getValue();
            Class<?> t = f.getType();
            if (e.getKey().equals("pq")) {
                @SuppressWarnings("unchecked") List<Object> l = (List<Object>) v;
                for (int i = 0; i < l.size(); i++) in.pq[i] = l.get(i) == null ? null : ((Long) l.get(i)).intValue();
            } else if (e.getKey().equals("outTokens") || e.getKey().equals("outLabels")) {
                @SuppressWarnings("unchecked") List<Object> l = (List<Object>) v;
                @SuppressWarnings("unchecked") List<String> dst = (List<String>) f.get(in);
                for (Object x : l) { String sx = (String) x; dst.add(sx.startsWith("@") ? s(sx.substring(1)) : sx); }
            } else if (t == Integer.class || t == int.class) {
                f.set(in, v == null ? null : ((Long) v).intValue());
            } else {
                f.set(in, v);
            }
        }
        return in;
    }
}
