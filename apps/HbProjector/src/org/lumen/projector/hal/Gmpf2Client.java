package org.lumen.projector.hal;

import android.os.RemoteException;

/**
 * Gmpf2Client of the Z9X HB: the shared IGmpf2 client (Gmpf2Base) over the HB AIDL managers (DisplayManager,
 * GmTvManager, SystemManager, ProjectorFocusManager; one connection state with GmpfClient), with HbCodes and HbRules,
 * and the HB calls of its own: the game menu calls of panel/HbGameIo and the output frame rate. setKstPrepare type 0
 * (manual keystone) is a REVIEW row (9.3) and stays refused; type 2 is the zoom session of Gmpf2Base.
 */
public final class Gmpf2Client extends Gmpf2Base<GmpfCall> {
    /** Z9X public API (the IGmpf2 descriptor); on the HB the Z9X IGmpf2 methods are spread over the managers. */
    public static final String IGMPF2 = "xgimi.hardware.gmpf.IGmpf2";
    /** Z9X public API (3D LUT files of the G0082); never sent on the HB. */
    public static final String[] LUT_FILES = {};
    /** getVRREnableState: 1 on. */
    private static final int ON = 1;

    public Gmpf2Client() {
        super(new AidlWire(), HbCodes.CODES, HbRules.RULES);
    }

    /** DISPLAY 185 setKstPrepare(enter, 0) is a REVIEW row: refused, nothing sent. */
    public boolean setKstPrepare(boolean enter) throws RemoteException {
        throw new AidlCaller.NotOnHbException("setKstPrepare(" + enter + ", 0) (not used on the HB)");
    }

    /** TV 131 getAllmStatus(source 1|2): the ALLM flag now received on that HDMI input. */
    public boolean getAllmStatus(int hdmiSource) throws RemoteException { return readAllmStatus(hdmiSource); }

    // ------------------------------------------------------------------ game menu (stock HB xhplayer GameModeApi / OtherViewModel)
    // src = 1 for HDMI 1, 2 for HDMI 2 (stock TV.inputToInt). Caller: panel/HbGameIo.

    /** TV 146 getVRREnableState(src): 1 on, everything else off (stock reads -1 as 0). */
    public boolean getVrr(int src) throws RemoteException { return readVrr(src) == ON; }

    /** TV 145 setVRRModeEnable(0 | 1, src) -> int (logged by the caller). */
    public int setVrr(boolean on, int src) throws RemoteException { return writeVrr(on, src); }

    /** TV 148 getGamePictureMode(src) -> 0..4; anything else (stock: -1) reads as 0. */
    public int getGamePicture(int src) throws RemoteException {
        int v = readGamePicture(src);
        return v < 0 || v > GAME_PICTURE_MAX ? 0 : v;
    }

    /** TV 147 setGamePictureMode(0..4, src) -> int. */
    public int setGamePicture(int mode, int src) throws RemoteException { return writeGamePicture(mode, src); }

    /** TV 150 getDarkBoostState(game picture, src): the game menu's dark enhance. */
    public boolean getDarkBoost(int gamePicture, int src) throws RemoteException { return readDarkBoost(gamePicture, src); }

    /** TV 149 setDarkBoostOnoff(on, game picture, src) -> int. */
    public int setDarkBoost(boolean on, int gamePicture, int src) throws RemoteException {
        return writeDarkBoost(on, gamePicture, src);
    }

    /*
     * Parked (2026-11-11 shared menu): the game bar's HDR item (HGiG) is gone with the HB game bar; the shared game menu
     * has no HDR item yet (devices/goat_elegant/menu.json pending game.hdr, REQUESTS-hb.md). Back with that item:
     *
     * TV 192 getHGiGMode().
     * public boolean getHgig() throws RemoteException { return callRetBool(Call.GET_HGIG_MODE); }
     * TV 191 setHGiGMode(bool).
     * public boolean setHgig(boolean on) throws RemoteException { return callBoolRetBool(Call.SET_HGIG_MODE, on); }
     */

    /** DISPLAY 179 getDispOutputFps(): the output frame rate (stock FPS overlay while VRR is on). */
    public int getOutputFps() throws RemoteException { return wire.retI32(GmpfCall.GET_DISP_OUTPUT_FPS); }
}
