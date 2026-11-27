// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * EarcRules: the decisions of cec/EarcLink (eARC the stock way, devices/goat_elegant/stock analysis), kept free of
 * Android classes so test/earc/run.sh checks them on the host. Same vendor flow on the Z9X and the Z9X HB.
 */
package org.lumen.projector.cec;

public final class EarcRules {
    /** getEARCStatus (Z9X IGmpf 619, HB TV 79): vendor EN_CEC_EarcStatus, 3 = eARC linked (stock XRMService "== 3"). */
    public static final int EARC_LINKED = 3;

    private EarcRules() {}

    /**
     * Stock (XRMService eARCstate -> AudioService.seteARCStatus, HdmiCecLocalDeviceTv "don't disconnect ARC while eARC
     * mode"): the HDMI ARC output stays connected while eARC is linked. ours = EarcLink connected it; arcPresent = an
     * HDMI ARC output is listed now (CEC ARC's, or ours). The framework removes CEC ARC's output on the soundbar's
     * &lt;Terminate ARC&gt; or on "Speaker", so it is connected again while the link holds; an output that is there
     * (with CEC ARC's descriptors) is never touched.
     */
    public static EarcStep step(int status, boolean ours, boolean arcPresent) {
        if (status == EARC_LINKED) return arcPresent ? EarcStep.NONE : EarcStep.CONNECT;
        return ours ? EarcStep.DISCONNECT : EarcStep.NONE;
    }

    /**
     * {@link #step(int, boolean, boolean)} with the projector's state: asleep or going to sleep ({@code awake} false:
     * PowerManager not interactive) no HDMI ARC output is connected. The framework ends ARC for its standby (ARC
     * termination, the output removed) and the stock framework keeps its output while eARC is linked, so stock adds
     * nothing then; connecting one after {@code <Standby>} made the vendor switch the sound path to ARC again (X50 live
     * 2026-11-26 14:10:04.94, test/earc). The watch at screen on connects it again.
     */
    public static EarcStep step(int status, boolean ours, boolean arcPresent, boolean awake) {
        EarcStep s = step(status, ours, arcPresent);
        return s == EarcStep.CONNECT && !awake ? EarcStep.NONE : s;
    }

    /**
     * The vendor's TV event 11 (stock: the TV service's signal receiver turns "eARCstate:1" into seteARCStatus(true),
     * any other value into false) as a link status: {@link #EARC_LINKED} or 0; -1 = not an eARC event.
     */
    public static int eventStatus(int type, String msg) {
        if (type != EVENT_EARC || msg == null) return -1;
        String[] f = msg.split(":");
        if (f.length < 2 || !"eARCstate".equals(f[0].trim())) return -1;
        return "1".equals(f[1].trim()) ? EARC_LINKED : 0;
    }

    /** The vendor's TV event type of an eARC link change ("eARCstate:1|0"). */
    public static final int EVENT_EARC = 11;

    /**
     * The link ended and this module had connected the HDMI ARC output. Stock AudioService.seteARCStatus(false)
     * removes it only while CEC ARC is not established; an app cannot read that state, so a System Audio mode that is
     * on (the soundbar's CEC ARC session) keeps the output. A new base with the framework part (sys.lumen.vendor_earc_glue)
     * decides this itself and the app never touches the output.
     */
    public static EarcStep afterLinkEnd(EarcStep s, boolean systemAudioOn) {
        return s == EarcStep.DISCONNECT && systemAudioOn ? EarcStep.NONE : s;
    }

    /**
     * The digital output format goes to the HAL again when the eARC link comes up or goes down (stock: XRMService event 11
     * -> AudioService.seteARCStatus -> an ARC output change -> libxgimiaudiopolicy sends the format with the path switch.
     * The vendor resets and updates its Aout on that change, "Update Aout and EDID when eARC connection status change",
     * while the HDMI ARC output Android lists may stay as it is, so no path switch tells the app). before = the last
     * read, -1 none yet (the first read of a process sends nothing: the boot send covers it).
     */
    public static boolean formatAgain(int before, int now) {
        if (before < 0 || before == now) return false;
        return before == EARC_LINKED || now == EARC_LINKED;
    }
}
