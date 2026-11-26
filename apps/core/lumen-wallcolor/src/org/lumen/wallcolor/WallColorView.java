// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.wallcolor;

/**
 * The screens of the flow (WallColorWindows on a projector, a recorder in the host tests). Every call comes on the
 * main thread; the flow keeps the state, the view only draws.
 */
public interface WallColorView {
    /** Stock SystemUtils.sendCloseSystemDialogBroadcast: menus and other system dialogs close. */
    void closeSystemDialogs();

    /** The menu card: title, the model's description, Start and Reset. */
    void showIntro();

    void hideIntro();

    /** The full screen pattern: the grey disc grows from the centre, then the measured pattern loops at {@code alpha}. */
    void showPattern(float alpha);

    void patternAlpha(float alpha);

    /** Closes the pattern: {@code fade} = the stock 500 ms fade, else at once. */
    void closePattern(boolean fade);

    /** The bar at the bottom: matched colour (focused first) and original colour. */
    void showResult();

    void hideResult();

    /** The question on BACK over the bar: keep the matched colour or go back to the original. */
    void showSaveAsk();

    void hideSaveAsk();

    void toast(WallColorMessage m);
}
