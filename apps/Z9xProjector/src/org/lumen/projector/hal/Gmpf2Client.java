package org.lumen.projector.hal;

import static org.lumen.hal.gmpf.utils.Args.checkRange;

import android.os.RemoteException;

/**
 * Gmpf2Client of the Z9X: the shared IGmpf2 client (Gmpf2Base) over the HIDL interface
 *   xgimi.hardware.gmpf@1.0::IGmpf2/default
 * a SEPARATE interface from IGmpf with its own codes (they overlap IGmpf numbers with another meaning: never mix
 * the two clients), and the Z9X calls of its own: the keystone ratio reset, the optical zoom limit position, the game
 * sound and setKstPrepare of the manual keystone screen. Registered by the same vendor service as IGmpf. Connection:
 * on first use (no death recipient); a failed transaction drops the binder so the next call reconnects. NOT here on
 * purpose: 76 cleanAllCalibrationData, 236 / 237 radar, 303 enableAiPq, 203..206 pan-tilt, 279 getExtDecInfo.
 */
public final class Gmpf2Client extends Gmpf2Base<Integer> {
    public static final String IGMPF2 = "xgimi.hardware.gmpf@1.0::IGmpf2";

    /** The only 3D LUT files offered (FS 3.6 built-ins; vendor files, read by the HAL in place). */
    public static final String[] LUT_FILES = {
            "/mnt/vendor/xgimiconfig/G0082/HDR/3dlut0.cube",   // "Off"
            "/mnt/vendor/xgimiconfig/G0082/HDR/3dlut1.cube",
            "/mnt/vendor/xgimiconfig/G0082/HDR/3dlut2.cube",
            "/mnt/vendor/xgimiconfig/G0082/HDR/3dlut3.cube",
            "/mnt/vendor/xgimiconfig/G0082/HDR/3dlut4.cube",
    };

    /** setKstPrepare type of the manual keystone screen (stock KeyStoneWind). */
    private static final int KST_PREPARE_KEYSTONE = 0;
    /** doKeystoneRatio argument of the stock keystone reset: the ratio back to default. */
    private static final int KEYSTONE_RATIO_RESET = 3;
    /** setGamingSoundEffectMode: 190 off, 191 surround, 192 story. */
    private static final int GAME_SOUND_OFF = 190, GAME_SOUND_STORY = 192;

    public Gmpf2Client() {
        super(new HidlWire(IGMPF2, true), Z9xCodes.GMPF2, Z9xRules.RULES);
    }

    // ------------------------------------------------------------------ keystone
    /** 46 doKeystoneRatio(3) only: the last step of the stock keystone reset (stock shows a toast on false). */
    public boolean resetKeystoneRatio() throws RemoteException {
        wire.ready();
        return wire.i32RetBool(HalCodes.GMPF2_DO_KEYSTONE_RATIO, KEYSTONE_RATIO_RESET);
    }

    /**
     * 18 setKstPrepare(enter, 0): stock MiddleWareApi.notifyKeystone(isEnter, 0) around the manual keystone screen
     * ((true, 0) before the first corner move, (false, 0) when it closes). Caller: panel.ManualKeystonePanel only.
     */
    public boolean setKstPrepare(boolean enter) throws RemoteException {
        wire.ready();
        return wire.boolI32RetBool(HalCodes.GMPF2_SET_KST_PREPARE, enter, KST_PREPARE_KEYSTONE);
    }

    // ------------------------------------------------------------------ optical zoom limit position
    /** 13 GetCurrentZoomLimitStep() -> {ret, limit, digital}: 0 out of the limit zone, -1..-count inside it. */
    public int[] getZoomLimitStep() throws RemoteException { return readZoomLimitStep(); }

    /** 14 SetOpticalZoomLimitStep(step), as stock ZoomUtils; ZoomGuard.checkLimitStep refuses (nothing sent) otherwise. */
    public int setOpticalZoomLimitStep(int step) throws RemoteException { return writeZoomLimitStep(step); }

    // ------------------------------------------------------------------ v6.2 game module, Lumen game menu (2026-11-11)
    // The stock Z9X game menu (vendor xhplayer, gsi-stock analysis) sends these with exactly the
    // arguments allowed by Gmpf2Base. Caller: panel.GameMenu through Z9xGameIo (apps/core/lumen-menu/test/gamemenu
    // counts it), and only while game mode runs on that HDMI input. Every write there is read back with the getter here.

    /** 310 getAllmStatus(source 1|2). Caller: game.GameProfile. */
    public boolean getAllmStatus(int hdmiSource) throws RemoteException { return readAllmStatus(hdmiSource); }

    /** 300 getVRREnableState(source 1|2) -> stock 0 off / 1 on (-1 read as off). */
    public int getVrrState(int hdmiSource) throws RemoteException { return readVrr(hdmiSource); }

    /** 301 setVRRModeEnable(state 0|1, source 1|2) (stock GameModeApi.setVrr). */
    public int setVrrState(boolean on, int hdmiSource) throws RemoteException { return writeVrr(on, hdmiSource); }

    /** 298 getGamePictureMode(source 1|2) -> 0 standard, 1 FPS, 2 RPG, 3 RTS, 4 sports (-1 read as 0). */
    public int getGamePicture(int hdmiSource) throws RemoteException { return readGamePicture(hdmiSource); }

    /** 299 setGamePictureMode(mode 0..4, source 1|2) (stock GameModeApi.setPictureMode). */
    public int setGamePicture(int mode, int hdmiSource) throws RemoteException { return writeGamePicture(mode, hdmiSource); }

    /** 296 getDarkBoostState(game picture 0..4, source 1|2) -> bool (stock OtherViewModel). */
    public boolean getDarkScenes(int gamePicture, int hdmiSource) throws RemoteException {
        return readDarkBoost(gamePicture, hdmiSource);
    }

    /** 297 setDarkBoostOnoff(on, game picture 0..4, source 1|2): one value per game picture in the vendor. */
    public int setDarkScenes(boolean on, int gamePicture, int hdmiSource) throws RemoteException {
        return writeDarkBoost(on, gamePicture, hdmiSource);
    }

    /** 1 getGamingSoundEffectMode() -> 190 off, 191 surround, 192 story (0 read as off). */
    public int getGameSound() throws RemoteException {
        wire.ready();
        return wire.retI32(HalCodes.GMPF2_GET_GAMING_SOUND_EFFECT_MODE);
    }

    /** 2 setGamingSoundEffectMode(190..192) (stock GameModeApi.setGameSoundEffect). */
    public boolean setGameSound(int mode) throws RemoteException {
        wire.ready();
        return wire.i32RetBool(HalCodes.GMPF2_SET_GAMING_SOUND_EFFECT_MODE,
                checkRange("game sound", mode, GAME_SOUND_OFF, GAME_SOUND_STORY));
    }
    // ================================================================== PARKED (not compiled)
    // No reviewed caller outside hal/ (check_hal_callers.py fails the build on a transacting
    // method without one). Kept as reference: re-enable a method only together with its caller,
    // and only after a device test where its argument values are marked UNVERIFIED.
    //
    // /** 53 setAEOnOff(FALSE) only, as the first step of wall-colour adaptation (FS 2.8). TSV W=writeBool R=readInt32. */
    // public int disableAutoExposureForWallColor() throws RemoteException { need(); return callBoolRetI32(SET_AE_ON_OFF, false); }
    //
    // (v6.2: 310 getAllmStatus moved up, caller game.GameProfile.)
    //
    // /** 192 notifyAutokst(0): a key press cancelled a curtain notice (FS 2.6). Only 0. TSV W=writeInt32 R=readBool. */
    // public boolean notifyAutokstCancelled() throws RemoteException { need(); return callI32RetBool(NOTIFY_AUTOKST, 0); }
    //
    // (46 doKeystoneRatio(3), resetKeystoneRatio: un-parked in Lumen OS 1.0.8 for the stock keystone reset.)
    //
    // (269/270, 283/284, 29/31, 328/329, 330/323, 326/327: un-parked in Lumen OS 1.0.4 for the professional
    // picture settings, see that section.)
    //
    // /** 289 getCustomized3DLut() -> path (reply Status + hidl_string). TSV W= (cb hidl_string). */
    // public String get3dLut() throws RemoteException { need(); return call(GET_CUSTOMIZED_3D_LUT, request(), HwParcel::readString); }
    //
    // /**
    //  * 290 setCustomized3DLut(path): ONLY one of {@link #LUT_FILES} (index 0 = off). Our own LUTs are
    //  * not supported (SELinux readability by hal_gmpf UNVERIFIED). TSV W=writeBuffer+embedded hidl_string R=readBool.
    //  */
    // public boolean set3dLut(int index) throws RemoteException {
    //     checkRange("lut index", index, 0, LUT_FILES.length - 1);
    //     need();
    //     HwParcel q = request();
    //     q.writeString(LUT_FILES[index]);
    //     return call(SET_CUSTOMIZED_3D_LUT, q, RET_BOOL);
    // }
}
