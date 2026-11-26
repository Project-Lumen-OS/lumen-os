// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package lumen.fake;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * The remote pairing scenarios (host only): each runs on a fresh {@link World} and a fresh class loader of the subject
 * (the code under test keeps static state), and prints the trace of decisions. The same scenarios run on the old
 * Z9X / HB / Play 6 RemoteAutoPair, the old X50 / RS30 RemotePair and the shared lumen-remote engine; test/parity.py
 * compares the traces with the goldens recorded from the old code before the move.
 *
 *   java -cp HARNESS:STUBS lumen.fake.Scenarios SUBJECT_CLASSES_DIR [ONLY_ID]
 */
public final class Scenarios {
    static final int V_XGIMI = 0x000d;

    interface Body { void run(Subject s) throws Exception; }

    static final class Sc {
        final String id, what; final Body body;
        Sc(String id, String what, Body body) { this.id = id; this.what = what; this.body = body; }
    }

    static final List<Sc> ALL = new ArrayList<>();

    static void sc(String id, String what, Body b) { ALL.add(new Sc(id, what, b)); }

    static void checkpoint(Subject s, String label) {
        World.event("check " + label + ": scan " + World.scanState() + ", " + s.state());
    }

    static {
        sc("S01", "trial first boot, empty data: the Z9X remote (000d:3841) held in pairing mode", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3841), true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S02", "trial first boot: the X50 / RS30 remote (000d:3840)", s -> {
            World.setupDone(false);
            World.ownName = "XGIMI X50 Ultra";
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840), true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S03", "trial first boot: the RC-S3R remote (000d:3843, name Xgimi-RC-S3R)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "Xgimi-RC-S3R", V_XGIMI, 0x3843), true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S04", "trial first boot: an XGIMI remote named BLuetooth 4.0 RC (stock name list)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "BLuetooth 4.0 RC", V_XGIMI, 0x3838), true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S05", "first boot: another XGIMI product discoverable nearby (never a remote)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "XGIMI X50 Ultra", 0, 0);
            r.flags = 0x06;
            r.hidUuid = false;
            World.pairingMode(r, true);
            World.Remote r2 = World.remote("<mac>", "XGIMI Sound 2", 0, 0);
            World.pairingMode(r2, true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S06", "first boot: an advertiser carrying this projector's own adapter name", s -> {
            World.setupDone(false);
            World.ownName = "XGIMI RC";
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3841), true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S07", "first boot: a remote name in a non-connectable advertisement", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3841);
            r.connectable = false;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S08", "after setup, nothing bonded: remote advertising LE General Discoverable", s -> {
            World.setupDone(true);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840);
            r.flags = 0x06;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S09", "after setup, nothing bonded: remote advertising no flags but the HID service", s -> {
            World.setupDone(true);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840);
            r.flags = -1;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "20s");
        });
        sc("S10", "after setup, a remote is bonded: the user asks to pair, a remote named XGIMI Remote", s -> {
            World.setupDone(true);
            World.bondedRemote("<mac>", "XGIMI RC", V_XGIMI, 0x3841, true);
            s.start();
            World.run(5_000);
            s.pair();
            World.run(1_000);
            World.pairingMode(World.remote("<mac>", "XGIMI Remote", V_XGIMI, 0x3842), true);
            World.run(20_000);
            checkpoint(s, "26s");
        });
        sc("S11", "after setup, the bonded remote is asleep: a NEW remote in pairing mode in the boot window", s -> {
            World.setupDone(true);
            World.bondedRemote("<mac>", "XGIMI RC", V_XGIMI, 0x3841, false);
            s.start();
            World.run(15_000);
            World.pairingMode(World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840), true);
            World.run(30_000);
            checkpoint(s, "45s");
        });
        sc("S12", "after setup, the bonded remote was reset (lost its keys): it advertises pairing mode", s -> {
            World.setupDone(true);
            World.Remote r = World.bondedRemote("<mac>", "XGIMI RC", V_XGIMI, 0x3841, false);
            s.start();
            World.run(12_000);
            r.sleeping = false;
            r.lostKeys = true;
            World.pairingMode(r, true);
            World.run(30_000);
            checkpoint(s, "42s");
        });
        sc("S13", "after setup, no remote anywhere: the scan budget (fast, then balanced, then off)", s -> {
            World.setupDone(true);
            s.start();
            World.run(60_000);
            checkpoint(s, "1min");
            World.run(120_000);
            checkpoint(s, "3min");
            World.run(58 * 60_000);
            checkpoint(s, "61min");
        });
        sc("S14", "after setup, nothing bonded, Bluetooth headphones connected: radio sharing", s -> {
            World.setupDone(true);
            World.audio = true;
            s.start();
            World.run(10_000);
            checkpoint(s, "10s");
        });
        sc("S15", "first boot: the remote asks for a PIN (variant 0): left to the system dialog", s -> {
            World.setupDone(false);
            World.pairVariant = 0;
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3841), true);
            World.run(8_000);
            checkpoint(s, "13s");
        });
        sc("S16", "after setup: the user removes the only remote's bond (remote lost)", s -> {
            World.setupDone(true);
            World.Remote r = World.bondedRemote("<mac>", "XGIMI RC", V_XGIMI, 0x3841, true);
            s.start();
            World.run(10_000);
            r.sleeping = true;
            World.removeBond(World.device(r.addr));
            World.run(20_000);
            checkpoint(s, "30s");
        });
        sc("S17", "trial: Bluetooth runtime permissions missing at first start (no default grant)", s -> {
            World.setupDone(false);
            World.perms.clear();
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3841), true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S18", "reboot with a bonded remote that is awake: it must come back by itself, no prompt", s -> {
            World.setupDone(true);
            World.Remote r = World.bondedRemote("<mac>", "XGIMI RC", V_XGIMI, 0x3841, false);
            s.start();
            World.run(3_000);
            World.wake(r);
            World.run(90_000);
            checkpoint(s, "93s");
        });
        sc("S19", "Bluetooth off, then on again (first boot, no remote)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.bt(false);
            World.run(5_000);
            checkpoint(s, "off");
            World.bt(true);
            World.run(10_000);
            checkpoint(s, "on");
        });
        sc("S20", "screen off and on (first boot, no remote)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.screen(false);
            World.run(5_000);
            checkpoint(s, "off");
            World.screen(true);
            World.run(10_000);
            checkpoint(s, "on");
        });
    }

    static {
        sc("S21", "trial first boot: an XGIMI remote named in Chinese (RS30 stock name 极米遥控器)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "\u6781\u7c73\u9065\u63a7\u5668", V_XGIMI, 0x3840), true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S22", "after setup: the user asks to pair, a Bluetooth LE keyboard in pairing mode", s -> {
            World.setupDone(true);
            s.start();
            World.run(5_000);
            s.pair();
            World.run(1_000);
            World.pairingMode(World.remote("<mac>", "Logitech K380", 0x046d, 0xb342), true);
            World.run(20_000);
            checkpoint(s, "26s");
        });
        sc("S23", "first boot: the same keyboard in pairing mode, nobody asked: never bonded unattended", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "Logitech K380", 0x046d, 0xb342), true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S24", "first boot: the XGIMI music controller (X_Remote) in pairing mode: not a remote", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "XGIMI X_Remote", V_XGIMI, 0x3839), true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S25", "trial on a Z9X with an X50 remote, permissions not granted by default, self-grant works", s -> {
            World.setupDone(false);
            World.perms.clear();
            World.ownName = "XGIMI Z9X";
            s.start();
            World.run(5_000);
            World.pairingMode(World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840), true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
    }

    static {
        sc("S26", "the app process restarts 30 min after boot (not a boot), the bonded remote is awake, the stack does not reconnect it", s -> {
            World.now = 30 * 60_000;
            World.hidAutoConnect = false;
            World.setupDone(true);
            World.Remote r = World.bondedRemote("<mac>", "XGIMI RC", V_XGIMI, 0x3840, false);
            r.sleeping = false;
            s.start();
            World.run(15_000);
            checkpoint(s, "15s");
        });
    }

    /** The stock RcPair pairing advert's manufacturer data (company 0x000D): 38 38 02 00 00 0x, X_Remote 39 38 02. */
    static final byte[] RC_MARK = {0x38, 0x38, 0x02, 0x00, 0x00, 0x00}, MUSIC_MARK = {0x39, 0x38, 0x02, 0x00, 0x00, 0x00};

    static {
        sc("S27", "trial first boot (RS30): the remote's pairing advert carries the stock mark, its flags no discoverable bit", s -> {
            World.setupDone(false);
            World.ownName = "XGIMI RS30";
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840);
            r.flags = 0x04;
            r.xgimiData = RC_MARK;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S28", "trial first boot: the pairing advert has the stock mark and no name (the name only in the scan response)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", null, V_XGIMI, 0x3840);
            r.xgimiData = RC_MARK;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S29", "first boot: the music controller's mark without a name: not a remote", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", null, V_XGIMI, 0x3839);
            r.xgimiData = MUSIC_MARK;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S30", "trial first boot: a table remote with flags but no discoverable bit and no mark (stock checks no flags)", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840);
            r.flags = 0x04;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S31", "after setup, nothing bonded: the same advert without discoverable bit or mark is not bonded", s -> {
            World.setupDone(true);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "XGIMI RC", V_XGIMI, 0x3840);
            r.flags = 0x04;
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S32", "first boot: another device with the 0x000D company id and no HID service", s -> {
            World.setupDone(false);
            s.start();
            World.run(5_000);
            World.Remote r = World.remote("<mac>", "TI SensorTag", 0, 0);
            r.hidUuid = false;
            r.flags = 0x06;
            r.xgimiData = new byte[] {0x01, 0x02};
            World.pairingMode(r, true);
            World.run(20_000);
            checkpoint(s, "25s");
        });
        sc("S33", "after setup, the bonded remote was reset: its pairing advert has the stock mark, no Limited flag", s -> {
            World.setupDone(true);
            World.Remote r = World.bondedRemote("<mac>", "XGIMI RC", V_XGIMI, 0x3840, false);
            s.start();
            World.run(12_000);
            r.sleeping = false;
            r.lostKeys = true;
            r.flags = 0x04;
            r.xgimiData = RC_MARK;
            World.pairingMode(r, true);
            World.run(30_000);
            checkpoint(s, "42s");
        });
    }

    public static void main(String[] a) throws Exception {
        File subject = new File(a[0]);
        String only = a.length > 1 ? a[1] : null;
        for (Sc sc : ALL) {
            if (only != null && !only.equals(sc.id)) continue;
            World.reset();
            Hooks.setupEvents = 0;
            ClassLoader cl = new URLClassLoader(new URL[] {subject.toURI().toURL()}, Scenarios.class.getClassLoader());
            Subject s = (Subject) cl.loadClass("lumen.subject.Impl").getDeclaredConstructor().newInstance();
            System.out.println("== " + sc.id + " " + sc.what);
            try {
                sc.body.run(s);
            } catch (Throwable t) {
                World.event("CRASH " + t);
            }
            for (String e : World.events) System.out.println(e);
            System.out.println("   setup events: " + (Hooks.setupEvents > 0 ? "yes" : "no"));
        }
    }
}
