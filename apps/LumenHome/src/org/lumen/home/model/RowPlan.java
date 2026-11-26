// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which rows the Витрина (Home A) shows and whether it is the calm screen. Pure. Everything sits on one screen, so
 * there are at most three rows: 1 or 2 "Continue watching" items become the first hero pages (never a lone card row),
 * 3 or more make the Continue row of the shelf; the app dock; the bottom line of input chips and the round buttons
 * All apps and Settings, always there. The calm screen (greeting and clock in the shelf) when there is nothing to watch
 * and no showcase (no network, no cache, no Watch Next). App channels and a "Popular" row are not shown: TMDB
 * trending lives in the hero pages only.
 */
public final class RowPlan {
    public static final int MAX_HERO = 5;
    /** Up to this many Continue items go to the hero; from one more on they make their own row. */
    public static final int MAX_HERO_CONTINUE = 2;

    public final boolean calm;
    /** Continue items shown as the first hero pages (0 when they have their own row). */
    public final int heroContinue;
    public final List<RowKind> rows;

    private RowPlan(boolean calm, int heroContinue, List<RowKind> rows) {
        this.calm = calm;
        this.heroContinue = heroContinue;
        this.rows = Collections.unmodifiableList(rows);
    }

    public static RowPlan of(int heroItems, int continueItems, int apps) {
        ArrayList<RowKind> rows = new ArrayList<>();
        boolean calm = heroItems == 0 && continueItems == 0;
        int heroContinue = calm || continueItems > MAX_HERO_CONTINUE ? 0 : continueItems;
        if (continueItems > MAX_HERO_CONTINUE) rows.add(RowKind.CONTINUE);
        if (apps > 0) rows.add(RowKind.APPS);
        rows.add(RowKind.INPUTS);
        return new RowPlan(calm, heroContinue, rows);
    }

    /** The shelf has a Continue row (3 items or more): no hero buttons, OK on a card resumes it. */
    public boolean continueRow() {
        return rows.get(0) == RowKind.CONTINUE;
    }

    /** The hero shows its buttons (Продолжить, Подробнее): a page, neither calm nor a Continue row. */
    public boolean heroButtons() {
        return !calm && !continueRow();
    }

    public int indexOf(RowKind k) {
        return rows.indexOf(k);
    }
}
