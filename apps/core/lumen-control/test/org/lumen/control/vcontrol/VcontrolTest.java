// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import static org.lumen.control.vcontrol.Check.eq;
import static org.lumen.control.vcontrol.Check.yes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.lumen.control.ControlSink;
import org.lumen.control.KeyAction;
import org.lumen.control.RemoteKey;
import org.lumen.control.ucontrol.UcontrolKeys;
import org.lumen.control.utils.HashedId;
import org.lumen.control.utils.LanAddress;

/**
 * Host tests of the phone remote adapter. The packets are real ones: what the XGIMI app (com.xgimi.zhushou,
 * ConnectManagerV2) and the open Home Assistant client send; the answers are what stock vcontrol 2.5.x writes for
 * them, field for field (CallBackCenter + Gson). java ... VcontrolTest <gsi tree>
 */
public final class VcontrolTest {
    private static final String PHONE = "<lan-ip>";
    private static final String TCP_PHONE = "/<lan-ip>:50000";
    private static final String UDP_AGREEMENT_1 =
            "\"agreement\":{\"agreementType\":0,\"isTcp\":true,\"msgId\":\"1\",\"phoneIp\":\"<lan-ip>\",\"port\":13145}}";

    private static FakeSink sink;
    private static FakeFacts facts;
    private static Vcontrol x50;

    public static void main(String[] args) throws Exception {
        String gsi = args[0];
        reset(VcontrolModels.of("G0111"));
        discovery();
        connect();
        heartbeat();
        controlCmd();
        keyEvent();
        phoneApp();
        volume();
        smallActions();
        unknownAndBroken();
        json();
        models();
        keyTables(gsi);
        frame();
        ucontrol();
        lan();
        pid();
        server();
        System.out.println("VcontrolTest: " + Check.passes + " passed, " + Check.failures + " failed");
        System.exit(Check.failures == 0 ? 0 : 1);
    }

    private static void reset(VcontrolModels m) {
        sink = new FakeSink();
        facts = new FakeFacts();
        x50 = new Vcontrol(m, facts, sink);
    }

    private static String udp(String msg) {
        return x50.handle(Request.parse(msg), Peer.udp(PHONE), Ports.TCP);
    }

    private static String tcp(String msg) {
        return x50.handle(Request.parse(msg), Peer.tcp(TCP_PHONE), Ports.TCP);
    }

    private static String status(int action, String msgid, int state) {
        return "{\"action\":" + action + ",\"endScreenCapTime\":0,\"msgid\":\"" + msgid + "\",\"startScreenCapTime\":0,"
                + "\"status\":{\"state\":" + state + "},\"type\":0,\"agreement\":{\"agreementType\":0,\"isTcp\":true,"
                + "\"msgId\":\"" + msgid + "\",\"phoneIp\":\"<lan-ip>\",\"port\":13145}}";
    }

    /** The XGIMI app's discovery broadcast to UDP 16752. */
    private static void discovery() {
        eq("discovery answer", "{\"action\":9999,\"deviceInfo\":{\"currentVolume\":0,\"device\":\"horse_popular\","
                + "\"deviceip\":\"<lan-ip>\",\"devicename\":\"XGIMI X50 Ultra\",\"devicetype\":\"unkonwn\","
                + "\"maxVolume\":0,\"pid\":\"0123456789ABCDEF\",\"productName\":\"Product_dianapro\",\"versioncode\":0},"
                + "\"endScreenCapTime\":0,\"msgid\":\"1\",\"startScreenCapTime\":0,\"type\":0," + UDP_AGREEMENT_1,
                udp("{\"action\":9998,\"msgid\":\"1\",\"appid\":\"1\"}"));
        eq("discovery runs nothing", "", sink.all());
        facts.name = "A&B <x>='y'";
        yes("names escaped as Gson does", udp("{\"action\":9998,\"msgid\":\"1\"}")
                .contains("\"devicename\":\"A\\u0026B \\u003cx\\u003e\\u003d\\u0027y\\u0027\""));
        facts.name = "XGIMI X50 Ultra";
    }

    /** Connect over TCP with an auth code of a phone stock would ask a code from: answered at once. */
    private static void connect() {
        eq("connect answer", "{\"action\":10001,\"deviceInfo\":{\"currentVolume\":7,\"device\":\"horse_popular\","
                + "\"devicetype\":\"unkonwn\",\"ipaddress\":\"/<lan-ip>:50000\",\"mac\":\"\",\"maxVolume\":15,"
                + "\"pid\":\"0123456789ABCDEF\",\"productName\":\"Product_dianapro\",\"versioncode\":20500408},"
                + "\"endScreenCapTime\":0,\"msgid\":\"7\",\"startScreenCapTime\":0,\"type\":0,\"agreement\":{"
                + "\"agreementType\":1,\"isTcp\":true,\"msgId\":\"7\",\"phoneIp\":\"/<lan-ip>:50000\",\"port\":13145}}",
                tcp("{\"action\":10000,\"msgid\":\"7\",\"authCode\":{\"authState\":2,\"phoneUid\":\"u1\"}}"));
        Vcontrol z9x = new Vcontrol(VcontrolModels.of("G0082"), facts, sink);
        yes("Z9X connect: its own version code",
                z9x.handle(Request.parse("{\"action\":10000}"), Peer.udp(PHONE), Ports.TCP).contains("\"versioncode\":20500320"));
    }

    private static void heartbeat() {
        eq("heartbeat, blank msgid is 0, no TCP", "{\"action\":10003,\"endScreenCapTime\":0,\"msgid\":\"0\","
                + "\"startScreenCapTime\":0,\"type\":0,\"agreement\":{\"agreementType\":0,\"isTcp\":false,\"msgId\":\"0\","
                + "\"phoneIp\":\"<lan-ip>\"}}",
                x50.handle(Request.parse("{\"action\":10002,\"msgid\":\" \"}"), Peer.udp(PHONE), null));
    }

    private static void controlCmd() {
        sink.ran.clear();
        String[] want = {"LEFT", "UP", "RIGHT", "DOWN", "OK", "BACK", "MENU", "HOME", "VOLUME_UP", "VOLUME_DOWN"};
        for (int t = 1; t <= 10; t++) {
            eq("keyMode " + t + " no answer", null, udp("{\"action\":20000,\"controlCmd\":{\"mode\":0,\"type\":" + t + "}}"));
            eq("keyMode " + t, "REMOTE_KEY " + want[t - 1] + " CLICK", sink.ran.get(sink.ran.size() - 1));
        }
        sink.ran.clear();
        eq("keyMode 11 ignored", null, udp("{\"action\":20000,\"controlCmd\":{\"mode\":0,\"type\":11}}"));
        eq("keyMode 0 ignored", null, udp("{\"action\":20000,\"controlCmd\":{\"mode\":0,\"type\":0}}"));
        eq("keyMode unknown types run nothing", "", sink.all());
        eq("power off: 20001 with the msgid as sent (none)", "{\"action\":20001,\"endScreenCapTime\":0,"
                + "\"startScreenCapTime\":0,\"type\":0,\"agreement\":{\"agreementType\":0,\"isTcp\":true,\"msgId\":\"0\","
                + "\"phoneIp\":\"<lan-ip>\",\"port\":13145}}",
                udp("{\"action\":20000,\"controlCmd\":{\"mode\":6,\"type\":0}}"));
        eq("power off is the power key", "REMOTE_KEY POWER CLICK", sink.all());
        sink.ran.clear();
        eq("reboot not served", null, udp("{\"action\":20000,\"controlCmd\":{\"mode\":6,\"type\":1}}"));
        eq("no controlCmd: stock gets no answer", null, udp("{\"action\":20000,\"msgid\":\"3\"}"));
        // Home Assistant's advanced command (Python str(dict), mode 5 = voice text): parsed, not served
        yes("HA heartbeat in single quotes", udp("{'action': 10002, 'msgid': '2'}").contains("\"msgid\":\"2\""));
        eq("HA voice command", null, udp("{'action': 20000, 'controlCmd': {'data': 'volumeup', 'delayTime': 0, "
                + "'mode': 5, 'time': 0, 'type': 0}, 'msgid': '2'}"));
        eq("mobile AF and zoom not served", null, udp("{\"action\":20000,\"controlCmd\":{\"mode\":31,\"data\":\"1\"}}"));
        eq("zoom not served", null, udp("{\"action\":20000,\"controlCmd\":{\"mode\":2,\"type\":1}}"));
        eq("nothing ran", "", sink.all());
    }

    private static String key(String type, String action, String code) {
        return udp("{\"action\":50036,\"msgid\":\"5\",\"commonCmd\":{\"type\":" + type + ",\"action\":" + action
                + ",\"code\":" + code + "}}");
    }

    private static void keyEvent() {
        String[] want = {"LEFT", "RIGHT", "UP", "DOWN", "OK", "BACK", "MENU", "HOME", "VOLUME_UP", "VOLUME_DOWN",
            "POWER", "GEAR", "POWER", "FOCUS_LEFT", "FOCUS_RIGHT"};
        for (int c = 1; c <= 15; c++) {
            sink.ran.clear();
            eq("key code " + c, status(50036, "5", 1), key("\"inst\"", "\"3\"", "\"" + c + "\""));
            eq("key code " + c + " runs", "REMOTE_KEY " + want[c - 1] + " CLICK", sink.all());
        }
        sink.ran.clear();
        key("\"inst\"", "\"1\"", "\"14\"");
        key("\"inst\"", "\"2\"", "\"14\"");
        eq("held focus key: down then up", "REMOTE_KEY FOCUS_LEFT DOWN;REMOTE_KEY FOCUS_LEFT UP", sink.all());
        sink.ran.clear();
        eq("an Android key code passes", status(50036, "5", 1), key("\"inst\"", "\"3\"", "\"66\""));
        eq("an Android key code runs", "ANDROID_KEY 66 CLICK", sink.all());
        eq("key code 0 passes as stock", status(50036, "5", 1), key("\"inst\"", "\"3\"", "\"0\""));
        sink.ran.clear();
        eq("other sender", status(50036, "5", 0), key("\"adb\"", "\"3\"", "\"5\""));
        eq("no sender", status(50036, "5", 0), key("\"\"", "\"3\"", "\"5\""));
        eq("negative code", status(50036, "5", 0), key("\"inst\"", "\"3\"", "\"-1\""));
        eq("action 4", status(50036, "5", 0), key("\"inst\"", "\"4\"", "\"5\""));
        eq("action 0", status(50036, "5", 0), key("\"inst\"", "\"0\"", "\"5\""));
        eq("code a JSON number (stock reads a Double, not a String)", status(50036, "5", 0), key("\"inst\"", "\"3\"", "5"));
        eq("code with a space", status(50036, "5", 0), key("\"inst\"", "\"3\"", "\" 5\""));
        eq("refused keys ran nothing", "", sink.all());
        eq("sign accepted as Kotlin toIntOrNull", status(50036, "5", 1), key("\"inst\"", "\"+3\"", "\"+5\""));
        eq("no commonCmd: stock gets no answer", null, udp("{\"action\":50036,\"msgid\":\"5\"}"));
        sink.ok = false;
        eq("a key that did not happen", status(50036, "5", 0), key("\"inst\"", "\"3\"", "\"5\""));
        sink.ok = true;
    }

    /**
     * The exact bytes of the XGIMI app com.xgimi.zhushou 4.10.31 (versionCode 40423, signer CN=xgimi, certificate
     * sha256 73860369...ddbb): Gson writes its beans' fields in dex order (VcontrolCmd, AuthCode, TcpKeyCommand of
     * ConnectManagerV2, ToolManager.handleAuth and GMKeyTcpCommandFactory).
     */
    private static void phoneApp() {
        sink.ran.clear();
        yes("app search", udp("{\"action\":9998,\"appid\":\"1\",\"msgid\":\"1\"}").startsWith("{\"action\":9999,\"deviceInfo\":{"));
        for (int state : new int[] {-1, 0, 1, 2}) {
            String r = tcp("{\"action\":10000,\"appid\":\"1\",\"authCode\":{\"authState\":" + state + ",\"ip\":\"<lan-ip>\","
                    + "\"phoneName\":\"Pixel 7\",\"phoneUid\":\"6f1c\",\"timeOut\":0},\"msgid\":\"1\"}");
            yes("app connect, auth state " + state + ": 10001 (the app logs it as paired)", r != null && r.startsWith("{\"action\":10001,"));
        }
        eq("app heartbeat after connect", true, tcp("{\"action\":10002,\"msgid\":\"2\"}").startsWith("{\"action\":10003,"));
        String back = tcp("{\"action\":50036,\"commonCmd\":{\"action\":\"3\",\"code\":\"6\",\"type\":\"inst\"}}");
        yes("app BACK answered", back.startsWith("{\"action\":50036,") && back.contains("\"msgid\":\"0\"") && back.contains("\"status\":{\"state\":1}"));
        tcp("{\"action\":50036,\"commonCmd\":{\"action\":\"3\",\"code\":\"131\",\"type\":\"inst\"}}");
        tcp("{\"action\":50036,\"commonCmd\":{\"action\":\"1\",\"code\":\"14\",\"type\":\"inst\"}}");
        tcp("{\"action\":50036,\"commonCmd\":{\"action\":\"2\",\"code\":\"14\",\"type\":\"inst\"}}");
        tcp("{\"action\":50036,\"commonCmd\":{\"action\":\"3\",\"code\":\"11\",\"type\":\"inst\"}}");
        eq("app keys", "REMOTE_KEY BACK CLICK;ANDROID_KEY 131 CLICK;REMOTE_KEY FOCUS_LEFT DOWN;REMOTE_KEY FOCUS_LEFT UP;"
                + "REMOTE_KEY POWER CLICK", sink.all());
    }

    private static void volume() {
        sink.ran.clear();
        eq("volume", status(50009, "6", 1), udp("{\"action\":50009,\"msgid\":\"6\",\"commonCmd\":{\"volume\":\"12\"}}"));
        eq("volume runs", "AUDIO_VOLUME 12 CLICK", sink.all());
        sink.ran.clear();
        eq("volume as a number", status(50009, "6", 0), udp("{\"action\":50009,\"msgid\":\"6\",\"commonCmd\":{\"volume\":12}}"));
        eq("no volume", status(50009, "6", 0), udp("{\"action\":50009,\"msgid\":\"6\"}"));
        eq("volume not a number: no answer", null, udp("{\"action\":50009,\"msgid\":\"6\",\"commonCmd\":{\"volume\":\"x\"}}"));
        eq("refused volumes ran nothing", "", sink.all());
    }

    private static void smallActions() {
        sink.ran.clear();
        eq("autofocus", status(46000, "8", 1), udp("{\"action\":46000,\"msgid\":\"8\"}"));
        eq("autofocus runs the focus key", "REMOTE_KEY AUTOFOCUS CLICK", sink.all());
        Vcontrol noAf = new Vcontrol(new VcontrolModels("x", 1, "v1", false), facts, sink);
        sink.ran.clear();
        eq("no autofocus on the model", status(46000, "8", 0),
                noAf.handle(Request.parse("{\"action\":46000,\"msgid\":\"8\"}"), Peer.udp(PHONE), Ports.TCP));
        eq("no autofocus runs nothing", "", sink.all());
        eq("sound off", status(50012, "8", 1), udp("{\"action\":50012,\"msgid\":\"8\"}"));
        eq("sound off is mute", "REMOTE_KEY MUTE CLICK", sink.all());
        sink.ran.clear();
        eq("dormancy", status(50021, "8", 1), udp("{\"action\":50021,\"msgid\":\"8\"}"));
        eq("dormancy is the power key (Lumen: short press = sleep)", "REMOTE_KEY POWER CLICK", sink.all());
        sink.ran.clear();
        eq("auth code shown", status(50000, "8", 1), udp("{\"action\":50000,\"msgid\":\"8\"}"));
        eq("auth code typed: no answer", null, udp("{\"action\":50001,\"msgid\":\"8\",\"authCode\":{\"verificationCode\":\"1234\"}}"));
        eq("auth accepted", status(50002, "8", 1), udp("{\"action\":50002,\"msgid\":\"8\",\"status\":{\"state\":1}}"));
        eq("auth refused", status(50002, "8", 0), udp("{\"action\":50002,\"msgid\":\"8\",\"status\":{\"state\":2}}"));
        eq("auth no status", status(50002, "8", 0), udp("{\"action\":50002,\"msgid\":\"8\"}"));
        eq("phone connected: no answer", null, udp("{\"action\":50010}"));
        eq("phone gone: no answer", null, udp("{\"action\":50011}"));
        eq("small actions ran nothing more", "", sink.all());
    }

    private static void unknownAndBroken() {
        sink.ran.clear();
        eq("a stock action Lumen does not do", status(50003, "9", 0), udp("{\"action\":50003,\"msgid\":\"9\"}"));
        eq("picture wall", status(70004, "9", 0), udp("{\"action\":70004,\"msgid\":\"9\"}"));
        eq("unknown action", "{\"action\":12346,\"endScreenCapTime\":0,\"extraInfo\":{\"message\":\"Unknown exception\"},"
                + "\"msgid\":\"9\",\"startScreenCapTime\":0,\"type\":0,\"agreement\":{\"agreementType\":0,\"isTcp\":true,"
                + "\"msgId\":\"9\",\"phoneIp\":\"<lan-ip>\",\"port\":13145}}", udp("{\"action\":12345,\"msgid\":\"9\"}"));
        eq("no action is action 0", true, udp("{}").startsWith("{\"action\":1,"));
        eq("not JSON", null, udp("KEYPRESSES:49"));
        eq("an array", null, udp("[1,2]"));
        eq("text after the object", null, udp("{\"action\":9998} x"));
        eq("action not a number", null, udp("{\"action\":\"x\"}"));
        eq("action 9998.5", null, udp("{\"action\":9998.5}"));
        eq("controlCmd not an object", null, udp("{\"action\":20000,\"controlCmd\":\"x\"}"));
        yes("action as a numeric string", udp("{\"action\":\"9998\",\"msgid\":\"1\"}").startsWith("{\"action\":9999,"));
        yes("action 9998.0", udp("{\"action\":9998.0}").startsWith("{\"action\":9999,"));
        yes("msgid as a number", udp("{\"action\":10002,\"msgid\":42}").contains("\"msgid\":\"42\""));
        yes("msgid as a boolean", udp("{\"action\":10002,\"msgid\":true}").contains("\"msgid\":\"true\""));
        eq("msgid an object", null, udp("{\"action\":10002,\"msgid\":{}}"));
        yes("action with spaces (Gson parses the double)", udp("{\"action\":\" 9998 \"}").startsWith("{\"action\":9999,"));
        eq("broken messages ran nothing", "", sink.all());
        eq("null request", null, x50.handle(null, Peer.udp(PHONE), Ports.TCP));
    }

    private static void json() {
        try {
            Object v = Json.parse("{'a': [1, -2.5e3, true, null], b: \"q\\\"\\u0041\\n\"}");
            eq("lenient json", "{a=[1, -2.5e3, true, null], b=q\"A\n}", String.valueOf(v));
        } catch (Json.BadJson e) {
            eq("lenient json", "parsed", e.getMessage());
        }
        for (String bad : new String[] {"", "{", "{\"a\"}", "{\"a\":1,}", "\"open", "[1 2]", "{\"a\":\"\\u12\"}"}) {
            try {
                Json.parse(bad);
                eq("bad json " + bad, "refused", "parsed");
            } catch (Json.BadJson e) {
                Check.passes++;
            }
        }
        String deepest = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);
        try {
            eq("nesting at the limit", "[[]]", String.valueOf(Json.parse("[[]]")));
            yes("nesting of " + Json.MAX_DEPTH, Json.parse(deepest) != null);
        } catch (Json.BadJson e) {
            eq("nesting at the limit", "parsed", e.getMessage());
        }
        for (String deep : new String[] {"[" + deepest + "]", "{\"a\":" + deepest.replace("[]", "{}") + "}", "[".repeat(200000)}) {
            try {
                Json.parse(deep);
                eq("too deep " + deep.length(), "refused", "parsed");
            } catch (Json.BadJson e) {
                yes("too deep " + deep.length() + " refused by the limit", e.getMessage().startsWith("nested deeper"));
            }
        }
        try {
            yes("many siblings are not depth", Json.parse("[" + "{\"a\":[1]},".repeat(3 * Json.MAX_DEPTH) + "{}]") != null);
        } catch (Json.BadJson e) {
            eq("many siblings are not depth", "parsed", e.getMessage());
        }
        eq("a deep message is dropped", null, Request.parse("{\"action\":9998,\"x\":" + "[".repeat(5000) + "}"));
        eq("Gson order and nulls", "{\"b\":1,\"a\":\"x\\\\y\\t\\u0001\"}",
                new Json.Obj().put("b", 1).put("n", null).put("a", "x\\y\t\u0001").toString());
    }

    private static void models() {
        Map<String, String> want = new HashMap<>();
        want.put("G0082", "horse_popular 20500320 v2.5.3.20");
        want.put("G0130", "goat_elegant 20500406 v2.5.4.6");
        want.put("G0148", "goat_elegant 20500502 v2.5.5.2");
        want.put("G0058", "dragon_healthy 20500319 v2.5.3.19");
        want.put("G0111", "horse_popular 20500408 v2.5.4.8");
        want.put("G0100", "horse_popular 20500408 v2.5.4.8");
        want.put("G0155", "horse_popular 20500502 v2.5.5.2");
        want.put("H0062", "horse_popular 20500408 v2.5.4.8");
        want.put("H0061", "horse_popular 20500408 v2.5.4.8");
        want.put("H0063", "horse_popular 20500408 v2.5.4.8");
        for (Map.Entry<String, String> e : want.entrySet()) {
            VcontrolModels m = VcontrolModels.of(e.getKey());
            eq("model " + e.getKey(), e.getValue(), m == null ? null : m.device + " " + m.versionCode + " " + m.versionName);
            yes("model " + e.getKey() + " autofocus", m != null && m.autofocus);
        }
        yes("model code trimmed", VcontrolModels.of(" G0130\n") != null);
        eq("unknown model", null, VcontrolModels.of("G9999"));
        eq("no model", null, VcontrolModels.of(null));
    }

    /** Every remote key of a keypad scancode is mapped by each Lumen key layout of that keypad. */
    private static void keyTables(String gsi) throws IOException {
        for (String kl : new String[] {"overlay/keylayout/Vendor_3697_Product_0002.kl",
                "overlay/v1/keylayout/Vendor_3697_Product_0002.kl"}) {
            Map<Integer, String> keys = new HashMap<>();
            for (String line : Files.readAllLines(Paths.get(gsi, kl), StandardCharsets.UTF_8)) {
                String[] w = line.replaceAll("#.*", "").trim().split("\\s+");
                if (w.length >= 3 && w[0].equals("key")) keys.put(Integer.parseInt(w[1]), w[2]);
            }
            for (RemoteKey k : RemoteKey.values()) {
                if (k.scancode == RemoteKey.NONE) continue;
                yes(kl + " maps " + k + " (" + k.scancode + ")", keys.containsKey(k.scancode));
            }
            eq(kl + " OK", "DPAD_CENTER", keys.get(RemoteKey.OK.scancode));
            eq(kl + " LEFT", "DPAD_LEFT", keys.get(RemoteKey.LEFT.scancode));
            eq(kl + " BACK", "BACK", keys.get(RemoteKey.BACK.scancode));
            eq(kl + " VOLUME_UP", "VOLUME_UP", keys.get(RemoteKey.VOLUME_UP.scancode));
        }
        yes("autofocus has no keypad scancode (279 is past the keypad's 255)", RemoteKey.AUTOFOCUS.scancode == RemoteKey.NONE);
        eq("autofocus is the FOCUS key", 80, RemoteKey.AUTOFOCUS.keyCode);
    }

    private static void frame() throws IOException {
        byte[] f = Frame.encode("{}");
        eq("frame bytes", "ffffe04a000000027b7d", hex(f));
        byte[] junk = {1, (byte) 0xE0, 2, (byte) 0xE0, 0x4A, 0, 0, 0, 0};
        InputStream in = new ByteArrayInputStream(concat(junk, f, Frame.encode("ä")));
        eq("frame after junk and an empty frame", "{}", Frame.read(in));
        eq("utf-8 frame", "ä", Frame.read(in));
        eq("end of stream", null, Frame.read(in));
        eq("cut body", "", Frame.read(new ByteArrayInputStream(new byte[] {(byte) 0xE0, 0x4A, 0, 0, 0, 5, 'a'})));
        eq("cut length", "", Frame.read(new ByteArrayInputStream(new byte[] {(byte) 0xE0, 0x4A, 0, 0})));
        try {
            Frame.read(new ByteArrayInputStream(new byte[] {(byte) 0xE0, 0x4A, 0x7F, 0, 0, 0}));
            eq("huge frame", "refused", "read");
        } catch (IOException e) {
            Check.passes++;
        }
    }

    private static void ucontrol() throws IOException {
        eq("click", "KEYPRESSES:49", UcontrolKeys.message(49, KeyAction.CLICK));
        eq("held down", "KEYSSTATUS:253+1", UcontrolKeys.message(253, KeyAction.DOWN));
        eq("released", "KEYSSTATUS:253+0", UcontrolKeys.message(253, KeyAction.UP));
        eq("past the keypad", null, UcontrolKeys.message(279, KeyAction.CLICK));
        eq("last keypad key", "KEYPRESSES:255", UcontrolKeys.message(255, KeyAction.CLICK));
        eq("one past the keypad", null, UcontrolKeys.message(256, KeyAction.CLICK));
        eq("no scancode", null, UcontrolKeys.message(RemoteKey.NONE, KeyAction.CLICK));
        try (DatagramSocket daemon = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            daemon.setSoTimeout(3000);
            UcontrolKeys k = new UcontrolKeys(InetAddress.getLoopbackAddress(), daemon.getLocalPort());
            yes("press sent", k.press(RemoteKey.OK.scancode, KeyAction.CLICK));
            yes("past the keypad not sent", !k.press(279, KeyAction.CLICK));
            DatagramPacket p = new DatagramPacket(new byte[64], 64);
            daemon.receive(p);
            eq("daemon got", "KEYPRESSES:49", new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII));
        }
    }

    private static void lan() throws IOException {
        // the private ranges are joined at run time: the public export check refuses them as literal addresses
        for (String a : new String[] {"<lan-ip>", String.join(".", "10", "0", "0", "1"), String.join(".", "172", "16", "5", "1"),
                "127.0.0.1", "169.254.1.1", "fd" + "00::1", "fe80::1", "::1"}) {
            yes("LAN " + a, LanAddress.isLan(InetAddress.getByName(a)));
        }
        for (String a : new String[] {"8.8.8.8", "172.32.0.1", "2001:db8::1", "100.64.0.1", "::8.8.8.8", "fe00::1", "252.1.2.3", "253.0.0.1"}) {
            yes("not LAN " + a, !LanAddress.isLan(InetAddress.getByName(a)));
        }
        yes("no address", !LanAddress.isLan(null));
    }

    private static final String SALT = "org.lumen.control.vcontrol.pid:";

    private static void pid() {
        String p = HashedId.of(SALT, "<serial>", 8);
        eq("pid stable", p, HashedId.of(SALT, "<serial>", 8));
        yes("pid is 16 hex", p.matches("[0-9A-F]{16}"));
        yes("pid is not the serial", !p.contains("XHN4"));
        yes("pid differs per serial", !p.equals(HashedId.of(SALT, "XHN4RZEC9YX7", 8)));
        eq("no serial", "", HashedId.of(SALT, "", 8));
    }

    /** The sockets end to end on loopback: UDP discovery answered to the phone port, TCP framed connect. */
    private static void server() throws Exception {
        int phonePort;
        try (DatagramSocket phone = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            phonePort = phone.getLocalPort();
            Endpoints e = new Endpoints(freeUdp(), freeUdp(), phonePort, freeTcp());
            reset(VcontrolModels.of("G0130"));
            VcontrolServer s = new VcontrolServer(x50, e, (what, t) -> { });
            s.start();
            try {
                eq("tcp up on its port", Integer.valueOf(e.tcp), s.tcpPort());
                phone.setSoTimeout(5000);
                String d;
                try (DatagramSocket other = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
                    send(other, e.discovery, "{\"action\":9998,\"msgid\":\"1\",\"appid\":\"1\"}");
                    d = receive(phone);
                }
                yes("udp discovery answered", d.startsWith("{\"action\":9999,") && d.contains("\"device\":\"goat_elegant\""));
                yes("udp answer names the phone and the TCP port", d.contains("\"phoneIp\":\"127.0.0.1\",\"port\":" + e.tcp + "}"));
                send(phone, e.commands, "{\"action\":20000,\"controlCmd\":{\"mode\":0,\"type\":5}}");
                send(phone, e.commands, "{\"action\":10002,\"msgid\":\"4\"}");
                yes("udp heartbeat answered", receive(phone).startsWith("{\"action\":10003,"));
                eq("udp key ran", "REMOTE_KEY OK CLICK", sink.all());
                try (Socket c = new Socket(InetAddress.getLoopbackAddress(), e.tcp)) {
                    c.setSoTimeout(5000);
                    OutputStream out = c.getOutputStream();
                    out.write(new byte[] {9, 9, 9});
                    out.write(Frame.encode("{\"action\":10000,\"msgid\":\"2\"}"));
                    out.flush();
                    String r = Frame.read(c.getInputStream());
                    yes("tcp connect answered", r != null && r.startsWith("{\"action\":10001,"));
                    yes("tcp answer is agreement 1", r != null && r.contains("\"agreementType\":1,"));
                    yes("tcp answer names the connection", r != null && r.contains("\"ipaddress\":\"/127.0.0.1:" + c.getLocalPort() + "\""));
                    out.write(Frame.encode("not json"));
                    out.write(Frame.encode("{\"action\":10002,\"msgid\":\"3\"}"));
                    out.flush();
                    String h = Frame.read(c.getInputStream());
                    yes("tcp survives a bad message", h != null && h.startsWith("{\"action\":10003,"));
                    out.write(Frame.encode("{\"action\":10002,\"x\":" + "[".repeat(300000) + "}"));
                    out.write(Frame.encode("{\"action\":10002,\"msgid\":\"5\"}"));
                    out.flush();
                    String deep = Frame.read(c.getInputStream());
                    yes("tcp survives a deep message", deep != null && deep.contains("\"msgid\":\"5\""));
                }
                send(phone, e.commands, "{\"action\":10002,\"x\":" + "[".repeat(8000) + "}");
                send(phone, e.commands, "{\"action\":10002,\"msgid\":\"6\"}");
                yes("udp survives a deep message", receive(phone).contains("\"msgid\":\"6\""));
            } finally {
                s.stop();
            }
            eq("tcp down after stop", null, s.tcpPort());
        }
        connectionCap();
        notLan();
        threadGuard();
    }

    /** The heartbeat answer on the connection, null when the server closed it. */
    private static String heartbeat(Socket c, String msgid) {
        try {
            c.getOutputStream().write(Frame.encode("{\"action\":10002,\"msgid\":\"" + msgid + "\"}"));
            c.getOutputStream().flush();
            return Frame.read(c.getInputStream());
        } catch (IOException e) {
            return null;
        }
    }

    /** At most MAX_CONNECTIONS phones on TCP at once; a closed one frees its place. */
    private static void connectionCap() throws Exception {
        Endpoints e = new Endpoints(freeUdp(), freeUdp(), freeUdp(), freeTcp());
        reset(VcontrolModels.of("G0111"));
        VcontrolServer s = new VcontrolServer(x50, e, (what, t) -> { });
        s.start();
        Socket[] open = new Socket[VcontrolServer.MAX_CONNECTIONS];
        try {
            for (int k = 0; k < open.length; k++) {
                open[k] = new Socket(InetAddress.getLoopbackAddress(), e.tcp);
                open[k].setSoTimeout(5000);
                yes("connection " + (k + 1) + " served", heartbeat(open[k], "c" + k) != null);
            }
            try (Socket more = new Socket(InetAddress.getLoopbackAddress(), e.tcp)) {
                more.setSoTimeout(5000);
                eq("one over the cap is closed", null, heartbeat(more, "x"));
            }
            open[0].close();
            String again = null;
            for (int k = 0; k < 50 && again == null; k++) {
                Thread.sleep(20);
                try (Socket next = new Socket(InetAddress.getLoopbackAddress(), e.tcp)) {
                    next.setSoTimeout(5000);
                    again = heartbeat(next, "n");
                }
            }
            yes("a closed connection frees its place", again != null && again.contains("\"msgid\":\"n\""));
        } finally {
            for (Socket c : open) if (c != null) c.close();
            s.stop();
        }
    }

    /** A sender outside the LAN gets nothing and runs nothing, on every input. */
    private static void notLan() throws Exception {
        VcontrolServer stock = new VcontrolServer(x50, Endpoints.STOCK, (what, t) -> { });
        yes("the projector's server answers the LAN", stock.lan.test(InetAddress.getByName("<lan-ip>")));
        yes("the projector's server ignores the internet", !stock.lan.test(InetAddress.getByName("8.8.8.8")));
        try (DatagramSocket phone = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            Endpoints e = new Endpoints(freeUdp(), freeUdp(), phone.getLocalPort(), freeTcp());
            reset(VcontrolModels.of("G0111"));
            VcontrolServer s = new VcontrolServer(x50, e, (what, t) -> { }, a -> false);
            s.start();
            try {
                phone.setSoTimeout(400);
                send(phone, e.discovery, "{\"action\":9998,\"msgid\":\"1\"}");
                send(phone, e.commands, "{\"action\":20000,\"controlCmd\":{\"mode\":0,\"type\":5}}");
                send(phone, e.commands, "{\"action\":10002,\"msgid\":\"2\"}");
                String got;
                try {
                    got = receive(phone);
                } catch (java.net.SocketTimeoutException timeout) {
                    got = null;
                }
                eq("udp outside the LAN: no answer", null, got);
                try (Socket c = new Socket(InetAddress.getLoopbackAddress(), e.tcp)) {
                    c.setSoTimeout(5000);
                    eq("tcp outside the LAN: closed", null, heartbeat(c, "3"));
                }
                eq("outside the LAN ran nothing", "", sink.all());
            } finally {
                s.stop();
            }
        }
    }

    /** A throwable no handler catches ends only that input and is logged: it never reaches the process. */
    private static void threadGuard() throws Exception {
        StringBuffer logged = new StringBuffer();
        ControlSink broken = new ControlSink() {
            @Override public boolean run(org.lumen.control.Command c) {
                throw new AssertionError("sink broke");
            }

            @Override public int[] volume() {
                return new int[] {0, 0};
            }
        };
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        StringBuffer uncaught = new StringBuffer();
        Thread.setDefaultUncaughtExceptionHandler((t, x) -> uncaught.append(t.getName()));
        try (DatagramSocket phone = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            Endpoints e = new Endpoints(freeUdp(), freeUdp(), phone.getLocalPort(), freeTcp());
            VcontrolServer s = new VcontrolServer(new Vcontrol(VcontrolModels.of("G0111"), new FakeFacts(), broken), e,
                    (what, t) -> logged.append(what).append(t == null ? "" : ": " + t.getMessage()).append(';'));
            s.start();
            try {
                send(phone, e.commands, "{\"action\":20000,\"controlCmd\":{\"mode\":0,\"type\":5}}");
                for (int k = 0; k < 100 && logged.indexOf("stopped") < 0; k++) Thread.sleep(20);
                yes("a thrown error is logged with its input", logged.toString().contains("vcontrol-udp stopped: sink broke"));
                eq("nothing reaches the process", "", uncaught.toString());
                phone.setSoTimeout(5000);
                send(phone, e.discovery, "{\"action\":9998,\"msgid\":\"7\"}");
                yes("the other inputs stay up", receive(phone).startsWith("{\"action\":9999,"));
            } finally {
                s.stop();
                Thread.setDefaultUncaughtExceptionHandler(before);
            }
        }
        logged.setLength(0);
        ControlSink failing = new ControlSink() {
            @Override public boolean run(org.lumen.control.Command c) {
                throw new IllegalStateException("sink failed");
            }

            @Override public int[] volume() {
                return new int[] {0, 0};
            }
        };
        try (DatagramSocket phone = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            Endpoints e = new Endpoints(freeUdp(), freeUdp(), phone.getLocalPort(), freeTcp());
            VcontrolServer s = new VcontrolServer(new Vcontrol(VcontrolModels.of("G0111"), new FakeFacts(), failing), e,
                    (what, t) -> logged.append(what).append(';'));
            s.start();
            try {
                phone.setSoTimeout(5000);
                send(phone, e.commands, "{\"action\":50012,\"msgid\":\"8\"}");
                send(phone, e.commands, "{\"action\":10002,\"msgid\":\"9\"}");
                yes("a failing command leaves its input up", receive(phone).contains("\"msgid\":\"9\""));
                eq("a failing command is logged as the message", "message;", logged.toString());
            } finally {
                s.stop();
            }
        }
    }

    private static int freeUdp() throws IOException {
        try (DatagramSocket s = new DatagramSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static int freeTcp() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void send(DatagramSocket from, int port, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        from.send(new DatagramPacket(b, b.length, InetAddress.getLoopbackAddress(), port));
    }

    private static String receive(DatagramSocket s) throws IOException {
        DatagramPacket p = new DatagramPacket(new byte[8192], 8192);
        s.receive(p);
        return new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xFF));
        return s.toString();
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }
}
