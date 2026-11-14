package org.lumen.projector.hal;

import android.os.HwParcel;
import android.os.Wire;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The wire trace of the Play 6 gmpf clients (IGmpf, and IGmpf2 behind its identity guard). P6Api names every public
 * member of the clients by the key it had when the golden was recorded ("g.name(Type,...)" for IGmpf, "g2.name(...)" for
 * IGmpf2) and calls it on the current clients. Every member is called with a fixed set of arguments under fixed reply
 * patterns in two passes (NUI reader down with the v1.7.116 IGmpf2 table, NUI reader up with the v1.1.67 table), then
 * scripted sessions: the boot handshake, the identity guard, struct replies, transport errors, the vendor callbacks.
 *
 *   java P6WireTrace
 */
public final class P6WireTrace {
    static final String HASH_V1_7_116 = "6816d9efd3c7da9c522bfa05b090f7a0795081319ee20396d1343c81a6380157";
    static final String HASH_V1_1_67 = "fa2afea7cb29be93bba20d99a3d60adadbaebe32a9cfa14964b074c781910b37";
    static final String HASH_IBASE = "ec7fd79ed02dfa85bc499426adae3ebe23ef0524f3cd6957139324b83b18ca4c";
    private static final StringBuilder OUT = new StringBuilder();

    private static final int[] INTS = {-201, -200, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 20, 25, 44, 50,
            100, 101, 160, 200, 201, 1080, 1920, 20000, 20001};
    private static final int[] FEW = {-1, 0, 1, 2, 5, 13, 50, 100, 160};

    public static void main(String[] a) throws Exception {
        P6Api.init();
        block("connect", 0, () -> P6Api.call("g.connect(DeathRecipient,long)", P6Api.deathRecipient(), 7L), "connect");
        String[][] passes = {{"nui-off", HASH_V1_7_116, "v1.7.116"}, {"nui-on", HASH_V1_1_67, "v1.1.67"}};
        for (String[] p : passes) {
            if (p[0].equals("nui-on")) Wire.PROPS.put("sys.z9x.nui", "ready"); else Wire.PROPS.remove("sys.z9x.nui");
            OUT.append("# pass ").append(p[0]).append(" vendor ").append(p[2]).append('\n');
            openGuard("identity " + p[2], p[1]);
            sweep();
        }
        Wire.PROPS.put("sys.z9x.nui", "ready");
        OUT.append("# scripted\n");
        scripted();
        System.out.print(OUT);
    }

    // ------------------------------------------------------------------ the sweep over every member
    private static void sweep() {
        for (String key : P6Api.keys()) {
            if (key.startsWith("g.connect(") || key.endsWith(".dropConnection()")) continue;
            String[] types = types(key);
            List<Object[]> cases = cases(types);
            int[] seeds = types.length == 0 ? new int[] {0, 1, 2, 3} : new int[] {0, 2};
            for (Object[] args : cases) {
                for (int s : seeds) {
                    Wire.clock += 100_000;
                    block(key + show(args) + " s" + s, s, () -> P6Api.call(key, args), key);
                }
            }
        }
    }

    static String[] types(String key) {
        String inner = key.substring(key.indexOf('(') + 1, key.length() - 1);
        return inner.isEmpty() ? new String[0] : inner.split(",");
    }

    private static Object[] values(String t) {
        switch (t) {
            case "int": return box(INTS);
            case "boolean": return new Object[] {false, true};
            case "long": return new Object[] {7L};
            case "float": return new Object[] {0f, 0.5f, 10f, 10.5f, 45f, 45.5f, -1f};
            case "String": return new Object[] {HASH_V1_7_116, HASH_V1_1_67, HASH_IBASE, "bad", null};
            default: return P6Api.values(t);
        }
    }

    private static Object[] box(int[] v) {
        Object[] o = new Object[v.length];
        for (int i = 0; i < v.length; i++) o[i] = v[i];
        return o;
    }

    /** Full product up to 100 cases, else over FEW up to 400, else each parameter over FEW with the others fixed. */
    private static List<Object[]> cases(String[] t) {
        Object[][] v = new Object[t.length][];
        Object[][] f = new Object[t.length][];
        long product = 1, few = 1;
        for (int i = 0; i < t.length; i++) {
            v[i] = values(t[i]);
            f[i] = t[i].equals("int") ? box(FEW) : v[i];
            product *= v[i].length;
            few *= f[i].length;
        }
        List<Object[]> out = new ArrayList<>();
        if (product <= 100) {
            product(v, 0, new Object[t.length], out);
            return out;
        }
        if (few <= 400) {
            product(f, 0, new Object[t.length], out);
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int base : FEW) {
            for (int i = 0; i < t.length; i++) {
                Object[][] w = new Object[t.length][];
                for (int j = 0; j < t.length; j++) w[j] = j == i ? f[j] : new Object[] {t[j].equals("int") ? base : v[j][Math.abs(base) % v[j].length]};
                List<Object[]> some = new ArrayList<>();
                product(w, 0, new Object[t.length], some);
                for (Object[] c : some) if (seen.add(show(c))) out.add(c);
            }
        }
        return out;
    }

    private static void product(Object[][] v, int i, Object[] cur, List<Object[]> out) {
        if (i == v.length) {
            out.add(cur.clone());
            return;
        }
        for (Object x : v[i]) {
            cur[i] = x;
            product(v, i + 1, cur, out);
        }
    }

    // ------------------------------------------------------------------ scripted sessions
    private interface Call { Object run() throws Exception; }

    private static Object[] r(Object... v) { return v; }

    private static void step(String title, Object[] script, String key, Object... args) {
        Wire.clock += 50;
        block(title + " " + key + show(args), 0, () -> {
            for (Object x : script) Wire.SCRIPT.add(x);
            return P6Api.call(key, args);
        }, key);
    }

    /** The IBase getHashChain reply of IGmpf2: the hidl_vec header (2 elements), then the two hashes. */
    private static Object[] chain(String first) {
        byte[] vec = new byte[16];
        vec[8] = 2;
        byte[] data = new byte[64];
        System.arraycopy(unhex(first), 0, data, 0, 32);
        System.arraycopy(unhex(HASH_IBASE), 0, data, 32, 32);
        return r(vec, data);
    }

    private static byte[] unhex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    private static void openGuard(String title, String hash) {
        step(title, new Object[0], "g2.dropConnection()");
        step(title, chain(hash), "g2.verifyIdentity()");
    }

    private static byte[] ints(int... v) {
        byte[] b = new byte[v.length * 4];
        for (int i = 0; i < v.length; i++) for (int k = 0; k < 4; k++) b[i * 4 + k] = (byte) (v[i] >> (8 * k));
        return b;
    }

    private static byte[] floats(float... v) {
        int[] i = new int[v.length];
        for (int k = 0; k < v.length; k++) i[k] = Float.floatToIntBits(v[k]);
        return ints(i);
    }

    /** A 326-byte 186 reply: mode 0, the four corners at their places of the 9 x 9 grid (row stride 36, from byte 2). */
    private static byte[] kst(int[][] corners, int extra) {
        byte[] b = new byte[326];
        int[][] rc = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (int c = 0; c < 4; c++) {
            int off = 2 + rc[c][0] * 36 + rc[c][1] * 4;
            b[off] = (byte) corners[c][0];
            b[off + 1] = (byte) (corners[c][0] >> 8);
            b[off + 2] = (byte) corners[c][1];
            b[off + 3] = (byte) (corners[c][1] >> 8);
        }
        b[2 + 8] = (byte) extra;
        return b;
    }

    private static void scripted() throws Exception {
        Wire.clock += 100_000;
        // the boot handshake in the order of BootHandshake, then the power-on UI ready of UiReady17
        step("handshake", r(), "g.setSystemUiReady()");
        step("handshake", r(true), "g.goToBootCompleted(int)", 0);
        step("handshake", r(), "g.setBootAnimFinished()");
        step("handshake", r(true), "g.goToBootCompleted(int)", 1);
        step("handshake", r(2), "g.getBootAnimState()");
        step("handshake", r(), "g.sendPowerOnUiReady()");
        // the identity guard: an unknown hash closes it for the connection, nothing is sent until a reconnect
        openGuard("guard unknown", HASH_IBASE);
        step("guard unknown", r(1), "g2.getGamma()");
        step("guard unknown", r(), "g2.isIdentityOpen()");
        step("guard unknown", r(1), "g2.verifyIdentity()");
        step("guard unreadable", r(), "g2.dropConnection()");
        byte[] bad = new byte[16];
        bad[8] = 9;
        step("guard unreadable", r(bad), "g2.verifyIdentity()");
        step("guard unreadable", r(1), "g2.setGamma(int)", 2);
        // a transport failure of the guard's own read leaves it unknown: the next call reconnects and reads again
        step("guard transport", r(), "g2.dropConnection()");
        Wire.failNext = true;
        step("guard transport", chain(HASH_V1_7_116), "g2.getGamma()");
        Object[] again = chain(HASH_V1_7_116);
        step("guard transport", r(again[0], again[1], 4), "g2.getGamma()");
        step("guard transport", r(), "g2.isIdentityOpen()");
        // IGmpf2 down: not available, nothing sent
        step("guard down", r(), "g2.dropConnection()");
        Wire.PROPS.put("down:xgimi.hardware.gmpf@1.0::IGmpf2", "1");
        step("guard down", r(), "g2.verifyIdentity()");
        step("guard down", r(), "g2.getGamma()");
        Wire.PROPS.remove("down:xgimi.hardware.gmpf@1.0::IGmpf2");
        // v1.1.67: the remapped codes and a method the old vendor does not have
        openGuard("guard v1.1.67", HASH_V1_1_67);
        step("guard v1.1.67", r(true), "g2.setGameModeState(boolean)", true);
        step("guard v1.1.67", r(1f), "g2.getPowerSupplyVoltage()");
        step("guard v1.1.67", r(true), "g2.getSmartAutofocus()");
        step("guard v1.1.67", r(), "g2.liveHashChain()");
        openGuard("guard v1.7.116", HASH_V1_7_116);
        // a failed IGmpf2 transaction drops the binder and the guard: the next call reconnects and checks again
        Wire.failNext = true;
        step("guard drop", r(1), "g2.getGamma()");
        Object[] c3 = chain(HASH_V1_7_116);
        step("guard drop", r(c3[0], c3[1], 3), "g2.getGamma()");
        // IGmpf transport errors: the binder stays
        Wire.failNext = true;
        step("error", r(1), "g.getLampLevel()");
        step("error", r(7), "g.getLampLevel()");
        step("error", r(), "g.isConnected()");
        // structs
        int[][] full = {{0, 0}, {1920, 0}, {1920, 1080}, {0, 1080}};
        step("struct", r(kst(full, 0)), "g.getKeystonePoints()");
        step("struct", r(kst(new int[][] {{0, 0}, {1921, 0}, {1920, 1080}, {0, 1080}}, 3)), "g.getKeystonePoints()");
        step("struct", r(new byte[12]), "g.getKeystonePoints()");
        step("struct", r(new byte[] {0, 15, 0, 0, 112, 8, 0, 0, 1, 1, 0, 0, 102, 23, 0, 0}), "g.getHdmiInfo()");
        step("struct", r(new byte[] {4, 6, 8, 0}), "g.getRgbLevels()");
        step("struct", r(0, ints(-1, 1, -200, 200)), "g.getZoomStepRange()");
        step("struct", r(0, ints(0, 5)), "g.getZoomStep()");
        step("struct", r(0, new byte[4]), "g.getZoomStep()");
        step("struct", r("feature_a;feature_b"), "g.getAllFeatureList()");
        step("struct", r(true, ints(0, 1, 2)), "g2.getGameModeProp()");
        step("struct", r(false, ints(0, 1, 2)), "g2.getGameModeProp()");
        step("struct", r(true, floats(-10f, 10f, 0f, 0f)), "g2.getScreenRotation()");
        step("struct", r(true, floats(-50f, 10f, 0f, 0f)), "g2.getScreenRotation()");
        step("struct", r(false, floats(-10f, 10f, 0f, 0f)), "g2.getScreenRotation()");
        step("struct", r(true, floats(-10f, 10f, 11f, 0f)), "g2.getScreenRotation()");
        step("struct", r(19.5f), "g2.getPowerSupplyVoltage()");
        step("struct", r(-12.5f), "g2.getStandVerticalAngle()");
        // stock sequences: the keystone reset (129 only after a 6), the game option written back as read
        step("sequence", r(6, true), "g.resetKeystone()");
        step("sequence", r(5), "g.resetKeystone()");
        step("sequence", r(3, true), "g.applyGameModeOption()");
        step("sequence", r(-1, false), "g.applyGameModeOption()");
        // every argument of the equalizer and the light levels at and beyond its bounds
        int[][] peq = {{1, -120, 50, 5}, {13, 60, 20000, 160}, {0, 0, 1000, 10}, {14, 0, 1000, 10}, {1, -121, 1000, 10},
                {1, 61, 1000, 10}, {1, 0, 49, 10}, {1, 0, 20001, 10}, {1, 0, 1000, 4}, {1, 0, 1000, 161}};
        for (int[] p : peq) step("peq", r(1), "g.setPeqBand(int,int,int,int)", p[0], p[1], p[2], p[3]);
        int[][] rgb = {{1, 1, 1}, {10, 10, 10}, {0, 5, 5}, {5, 11, 5}, {5, 5, 0}, {3, 7, 9}};
        for (int[] c : rgb) step("rgb", r(), "g.setRgbLevels(int,int,int)", c[0], c[1], c[2]);
        // the vendor calls our callbacks: an event, the IBase codes, a parse error
        callbacks();
    }

    private static void callbacks() throws Exception {
        String[][] regs = {{"focus", "g.connectFocusManager(Play6FocusCallback)", "Play6FocusCallback"},
                {"power", "g.connectPowerManager(Play6PowerCallback)", "Play6PowerCallback"},
                {"music", "g.connectMusicPlayer(Play6MusicPlayerCallback)", "Play6MusicPlayerCallback"}};
        for (String[] reg : regs) {
            Wire.lastBinder = null;
            step("callback " + reg[0], r(), reg[1], P6Api.values(reg[2])[0]);
            Object cb = Wire.lastBinder;
            if (cb == null) {
                OUT.append("! no ").append(reg[0]).append(" callback binder\n");
                continue;
            }
            java.lang.reflect.Method on = cb.getClass().getMethod("onTransact", int.class, HwParcel.class, HwParcel.class, int.class);
            on.setAccessible(true);
            int[] codes = {1, 0x0f43484e, 0x0f445343, 0x0f504e47, 0x0f485348, 0x0f524546, 0x0f444247, 0x0f494e54, 0x0f535953, 99};
            for (int code : codes) {
                for (int parse = 0; parse < 2; parse++) {
                    final int bad = parse;
                    block("callback " + reg[0] + " onTransact " + code + (bad == 1 ? " parse error" : ""), 0, () -> {
                        if (bad == 0) {
                            Wire.SCRIPT.add(5);
                            Wire.SCRIPT.add(reg[0].equals("music") ? 82 : "value");
                            Wire.SCRIPT.add(reg[0].equals("music") ? 1 : "value");
                        } else {
                            Wire.SCRIPT.add("not an int");
                        }
                        on.invoke(cb, code, new HwParcel(), new HwParcel(), 0);
                        return null;
                    }, "callback");
                }
            }
        }
    }

    // ------------------------------------------------------------------ recording
    private static void block(String title, int seed, Call c, String key) {
        Wire.LOG.clear();
        Wire.reset(seed);
        String res;
        try {
            Object v = c.run();
            res = "= " + value(v);
        } catch (Throwable t) {
            Throwable x = t instanceof InvocationTargetException ? t.getCause() : t;
            res = "! " + x.getClass().getSimpleName() + ": " + x.getMessage();
        }
        Wire.SCRIPT.clear();
        OUT.append("C ").append(title).append('\n');
        for (String l : Wire.LOG) OUT.append("  ").append(l).append('\n');
        OUT.append("  ").append(res).append('\n');
    }

    private static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof float[]) return Arrays.toString((float[]) v);
        if (v instanceof String[]) return Arrays.toString((String[]) v);
        return P6Api.show(v);
    }

    static String show(Object[] a) {
        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < a.length; i++) s.append(i == 0 ? "" : ",").append(a[i] == null ? "null" : P6Api.show(a[i]));
        return s.append(')').toString();
    }
}
