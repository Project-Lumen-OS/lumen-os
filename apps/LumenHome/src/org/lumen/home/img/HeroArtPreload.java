package org.lumen.home.img;

import android.graphics.Bitmap;

import org.lumen.home.data.Card;
import org.lumen.home.model.ArtRule;
import org.lumen.home.model.HeroWindow;

import java.util.Collections;
import java.util.List;

/**
 * Keeps every Витрина page ready before it is shown: each page's art is fetched to the disk cache (its size probed,
 * the next page first), the pages of {@link HeroWindow} are decoded at screen size into the loader's small hero LRU
 * together with their blurred copy. {@link #now} then hands the art to the backdrop at once, so a page change never
 * waits for the network or a decode. Main thread only.
 */
public final class HeroArtPreload {
    /** Art the backdrop can show right now. */
    public static final class Art {
        public final int mode, w, h;
        public final String uri;
        public final Bitmap sharp, blur;

        Art(int mode, String uri, Bitmap sharp, Bitmap blur, int w, int h) {
            this.mode = mode;
            this.uri = uri;
            this.sharp = sharp;
            this.blur = blur;
            this.w = w;
            this.h = h;
        }
    }

    private final ImageLoader mImg;
    private final int mScreenW, mScreenH, mBoxW, mBoxH;
    private List<Card> mPages = Collections.emptyList();
    private int mCurrent;

    /** {@code boxW x boxH}: the framed still's box (FRAME art is decoded at or below its own pixels inside it). */
    public HeroArtPreload(ImageLoader img, int screenW, int screenH, int boxW, int boxH) {
        mImg = img;
        mScreenW = screenW;
        mScreenH = screenH;
        mBoxW = boxW;
        mBoxH = boxH;
    }

    /** The art URIs of a hero card in the order {@link ArtRule} weighs them (published art first). */
    public static String[] uris(Card k, int screenW) {
        return k.kind == Card.FEATURE ? new String[] { ArtUrl.backdrop(k.image, screenW) }
                : new String[] { ArtUrl.backdrop(k.image, screenW), ArtUrl.backdrop(k.image2, screenW) };
    }

    /** New pages (a reload) with the one on screen. */
    public void pages(List<Card> pages, int current) {
        mPages = pages == null ? Collections.<Card>emptyList() : pages;
        moved(current);
    }

    /** The page on screen changed: fetch what is missing, decode the window around it. */
    public void moved(int current) {
        int n = mPages.size();
        if (n == 0) return;
        mCurrent = Math.floorMod(current, n);
        // the loader runs the newest request first: ask in reverse, so the next page comes first
        int[] fetch = HeroWindow.fetchOrder(mCurrent, n);
        for (int i = fetch.length - 1; i >= 0; i--) prepare(fetch[i]);
    }

    /** The art of {@code k} when every size is known and the picture is decoded, else null (also starts it). */
    public Art now(Card k) {
        if (k == null) return null;
        String[] uris = uris(k, mScreenW);
        int[] sizes = new int[uris.length * 2];
        for (int i = 0; i < uris.length; i++) {
            if (uris[i] == null || uris[i].isEmpty()) continue;
            int[] s = mImg.peekSize(uris[i]);
            if (s == null) return null;
            sizes[2 * i] = s[0];
            sizes[2 * i + 1] = s[1];
        }
        ArtRule.Pick p = ArtRule.pick(sizes, mBoxW, mBoxH);
        if (p.mode == ArtRule.NONE) return new Art(ArtRule.NONE, null, null, null, 0, 0);
        String uri = uris[p.index];
        boolean full = p.mode == ArtRule.FULL;
        Bitmap sharp = mImg.peekHero(uri, full ? mScreenW : p.w, full ? mScreenH : p.h);
        if (sharp == null) return null;
        return new Art(p.mode, uri, sharp, mImg.peekBlur(uri), p.w, p.h);
    }

    /** True when page {@code i}'s art can be shown without waiting. */
    public boolean ready(int i) {
        return i >= 0 && i < mPages.size() && now(mPages.get(i)) != null;
    }

    /** Probes every art of page {@code i} (downloads its master); in the window also decodes the picked one. */
    private void prepare(int i) {
        final Card k = mPages.get(i);
        final boolean decode = HeroWindow.keep(i, mCurrent, mPages.size(), HeroWindow.SLOTS);
        String[] uris = uris(k, mScreenW);
        final int[] left = { 0 };
        for (String u : uris) if (u != null && !u.isEmpty()) left[0]++;
        if (left[0] == 0) return;
        for (String u : uris) {
            if (u == null || u.isEmpty()) continue;
            if (mImg.peekSize(u) != null) {
                if (--left[0] == 0 && decode) decode(k);
                continue;
            }
            mImg.probe(u, k.pkg, (w, h) -> {
                if (--left[0] == 0 && decode) decode(k);
            });
        }
    }

    private void decode(Card k) {
        if (now(k) != null) return;
        String[] uris = uris(k, mScreenW);
        int[] sizes = new int[uris.length * 2];
        for (int i = 0; i < uris.length; i++) {
            int[] s = uris[i] == null || uris[i].isEmpty() ? null : mImg.peekSize(uris[i]);
            if (s == null) continue;
            sizes[2 * i] = s[0];
            sizes[2 * i + 1] = s[1];
        }
        ArtRule.Pick p = ArtRule.pick(sizes, mBoxW, mBoxH);
        if (p.mode == ArtRule.NONE) return;
        String uri = uris[p.index];
        boolean full = p.mode == ArtRule.FULL;
        mImg.loadBlur(uri, k.pkg, (b, c) -> { });
        mImg.loadHero(uri, full ? mScreenW : p.w, full ? mScreenH : p.h, k.pkg, (b, c) -> { });
    }
}
