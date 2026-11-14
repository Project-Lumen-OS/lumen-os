package org.lumen.projector.hal;

import static org.lumen.hal.gmpf.GmpfOp.*;
import static org.lumen.hal.gmpf.utils.Args.checkRange;
import static org.lumen.hal.gmpf.utils.Args.oneOf;

import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import org.lumen.hal.gmpf.GmpfCaller;
import org.lumen.hal.gmpf.GmpfCodes;
import org.lumen.hal.gmpf.GmpfGate;
import org.lumen.hal.gmpf.GmpfOp;
import org.lumen.hal.gmpf.GmpfRules;
import org.lumen.hal.gmpf.GmpfWire;
import org.lumen.hal.gmpf.ZoomGuard;
import org.lumen.hal.gmpf.utils.ReadBudget;

/**
 * The IGmpf client of every projector built on the Z9X app; each device's GmpfClient extends it with its transport,
 * its code table, its rules and its own calls. Every public method is one typed, whitelisted call with fixed or
 * range-checked arguments (IllegalArgumentException before anything is sent); there is no generic transact. A call
 * the projector does not have, or a feature its table switched off, throws before anything is sent. Errors:
 * RemoteException = not delivered or vendor dead; ReplyException = delivered with a bad reply, never repeat one-shot
 * calls. Method comments name the stock method; the stock analysis behind each value is in docs/z9x/parity and
 * the design notes.
 */
public abstract class GmpfBase<K> extends GmpfCaller<K> {
    static final String TAG = "Z9xGmpf";

    /** EDID of the current HDMI input (stock GmTvManager): 0 = 1.4, 1 = 2.0, 2 = auto, 3 = the enhanced 2.1 style. */
    public static final int EDID_14 = 0, EDID_20 = 1, EDID_AUTO = 2, EDID_21 = 3;
    /** Input source of getCurrentInputSource: 0 media / storage, 1 HDMI 1, 2 HDMI 2. */
    public static final int SOURCE_MEDIA = 0, SOURCE_HDMI1 = 1, SOURCE_HDMI2 = 2;
    /** getHdrType values (stock PictureUtils). */
    public static final int HDR_SDR = 0, HDR_HDR10 = 1, HDR_DOLBY_VISION = 2, HDR_HLG = 4, HDR_HDR10_PLUS = 5, HDR_VIVID = 6;
    /** SetPEQ bands 1..13 (MTK basic-sound PEQ); our gain limits in 0.1 dB: boosts up to +6 dB, cuts down to -12 dB. */
    public static final int PEQ_BANDS = 13;
    public static final int PEQ_GAIN_MIN = -120, PEQ_GAIN_MAX = 60;
    /** Set to "ready" by the reader service of the vendor native-UI fifos (Z9X z9x_nui.sh); empty until then. */
    public static final String NUI_READY_PROP = "sys.z9x.nui";

    /** Picture calls address window 0, as stock XgimiVideo. */
    static final int WINDOW_MAIN = 0;
    private static final int PERCENT_MAX = 100;
    private static final int FOCUS_STATUS_REMOTE_AF = 44;      // stock FocusUIV2.autoFocus (remote key)
    private static final int FOCUS_STATUS_UI_READY = 17;       // stock FocusUIV2.start: the power-on AF flow
    private static final int FOCUS_STATUS_AF_UI_OPEN = 4;      // stock AutoMaticFA: the AF card is on screen
    private static final int FOCUS_STATUS_AF_CARD_CLOSED = 5;  // stock AutoMaticFA.hideAutoMaticFaView, then 3
    private static final int FOCUS_STATUS_AF_CARD_DONE = 3;    // also alone: stock AutoMaticFA stop / cancel
    private static final int SYSTEM_UI_STATE_READY = 1;        // stock SystemUI systemUIServicesCompleted
    private static final int BOOT_ANIM_STATE_FINISHED = 2;     // stock bootanimation exit
    private static final int TEMPORARY_SWITCH_AK = 2;          // stock KeyStoneManager TEMPORARY_SWITCH_AK
    /** AK overlay steps acknowledged with uiAkDisplay (110 is never acked). */
    private static final int[] AK_ACK_TYPES = {106, 107, 109, 113, 114, 115, 116, 118};
    /** getCurrentKeystoneMode value after which stock setIChipReset sends switchKeystoneMode(0, 0). */
    private static final int KEYSTONE_MODE_LEAVE_ON_RESET = 6;
    private static final int LAMP_MIN = 1, LAMP_MAX = 10;
    private static final int UCD_MAX = 3, SOURCE_MAX = 3, PICTURE_MODE_MAX = 40;
    private static final int HEARTBEAT_MIN_MS = 500, HEARTBEAT_MAX_MS = 10_000;
    private static final int DIGITAL_ZOOM_LIMIT = 200;
    private static final int PEQ_FC_MIN = 50, PEQ_FC_MAX = 20_000, PEQ_Q_MIN = 5, PEQ_Q_MAX = 160;
    /** zoomControl modes, both with 10 steps as stock: 5 walks the lens home, 12 reads the limit count. */
    private static final int ZOOM_HOME = 5;
    private static final int ZOOM_LIMIT_TOTAL = 12;
    private static final int ZOOM_STEPS = 10;
    /** zoomStart / zoomFinish argument (reserved, always 0 as stock). */
    private static final int ZOOM_SESSION = 0;
    /** The getter of setGameModeOption lags a write by seconds: for this long after a true answer it reads the write. */
    private static final long OPTION_LAG_MS = 8_000;
    /** The two sensor reads of the fit: per minute and the gap between two. */
    private static final ReadBudget TOF = new ReadBudget(6, 1_000);
    private static final ReadBudget MOTION = new ReadBudget(20, 80);

    private static volatile boolean sFoldLogged;
    private static volatile GameModeOption sWrittenOpt;
    private static volatile long sWrittenAt;

    /** elapsedRealtime of our last sound-mode call, before and after the transaction (audio.VendorSoundWatch). */
    public static volatile long lastSoundWriteAt = Long.MIN_VALUE / 2;

    protected GmpfBase(GmpfWire<K> wire, GmpfCodes<K> codes, GmpfRules rules) {
        super(wire, codes, rules);
    }

    // ------------------------------------------------------------------ NUI fifo reader gate
    /**
     * True when a reader holds the vendor native-UI fifo: the vendor opens it blocking, so without a reader the eye
     * protection calls would block the HAL thread until reboot. Fails closed. Any thread.
     */
    public static boolean nuiReaderReady() {
        try {
            return "ready".equals(SystemProperties.get(NUI_READY_PROP, ""));
        } catch (Throwable t) {
            return false;
        }
    }

    private void requireNuiReader(GmpfOp op) throws NuiNotReadyException {
        if (!nuiReaderReady()) throw new NuiNotReadyException(label(op));
    }

    // ------------------------------------------------------------------ focus listener
    /** connectProjectorFocusManager(pid, callback), or setFocusCallback(callback) where that is the registration. */
    public void connectFocusManager() throws RemoteException {
        wire.focusCallback(key(has(SET_FOCUS_CALLBACK) ? SET_FOCUS_CALLBACK : CONNECT_PROJECTOR_FOCUS_MANAGER));
    }

    /** setProjectorFocusListener(pid, callback); folded into the registration where the HAL has no such call. */
    public void setFocusListener() throws RemoteException {
        if (has(SET_PROJECTOR_FOCUS_LISTENER)) {
            wire.focusCallback(key(SET_PROJECTOR_FOCUS_LISTENER));
        } else if (!sFoldLogged) {
            sFoldLogged = true;
            Log.i(TAG, "setFocusListener: folded into the focus callback registration, nothing sent");
        }
    }

    // ------------------------------------------------------------------ boot handshake
    /** setSystemUIState(1). A plain store in the vendor; idempotent. */
    public void setSystemUiReady() throws RemoteException { wire.i32(key(SET_SYSTEM_UI_STATE), SYSTEM_UI_STATE_READY); }

    public int getSystemUiState() throws RemoteException { return wire.retI32(key(GET_SYSTEM_UI_STATE)); }

    /** setBootAnimState(2). A plain store in the vendor; idempotent. */
    public void setBootAnimFinished() throws RemoteException { wire.i32(key(SET_BOOT_ANIM_STATE), BOOT_ANIM_STATE_FINISHED); }

    public int getBootAnimState() throws RemoteException { return wire.retI32(key(GET_BOOT_ANIM_STATE)); }

    /** goToBootCompleted(0 or 1). The caller guarantees once per boot (per stage). */
    public boolean goToBootCompleted(BootStage s) throws RemoteException {
        return wire.i32RetBool(key(GO_TO_BOOT_COMPLETED), s.wire);
    }

    /** sendFocusStatus(17): starts the vendor power-on AF flow. Handshake only, last. */
    public void sendPowerOnUiReady() throws RemoteException { wire.u8(key(SEND_FOCUS_STATUS), FOCUS_STATUS_UI_READY); }

    // ------------------------------------------------------------------ focus
    /** sendFocusStatus(44): remote focus key short press. */
    public void sendRemoteAutofocus() throws RemoteException { wire.u8(key(SEND_FOCUS_STATUS), FOCUS_STATUS_REMOTE_AF); }

    /** sendFocusStatus(4): our AF card is on screen (the vendor AF waits about 3 s for it). Caller: AfCardOverlay. */
    public void sendAfUiOpen() throws RemoteException { wire.u8(key(SEND_FOCUS_STATUS), FOCUS_STATUS_AF_UI_OPEN); }

    /** sendFocusStatus(5) then (3): a shown AF card was closed. Caller: AfCardOverlay, after a 4. */
    public void sendAfCardClosed() throws RemoteException {
        wire.u8(key(SEND_FOCUS_STATUS), FOCUS_STATUS_AF_CARD_CLOSED);
        wire.u8(key(SEND_FOCUS_STATUS), FOCUS_STATUS_AF_CARD_DONE);
    }

    /** sendFocusStatus(3): the user stopped the AF on the card. Caller: AfCardOverlay. */
    public void sendAfStop() throws RemoteException { wire.u8(key(SEND_FOCUS_STATUS), FOCUS_STATUS_AF_CARD_DONE); }

    /** manualFocus(17 / 0 / 1 / 2). Returns at once; the vendor runs the motor itself. */
    public void manualFocus(ManualFocus m) throws RemoteException { wire.u8(key(MANUAL_FOCUS), m.wire); }

    /** Autofocus at power-on. */
    public boolean getPowerOnAf() throws RemoteException { return wire.retBool(key(IS_POWER_ON_AF_ENABLED)); }
    public void setPowerOnAf(boolean on) throws RemoteException { wire.bool(key(POWER_ON_AF_ENABLE), on); }
    /** Autofocus after the projector was moved. */
    public boolean getMoveAf() throws RemoteException { return wire.retBool(key(GET_ANG_TRIGGER_AF)); }
    public boolean setMoveAf(boolean on) throws RemoteException { return wire.boolRetBool(key(SET_ANG_TRIGGER_AF), on); }

    // ------------------------------------------------------------------ keystone
    /** autoKst(). May block inside the HAL for seconds: keystone thread only. */
    public byte autoKst() throws RemoteException {
        gate(GmpfGate.AUTO_KEYSTONE, "autoKst");
        return wire.retI8(key(AUTO_KST));
    }

    /** uiAkDisplay(type): the AK overlay rendered step type (the vendor waits up to 5 s for it). Ack types only. */
    public void uiAkDisplay(int type) throws RemoteException {
        if (!oneOf(type, AK_ACK_TYPES)) throw new IllegalArgumentException("uiAkDisplay: type " + type + " is not an AK ack type");
        wire.i32(key(UI_AK_DISPLAY), type);
    }

    /** generateLogoByTrapezoid(0): regenerate the boot logo after manual keystone. */
    public int regenerateBootLogo() throws RemoteException { return wire.i32RetI32(key(GENERATE_LOGO_BY_TRAPEZOID), 0); }

    /** temporarySwitchTrigger(2, enable): pause / resume real-time AK around the keystone, zoom and rotation screens. */
    public boolean setRealtimeAkTemporarily(boolean enable) throws RemoteException {
        return wire.i32BoolRetBool(key(TEMPORARY_SWITCH_TRIGGER), TEMPORARY_SWITCH_AK, enable);
    }

    /** correctKeystoneReset(0) with the rest of stock setIChipReset, ONLY in HDMI game top-speed mode (refused otherwise). */
    public void resetKeystoneForTopSpeedGame() throws RemoteException {
        GameModeOption cur = getGameModeOption();
        if (cur == null || !cur.isTopSpeed()) {
            throw new IllegalStateException("correctKeystoneReset only in top-speed game mode (now " + cur + ")");
        }
        chipReset();
    }

    /** Stock setIChipReset: correctKeystoneReset(0), then switchKeystoneMode(0, 0) only when the mode reads 6. */
    protected final int chipReset() throws RemoteException {
        wire.u8(key(CORRECT_KEYSTONE_RESET), 0);
        int mode = wire.retI32(key(GET_CURRENT_KEYSTONE_MODE));
        if (mode == KEYSTONE_MODE_LEAVE_ON_RESET) {
            boolean r = wire.i32I32RetBool(key(SWITCH_KEYSTONE_MODE), 0, 0);
            Log.i(TAG, "keystone reset: mode read " + mode + ", switchKeystoneMode(0,0) -> " + r);
        }
        return mode;
    }

    /** translationKst(dir, onlyCheck) -> 0 possible / -1 at the edge: a software move of the picture (no motor). */
    public int shiftPicture(ShiftDir d, boolean onlyCheck) throws RemoteException {
        gate(GmpfGate.ZOOM_SHIFT, "translationKst");
        return wire.i32BoolRetI32(key(TRANSLATION_KST), d.wire, onlyCheck);
    }

    // ------------------------------------------------------------------ projection toggles (getter first, setter on click)
    /** Fit the picture into the projection screen (curtain). */
    public boolean getCurtainFit() throws RemoteException { return wire.retBool(key(GET_PROJ_CURTAIN_ADP)); }
    public void setCurtainFit(boolean on) throws RemoteException {
        gate(GmpfGate.CURTAIN_FIT, "setProjCurtainAdp");
        wire.bool(key(SET_PROJ_CURTAIN_ADP), on);
    }
    /** Obstacle avoidance. */
    public boolean getObstacleAvoid() throws RemoteException { return wire.retBool(key(GET_PROJ_SCREEN_ADP)); }
    public void setObstacleAvoid(boolean on) throws RemoteException {
        gate(GmpfGate.OBSTACLE_AVOID, "setProjScreenAdp");
        wire.bool(key(SET_PROJ_SCREEN_ADP), on);
    }
    /** Auto keystone at power-on. */
    public boolean getPowerOnAk() throws RemoteException { return wire.retBool(key(GET_POWER_ON_AK_FLAG)); }
    public void setPowerOnAk(boolean on) throws RemoteException { wire.bool(key(SAVE_POWER_ON_AK_FLAG), on); }
    /** Real-time keystone (auto keystone after the projector was moved). */
    public boolean getMoveAk() throws RemoteException { return wire.retBool(key(GET_ACC_TRIGGER_AK)); }
    public boolean setMoveAk(boolean on) throws RemoteException { return wire.boolRetBool(key(SET_ACC_TRIGGER_AK), on); }
    /** Automatic flip ("Auto" projection mode). */
    public boolean getAutoReverse() throws RemoteException { return wire.retBool(key(GET_AUTO_REVERSE)); }
    public boolean setAutoReverse(boolean on) throws RemoteException { return wire.boolRetBool(key(SET_AUTO_REVERSE), on); }

    /** getProjectorPutMode() (null if not 0..3). */
    public PutMode getPutMode() throws RemoteException { return PutMode.fromWire(wire.retU8(key(GET_PROJECTOR_PUT_MODE))); }
    /** setProjectorPutMode: flips the image at once, confirm first. */
    public void setPutMode(PutMode m) throws RemoteException { wire.u8(key(SET_PROJECTOR_PUT_MODE), m.wire); }

    // ------------------------------------------------------------------ lamp / screen
    /** getDlpLumensLevel() -> 1..10. */
    public int getLampLevel() throws RemoteException { return wire.retU8(key(GET_DLP_LUMENS_LEVEL)); }
    public void setLampLevel(int level) throws RemoteException {
        wire.u8(key(SET_DLP_LUMENS_LEVEL), checkRange("lamp level", level, LAMP_MIN, LAMP_MAX));
    }

    /** getDlpLumensMode() raw; Snapshot keeps the colour temperature only for this projector's ColorTemp values. */
    public int getLumensModeRaw() throws RemoteException { return wire.retU8(key(GET_DLP_LUMENS_MODE)); }
    /** setDlpLumensMode(c): this projector's ColorTemp values only. Re-read the lamp level afterwards. */
    public void setColorTemp(ColorTemp c) throws RemoteException {
        gate(GmpfGate.COLOR_TEMP, "setDlpLumensMode");
        wire.u8(key(SET_DLP_LUMENS_MODE), c.wire);
    }

    /** Light / screen on. */
    public boolean getScreenOn() throws RemoteException { return wire.retBool(key(GET_SCREEN_ON_OFF)); }
    /** setScreenOnOff(bool): lamp and lens door, as stock xgimiPrepareSleep. */
    public void setScreenOn(boolean on) throws RemoteException { wire.bool(key(SET_SCREEN_ON_OFF), on); }

    /** Smart eye protection (ToF human detect); the setter only while the NUI fifo reader is up. Confirm before OFF. */
    public boolean getEyeProtection() throws RemoteException { return wire.retBool(key(GET_HUMAN_DETECT_ON_OFF)); }
    public boolean setEyeProtection(boolean on) throws RemoteException {
        requireNuiReader(SET_HUMAN_DETECT_ON_OFF);
        return wire.boolRetBool(key(SET_HUMAN_DETECT_ON_OFF), on);
    }
    public boolean getEyeProtectionScreenOn() throws RemoteException { return wire.retBool(key(GET_HUMAN_DETECT_SCREEN_ON_OFF)); }
    /** setHumanDetectScreenOnOff(TRUE) only: the light back after an eye-protection blank. */
    public boolean restoreEyeProtectionScreen() throws RemoteException {
        requireNuiReader(SET_HUMAN_DETECT_SCREEN_ON_OFF);
        return wire.boolRetBool(key(SET_HUMAN_DETECT_SCREEN_ON_OFF), true);
    }

    /**
     * uiHeartBeatSet(name, arm, period ms): arms (or disarms) the vendor UI watchdog; without a beat within the period
     * the vendor gives the light back itself. Name and period fixed by the caller; only while the NUI reader is up.
     */
    public byte uiHeartBeatSet(String name, boolean arm, int periodMs) throws RemoteException {
        checkWatchName(name);
        checkRange("heartbeat period", periodMs, HEARTBEAT_MIN_MS, HEARTBEAT_MAX_MS);
        requireNuiReader(UI_HEART_BEAT_SET);
        return wire.strI32I32RetI8(key(UI_HEART_BEAT_SET), name, arm ? 1 : 0, periodMs);
    }

    /** uiHeartBeatDo(name): one beat of the armed watchdog. */
    public byte uiHeartBeatDo(String name) throws RemoteException {
        checkWatchName(name);
        requireNuiReader(UI_HEART_BEAT_DO);
        return wire.strRetI8(key(UI_HEART_BEAT_DO), name);
    }

    /** Watchdog names: short ASCII identifiers only (the vendor compares a fixed buffer). */
    private static void checkWatchName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,31}")) throw new IllegalArgumentException("bad watchdog name: " + name);
    }

    /** getSystemTemperature() -> deg C. */
    public int getSystemTemperature() throws RemoteException { return wire.retU8(key(GET_SYSTEM_TEMPERATURE)); }

    // ------------------------------------------------------------------ picture (window 0)
    /** getPictureMode(0): 10..17 = AI. */
    public int getPictureModeRaw() throws RemoteException { return wire.i32RetI32(key(GET_PICTURE_MODE), WINDOW_MAIN); }
    /** setPictureMode(0, m). Re-read every PQ getter afterwards (vendor side effects). */
    public boolean setPictureMode(PictureMode m) throws RemoteException {
        if (m == PictureMode.OFFICE) gate(GmpfGate.OFFICE_MODE, "setPictureMode(OFFICE)");
        return wire.i32I32RetBool(key(SET_PICTURE_MODE), WINDOW_MAIN, m.wire);
    }
    /** setPictureMode(0, 10..17): AI scene refinement, only while the current mode is AI. */
    public boolean setAiScene(AiScene s) throws RemoteException {
        return wire.i32I32RetBool(key(SET_PICTURE_MODE), WINDOW_MAIN, s.wire);
    }

    public int getPq(PqItem item) throws RemoteException {
        switch (item) {
            case BRIGHTNESS: return getWindowValue(GET_BRIGHTNESS);
            case CONTRAST: return getWindowValue(GET_CONTRAST);
            case SATURATION: return getWindowValue(GET_SATURATION);
            case SHARPNESS: return getWindowValue(GET_SHARPNESS);
            default: throw new IllegalArgumentException("pq " + item);
        }
    }
    /** Debounce sliders (200 ms, drop older values). */
    public boolean setPq(PqItem item, int value) throws RemoteException {
        int v = checkRange(item.name(), value, 0, PERCENT_MAX);
        switch (item) {
            case BRIGHTNESS: return setWindowValue(SET_BRIGHTNESS, v);
            case CONTRAST: return setWindowValue(SET_CONTRAST, v);
            case SATURATION: return setWindowValue(SET_SATURATION, v);
            case SHARPNESS: return setWindowValue(SET_SHARPNESS, v);
            default: throw new IllegalArgumentException("pq " + item);
        }
    }

    public int getMemcLevel() throws RemoteException { return wire.i32RetI32(key(GET_MEMC_LEVEL), WINDOW_MAIN); }
    public boolean setMemcLevel(MemcLevel l) throws RemoteException {
        return wire.i32I32RetBool(key(SET_MEMC_LEVEL), WINDOW_MAIN, l.wire);
    }

    /** White balance gains and offsets (stock professional picture settings), 0..100. */
    public int getWb(WbItem item) throws RemoteException {
        switch (item) {
            case RED_GAIN: return getWindowValue(GET_COLOR_TEMP_RED_GAIN);
            case GREEN_GAIN: return getWindowValue(GET_COLOR_TEMP_GREEN_GAIN);
            case BLUE_GAIN: return getWindowValue(GET_COLOR_TEMP_BLUE_GAIN);
            case RED_OFFSET: return getWindowValue(GET_COLOR_TEMP_RED_OFFSET);
            case GREEN_OFFSET: return getWindowValue(GET_COLOR_TEMP_GREEN_OFFSET);
            case BLUE_OFFSET: return getWindowValue(GET_COLOR_TEMP_BLUE_OFFSET);
            default: throw new IllegalArgumentException("wb " + item);
        }
    }
    public boolean setWb(WbItem item, int value) throws RemoteException {
        int v = checkRange(item.name(), value, 0, PERCENT_MAX);
        switch (item) {
            case RED_GAIN: return setWindowValue(SET_COLOR_TEMP_RED_GAIN, v);
            case GREEN_GAIN: return setWindowValue(SET_COLOR_TEMP_GREEN_GAIN, v);
            case BLUE_GAIN: return setWindowValue(SET_COLOR_TEMP_BLUE_GAIN, v);
            case RED_OFFSET: return setWindowValue(SET_COLOR_TEMP_RED_OFFSET, v);
            case GREEN_OFFSET: return setWindowValue(SET_COLOR_TEMP_GREEN_OFFSET, v);
            case BLUE_OFFSET: return setWindowValue(SET_COLOR_TEMP_BLUE_OFFSET, v);
            default: throw new IllegalArgumentException("wb " + item);
        }
    }

    private int getWindowValue(GmpfOp op) throws RemoteException { return wire.i32RetI32(key(op), WINDOW_MAIN); }

    private boolean setWindowValue(GmpfOp op, int value) throws RemoteException {
        return wire.i32I32RetBool(key(op), WINDOW_MAIN, value);
    }

    /** getUcdLevel(0) -> 0..3 ("local contrast"). */
    public int getLocalContrast() throws RemoteException { return wire.i32RetI32(key(GET_UCD_LEVEL), WINDOW_MAIN); }
    public boolean setLocalContrast(int level) throws RemoteException {
        return wire.i32I32RetBool(key(SET_UCD_LEVEL), WINDOW_MAIN, checkRange("ucd level", level, 0, UCD_MAX));
    }

    public int getNoiseReduction() throws RemoteException { return wire.i32RetI32(key(GET_3DNR), WINDOW_MAIN); }
    public boolean setNoiseReduction(NoiseReduction n) throws RemoteException {
        return wire.i32I32RetBool(key(SET_3DNR), WINDOW_MAIN, n.wire);
    }

    /** getDispColorSpace(source, pictureMode): as stock, source 1 / 2 on HDMI 1 / 2, else 0; the mode is getPictureMode's. */
    public int getColorSpaceRaw(int source, int pictureMode) throws RemoteException {
        return wire.i32I32RetI32(key(GET_DISP_COLOR_SPACE), checkRange("source", source, 0, SOURCE_MAX),
                checkRange("picture mode", pictureMode, 0, PICTURE_MODE_MAX));
    }
    /** switchDispColorSpace(cs) (stock ignores the result and re-reads). */
    public int setColorSpace(ColorSpace cs) throws RemoteException { return wire.i32RetI32(key(SWITCH_DISP_COLOR_SPACE), cs.wire); }

    public int getHdrType() throws RemoteException { return wire.retI32(key(GET_HDR_TYPE)); }

    public int getCurrentInputSource() throws RemoteException { return wire.retI32(key(GET_CURRENT_INPUT_SOURCE)); }

    /**
     * Connects HDMI 1 / 2 to the main video window, or gives it back to Android (SOURCE_MEDIA), as the stock HDMI player.
     * Where the HAL has switchInputSourceWithCallingPid it takes our pid (kept only for its client-died fallback),
     * else the plain switchInputSource. Any other source is refused.
     */
    public boolean switchInputSource(int source) throws RemoteException {
        if (!oneOf(source, SOURCE_MEDIA, SOURCE_HDMI1, SOURCE_HDMI2)) throw new IllegalArgumentException("source " + source);
        if (has(SWITCH_INPUT_SOURCE_WITH_CALLING_PID)) {
            return wire.i32I32RetBool(key(SWITCH_INPUT_SOURCE_WITH_CALLING_PID), source, Process.myPid());
        }
        return wire.i32RetBool(key(SWITCH_INPUT_SOURCE), source);
    }

    // ------------------------------------------------------------------ HDMI settings of the current input
    /** getHdmiRange() -> 0 auto / 1 full / 2 limited. */
    public int getHdmiRange() throws RemoteException { return wire.retI8(key(GET_HDMI_RANGE)); }
    /** setHdmiRange(r): saved by the vendor per input. Caller checks HDMI is on screen. */
    public boolean setHdmiRange(HdmiRange r) throws RemoteException { return wire.u8RetBool(key(SET_HDMI_RANGE), r.wire); }

    public int getEdidVersion() throws RemoteException { return wire.retI32(key(GET_EDID_VERSION)); }
    /** setEdidVersion(v), this projector's stock values only: the source sees a new EDID and reconnects. */
    public boolean setEdidVersion(int v) throws RemoteException {
        if (!oneOf(v, rules().edidVersions())) throw new IllegalArgumentException("edid " + v + ": not a stock value here");
        return wire.i32RetBool(key(SET_EDID_VERSION), v);
    }

    // ------------------------------------------------------------------ 3D (the five stock calls, nothing else)
    public boolean isVideoPlaying() throws RemoteException { return wire.retBool(key(IS_VIDEO_PLAYING)); }

    /** enable3D(1 off | 2 side-by-side | 3 top-bottom). The vendor refuses by itself without a fit video. */
    public boolean set3DMode(Mode3D m) throws RemoteException { return wire.u8RetBool(key(ENABLE_3D), m.wire); }

    /** enable3DTo2D(side-by-side | top-bottom): one eye's picture, no glasses. */
    public boolean set3DTo2D(boolean topBottom) throws RemoteException {
        return wire.u8RetBool(key(ENABLE_3D_TO_2D), (topBottom ? Mode3D.TOP_BOTTOM : Mode3D.SIDE_BY_SIDE).wire);
    }

    /** getCurrent3DFormat(); 2D without a call where the HAL has no 3D (shared code reads an unknown state as 3D). */
    public int getCurrent3DFormat() throws RemoteException {
        return has(GET_CURRENT_3D_FORMAT) ? wire.retU8(key(GET_CURRENT_3D_FORMAT)) : Mode3D.OFF.wire;
    }

    /** is3DTo2DEnabled(); false without a call where the HAL has no 3D. */
    public boolean is3DTo2DEnabled() throws RemoteException {
        return has(IS_3D_TO_2D_ENABLED) && wire.retBool(key(IS_3D_TO_2D_ENABLED));
    }

    // ------------------------------------------------------------------ optical zoom (ZoomGuard holds the session rules)
    /** zoomControl(5, 10): the lens walks home to the biggest picture, into the vendor's limit position, as stock reset. */
    public int zoomHome() throws RemoteException {
        gate(GmpfGate.OPTICAL_ZOOM, "zoomControl(5, 10)");
        int r = wire.i32I32RetI32(key(ZOOM_CONTROL), ZOOM_HOME, ZOOM_STEPS);
        ZoomGuard.noteHome();
        return r;
    }

    /** zoomControl(12, 10): the number of limit positions beyond optical index 0 (a read, stock getLimitStep). */
    public int getZoomLimitTotal() throws RemoteException {
        gate(GmpfGate.OPTICAL_ZOOM, "zoomControl(12, 10)");
        int r = wire.i32I32RetI32(key(ZOOM_CONTROL), ZOOM_LIMIT_TOTAL, ZOOM_STEPS);
        ZoomGuard.noteLimitTotal(r);
        return r;
    }

    /** zoomStart(0): opens the session (the vendor takes the motor position here). */
    public int zoomSessionStart() throws RemoteException {
        ZoomGuard.clearReads();
        int r = wire.i32RetI32(key(ZOOM_START), ZOOM_SESSION);
        ZoomGuard.sessionOpened();
        return r;
    }

    /** zoomFinish(0): closes the session; always after zoomStart, also on errors. */
    public int zoomSessionFinish() throws RemoteException {
        ZoomGuard.sessionClosed();
        return wire.i32RetI32(key(ZOOM_FINISH), ZOOM_SESSION);
    }

    /** getZoomStepRange() -> {ret, opticalMin, opticalMax, digitalMin, digitalMax}. Valid inside a session; callers validate. */
    public int[] getZoomStepRange() throws RemoteException {
        int[] v = wire.i32Struct(key(GET_ZOOM_STEP_RANGE), 4);
        ZoomGuard.noteRange(v[1], v[2]);
        return v;
    }

    /** getCurrentZoomStep2() -> {ret, optical, digital}. Callers validate. */
    public int[] getZoomStep() throws RemoteException {
        int[] v = wire.i32Struct(key(GET_CURRENT_ZOOM_STEP2), 2);
        ZoomGuard.noteStep(v[1]);
        return v;
    }

    /**
     * setOpticalZoomStep(step): one bounded lens move, refused here (nothing sent) unless ZoomGuard.checkOpticalStep
     * passes: inside a prepared session, in the read range, the neighbour of a fresh step read, not in the limit zone.
     */
    public int setOpticalZoomStep(int step) throws RemoteException {
        gate(GmpfGate.OPTICAL_ZOOM, "setOpticalZoomStep");
        checkRange("optical zoom step", step, 0, ZoomGuard.MAX_OPTICAL_STEP);
        ZoomGuard.checkOpticalStep(label(SET_OPTICAL_ZOOM_STEP), step, rules().stepNeedsLimitRead());
        return wire.i32RetI32(key(SET_OPTICAL_ZOOM_STEP), step);
    }

    /** setDigitalZoomStep(step): the stock digital zoom inside the panel (no motor); callers send only a neighbour. */
    public int setDigitalZoomStep(int step) throws RemoteException {
        gate(GmpfGate.ZOOM_SHIFT, "setDigitalZoomStep");
        return wire.i32RetI32(key(SET_DIGITAL_ZOOM_STEP), checkRange("digital zoom step", step, -DIGITAL_ZOOM_LIMIT, DIGITAL_ZOOM_LIMIT));
    }

    // ------------------------------------------------------------------ fit to my screen size: sensor reads, rate-limited
    /** getTofDistance() -> mm, one active measurement (about 0.6 s). Never in a loop or a timer. */
    public int getTofDistance() throws RemoteException {
        gate(GmpfGate.FIT_SCREEN, "getTofDistance");
        K k = key(GET_TOF_DISTANCE);
        TOF.take(wire.code(k) + " getTofDistance");
        return wire.retI32(k);
    }

    /** getMotionDetection() -> {acc x, y, z, gyro x, y, z, type}; axis order and signs UNVERIFIED for the pitch. */
    public int[] getMotionDetection() throws RemoteException {
        gate(GmpfGate.FIT_SCREEN, "getMotionDetection");
        K k = key(GET_MOTION_DETECTION);
        MOTION.take(wire.code(k) + " getMotionDetection");
        return wire.motion(k);
    }

    // ------------------------------------------------------------------ HDMI / game / 120 Hz
    public int getAspectRatioRaw() throws RemoteException { return wire.retI32(key(GET_ASPECT_RATIO)); }
    /** setAspectRatio(a): this projector's AspectRatio values. HDMI only. */
    public boolean setAspectRatio(AspectRatio a) throws RemoteException { return wire.i32RetBool(key(SET_ASPECT_RATIO), a.wire); }

    /** getGameModeOption() -> 0..3 (null otherwise); for OPTION_LAG_MS after a write that answered true, the write. */
    public GameModeOption getGameModeOption() throws RemoteException {
        GameModeOption w = sWrittenOpt;
        if (w != null && SystemClock.uptimeMillis() - sWrittenAt < OPTION_LAG_MS) return w;
        return GameModeOption.fromWire(wire.retI32(key(GET_GAME_MODE_OPTION)));
    }

    /** setGameModeOption(0..3). HDMI only. After top speed stock also resets the keystone (resetKeystoneForTopSpeedGame). */
    public boolean setGameModeOption(GameModeOption o) throws RemoteException {
        boolean r = wire.i32RetBool(key(SET_GAME_MODE_OPTION), o.wire);
        if (r) {
            sWrittenOpt = o;
            sWrittenAt = SystemClock.uptimeMillis();
        }
        return r;
    }

    /** setOutputTiming(4 on | 6 off): "Ultra 120 Hz", the screen blanks about 4 s. Caller refuses with 3D or >= 62 Hz input. */
    public boolean setOutputTiming(OutputTiming t) throws RemoteException { return wire.i32RetBool(key(SET_OUTPUT_TIMING), t.wire); }

    // ------------------------------------------------------------------ sound
    /** getSoundeffect() (null if not a known mode). */
    public SoundEffect getSoundEffect() throws RemoteException { return SoundEffect.fromWire(getSoundEffectRaw()); }
    /** getSoundeffect() raw 0..255 (boot safety check of SoundProfiles). */
    public int getSoundEffectRaw() throws RemoteException { return wire.retU8(key(GET_SOUND_EFFECT)) & 0xff; }

    /** setSoundeffect: only AI / MOVIE / MUSIC / SPORTS / KARAOKE (the modes with an amp table). */
    public void setSoundEffect(SoundEffect e) throws RemoteException {
        if (e != SoundEffect.AI && e != SoundEffect.MOVIE && e != SoundEffect.MUSIC
                && e != SoundEffect.SPORTS && e != SoundEffect.KARAOKE) {
            throw new IllegalArgumentException("sound mode " + e + " has no amp table: not sent");
        }
        noteSoundWrite();
        try { wire.u8(key(SET_SOUND_EFFECT), e.wire); } finally { noteSoundWrite(); }
    }

    /** setAutoSoundeffect: the AI-sound sub-mode, MOVIE / MUSIC / SPORTS / KARAOKE only; callers read AI right before. */
    public void setAutoSoundEffect(SoundEffect sub) throws RemoteException {
        if (sub != SoundEffect.MOVIE && sub != SoundEffect.MUSIC && sub != SoundEffect.SPORTS && sub != SoundEffect.KARAOKE) {
            throw new IllegalArgumentException("AI sub-mode " + sub + " has no amp table: not sent");
        }
        noteSoundWrite();
        try { wire.u8(key(SET_AUTO_SOUND_EFFECT), sub.wire); } finally { noteSoundWrite(); }
    }

    private static void noteSoundWrite() { lastSoundWriteAt = SystemClock.elapsedRealtime(); }

    /** SetPEQ(band 1..13, gain 0.1 dB, fc 50..20000 Hz, q 0.5..16.0 in tenths). Caller: audio.SoundEq only. */
    public int setPeqBand(int band, int gainTenthDb, int fcHz, int qTenth) throws RemoteException {
        return wire.i32x4RetI32(key(SET_PEQ), checkRange("peq band", band, 1, PEQ_BANDS),
                checkRange("peq gain", gainTenthDb, PEQ_GAIN_MIN, PEQ_GAIN_MAX),
                checkRange("peq fc", fcHz, PEQ_FC_MIN, PEQ_FC_MAX), checkRange("peq q", qTenth, PEQ_Q_MIN, PEQ_Q_MAX));
    }

    /** SetPEQEnable(bool): true pushes the stored bands, false bypasses them. No getter. */
    public int setPeqEnabled(boolean on) throws RemoteException { return wire.boolRetI32(key(SET_PEQ_ENABLE), on); }

    /** setAudioOutput(path): the audioserver report of the current media output. Caller: audio.AudioPathReporter only. */
    public void setAudioOutput(AudioPath p) throws RemoteException { wire.u8(key(SET_AUDIO_OUTPUT), p.wire); }

    /** getAudioOutput(): the path the vendor saved at its last SetOutputType, 0 before its audio thread runs. */
    public int getAudioOutputRaw() throws RemoteException { return wire.retU8(key(GET_AUDIO_OUTPUT)) & 0xff; }

    /** getDtsEffectsEnable(). If true, the enhancement shows neither option. */
    public boolean getDtsEffects() throws RemoteException { return wire.retBool(key(GET_DTS_EFFECTS_ENABLE)); }
    /** getSoundProcessType() (2 Harman, 5 DTS Virtual:X). */
    public int getSoundProcessRaw() throws RemoteException { return wire.retI32(key(GET_SOUND_PROCESS_TYPE)); }

    /** Sound enhancement in the stock order: setDtsEffectsEnable(false), then setSoundProcessType. User action only. */
    public boolean setSoundEnhancement(SoundProcess p) throws RemoteException {
        K dts = key(SET_DTS_EFFECTS_ENABLE);
        K type = key(SET_SOUND_PROCESS_TYPE);
        noteSoundWrite();
        try {
            wire.boolRetBool(dts, false);
            return wire.i32RetBool(type, p.wire);
        } finally {
            noteSoundWrite();
        }
    }

    /** getVolumeBalanceEnable() (volume levelling). */
    public boolean getVolumeBalance() throws RemoteException { return wire.retBool(key(GET_VOLUME_BALANCE_ENABLE)); }

    /** setVolumeBalanceEnable(false): the stock default back, while it reads true. User action only. */
    public boolean disableVolumeBalance() throws RemoteException { return setVolumeBalance(false); }

    /** setVolumeBalanceEnable(on): the stock "Volume balance" switch. User action only. */
    public boolean setVolumeBalance(boolean on) throws RemoteException {
        K k = key(SET_VOLUME_BALANCE_ENABLE);
        noteSoundWrite();
        try { return wire.boolRetBool(k, on); } finally { noteSoundWrite(); }
    }

    /** The stock eARC mode: true = Auto, false = Off. */
    public boolean getEarc() throws RemoteException { return wire.retBool(key(GET_EARC_ENABLE_STATE)); }
    public boolean setEarc(boolean on) throws RemoteException { return wire.boolRetBool(key(SET_EARC_ENABLE), on); }
    /** getEARCStatus(): 3 = eARC linked. */
    public int getEarcStatus() throws RemoteException { return wire.retI32(key(GET_EARC_STATUS)); }

    /** The stock "Power-on music", kept in the vendor's settings store. */
    public boolean getPowerOnMusic() throws RemoteException { return wire.retBool(key(IS_POWER_ON_MUSIC_ENABLED)); }
    public void setPowerOnMusic(boolean on) throws RemoteException { wire.bool(key(ENABLE_POWER_ON_MUSIC), on); }

    // ------------------------------------------------------------------ power diagnostics (read-only, logging only)
    public int getBootReason() throws RemoteException { return wire.retI8(key(GET_BOOT_REASON)); }
    public int getWakeUpSource() throws RemoteException { return wire.retI32(key(GET_WAKE_UP_SOURCE)); }
}
