/*
 * PictureState: a READ-ONLY dump of the picture and light state of an XGIMI Z9X (G0082), the same text on the
 * stock system and on Lumen OS, so that `diff` of two runs shows what differs. It runs as the adb shell user
 * through app_process: nothing is installed and nothing is written on the projector.
 *
 *   adb push picture_state.jar /data/local/tmp/
 *   adb shell CLASSPATH=/data/local/tmp/picture_state.jar app_process /system/bin PictureState > picture_state.txt
 *   adb shell rm /data/local/tmp/picture_state.jar
 *
 * Safety. READS below is the only thing that can reach the vendor HAL (xgimi.hardware.gmpf@1.0 IGmpf / IGmpf2).
 * Every row is a method the stock proxy table (research/play6/z9x_gmpf_codes.txt) names get* / is*, with the wire
 * layout of that table; tools/devices/test/picture_state_test.sh proves every row against it. Arguments are
 * constants taken from the stock callers (window 0, AI PQ type 3, reserve 0) or the two values read first
 * (input source 696, picture mode 666). There is no setter, listener, session or factory call, and NEVER lists
 * the power codes that must not be sent at all. A row with a gate is printed as SKIPPED and not sent:
 *   never-send  a getter the project rules forbid on the Z9X: IGmpf 194 isPlayingHDRVideo
 *              , IGmpf 275 getAmbientLight (no sensor, the vendor
 *               path may run the camera: apps/LumenSensors GmpfAllowlist), IGmpf2 252 getCurHwCurrent (laser currents)
 *   no-caller   no stock caller shows the argument (IGmpf 122 getLumensAdjState)
 * A failed read is printed as ERROR and never repeated. The tool refuses any model but G0082.
 *
 * Output: sections [meta] [hal] [hal-volatile] [props] [surfaceflinger] [settings] [xgimiconfig]. Everything
 * but [meta] and [hal-volatile] is stable between two runs in the same state (picture_state_diff.sh).
 */
import android.os.HwBinder;
import android.os.HwBlob;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.SystemProperties;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PictureState {
    private PictureState() {}

    static final String MODEL = "G0082";
    static final String[] DESCRIPTOR = {null, "xgimi.hardware.gmpf@1.0::IGmpf", "xgimi.hardware.gmpf@1.0::IGmpf2"};
    static final String[] IFACE = {null, "IGmpf ", "IGmpf2"};

    /** Never sent on either interface: goToSleep, goToShutdown, wakeUp, setStandbyStage, setPowerOnMode. */
    static final int[] NEVER = {241, 422, 229, 233, 543};

    static final int SOURCE = 696, PICTURE_MODE = 666;

    /**
     * interface (1 IGmpf, 2 IGmpf2), code, stock method, arguments, reply, section, gate.
     * arguments: w0 = window 0, i0 / i3 = that int32, src = the 696 reply, src012 = 696 when 1 or 2 else 0 (as the
     * Lumen client asks 111), pic = the 666 reply. reply: u8 i8 bool i32, bufN:i32 / bool+bufN:i32 / bool+bufN:i16
     * = a struct of N bytes printed as ints. section: s stable, v volatile.
     */
    static final String[][] READS = {
        {"1", "696", "getCurrentInputSource", "", "i32", "s", ""},
        {"1", "666", "getPictureMode", "w0", "i32", "s", ""},
        {"1", "177", "getDlpLumensLevel", "", "u8", "s", ""},
        {"1", "175", "getDlpLumensMode", "", "u8", "s", ""},
        {"1", "196", "getScreenOnOff", "", "bool", "s", ""},
        {"1", "692", "getBrightness", "w0", "i32", "s", ""},
        {"1", "694", "getContrast", "w0", "i32", "s", ""},
        {"1", "690", "getSaturation", "w0", "i32", "s", ""},
        {"1", "688", "getSharpness", "w0", "i32", "s", ""},
        {"1", "686", "getHue", "w0", "i32", "s", ""},
        {"1", "684", "getColorTemperature", "w0", "i32", "s", ""},
        {"1", "682", "getColorTempRedGain", "w0", "i32", "s", ""},
        {"1", "680", "getColorTempGreenGain", "w0", "i32", "s", ""},
        {"1", "678", "getColorTempBlueGain", "w0", "i32", "s", ""},
        {"1", "676", "getColorTempRedOffset", "w0", "i32", "s", ""},
        {"1", "674", "getColorTempGreenOffset", "w0", "i32", "s", ""},
        {"1", "672", "getColorTempBlueOffset", "w0", "i32", "s", ""},
        {"1", "668", "getMemcLevel", "w0", "i32", "s", ""},
        {"1", "670", "get3DNR", "w0", "i32", "s", ""},
        {"1", "651", "getUcdLevel", "w0", "i32", "s", ""},
        {"1", "111", "getDispColorSpace", "src012,pic", "i32", "s", ""},
        {"1", "620", "getHdrType", "", "i32", "s", ""},
        {"1", "649", "getHdrEnable", "", "bool", "s", ""},
        {"1", "634", "getHdmiRange", "", "i8", "s", ""},
        {"1", "661", "getEdidVersion", "", "i32", "s", ""},
        {"1", "647", "getGameModeOption", "", "i32", "s", ""},
        {"1", "664", "getAspectRatio", "", "i32", "s", ""},
        {"1", "192", "getCurrent3DFormat", "", "u8", "s", ""},
        {"1", "180", "isVideoPlaying", "", "bool", "s", ""},
        {"1", "151", "getHumanDetectOnOff", "", "bool", "s", ""},
        {"1", "147", "getHumanDetectScreenOnOff", "", "bool", "s", ""},
        {"1", "154", "getALOnOff", "", "bool", "s", ""},
        {"1", "79", "getALOnType", "", "i32", "s", ""},
        {"1", "77", "getColorEnhanceType", "", "i32", "s", ""},
        {"1", "81", "getColorEnhance", "", "i32", "s", ""},
        {"1", "277", "getTurboLightOnOff", "", "bool", "s", ""},
        {"1", "327", "getEcoState", "", "bool", "s", ""},
        {"1", "104", "getAutoColorStatus", "", "bool", "s", ""},
        {"1", "103", "getAutoColorCalibrationStatus", "", "bool", "s", ""},
        {"1", "139", "getOverLapOnOff", "", "bool", "s", ""},
        {"1", "145", "getDlpReduceCurrentFlag", "", "i32", "s", ""},
        {"1", "166", "getDlpXPRMode", "", "bool", "s", ""},
        {"1", "538", "getDlpCCAOnOff", "", "bool", "s", ""},
        {"1", "539", "getDlpCCIOnOff", "", "bool", "s", ""},
        {"1", "226", "getLightType", "", "i32", "s", ""},
        {"1", "590", "getAisrLevel", "", "i32", "s", ""},
        {"1", "447", "getLensIrisLevel", "", "i32", "s", ""},
        {"1", "623", "getDolbyVisionPicMode", "", "i32", "s", ""},
        {"1", "621", "getDolbyVisionTmaxGain", "", "i32", "s", ""},
        {"1", "622", "getDolbyVisionTmaxSize", "", "i32", "s", ""},
        {"1", "194", "isPlayingHDRVideo", "", "bool", "s", "never-send"},
        {"1", "275", "getAmbientLight", "", "i32", "v", "never-send"},
        {"1", "122", "getLumensAdjState", "i0", "i32", "s", "no-caller"},
        {"1", "157", "getOutputTimingInfo", "", "bool+buf40:i32", "s", ""},
        {"1", "662", "getHdmiInfo", "", "buf16:i32", "s", ""},
        {"1", "438", "getSystemTemperature", "", "u8", "v", ""},
        {"2", "51", "getLensDispMode", "", "i32", "s", ""},
        {"2", "25", "getDispLumensLevel", "", "i32", "s", ""},
        {"2", "29", "getDynamicBlackType", "", "i32", "s", ""},
        {"2", "28", "getDynamicBlackLevel", "", "i32", "s", ""},
        {"2", "36", "getDispContrastEnhanceLvl", "", "i32", "s", ""},
        {"2", "43", "getDualLightMode", "", "i32", "s", ""},
        {"2", "47", "getHightColorGamutFlag", "i0", "i32", "s", ""},
        {"2", "64", "getDolbyDefaultMode", "", "i32", "s", ""},
        {"2", "69", "getAutoEcoMode", "", "bool", "s", ""},
        {"2", "285", "getAIContrastLevel", "w0", "i32", "s", ""},
        {"2", "287", "getAIContrastStatus", "w0", "i32", "s", ""},
        {"2", "302", "getAiPqEnableStatus", "i3", "bool", "s", ""},
        {"2", "321", "getAisrType", "", "i32", "s", ""},
        {"2", "326", "getTvGammaLevel", "w0", "i32", "s", ""},
        {"2", "328", "getTvDynamicContrastEnable", "w0", "bool", "s", ""},
        {"2", "330", "getHsyPictureModeEnable", "w0", "bool", "s", ""},
        {"2", "269", "GetHDRDyToneMappingStatus", "w0", "i32", "s", ""},
        {"2", "283", "getFDBoostStatus", "w0", "i32", "s", ""},
        {"2", "293", "isFFMPlaying", "", "bool", "s", ""},
        {"2", "294", "getFFMState", "src", "i32", "s", ""},
        {"2", "306", "isHdrVividPlaying", "", "bool", "s", ""},
        {"2", "307", "getHdrVividState", "src,pic", "i32", "s", ""},
        {"2", "309", "getImaxType", "", "i32", "s", ""},
        {"2", "314", "isImaxVideoPlaying", "", "bool", "s", ""},
        {"2", "315", "getImaxState", "src", "i32", "s", ""},
        {"2", "274", "getPictureSplicingState", "", "bool", "s", ""},
        {"2", "22", "getDispOutputFps", "", "i32", "s", ""},
        {"2", "55", "getGameModeProp", "", "bool+buf12:i32", "s", ""},
        {"2", "252", "getCurHwCurrent", "", "bool+buf16:i16", "v", "never-send"},
    };

    /** Properties worth comparing: picture, HDR, light, HWC and the identity of the build. */
    static final String PROPS = "xgimi|aipq|hdr|dolby|hwc|mstar|mtk.*(pq|disp|video)|persist\\.sys\\.sf|ro\\.surface_flinger"
            + "|ro\\.build\\.(fingerprint|version\\.incremental)|ro\\.z9x|ro\\.lumen|ro\\.product\\.(model|device)$";
    static final String PROPS_SKIP = "bluetooth|\\.bt\\.|wifi|wlan|mac|serial|\\bsn\\b|audio|uptime|boottime|\\.time|token|uuid|deviceid"
            + "|svc_debug_pid|^\\[dev\\.mnt|uacmic|wirednfc|proc_compact|autotheme|theme\\.url";

    static final String SF_LINES = "ColorMode|colorMode|DisplayColorSetting|wideColorGamut|WideColor|hdr10|hlg|dolby|Dolby"
            + "|supportedHdrTypes|maxLuminance|minLuminance|maxAverageLuminance|activeColorMode|dataspace=|composition"
            + "|SIDEBAND|sideband";
    static final String SETTINGS = "bright|color|colour|picture|hdr|night|display_|turbo|ambient|eco|aipq|ai_|lumen|gamut|contrast";

    static Integer source, pictureMode;

    public static void main(String[] argv) {
        String model = SystemProperties.get("ro.boot.xgimi.modelname", "");
        if (!MODEL.equals(model)) {
            System.out.println("REFUSED: ro.boot.xgimi.modelname=" + model + " (this tool is for the Z9X " + MODEL + " only)");
            return;
        }
        System.out.println("# picture_state 1");
        System.out.println("[meta]");
        for (String p : new String[] {"ro.boot.xgimi.modelname", "ro.build.fingerprint", "ro.vendor.build.fingerprint",
                "ro.vendor.xgimi.version", "ro.z9x.build_id", "ro.boot.slot_suffix", "sys.boot_completed"}) {
            System.out.println(p + "=" + SystemProperties.get(p, ""));
        }
        System.out.println("uid=" + android.os.Process.myUid());
        hal();
        props();
        exec("[surfaceflinger]", new String[] {"dumpsys", "SurfaceFlinger"}, SF_LINES, null, false);
        System.out.println("[settings]");
        for (String ns : new String[] {"system", "secure", "global"}) {
            exec(null, new String[] {"settings", "list", ns}, SETTINGS, ns + " ", true);
        }
        config("/mnt/vendor/xgimiconfig/" + MODEL);
        System.out.println("# done");
    }

    // ------------------------------------------------------------------ HAL

    static void hal() {
        IHwBinder[] b = new IHwBinder[3];
        String[] why = new String[3];
        for (int i = 1; i <= 2; i++) {
            try {
                b[i] = HwBinder.getService(DESCRIPTOR[i], "default", false);
                if (b[i] == null) why[i] = "service not found";
            } catch (Throwable t) {
                why[i] = t.toString();
            }
        }
        List<String> stable = new ArrayList<>(), vol = new ArrayList<>();
        for (String[] r : READS) {
            int i = Integer.parseInt(r[0]), code = Integer.parseInt(r[1]);
            String line = IFACE[i] + " " + r[1] + " " + r[2] + "(" + r[3] + ") = ";
            if (!r[6].isEmpty()) line += "SKIPPED " + r[6];
            else if (never(code)) line += "REFUSED";
            else if (b[i] == null) line += "NOT CONNECTED " + why[i];
            else {
                int[] args = args(r[3]);
                if (args == null) line += "SKIPPED no source or picture mode";
                else {
                    try {
                        String v = transact(b[i], DESCRIPTOR[i], code, args, r[4]);
                        line += v;
                        if (i == 1 && code == SOURCE) source = Integer.valueOf(v);
                        if (i == 1 && code == PICTURE_MODE) pictureMode = Integer.valueOf(v);
                    } catch (Throwable t) {
                        line += "ERROR " + t;
                    }
                }
            }
            ("v".equals(r[5]) ? vol : stable).add(line);
            System.err.println(line);
        }
        System.out.println("[hal]");
        for (String s : stable) System.out.println(s);
        System.out.println("[hal-volatile]");
        for (String s : vol) System.out.println(s);
    }

    static boolean never(int code) {
        for (int n : NEVER) if (n == code) return true;
        return false;
    }

    /** The int32 arguments of a row, or null when it needs a source / picture mode that was not read. */
    static int[] args(String spec) {
        if (spec.isEmpty()) return new int[0];
        String[] t = spec.split(",");
        int[] a = new int[t.length];
        for (int i = 0; i < t.length; i++) {
            switch (t[i]) {
                case "w0": case "i0": a[i] = 0; break;
                case "i3": a[i] = 3; break;
                case "src":
                    if (source == null || source < 0 || source > 64) return null;
                    a[i] = source; break;
                case "src012":
                    if (source == null) return null;
                    a[i] = source == 1 || source == 2 ? source : 0; break;
                case "pic":
                    if (pictureMode == null || pictureMode < 0 || pictureMode > 40) return null;
                    a[i] = pictureMode; break;
                default: throw new IllegalStateException(spec);
            }
        }
        return a;
    }

    static String transact(IHwBinder b, String desc, int code, int[] args, String reply) throws Exception {
        HwParcel q = new HwParcel();
        q.writeInterfaceToken(desc);
        for (int a : args) q.writeInt32(a);
        HwParcel r = new HwParcel();
        try {
            b.transact(code, q, r, 0);
            r.verifySuccess();
            q.releaseTemporaryStorage();
            switch (reply) {
                case "u8": return Integer.toString(r.readInt8() & 0xff);
                case "i8": return Integer.toString(r.readInt8());
                case "bool": return Boolean.toString(r.readBool());
                case "i32": return Integer.toString(r.readInt32());
                default: break;
            }
            String head = "";
            String s = reply;
            if (s.startsWith("bool+")) { head = r.readBool() + " "; s = s.substring(5); }
            int n = Integer.parseInt(s.substring(3, s.indexOf(':')));
            int step = s.endsWith("i16") ? 2 : 4;
            HwBlob x = r.readBuffer(n);
            StringBuilder sb = new StringBuilder(head);
            for (int i = 0; i + step <= n; i += step) {
                if (i > 0) sb.append(',');
                sb.append(step == 2 ? x.getInt16(i) : x.getInt32(i));
            }
            return sb.toString();
        } finally {
            try { r.release(); } catch (RuntimeException ignored) { }
        }
    }

    // ------------------------------------------------------------------ the rest

    static void props() {
        exec("[props]", new String[] {"getprop"}, PROPS, null, true);
    }

    /** Runs a read-only shell command and prints its lines that match keep (sorted when asked). */
    static void exec(String header, String[] cmd, String keep, String prefix, boolean sort) {
        if (header != null) System.out.println(header);
        List<String> out = new ArrayList<>();
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            java.util.regex.Pattern k = java.util.regex.Pattern.compile(keep);
            java.util.regex.Pattern skip = java.util.regex.Pattern.compile(PROPS_SKIP);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                for (String l; (l = r.readLine()) != null; ) {
                    l = l.trim();
                    if (!k.matcher(l).find() || (sort && skip.matcher(l).find())) continue;
                    out.add((prefix == null ? "" : prefix) + l);
                }
            }
            p.waitFor();
        } catch (Throwable t) {
            out.add("ERROR " + String.join(" ", cmd) + ": " + t);
        }
        if (sort) java.util.Collections.sort(out);
        for (String l : out) System.out.println(l);
    }

    /** sha256 of every file of the vendor picture tables, or what could not be read. */
    static void config(String root) {
        System.out.println("[xgimiconfig]");
        List<String> out = new ArrayList<>();
        walk(new File(root), root.length() + 1, out);
        java.util.Collections.sort(out);
        if (out.isEmpty()) out.add("UNREADABLE " + root);
        for (String l : out) System.out.println(l);
    }

    static void walk(File dir, int cut, List<String> out) {
        File[] list = dir.listFiles();
        if (list == null) {
            if (dir.getPath().length() > cut) out.add("UNREADABLE " + dir.getPath().substring(cut) + "/");
            return;
        }
        Arrays.sort(list);
        for (File f : list) {
            if (f.isDirectory()) { walk(f, cut, out); continue; }
            String name = f.getPath().substring(cut);
            try (InputStream in = new FileInputStream(f)) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
                StringBuilder sb = new StringBuilder();
                for (byte x : md.digest()) sb.append(String.format("%02x", x & 0xff));
                out.add(sb + "  " + name);
            } catch (Throwable t) {
                out.add("UNREADABLE " + name);
            }
        }
    }
}
