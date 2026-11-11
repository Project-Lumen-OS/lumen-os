package org.lumen.ui;

/**
 * The colour tokens of the Lumen TV look (decision D3, 2026-11-08: one accent colour everywhere, #3B78E7, the
 * z9x_accent_strong token of apps/common/tokens.xml). ARGB ints, so code without resources (overlay windows, toasts,
 * dialogs styled at run time) uses the same values. The frozen Z9X apps keep their own copies of apps/common/tokens.xml
 * until the 1.1 seam.
 */
public final class Tokens {
    /** The accent: focused buttons and rows, progress, selection. */
    public static final int ACCENT = 0xFF3B78E7;
    /** The accent while pressed (0.82 of ACCENT per channel). */
    public static final int ACCENT_PRESSED = 0xFF3062BD;
    /** An unfocused button and its text. */
    public static final int IDLE = 0xFF262D38, IDLE_TEXT = 0xFFA9B2BF;
    /** A disabled button and its text. */
    public static final int OFF = 0xFF1A1E25, OFF_TEXT = 0xFF5D6570;
    /** Cards, dialogs, notices. */
    public static final int PANEL = 0xFF161A21;
    /** Text on a PANEL: titles and body. */
    public static final int TITLE = 0xFFE6E9EE, BODY = 0xFFB4BCC8;

    private Tokens() {}
}
