package org.lumen.projector.panel;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Render harness library: string resources of an assembled res/ tree and the printed screen model. */
final class WalkLib {
    private WalkLib() {}

    /** All strings of res/values* by folder. */
    static final class Res {
        final Map<String, Map<String, String>> byDir = new LinkedHashMap<>();
        String locale = "values";

        Res(File res) throws Exception {
            File[] dirs = res.listFiles();
            if (dirs == null) throw new IllegalStateException("no res " + res);
            Pattern p = Pattern.compile("<string name=\"([^\"]+)\"[^>]*?(?:/>|>(.*?)</string>)", Pattern.DOTALL);
            for (File d : dirs) {
                if (!d.getName().startsWith("values")) continue;
                Map<String, String> m = byDir.computeIfAbsent(d.getName(), k -> new LinkedHashMap<>());
                File[] xs = d.listFiles();
                if (xs == null) continue;
                for (File x : xs) {
                    if (!x.getName().endsWith(".xml")) continue;
                    Matcher mm = p.matcher(new String(Files.readAllBytes(x.toPath()), StandardCharsets.UTF_8));
                    while (mm.find()) m.put(mm.group(1), mm.group(2) == null ? "" : mm.group(2));
                }
            }
        }

        List<String> locales() {
            List<String> l = new ArrayList<>();
            for (String d : byDir.keySet()) if (d.equals("values") || d.matches("values-[a-z]{2}(-r[A-Z]{2})?")) l.add(d);
            java.util.Collections.sort(l);
            return l;
        }

        String raw(String name) {
            Map<String, String> m = byDir.get(locale);
            String v = m == null ? null : m.get(name);
            if (v == null) v = byDir.get("values").get(name);
            if (v == null) return null;
            int guard = 0;
            while (v.startsWith("@string/") && guard++ < 5) v = raw(v.substring(8));
            return v;
        }

        String get(String name, Object... args) {
            if (name == null) return null;
            String v = raw(name);
            if (v == null) return "<missing " + name + ">";
            v = unescape(v);
            if (args.length > 0) {
                try { v = String.format(java.util.Locale.ROOT, v, args); } catch (Exception e) { v = v + " <format " + e.getClass().getSimpleName() + ">"; }
            }
            return v;
        }
    }

    static String unescape(String v) {
        v = v.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
        v = v.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&#8230;", "\u2026");
        return v;
    }

    /** One row of a page as drawn: kind, title, options, value, enabled, shown. */
    static final class Row {
        String kind, title, value, placeholder;
        List<String> opts;
        boolean enabled = true, visible = true;

        Row(String kind, String title) { this.kind = kind; this.title = title; }

        String line() {
            StringBuilder b = new StringBuilder(kind).append(' ').append(title == null ? "" : title.replace("\n", " / "));
            if (opts != null) b.append(" {").append(String.join("|", opts)).append('}');
            if (value != null) b.append(" = ").append(value);
            if (placeholder != null && value == null) b.append(" = (").append(placeholder).append(')');
            if (!enabled) b.append(" [disabled]");
            return b.toString();
        }
    }

    static void page(StringBuilder out, String head, List<Row> rows) {
        out.append(head).append('\n');
        for (Row r : rows) if (r.visible) out.append("  ").append(r.line()).append('\n');
    }
}
