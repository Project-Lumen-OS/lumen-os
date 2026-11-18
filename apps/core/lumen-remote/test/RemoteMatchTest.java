import org.lumen.remote.RemoteAdvert;
import org.lumen.remote.RemoteCatalog;
import org.lumen.remote.RemoteKind;
import org.lumen.remote.RemoteMatch;

/** Host test of the remote table rules (RemoteMatch over RemoteCatalog): java RemoteMatchTest */
public final class RemoteMatchTest {
    static int pass, fail;
    /** Manufacturer data of company 0x000D in the stock RcPair pairing advert (09 FF 0D 00 38 38 02 00 00 0x). */
    static final byte[] MARK = {0x38, 0x38, 0x02, 0x00, 0x00, 0x00};
    static final byte[] MUSIC = {0x39, 0x38, 0x02, 0x00, 0x00, 0x00};

    static void ok(String what, boolean c) {
        if (c) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    static RemoteAdvert adv(String name, boolean connectable, int flags, boolean hid) {
        return new RemoteAdvert(name, connectable, flags, hid, null, -50);
    }

    static RemoteAdvert mark(String name, int flags, boolean hid, byte[] data) {
        return new RemoteAdvert(name, true, flags, hid, data, -50);
    }

    static boolean auto(RemoteAdvert a, String own, boolean bonded) {
        return RemoteMatch.automatic(a, own, bonded, false);
    }

    static boolean firstRun(RemoteAdvert a, boolean bonded) {
        return RemoteMatch.automatic(a, "X", bonded, true);
    }

    public static void main(String[] a) {
        int remotes = 0;
        for (RemoteCatalog.Row r : RemoteCatalog.ROWS) {
            if (r.kind != RemoteKind.BLE_REMOTE) continue;
            remotes++;
            for (String n : r.names) {
                // any XGIMI remote pairs on any projector: every table name, in pairing mode, unattended
                ok(r.id + " " + n + ": limited flag bonds", auto(adv(n, true, 0x05, true), "XGIMI Z9X", false));
                ok(r.id + " " + n + ": general flag bonds", auto(adv(n, true, 0x06, false), "XGIMI Z9X", true));
                ok(r.id + " " + n + ": no flags + HID, nothing bonded", auto(adv(n, true, -1, true), "X", false));
                ok(r.id + " " + n + ": no flags + HID, a remote bonded: no", !auto(adv(n, true, -1, true), "X", true));
                ok(r.id + " " + n + ": flags without discoverable after the setup: no", !auto(adv(n, true, 0x04, true), "X", false));
                ok(r.id + " " + n + ": flags without discoverable, first-run setup, nothing bonded (stock checks no flags)",
                        firstRun(adv(n, true, 0x04, true), false));
                ok(r.id + " " + n + ": flags without discoverable, first-run setup, a remote bonded: no",
                        !firstRun(adv(n, true, 0x04, true), true));
                ok(r.id + " " + n + ": first-run setup without HID and without discoverable: no", !firstRun(adv(n, true, 0x04, false), false));
                ok(r.id + " " + n + ": not connectable: no", !auto(adv(n, false, 0x05, true), "X", false));
                ok(r.id + " " + n + ": not connectable, first-run: no", !firstRun(adv(n, false, 0x04, true), false));
                ok(r.id + " " + n + ": own adapter name: no", !auto(adv(n, true, 0x05, true), n, false));
                ok(r.id + " " + n + ": contained in a longer name", RemoteMatch.isRemoteName("Remote " + n + " 2"));
                ok(r.id + " " + n + ": case ignored", RemoteMatch.isRemoteName(n.toLowerCase(java.util.Locale.ROOT)));
                ok(r.id + " " + n + ": binder pre-filter keeps it", RemoteMatch.worthPosting(adv(n, true, 0x05, false), false));
                ok(r.id + " " + n + ": manual window too", RemoteMatch.manual(adv(n, true, 0x05, true), "X"));
                ok(r.id + " " + n + ": stock pairing mark, flags without discoverable, a remote bonded",
                        auto(mark(n, 0x04, true, MARK), "X", true));
            }
            ok(r.id + ": has the pairing keys", !r.combo.isEmpty());
        }
        ok("at least 8 BLE remotes in the table", remotes >= 8);
        // Lumen OS 1.0 Z9X rule stays accepted: prefix XGIMI RC ignoring case
        ok("XGIMI RC2 (Z9X 1.0 prefix rule)", auto(adv("XGIMI RC2", true, 0x05, false), "X", false));
        ok("xgimi rc (Z9X 1.0 ignored case)", auto(adv("xgimi rc", true, 0x05, false), "X", false));
        // X50 stock names stay accepted (exact list of com.xgimi.bluetoothservice)
        for (String n : new String[] {"BLuetooth 4.0 RC", "Aladdin RC", "XGIMI RC", "Dangbei"}) {
            ok("X50 stock name " + n, auto(adv(n, true, 0x05, true), "X", false));
        }
        // never bonded unattended: other XGIMI products, the music controller, strangers
        for (String n : new String[] {"XGIMI X50 Ultra", "XGIMI Sound 2", "XGIMI C4 MIC", "XGIMI X_Remote", "Logitech K380",
                "MTK BLE Remote", "LE-XGIMI SoundGo", "XGIMI", ""}) {
            ok("not a remote: " + n, !auto(adv(n, true, 0x05, true), "X", false));
            ok("not a remote in the first-run setup: " + n, !firstRun(adv(n, true, 0x04, true), false));
        }
        ok("no name: no", !auto(adv(null, true, 0x05, true), "X", false));
        ok("no name, first-run: no", !firstRun(adv(null, true, 0x05, true), false));
        // the stock RcPair pairing advert (BluRemConService RC_PAIR_ADV_HEX): HID + 0x000D 38 38 02 00 00, flags ignored
        ok("mark: no name in the advert bonds", auto(mark(null, -1, true, MARK), "X", false));
        ok("mark: no name, flags without discoverable, a remote bonded", auto(mark(null, 0x04, true, MARK), "X", true));
        ok("mark: a table name bonds", auto(mark("XGIMI RC", 0x00, true, MARK), "X", false));
        ok("mark: another XGIMI product's name: no", !auto(mark("XGIMI X50 Ultra", 0x04, true, MARK), "X", false));
        ok("mark: a stranger's name: no", !auto(mark("Logitech K380", 0x04, true, MARK), "X", false));
        ok("mark: own adapter name: no", !auto(mark("XGIMI RC", 0x04, true, MARK), "XGIMI RC", false));
        ok("mark: without the HID service: no", !auto(mark(null, 0x04, false, MARK), "X", false));
        ok("mark: not connectable: no", !RemoteMatch.automatic(new RemoteAdvert(null, false, 0x04, true, MARK, -50), "X", false, true));
        ok("music controller mark 39 38 02: no", !auto(mark(null, 0x05, true, MUSIC), "X", false)
                && !firstRun(mark(null, 0x04, true, MUSIC), false));
        ok("music controller with its name and mark: no", !auto(mark("XGIMI X_Remote", 0x05, true, MUSIC), "X", false));
        ok("a short mark (38 38 02) is not the stock one", !auto(mark(null, 0x05, true, new byte[] {0x38, 0x38, 0x02}), "X", false));
        ok("another 0x000D device: no", !auto(mark("TI SensorTag", 0x06, false, new byte[] {0x01, 0x02}), "X", false));
        ok("mark: candidate without a name", RemoteMatch.candidate(mark(null, -1, true, MARK), false));
        ok("mark: the manual window takes it too", RemoteMatch.manual(mark(null, 0x04, true, MARK), "X"));
        // the user-started window: Lumen OS 1.0 broad XGIMI rule and any BLE HID remote / keyboard in pairing mode
        ok("manual: XGIMI Remote", RemoteMatch.manual(adv("XGIMI Remote", true, 0x05, false), "X"));
        ok("manual: XGIMI something with HID", RemoteMatch.manual(adv("XGIMI Box", true, -1, true), "X"));
        ok("manual: a BLE keyboard in pairing mode", RemoteMatch.manual(adv("Logitech K380", true, 0x05, true), "X"));
        ok("manual: a keyboard not discoverable: no", !RemoteMatch.manual(adv("Logitech K380", true, 0x04, true), "X"));
        ok("manual: a keyboard without flags", RemoteMatch.manual(adv("Logitech K380", true, -1, true), "X"));
        ok("manual: a speaker without HID: no", !RemoteMatch.manual(adv("JBL Flip", true, 0x06, false), "X"));
        ok("manual: own name: no", !RemoteMatch.manual(adv("XGIMI Remote", true, 0x05, true), "XGIMI Remote"));
        ok("manual: not connectable: no", !RemoteMatch.manual(adv("XGIMI RC", false, 0x05, true), "X"));
        ok("manual: no name, no mark: no", !RemoteMatch.manual(adv(null, true, 0x05, true), "X"));
        // the binder pre-filter: what can matter, plus one log line for every XGIMI company or HID advert
        ok("auto pre-filter: a keyboard is posted for the log", RemoteMatch.worthPosting(adv("Logitech K380", true, 0x05, true), false));
        ok("auto pre-filter: but never a candidate", !RemoteMatch.candidate(adv("Logitech K380", true, 0x05, true), false));
        ok("manual: the keyboard is a candidate", RemoteMatch.candidate(adv("Logitech K380", true, 0x05, true), true));
        ok("auto pre-filter: a 0x000D advert without a name is posted", RemoteMatch.worthPosting(mark(null, -1, false, MUSIC), false));
        ok("auto pre-filter: a speaker without name or HID is not", !RemoteMatch.worthPosting(adv(null, true, 0x06, false), false));
        ok("auto pre-filter: a stranger's name without HID is not", !RemoteMatch.worthPosting(adv("JBL Flip", true, 0x06, false), false));
        ok("manual pre-filter: no name with the Limited flag is posted", RemoteMatch.worthPosting(adv(null, true, 0x05, false), true));
        ok("auto pre-filter: no name with the Limited flag is not", !RemoteMatch.worthPosting(adv(null, true, 0x05, false), false));
        // the log line names every field the rules read
        String line = mark("XGIMI RC", 4, true, MARK).toString();
        ok("advert log line " + line, line.equals("name=XGIMI RC rssi=-50 flags=4 hid=true connectable=true xgimi=383802000000"));
        ok("advert log line without data", adv(null, true, -1, false).toString().endsWith(" xgimi=-"));
        // input devices: XGIMI remote vendors, never the IR receiver or the keypad
        ok("vendor 000d", RemoteMatch.isRemoteVendor(0x000d));
        ok("vendor 1d5a", RemoteMatch.isRemoteVendor(0x1d5a));
        ok("vendor 26e3 (2.4 GHz receiver)", RemoteMatch.isRemoteVendor(0x26e3));
        ok("IR 3697 is not a remote vendor", !RemoteMatch.isRemoteVendor(0x3697));
        ok("Logitech 046d is not", !RemoteMatch.isRemoteVendor(0x046d));
        ok("X50 remote by HID", RemoteMatch.byHid(0x000d, 0x3840) != null && "xgimi_rc_3840".equals(RemoteMatch.byHid(0x000d, 0x3840).id));
        ok("Z9X remote by HID", "xgimi_rc_3841".equals(RemoteMatch.byHid(0x000d, 0x3841).id));
        ok("unknown HID", RemoteMatch.byHid(0x1234, 0x5678) == null);
        System.out.println("RemoteMatchTest: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
