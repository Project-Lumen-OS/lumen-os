package org.lumen.projector.hal;

import static org.lumen.hal.gmpf.GmpfOp.*;
import static org.lumen.hal.gmpf.utils.Args.checkRange;
import static org.lumen.hal.gmpf.utils.Args.oneOf;

import android.os.RemoteException;

import org.lumen.hal.gmpf.GmpfCaller;
import org.lumen.hal.gmpf.GmpfCodes;
import org.lumen.hal.gmpf.GmpfGate;
import org.lumen.hal.gmpf.GmpfRules;
import org.lumen.hal.gmpf.GmpfWire;
import org.lumen.hal.gmpf.ZoomGuard;

/**
 * The IGmpf2 client of every projector built on the Z9X app (the Z9X IGmpf2 interface; on the HB the same methods
 * live in its AIDL managers); each device's Gmpf2Client extends it. Same rules as GmpfBase: typed, whitelisted,
 * checked arguments, nothing sent for an absent call or a switched-off feature. The structs are checked inside the
 * reply (a false return, a misread or an implausible value fails the call as a ReplyException).
 */
public abstract class Gmpf2Base<K> extends GmpfCaller<K> {
    /** Colours of the HSY arrays, in array order (stock ColorCorrection): red, green, blue, cyan, magenta, yellow, skin. */
    public static final int HSY_COLORS = 7;
    /** The 11 points of multi-point white balance (stock image_segmentation): 5, 10, 20 ... 100 (% of white). */
    public static final int[] WB_POINTS = {5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    static final int WINDOW_MAIN = 0;
    private static final int ON = 1, OFF = 0;
    private static final int PERCENT_MAX = 100;
    /** setGameModeState: 0 on, 1 off (stock). */
    private static final int GAME_MODE_ON = 0, GAME_MODE_OFF = 1;
    /** setKstPrepare type of the optical zoom dial (stock ZoomStateView). */
    private static final int KST_PREPARE_ZOOM = 2;
    /** setScreenRotation directions and the largest relative step. */
    private static final int ROTATE_CLOCKWISE = 5, ROTATE_COUNTER_CLOCKWISE = 6;
    private static final float ROTATE_STEP_MAX = 10f;
    /** The plausible rotation range of getScreenRotationInfo, and the read tolerance of the current angle. */
    private static final float ROTATION_LIMIT = 45f, ROTATION_SLACK = 0.01f;
    private static final int ROTATION_FIELDS = 4, GAME_PROP_FIELDS = 3, MULTI_WB_FIELDS = 4;
    /** Gamma: 1.8..2.6 = 0..8, BT.1886 / ST.2084 / HLG / DICOM 100..800 = 10..17. */
    private static final int GAMMA_INDEX_MAX = 8, GAMMA_CURVE_MIN = 10, GAMMA_CURVE_MAX = 17;
    /** Game picture of the game menu: 0 Standard, 1 FPS, 2 RPG, 3 RTS, 4 Sports. */
    public static final int GAME_PICTURE_MAX = 4;
    /** The HDMI source of the game menu calls (stock TV.inputToInt): 1 HDMI 1, 2 HDMI 2. */
    private static final int HDMI_1 = 1, HDMI_2 = 2;
    /** The action of the stock power menu's "Turn off screen" (PowerUtils.shutScreen). */
    private static final int SHUT_SCREEN_ACTION = 1;

    protected Gmpf2Base(GmpfWire<K> wire, GmpfCodes<K> codes, GmpfRules rules) {
        super(wire, codes, rules);
    }

    /** Gamma (stock 3.5): 0..8 (1.8..2.6) and 10..17 (curves). */
    public static boolean isValidGamma(int g) {
        return (g >= 0 && g <= GAMMA_INDEX_MAX) || (g >= GAMMA_CURVE_MIN && g <= GAMMA_CURVE_MAX);
    }

    // ------------------------------------------------------------------ brightness Boost
    /** getLensDispMode() -> 1 = Boost. */
    public boolean isBoost() throws RemoteException { return wire.retI32(key(GET_LENS_DISP_MODE)) == ON; }
    /**
     * setLensDispMode(1 Boost | 0 normal): the colour wheel sequence, not the laser current. Keep the stock two-press
     * confirmation and refuse in 3D, as stock. Leaving Boost is always allowed.
     */
    public boolean setBoost(boolean on) throws RemoteException {
        if (on) gate(GmpfGate.BOOST, "setBoost(true)");
        return wire.i32RetBool(key(SET_LENS_DISP_MODE), on ? ON : OFF);
    }

    // ------------------------------------------------------------------ game mode / ALLM
    /** setGameModeType(0 manual | 1 auto). */
    public boolean setGameModeType(GameModeType t) throws RemoteException { return wire.i32RetBool(key(SET_GAME_MODE_TYPE), t.wire); }
    /** setGameModeState(0 on | 1 off). */
    public boolean setGameModeState(boolean on) throws RemoteException {
        return wire.i32RetBool(key(SET_GAME_MODE_STATE), on ? GAME_MODE_ON : GAME_MODE_OFF);
    }

    /** getGameModeProp() -> {state, type, gameModeOpt}; callers decode it with {@link #gameModeOf}. */
    public int[] getGameModeProp() throws RemoteException { return wire.boolStruct(key(GET_GAME_MODE_PROP), GAME_PROP_FIELDS); }

    /**
     * Game mode of a getGameModeProp reply {state, type, opt}: 2 auto (type 1), 1 on (state 0), 0 off (state 1); -1
     * when it does not fit (null, opt != the getGameModeOption read just before, state or type not 0 / 1). No HAL call.
     */
    public static int gameModeOf(int[] p, int opt647) {
        if (p == null || p.length < 3 || p[2] != opt647 || (p[0] != 0 && p[0] != 1) || (p[1] != 0 && p[1] != 1)) return -1;
        if (p[1] == 1) return 2;
        return p[0] == 0 ? 1 : 0;
    }

    public boolean getAllmAutoSwitch() throws RemoteException { return wire.retBool(key(GET_ALLM_ENABLE_STATE)); }
    /** setAllmEnable(bool): true = switch to game mode on ALLM content. */
    public boolean setAllmAutoSwitch(boolean on) throws RemoteException { return wire.boolRetBool(key(SET_ALLM_ENABLE), on); }

    // ------------------------------------------------------------------ game menu (stock xhplayer GameModeApi / OtherViewModel)
    // Every projector names these for its own game menu adapter (Z9xGameIo, HbGameIo); the wire is the same.

    /** getAllmStatus(hdmi): the ALLM flag now received on that HDMI input. */
    protected final boolean readAllmStatus(int hdmi) throws RemoteException {
        wire.ready();
        return wire.i32RetBool(key(GET_ALLM_STATUS), hdmi(hdmi));
    }

    /** getVRREnableState(hdmi) -> stock 0 off / 1 on (-1 when unknown). */
    protected final int readVrr(int hdmi) throws RemoteException {
        wire.ready();
        return wire.i32RetI32(key(GET_VRR_ENABLE_STATE), hdmi(hdmi));
    }

    /** setVRRModeEnable(0 | 1, hdmi). */
    protected final int writeVrr(boolean on, int hdmi) throws RemoteException {
        wire.ready();
        return wire.i32I32RetI32(key(SET_VRR_MODE_ENABLE), on ? ON : OFF, hdmi(hdmi));
    }

    /** getGamePictureMode(hdmi) -> 0..GAME_PICTURE_MAX (-1 when unknown). */
    protected final int readGamePicture(int hdmi) throws RemoteException {
        wire.ready();
        return wire.i32RetI32(key(GET_GAME_PICTURE_MODE), hdmi(hdmi));
    }

    /** setGamePictureMode(mode, hdmi). */
    protected final int writeGamePicture(int mode, int hdmi) throws RemoteException {
        wire.ready();
        return wire.i32I32RetI32(key(SET_GAME_PICTURE_MODE), gamePicture(mode), hdmi(hdmi));
    }

    /** getDarkBoostState(game picture, hdmi): one value per game picture in the vendor. */
    protected final boolean readDarkBoost(int gamePicture, int hdmi) throws RemoteException {
        wire.ready();
        return wire.i32I32RetBool(key(GET_DARK_BOOST_STATE), gamePicture(gamePicture), hdmi(hdmi));
    }

    /** setDarkBoostOnoff(on, game picture, hdmi). */
    protected final int writeDarkBoost(boolean on, int gamePicture, int hdmi) throws RemoteException {
        wire.ready();
        return wire.boolI32I32RetI32(key(SET_DARK_BOOST_ONOFF), on, gamePicture(gamePicture), hdmi(hdmi));
    }

    private static int hdmi(int v) { return checkRange("hdmi source", v, HDMI_1, HDMI_2); }

    private static int gamePicture(int v) { return checkRange("game picture", v, 0, GAME_PICTURE_MAX); }

    // ------------------------------------------------------------------ focus / keystone
    /** Smart autofocus (stock FocusRepository.setSmartAutoFocus). */
    public boolean getSmartAutofocus() throws RemoteException { return wire.retBool(key(GET_POWER_ON_AF_USE_DISTANCE)); }
    public boolean setSmartAutofocus(boolean on) throws RemoteException { return wire.boolRetBool(key(SET_POWER_ON_AF_USE_DISTANCE), on); }

    /** setKstPrepare(enter, 2) around an optical zoom session, as stock ZoomStateView. Caller: panel.OpticalZoomPanel. */
    public boolean setKstPrepareForZoom(boolean enter) throws RemoteException {
        gate(GmpfGate.OPTICAL_ZOOM, "setKstPrepare(" + enter + ", 2)");
        K k = key(SET_KST_PREPARE);
        if (!enter) ZoomGuard.prepare(false);
        boolean r = wire.boolI32RetBool(k, enter, KST_PREPARE_ZOOM);
        if (enter) ZoomGuard.prepare(true);
        return r;
    }

    // ------------------------------------------------------------------ zoom / shift / rotation screens
    /** setPushPullScreenState(1) while a zoom, shift or rotation screen is open, (0) when it closes, as stock. */
    public boolean setAdjustScreenOpen(boolean open) throws RemoteException {
        return wire.i32RetBool(key(SET_PUSH_PULL_SCREEN_STATE), open ? ON : OFF);
    }

    /** getScreenRotationInfo() -> {minAng, maxAng, curAng, startAng}; fails unless plausible: nothing rotates on a misread. */
    public float[] getScreenRotation() throws RemoteException {
        gate(GmpfGate.ROTATION, "getScreenRotationInfo");
        K k = key(GET_SCREEN_ROTATION_INFO);
        int code = wire.code(k);
        return wire.boolFloats(k, ROTATION_FIELDS, v -> {
            float min = v[0], max = v[1], cur = v[2], start = v[3];
            if (!(min >= -ROTATION_LIMIT && min < 0f && max > 0f && max <= ROTATION_LIMIT
                    && cur >= min - ROTATION_SLACK && cur <= max + ROTATION_SLACK)) {
                throw new IllegalStateException(code + " struct " + min + "," + max + "," + cur + "," + start);
            }
        });
    }

    /** setScreenRotation(5 clockwise | 6 counter-clockwise, degrees > 0, relative), one step per key as stock. */
    public boolean rotateScreen(boolean clockwise, float degrees) throws RemoteException {
        gate(GmpfGate.ROTATION, "setScreenRotation");
        if (!(degrees > 0f && degrees <= ROTATE_STEP_MAX)) throw new IllegalArgumentException("rotation step " + degrees);
        return wire.i32F32RetBool(key(SET_SCREEN_ROTATION), clockwise ? ROTATE_CLOCKWISE : ROTATE_COUNTER_CLOCKWISE, degrees);
    }

    /**
     * The stock power menu's "Turn off screen": setScreenOnOffWithAction(false, 1), as PowerUtils.shutScreen sends it
     * (GmDisplayManager, xgimi.api.version 492 and up). false = the vendor did not do it (stock then sends
     * setScreenOnOff(false)).
     */
    public boolean shutScreen() throws RemoteException {
        return wire.boolI32RetBool(key(SET_SCREEN_ON_OFF_WITH_ACTION), false, SHUT_SCREEN_ACTION);
    }

    /** setPushPullScreenOnOff(FALSE) only, when switching to ceiling mount. */
    public boolean disablePushPullScreenForCeiling() throws RemoteException {
        return wire.boolRetBool(key(SET_PUSH_PULL_SCREEN_ON_OFF), false);
    }

    // ------------------------------------------------------------------ AI / picture extras (window 0)
    /** getAisrType() -> 0 off / 1 on (super resolution). */
    public boolean getSuperResolution() throws RemoteException { return wire.retI32(key(GET_AISR_TYPE)) == ON; }
    public boolean setSuperResolution(boolean on) throws RemoteException { return wire.i32RetBool(key(SET_AISR_TYPE), on ? ON : OFF); }

    /** getAIContrastStatus(0) -> 0 / 1. */
    public boolean getAiContrast() throws RemoteException { return wire.i32RetI32(key(GET_AI_CONTRAST_STATUS), WINDOW_MAIN) == ON; }
    /** toggleAIContrast(0, 0|1). SDR only. */
    public boolean setAiContrast(boolean on) throws RemoteException {
        return wire.i32I32RetBool(key(TOGGLE_AI_CONTRAST), WINDOW_MAIN, on ? ON : OFF);
    }

    // ------------------------------------------------------------------ professional picture settings (window 0, read back by the panel)
    /** getTvGammaLevel(0) -> 0..8 (gamma 1.8..2.6) or 10..17 (curves). */
    public int getGamma() throws RemoteException { return wire.i32RetI32(key(GET_TV_GAMMA_LEVEL), WINDOW_MAIN); }
    /** setTvGammaLevel(0, g), g per {@link #isValidGamma}. */
    public boolean setGamma(int g) throws RemoteException {
        if (!isValidGamma(g)) throw new IllegalArgumentException("gamma " + g);
        return wire.i32I32RetBool(key(SET_TV_GAMMA_LEVEL), WINDOW_MAIN, g);
    }

    /** GetHDRDyToneMappingStatus(0) == 1. Shown only while HDR10 plays (stock). */
    public boolean getHdrDynamicToneMapping() throws RemoteException {
        return wire.i32RetI32(key(GET_HDR_DY_TONE_MAPPING_STATUS), WINDOW_MAIN) == ON;
    }
    public boolean setHdrDynamicToneMapping(boolean on) throws RemoteException {
        return wire.i32I32RetBool(key(TOGGLE_HDR_DY_TONE_MAPPING), WINDOW_MAIN, on ? ON : OFF);
    }

    /** getFDBoostStatus(0) == 1 ("original frame rate"). */
    public boolean getNativeFrameRate() throws RemoteException { return wire.i32RetI32(key(GET_FD_BOOST_STATUS), WINDOW_MAIN) == ON; }
    /** toggleFDBoost(0, 0|1). Stock turns Ultra 120 Hz off with it and refuses motion smoothing while on; the panel does both. */
    public boolean setNativeFrameRate(boolean on) throws RemoteException {
        return wire.i32I32RetBool(key(TOGGLE_FD_BOOST), WINDOW_MAIN, on ? ON : OFF);
    }

    /** getDynamicBlackType() == 1 ("dynamic black enhancement"). */
    public boolean getDynamicBlack() throws RemoteException { return wire.retI32(key(GET_DYNAMIC_BLACK_TYPE)) == ON; }
    public boolean setDynamicBlack(boolean on) throws RemoteException { return wire.i32RetBool(key(SET_DYNAMIC_BLACK_TYPE), on ? ON : OFF); }

    public boolean getDynamicContrast() throws RemoteException {
        return wire.i32RetBool(key(GET_TV_DYNAMIC_CONTRAST_ENABLE), WINDOW_MAIN);
    }
    public boolean setDynamicContrast(boolean on) throws RemoteException {
        return wire.i32BoolRetBool(key(SET_TV_DYNAMIC_CONTRAST_ENABLE), WINDOW_MAIN, on);
    }

    /** getHsyPictureModeEnable(0) ("colour optimisation"). */
    public boolean getColorOptimization() throws RemoteException {
        return wire.i32RetBool(key(GET_HSY_PICTURE_MODE_ENABLE), WINDOW_MAIN);
    }
    public boolean setColorOptimization(boolean on) throws RemoteException {
        return wire.i32BoolRetBool(key(SET_HSY_PICTURE_MODE_ENABLE), WINDOW_MAIN, on);
    }

    /**
     * getHsyColorModel(0, type) -> the 7 values (0..100, 50 = unchanged) of red, green, blue, cyan, magenta, yellow,
     * skin. A false or a value outside 0..100 fails the call: the panel then hides colour correction.
     */
    public int[] getHsyColorModel(HsyType t) throws RemoteException {
        K k = key(GET_HSY_COLOR_MODEL);
        int code = wire.code(k);
        return wire.i32I32BoolBytes(k, WINDOW_MAIN, t.wire, HSY_COLORS, v -> {
            for (int i = 0; i < v.length; i++) {
                if (v[i] < 0 || v[i] > PERCENT_MAX) throw new IllegalStateException(code + " value " + v[i] + " at " + i);
            }
        });
    }

    /** setHsyColorModel(0, type, 7 values 0..100): the whole array, as stock (one changed colour, six as read). */
    public boolean setHsyColorModel(HsyType t, int[] values) throws RemoteException {
        if (values == null || values.length != HSY_COLORS) throw new IllegalArgumentException("hsy array");
        byte[] b = new byte[HSY_COLORS];
        for (int i = 0; i < HSY_COLORS; i++) b[i] = (byte) checkRange("hsy value", values[i], 0, PERCENT_MAX);
        return wire.i32I32BytesRetBool(key(SET_HSY_COLOR_MODEL), WINDOW_MAIN, t.wire, b);
    }

    /**
     * getOneOfMultiPointWB(point) -> {red, green, blue} 0..100. The struct {per_gain, red, green, blue} must carry
     * per_gain == point and three values 0..100, else the call fails and the panel hides 11-point white balance.
     */
    public int[] getMultiPointWb(int point) throws RemoteException {
        if (!oneOf(point, WB_POINTS)) throw new IllegalArgumentException("wb point " + point);
        K k = key(GET_ONE_OF_MULTI_POINT_WB);
        int code = wire.code(k);
        int[] v = wire.i32BoolStruct(k, point, MULTI_WB_FIELDS, s -> {
            int pg = s[0], red = s[1], green = s[2], blue = s[3];
            if (pg != point || red < 0 || red > PERCENT_MAX || green < 0 || green > PERCENT_MAX || blue < 0 || blue > PERCENT_MAX) {
                throw new IllegalStateException(code + " struct " + pg + "," + red + "," + green + "," + blue + " for " + point);
            }
        });
        return new int[] {v[1], v[2], v[3]};
    }

    /** setOneOfMultiPointWB(point, {point, red, green, blue}), as stock. */
    public boolean setMultiPointWb(int point, int red, int green, int blue) throws RemoteException {
        if (!oneOf(point, WB_POINTS)) throw new IllegalArgumentException("wb point " + point);
        int[] s = {point, checkRange("wb red", red, 0, PERCENT_MAX), checkRange("wb green", green, 0, PERCENT_MAX),
                checkRange("wb blue", blue, 0, PERCENT_MAX)};
        return wire.i32StructRetBool(key(SET_ONE_OF_MULTI_POINT_WB), point, s);
    }

    /** ResetPictureToDefault(one of this projector's stock triples). */
    public boolean resetPicture(PictureReset r) throws RemoteException {
        return wire.i32x3RetBool(key(RESET_PICTURE_TO_DEFAULT), r.a, r.b, r.c);
    }
}
