package org.lumen.home.model;

import org.lumen.home.data.Card;

import java.util.HashMap;

/**
 * Binds the hero only when what it shows changes. A focus move re-asks for the hero on every key press; the same card
 * (same app, title, season line, texts, page) leaves the hero alone, so its texts, badge and fade never re-run. App
 * badges are kept per package: a card of a known app shows its badge at once, an unknown one keeps the badge slot
 * (empty, not removed) until the icon arrives, so the app name never shifts.
 *
 * @param <B> the badge image (Bitmap on the device, any object in host tests)
 */
public final class HeroBind<B> {
    /** The hero view the binder drives. */
    public interface Target<B> {
        void item(Card k, boolean buttons, int page, int pages);

        void feature(Card k, int page, int pages);

        /** The badge: an image, an empty reserved slot ({@code b == null && slot}) or none at all. */
        void badge(B b, boolean slot);

        void loadBadge(String pkg);
    }

    private final Target<B> mTarget;
    private final HashMap<String, B> mBadges = new HashMap<>();
    private String mShown, mPkg;

    public HeroBind(Target<B> target) {
        mTarget = target;
    }

    /** A Continue item; true when the hero changed. */
    public boolean item(Card k, boolean buttons, int page, int pages) {
        String key = key('i', k, buttons, page, pages) + '\u0001' + k.left + '\u0001' + k.desc + '\u0001' + k.progress;
        if (key.equals(mShown)) return false;
        mShown = key;
        boolean slot = k.pkg != null && !k.pkg.isEmpty();
        mPkg = slot ? k.pkg : null;
        mTarget.item(k, buttons, page, pages);
        B b = slot ? mBadges.get(k.pkg) : null;
        mTarget.badge(b, slot);
        if (slot && b == null) mTarget.loadBadge(k.pkg);
        return true;
    }

    /** A showcase page (no app line); true when the hero changed. */
    public boolean feature(Card k, int page, int pages) {
        String key = key('f', k, true, page, pages) + '\u0001' + k.desc + '\u0001' + k.watchLabel;
        if (key.equals(mShown)) return false;
        mShown = key;
        mPkg = null;
        mTarget.feature(k, page, pages);
        mTarget.badge(null, false);
        return true;
    }

    /** An icon arrived: kept for later, shown when its app is still on the hero (a missing icon keeps the slot). */
    public void badgeLoaded(String pkg, B b) {
        if (b == null) return;
        mBadges.put(pkg, b);
        if (pkg.equals(mPkg)) mTarget.badge(b, true);
    }

    private static String key(char kind, Card k, boolean buttons, int page, int pages) {
        return kind + (buttons ? "b" : "c") + page + '/' + pages + '\u0001' + k.pkg + '\u0001' + k.appLabel + '\u0001'
                + k.title + '\u0001' + k.meta;
    }
}
