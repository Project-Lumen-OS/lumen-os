// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

/**
 * The HAL steps of the digital zoom and picture shift card (Play6ZoomShiftPanel), one implementation per HAL family
 * that has the card (play6-hidl panel/Play6PanelIo.zoom), called on that family's HAL thread. The card brackets its
 * reads and steps with pushPullOn / pauseRealtime / zoomStart and close; zoomStepRange reads {lo, hi} (117),
 * zoomStep {digital, optical, cur} (116), setDigitalZoomStep writes 118, shift moves the picture one step (141).
 */
interface ZoomIo {
    /** The digital zoom steps the HAL accepts (its range check of 118). */
    int STEP_MIN = -200, STEP_MAX = 200;

    boolean pushPullOn(String who);

    boolean pauseRealtime(String who);

    void zoomStart(String who) throws Exception;

    void zoomRestart(String who) throws Exception;

    boolean close(String who);

    int[] zoomStepRange() throws Exception;

    int[] zoomStep() throws Exception;

    int setDigitalZoomStep(int step) throws Exception;

    int shift(ShiftMove dir, boolean check) throws Exception;

    /** The keystone reset to the full frame (207(0), 130); returns what 130 reads. */
    int resetKeystone() throws Exception;

    /** The HAL refused the call with an implausible reply: the card marks the model unsupported. */
    boolean replyRefused(Throwable t);
}
