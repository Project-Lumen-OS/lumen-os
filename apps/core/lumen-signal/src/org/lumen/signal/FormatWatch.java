// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * When the panel asks the HDMI receiver again (HdmiRxBridge) and which answer it may show. 2026-11-18: the
 * values must follow the console live, also when only the colour format, depth or range changes (RGB to YUV 4:2:2 at
 * the same 3840x2160 60 Hz raises no vendor event and no new timing; the dump has no change flag either: the eARC
 * bCapChangeFlag / bStatChangeFlag are about eARC, the AVI checksum PB0 is not printed). So, only while the panel is
 * on screen:
 * <ul>
 *   <li>at once when it opens;</li>
 *   <li>{@link #SETTLE_MS} after the signal the HAL reports (size, rate, HDR kind, port) changed and stayed the same;</li>
 *   <li>every {@link SignalRefresh#periodMs} (Normal 5 s, Fast 1 s);</li>
 *   <li>never closer than {@link #GAP_MS} (the bridge refuses closer requests), never while hidden.</li>
 * </ul>
 * An answer is shown only when it came for the signal on screen now, for the HDMI port on screen (the model's port map)
 * and with the same size: a change, a failed read, another port or a late answer hides the rows at once, so a row never
 * shows an old value. Pure, host tested.
 */
public final class FormatWatch {
    public static final long SETTLE_MS = 1_000L;
    /** the bridge's own floor (image/lumen_hdmirx.sh MIN_GAP_CS 90) with a little margin */
    public static final long GAP_MS = 950L;

    private SignalRefresh refresh = SignalRefresh.NORMAL;
    /** the signal now (null = no HDMI input with a timing) and since when */
    private String key;
    private long since;
    /** the open request: its number and the signal it was asked for; 0 = none */
    private long asking, seq;
    private String askedFor;
    /** the last request time (-1 = none since the panel opened), the signal of the last answer */
    private long askedAt = -1;
    private String readFor;
    private HdmiRxInfo shown;

    public void refresh(SignalRefresh r) { refresh = r == null ? SignalRefresh.NORMAL : r; }

    public SignalRefresh refresh() { return refresh; }

    /** The panel opened or closed: forget everything, a late answer of before is dropped. */
    public void reset() {
        key = null;
        since = 0;
        asking = 0;
        askedFor = null;
        askedAt = -1;
        readFor = null;
        shown = null;
    }

    /** The signal on screen now ({@link #key}); a change drops the shown answer at once. */
    public void signal(String now, long t) {
        if (now == null ? key == null : now.equals(key)) return;
        key = now;
        since = t;
        shown = null;
    }

    /**
     * A request to send now, or 0. {@code visible}: the panel is on screen (hidden = never). The number goes back
     * with the answer ({@link #answer}).
     */
    public long due(boolean visible, long t) {
        if (!visible || key == null || asking != 0) return 0;
        if (askedAt >= 0 && t - askedAt < GAP_MS) return 0;
        boolean go;
        if (readFor == null && askedAt < 0) go = true;                       // the panel just opened
        else if (!key.equals(readFor)) go = t - since >= SETTLE_MS;           // a new signal, once it is stable
        else go = t - askedAt >= refresh.periodMs;                            // the same signal: the period
        if (!go) return 0;
        asking = ++seq;
        askedFor = key;
        askedAt = t;
        return asking;
    }

    /**
     * The bridge answered request {@code id} (null = failed). {@code wantPort}: the MStar port of the HDMI input on
     * screen (-1 = not in the model's map); {@code w}, {@code h}: the size the HAL reports now. True when the shown
     * value changed (draw).
     */
    public boolean answer(long id, HdmiRxInfo info, int wantPort, int w, int h) {
        if (id == 0 || id != asking) return false;                            // reset meanwhile or not ours
        asking = 0;
        readFor = askedFor;
        HdmiRxInfo next = info != null && info.usable() && wantPort >= 0 && info.port == wantPort
                && askedFor != null && askedFor.equals(key) && info.sameSize(w, h) ? info : null;
        boolean changed = !same(shown, next);
        shown = next;
        return changed;
    }

    /** What the panel may show now, null = hide the rows. */
    public HdmiRxInfo shown() { return shown; }

    /** The signal key of {@code v}: port, size, rate, scan, HDR kind; null without an HDMI input and a timing. */
    public static String key(SignalValues v) {
        if (v == null || v.port <= 0 || !v.hasInput()) return null;
        return v.port + ":" + v.width + "x" + v.height + (Boolean.TRUE.equals(v.interlaced) ? "i" : "p") + "@"
                + v.rateCenti + "/" + v.hdrType;
    }

    private static boolean same(HdmiRxInfo a, HdmiRxInfo b) {
        return a == null ? b == null : b != null && a.toString().equals(b.toString());
    }
}
