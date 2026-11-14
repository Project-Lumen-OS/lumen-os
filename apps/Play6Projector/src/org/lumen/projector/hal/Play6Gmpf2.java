package org.lumen.projector.hal;

import static org.lumen.hal.gmpf.GmpfOp.SET_SCREEN_ROTATION;
import static org.lumen.hal.gmpf.GmpfOp.SET_TV_GAMMA_LEVEL;
import static org.lumen.hal.gmpf.utils.Args.checkRange;

import android.os.RemoteException;
import android.util.Log;

/**
 * The IGmpf2 client of the XGIMI Play 6: the shared IGmpf2 client (Gmpf2Base) over Play6Gmpf2Wire, the identity guard
 * and the vendor code tables of the Play 6 IGmpf2 (its numbers are not the Z9X ones), with the Play 6 codes and rules.
 * Every call checks the guard first; a call the open vendor build does not have throws AbsentException, nothing sent.
 */
public final class Play6Gmpf2 extends Gmpf2Base<Integer> {
    /** The IGmpf2 hashes the guard accepts (the stock v1.7.116 and v1.1.67 vendor libraries), for the diagnostics. */
    public static final String RECORDED_HASH = Gmpf2Vendor.V1_7_116.hash, RECORDED_HASH_V1_1_67 = Gmpf2Vendor.V1_1_67.hash;
    /** IBase hash: element 1 of every chain. */
    public static final String IBASE_HASH = "ec7fd79ed02dfa85bc499426adae3ebe23ef0524f3cd6957139324b83b18ca4c";
    /** setSlideFunc: 0 closing shuts down, 1 closing turns the projector into the ambient speaker. */
    public static final int SLIDE_POWER = 0, SLIDE_AMBIENT_SPEAKER = 1;

    private static final int GAMMA_MAX = 8;
    private static final int ROTATE_CLOCKWISE = 5, ROTATE_COUNTER_CLOCKWISE = 6;
    private static final float ROTATION_STEP_MAX = 45f;
    private static final int HDMI_SOURCE_1 = 1;

    private final Play6Gmpf2Wire guard;

    public Play6Gmpf2() {
        this(new Play6Gmpf2Wire());
    }

    private Play6Gmpf2(Play6Gmpf2Wire guard) {
        super(guard, Play6Codes.GMPF2, Play6Rules.RULES);
        this.guard = guard;
    }

    // ------------------------------------------------------------------ identity guard
    /** The vendor build whose recorded IGmpf2 hash this is ("v1.7.116" / "v1.1.67"), or null. */
    public static String recordedVendor(String hash) {
        Gmpf2Vendor v = Gmpf2Vendor.of(hash);
        return v == null ? null : v.label;
    }

    /** Checks the identity of IGmpf2/default now: true = the guard is open. */
    public boolean verifyIdentity() {
        try {
            guard.need();
            return true;
        } catch (Throwable t) {
            if (!(t instanceof IdentityException)) Log.w(GmpfBase.TAG, "IGmpf2 identity not checked: " + t);
            return false;
        }
    }

    /** True while the guard is open for the current connection (no call). */
    public boolean isIdentityOpen() { return guard.isOpen(); }

    /** The live IBase hash chain of IGmpf2/default as hex, for the diagnostics. */
    public String[] liveHashChain() throws RemoteException { return guard.liveHashChain(); }

    // ------------------------------------------------------------------ Play 6 value rules of shared calls
    /** The relative step of one setScreenRotation: (0, 45] degrees (a session undo is at most max - min). */
    public static float checkRotationStep(String what, float degrees) {
        if (!(degrees > 0f && degrees <= ROTATION_STEP_MAX)) throw new IllegalArgumentException(what + " " + degrees + " not in (0, 45]");
        return degrees;
    }

    @Override

    public boolean rotateScreen(boolean clockwise, float degrees) throws RemoteException {
        Integer k = key(SET_SCREEN_ROTATION);
        return wire.i32F32RetBool(k, clockwise ? ROTATE_CLOCKWISE : ROTATE_COUNTER_CLOCKWISE, checkRotationStep("rotation step", degrees));
    }

    /** setTvGammaLevel(0, 0..8) = gamma 1.8 .. 2.6, the Play 6 values (Custom only). */
    @Override
    public boolean setGamma(int index) throws RemoteException {
        Integer k = key(SET_TV_GAMMA_LEVEL);
        return wire.i32I32RetBool(k, WINDOW_MAIN, checkRange("gamma", index, 0, GAMMA_MAX));
    }

    // ------------------------------------------------------------------ Play 6 calls
    /** getAllmStatus(1): ALLM received on the HDMI port now (Diagnostics). */
    public boolean getAllmStatus() throws RemoteException { return readAllmStatus(HDMI_SOURCE_1); }

    /** getCopowerDeviceType() -> 0 adapter, 1 stand, 2 low power, 3 internal battery, 4 stand + adapter. */
    public int getPowerSourceType() throws RemoteException { return wire.retI32(HalCodes.GMPF2_GET_COPOWER_DEVICE_TYPE); }

    /** getCopowerBatteryLevel(): the charging stand battery (read only while the type is 1 or 4). */
    public int getStandBatteryLevel() throws RemoteException { return wire.retI8(HalCodes.GMPF2_GET_COPOWER_BATTERY_LEVEL); }

    /** getPowerSupplyVoltage(), unit unverified. */
    public float getPowerSupplyVoltage() throws RemoteException { return guard.retF32(HalCodes.GMPF2_GET_POWER_SUPPLY_VOLTAGE); }

    /** getAutoEcoMode() (its setter stays banned). */
    public boolean getAutoEcoMode() throws RemoteException { return wire.retBool(HalCodes.GMPF2_GET_AUTO_ECO_MODE); }

    /** getStandVerticalAngle(): degrees of the passive stand. */
    public float getStandVerticalAngle() throws RemoteException { return guard.retF32(HalCodes.GMPF2_GET_STAND_VERTICAL_ANGLE); }

    /** getIsAutoAtmoSphereLight(): the stock gate of the ambient light settings. */
    public boolean getAmbientLightSupported() throws RemoteException { return wire.retBool(HalCodes.GMPF2_GET_IS_AUTO_ATMOSPHERE_LIGHT); }

    /** getSlideFunc() -> SLIDE_*; anything but 0 is the ambient speaker, as stock. */
    public int getSlideFunction() throws RemoteException { return wire.retI8(HalCodes.GMPF2_GET_SLIDE_FUNC); }

    /** setSlideFunc(SLIDE_*): the stock "Close device" choice, on the user's row action only. */
    public boolean setSlideFunction(int f) throws RemoteException {
        return wire.u8RetBool(HalCodes.GMPF2_SET_SLIDE_FUNC, checkRange("slide function", f, SLIDE_POWER, SLIDE_AMBIENT_SPEAKER));
    }

    /** isConvexLensIn(): the starry sky lens is attached. */
    public boolean isConvexLensIn() throws RemoteException { return wire.retBool(HalCodes.GMPF2_IS_CONVEX_LENS_IN); }

    /** getConvexLensType(): the lens index (meaning unverified). */
    public int getConvexLensType() throws RemoteException { return wire.retI32(HalCodes.GMPF2_GET_CONVEX_LENS_TYPE); }
}
