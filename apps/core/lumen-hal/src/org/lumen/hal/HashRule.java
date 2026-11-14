package org.lumen.hal;

/**
 * Which getInterfaceHash answers the guard of {@link AidlTransport} accepts. Plain Java (the host tests run it).
 *
 * The base hash is accepted on every stock firmware, as before this class existed. Each extra hash is accepted only
 * when the vendor firmware that runs now (ro.vendor.build.version.incremental, {@link #FIRMWARE_PROP}; inside a DSU
 * trial and on an installed Lumen OS /vendor is the unit's own stock vendor) is exactly the firmware named with it.
 * XGIMI edits the "frozen" gmpf V1 in place between firmwares (X50 v1.2.54 ecadd76e..., v1.2.64 715affd0...: v1.2.64
 * only appended seven DisplayManager methods), so an extra hash is added only after the code table of that firmware
 * was checked against the app's whole allow list (tools/devices/check_aidl_hal.py with one --codes per firmware).
 */
public final class HashRule {
    /** the stock firmware of the running vendor, e.g. "v1.2.54" */
    public static final String FIRMWARE_PROP = "ro.vendor.build.version.incremental";
    /** accepts no hash: the rule of a model no table names */
    public static final HashRule NONE = new HashRule();

    private final String base;
    private final String[] firmware, hash;

    /** base: accepted everywhere; then pairs FIRMWARE, HASH: HASH only on exactly that firmware */
    public HashRule(String base, String... firmwareHashPairs) {
        if (!wellFormed(base) || firmwareHashPairs.length % 2 != 0) throw new IllegalArgumentException("hash rule");
        this.base = base;
        int n = firmwareHashPairs.length / 2;
        firmware = new String[n];
        hash = new String[n];
        for (int i = 0; i < n; i++) {
            String f = firmwareHashPairs[2 * i], h = firmwareHashPairs[2 * i + 1];
            if (f == null || !f.matches("v[0-9]+(\\.[0-9]+)+") || !wellFormed(h) || h.equals(base))
                throw new IllegalArgumentException("hash rule pair " + f + " " + h);
            firmware[i] = f;
            hash[i] = h;
        }
    }

    private HashRule() {
        base = null;
        firmware = new String[0];
        hash = new String[0];
    }

    private static boolean wellFormed(String h) {
        return h != null && h.matches("[0-9a-f]{40}");
    }

    /** the guard's answer for this hash on this running firmware (null firmware = unknown: base only) */
    public boolean accepted(String h, String fw) {
        if (h == null) return false;
        if (h.equals(base)) return true;
        String f = fw == null ? "" : fw.trim();
        for (int i = 0; i < hash.length; i++) if (h.equals(hash[i]) && f.equals(firmware[i])) return true;
        return false;
    }
}
