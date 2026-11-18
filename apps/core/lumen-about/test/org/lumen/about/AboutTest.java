// SPDX-License-Identifier: Apache-2.0
package org.lumen.about;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * lumen-about on the JVM (org.json from the AOSP sources the updater's tests use): what the projector says about its
 * XGIMI side, the About rows, Lumen Installer's catalog for the way back and what the screen shows.
 * Args: the module folder (for the list certificate and the recorded live catalog).
 */
public final class AboutTest {
    private static int failed, passed;
    private static final String Z9X_URL_19 = "https://product-library.static.xgimi.com/firmware/release/MTK9681/dapengend/"
            + "MTK9681_LEO_6.15.19_20251014-012201/XGIMI_HORSEPOPULAR_XR03D.bin";
    private static final String Z9X_URL_62 = "https://product-library.static.xgimi.com/firmware/release/MTK9681/dapengend/"
            + "MTK9681_LEO_6.15.62_20260325-154151/XGIMI_HORSEPOPULAR_XR03D.bin";
    private static final String Z9X_BIN = "XGIMI_HORSEPOPULAR_XR03D.bin";
    private static final String Z9X_INI = "##update Bin name\n#setting use it to match update bin file\n"
            + "UpdateBinName = \"" + Z9X_BIN + "\"\n\n##update Bin name\nUpdateOTAName = \"XGIMI_HORSEPOPULAR_XR03D_FULL_V\"\n";

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else failed++;
        System.out.println((ok ? "ok   " : "FAIL ") + what);
    }

    private static void eq(Object got, Object want, String what) {
        boolean ok = want == null ? got == null : want.equals(got);
        check(ok, what + (ok ? "" : ": got " + got + ", want " + want));
    }

    private static Function<String, String> props(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return k -> m.getOrDefault(k, "");
    }

    private static XgimiFacts facts(String model, String firmware, String ini) {
        return XgimiFacts.read(props(XgimiFacts.MODEL_PROP, model, XgimiFacts.FIRMWARE_PROP, firmware), p -> ini);
    }

    // ------------------------------------------------------------------ facts
    private static void factsTests() {
        eq(XgimiFacts.model(props(XgimiFacts.MODEL_PROP, "G0082")), "G0082", "model: the boot property");
        eq(XgimiFacts.model(props(XgimiFacts.MODEL_PROP_OLD, "G0130")), "G0130", "model: the older property when the boot one is empty");
        eq(XgimiFacts.model(props(XgimiFacts.MODEL_PROP, "G0111", XgimiFacts.MODEL_PROP_OLD, "G0100")), "G0111",
                "model: the boot property wins");
        eq(XgimiFacts.model(props(XgimiFacts.MODEL_PROP, "../G0082", XgimiFacts.MODEL_PROP_OLD, "H0062")), "H0062",
                "model: an odd boot value is skipped");
        eq(XgimiFacts.model(props(XgimiFacts.MODEL_PROP, " G0155 ")), "G0155", "model: blanks around are dropped");
        for (String bad : new String[] {"g0082", "G082", "G00820", "0082", "G0082/..", ""}) {
            eq(XgimiFacts.model(props(XgimiFacts.MODEL_PROP, bad)), "", "model: '" + bad + "' is no model code");
        }
        eq(XgimiFacts.firmware("V6.15.62"), "v6.15.62", "firmware: V6.15.62");
        eq(XgimiFacts.firmware("v6.16.24"), "v6.16.24", "firmware: v6.16.24");
        eq(XgimiFacts.firmware("6.15.58"), "v6.15.58", "firmware: without the v");
        eq(XgimiFacts.firmware(" v1.2.64 "), "v1.2.64", "firmware: blanks around");
        eq(XgimiFacts.firmware("v1.8.127"), "v1.8.127", "firmware: three digits");
        for (String bad : new String[] {"", "eng.root.20261115", "V6.15.62-test", "v6", "6.15.62.1.2.3", null}) {
            eq(XgimiFacts.firmware(bad), "", "firmware: '" + bad + "' is no version");
        }
        eq(facts("G0082", "v6.15.62", null).firmwareShown(), "V6.15.62", "firmware shown the XGIMI way");
        eq(facts("G0082", "", null).firmwareShown(), "", "no firmware shown when unknown");
        eq(XgimiFacts.shown("v1.2.54"), "V1.2.54", "a catalog version shown the XGIMI way");

        eq(XgimiFacts.updateBinName(Z9X_INI), Z9X_BIN, "stick name: the Z9X form");
        eq(XgimiFacts.updateBinName("UpdateBinName = \"XGIMI_HORSEPOPULAR_XR08C.bin\";\n"), "XGIMI_HORSEPOPULAR_XR08C.bin",
                "stick name: with the semicolon the RS30 config writes");
        eq(XgimiFacts.updateBinName("a = 1\r\n  UpdateBinName=XGIMI_GOATELEGANT_XR03D.bin\r\n"), "XGIMI_GOATELEGANT_XR03D.bin",
                "stick name: CRLF, no quotes, no blanks");
        eq(XgimiFacts.updateBinName("#UpdateBinName = \"XGIMI_OLD_X.bin\"\nUpdateBinName = \"XGIMI_NEW_X.bin\" # note\n"),
                "XGIMI_NEW_X.bin", "stick name: a commented line and a trailing comment are skipped");
        eq(XgimiFacts.updateBinName("UpdateOTAName = \"XGIMI_HORSEPOPULAR_XR03D_FULL_V\"\n"), "", "stick name: only the OTA name");
        eq(XgimiFacts.updateBinName("UpdateBinNameOld = \"XGIMI_A.bin\"\n"), "", "stick name: another key");
        for (String bad : new String[] {"../XGIMI_X.bin", "XGIMI_x.bin", "XGIMI_X.zip", "OTHER_X.bin", "XGIMI_X .bin"}) {
            eq(XgimiFacts.updateBinName("UpdateBinName = \"" + bad + "\"\n"), "", "stick name: '" + bad + "' is refused");
        }
        String[] asked = new String[1];
        List<String> paths = new java.util.ArrayList<>();
        XgimiFacts f = XgimiFacts.read(props(XgimiFacts.MODEL_PROP, "G0130", XgimiFacts.FIRMWARE_PROP, "v6.17.12"), p -> {
            asked[0] = p;
            paths.add(p);
            return "UpdateBinName = \"XGIMI_GOATELEGANT_XR03D.bin\"\n";
        });
        eq(paths, java.util.Arrays.asList("/mnt/vendor/xgimiconfig/G0130/dlp/dlp.ini", "/mnt/vendor/xgimiconfig/G0130/dlp/DlpFlashImageInfo.ini",
                "/mnt/vendor/xgimiconfig/G0130/system.ini", "/vendor/etc/version.txt"),
                "read: the model's own config files and the platform file, nothing else");
        eq(f.usbFile, "XGIMI_GOATELEGANT_XR03D.bin", "read: its stick name");
        eq(f.firmware, "v6.17.12", "read: the firmware");
        asked[0] = null;
        XgimiFacts none = XgimiFacts.read(props(), p -> {
            asked[0] = p;
            return Z9X_INI;
        });
        check(asked[0] == null && none.model.isEmpty() && none.usbFile.isEmpty(), "read: no model, no config read");
        eq(facts("G0082", "v6.15.62", null).usbFile, "", "read: an unreadable config gives no stick name");
    }

    // ------------------------------------------------------------------ rows
    private static String text(RowText t) {
        switch (t) {
            case MODEL_CODE: return "Model code";
            case DLP: return "DLP controller";
            case PART_FIRMWARE: return "%1$s, firmware %2$s";
            case FPGA: return "FPGA";
            case FIRMWARE: return "XGIMI firmware";
            case FIRMWARE_DATED: return "%1$s from %2$s";
            case ANDROID: return "Android";
            case ANDROID_PATCH: return "%1$s, security update %2$s";
            case KERNEL: return "Kernel";
            case BOOT_LOADER: return "Startup firmware";
            case GMUI: return "XGIMI interface";
            case PLATFORM: return "MediaTek platform";
            case REMOTE: return "Remote";
            case WIFI: return "Wi-Fi";
            case BACK: return "Back to the XGIMI system";
            case BACK_SUMMARY_FILE: return "USB stick file: %1$s";
            case BACK_SUMMARY: return "With the XGIMI recovery USB stick";
            default: throw new IllegalArgumentException();
        }
    }

    private static String rowsText(List<AboutRow> rows) {
        StringBuilder b = new StringBuilder();
        for (AboutRow r : rows) b.append(r.key).append('|').append(r.title).append('|').append(r.summary).append('|')
                .append(r.opensScreen).append('\n');
        return b.toString();
    }

    private static void rowsTests() {
        eq(rowsText(AboutRows.of(facts("G0082", "V6.15.62", Z9X_INI), AboutTest::text)),
                "lumen_model_code|Model code|G0082|false\nlumen_xgimi_firmware|XGIMI firmware|V6.15.62|false\n"
                        + "lumen_back_to_xgimi|Back to the XGIMI system|USB stick file: " + Z9X_BIN + "|true\n",
                "rows: model code, firmware, back with the stick name, in this order");
        eq(rowsText(AboutRows.of(facts("H0062", "", null), AboutTest::text)),
                "lumen_model_code|Model code|H0062|false\nlumen_back_to_xgimi|Back to the XGIMI system|"
                        + "With the XGIMI recovery USB stick|true\n",
                "rows: no firmware row without a version, the plain back summary without a stick name");
        check(AboutRows.of(facts("", "v6.15.62", Z9X_INI), AboutTest::text).isEmpty(), "rows: none without a model code");
        check(AboutRows.versions(facts("", "v6.15.62", Z9X_INI), AboutTest::text, true).isEmpty(), "versions: none without a model code");
        XgimiFacts x50 = XgimiFacts.read(props(XgimiFacts.MODEL_PROP, "G0111", XgimiFacts.FIRMWARE_PROP, "V1.2.64"),
                p -> p.endsWith("/dlp/dlp.ini") ? X50_DLP : p.endsWith("/DlpFlashImageInfo.ini") ? X50_FLASH : null);
        eq(rowsText(AboutRows.of(x50, AboutTest::text)),
                "lumen_model_code|Model code|G0111|false\nlumen_xgimi_firmware|XGIMI firmware|V1.2.64|false\n"
                        + "lumen_dlp_controller|DLP controller|TI DLPC8445, firmware 9.4.128|false\nlumen_fpga|FPGA|160T|false\n"
                        + "lumen_back_to_xgimi|Back to the XGIMI system|With the XGIMI recovery USB stick|true\n",
                "rows: after the firmware the DLP controller with its firmware and the FPGA");
        XgimiFacts z9x = XgimiFacts.read(props(XgimiFacts.MODEL_PROP, "G0082"), p -> p.endsWith("/dlp/dlp.ini") ? Z9X_DLP : null);
        eq(rowsText(AboutRows.of(z9x, AboutTest::text)),
                "lumen_model_code|Model code|G0082|false\nlumen_dlp_controller|DLP controller|TI DLPC8445|false\n"
                        + "lumen_back_to_xgimi|Back to the XGIMI system|With the XGIMI recovery USB stick|true\n",
                "rows: a model without an FPGA and without a flash image file: the controller alone");
    }

    // ------------------------------------------------------------------ firmware parts
    private static void partsTests() {
        eq(FirmwareParts.date("Wed Mar 25 15:55:23 HKT 2026"), "2026-03-25", "date: the build's own form");
        eq(FirmwareParts.date("Wed Oct  7 13:52:50 CST 2026"), "2026-10-07", "date: a one digit day with two blanks");
        for (String bad : new String[] {"", "2026-03-25", "Wed Foo 25 15:55:23 HKT 2026", "Wed Mar 32 15:55:23 HKT 2026",
                "Wed Mar 0 15:55:23 HKT 2026", "Mar 25 2026", null}) {
            eq(FirmwareParts.date(bad), "", "date: '" + bad + "' is no build date");
        }
        Function<String, String> stock = props(FirmwareParts.ANDROID_PROP, "14", FirmwareParts.PATCH_PROP, "2025-07-05",
                FirmwareParts.BOOT_LOADER_PROP, "01.01.260325", FirmwareParts.GMUI_PROP, "overseas_6.15.202.04",
                FirmwareParts.REMOTE_PROP, "B940D_CY06_CY_V1.0.3", FirmwareParts.SYSTEM_DATE_PROP, "Wed Mar 25 15:55:23 HKT 2026",
                FirmwareParts.WIFI_PROP, "t-neptune-main-sta-2017-MT7920_2034_L_x86_Gen4m_AT-20260205002547");
        FirmwareParts p = FirmwareParts.parse(stock, "export VERSION := t-apollo-mp-2101-cnu-mt9681-55-001-55\n",
                "5.15.167-android14-11-g6af14f615a75-dirty", true);
        eq(String.join("|", p.date, p.android, p.patch, p.kernel, p.bootLoader, p.gmui, p.platform, p.remote, p.remoteFirmware,
                p.wifi, p.wifiFirmware), "2026-03-25|14|2025-07-05|5.15.167|01.01.260325|6.15.202.04|"
                + "t-apollo-mp-2101-cnu-mt9681-55-001-55|B940D|1.0.3|MT7920|20260205002547",
                "parts: every value in the stock form, the system date stands in on the XGIMI system");
        eq(FirmwareParts.parse(stock, null, "", false).date, "", "parts: on Lumen OS the system date is Lumen's, never shown");
        FirmwareParts odd = FirmwareParts.parse(props(FirmwareParts.ANDROID_PROP, "Baklava", FirmwareParts.PATCH_PROP, "2025-07-05",
                FirmwareParts.BOOT_LOADER_PROP, "123456789", FirmwareParts.GMUI_PROP, "GMUI6.0",
                FirmwareParts.REMOTE_PROP, "null", FirmwareParts.WIFI_PROP, "2_5_0-\"0\""), "VERSION = x\n", "eng-kernel", true);
        eq(String.join("|", odd.android, odd.patch, odd.kernel, odd.bootLoader, odd.gmui, odd.platform, odd.remote, odd.wifi),
                "|||||||", "parts: placeholders and odd forms give nothing (no patch without an Android version)");
        eq(FirmwareParts.parse(props(FirmwareParts.BOOT_LOADER_PROP, "20170621"), null, "4.19.116+", true).bootLoader + "|"
                + FirmwareParts.parse(props(), null, "4.19.116+", true).kernel, "|4.19.116",
                "parts: the Play 6 boot loader placeholder is hidden, its kernel shown");
        Function<String, String> lumen = props(XgimiFacts.MODEL_PROP, "G0082", "ro.lumen.version", "1.0.10",
                XgimiFacts.SYSTEM_FIRMWARE_PROP, "1.0.10");
        eq(XgimiFacts.read(lumen, f -> null).firmware, "", "facts: on Lumen OS the system version is never taken for XGIMI's");
        eq(XgimiFacts.read(props(XgimiFacts.MODEL_PROP, "G0082", XgimiFacts.SYSTEM_FIRMWARE_PROP, "v6.15.62"), f -> null).firmware,
                "v6.15.62", "facts: on the XGIMI system its own version stands in for a hidden vendor one");
        eq(XgimiFacts.read(props(XgimiFacts.MODEL_PROP, "G0082", XgimiFacts.TRIAL_PROP, "1", XgimiFacts.SYSTEM_FIRMWARE_PROP,
                "1.0.10"), f -> null).firmware, "", "facts: in a trial neither");
    }

    // ------------------------------------------------------------------ every model, from its dump
    /** the rows Lumen Installer shows on the XGIMI system of each model (SELinux permissive: it reads every input) */
    private static final String[][] FULL = {
        {"G0082", "XGIMI firmware|V6.15.62 from 2026-03-25", "Android|14, security update 2025-07-05", "Kernel|5.15.167",
            "Startup firmware|01.01.260325", "XGIMI interface|GMUI 6.17.200.03", "MediaTek platform|t-apollo-mp-2101-cnu-mt9681-55-001-55",
            "DLP controller|TI DLPC8445", "Remote|B940D, firmware 1.0.3", "Wi-Fi|MT7920, firmware 20240902122656"},
        {"G0130", "XGIMI firmware|V6.16.24 from 2026-04-28", "Android|13, security update 2026-01-05",
            "XGIMI interface|GMUI 6.18.201.02", "MediaTek platform|t-apollo-mp-2101-cnt-mt5873-71-001-119",
            "DLP controller|TI DLPC8445, firmware 6.9.4", "Remote|B940D, firmware 1.0.3", "Wi-Fi|MT7920, firmware 20260205002547"},
        {"G0148", "XGIMI firmware|V6.17.12 from 2026-07-28", "Android|13, security update 2026-04-05", "Kernel|5.15.189",
            "XGIMI interface|GMUI 6.18.202.05", "MediaTek platform|t-apollo-mp-2101-cnt-mt5873-71-001-132",
            "DLP controller|TI DLPC8445, firmware 6.12.12", "Remote|B940D, firmware 1.0.3"},
        {"G0111", "XGIMI firmware|V1.2.64 from 2026-06-29", "Android|14, security update 2026-04-05", "Kernel|5.15.192",
            "Startup firmware|01.01.260629", "XGIMI interface|GMUI 6.18.202.04", "MediaTek platform|t-apollo-mp-2101-cnu-mt9681-55-001-112",
            "DLP controller|TI DLPC8445, firmware 9.4.128", "FPGA|160T", "Remote|B970C, firmware 1.0.4",
            "Wi-Fi|MT7920, firmware 20240902122656"},
        {"G0100", "DLP controller|TI DLPC8445, firmware 9.3.130", "FPGA|160T"},
        {"G0155", "XGIMI firmware|V1.1.42 from 2026-10-07", "Android|14, security update 2026-06-05", "Kernel|5.15.192",
            "Startup firmware|01.01.261007", "XGIMI interface|GMUI 6.20.201.02", "MediaTek platform|t-apollo-mp-2101-cnu-mt9681-55-001-122",
            "DLP controller|TI DLPC8445, firmware 11.4.16", "FPGA|PH1A400", "Remote|B870C, firmware 1.0.5",
            "Wi-Fi|MT7920, firmware 20240902122656"},
        {"H0062", "XGIMI firmware|V1.2.99 from 2026-09-07", "Android|14, security update 2026-04-05",
            "XGIMI interface|GMUI 6.15.202.04", "MediaTek platform|t-apollo-mp-2101-cnu-mt9681-55-001-112",
            "DLP controller|TI DLPC8445, firmware 9.4.7", "FPGA|160T"},
        {"H0063", "XGIMI firmware|V1.2.99 from 2026-09-07", "Android|14, security update 2026-04-05",
            "XGIMI interface|GMUI 6.15.202.04", "MediaTek platform|t-apollo-mp-2101-cnu-mt9681-55-001-112",
            "DLP controller|TI DLPC8445, firmware 9.3.63", "FPGA|160T"},
        {"G0058", "XGIMI firmware|V1.8.127 from 2026-04-10", "Android|11, security update 2024-02-05", "Kernel|4.19.116",
            "XGIMI interface|GMUI 6.18.100.01", "Remote|B940D, firmware 1.0.3"},
    };

    private static String fixture(String dir, String name) throws Exception {
        java.nio.file.Path p = Paths.get(dir, name);
        return Files.isRegularFile(p) ? new String(Files.readAllBytes(p), StandardCharsets.UTF_8) : null;
    }

    private static String shown(List<AboutRow> rows) {
        StringBuilder b = new StringBuilder();
        for (AboutRow r : rows) b.append(r.title).append('|').append(r.summary).append('\n');
        return b.toString();
    }

    private static String lines(List<String> rows) {
        StringBuilder b = new StringBuilder();
        for (String r : rows) b.append(r).append('\n');
        return b.toString();
    }

    private static void modelTests(String module) throws Exception {
        String dir = module + "/test/models";
        Map<String, String[]> policy = new HashMap<>();
        for (String line : fixture(dir, "stock_app.tsv").split("\n")) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] c = line.split("\t");
            policy.put(c[0], c);
        }
        check(policy.size() == FULL.length, "models: every model has its row in stock_app.tsv");
        for (String[] want : FULL) {
            String code = want[0];
            List<String> full = java.util.Arrays.asList(want).subList(1, want.length);
            Map<String, String> pm = new HashMap<>();
            String kernel = "";
            for (String line : fixture(dir, code + ".props").split("\n")) {
                int eqi = line.indexOf('=');
                if (eqi < 0) continue;
                if (line.startsWith("kernel=")) kernel = line.substring(7);
                else pm.put(line.substring(0, eqi), line.substring(eqi + 1));
            }
            Map<String, String> files = new HashMap<>();
            files.put(XgimiFacts.PLATFORM_FILE, fixture(dir, code + ".version.txt"));
            files.put(XgimiFacts.CONFIG_ROOT + code + XgimiFacts.DLP_INI, fixture(dir, code + ".dlp.ini"));
            files.put(XgimiFacts.CONFIG_ROOT + code + XgimiFacts.DLP_FLASH_INI, fixture(dir, code + ".flash.ini"));
            String[] pol = policy.get(code);
            check(pol != null && "permissive".equals(pol[1]), "models: " + code + " boots its stock system permissive");
            // Lumen Installer on the XGIMI system: an ordinary app, every input readable (permissive, o+r files)
            XgimiFacts app = XgimiFacts.read(k -> pm.getOrDefault(k, ""), files::get, kernel);
            eq(shown(AboutRows.versions(app, AboutTest::text, true)), lines(full), "models: " + code + " in Lumen Installer");
            // the same unit if its firmware ever boots enforcing: what the image's policy hides has no row, nothing else moves
            java.util.Set<String> hp = new java.util.HashSet<>(java.util.Arrays.asList(pol[2].split(",")));
            XgimiFacts closed = XgimiFacts.read(k -> hp.contains(k) ? "" : pm.getOrDefault(k, ""),
                    f -> f.startsWith(pol[3]) ? null : files.get(f), kernel);
            List<String> open = new java.util.ArrayList<>();
            for (String r : full) {
                boolean config = r.startsWith("DLP controller|") || r.startsWith("FPGA|");
                if (!(config && pol[3].startsWith("/mnt/vendor/xgimiconfig/")) && !(r.startsWith("Wi-Fi|") && hp.contains(FirmwareParts.WIFI_PROP))) open.add(r);
            }
            eq(shown(AboutRows.versions(closed, AboutTest::text, true)), lines(open), "models: " + code + " under an enforcing policy");
            // the About page of Lumen OS: the same rows, without the Android and kernel ones (the page lists Lumen's own)
            Map<String, String> lp = new HashMap<>(pm);
            lp.put("ro.lumen.version", "1.0.10");
            lp.put(XgimiFacts.SYSTEM_FIRMWARE_PROP, "1.0.10");
            XgimiFacts lumen = XgimiFacts.read(k -> lp.getOrDefault(k, ""), files::get, kernel);
            List<String> about = new java.util.ArrayList<>();
            about.add("Model code|" + code);
            for (String r : full) if (!r.startsWith("Android|") && !r.startsWith("Kernel|")) about.add(r);
            about.add("Back to the XGIMI system|With the XGIMI recovery USB stick");
            eq(shown(AboutRows.of(lumen, AboutTest::text)), lines(about), "models: " + code + " on the About page of Lumen OS");
        }
    }

    // ------------------------------------------------------------------ light engine
    /** the head of /mnt/vendor/xgimiconfig/G0111/dlp/dlp.ini of X50 Ultra V1.2.64 (the enums are in its comments) */
    static final String X50_DLP = "[Device]\r\n##DLP_TYPE\r\n# EN_DLP_TI6401 = 1\r\n# EN_DLP_TI6421 = 2\r\n# EN_DLP_TI3432 = 3\r\n"
            + "# EN_DLP_TI3436 = 9\r\n# EN_DLP_TI6421_led = 10\r\n# EN_DLP_TI6540 = 11\r\n# EN_DLP_TI34x_ALL = 12 //EN_DLP_TI3440 | EN_DLP_TI3439\r\n"
            + "# EN_DLP_TI8445 = 13\r\nDlpType = 13;\r\n#E_FPGA_DEV_TYPE_NONE = 0,\r\n#E_FPGA_DEV_TYPE_PL25G = 1,\r\n"
            + "#E_FPGA_DEV_TYPE_200H = 2,\r\n#E_FPGA_DEV_TYPE_160T = 3,\r\n#E_FPGA_DEV_TYPE_PH1A400 = 4,\r\nextFPGAType = 3;\r\n"
            + "# PANEL_TYPE_4K   = 5\r\nDlp2dPanelType = 5;\r\n";
    static final String X50_FLASH = "[DLP_UPGRADE_IMAGE_INFO]\r\nDlpFlashImageVersion = 9.4.128;\r\nDlpSubType = 0xff;\r\nimageCrc = 3966266752;\r\n";
    /** Z9X G0082 V6.15.62: the same enum, no external FPGA line, no flash image file */
    static final String Z9X_DLP = "##DLP_TYPE\n# EN_DLP_TI3436 = 9\n# EN_DLP_TI8445 = 13\nDlpType = 13;\n";

    private static void engineTests() {
        LightEngine x = LightEngine.parse(X50_DLP, X50_FLASH);
        eq(x.controller + "|" + x.firmware + "|" + x.fpga, "TI DLPC8445|9.4.128|160T", "engine: X50 Ultra");
        LightEngine rs = LightEngine.parse(X50_DLP.replace("extFPGAType = 3;", "extFPGAType = 4;"), X50_FLASH.replace("9.4.128", "11.4.16"));
        eq(rs.controller + "|" + rs.firmware + "|" + rs.fpga, "TI DLPC8445|11.4.16|PH1A400", "engine: RS30 Ultra Max");
        LightEngine p6 = LightEngine.parse(X50_DLP.replace("DlpType = 13;", "DlpType = 9;").replace("extFPGAType = 3;", "extFPGAType = 0;"), null);
        eq(p6.controller + "|" + p6.firmware + "|" + p6.fpga, "TI DLPC3436||", "engine: DLPC3436, an FPGA of type NONE is no FPGA");
        LightEngine z = LightEngine.parse(Z9X_DLP, null);
        eq(z.controller + "|" + z.firmware + "|" + z.fpga, "TI DLPC8445||", "engine: Z9X");
        eq(LightEngine.parse(X50_DLP.replace("DlpType = 13;", "DlpType = 12;"), X50_FLASH).controller, "", "engine: a group value is no chip name");
        eq(LightEngine.parse(X50_DLP.replace("DlpType = 13;", "DlpType = 99;"), X50_FLASH).firmware, "", "engine: an unknown chip, no firmware either");
        eq(LightEngine.parse("DlpType = 13;\nextFPGAType = 3;\n", X50_FLASH).controller, "", "engine: no enum in the file, nothing shown");
        eq(LightEngine.parse("#DlpType = 13;\n# EN_DLP_TI8445 = 13\n", null).controller, "", "engine: a commented value is no value");
        eq(LightEngine.parse(null, X50_FLASH).controller, "", "engine: no file, nothing");
        eq(LightEngine.parse(X50_DLP, "DlpFlashImageVersion = 9.4.128abc;\n").firmware, "", "engine: an odd version is left out");
    }

    // ------------------------------------------------------------------ catalog
    private static KeyPair keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static byte[] sign(byte[] data, PrivateKey k) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(k);
        s.update(data);
        return s.sign();
    }

    private static JSONObject file(String stock, String version, long size, String url) throws Exception {
        return new JSONObject().put("stock", stock).put("version", version).put("size", size).put("url", url);
    }

    private static JSONObject catalog(JSONObject z9xRestore) throws Exception {
        JSONArray models = new JSONArray();
        models.put(new JSONObject().put("model", "G0130").put("name", "XGIMI Z9X HB"));
        JSONObject z = new JSONObject().put("model", "G0082").put("name", "XGIMI Z9X");
        if (z9xRestore != null) z.put("restore", z9xRestore);
        models.put(z);
        return new JSONObject().put("schema", 1).put("kind", "lumen-installer-catalog").put("serial", 1794777146L)
                .put("releases", "https://github.com/Project-Lumen-OS/lumen-os/releases").put("models", models);
    }

    private static JSONObject z9xRestore() throws Exception {
        return new JSONObject().put("file", Z9X_BIN).put("files", new JSONArray()
                .put(file("v6.15.19", "v6.15.19", 2589266736L, Z9X_URL_19))
                .put(file("v6.15.58", "v6.15.62", 2656047920L, Z9X_URL_62))
                .put(file("v6.15.62", "v6.15.62", 2656047920L, Z9X_URL_62)));
    }

    private static RestoreCatalog signed(JSONObject c, KeyPair k) throws Exception {
        byte[] b = c.toString().getBytes(StandardCharsets.UTF_8);
        return RestoreCatalog.read(b, sign(b, k.getPrivate()), k.getPublic());
    }

    private static void catalogTests() throws Exception {
        KeyPair k = keys(), other = keys();
        byte[] good = catalog(z9xRestore()).toString().getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(good, k.getPrivate());
        RestoreCatalog c = RestoreCatalog.read(good, sig, k.getPublic());
        check(c != null && c.serial == 1794777146L, "catalog: a signed catalog is read, with its serial");
        byte[] tampered = good.clone();
        tampered[tampered.length / 2] ^= 1;
        check(RestoreCatalog.read(tampered, sig, k.getPublic()) == null, "catalog: one changed byte is refused");
        check(RestoreCatalog.read(good, sig, other.getPublic()) == null, "catalog: another key is refused");
        check(RestoreCatalog.read(good, null, k.getPublic()) == null, "catalog: no signature is refused");
        check(RestoreCatalog.read(good, sig, null) == null, "catalog: no key is refused");
        byte[] big = new byte[RestoreCatalog.MAX_BYTES + 1];
        java.util.Arrays.fill(big, (byte) ' ');
        System.arraycopy(good, 0, big, 0, good.length);
        check(RestoreCatalog.read(big, sign(big, k.getPrivate()), k.getPublic()) == null, "catalog: more than 1 MiB is refused");
        check(signed(catalog(null).put("kind", "lumen-installer-devices"), k) == null, "catalog: another kind is refused");
        check(signed(catalog(null).put("schema", 2), k) == null, "catalog: another schema is refused");
        check(signed(catalog(null).put("serial", 0), k) == null, "catalog: serial 0 is refused");
        byte[] notJson = "{\"schema\": 1,".getBytes(StandardCharsets.UTF_8);
        check(RestoreCatalog.read(notJson, sign(notJson, k.getPrivate()), k.getPublic()) == null, "catalog: broken JSON is refused");

        RestoreCatalog older = signed(catalog(null).put("serial", 1794777145L), k);
        RestoreCatalog newer = signed(catalog(null).put("serial", 1794777147L), k);
        check(RestoreCatalog.replaces(c, null) && RestoreCatalog.replaces(newer, c) && RestoreCatalog.replaces(c, c),
                "catalog: a fresh copy replaces none, an older or the same kept one");
        check(!RestoreCatalog.replaces(older, c) && !RestoreCatalog.replaces(null, c) && !RestoreCatalog.replaces(null, null),
                "catalog: an older copy or none never replaces the kept one");
        eq(c.usbFile("G0082"), Z9X_BIN, "catalog: the stick name of a model with a way back");
        eq(c.usbFile("G0130"), "", "catalog: no stick name for a model without one");
        eq(c.usbFile("G0111"), "", "catalog: no stick name for a model it does not list");
        OfficialFile f = c.file("G0082", "v6.15.58");
        check(f != null && f.version.equals("v6.15.62") && f.size == 2656047920L && f.url.equals(Z9X_URL_62)
                && f.name.equals(Z9X_BIN), "catalog: V6.15.58 goes back with the newer official V6.15.62 file");
        f = c.file("G0082", "6.15.19");
        check(f != null && f.version.equals("v6.15.19") && f.url.equals(Z9X_URL_19), "catalog: V6.15.19 with its own file");
        f = c.file("G0082", "V6.15.62");
        check(f != null && f.version.equals("v6.15.62") && f.url.equals(Z9X_URL_62), "catalog: V6.15.62 with its own file");
        check(c.file("G0082", "v6.15.99") == null, "catalog: no file for a version it does not list");
        check(c.file("G0082", "") == null, "catalog: no file without a firmware");
        check(c.file("G0130", "v6.17.12") == null, "catalog: no file for a model without a way back");

        String[][] badRows = {
                {"v6.15.62", "1", "https://example.com/firmware/release/" + Z9X_BIN, "an address off XGIMI's server"},
                {"v6.15.62", "1", Z9X_URL_62.replace(Z9X_BIN, "XGIMI_HORSEPOPULAR_XR04E.bin"), "a file of another name"},
                {"v6.15.62", "1", Z9X_URL_62.replace("/release/", "/release/../debug/"), "an address with .."},
                {"v6.15.62", "1", Z9X_URL_62.replace("release", "rel ease"), "an address with a blank"},
                {"v6.15.62", "0", Z9X_URL_62, "size 0"},
                {"latest", "1", Z9X_URL_62, "no version"},
                {"v6.15.62", "1", "http://product-library.static.xgimi.com/firmware/release/" + Z9X_BIN, "plain http"},
                {"v6.15.19", "1", Z9X_URL_19, "a file older than the firmware (the stick may refuse it)"},
                {"v6.15", "1", Z9X_URL_62, "a shorter, lower version"},
        };
        for (String[] b : badRows) {
            JSONObject r = new JSONObject().put("file", Z9X_BIN).put("files", new JSONArray()
                    .put(file("v6.15.58", b[0], Long.parseLong(b[1]), b[2])));
            check(signed(catalog(r), k).file("G0082", "v6.15.58") == null, "catalog: a row with " + b[3] + " is left out");
        }
        JSONObject byNumber = new JSONObject().put("file", Z9X_BIN).put("files", new JSONArray()
                .put(file("v6.15.9", "v6.15.10", 1, Z9X_URL_62)));
        check(signed(catalog(byNumber), k).file("G0082", "v6.15.9") != null,
                "catalog: versions compare by number (V6.15.10 is newer than V6.15.9)");
        check(RestoreCatalog.older("v6.15.19", "v6.15.58") && RestoreCatalog.older("v6.15", "v6.15.1")
                && !RestoreCatalog.older("v6.15.62", "v6.15.62") && !RestoreCatalog.older("v6.15.100", "v6.15.99")
                && !RestoreCatalog.older("v6.16", "v6.15.99"), "catalog: older, part by part");
        JSONObject badName = new JSONObject().put("file", "../" + Z9X_BIN).put("files", new JSONArray()
                .put(file("v6.15.58", "v6.15.62", 1, Z9X_URL_62)));
        RestoreCatalog bn = signed(catalog(badName), k);
        check(bn.usbFile("G0082").isEmpty() && bn.file("G0082", "v6.15.58") == null, "catalog: an odd stick name gives nothing");
        check(RestoreCatalog.official(Z9X_URL_19, Z9X_BIN), "catalog: the real Z9X address is official");
        check(!RestoreCatalog.official(Z9X_URL_19 + "x", Z9X_BIN), "catalog: an address past the name is not");
        check(!RestoreCatalog.official(Z9X_URL_19.replace(".bin", ".bin\""), Z9X_BIN), "catalog: an address with a quote is not");
    }

    // ------------------------------------------------------------------ the real list certificate and live catalog
    private static void liveTests(String module) throws Exception {
        PublicKey key;
        try (InputStream in = new FileInputStream(module + "/assets/lumen_about/index.x509.pem")) {
            key = CertificateFactory.getInstance("X.509").generateCertificate(in).getPublicKey();
        }
        byte[] json = Files.readAllBytes(Paths.get(module, "test", "live", "catalog-v2.json"));
        byte[] sig = Files.readAllBytes(Paths.get(module, "test", "live", "catalog-v2.json.sig"));
        RestoreCatalog live = RestoreCatalog.read(json, sig, key);
        check(live != null && live.serial == 1794777146L, "live: the published catalog verifies with the list certificate");
        check(live != null && live.usbFile("G0082").isEmpty() && live.file("G0082", "v6.15.58") == null,
                "live: the published catalog has no way back rows yet (the screen shows the guide)");
    }

    // ------------------------------------------------------------------ the screen
    private static void wayBackTests() throws Exception {
        KeyPair k = keys();
        RestoreCatalog c = signed(catalog(z9xRestore()), k);
        WayBack w = WayBack.of(facts("G0082", "v6.15.58", Z9X_INI), c, "ru");
        check(w.official != null && w.link.equals(Z9X_URL_62) && w.usbFile.equals(Z9X_BIN) && w.firmware.equals("V6.15.58")
                && w.model.equals("G0082"), "screen: the official file, its address in the QR code");
        w = WayBack.of(facts("G0082", "v6.15.58", "UpdateBinName = \"XGIMI_HORSEPOPULAR_XR09D.bin\"\n"), c, "en");
        check(w.official == null && w.usbFile.equals("XGIMI_HORSEPOPULAR_XR09D.bin") && w.link.equals(Guide.backToStock("en")),
                "screen: a catalog file of another name than the unit's own is never offered");
        w = WayBack.of(facts("G0082", "v6.15.62", null), c, "en");
        check(w.official != null && w.usbFile.equals(Z9X_BIN) && w.link.equals(Z9X_URL_62),
                "screen: the catalog's stick name when the unit's config cannot be read");
        w = WayBack.of(facts("G0082", "v6.15.58", Z9X_INI), null, "uk");
        check(w.official == null && w.usbFile.equals(Z9X_BIN) && w.link.equals(Guide.backToStock("uk")),
                "screen: no catalog yet, the guide");
        w = WayBack.of(facts("G0082", "v6.15.99", Z9X_INI), c, "de");
        check(w.official == null && w.link.equals(Guide.backToStock("de")), "screen: a firmware the catalog does not know, the guide");
        eq(Guide.backToStock("ru"), "https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/ru.md#stock", "guide: Russian");
        for (String l : new String[] {"uk", "be", "kk"}) eq(Guide.backToStock(l), Guide.backToStock("ru"), "guide: Russian for " + l);
        for (String l : new String[] {"en", "de", "zh", ""}) {
            eq(Guide.backToStock(l), "https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/en.md#stock",
                    "guide: English for '" + l + "'");
        }
    }

    public static void main(String[] a) throws Exception {
        factsTests();
        engineTests();
        rowsTests();
        partsTests();
        modelTests(a[0]);
        catalogTests();
        liveTests(a[0]);
        wayBackTests();
        System.out.println("AboutTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
