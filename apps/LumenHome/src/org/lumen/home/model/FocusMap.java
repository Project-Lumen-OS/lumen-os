package org.lumen.home.model;

/**
 * The D-pad map of the Витрина (Home A). Pure: the activity feeds keys, reads the zone and indexes back and moves the
 * views. Zones: the hero buttons (Продолжить, Подробнее) and the rows (the Continue row with 3 items or more, the app
 * dock, the bottom line of input chips and round buttons). There is no header: UP from the hero does nothing.
 * LEFT on a row's first item and RIGHT on its last do nothing (no jump into another row); LEFT on the first hero
 * button and RIGHT on the last page the hero; DOWN and UP between rows land on the item nearest on screen; BACK goes
 * from a row to the hero in one press (without hero buttons, to the first item of the first row).
 */
public final class FocusMap {
    public static final int HERO = 1, ROWS = 2;
    /** The two pages: Home and All apps. */
    public static final int H_HOME = 0, H_APPS = 1;
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3, BACK = 4;
    /** move() results. */
    public static final int NONE = 0, MOVED = 1, HERO_PREV = 2, HERO_NEXT = 3;
    /** The rows' left edge (the grid margin). */
    public static final int LEFT_X = org.lumen.ui.Grid.MARGIN;

    /** One row: a sliding strip of equal items, or fixed items at their own centres (the bottom line). */
    public static final class Row {
        public final int count, itemW, gap;
        private final int[] mCenters;

        public Row(int count, int itemW, int gap) {
            this.count = count;
            this.itemW = itemW;
            this.gap = gap;
            mCenters = null;
        }

        /** Fixed items (never slide) at these centres (design px). */
        public Row(int[] centers) {
            count = centers.length;
            itemW = 0;
            gap = 0;
            mCenters = centers.clone();
        }

        /** Items fully on screen when the row slides. */
        public int visible() {
            return mCenters != null ? count : Vitrina.visible(itemW, gap);
        }

        /** Screen x (design px) of an item's centre with {@code first} at the left edge. */
        public float centerX(int index, int first) {
            if (mCenters != null) return mCenters[index];
            return LEFT_X + (index - first) * (float) (itemW + gap) + itemW / 2f;
        }
    }

    public int zone = HERO, heroBtn, row;
    /** The page on screen (H_HOME or H_APPS). */
    public int page = H_HOME;
    private int mHeroButtons;
    private Row[] mRows = new Row[0];
    private int[] mItem = new int[0], mFirst = new int[0];

    /** New content: indexes are kept where they still exist, the zone moves when its content is gone. */
    public void setLayout(int heroButtons, Row[] rows) {
        mHeroButtons = Math.max(0, heroButtons);
        int[] old = mItem, oldFirst = mFirst;
        mRows = rows == null ? new Row[0] : rows;
        mItem = new int[mRows.length];
        mFirst = new int[mRows.length];
        for (int r = 0; r < mRows.length; r++) {
            int prev = r < old.length ? old[r] : 0;
            mItem[r] = Math.max(0, Math.min(prev, mRows[r].count - 1));
            int f = r < oldFirst.length ? oldFirst[r] : 0;
            mFirst[r] = Vitrina.scroll(f, mItem[r], mRows[r].count, mRows[r].visible());
        }
        heroBtn = Math.max(0, Math.min(heroBtn, mHeroButtons - 1));
        if (zone == ROWS && mRows.length == 0) zone = HERO;
        if (zone == HERO && mHeroButtons == 0 && mRows.length > 0) zone = ROWS;
        row = Math.max(0, Math.min(row, mRows.length - 1));
    }

    public int rows() {
        return mRows.length;
    }

    public int heroButtons() {
        return mHeroButtons;
    }

    /**
     * What shows the focus, exactly one element at a time: the hero button in focus (-1 none), a row item, a tile
     * of the Apps page. The activity paints every element from these on each change, never only the old and the new.
     */
    public int heroFocus() {
        return page == H_HOME && zone == HERO && mHeroButtons > 0 ? heroBtn : -1;
    }

    public boolean itemFocused(int r, int i) {
        return page == H_HOME && zone == ROWS && r == row && r < mRows.length && i == mItem[r];
    }

    public boolean gridFocused(int i, int gridIndex) {
        return page == H_APPS && i == gridIndex;
    }

    public int item(int r) {
        return r >= 0 && r < mItem.length ? mItem[r] : 0;
    }

    /** The first item shown of row {@code r} (the strip slides by this many items). */
    public int first(int r) {
        return r >= 0 && r < mFirst.length ? mFirst[r] : 0;
    }

    /** Focus straight onto an item (a mouse click, a restored focus). */
    public void focusItem(int r, int i) {
        if (r < 0 || r >= mRows.length) return;
        zone = ROWS;
        row = r;
        set(r, i);
    }

    /** Where HOME lands: the first hero button, without one the first item of the first row. */
    public void home() {
        page = H_HOME;
        heroBtn = 0;
        if (mHeroButtons > 0) zone = HERO;
        else if (mRows.length > 0) {
            zone = ROWS;
            row = 0;
            set(0, 0);
        } else zone = HERO;
    }

    public int move(int key) {
        if (zone == HERO) return hero(key);
        if (mRows.length == 0) return NONE;
        return rows(key);
    }

    private void set(int r, int i) {
        Row x = mRows[r];
        mItem[r] = Math.max(0, Math.min(i, x.count - 1));
        mFirst[r] = Vitrina.scroll(mFirst[r], mItem[r], x.count, x.visible());
    }

    private int hero(int key) {
        switch (key) {
            case LEFT:
                if (heroBtn > 0) {
                    heroBtn--;
                    return MOVED;
                }
                return HERO_PREV;
            case RIGHT:
                if (heroBtn < mHeroButtons - 1) {
                    heroBtn++;
                    return MOVED;
                }
                return HERO_NEXT;
            case DOWN:
                if (mRows.length == 0) return NONE;
                zone = ROWS;
                row = 0;
                return MOVED;
            default:
                return NONE;
        }
    }

    private int rows(int key) {
        Row r = mRows[row];
        switch (key) {
            case LEFT:
                if (mItem[row] == 0) return NONE;
                set(row, mItem[row] - 1);
                return MOVED;
            case RIGHT:
                if (mItem[row] >= r.count - 1) return NONE;
                set(row, mItem[row] + 1);
                return MOVED;
            case DOWN:
                if (row >= mRows.length - 1) return NONE;
                jump(row + 1);
                return MOVED;
            case UP:
                if (row > 0) {
                    jump(row - 1);
                    return MOVED;
                }
                if (mHeroButtons == 0) return NONE;
                zone = HERO;
                heroBtn = 0;
                return MOVED;
            case BACK:
                if (mHeroButtons > 0) {
                    zone = HERO;
                    heroBtn = 0;
                    return MOVED;
                }
                if (row == 0 && mItem[0] == 0) return NONE;
                row = 0;
                set(0, 0);
                return MOVED;
            default:
                return NONE;
        }
    }

    /** To row {@code to}, on the item whose centre is nearest the focused one's (with each row's own slide). */
    private void jump(int to) {
        float x = mRows[row].centerX(mItem[row], mFirst[row]);
        Row t = mRows[to];
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < t.count; i++) {
            float d = Math.abs(t.centerX(i, mFirst[to]) - x);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        row = to;
        set(to, best);
    }

    /**
     * The Apps page grid: the new index, or -1 when UP or BACK leaves it. RIGHT on the last of a line and LEFT on the
     * first stay; DOWN into a shorter last line lands on its last item.
     */
    public static int grid(int index, int count, int cols, int key) {
        if (count <= 0) return -1;
        switch (key) {
            case UP:
                return index - cols >= 0 ? index - cols : -1;
            case DOWN:
                if (index + cols < count) return index + cols;
                return index / cols < (count - 1) / cols ? count - 1 : index;
            case LEFT:
                return index % cols > 0 ? index - 1 : index;
            case RIGHT:
                return index % cols < cols - 1 && index + 1 < count ? index + 1 : index;
            default:
                return index;
        }
    }
}
