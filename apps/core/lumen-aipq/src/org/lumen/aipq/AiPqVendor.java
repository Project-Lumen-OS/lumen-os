// SPDX-License-Identifier: Apache-2.0
package org.lumen.aipq;

/**
 * The vendor calls the stock AI picture quality makes (XRMService utils/AIPQ.java), one implementation per projector
 * over its own HAL client. Every HAL method is one stock getter or setter of window 0; called on the app's HAL thread
 * only. A failed call throws: AiPq then does what stock does when the HAL answers nothing (no change).
 */
public interface AiPqVendor {
    /** GmTvManager.getPictureMode(0). */
    int pictureMode() throws Exception;

    /** GmTvManager.setPictureMode(0, mode). */
    void setPictureMode(int mode) throws Exception;

    /** GmTvManager.getHdrType(). */
    int hdrType() throws Exception;

    /** GmTvManager.getHdrVividState(getCurrentInputSource(), pictureMode) == EN_HDR_VIVID_STATE_AUTO (1). */
    boolean hdrVividAuto(int pictureMode) throws Exception;

    /** GmTvManager.isHdrVividPlaying(). */
    boolean hdrVividPlaying() throws Exception;

    /** GmTvManager.isImaxVideoPlaying(). */
    boolean imaxPlaying() throws Exception;

    /** DisplayManager.getCurrent3DFormat() (EN_XGIMI_3D_NONE = 1). */
    int current3DFormat() throws Exception;

    /** DisplayManager.getDlpLumensMode(). */
    int lumensMode() throws Exception;

    /** DisplayManager.isVideoPlaying(). */
    boolean videoPlaying() throws Exception;

    /**
     * SystemProperties multi window switch (stock MultiWindowConstants.PROP_MULTIWINDOW_ENABLE, sys.mw.enable). The
     * Android side, the same on every model: AiPqDriver answers it, a HAL implementation leaves the default.
     */
    default boolean multiWindow() { return false; }

    /** The package of the top activity (stock Utils.getTopActivityPackageName). AiPqDriver answers it, as multiWindow. */
    default String topPackage() { return ""; }
}
