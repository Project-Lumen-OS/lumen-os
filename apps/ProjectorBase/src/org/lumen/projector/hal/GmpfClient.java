package org.lumen.projector.hal;

import android.os.HwBlob;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.RemoteException;

/**
 * GmpfClient of the Z9X: the shared IGmpf client (GmpfBase) over the HIDL interface
 *   xgimi.hardware.gmpf@1.0::IGmpf/default   (vendor service "gmpfHw", domain hal_gmpf_xgimi)
 * with the codes of devices/horse_popular/hal/codes.tsv (Z9xCodes) and the Z9X calls of its own: the keystone point
 * structs, the stock keystone reset with correctKeystone(50), newAutoKst, the HDMI and output timing structs.
 * IGmpf2 is a separate interface with its own codes: see Gmpf2Client.
 *
 * SAFETY (hard rules of the Z9X project): no generic transact; never autoFocus 301, 299, factory or calibration
 * codes, 155 / 80 (no sensor), power 229 / 233 / 241 / 422 / 543, newAutoKst 24, manualFocus other than {17, 0, 1, 2}
 * (apps/check_hal_callers.py holds the banned codes and fails the build on a transacting method without a caller
 * outside hal/). The focusEvent callback (HidlFocusCallback) answers at once and never calls the HAL.
 */
public final class GmpfClient extends GmpfBase<Integer> {
    public static final String IGMPF = "xgimi.hardware.gmpf@1.0::IGmpf";
    public static final String IPROJECTOR_FOCUS_CALLBACK = HidlFocusCallback.DESCRIPTOR;

    /** correctKeystone argument of stock setLowConfigureKeyStoneValue(50): the old one-axis value back to the centre. */
    private static final int KEYSTONE_ONE_AXIS_CENTRE = 50;
    /** stHdmiInfo: u32 width @0, u32 height @4, bool interlace @8, bool hdmi @9, u32 frame rate (1/100 Hz) @12. */
    private static final int HDMI_INFO_SIZE = 16;
    private static final int HDMI_WIDTH_AT = 0, HDMI_HEIGHT_AT = 4, HDMI_INTERLACE_AT = 8, HDMI_MODE_AT = 9, HDMI_RATE_AT = 12;
    /** OutputTimingInfo: 7 int32 (user timing, display w / h, current timing, current w / h, vfreq) + 3 int32 support table. */
    private static final int OUTPUT_TIMING_SIZE = 40, OUTPUT_TIMING_FIELDS = 7, I32 = 4;

    private final HidlWire hidl;

    public GmpfClient(FocusEventSink sink) {
        this(new HidlWire(IGMPF, false), sink);
    }

    private GmpfClient(HidlWire hidl, FocusEventSink sink) {
        super(hidl, Z9xCodes.GMPF, Z9xRules.RULES);
        this.hidl = hidl;
        HidlFocusCallback.attach(sink);
    }

    /** HwBinder.getService(IGmpf, "default", retry=false) + linkToDeath. Does not wait. True when connected. Never throws. */
    public boolean connect(IHwBinder.DeathRecipient death, long cookie) {
        return hidl.connect(death, cookie);
    }

    // ------------------------------------------------------------------ keystone
    /** 272 newAutoKst(9 / 10 / 11). May block: keystone thread only. */
    public byte newAutoKst(AutoKstMode m) throws RemoteException {
        return wire.i32RetI8(HalCodes.GMPF_NEW_AUTO_KST, m.wire);
    }

    /**
     * 186 getCorrectKeystoneFullPiont(mode 0): current 4 corners (reply Status + the 326-byte KstPoint). Throws
     * ReplyException if the reply is not a 326-byte buffer or a corner is outside the 3840x2160 panel.
     */
    public KstPoint getKeystonePoints() throws RemoteException {
        HwParcel q = hidl.request();
        q.writeInt8((byte) KstPoint.MODE_FOUR_POINT);
        try {
            return hidl.send(HalCodes.GMPF_GET_CORRECT_KEYSTONE_FULL_PIONT, q,
                    r -> KstPoint.fromBlob(r.readBuffer(KstPoint.WIRE_SIZE)));
        } catch (IllegalArgumentException e) {
            throw new ReplyException("186 corner out of range: " + e.getMessage());
        }
    }

    /** 150 checkTrapezoidCoordinate(KstPoint) -> true when the vendor accepts the corners. */
    public boolean checkKeystonePoints(KstPoint p) throws RemoteException {
        HwParcel q = hidl.request();
        q.writeBuffer(p.toBlob());
        return hidl.send(HalCodes.GMPF_CHECK_TRAPEZOID_COORDINATE, q, HidlCaller.RET_BOOL);
    }

    /** 185 correctKeystoneFullPiont(KstPoint) -> i32, applied at once. Callers checked the same points with 150 first. */
    public int applyKeystonePoints(KstPoint p) throws RemoteException {
        HwParcel q = hidl.request();
        q.writeBuffer(p.toBlob());
        return hidl.send(HalCodes.GMPF_CORRECT_KEYSTONE_FULL_PIONT, q, HidlCaller.RET_I32);
    }

    /**
     * The stock keystone reset, chip part (stock KeyStoneManager reset): setIChipReset, then 181 correctKeystone(50).
     * The caller sends the rest of the stock sequence (zoomHome, IGmpf2 46 doKeystoneRatio(3), 158). Returns the 181
     * result (logged). Caller: the quick panel's "Restore default picture" row (user action) only.
     */
    public int resetKeystoneStock() throws RemoteException {
        chipReset();
        HwParcel q = hidl.request();
        q.writeInt8((byte) KEYSTONE_ONE_AXIS_CENTRE);
        return hidl.send(HalCodes.GMPF_CORRECT_KEYSTONE, q, HidlCaller.RET_I32);
    }

    /** The corners-only reset of the stock manual keystone screen: setIChipReset alone. Caller: ManualKeystonePanel. */
    public void resetKeystoneCorners() throws RemoteException {
        chipReset();
    }

    // ------------------------------------------------------------------ HDMI / output timing structs
    /** 662 getHdmiInfo(): the current HDMI input timing (reply Status + the 16-byte stHdmiInfo). Read-only. */
    public HdmiSignal getHdmiInfo() throws RemoteException {
        return hidl.send(HalCodes.GMPF_GET_HDMI_INFO, hidl.request(), r -> hdmiSignal(r.readBuffer(HDMI_INFO_SIZE)));
    }

    /** 157 getOutputTimingInfo(): Status, bool (false fails), the 40-byte OutputTimingInfo. Read-only. */
    public OutputTimingInfo getOutputTimingInfo() throws RemoteException {
        return hidl.send(HalCodes.GMPF_GET_OUTPUT_TIMING_INFO, hidl.request(), r -> {
            if (!r.readBool()) throw new IllegalStateException("157 returned false");
            HwBlob b = r.readBuffer(OUTPUT_TIMING_SIZE);
            int[] f = new int[OUTPUT_TIMING_FIELDS];
            for (int i = 0; i < f.length; i++) f[i] = b.getInt32((long) i * I32);
            return new OutputTimingInfo(f[0], f[1], f[2], f[3], f[4], f[5], f[6]);
        });
    }

    private static HdmiSignal hdmiSignal(HwBlob b) {
        StringBuilder raw = new StringBuilder(2 * HDMI_INFO_SIZE);
        for (int i = 0; i < HDMI_INFO_SIZE; i++) raw.append(String.format("%02x", b.getInt8(i) & 0xff));
        return new HdmiSignal(b.getInt32(HDMI_WIDTH_AT), b.getInt32(HDMI_HEIGHT_AT), b.getInt8(HDMI_INTERLACE_AT) != 0,
                b.getInt8(HDMI_MODE_AT) != 0, b.getInt32(HDMI_RATE_AT), raw.toString());
    }

    // ================================================================== PARKED (not compiled)
    // No reviewed caller outside hal/ (check_hal_callers.py fails the build on a transacting
    // method without one). Kept as reference: re-enable a method only together with its caller,
    // and only after a device test where its argument values are marked UNVERIFIED.
    //
    // (307 sendFocusStatus(5) then (3), sendAfCardClosed: un-parked in Lumen OS 1.0.8 for AfCardOverlay, see the focus
    // section.)
    //
    // (121 / 120 zoom session: un-parked in Lumen OS 1.0.2 for the optical zoom, see the 3D / zoom section.)
    //
    // (141 translationKst: un-parked in Lumen OS 1.0.4 for panel.ZoomShiftPanel.)
    //
    // /** 87 / 88: eye protection mode, 0 follow / 1 safe. TSV 88 W=writeInt32 R=readBool. */
    // public int getEyeProtectionMode() throws RemoteException { return callRetI32(GET_HUMAN_DETECT_MODE); }
    //
    // public boolean setEyeProtectionMode(HumanDetectMode m) throws RemoteException {
    //     return callI32RetBool(SET_HUMAN_DETECT_MODE, m.wire);
    // }
    //
    // (670 / 671 noise reduction, 651 / 655 local contrast, 111 / 112 colour space: un-parked in Lumen OS 1.0.4
    // for the professional picture settings, see the picture section.)
    //
    // /** 623 getDolbyVisionPicMode() -> 0 bright / 1 soft. Practically unreachable on this SKU (QS verdict). */
    // public int getDolbyVisionPicMode() throws RemoteException { return callRetI32(GET_DOLBY_VISION_PIC_MODE); }
    //
    // /**
    //  * 624 setDolbyVisionPicMode(0 bright | 1 soft). GATED: refused unless 620 getHdrType() == 2 right
    //  * now (Dolby content playing). The Z9X SKU has no DV (SupportHashkeyMode=8), so expect it to be
    //  * refused; never advertise DV. TSV W=writeInt32 R=readBool.
    //  */
    // public boolean setDolbyVisionPicMode(boolean soft) throws RemoteException {
    //     int hdr = getHdrType();
    //     if (hdr != HDR_DOLBY_VISION) throw new IllegalStateException("Dolby Vision picture mode needs hdrType 2 (now " + hdr + ")");
    //     return callI32RetBool(SET_DOLBY_VISION_PIC_MODE, soft ? 1 : 0);
    // }
    //
    // (180 / 190 / 191, 3D: un-parked in Lumen OS 1.0.2 for display.ThreeD, see the 3D section.)
    //
    // (41 / 42 volume levelling: un-parked in v6.3 for the Z9X sound profile, see the sound section.)
    //
    // (420 / 419 power-on sound: un-parked in Lumen OS 1.0.4, see the sound section.)
    //
    // /**
    //  * 106 startAutoColor() -> bool. Stock sequence (FS 2.8): IGmpf2 53 setAEOnOff(false) first
    //  * (Gmpf2Client.disableAutoExposureForWallColor), then this. Never 99/101/102 (calibration).
    //  */
    // public boolean startWallColorAdaptation() throws RemoteException { return callRetBool(START_AUTO_COLOR); }
    //
    // /** 100 setAutoColorDefault() -> bool ("Default" button of the wall colour dialog). */
    // public boolean resetWallColorAdaptation() throws RemoteException { return callRetBool(SET_AUTO_COLOR_DEFAULT); }
    //
    // /** 249 / 250: auto colour after AK (advanced switch only). TSV 250 W=writeBool R=. */
    // public boolean getAutoColorAfterAk() throws RemoteException { return callRetBool(GET_PICTURE_ADJUST_AUTO_COLOR); }
    //
    // public void setAutoColorAfterAk(boolean on) throws RemoteException { callBool(SET_PICTURE_ADJUST_AUTO_COLOR, on); }
}
