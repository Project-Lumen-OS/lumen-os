package org.lumen.remote;

import java.util.Locale;

/**
 * Which advertiser is a remote and may be bonded now (pure, host-tested: test/run.sh). The names come from the remote
 * table (RemoteCatalog): a remote matches when its advertised name CONTAINS one of the names of a BLE_REMOTE row,
 * ignoring case (the stock RcPair rule of every XGIMI model is String.contains of "XGIMI RC" / "BLuetooth 4.0 RC",
 * case-sensitive; Lumen OS 1.0 matched the prefix "XGIMI RC" ignoring case; both stay accepted).
 *
 * Unattended windows (first-run setup, no remote bonded, the remote-lost prompt, a bonded remote's reconnect window):
 * connectable, not this projector's own adapter name, and one of
 *  - the stock RcPair pairing advertisement (BluRemConService RC_PAIR_ADV_HEX): the HID service 0x1812 and the
 *    manufacturer data of company 0x000D starting 38 38 02 00 00, whatever the flags, with a table name or no name;
 *  - a table remote in pairing mode: the LE Limited (0x01) or General (0x02) Discoverable flag, or the HID service
 *    while no remote is bonded and either no flags AD (the X50 rule) or the first-run setup (stock checks no flags).
 * Another XGIMI product ("XGIMI X50 Ultra", "XGIMI Sound 2"), the music controller, a sleeping remote after the setup
 * or a stranger is never bonded.
 *
 * The user-started window (Pair remote): also a name with XGIMI plus RC / REMOTE / the HID service / the Limited
 * flag (Lumen OS 1.0), and any other Bluetooth LE remote or keyboard that advertises the HID service in pairing mode.
 */
public final class RemoteMatch {
    public static final int FLAG_LE_LIMITED = 0x01, FLAG_LE_GENERAL = 0x02;
    public static final String HID_UUID = "00001812-0000-1000-8000-00805f9b34fb";
    /** Bluetooth company id in the XGIMI remotes' manufacturer data (stock RcPair advert 09 FF 0D 00 ...). */
    public static final int ADV_COMPANY = 0x000D;
    /** The remote pairing mark after the company id (stock RC_PAIR_ADV_HEX ...0D00 3838 0200 00); X_Remote sends 39 38. */
    static final byte[] REMOTE_PAIRING_MARK = {0x38, 0x38, 0x02, 0x00, 0x00};

    private RemoteMatch() {}

    /** The table row of a BLE remote whose name the advertised name contains (ignoring case), else null. */
    public static RemoteCatalog.Row byName(String name) {
        if (name == null || name.isEmpty()) return null;
        String up = name.toUpperCase(Locale.ROOT);
        for (RemoteCatalog.Row r : RemoteCatalog.ROWS) {
            if (r.kind != RemoteKind.BLE_REMOTE) continue;
            for (String n : r.names) if (up.contains(n.toUpperCase(Locale.ROOT))) return r;
        }
        return null;
    }

    /** A remote of the table by its Bluetooth name. */
    public static boolean isRemoteName(String name) {
        return byName(name) != null;
    }

    /** The table row of a HID vendor / product, else null. */
    public static RemoteCatalog.Row byHid(int vendor, int product) {
        for (RemoteCatalog.Row r : RemoteCatalog.ROWS) if (r.vendor == vendor && r.product == product) return r;
        return null;
    }

    /** HID vendor of an XGIMI remote (BLE remote or 2.4 GHz receiver rows; never the IR receiver or the keypad). */
    public static boolean isRemoteVendor(int vendor) {
        for (RemoteCatalog.Row r : RemoteCatalog.ROWS) {
            if ((r.kind == RemoteKind.BLE_REMOTE || r.kind == RemoteKind.USB_DONGLE) && r.vendor == vendor && vendor >= 0) {
                return true;
            }
        }
        return false;
    }

    static boolean hasXgimi(String name) {
        return name != null && name.toUpperCase(Locale.ROOT).contains("XGIMI");
    }

    static boolean ownName(RemoteAdvert a, String ownName) {
        return a.name != null && ownName != null && a.name.equalsIgnoreCase(ownName);
    }

    static boolean pairingMode(RemoteAdvert a, boolean anyRemoteBonded, boolean firstRun) {
        if (a.hid && !anyRemoteBonded && (a.flags < 0 || firstRun)) return true;
        return a.discoverable();
    }

    /**
     * Binder thread pre-filter, before anything is posted: can this advertisement matter, or is it worth one log
     * line (every advert with the XGIMI company data or the HID service is judged and logged once per address)?
     */
    public static boolean worthPosting(RemoteAdvert a, boolean manual) {
        if (a.xgimiData != null || a.hid) return true;
        if (a.name != null) return hasXgimi(a.name) || isRemoteName(a.name);
        return manual && a.limited();
    }

    /** First look of the pairing thread: worth judging (the stock mark, an XGIMI name, a table remote, any HID when manual). */
    public static boolean candidate(RemoteAdvert a, boolean manual) {
        if (a.stockPairingMark()) return true;
        if (a.name == null) return false;
        return hasXgimi(a.name) || isRemoteName(a.name) || (manual && a.hid);
    }

    /** May this advertisement be bonded in an unattended window? firstRun = the first-run setup is not complete. */
    public static boolean automatic(RemoteAdvert a, String ownName, boolean anyRemoteBonded, boolean firstRun) {
        if (!a.connectable || ownName(a, ownName)) return false;
        if (a.stockPairingMark()) return a.name == null || isRemoteName(a.name);
        if (!isRemoteName(a.name)) return false;
        return pairingMode(a, anyRemoteBonded, firstRun);
    }

    /** May this advertisement be bonded in the user-started window? */
    public static boolean manual(RemoteAdvert a, String ownName) {
        if (!a.connectable || ownName(a, ownName)) return false;
        if (automatic(a, ownName, false, false)) return true;
        if (a.name == null) return false;
        String up = a.name.toUpperCase(Locale.ROOT);
        if (up.contains("XGIMI") && (up.contains("RC") || up.contains("REMOTE") || a.hid || a.limited())) return true;
        return a.hid && pairingMode(a, false, false);
    }
}
