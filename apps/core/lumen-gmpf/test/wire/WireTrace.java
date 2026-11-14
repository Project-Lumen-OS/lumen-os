package org.lumen.projector.hal;

import android.os.HwParcel;
import android.os.Parcel;
import android.os.Wire;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The wire trace of the gmpf clients of one projector app: every public member of GmpfClient and Gmpf2Client (and
 * their bases) called with a fixed set of arguments under fixed reply patterns, then a few scripted sessions (zoom,
 * game option, sensor budgets, struct replies, transport errors, the focus callback). Prints one block per call: the
 * transactions sent (interface, code, flags, every argument word), then the result or the exception. The same
 * trace from two builds means the same bytes on the wire and the same parsed results.
 *
 *   java WireTrace z9x|hb
 */
public final class WireTrace {
    private static String dev;
    private static Object g, g2;
    private static final StringBuilder OUT = new StringBuilder();
    /** HB names of the shared game calls before they took the Z9X names. */
    private static final Map<String, String> RENAMED = new TreeMap<>();
    static {
        RENAMED.put("getVrr", "getVrrState");
        RENAMED.put("setVrr", "setVrrState");
        RENAMED.put("getDarkBoost", "getDarkScenes");
        RENAMED.put("setDarkBoost", "setDarkScenes");
    }

    public static void main(String[] a) throws Exception {
        dev = a[0];
        Wire.PROPS.put("ro.vendor.build.version.incremental", "V6.16.24");
        Class<?> gc = Class.forName("org.lumen.projector.hal.GmpfClient");
        java.lang.reflect.Constructor<?> ctor = gc.getConstructors()[0];
        Class<?> sinkType = ctor.getParameterTypes()[0];
        g = ctor.newInstance(proxy(sinkType));
        g2 = Class.forName("org.lumen.projector.hal.Gmpf2Client").getConstructor().newInstance();
        block("connect", () -> {
            for (Method m : gc.getMethods()) {
                if (!m.getName().equals("connect")) continue;
                Class<?>[] t = m.getParameterTypes();
                return t.length == 2 ? m.invoke(g, proxy(t[0]), 7L) : m.invoke(g, proxy(t[0]));
            }
            throw new IllegalStateException("no connect");
        });
        String[][] passes = dev.equals("hb")
                ? new String[][] {{"nui-off", "on"}, {"nui-on", "on"}, {"nui-on", "off"}}
                : new String[][] {{"nui-off", "on"}, {"nui-on", "on"}};
        for (String[] p : passes) {
            if (p[0].equals("nui-on")) Wire.PROPS.put("sys.z9x.nui", "ready"); else Wire.PROPS.remove("sys.z9x.nui");
            features(p[1].equals("on"));
            OUT.append("# pass ").append(p[0]).append(" features-").append(p[1]).append('\n');
            sweep();
        }
        features(true);
        Wire.PROPS.put("sys.z9x.nui", "ready");
        OUT.append("# scripted\n");
        scripted();
        System.out.print(OUT);
    }

    // ------------------------------------------------------------------ the sweep over every public member
    private static void sweep() throws Exception {
        TreeMap<String, Object[]> calls = new TreeMap<>();
        for (Object o : new Object[] {g, g2}) {
            for (Method m : o.getClass().getMethods()) {
                if (!m.getDeclaringClass().getName().startsWith("org.lumen.")) continue;
                if (m.getName().equals("connect") || m.getName().equals("dropConnection")) continue;
                String key = key(m);
                if (key == null) continue;
                calls.putIfAbsent(key, new Object[] {o, m});
            }
        }
        for (Map.Entry<String, Object[]> e : calls.entrySet()) {
            Method m = (Method) e.getValue()[1];
            Object recv = Modifier.isStatic(m.getModifiers()) ? null : e.getValue()[0];
            List<Object[]> cases = cases(m);
            int[] seeds = m.getParameterCount() == 0 ? new int[] {0, 1, 2, 3} : new int[] {0, 2};
            for (Object[] args : cases) {
                for (int s : seeds) {
                    Wire.clock += 100_000;
                    final Object[] fa = args;
                    block(e.getKey() + show(args) + " s" + s, s, () -> m.invoke(recv, fa), e.getKey());
                }
            }
        }
    }

    private static String key(Method m) {
        StringBuilder k = new StringBuilder(RENAMED.getOrDefault(m.getName(), m.getName())).append('(');
        Class<?>[] t = m.getParameterTypes();
        for (int i = 0; i < t.length; i++) {
            if (values(t[i]) == null) return null;
            k.append(i == 0 ? "" : ",").append(t[i].getSimpleName());
        }
        return k.append(')').toString();
    }

    private static final int[] INTS = {-1, 0, 1, 2, 3, 5, 6, 10, 12, 13, 17, 44, 50, 100, 101, 106, 160, 192, 500, 20000};
    private static final int[] FEW = {-1, 0, 1, 2, 5, 13, 50, 100, 160};

    private static Object[] values(Class<?> t) {
        if (t == int.class) return box(INTS);
        if (t == boolean.class) return new Object[] {false, true};
        if (t == long.class) return new Object[] {7L};
        if (t == float.class) return new Object[] {0f, 0.5f, 10f, 10.5f, -1f};
        if (t == String.class) return new Object[] {"NativeUIWatch", "bad name", null};
        if (t == int[].class) return new Object[] {null, new int[0], new int[] {50, 50, 50, 50, 50, 50, 50},
                new int[] {0, 100, 20, 30, 40, 50, 60}, new int[] {50, 50, 50, 50, 50, 50, 101}};
        if (t.isEnum()) {
            Object[] c = t.getEnumConstants();
            Object[] v = Arrays.copyOf(c, c.length + 1);
            return v;
        }
        if (t.getSimpleName().equals("KstPoint")) {
            try {
                Object full = t.getMethod("fullFrame").invoke(null);
                Object moved = t.getMethod("copy").invoke(full);
                t.getMethod("set", int.class, int.class, int.class).invoke(moved, 0, 100, 120);
                return new Object[] {full, moved, null};
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private static Object[] box(int[] v) {
        Object[] o = new Object[v.length];
        for (int i = 0; i < v.length; i++) o[i] = v[i];
        return o;
    }

    /** Full product over INTS up to 100 cases, else over FEW up to 400, else each parameter over FEW in turn. */
    private static List<Object[]> cases(Method m) {
        Class<?>[] t = m.getParameterTypes();
        Object[][] v = new Object[t.length][];
        Object[][] f = new Object[t.length][];
        long product = 1, few = 1;
        for (int i = 0; i < t.length; i++) {
            v[i] = values(t[i]);
            f[i] = t[i] == int.class ? box(FEW) : v[i];
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
                for (int j = 0; j < t.length; j++) w[j] = j == i ? f[j] : new Object[] {pick(t[j], v[j], base)};
                List<Object[]> some = new ArrayList<>();
                product(w, 0, new Object[t.length], some);
                for (Object[] c : some) if (seen.add(show(c))) out.add(c);
            }
        }
        return out;
    }

    private static Object pick(Class<?> t, Object[] v, int base) {
        if (t == int.class) return base;
        return v[Math.abs(base) % v.length];
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

    private static Object call(String name, Object... args) throws Exception {
        for (Object o : new Object[] {g, g2}) {
            for (Method m : o.getClass().getMethods()) {
                String n = RENAMED.getOrDefault(m.getName(), m.getName());
                if (n.equals(name) && m.getParameterCount() == args.length) {
                    try {
                        return m.invoke(o, args);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void step(String title, Object[] script, String name, Object... args) throws Exception {
        Wire.clock += 50;
        block(title + " " + name + show(args), 0, () -> {
            for (Object x : script) Wire.SCRIPT.add(x);
            return call(name, args);
        }, name);
    }

    private static boolean z9x() { return dev.equals("z9x"); }

    private static byte[] ints(int... v) {
        byte[] b = new byte[v.length * 4];
        for (int i = 0; i < v.length; i++) for (int k = 0; k < 4; k++) b[i * 4 + k] = (byte) (v[i] >> (8 * k));
        return b;
    }

    /** The reply words of the step's calls, in read order (the stubs answer the status themselves). */
    private static Object[] r(Object... v) { return v; }

    private static Object[] range(int ret, int a, int b, int c, int d) {
        return z9x() ? r(ret, ints(a, b, c, d)) : r(ret, 1, 20, a, b, c, d);
    }

    private static Object[] step2(int ret, int a, int b) {
        return z9x() ? r(ret, ints(a, b)) : r(ret, 1, 12, a, b);
    }

    private static void scripted() throws Exception {
        Wire.clock += 100_000;
        step("zoom", r(true), "setKstPrepareForZoom", true);
        step("zoom", r(0), "zoomSessionStart");
        step("zoom", range(0, 0, 8, -200, 200), "getZoomStepRange");
        step("zoom", r(1), "getZoomLimitTotal");
        step("zoom", step2(0, 5, 0), "getZoomStep");
        step("zoom", r(0), "setOpticalZoomStep", 4);
        step("zoom", r(0), "setOpticalZoomStep", 3);
        step("zoom", step2(0, 5, 0), "getZoomStep");
        step("zoom", step2(0, 0, 0), "getZoomLimitStep");
        step("zoom", r(0), "setOpticalZoomStep", 6);
        step("zoom", step2(0, 0, 0), "getZoomStep");
        step("zoom", step2(0, 0, 0), "getZoomLimitStep");
        step("zoom", r(0), "setOpticalZoomLimitStep", -1);
        step("zoom", step2(0, 0, 0), "getZoomStep");
        step("zoom", step2(0, -1, 0), "getZoomLimitStep");
        step("zoom", r(0), "setOpticalZoomStep", 1);
        step("zoom", r(0), "setOpticalZoomLimitStep", 0);
        step("zoom", step2(0, 1, 0), "getZoomStep");
        step("zoom", r(0), "setOpticalZoomStep", 0);
        step("zoom", r(0), "zoomHome");
        step("zoom", r(0), "zoomSessionFinish");
        step("zoom", r(true), "setKstPrepareForZoom", false);
        step("zoom", r(0), "setOpticalZoomStep", 1);
        Object topSpeed = enumValue("GameModeOption", 2);
        step("option", r(true), "setGameModeOption", topSpeed);
        step("option", r(6, true), "resetKeystoneForTopSpeedGame");
        step("option", r(6, true), "resetKeystoneStock");
        step("option", new Object[0], "getGameModeOption");
        Wire.clock += 9_000;
        step("option", r(0), "getGameModeOption");
        step("option", r(false), "setGameModeOption", enumValue("GameModeOption", 1));
        step("option", r(1), "getGameModeOption");
        Wire.clock += 100_000;
        for (int i = 0; i < 8; i++) {
            Wire.clock += i == 1 ? 10 : 2_000;
            step("tof", r(1234), "getTofDistance");
        }
        Object[] motion = z9x() ? r(new byte[] {1, 0, 2, 0, -1, -1, 4, 0, 5, 0, 6, 0, 3, 0})
                : r(1, 40, 3, 1, 2, -1, 3, 4, 5, 6, 3);
        Object[] motionShort = z9x() ? r(new byte[] {1, 0}) : r(1, 32, 2, 1, 2, 3, 4, 5, 6, 3);
        for (int i = 0; i < 3; i++) {
            Wire.clock += i == 1 ? 10 : 1_000;
            step("motion", i == 2 ? motionShort : motion, "getMotionDetection");
        }
        Object[] rot = z9x() ? r(true, floats(-10f, 10f, 0f, 0f)) : r(true, 1, 20, -10f, 10f, 0f, 0f);
        step("struct", rot, "getScreenRotation");
        Object[] rotBad = z9x() ? r(true, floats(-50f, 10f, 0f, 0f)) : r(true, 1, 20, -50f, 10f, 0f, 0f);
        step("struct", rotBad, "getScreenRotation");
        step("struct", z9x() ? r(false) : r(false, 1, 20, -10f, 10f, 0f, 0f), "getScreenRotation");
        step("struct", z9x() ? r(true, ints(0, 0, 1)) : r(true, 1, 16, 0, 0, 1), "getGameModeProp");
        step("struct", z9x() ? r(false) : r(false, 1, 16, 0, 0, 1), "getGameModeProp");
        step("struct", z9x() ? r(true, ints(50, 10, 20, 30)) : r(true, 1, 20, 50, 10, 20, 30), "getMultiPointWb", 50);
        step("struct", z9x() ? r(true, ints(40, 10, 20, 30)) : r(true, 1, 20, 40, 10, 20, 30), "getMultiPointWb", 50);
        step("struct", r(true), "setMultiPointWb", 50, 10, 20, 30);
        Object hsy = enumValue("HsyType", 0);
        byte[] h7 = {50, 40, 30, 20, 10, 0, 100};
        step("struct", z9x() ? r(true, h7) : r(true, 7, 0x141e2832, 0x64000a), "getHsyColorModel", hsy);
        byte[] bad = {50, 40, 30, 20, 10, 0, 101};
        step("struct", z9x() ? r(true, bad) : r(true, 7, 0x141e2832, 0x65000a), "getHsyColorModel", hsy);
        step("struct", z9x() ? r(true, new byte[] {1, 2}) : r(true, 6, 0x141e2832, 0x65000a), "getHsyColorModel", hsy);
        step("struct", z9x() ? r(new byte[] {0, 15, 0, 0, 112, 8, 0, 0, 0, 1, 0, 0, 102, 23, 0, 0})
                : r(1, 24, 3840, 2160, 0, 1, 5990), "getHdmiInfo");
        step("struct", z9x() ? r(new byte[16]) : r(1, 24, 0, 0, 0, 0, 0), "getHdmiInfo");
        step("struct", z9x() ? r(true, ints(4, 3840, 2160, 4, 1920, 1080, 120, 6, 4, 7)) : r(), "getOutputTimingInfo");
        step("struct", z9x() ? r(false) : r(), "getOutputTimingInfo");
        step("struct", z9x() ? r(kstBlob()) : kstHb(), "getKeystonePoints");
        Wire.failNext = true;
        step("error", r(1), "getLampLevel");
        Wire.failNext = true;
        step("error", r(1), "isBoost");
        step("error", r(1), "isBoost");
        if (!z9x()) {
            Wire.unimplementedNext = true;
            step("error", r(1), "getLampLevel");
        }
        step("callback", new Object[0], "connectFocusManager");
        callback();
    }

    private static byte[] floats(float... v) {
        int[] i = new int[v.length];
        for (int k = 0; k < v.length; k++) i[k] = Float.floatToIntBits(v[k]);
        return ints(i);
    }

    private static byte[] kstBlob() throws Exception {
        Class<?> k = Class.forName("org.lumen.projector.hal.KstPoint");
        Object full = k.getMethod("fullFrame").invoke(null);
        Method tb = k.getDeclaredMethod("toBlob");
        tb.setAccessible(true);
        Object blob = tb.invoke(full);
        java.lang.reflect.Field f = blob.getClass().getDeclaredField("b");
        f.setAccessible(true);
        return ((byte[]) f.get(blob)).clone();
    }

    private static Object[] kstHb() {
        List<Object> w = new ArrayList<>(Arrays.asList(1, 12 + 81 * 16, 0, 81));
        for (int i = 0; i < 81; i++) w.addAll(Arrays.asList(1, 12, (i % 9) * 400, (i / 9) * 200));
        return w.toArray();
    }

    private static Object enumValue(String simple, int wire) throws Exception {
        Class<?> c = Class.forName("org.lumen.projector.hal." + simple);
        for (Object o : c.getEnumConstants()) {
            try {
                if (c.getField("wire").getInt(o) == wire) return o;
            } catch (NoSuchFieldException e) {
                return c.getEnumConstants()[wire];
            }
        }
        throw new IllegalStateException(simple + " " + wire);
    }

    /** The vendor calls our focus callback: an event, the meta and IBase codes, a parse error. */
    private static void callback() throws Exception {
        Object cb = Wire.lastBinder;
        if (cb == null) {
            OUT.append("! no callback binder\n");
            return;
        }
        if (z9x()) {
            Method on = cb.getClass().getMethod("onTransact", int.class, HwParcel.class, HwParcel.class, int.class);
            on.setAccessible(true);
            for (int code : new int[] {1, 0x0f43484e, 0x0f445343, 0x0f504e47, 0x0f485348, 0x0f524546, 0x0f444247, 0x0f494e54, 99}) {
                block("callback onTransact " + code, 0, () -> {
                    Wire.SCRIPT.add(5);
                    Wire.SCRIPT.add("value");
                    on.invoke(cb, code, new HwParcel(), new HwParcel(), 0);
                    return null;
                }, "callback");
            }
        } else {
            for (int code : new int[] {1, 0x00FFFFFF, 0x00FFFFFE, 77}) {
                block("callback transact " + code, 0, () -> {
                    Parcel d = Parcel.obtain();
                    d.writeInt(5);
                    d.writeString("value");
                    d.setDataPosition(0);
                    Parcel rp = Parcel.obtain();
                    boolean ok = ((android.os.IBinder) cb).transact(code, d, rp, 1);
                    return ok + " reply " + rp.dataSize();
                }, "callback");
            }
        }
    }

    // ------------------------------------------------------------------ recording
    private static Object proxy(Class<?> iface) {
        return Proxy.newProxyInstance(WireTrace.class.getClassLoader(), new Class<?>[] {iface}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return m.getName().equals("hashCode") ? 7 : null;
            Wire.log("EVENT " + m.getName() + (a == null ? "" : Arrays.toString(a)));
            return null;
        });
    }

    private static void features(boolean on) {
        try {
            Class<?> f = Class.forName("org.lumen.projector.hal.HbFeatures");
            f.getDeclaredField("allOn").setBoolean(null, on);
        } catch (ClassNotFoundException e) {
            // no feature table on this projector
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void block(String title, Call c) {
        block(title, 0, c, title);
    }

    private static void block(String title, int seed, Call c, String key) {
        Wire.LOG.clear();
        Wire.reset(seed);
        String res;
        try {
            Object v = c.run();
            res = "= " + value(key, v);
        } catch (Throwable t) {
            Throwable x = t instanceof InvocationTargetException ? t.getCause() : t;
            res = "! " + x.getClass().getSimpleName() + ": " + x.getMessage();
        }
        Wire.SCRIPT.clear();
        OUT.append("C ").append(title).append('\n');
        for (String l : Wire.LOG) OUT.append("  ").append(l).append('\n');
        OUT.append("  ").append(res).append('\n');
    }

    private static String value(String key, Object v) {
        if (dev.equals("hb") && key.startsWith("getVrrState")) {
            return "eq1 " + (v instanceof Boolean ? (Boolean) v : ((Integer) v) == 1);
        }
        if (v == null) return "null";
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof float[]) return Arrays.toString((float[]) v);
        if (v instanceof String[]) return Arrays.toString((String[]) v);
        return String.valueOf(v);
    }

    private static String show(Object[] a) {
        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < a.length; i++) {
            Object x = a[i];
            s.append(i == 0 ? "" : ",").append(x instanceof int[] ? Arrays.toString((int[]) x) : String.valueOf(x));
        }
        return s.append(')').toString();
    }
}
