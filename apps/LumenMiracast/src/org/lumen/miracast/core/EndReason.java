// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/** Which diagnostics stage a finished session failed at, from how it ended (host tested). */
public final class EndReason {
    private EndReason() {}

    /**
     * @param why      the end reason (user, system or connection)
     * @param userEnd  ended from our side: BACK, left the screen, standby, switched off, group gone
     * @param playing  the sender accepted PLAY
     * @param media    at least one RTP packet arrived
     * @param frame    a picture was shown
     * @return the stage to mark FAIL, or null for a normal end
     */
    public static Diag.Stage failedStage(String why, boolean userEnd, boolean playing, boolean media, boolean frame) {
        String w = why == null ? "" : why;
        if (playing) {
            // after PLAY the picture is what counts, however it ended (a BACK after 10 black seconds too)
            if (!media) return Diag.Stage.MEDIA;
            if (!frame) return Diag.Stage.FIRST_FRAME;
            return null;
        }
        if (userEnd) return w.contains("group gone") ? Diag.Stage.GROUP : null;
        if (w.startsWith("cancelled")) return null;
        if (w.startsWith("source not found")) return Diag.Stage.SOURCE_IP;
        if (w.startsWith("connect failed")) return Diag.Stage.RTSP_TCP;
        if (w.startsWith("no free RTP port")) return Diag.Stage.MEDIA;
        return Diag.Stage.RTSP;
    }
}
