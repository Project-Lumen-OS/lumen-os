// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import org.lumen.home.model.RowPlan;

import java.util.ArrayList;
import java.util.List;

/** Everything one Home screen shows, built on the io thread by {@link HomeLoader}. */
public final class HomeData {
    /** Visible apps in the user's order (Card.APP). */
    public final List<Card> apps = new ArrayList<>();
    /** The top level of the dock and the Apps page: apps and folders (Card.FOLDER) in the user's order. */
    public final List<Card> dock = new ArrayList<>();
    /** Apps the user hid (to show again). */
    public final List<Card> hidden = new ArrayList<>();
    /** "Continue watching": one per series, texts ready (meta = season line, left = status line, desc). */
    public final List<Card> cont = new ArrayList<>();
    /** Showcase items (TMDB trending, up to 5): title, meta, desc, image = backdrop. */
    public final List<Card> hero = new ArrayList<>();
    /** Inputs for the source menu and the calm screen: HDMI ports, screen mirroring, USB drives. */
    public final List<Card> inputs = new ArrayList<>();
    public boolean online;
    public RowPlan plan;

    /** True when Continue watching has the card {@code id}. */
    public boolean hasContinue(String id) {
        for (Card k : cont) if (k.id.equals(id)) return true;
        return false;
    }

    /** The same screen without the Continue card {@code id} (Delete in its menu); the plan stays the one shown. */
    public HomeData withoutContinue(String id) {
        HomeData d = copy(plan);
        d.cont.removeIf(k -> k.id.equals(id));
        return d;
    }

    /** The plan is the one the counts call for (a Continue row deleted down to the hero's share is not). */
    public boolean planned() {
        RowPlan p = RowPlan.of(hero.size(), cont.size(), dock.size());
        return plan != null && plan.calm == p.calm && plan.heroContinue == p.heroContinue && plan.rows.equals(p.rows);
    }

    /** The same cards laid out by the plan their counts call for. */
    public HomeData replanned() {
        return copy(RowPlan.of(hero.size(), cont.size(), dock.size()));
    }

    private HomeData copy(RowPlan p) {
        HomeData d = new HomeData();
        d.apps.addAll(apps);
        d.dock.addAll(dock);
        d.hidden.addAll(hidden);
        d.cont.addAll(cont);
        d.hero.addAll(hero);
        d.inputs.addAll(inputs);
        d.online = online;
        d.plan = p;
        return d;
    }
}
