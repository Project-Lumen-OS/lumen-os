package org.lumen.info.hal;

/**
 * GmpfHash: which getInterfaceHash answer of XGIMI's stable AIDL gmpf V1 this app accepts on the
 * Z9X HB (goat_elegant), bound to the stock vendor firmware (LumenInfo: HbGmpf). Plain Java, no Android
 * API: the transport reads ro.vendor.build.version.incremental and asks {@link #accepted} in its version guard.
 *
 * XGIMI edited the "frozen" V1 in place between the stock firmwares (gsi-stock analysis section
 * 2.1): V6.16.24 answers V61624, V6.17.12 answers V61712. The 6.17.12 table (stock analysis
 * aidl_codes_v61712.tsv) keeps all 1159 rows of 6.16.24 (one signature changed, FOCUS 102 doProImgFusionOverlap,
 * never sent) and appends 40 methods; every call this app can send has the same code and signature in both tables
 * (tools/devices/check_aidl_hal.py with both tables). So:
 *   V61624 on any firmware (the 6.16.24 interface, every 6.17.12 code Lumen uses is equal to it);
 *   V61712 ONLY when the firmware is exactly V61712_FIRMWARE, never on another or an unknown one;
 *   anything else, null or an empty firmware: refused (the guard latches the interface closed, nothing is sent).
 * The same three constants and rule are in apps/HbProjector, apps/HbBoot and apps/LumenInfo (one app each, no shared
 * library between them); tools/devices/test/hb_gmpf_hash_test.sh runs the rule of all three copies.
 */
public final class GmpfHash {
    /** V1 hash of the stock V6.16.24 vendor (vendor gmpf-V1-ndk.so sha256 2d391908...). */
    public static final String V61624 = "908de964b7da427ad21df846e629b80ffdc183ab";
    /** V1 hash of the stock V6.17.12 vendor (vendor gmpf-V1-ndk.so sha256 b008632e...). */
    public static final String V61712 = "715affd032170b4d256c28c9bbc3c6cbaf5de492";
    /** The only firmware on which V61712 is accepted (ro.vendor.build.version.incremental). */
    public static final String V61712_FIRMWARE = "v6.17.12";
    /** The property the transport passes as firmware. */
    public static final String FIRMWARE_PROP = "ro.vendor.build.version.incremental";

    private GmpfHash() {}

    /** true when the interface hash {@code hash} is accepted on the vendor firmware {@code firmware}. */
    public static boolean accepted(String hash, String firmware) {
        if (V61624.equals(hash)) return true;
        return V61712.equals(hash) && V61712_FIRMWARE.equals(firmware);
    }

    /** The hash a callback stub of this app answers: the one the vendor reported and the guard accepted. */
    public static String answer(String accepted) {
        return V61712.equals(accepted) ? V61712 : V61624;
    }
}
