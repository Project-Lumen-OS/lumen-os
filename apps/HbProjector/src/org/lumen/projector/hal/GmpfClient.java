package org.lumen.projector.hal;

import android.os.RemoteException;

/**
 * GmpfClient of the Z9X HB (goat_elegant): the shared IGmpf client (GmpfBase) over the stable AIDL services of the
 * HB vendor (xgimi.hardware.gmpf V1, AidlCaller), with the allow-list Calls of HbCodes and the HB rules (HbRules:
 * the feature table HbFeatures, NotOnHbException for what the HB does not have), and the HB calls of its own: the
 * handshake connect, the boot animation row A, newAutoKst with the stock modes, the 81-point keystone parcelable, the
 * temporary switch reset, the stock HB keystone reset and the HDMI parcelable (5, 9).
 * Every argument is fixed or range-checked before anything is sent; REVIEW rows of banned.tsv stay refused.
 */
public final class GmpfClient extends GmpfBase<GmpfCall> {
    public static final String IGMPF = "xgimi.hardware.gmpf.IGmpf";
    public static final String IPROJECTOR_FOCUS_CALLBACK = "xgimi.hardware.gmpf.IProjectorFocusCallback";

    private static final int BOOT_ANIM_STATE_RUNNING = 1;    // libbootanimation "set boot anim running to gmpf!"
    /** AuxiliaryCurtainAlignDialog.RESET_TEMPORARY_SWITCH_AK = 0 (stock HB SystemUI, sent as (0, false) on dismiss). */
    private static final int RESET_TEMPORARY_SWITCH_AK = 0;
    /**
     * The only newAutoKst modes on the HB (allowed.tsv ProjectorFocusManager 53, 9.1): 9 newsettings autoKeystone,
     * 10 KeyStoneWind, 11 the curtain re-fit, 12 the stock curtain align hint window (2026-10-30, hb-t7).
     */
    static final int[] NEW_AUTO_KST_MODES = {9, 10, 11, 12};
    private static final int NEW_AUTO_KST_CURTAIN = 11;
    private static final int NEW_AUTO_KST_CURTAIN_ALIGN = 12;

    public GmpfClient(FocusEventSink sink) {
        super(new AidlWire(), HbCodes.CODES, HbRules.RULES);
        AidlCaller.setFocusSink(sink == null ? null : sink::onFocusEvent);
    }

    /**
     * checkService + linkToDeath + version guard of SYSTEM, POWER and FOCUS (the handshake interfaces). Does not wait.
     * True when all three are connected and verified. Never throws. The listener hears every later vendor death.
     */
    public boolean connect(DeathListener death) {
        return AidlCaller.connectHandshake(death == null ? null : death::onVendorDied);
    }

    /**
     * SYSTEM 32 setBootAnimState(1): HbBoot row A (stock BootAnimation constructor), only while the boot animation runs
     * and SYSTEM 33 is not 2 yet; the HB handshake checks both (HalController, its only caller).
     */
    public void setBootAnimRunning() throws RemoteException {
        wire.i32(GmpfCall.SET_BOOT_ANIM_STATE, BOOT_ANIM_STATE_RUNNING);
    }

    // ------------------------------------------------------------------ keystone
    /**
     * FOCUS 53 newAutoKst(mode) (2026-10-29, 9.1): only the stock modes of NEW_AUTO_KST_MODES; any other value is
     * refused and nothing is sent. 11 and 12 need CURTAIN_FIT ON, 9 and 10 AUTO_KEYSTONE. May block: keystone thread only.
     */
    public byte newAutoKst(AutoKstMode m) throws RemoteException {
        final int mode = checkedAutoKstMode(m);
        HbFeatures.require(mode == NEW_AUTO_KST_CURTAIN || mode == NEW_AUTO_KST_CURTAIN_ALIGN
                        ? HbFeatures.F.CURTAIN_FIT : HbFeatures.F.AUTO_KEYSTONE,
                "newAutoKst(" + mode + ")");
        return wire.i32RetI8(GmpfCall.NEW_AUTO_KST, mode);
    }

    /** The wire value of m when it is one of NEW_AUTO_KST_MODES; else IllegalArgumentException. */
    static int checkedAutoKstMode(AutoKstMode m) {
        if (m == null) throw new IllegalArgumentException("newAutoKst: no mode");
        for (int v : NEW_AUTO_KST_MODES) if (v == m.wire) return v;
        throw new IllegalArgumentException("newAutoKst: mode " + m.wire + " is not 9, 10, 11 or 12 (9.1)");
    }

    /**
     * DISPLAY 16 getCorrectKeystoneFullPiont(mode 0) -> KstPoint: the current corners. Throws ReplyException if the
     * reply is not an 81-point KstPoint or a corner is outside the 3840x2160 panel.
     */
    public KstPoint getKeystonePoints() throws RemoteException {
        HbFeatures.require(HbFeatures.F.MANUAL_KEYSTONE, "getCorrectKeystoneFullPiont");
        final byte mode = (byte) KstPoint.MODE_FOUR_POINT;
        try {
            return AidlCaller.call(GmpfCall.GET_CORRECT_KEYSTONE_FULL_PIONT, o -> o.i8(mode), r -> r.object(KstPoint::readFrom));
        } catch (IllegalArgumentException e) {
            throw new ReplyException("16 corner out of range: " + e.getMessage());
        }
    }

    /** DISPLAY 64 checkTrapezoidCoordinate(KstPoint) -> true when the vendor accepts the corners. */
    public boolean checkKeystonePoints(KstPoint p) throws RemoteException {
        HbFeatures.require(HbFeatures.F.MANUAL_KEYSTONE, "checkTrapezoidCoordinate");
        return AidlCaller.call(GmpfCall.CHECK_TRAPEZOID_COORDINATE, o -> o.object(p::writeTo), AidlCaller.RET_BOOL);
    }

    /** DISPLAY 15 setCorrectKeystoneFullPiont(mode 0, KstPoint) -> 1 for true, 0 for false (callers only log it). */
    public int applyKeystonePoints(KstPoint p) throws RemoteException {
        HbFeatures.require(HbFeatures.F.MANUAL_KEYSTONE, "setCorrectKeystoneFullPiont");
        final byte mode = (byte) KstPoint.MODE_FOUR_POINT;
        return AidlCaller.call(GmpfCall.SET_CORRECT_KEYSTONE_FULL_PIONT, o -> { o.i8(mode); o.object(p::writeTo); },
                AidlCaller.RET_BOOL) ? 1 : 0;
    }

    /**
     * MOTION 15 temporarySwitchTrigger(0, false): ends the temporary switch, exactly as stock HB SystemUI
     * AuxiliaryCurtainAlignDialog.dismiss. hb-t7, only from CurtainAlignOverlay after its show sent (2, false).
     */
    public boolean resetRealtimeAkTemporary() throws RemoteException {
        return wire.i32BoolRetBool(GmpfCall.TEMPORARY_SWITCH_TRIGGER, RESET_TEMPORARY_SWITCH_AK, false);
    }

    /**
     * hb-t5 keystone reset (9.7): the stock G0130 V3DisplayManager.setIChipReset body alone (not the stock
     * correctKeystone(50), banned, and no 158). Refused in top-speed game mode (that has its own path). Returns the 84
     * mode. HAL thread.
     */
    public int resetKeystoneStock() throws RemoteException {
        HbFeatures.require(HbFeatures.F.KEYSTONE_RESET, "resetKeystoneStock");
        GameModeOption cur = getGameModeOption();
        if (cur != null && cur.isTopSpeed()) {
            throw new IllegalStateException("keystone reset refused in top-speed game mode (" + cur + ")");
        }
        return chipReset();
    }

    // ------------------------------------------------------------------ optical zoom limit position (hb-t6)
    /** DISPLAY 190 GetCurrentZoomLimitStep -> {ret, limit, digital}: on the HB next to the other zoom calls. */
    public int[] getZoomLimitStep() throws RemoteException { return readZoomLimitStep(); }

    /** DISPLAY 189 SetOpticalZoomLimitStep(step), guarded as on the Z9X IGmpf2 14 (ZoomGuard.checkLimitStep). */
    public int setOpticalZoomLimitStep(int step) throws RemoteException { return writeZoomLimitStep(step); }

    // ------------------------------------------------------------------ HDMI / output timing
    /** TV 38 getHdmiInfo() -> the stHdmiInfo parcelable (its own field order). Read-only. */
    public HdmiSignal getHdmiInfo() throws RemoteException {
        return AidlCaller.call(GmpfCall.GET_HDMI_INFO, null, r -> r.object(q -> hdmiSignal(q.i32(), q.i32(), q.bool(), q.bool(), q.i32())));
    }

    /** getOutputTimingInfo (Ultra 120 Hz state): not in the HB tree (ULTRA_120 OFF). Nothing sent. */
    public OutputTimingInfo getOutputTimingInfo() throws RemoteException {
        throw new AidlCaller.NotOnHbException("getOutputTimingInfo (Ultra 120 Hz)");
    }

    private static HdmiSignal hdmiSignal(int width, int height, boolean interlace, boolean hdmiMode, int frameRateCenti) {
        return new HdmiSignal(width, height, interlace, hdmiMode, frameRateCenti,
                width + "," + height + "," + interlace + "," + hdmiMode + "," + frameRateCenti);
    }
}
