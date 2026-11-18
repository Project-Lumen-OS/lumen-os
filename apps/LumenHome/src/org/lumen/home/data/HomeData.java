package org.lumen.home.data;

import org.lumen.home.model.RowPlan;

import java.util.ArrayList;
import java.util.List;

/** Everything one Home screen shows, built on the io thread by {@link HomeLoader}. */
public final class HomeData {
    /** Visible apps in the user's order (Card.APP). */
    public final List<Card> apps = new ArrayList<>();
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
}
