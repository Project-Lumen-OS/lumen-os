package org.lumen.projector.hal;

import static org.lumen.hal.gmpf.GmpfOp.GET_CURRENT_KEYSTONE_MODE;
import static org.lumen.hal.gmpf.GmpfOp.GET_PROJECTOR_PUT_MODE;
import static org.lumen.hal.gmpf.GmpfOp.SET_SOUND_EFFECT;
import static org.lumen.hal.gmpf.utils.Args.checkRange;
import static org.lumen.hal.gmpf.utils.Args.oneOf;

import android.os.HwBlob;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.Process;
import android.os.RemoteException;

/**
 * The IGmpf client of the XGIMI Play 6 (xgimi.hardware.gmpf@1.0::IGmpf/default, a 32-bit vendor): the shared IGmpf
 * client (GmpfBase) over the Z9X HIDL transport with the Play 6 codes (Play6Codes, devices/dragon_healthy/hal/codes.tsv)
 * and rules (Play6Rules), plus the calls only the Play 6 makes. Value sets, bans and the boot handshake order:
 * tools/play6/check_play6_hal.py and check_handshake.py; the bytes: apps/core/lumen-gmpf/test/wire (dragon_healthy).
 */
public final class Play6Gmpf extends GmpfBase<Integer> {
    public static final String IGMPF = "xgimi.hardware.gmpf@1.0::IGmpf";

    /** EDID of the HDMI input (660 / 661): 1 HDMI 1.4, 2 HDMI 2.0. */
    public static final int EDID_V14 = 1, EDID_V20 = 2;
    /** getPictureModeRaw: the MStar Game picture mode, set by the vendor in game mode only (never sent). */
    public static final int PICTURE_GAME = 4;
    /** getAspectRatioRaw: 3 point to point (shown as Original); above ASPECT_MAX the panel repairs it. */
    public static final int ASPECT_POINT = 3, ASPECT_MAX = 4;
    /** getSoundProcessRaw: the AMP process, the Play 6 default (CustomerEnvTbl sound_process_type 2). */
    public static final int SOUND_PROCESS_AMP = 2;
    /** Sanity bounds of a digital zoom step (the stock dial has tens of steps). */
    public static final int ZOOM_STEP_MIN = -200, ZOOM_STEP_MAX = 200;

    private static final int KST_MODE_FOUR_POINT = 0;
    private static final int HDMI_INFO_SIZE = 16;
    private static final int RGB_LEVEL_SIZE = 4, RGB_LEVEL_MIN = 1, RGB_LEVEL_MAX = 10, RGB_LEVEL_C = 0;
    private static final int AREA_ORIGIN = 0, AREA_WIDTH = 1920, AREA_HEIGHT = 1080;
    private static final int COLOR_TEMPERATURE_MAX = 3;
    private static final int RESET_CUSTOM_PICTURE = 3;

    private final HidlWire hidl;

    /** The boot handshake's client: no focus listener. */
    public Play6Gmpf() {
        this(null);
    }

    /** The facade's client: the focus events of 298 / 306 go to sink. */
    public Play6Gmpf(FocusEventSink sink) {
        this(new HidlWire(IGMPF, false), sink);
    }

    private Play6Gmpf(HidlWire hidl, FocusEventSink sink) {
        super(hidl, Play6Codes.GMPF, Play6Rules.RULES);
        this.hidl = hidl;
        if (sink != null) HidlFocusCallback.attach(sink);
    }

    /** HwBinder.getService(IGmpf, "default", retry=false), no death recipient. Never throws. */
    public boolean connect() {
        return hidl.connect(null, 0);
    }

    /** The same with linkToDeath (the facade's instance). Never throws. */
    public boolean connect(IHwBinder.DeathRecipient death, long cookie) {
        return hidl.connect(death, cookie);
    }

    // ------------------------------------------------------------------ Play 6 value rules of shared calls
    /** setSoundeffect: every Play 6 sound mode, Standard too (stock dayao tree). */
    @Override
    public void setSoundEffect(SoundEffect e) throws RemoteException { wire.u8(key(SET_SOUND_EFFECT), e.wire); }

    /** switchInputSourceWithCallingPid(0 Android | 1 HDMI, our pid): the Play 6 has one HDMI port. */
    @Override
    public boolean switchInputSource(int source) throws RemoteException {
        if (!oneOf(source, SOURCE_MEDIA, SOURCE_HDMI1)) throw new IllegalArgumentException("input source " + source + " not allowed");
        return super.switchInputSource(source);
    }

    // ------------------------------------------------------------------ keystone
    /** getCurrentKeystoneMode() (logged). */
    public int getKeystoneMode() throws RemoteException { return wire.retI32(key(GET_CURRENT_KEYSTONE_MODE)); }

    /** Stock V3DisplayManager.setIChipReset without 181 (banned): returns the getCurrentKeystoneMode read. */
    public int resetKeystone() throws RemoteException { return chipReset(); }

    /** getCorrectKeystoneFullPiont(0): the 4 corners; a corner off the 1920 x 1080 panel fails the reply. */
    public Play6KstPoint getKeystonePoints() throws RemoteException {
        HwParcel q = hidl.request();
        q.writeInt8((byte) KST_MODE_FOUR_POINT);
        try {
            return hidl.send(HalCodes.GMPF_GET_CORRECT_KEYSTONE_FULL_PIONT, q, r -> Play6KstPoint.fromBlob(r.readBuffer(Play6KstPoint.WIRE_SIZE)));
        } catch (IllegalArgumentException e) {
            throw new ReplyException("186 corner out of range: " + e.getMessage());
        }
    }

    /** checkTrapezoidCoordinate(points): true when the vendor accepts the corners. */
    public boolean checkKeystonePoints(Play6KstPoint p) throws RemoteException {
        HwParcel q = hidl.request();
        q.writeBuffer(p.toBlob());
        return hidl.send(HalCodes.GMPF_CHECK_TRAPEZOID_COORDINATE, q, HidlCaller.RET_BOOL);
    }

    /** correctKeystoneFullPiont(points), after checkKeystonePoints with the same points. */
    public int applyKeystonePoints(Play6KstPoint p) throws RemoteException {
        HwParcel q = hidl.request();
        q.writeBuffer(p.toBlob());
        return hidl.send(HalCodes.GMPF_CORRECT_KEYSTONE_FULL_PIONT, q, HidlCaller.RET_I32);
    }

    // ------------------------------------------------------------------ projection, game
    /** getProjectorPutMode() raw (0 front table .. 3 rear ceiling). */
    public int getPutModeRaw() throws RemoteException { return wire.retU8(key(GET_PROJECTOR_PUT_MODE)); }

    /** getGameModeOption() raw. */
    public int getGameModeOptionRaw() throws RemoteException { return wire.retI32(HalCodes.GMPF_GET_GAME_MODE_OPTION); }

    /** setGameModeOption(the value getGameModeOption reports now): the game-mode On path only. */
    public boolean applyGameModeOption() throws RemoteException {
        int opt = wire.retI32(HalCodes.GMPF_GET_GAME_MODE_OPTION);
        return wire.i32RetBool(HalCodes.GMPF_SET_GAME_MODE_OPTION, opt);
    }

    // ------------------------------------------------------------------ HDMI
    /** getSignalStatus(): the HDMI signal state, for the HDMI fallback only. */
    public int getSignalStatus() throws RemoteException { return wire.retI32(HalCodes.GMPF_GET_SIGNAL_STATUS); }

    /** setDisplayArea(0, 0, 1920, 1080): the HDMI picture over the whole panel (HdmiSource's debug fallback). */
    public boolean setFullDisplayArea() throws RemoteException {
        HwParcel q = hidl.request();
        q.writeInt32(AREA_ORIGIN);
        q.writeInt32(AREA_ORIGIN);
        q.writeInt32(AREA_WIDTH);
        q.writeInt32(AREA_HEIGHT);
        return hidl.send(HalCodes.GMPF_SET_DISPLAY_AREA, q, HidlCaller.RET_BOOL);
    }

    /** getHdmiInfo() -> {width, height, interlace 0/1, hdmi mode 0/1, frame rate x 100}. */
    public int[] getHdmiInfo() throws RemoteException {
        return hidl.send(HalCodes.GMPF_GET_HDMI_INFO, hidl.request(), r -> {
            HwBlob b = r.readBuffer(HDMI_INFO_SIZE);
            return new int[] {b.getInt32(0), b.getInt32(4), b.getBool(8) ? 1 : 0, b.getBool(9) ? 1 : 0, b.getInt32(12)};
        });
    }

    /** getHdmiAutoSwitch(): the vendor HDMI plug-and-play switch (Diagnostics). */
    public boolean getHdmiAutoSwitch() throws RemoteException { return wire.retBool(HalCodes.GMPF_GET_HDMI_AUTO_SWITCH); }

    // ------------------------------------------------------------------ picture (Custom mode)
    /** getDlpLumensComponent_RGB_Level() -> {R, G, B, C}: the light levels of the Custom picture style. */
    public int[] getRgbLevels() throws RemoteException {
        return hidl.send(HalCodes.GMPF_GET_DLP_LUMENS_COMPONENT_RGB_LEVEL, hidl.request(), r -> {
            HwBlob b = r.readBuffer(RGB_LEVEL_SIZE);
            return new int[] {b.getInt8(0) & 0xff, b.getInt8(1) & 0xff, b.getInt8(2) & 0xff, b.getInt8(3) & 0xff};
        });
    }

    /** setDlpLumensComponent_RGB_Level({R, G, B, 0}), each 1..10, as stock BrightnessManager. */
    public void setRgbLevels(int red, int green, int blue) throws RemoteException {
        HwBlob b = new HwBlob(RGB_LEVEL_SIZE);
        b.putInt8(0, (byte) checkRange("red level", red, RGB_LEVEL_MIN, RGB_LEVEL_MAX));
        b.putInt8(1, (byte) checkRange("green level", green, RGB_LEVEL_MIN, RGB_LEVEL_MAX));
        b.putInt8(2, (byte) checkRange("blue level", blue, RGB_LEVEL_MIN, RGB_LEVEL_MAX));
        b.putInt8(3, (byte) RGB_LEVEL_C);
        HwParcel q = hidl.request();
        q.writeBuffer(b);
        hidl.send(HalCodes.GMPF_SET_DLP_LUMENS_COMPONENT_RGB_LEVEL, q, HidlCaller.RET_VOID);
    }

    /** getColorTemperature(0) -> 0 cool, 1 standard, 2 warm, 3 custom. */
    public int getColorTemperature() throws RemoteException { return wire.i32RetI32(HalCodes.GMPF_GET_COLOR_TEMPERATURE, WINDOW_MAIN); }

    public boolean setColorTemperature(int v) throws RemoteException {
        return wire.i32I32RetBool(HalCodes.GMPF_SET_COLOR_TEMPERATURE, WINDOW_MAIN, checkRange("color temperature", v, 0, COLOR_TEMPERATURE_MAX));
    }

    /** getHdrEnable(): HDR auto (true) or off. */
    public boolean getHdrAuto() throws RemoteException { return wire.retBool(HalCodes.GMPF_GET_HDR_ENABLE); }

    public boolean setHdrAuto(boolean on) throws RemoteException { return wire.boolRetBool(HalCodes.GMPF_SET_HDR_ENABLE, on); }

    /** resetPqSettings(3): the stock Reset of the Custom picture mode. */
    public int resetCustomPicture() throws RemoteException { return wire.i32RetI32(HalCodes.GMPF_RESET_PQ_SETTINGS, RESET_CUSTOM_PICTURE); }

    // ------------------------------------------------------------------ power board, battery, system (read only but eco)
    public int getBatteryLevel() throws RemoteException { return wire.retU8(HalCodes.GMPF_GET_BATTERY_LEVEL); }

    public boolean isAdapterPowered() throws RemoteException { return wire.retBool(HalCodes.GMPF_IS_ADAPTER_POWERED); }

    public boolean isBatteryFull() throws RemoteException { return wire.retBool(HalCodes.GMPF_IS_BATTERY_CHARGED_FULL); }

    /** getEcoState(): power saving is on (the vendor also switches it by itself). */
    public boolean getEcoState() throws RemoteException { return wire.retBool(HalCodes.GMPF_GET_ECO_STATE); }

    /** setEcoState(on): the user's Power saving switch only. */
    public void setEcoState(boolean on) throws RemoteException { wire.bool(HalCodes.GMPF_SET_ECO_STATE, on); }

    /** getAllFeatureList(): may take long inside the HAL (the long thread). */
    public String getAllFeatureList() throws RemoteException {
        return hidl.send(HalCodes.GMPF_GET_ALL_FEATURE_LIST, hidl.request(), HwParcel::readString);
    }

    // ------------------------------------------------------------------ vendor listeners (pid, then the callback)
    /** connectPowerManager(pid, callback), then setPowerListener with the same callback, as stock PowerManager. */
    public void connectPowerManager(Play6PowerCallback cb) throws RemoteException { register(HalCodes.GMPF_CONNECT_POWER_MANAGER, cb); }

    public void setPowerListener(Play6PowerCallback cb) throws RemoteException { register(HalCodes.GMPF_SET_POWER_LISTENER, cb); }

    /** connectMusicPlayerManager(pid, callback), then setMusicPlayerListener, as stock MusicPlayerManager. */
    public void connectMusicPlayer(Play6MusicPlayerCallback cb) throws RemoteException {
        register(HalCodes.GMPF_CONNECT_MUSIC_PLAYER_MANAGER, cb);
    }

    public void setMusicPlayerListener(Play6MusicPlayerCallback cb) throws RemoteException {
        register(HalCodes.GMPF_SET_MUSIC_PLAYER_LISTENER, cb);
    }

    private void register(int code, IHwBinder cb) throws RemoteException {
        if (cb == null) throw new RemoteException("callback must be non-null");
        HwParcel q = hidl.request();
        q.writeInt32(Process.myPid());
        q.writeStrongBinder(cb);
        hidl.send(code, q, HidlCaller.RET_VOID);
    }
}
