// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The input format the MStar HDMI receiver reports about its active input: colour format and chroma, bit depth,
 * colorimetry, the quantization the signal asks for and the picture aspect. Source: the receiver's debug node
 * /proc/utopia_mdb/HDMIRX, which prints into the kernel log; the root bridge image/lumen_hdmirx.sh keeps only the info
 * lines (no EDID, no HDCP) and HdmiRxBridge hands them here. Pure, host tested against two real captures
 * (test/fixtures/z9x_hdmirx_ps5_4k60.txt, Z9X 2026-11-18, PS5 3840x2160 60 Hz RGB 12 bit, and
 * test/fixtures/hb_hdmirx_yuv420_4k60.txt, Z9X HB G0130 2026-11-18, 3840x2160 60 Hz YUV 4:2:0 10 bit BT.2020).
 *
 * <p>Dump layout: per receiver one block "Mstar HDMI IP&lt;n&gt; Info", "Signal invalid" when it has no input; the active
 * one prints HDE / VDE, "Color space RGB444", "Color depth 12bit", "AVI packet Byte 0..12" and a part "HDMI Utopia
 * PollingInfo HDMI IP n" with "ucHDMIInfoSource 2-&gt;(0 1 2 3)" (its HDMI port, MStar numbering). The port sections
 * before it name the port on screen: "Active HDMI Port 2-&gt;(0 1 2 3)", and the section of that port prints the size
 * the scaler takes from it ("HDE: 3840  VDE: 2160", the last such line of the section).
 *
 * <p>Z9X HB (MT9660) prints the same layout with two differences. Its IP block size is not the picture (the capture
 * says HDE 1919, VDE 9, VTT 99 for a 3840x2160 4:2:0 signal whose port section says 1920x2160, HTT 2200, VTT 2250), so
 * the size comes from the port section of the active port, the IP block's only when that section has none (on the Z9X
 * capture both say 3840x2160). And its depth fields can disagree: "Color depth not indicated or default as 8bit" (the
 * General Control packet's CD field is 0, the CTA wording for "not indicated") next to "u8Colordepth 10-bit" (the
 * receiver's own polling state). Rule: the GC packet's deep colour field when it names a depth ("Color depth 12bit",
 * GC Byte 1 CD), else u8Colordepth. Proof in both captures: on the Z9X the GC says 12 bit and u8Colordepth "Unknow",
 * TMDS 889 MHz / 592 MHz pixel = 1.5 = 12 bit; on the HB the GC says nothing and u8Colordepth 10 bit, TMDS clock
 * 92 MHz in 40x mode = 368 MHz characters / 297 MHz pixel (HTT 2200 x VTT 2250 x 60) = 1.25 = 10 bit.
 *
 * <p>AVI "Byte k" is data byte PB(k+1) of CTA-861: Byte 0 = PB1 (Y, A, B, S), Byte 1 = PB2 (C, M, R), Byte 2 = PB3
 * (ITC, EC, Q, SC), Byte 3 = PB4 (VIC), Byte 4 = PB5 (YQ, CN, PR). Proof from the capture: 00 28 08 61 40 read as
 * PB1.. give RGB, 16:9, full range, VIC 97 (3840x2160p60), which is what the receiver prints itself; read as PB0.. they
 * would give YUV 4:2:2 and VIC 64 (1920x1080p100). PB14 (ACE, the DCI-P3 code) is not printed, so P3 shows only when a
 * dump carries "Byte 13". VS "Byte k" has the header in front (Byte 0 = type 0x81, Byte 4 = PB0 checksum, Byte 5..7 =
 * OUI): the capture's HDMI Forum VSIF checks out with that layout.
 */
public final class HdmiRxInfo {
    /** colour format tokens */
    public static final String RGB = "rgb", YUV444 = "yuv444", YUV422 = "yuv422", YUV420 = "yuv420";
    /** colorimetry tokens (CTA-861 C, EC and ACE) */
    public static final String BT601 = "bt601", BT709 = "bt709", BT2020 = "bt2020", BT2020_CL = "bt2020cl",
            XVYCC601 = "xvycc601", XVYCC709 = "xvycc709", SYCC601 = "sycc601", OPYCC601 = "opycc601", OPRGB = "oprgb",
            P3 = "p3";
    /** quantization tokens */
    public static final String FULL = "full", LIMITED = "limited";
    /** the MStar HDMI receiver prints 13 AVI data bytes (PB1..PB13), PB14 only if a newer driver adds it */
    static final int AVI_BYTES = 14, VS_BYTES = 32, GC_BYTES = 4;
    /** CTA-861 VIC 1 (640x480) is an IT format: RGB there defaults to full range like a PC signal */
    private static final int VIC_VGA = 1;
    /** HDMI 1.4 VSIF: OUI 0x000C03, HDMI_Video_Format 1 = an extended resolution (HDMI_VIC, a CE format) */
    private static final int OUI_HDMI14 = 0x000C03, HDMI_VIDEO_EXT = 1;

    /** the MStar port of the active input (0..3) */
    public int port = -1;
    /** the active area as the receiver measured it (HDE, VDE) */
    public int width, height;
    /** RGB / YUV444 / YUV422 / YUV420, null = unknown */
    public String format;
    /** bits per colour: 8, 10, 12, 16; 0 = unknown */
    public int depth;
    /** BT709, BT2020, ..., null = unknown; {@link #colorimetryDefault}: the signal sent "no data", this is its default */
    public String colorimetry;
    public boolean colorimetryDefault;
    /** what the signal asks for (CTA-861 Q / YQ and the format's default): FULL or LIMITED, null = unknown */
    public String quant;
    /** CTA-861 VIC of the AVI InfoFrame, -1 = none */
    public int vic = -1;
    /** the picture aspect the AVI InfoFrame names (M): "4:3" or "16:9", null = none */
    public String aspect;

    /** True when there is something to show. */
    public boolean usable() {
        return port >= 0 && (format != null || depth > 0 || colorimetry != null || quant != null);
    }

    /** True when this answer belongs to the signal {@code w} x {@code h} the vendor HAL reports (no stale or foreign data). */
    public boolean sameSize(int w, int h) {
        if (w <= 0 || h <= 0 || width <= 0 || height <= 0) return false;
        boolean wide = width == w || (YUV420.equals(format) && width * 2 == w);
        return wide && (height == h || height * 2 == h);
    }

    @Override public String toString() {
        return "port=" + port + " " + width + "x" + height + " " + format + " " + depth + "bit " + colorimetry
                + (colorimetryDefault ? "(default)" : "") + " " + quant + " vic=" + vic + " aspect=" + aspect;
    }

    // ------------------------------------------------------------------ parsing

    private static final Pattern STAMP = Pattern.compile("^\\s*\\[\\s*\\d+\\.\\d+\\]\\s?");
    private static final Pattern IP = Pattern.compile("Mstar HDMI IP(\\d) Info");
    private static final Pattern PORT = Pattern.compile("Mstar HDMI port(\\d) Info");
    private static final Pattern END = Pattern.compile("eARC Start|Mstar HDMI Rx help");
    private static final Pattern ACTIVE = Pattern.compile("^Active HDMI Port (\\d)");
    private static final Pattern SOURCE = Pattern.compile("^ucHDMIInfoSource\\s+(\\d)");
    private static final Pattern HDE = Pattern.compile("^HDE:\\s*(\\d+)\\s*$");
    private static final Pattern VDE = Pattern.compile("^VDE:\\s*(\\d+)\\s*$");
    private static final Pattern SPACE = Pattern.compile("^Color space (RGB|YUV|YCBCR)[ _]?(444|422|420)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CEC = Pattern.compile("^Color space u8C10Value:(\\d+),\\s*u8EC210Value:(\\d+)");
    private static final Pattern DEPTH = Pattern.compile("^Color depth (\\d+)\\s*bit", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKET = Pattern.compile("^(AVI|VS|GC) packet Byte (\\d+): 0x([0-9a-fA-F]{1,2})\\s*$");
    private static final Pattern YUV420_FLAG = Pattern.compile("^bYUV420Flag\\s+(\\d)");
    private static final Pattern RX_DEPTH = Pattern.compile("^u8Colordepth\\s+(\\d+)-bit");
    /** a size line of a port section, "HDE: 3840  VDE: 2160" */
    private static final Pattern PORT_SIZE = Pattern.compile("^HDE:\\s*(\\d+)\\s+VDE:\\s*(\\d+)\\s*$");
    /** a port section size of 1 x 1 is the receiver's "no picture" */
    private static final int NO_PICTURE = 1;

    /** One receiver block of the dump. */
    private static final class Block {
        final int ip;
        boolean invalid;
        int hde, vde, source = -1, c = -1, ec = -1, depth, rxDepth, yuv420 = -1;
        String space;
        final int[] avi = filled(AVI_BYTES), vs = filled(VS_BYTES), gc = filled(GC_BYTES);

        Block(int ip) { this.ip = ip; }

        void line(String s) {
            Matcher m;
            if (s.equals("Signal invalid")) invalid = true;
            else if (hde == 0 && (m = HDE.matcher(s)).find()) hde = Integer.parseInt(m.group(1));
            else if (vde == 0 && (m = VDE.matcher(s)).find()) vde = Integer.parseInt(m.group(1));
            else if ((m = SPACE.matcher(s)).find()) space = format(m.group(1), m.group(2));
            else if ((m = CEC.matcher(s)).find()) { c = Integer.parseInt(m.group(1)); ec = Integer.parseInt(m.group(2)); }
            else if ((m = DEPTH.matcher(s)).find()) depth = Integer.parseInt(m.group(1));
            else if ((m = SOURCE.matcher(s)).find()) source = Integer.parseInt(m.group(1));
            else if ((m = YUV420_FLAG.matcher(s)).find()) yuv420 = Integer.parseInt(m.group(1));
            else if ((m = RX_DEPTH.matcher(s)).find()) rxDepth = Integer.parseInt(m.group(1));
            else if ((m = PACKET.matcher(s)).find()) {
                int[] to = "AVI".equals(m.group(1)) ? avi : "VS".equals(m.group(1)) ? vs : gc;
                int k = Integer.parseInt(m.group(2));
                if (k < to.length) to[k] = Integer.parseInt(m.group(3), 16);
            }
        }

        boolean active() { return !invalid && hde > 0 && vde > 0; }
    }

    /**
     * The info of the input on screen from the kernel log text of one dump (a longer log is fine: the last dump
     * counts, other drivers' lines are skipped). Null when no receiver block has a signal or the active one cannot be
     * told apart.
     */
    public static HdmiRxInfo parse(String text) {
        if (text == null) return null;
        Map<Integer, Block> blocks = new HashMap<>();
        Map<Integer, int[]> portSizes = new HashMap<>();
        Block cur = null;
        int active = -1, section = -1;
        for (String raw : text.split("\n")) {
            String s = STAMP.matcher(raw).replaceFirst("").trim();
            Matcher m;
            if ((m = IP.matcher(s)).find()) {
                cur = new Block(Integer.parseInt(m.group(1)));
                blocks.put(cur.ip, cur);
                section = -1;
            } else if ((m = PORT.matcher(s)).find()) {
                section = Integer.parseInt(m.group(1));
                if (section == 0) { blocks.clear(); portSizes.clear(); active = -1; }   // a new dump starts
                cur = null;
            } else if (END.matcher(s).find()) {
                cur = null;
                section = -1;
            } else if (cur != null) {
                cur.line(s);
            } else if ((m = ACTIVE.matcher(s)).find()) {
                active = Integer.parseInt(m.group(1));
            } else if (section >= 0 && (m = PORT_SIZE.matcher(s)).find()) {
                portSizes.put(section, new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
            }
        }
        Block b = pick(blocks, active);
        return b == null ? null : decode(b, portSizes.get(b.source >= 0 ? b.source : b.ip));
    }

    /** The block of the port on screen: the one whose source is the active port, else the only one with a signal. */
    private static Block pick(Map<Integer, Block> blocks, int active) {
        Block only = null;
        int live = 0;
        for (Block b : blocks.values()) {
            if (!b.active()) continue;
            live++;
            only = b;
            if (active >= 0 && (b.source == active || (b.source < 0 && b.ip == active))) return b;
        }
        return active < 0 && live == 1 ? only : null;
    }

    /** {@code portSize}: the last size line of the active port's section, null = none. */
    private static HdmiRxInfo decode(Block b, int[] portSize) {
        HdmiRxInfo i = new HdmiRxInfo();
        i.port = b.source >= 0 ? b.source : b.ip;
        boolean scaler = portSize != null && portSize[0] > NO_PICTURE && portSize[1] > NO_PICTURE;
        i.width = scaler ? portSize[0] : b.hde;
        i.height = scaler ? portSize[1] : b.vde;
        int[] a = b.avi;
        boolean avi = a[0] >= 0 && a[1] >= 0 && a[2] >= 0 && a[3] >= 0 && a[4] >= 0;
        i.format = b.space;
        if (i.format == null && avi) i.format = formatOfY((a[0] >> 5) & 7);
        if (i.format == null && b.yuv420 == 1) i.format = YUV420;
        // the GC packet's deep colour field when it names a depth, else the receiver's own polling state (class doc)
        i.depth = bits(b.depth);
        if (i.depth == 0 && b.gc[1] >= 0) i.depth = gcDepth(b.gc[1] & 0xF);
        if (i.depth == 0) i.depth = bits(b.rxDepth);
        // HDMI carries YCbCr 4:2:2 in 12 bits per colour whatever the source's own depth (sources leave the deep
        // colour field of the GC packet empty for it), as every TV that shows "YCbCr 4:2:2 12 bit" does
        if (YUV422.equals(i.format) && i.depth <= 8) i.depth = 12;
        if (avi) {
            i.vic = a[3] & 0xFF;
            int m = (a[1] >> 4) & 3;
            i.aspect = m == 1 ? SignalFormat.ASPECT_4_3 : m == 2 ? SignalFormat.ASPECT_16_9 : null;
        }
        int c = avi ? (a[1] >> 6) & 3 : b.c, ec = avi ? (a[2] >> 4) & 7 : b.ec;
        if (c >= 0) colorimetry(i, c, ec, a[13]);
        if (avi && i.format != null) i.quant = quant(i, a, b.vs);
        return i;
    }

    private static void colorimetry(HdmiRxInfo i, int c, int ec, int pb14) {
        switch (c) {
            case 0:
                // no data: RGB is sRGB / BT.709 primaries, YCbCr follows the format (SD BT.601, else BT.709)
                if (i.format == null) return;
                i.colorimetry = !RGB.equals(i.format) && i.height > 0 && i.height <= 576 ? BT601 : BT709;
                i.colorimetryDefault = true;
                return;
            case 1: i.colorimetry = BT601; return;
            case 2: i.colorimetry = BT709; return;
            default:
                String[] ext = {XVYCC601, XVYCC709, SYCC601, OPYCC601, OPRGB, BT2020_CL, BT2020};
                if (ec >= 0 && ec < ext.length) i.colorimetry = ext[ec];
                else if (ec == 7 && pb14 >= 0 && ((pb14 >> 4) & 0xF) <= 1) i.colorimetry = P3;   // ACE 0 D65, 1 theater
        }
    }

    /**
     * The range the signal asks for: RGB by Q (1 limited, 2 full, 0 = the format's default: limited for a CE video
     * format, full for an IT one such as a PC mode), YCbCr by YQ (1 full, else limited).
     */
    private static String quant(HdmiRxInfo i, int[] a, int[] vs) {
        if (RGB.equals(i.format)) {
            int q = (a[2] >> 2) & 3;
            if (q == 1) return LIMITED;
            if (q == 2) return FULL;
            if (q != 0) return null;
            boolean ce = i.vic > VIC_VGA || (i.vic == 0 && hdmiVic(vs));
            return ce ? LIMITED : FULL;
        }
        int yq = (a[4] >> 6) & 3;
        return yq == 1 ? FULL : yq == 0 ? LIMITED : null;
    }

    /** True when an HDMI 1.4 VSIF names an extended resolution (4K sent with VIC 0 and an HDMI_VIC). */
    private static boolean hdmiVic(int[] vs) {
        if (vs[0] != 0x81 || vs[5] < 0 || vs[6] < 0 || vs[7] < 0 || vs[8] < 0) return false;
        int oui = vs[5] | (vs[6] << 8) | (vs[7] << 16);
        return oui == OUI_HDMI14 && ((vs[8] >> 5) & 7) == HDMI_VIDEO_EXT;
    }

    private static String formatOfY(int y) {
        switch (y) {
            case 0: return RGB;
            case 1: return YUV422;
            case 2: return YUV444;
            case 3: return YUV420;
            default: return null;
        }
    }

    private static String format(String kind, String sampling) {
        if ("RGB".equalsIgnoreCase(kind)) return RGB;
        if ("422".equals(sampling)) return YUV422;
        if ("420".equals(sampling)) return YUV420;
        return "444".equals(sampling) ? YUV444 : null;
    }

    private static int bits(int n) { return n == 8 || n == 10 || n == 12 || n == 16 ? n : 0; }

    /** General Control packet colour depth (CD): 4 = 8, 5 = 10, 6 = 12, 7 = 16 bits; 0 = not told. */
    private static int gcDepth(int cd) { return cd >= 4 && cd <= 7 ? new int[] {8, 10, 12, 16}[cd - 4] : 0; }

    private static int[] filled(int n) {
        int[] a = new int[n];
        Arrays.fill(a, -1);
        return a;
    }
}
