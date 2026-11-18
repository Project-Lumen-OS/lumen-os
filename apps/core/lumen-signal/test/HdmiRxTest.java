package org.lumen.signal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;

/**
 * Host tests of the HDMI receiver report: the parser against the real Z9X and Z9X HB captures and synthetic dumps, the
 * refresh and stale rules (FormatWatch), the pinned card (PanelToggle), the rows of the sheet, the port map.
 * java -Dfixture=test/fixtures/z9x_hdmirx_ps5_4k60.txt [-Dbridge.out=FILE]
 *   [-Dhb.fixture=test/fixtures/hb_hdmirx_yuv420_4k60.txt [-Dhb.bridge.out=FILE]] org.lumen.signal.HdmiRxTest
 */
public final class HdmiRxTest {
    private static int fails, oks;

    static void ok(String what, boolean cond) {
        if (cond) oks++;
        else { fails++; System.out.println("FAIL " + what); }
    }

    static void eq(String what, Object want, Object got) {
        ok(what + " (want " + want + ", got " + got + ")", want == null ? got == null : want.equals(got));
    }

    public static void main(String[] a) throws Exception {
        String capture = new String(Files.readAllBytes(Paths.get(System.getProperty("fixture"))), StandardCharsets.UTF_8);
        capture(capture);
        bridgeOutput(capture, "bridge.out");
        String hb = System.getProperty("hb.fixture");
        ok("the HB capture is given", hb != null);
        if (hb != null) {
            String hbCapture = new String(Files.readAllBytes(Paths.get(hb)), StandardCharsets.UTF_8);
            hbCapture(hbCapture);
            bridgeOutput(hbCapture, "hb.bridge.out");
            hbSheet(hbCapture);
        }
        depthRule();
        synthetic();
        watch();
        timeline();
        pin();
        sheet(capture);
        ports();
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "HdmiRxTest: " + oks + " ok, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }

    /** The live capture, Z9X 2026-11-18: PS5 3840x2160 60 Hz, RGB, 12 bit, full range (TMDS 889 MHz = 1.5 x 592). */
    static void capture(String text) {
        HdmiRxInfo i = HdmiRxInfo.parse(text);
        ok("capture parses", i != null);
        if (i == null) return;
        eq("active port: MStar port 2", 2, i.port);
        eq("size from the IP block", "3840x2160", i.width + "x" + i.height);
        eq("colour format RGB", HdmiRxInfo.RGB, i.format);
        eq("12 bit", 12, i.depth);
        eq("AVI PB4: VIC 97 = 3840x2160p60", 97, i.vic);
        eq("AVI PB2 M: 16:9", "16:9", i.aspect);
        eq("C = no data: BT.709 by default for RGB", HdmiRxInfo.BT709, i.colorimetry);
        ok("marked as the default", i.colorimetryDefault);
        eq("AVI PB3 Q = 2: full range", HdmiRxInfo.FULL, i.quant);
        ok("usable", i.usable());
        ok("same size as the HAL's 3840x2160", i.sameSize(3840, 2160) && !i.sameSize(1920, 1080));
        // the AVI numbering: read as PB0.. the bytes would contradict the receiver's own lines
        int pb1AsByte1 = 0x28;
        ok("Byte 1 read as PB1 would say YUV 4:2:2 (wrong): Byte 0 is PB1", ((pb1AsByte1 >> 5) & 3) == 1);
        // the EDID of each port names its HDMI input: physical address in the HDMI VSDB (03 0C 00 AB CD)
        eq("MStar port 2 EDID = HDMI 1 (1.0.0.0)", "1.0.0.0", physical(text, "port2"));
        eq("MStar port 0 EDID = HDMI 2 (2.0.0.0)", "2.0.0.0", physical(text, "port0"));
        ok("the eARC output is MStar port 2 (HDMI 1 is the eARC port)", text.matches("(?s).*u8EARCOutputPort\\s+2->.*"));
        ok("no change flag in the dump: eARC flags only, no AVI checksum PB0",
                text.matches("(?s).*bCapChangeFlag\\s+0.*") && !text.contains("AVI packet Byte 13"));
        ok("the GC packet names 12 bit, the receiver's own field does not", text.contains("Color depth 12bit")
                && text.matches("(?s).*u8Colordepth\\s+Unknow.*"));
    }

    /**
     * The live capture, Z9X HB G0130 2026-11-18 (Lumen beta18 trial, plain root cat of the node): 3840x2160 60 Hz
     * YUV 4:2:0, 10 bit, BT.2020, limited, HDR packet present, on MStar port 1 = HDMI 2.
     */
    static void hbCapture(String text) {
        HdmiRxInfo i = HdmiRxInfo.parse(text);
        ok("HB capture parses", i != null);
        if (i == null) return;
        eq("HB: active port MStar 1 (Active HDMI Port 1, IP1 ucHDMIInfoSource 1)", 1, i.port);
        ok("HB: the IP block's size is not the picture", text.matches("(?s).*IP1 Info.*\\] HDE: 1919\\s.*\\] VDE: 9\\s.*"));
        eq("HB: size from the port section (4:2:0 halves the width)", "1920x2160", i.width + "x" + i.height);
        ok("HB: same size as the HAL's 3840x2160", i.sameSize(3840, 2160) && !i.sameSize(1920, 1080));
        eq("HB: YUV 4:2:0", HdmiRxInfo.YUV420, i.format);
        eq("HB: 10 bit from u8Colordepth (the GC field says not indicated)", 10, i.depth);
        eq("HB: AVI PB4 VIC 97 = 3840x2160p60", 97, i.vic);
        eq("HB: AVI PB2 M 16:9", "16:9", i.aspect);
        eq("HB: AVI C = 3, EC = 6: BT.2020", HdmiRxInfo.BT2020, i.colorimetry);
        ok("HB: BT.2020 is sent, not a default", !i.colorimetryDefault);
        eq("HB: AVI PB5 YQ = 0: limited", HdmiRxInfo.LIMITED, i.quant);
        // the depth rule against the link itself: HDMI 2.0 40x mode sends the TMDS clock at a quarter of the character
        // rate; the pixel clock of the 4:2:0 timing is HTT x VTT x 60 of the port section
        int tmds = num(text, "Mstar HDMI port1 Info", "TMDS clock rate:(\\d+)M");
        int htt = num(text, "Mstar HDMI port1 Info", "HTT: (\\d+)  VTT");
        int vtt = num(text, "Mstar HDMI port1 Info", "VTT: (\\d+)\\s");
        double ratio = tmds * 4.0 / (htt * (double) vtt * 60 / 1e6);
        ok("HB: the link carries 10 bit (TMDS " + tmds + " MHz x 4 / pixel " + htt + "x" + vtt + "x60 = " + ratio + ")",
                Math.abs(ratio - 1.25) < 0.02);
        ok("HB: 40x mode", text.contains("HDMI 2.0 timing 40x mode"));
        ok("HB: GC depth not indicated, receiver says 10 bit", text.contains("Color depth not indicated or default as 8bit")
                && text.matches("(?s).*u8Colordepth\\s+10-bit.*"));
        eq("HB: MStar port 0 EDID = HDMI 1 (1.0.0.0)", "1.0.0.0", physical(text, "port0"));
        eq("HB: MStar port 1 EDID = HDMI 2 (2.0.0.0)", "2.0.0.0", physical(text, "port1"));
        ok("HB: the eARC output is MStar port 0 (HDMI 1 is the eARC port)", text.matches("(?s).*u8EARCOutputPort\\s+0->.*"));
        int[] map = SignalItems.ports("hdmi_format\tMDB HDMIRX bridge ports=1:0,2:1\tnote\n");
        eq("HB map: HDMI 2 = MStar 1", 1, SignalItems.port(map, 2));
        eq("HB map: HDMI 1 = MStar 0", 0, SignalItems.port(map, 1));
        ok("HB: the read costs the receiver lock ~0.4 s (why the CHANGELOG warns)",
                text.contains("hold _msAPI_XC_Mutex for 359ms") && text.contains("hold _msAPI_XC_Mutex for 393ms"));
    }

    /** First number of {@code rx} after {@code from}. */
    static int num(String text, String from, String rx) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(rx).matcher(text.substring(text.indexOf(from)));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** The HB card: the owner's order with the HB receiver's values. */
    static void hbSheet(String capture) {
        SignalValues v = new SignalValues();
        v.port = 2; v.width = 3840; v.height = 2160; v.rateCenti = 6000; v.interlaced = false; v.dvi = false;
        v.hdrType = 1; v.range = "auto";
        v.rx = HdmiRxInfo.parse(capture);
        Set<SignalItem> items = EnumSet.of(SignalItem.HDMI_INFO, SignalItem.HDR, SignalItem.RANGE, SignalItem.HDMI_FORMAT);
        String rows = SignalSheet.build(v, items, SignalTest.WORDS).cardRows.toString();
        ok("HB card: 4:2:0, limited, BT.2020, 10 bit (" + rows + ")", rows.contains("r_color_format = YUV 4:2:0")
                && rows.contains("r_range = range_auto_limited") && rows.contains("r_colorimetry = BT.2020")
                && rows.contains("r_depth = depth_bits|10"));
    }

    /**
     * Which depth field wins: the GC packet's deep colour field when it names a depth, else the receiver's u8Colordepth,
     * else nothing (4:2:2 still reads 12 bit, the HDMI container).
     */
    static void depthRule() {
        String base = dump(1, "YUV444", null, 0x40, 0x08, 0x00, 0x10, 0x00);
        String gc12rx10 = base.replace("[ 1.1] Receive AVI", "[ 1.1] Color depth 12bit\n[ 1.1] Receive AVI")
                .replace("[ 1.2] =", "[ 1.1] u8Colordepth             10-bit\n[ 1.2] =");
        eq("GC names 12, receiver 10: the GC wins", 12, HdmiRxInfo.parse(gc12rx10).depth);
        String none10 = base.replace("[ 1.1] Receive AVI", "[ 1.1] Color depth not indicated or default as 8bit\n[ 1.1] Receive AVI")
                .replace("[ 1.2] =", "[ 1.1] u8Colordepth             10-bit\n[ 1.2] =");
        eq("GC not indicated, receiver 10: 10", 10, HdmiRxInfo.parse(none10).depth);
        String noneUnknown = none10.replace("10-bit", "Unknow");
        eq("GC not indicated, receiver unknown: no depth row (never a guessed 8)", 0, HdmiRxInfo.parse(noneUnknown).depth);
        eq("receiver only: its depth", 12, HdmiRxInfo.parse(base.replace("[ 1.2] =", "[ 1.1] u8Colordepth 12-bit\n[ 1.2] =")).depth);
        String ip0 = "[ 1.0] ----------------  Mstar HDMI IP0";
        String port1 = "[ 1.0] ----------------  Mstar HDMI port1 Info -----------------\n";
        HdmiRxInfo none = HdmiRxInfo.parse(base.replace(ip0, port1 + "[ 1.0] HDE: 1  VDE: 1\n" + ip0));
        eq("a 1 x 1 port size (no picture) leaves the IP block's", "3840x2160", none.width + "x" + none.height);
        HdmiRxInfo scaler = HdmiRxInfo.parse(base.replace(ip0, port1 + "[ 1.0] HDE: 5760  VDE: 2160\n"
                + "[ 1.0] HDE: 1920  VDE: 1080\n" + ip0));
        eq("the active port's last size line wins over the IP block", "1920x1080", scaler.width + "x" + scaler.height);
        HdmiRxInfo other = HdmiRxInfo.parse(base.replace(ip0, "[ 1.0] ----------------  Mstar HDMI port2 Info -----------------\n"
                + "[ 1.0] HDE: 1920  VDE: 1080\n" + ip0));
        eq("another port's size line is not taken", "3840x2160", other.width + "x" + other.height);
    }

    /** The bridge's filtered file (test/hdmirx_bridge_test.sh wrote it from the same capture) decodes the same. */
    static void bridgeOutput(String capture, String prop) throws Exception {
        String p = System.getProperty(prop);
        if (p == null) return;
        String f = new String(Files.readAllBytes(Paths.get(p)), StandardCharsets.UTF_8);
        ok("bridge file starts with its token", f.startsWith("token "));
        ok("bridge file: no EDID rows, no HDCP, no other driver", !f.matches("(?s).*\\n[0-9A-F]{2}: [0-9A-F]{2} .*")
                && !f.contains("HDCP") && !f.contains("PQ_DBG") && !f.contains("avc:"));
        eq("bridge file decodes like the capture", String.valueOf(HdmiRxInfo.parse(capture)), String.valueOf(HdmiRxInfo.parse(f)));
        ok("bridge file is small", f.length() < 6000);
    }

    /** The EDID rows of a port section joined (the VSDB may wrap to the next row, as on the HB). */
    static String physical(String text, String port) {
        int at = text.indexOf("Mstar HDMI " + port + " Info");
        StringBuilder edid = new StringBuilder();
        java.util.regex.Matcher row = java.util.regex.Pattern.compile("\\] [0-9A-F]{2}: ((?:[0-9A-F]{2} ?){16})")
                .matcher(text.substring(at, text.indexOf("Info", at + 30)));
        while (row.find()) edid.append(row.group(1).trim()).append(' ');
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("03 0C 00 ([0-9A-F])([0-9A-F]) ([0-9A-F])([0-9A-F])")
                .matcher(edid);
        return m.find() ? m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4) : null;
    }

    /** A receiver dump in the capture's layout. */
    static String dump(int active, String space, String depth, int... avi) {
        StringBuilder b = new StringBuilder();
        b.append("[ 1.0] ----------------  Mstar HDMI port0 Info -----------------\n");
        b.append("[ 1.0] Active HDMI Port ").append(active).append("->(0 1 2 3)\n");
        b.append("[ 1.0] ----------------  Mstar HDMI IP0 Info -----------------\n[ 1.0] Signal invalid\n");
        b.append("[ 1.1] [PQ] PQ_DBG_ERROR noise of another driver\n");
        b.append("[ 1.1] ----------------  Mstar HDMI IP").append(active).append(" Info -----------------\n");
        b.append("[ 1.1] HDE: 3840\n[ 1.1] VDE: 2160\n");
        if (space != null) b.append("[ 1.1] Color space ").append(space).append("\n");
        if (depth != null) b.append("[ 1.1] Color depth ").append(depth).append("\n");
        b.append("[ 1.1] Receive AVI packet\n");
        for (int k = 0; k < avi.length; k++) {
            b.append("[ 1.1] AVI packet Byte ").append(k).append(": 0x").append(Integer.toHexString(avi[k])).append(" \n");
        }
        b.append("[ 1.1] ---------------- HDMI Utopia PollingInfo HDMI IP ").append(active).append(" status ----------------\n");
        b.append("[ 1.1] ucHDMIInfoSource         ").append(active).append("->(0 1 2 3)\n");
        b.append("[ 1.2] ============= eARC Start===============\n");
        return b.toString();
    }

    static void synthetic() {
        // HDR10 from a console: YUV 4:2:2 10 bit, BT.2020 (C=3, EC=6), limited (YQ 0), VIC 97
        HdmiRxInfo h = HdmiRxInfo.parse(dump(2, "YUV422", "10bit", 0x20, 0xE8, 0x60, 0x61, 0x00));
        eq("HDR10 422: format", HdmiRxInfo.YUV422, h.format);
        eq("HDR10 422: the receiver's 10 bit kept", 10, h.depth);
        eq("HDR10 422: BT.2020", HdmiRxInfo.BT2020, h.colorimetry);
        ok("HDR10 422: not a default", !h.colorimetryDefault);
        eq("HDR10 422: YQ 0 limited", HdmiRxInfo.LIMITED, h.quant);
        eq("4:2:2 with 8 bit reported: HDMI carries 12", 12,
                HdmiRxInfo.parse(dump(2, "YUV422", "8bit", 0x20, 0xA8, 0x00, 0x10, 0x00)).depth);
        HdmiRxInfo y = HdmiRxInfo.parse(dump(1, null, null, 0x40, 0xA8, 0x00, 0x10, 0x40));
        eq("no Color space line: AVI Y = 2 is 4:4:4", HdmiRxInfo.YUV444, y.format);
        eq("C = 2: BT.709", HdmiRxInfo.BT709, y.colorimetry);
        eq("YQ = 1: full", HdmiRxInfo.FULL, y.quant);
        eq("no depth line and no GC: unknown", 0, y.depth);
        eq("YUV420 line", HdmiRxInfo.YUV420, HdmiRxInfo.parse(dump(2, "YUV420", "8bit", 0x60, 0xA8, 0, 0x61, 0)).format);
        HdmiRxInfo h420 = new HdmiRxInfo();
        h420.format = HdmiRxInfo.YUV420; h420.width = 1920; h420.height = 2160;
        ok("4:2:0 at half width still matches 3840x2160", h420.sameSize(3840, 2160));
        eq("RGB Q 0, VIC 0 (a PC mode): full", HdmiRxInfo.FULL, HdmiRxInfo.parse(dump(2, "RGB444", "8bit", 0, 0, 0, 0, 0)).quant);
        eq("RGB Q 0, VIC 16 (a video mode): limited", HdmiRxInfo.LIMITED,
                HdmiRxInfo.parse(dump(2, "RGB444", "8bit", 0, 0x28, 0, 16, 0)).quant);
        eq("RGB Q 1: limited", HdmiRxInfo.LIMITED, HdmiRxInfo.parse(dump(2, "RGB444", "8bit", 0, 0x28, 0x04, 97, 0)).quant);
        String vsif = dump(2, "RGB444", "8bit", 0, 0, 0, 0, 0).replace("[ 1.2] =====",
                "[ 1.1] VS packet Byte 0: 0x81 \n[ 1.1] VS packet Byte 4: 0x0 \n[ 1.1] VS packet Byte 5: 0x3 \n"
                        + "[ 1.1] VS packet Byte 6: 0xc \n[ 1.1] VS packet Byte 7: 0x0 \n[ 1.1] VS packet Byte 8: 0x20 \n[ 1.2] =====");
        eq("RGB Q 0, VIC 0 with an HDMI 1.4 HDMI_VIC (4K30): limited", HdmiRxInfo.LIMITED, HdmiRxInfo.parse(vsif).quant);
        eq("EC 7 without PB14: unknown, no guess", null, HdmiRxInfo.parse(dump(2, "RGB444", "8bit", 0, 0xE8, 0x70, 97, 0)).colorimetry);
        eq("EC 7 with ACE 0 in PB14: DCI-P3", HdmiRxInfo.P3,
                HdmiRxInfo.parse(dump(2, "RGB444", "8bit", 0, 0xE8, 0x70, 97, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00)).colorimetry);
        eq("C 0 YCbCr at 576 lines: BT.601", HdmiRxInfo.BT601,
                HdmiRxInfo.parse(dump(2, "YUV444", "8bit", 0x40, 0x18, 0, 17, 0).replace("VDE: 2160", "VDE: 576")).colorimetry);
        String two = dump(2, "RGB444", "8bit", 0, 0x28, 0x08, 97, 0).replace("Active HDMI Port 2->(0 1 2 3)\n", "")
                + "[ 1.3] ----------------  Mstar HDMI IP0 Info -----------------\n[ 1.3] HDE: 1920\n[ 1.3] VDE: 1080\n";
        eq("two inputs with a signal and no active port: nothing, never a guess", null, HdmiRxInfo.parse(two));
        eq("no dump", null, HdmiRxInfo.parse("[ 1.0] nothing here\n"));
        eq("null", null, HdmiRxInfo.parse(null));
        String again = dump(1, "YUV422", "12bit", 0x20, 0xA8, 0, 97, 0) + dump(2, "RGB444", "8bit", 0, 0x28, 0x08, 97, 0);
        eq("two dumps in the log: the last one counts", 2, HdmiRxInfo.parse(again).port);
    }

    static HdmiRxInfo info(int port, String format, int depth) {
        HdmiRxInfo i = new HdmiRxInfo();
        i.port = port; i.width = 3840; i.height = 2160; i.format = format; i.depth = depth;
        i.colorimetry = HdmiRxInfo.BT709; i.quant = HdmiRxInfo.FULL;
        return i;
    }

    static void watch() {
        FormatWatch w = new FormatWatch();
        String k = "2:3840x2160p@6000/0";
        eq("no signal yet: no request", 0L, w.due(true, 0));
        w.signal(k, 0);
        long id = w.due(true, 0);
        ok("panel opened: asks at once", id != 0);
        eq("one request at a time", 0L, w.due(true, 100));
        ok("answer shows", w.answer(id, info(2, HdmiRxInfo.RGB, 12), 2, 3840, 2160));
        eq("shown RGB", HdmiRxInfo.RGB, w.shown().format);
        // the console switches RGB -> YUV 4:2:2 at the same timing: no event, no new key
        eq("Normal: not again before 5 s", 0L, w.due(true, 4_999));
        id = w.due(true, 5_000);
        ok("Normal: again at 5 s", id != 0);
        w.answer(id, info(2, HdmiRxInfo.YUV422, 12), 2, 3840, 2160);
        eq("format change without a timing change picked up within 5 s", HdmiRxInfo.YUV422, w.shown().format);
        // hidden: never
        FormatWatch hid = new FormatWatch();
        hid.signal(k, 0);
        boolean any = false;
        for (long t = 0; t <= 60_000; t += 100) any |= hid.due(false, t) != 0;
        ok("hidden: no request in a minute", !any);
        // Fast: every second, only while visible
        FormatWatch f = new FormatWatch();
        f.refresh(SignalRefresh.FAST);
        f.signal(k, 0);
        f.answer(f.due(true, 0), info(2, HdmiRxInfo.RGB, 8), 2, 3840, 2160);
        eq("Fast: not before 1 s", 0L, f.due(true, 999));
        ok("Fast: at 1 s", f.due(true, 1_000) != 0);
        // stale: a change hides at once, an answer for the old signal is dropped
        FormatWatch s = new FormatWatch();
        s.signal(k, 0);
        s.answer(s.due(true, 0), info(2, HdmiRxInfo.RGB, 12), 2, 3840, 2160);
        long late = s.due(true, 5_000);
        s.signal("2:3840x2160p@6000/1", 5_100);                  // HDR on
        eq("a signal change hides the old value at once", null, s.shown());
        s.answer(late, info(2, HdmiRxInfo.RGB, 12), 2, 3840, 2160);
        eq("an answer asked for the old signal is not shown", null, s.shown());
        eq("debounce: not before 1 s of a stable new signal", 0L, s.due(true, 6_099));
        long fresh = s.due(true, 6_100);
        ok("debounce: 1 s after the change", fresh != 0);
        s.answer(fresh, null, 2, 3840, 2160);
        eq("a failed read shows nothing", null, s.shown());
        long p = s.due(true, 11_100);
        s.answer(p, info(0, HdmiRxInfo.RGB, 12), 2, 3840, 2160);
        eq("an answer for another port shows nothing", null, s.shown());
        p = s.due(true, 16_100);
        s.answer(p, info(2, HdmiRxInfo.RGB, 12), -1, 3840, 2160);
        eq("an HDMI input missing in the port map shows nothing", null, s.shown());
        p = s.due(true, 21_100);
        s.answer(p, info(2, HdmiRxInfo.RGB, 12), 2, 1920, 1080);
        eq("an answer of another size shows nothing", null, s.shown());
        p = s.due(true, 26_100);
        s.reset();
        ok("closed meanwhile: the late answer is ignored", !s.answer(p, info(2, HdmiRxInfo.RGB, 12), 2, 3840, 2160)
                && s.shown() == null);
        ok("unknown request id ignored", !s.answer(12345, info(2, HdmiRxInfo.RGB, 12), 2, 3840, 2160));
        s.signal(null, 30_000);
        eq("no input: nothing asked", 0L, s.due(true, 40_000));
        eq("Refresh tokens", SignalRefresh.FAST, SignalRefresh.of("fast"));
        eq("Refresh default Normal", SignalRefresh.NORMAL, SignalRefresh.of(null));
        SignalValues v = new SignalValues();
        v.port = 1; v.width = 3840; v.height = 2160; v.rateCenti = 5994; v.hdrType = 1;
        eq("key", "1:3840x2160p@5994/1", FormatWatch.key(v));
        v.port = 0;
        eq("key: apps on screen", null, FormatWatch.key(v));
    }

    /**
     * The panel's 500 ms tick as a timeline: the pinned card over HDMI, the console flips its colour format at the same
     * timing, the user switches the input, the panel is hidden. Reads happen only while visible, at the chosen pace.
     */
    static void timeline() {
        for (SignalRefresh r : SignalRefresh.values()) {
            PanelToggle tg = new PanelToggle();
            FormatWatch w = new FormatWatch();
            w.refresh(r);
            tg.pin(true, true);
            String[] receiver = {HdmiRxInfo.RGB};
            int port = 1, reads = 0, hiddenReads = 0;
            long lastRead = -1, flipSeen = -1, maxGap = 0;
            for (long t = 0; t <= 40_000; t += 500) {
                if (t == 7_000) receiver[0] = HdmiRxInfo.YUV422;          // format change, same timing
                if (t == 15_000) port = 0;                                  // input switch passes through none
                if (t == 16_500) port = 2;
                if (t == 30_000) tg.press();                               // card -> full panel
                if (t == 30_500) tg.press();                               // -> hidden by the user
                PanelMode m = tg.pinTick(port > 0, t);
                if (!m.visible()) w.reset();
                w.signal(port > 0 ? port + ":3840x2160p@6000/0" : null, t);
                long id = w.due(m.visible(), t);
                if (id != 0) {
                    reads++;
                    if (!m.visible()) hiddenReads++;
                    if (lastRead >= 0 && t - lastRead > maxGap && t < 15_000) maxGap = t - lastRead;
                    lastRead = t;
                    w.answer(id, info(port == 1 ? 2 : 0, receiver[0], 12), port == 1 ? 2 : port == 2 ? 0 : -1, 3840, 2160);
                }
                if (flipSeen < 0 && w.shown() != null && HdmiRxInfo.YUV422.equals(w.shown().format)) flipSeen = t;
                if (t == 16_000) ok(r + ": pinned card survives the input switch", tg.mode() == PanelMode.COMPACT);
                if (t == 16_000) eq(r + ": no value while no input", null, w.shown());
                if (t == 29_500) ok(r + ": card still there after the switch", tg.mode() == PanelMode.COMPACT);
                if (t == 29_500) ok(r + ": HDMI 2 value shown", w.shown() != null);
                if (t == 35_000) eq(r + ": user closed it, it stays closed", PanelMode.HIDDEN, tg.mode());
            }
            ok(r + ": no read while hidden", hiddenReads == 0);
            ok(r + ": format flip shown within one period (" + (flipSeen - 7_000) + " ms)",
                    flipSeen >= 7_000 && flipSeen - 7_000 <= r.periodMs);
            ok(r + ": pace " + maxGap + " ms", maxGap == r.periodMs);
            ok(r + ": reads stop after the user closed it", lastRead <= 30_500);
            ok(r + ": some reads", reads > 3);
        }
    }

    static void pin() {
        PanelToggle t = new PanelToggle();
        eq("pin on over HDMI shows the card", PanelMode.COMPACT, t.pin(true, true));
        eq("a new signal keeps it", PanelMode.COMPACT, t.signalChanged(true, true));
        eq("pin watch keeps it over HDMI", PanelMode.COMPACT, t.pinTick(true, 0));
        eq("input switch: 1.5 s without HDMI keeps it", PanelMode.COMPACT, t.pinTick(false, 1_000));
        eq("  ...", PanelMode.COMPACT, t.pinTick(false, 2_500));
        eq("  the new input", PanelMode.COMPACT, t.pinTick(true, 3_000));
        t.pinTick(false, 10_000);
        eq("back to the apps for 3 s: the pinned card goes", PanelMode.HIDDEN, t.pinTick(false, 13_000));
        eq("HDMI again: back", PanelMode.COMPACT, t.pinTick(true, 14_000));
        eq("key: full panel", PanelMode.DETAIL, t.press());
        eq("key: hidden", PanelMode.HIDDEN, t.press());
        eq("closed by the user: stays closed over HDMI", PanelMode.HIDDEN, t.pinTick(true, 15_000));
        t.pinTick(false, 16_000);
        t.pinTick(false, 19_000);
        eq("next HDMI input after the apps: shown again", PanelMode.COMPACT, t.pinTick(true, 20_000));
        t.press();
        eq("BACK on the full panel closes it for good too", PanelMode.HIDDEN, t.back());
        eq("  stays closed", PanelMode.HIDDEN, t.pinTick(true, 21_000));
        eq("option on again shows it", PanelMode.COMPACT, t.pin(true, true));
        eq("standby hides", PanelMode.HIDDEN, t.hide());
        eq("after the wake over HDMI it is back", PanelMode.COMPACT, t.pinTick(true, 30_000));
        eq("option off takes it away", PanelMode.HIDDEN, t.pin(false, true));
        eq("off: the watch does nothing", PanelMode.HIDDEN, t.pinTick(true, 31_000));
        PanelToggle apps = new PanelToggle();
        eq("pin on over the apps: nothing yet", PanelMode.HIDDEN, apps.pin(true, false));
        eq("  then HDMI: the card", PanelMode.COMPACT, apps.pinTick(true, 1_000));
        PanelToggle own = new PanelToggle();
        own.pin(true, false);
        own.press();                                                         // opened by hand over the apps
        own.pinTick(false, 0);
        eq("a card opened by hand over the apps is not taken by the pin", PanelMode.COMPACT, own.pinTick(false, 5_000));
        PanelToggle pop = new PanelToggle();
        pop.signalChanged(true, true);
        eq("unpinned popup still hides after 5 s", PanelMode.HIDDEN, pop.popupOver());
    }

    static void sheet(String capture) {
        SignalValues v = new SignalValues();
        v.port = 1; v.width = 3840; v.height = 2160; v.rateCenti = 6000; v.interlaced = false; v.dvi = false;
        v.hdrType = 0; v.range = "auto";
        v.rx = HdmiRxInfo.parse(capture);
        Set<SignalItem> items = EnumSet.of(SignalItem.HDMI_INFO, SignalItem.HDR, SignalItem.RANGE, SignalItem.HDMI_FORMAT);
        SignalSheet s = SignalSheet.build(v, items, SignalTest.WORDS);
        eq("card: the owner's order with the receiver's values", "[r_aspect = 16:9, r_color_format = RGB 4:4:4, "
                + "r_range = range_auto_full, r_colorimetry = BT.709, r_depth = depth_bits|12, r_hdr = hdr_sdr]",
                s.cardRows.toString());
        String full = s.valueRows().toString();
        ok("full panel: colorimetry says it is the default", full.contains("r_colorimetry = BT.709 (color_default)"));
        Set<SignalItem> none = EnumSet.of(SignalItem.HDMI_INFO, SignalItem.HDR, SignalItem.RANGE);
        eq("model without the bridge: plain rows only", "[r_aspect = 16:9, r_range = range_auto, r_hdr = hdr_sdr]",
                SignalSheet.build(v, none, SignalTest.WORDS).cardRows.toString());
        v.range = "full";
        ok("range set by hand: the setting", SignalSheet.build(v, items, SignalTest.WORDS).cardRows.toString()
                .contains("r_range = range_full"));
        v.range = "auto";
        v.width = 1920; v.height = 1080;
        eq("receiver size differs from the HAL's: its rows hidden", "[r_aspect = 16:9, r_range = range_auto, r_hdr = hdr_sdr]",
                SignalSheet.build(v, items, SignalTest.WORDS).cardRows.toString());
        v.width = 3840; v.height = 2160; v.rx = null;
        eq("no answer: rows hidden", "[r_aspect = 16:9, r_range = range_auto, r_hdr = hdr_sdr]",
                SignalSheet.build(v, items, SignalTest.WORDS).cardRows.toString());
        v.width = 720; v.height = 576; v.rateCenti = 5000;
        v.rx = HdmiRxInfo.parse(dump(2, "YUV444", "8bit", 0x40, 0x18, 0, 17, 0).replace("HDE: 3840", "HDE: 720")
                .replace("VDE: 2160", "VDE: 576"));
        ok("SD: the AVI picture aspect fills the aspect row", SignalSheet.build(v, items, SignalTest.WORDS).cardRows.toString()
                .startsWith("[r_aspect = 4:3, r_color_format = YUV 4:4:4"));
    }

    static void ports() {
        String tsv = "item\tcall\tnote\nhdmi_info\tIGmpf 662\tx\nhdmi_format\tMDB HDMIRX bridge ports=1:2,2:0\tnote\n";
        int[] p = SignalItems.ports(tsv);
        eq("HDMI 1 = MStar 2", 2, SignalItems.port(p, 1));
        eq("HDMI 2 = MStar 0", 0, SignalItems.port(p, 2));
        eq("HDMI 3 unknown", -1, SignalItems.port(p, 3));
        eq("apps", -1, SignalItems.port(p, 0));
        eq("no row: unknown", -1, SignalItems.port(SignalItems.ports("hdmi_info\tx\n"), 1));
        Set<SignalItem> m = SignalItems.parse(tsv);
        ok("the bridge item parses", m.contains(SignalItem.HDMI_FORMAT));
        ok("shown without the reader naming it", SignalItems.shown(m, EnumSet.of(SignalItem.HDMI_INFO))
                .contains(SignalItem.HDMI_FORMAT));
        ok("not shown where the model lacks it", !SignalItems.shown(EnumSet.of(SignalItem.HDMI_INFO),
                EnumSet.allOf(SignalItem.class)).contains(SignalItem.HDMI_FORMAT));
        ok("never counted by the HAL budget", new SignalBudget(EnumSet.of(SignalItem.HDMI_FORMAT))
                .due(0, PanelMode.DETAIL, true).isEmpty());
    }
}
