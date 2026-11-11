// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * The menu of a profile as text: every tile, every row with its options, hint pictures,
 * Boost, keys and the game menu. test/run.sh compares it with test/snapshots/&lt;codename&gt;.txt for every
 * devices/&lt;codename&gt;/menu.json, so a profile change shows up as a reviewed text diff.
 *
 *   java org.lumen.menu.MenuSnapshot devices/&lt;c&gt;/menu.json      prints the snapshot (problems: exit 1)
 */
public final class MenuSnapshot {
    private MenuSnapshot() {}

    public static String render(MenuProfile p) {
        StringBuilder b = new StringBuilder();
        b.append("device ").append(p.device()).append(" model ").append(p.model()).append('\n');
        if (!p.ok()) {
            b.append("INVALID PROFILE: no menu\n");
            for (String s : p.problems()) b.append("  problem: ").append(s).append('\n');
            return b.toString();
        }
        MenuSpec m = MenuSpec.of(p);
        b.append("card\n");
        StringBuilder r0 = new StringBuilder();
        for (MenuSpec.Tile t : m.round) r0.append(r0.length() == 0 ? "" : " | ").append(t);
        b.append("  top: ").append(r0).append('\n');
        int n = 1;
        for (List<MenuSpec.Tile> row : m.rows) {
            StringBuilder r = new StringBuilder();
            for (MenuSpec.Tile t : row) r.append(r.length() == 0 ? "" : " | ").append(t);
            b.append("  row ").append(n++).append(": ").append(r).append('\n');
        }
        if (p.has(Feature.LAMP)) {
            b.append("  slider: lamp ").append(p.number(Feature.LAMP, "min", 0)).append("..")
                    .append(p.number(Feature.LAMP, "max", 0));
            if (m.boostLevel() > 0) b.append(", Boost at ").append(m.boostLevel()).append(" (hint: ").append(p.boostSummary()).append(')');
            b.append('\n');
        }
        for (String sec : m.sections.keySet()) {
            b.append("section ").append(sec).append('\n');
            for (MenuSpec.Group g : m.sections.get(sec)) {
                if (g.header != null) b.append("  -- ").append(g.header).append('\n');
                for (String f : g.rows) {
                    b.append("  ").append(Feature.def(f).kind.name().toLowerCase()).append(' ').append(f);
                    List<String> o = p.options(f);
                    if (!o.isEmpty()) b.append(" ").append(o);
                    List<String> it = p.items(f);
                    if (!it.isEmpty()) b.append(" items ").append(it);
                    if (Feature.def(f).kind == Feature.Kind.SLIDER) {
                        b.append(" ").append(p.number(f, "min", 0)).append("..").append(p.number(f, "max", 0));
                    }
                    String h = p.hint(f);
                    if (h != null) b.append("  hint picture ").append(h);
                    b.append('\n');
                }
            }
        }
        b.append("all settings: ").append(String.join(" ", m.allSettings)).append('\n');
        b.append("keys: gear short ").append(p.gearShort()).append(", gear long ").append(p.gearLong()).append('\n');
        if (p.gameMenu()) {
            b.append("game menu: ").append(String.join(" ", p.gameItems()))
                    .append(p.gameGated().isEmpty() ? "" : " (shown once the device answers: " + String.join(" ", p.gameGated()) + ")")
                    .append('\n');
            if (p.gameSoundNamed()) b.append("  game sound: ").append(String.join(" ", p.gameSounds())).append('\n');
            b.append("  themes: ").append(String.join(" ", p.gameThemes())).append(", CEC detection ")
                    .append(p.cecDetect() ? "on" : "off").append('\n');
        } else {
            b.append("game menu: none\n");
        }
        if (!p.pending().isEmpty()) b.append("pending (not shown): ").append(String.join(" ", p.pending())).append('\n');
        return b.toString();
    }

    public static void main(String[] a) throws IOException {
        if (a.length != 1) {
            System.err.println("usage: MenuSnapshot devices/<codename>/menu.json");
            System.exit(2);
        }
        MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        System.out.print(render(p));
        System.exit(p.ok() ? 0 : 1);
    }
}
