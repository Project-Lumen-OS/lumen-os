// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.wallcolor;

/**
 * The stock wall colour adaptation as one state machine on the main thread (stock newsettings BrightnessViewModel
 * brightnessWallColorCalibration, SystemUI WallColorCore + OverWindowV2, FocusUIV2 screen on/off and HOME):
 * <ol>
 * <li>the menu card: Start = setAEOnOff(false), then startAutoColor(); Reset = setAutoColorDefault();</li>
 * <li>event 11001 show / show_detect: the pattern (already up: only its opacity); close: the pattern fades (100 ms
 * later when the result bar is already up);</li>
 * <li>result true code 0: the bar, focus on the matched colour; focusing a choice applies it at once (matched =
 * setAutoColorCalibration, original = setAutoColorDefault), OK closes the bar with the focused one kept;</li>
 * <li>BACK on the bar asks to keep the result (matched or original); HOME, screen off and screen on keep the matched
 * colour and close everything;</li>
 * <li>result false: the stock text of the code (code 100 closes everything); true with code 1 or 100: nothing.</li>
 * </ol>
 * No other vendor call is ever made (WallColorStep).
 */
public final class WallColorFlow {
    /** Stock WallColorCore.core close: the pattern waits 100 ms when the result window is already up. */
    public static final long CLOSE_AFTER_RESULT_MS = 100;
    /** Stock result codes: 0 matched, 1 nothing to adapt, 100 cancelled by a mutex. */
    static final int CODE_MATCHED = 0, CODE_NOT_NEEDED = 1, CODE_MUTEX = 100;

    /** Posts work to the main thread later. */
    public interface Delay {
        void post(Runnable r, long ms);
    }

    private final WallColorHal hal;
    private final WallColorView view;
    private final Delay delay;
    private boolean intro, pattern, result, saveAsk, screenOn = true;

    public WallColorFlow(WallColorHal hal, WallColorView view, Delay delay) {
        this.hal = hal;
        this.view = view;
        this.delay = delay;
    }

    /** The menu row: the card with Start and Reset. */
    public void open() {
        intro = true;
        view.showIntro();
    }

    /** Start on the card: auto exposure off, then the vendor's start (stock order, the AE answer is not judged). */
    public void start() {
        closeIntro();
        hal.call(WallColorStep.AE_OFF, aeOk -> hal.call(WallColorStep.START, started -> {
            if (!started) view.toast(WallColorMessage.NOT_STARTED);
        }));
    }

    /** Reset on the card: back to the original colour. */
    public void reset() {
        closeIntro();
        hal.call(WallColorStep.USE_ORIGINAL, ok -> { });
    }

    /** BACK on the card. */
    public void back() {
        closeIntro();
    }

    public void onEvent(WallColorEvent e) {
        if (e == null) return;
        switch (e.cmd) {
            case WallColorEvent.SHOW:
            case WallColorEvent.SHOW_DETECT:
                view.closeSystemDialogs();
                if (pattern) {
                    view.patternAlpha(e.alpha());
                } else {
                    pattern = true;
                    view.showPattern(e.alpha());
                }
                return;
            case WallColorEvent.CLOSE:
                if (!pattern) return;
                pattern = false;
                if (result || saveAsk) delay.post(() -> view.closePattern(true), CLOSE_AFTER_RESULT_MS);
                else view.closePattern(true);
                return;
            case WallColorEvent.RESULT:
                onResult(e);
                return;
            default:
        }
    }

    private void onResult(WallColorEvent e) {
        if (!e.status) {
            if (e.code == CODE_MUTEX) cancelAll();
            else view.toast(WallColorMessage.failure(e.code));
            return;
        }
        if (e.code == CODE_NOT_NEEDED || e.code == CODE_MUTEX) return;
        if (e.code != CODE_MATCHED) {
            view.toast(WallColorMessage.OUT_OF_RANGE);
            return;
        }
        view.closeSystemDialogs();
        if (!screenOn || result) return;
        result = true;
        view.showResult();
    }

    /** The matched colour got the focus on the bar (also when the bar opens). */
    public void focusMatched() {
        if (result) hal.call(WallColorStep.USE_MATCHED, ok -> { });
    }

    /** The original colour got the focus on the bar. */
    public void focusOriginal() {
        if (result) hal.call(WallColorStep.USE_ORIGINAL, ok -> { });
    }

    /** OK on the bar: it closes, the focused colour stays. */
    public void choose() {
        closeResult();
    }

    /** BACK on the bar: the save question. */
    public void backOnResult() {
        if (!result || saveAsk) return;
        saveAsk = true;
        view.showSaveAsk();
    }

    /** The answer of the save question: true = matched colour, false = original. */
    public void save(boolean matched) {
        closeResult();
        hal.call(matched ? WallColorStep.USE_MATCHED : WallColorStep.USE_ORIGINAL, ok -> { });
    }

    /** BACK on the save question: back to the bar. */
    public void backOnSaveAsk() {
        if (!saveAsk) return;
        saveAsk = false;
        view.hideSaveAsk();
    }

    /** HOME while the bar or the question is up: the matched colour stays (stock dialogCancel(homekey)). */
    public void home() {
        if (!result && !saveAsk) return;
        closeResult();
        hal.call(WallColorStep.USE_MATCHED, ok -> { });
    }

    /** Screen off (standby): everything closes (stock strScreenOff), the bar never opens while it is off. */
    public void screenOff() {
        screenOn = false;
        cancelAll();
    }

    public void screenOn() {
        screenOn = true;
        cancelAll();
    }

    /** True while any screen of the flow is up. */
    public boolean showing() {
        return intro || pattern || result || saveAsk;
    }

    /** Stock cancelMutex: the pattern at once, the bar; an open save question keeps the matched colour. */
    private void cancelAll() {
        if (pattern) {
            pattern = false;
            view.closePattern(false);
        }
        boolean asked = saveAsk;
        closeResult();
        if (asked) hal.call(WallColorStep.USE_MATCHED, ok -> { });
    }

    private void closeIntro() {
        if (!intro) return;
        intro = false;
        view.hideIntro();
    }

    private void closeResult() {
        if (saveAsk) {
            saveAsk = false;
            view.hideSaveAsk();
        }
        if (result) {
            result = false;
            view.hideResult();
        }
    }
}
