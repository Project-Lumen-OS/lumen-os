#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of lumen-about and its wiring: each one must turn test/run.sh red (run.sh green first).
  JAVA_HOME=... ANDROID_SDK=... python3 apps/core/lumen-about/test/mutate.py"""
import os
import shutil
import subprocess
import sys
import tempfile

T = os.path.dirname(os.path.abspath(__file__))
G = os.path.abspath(os.path.join(T, "../../../.."))
SRC = "apps/core/lumen-about/src/org/lumen/about/"
F, RC, WB, GD, AR, AP, CS, LE = (SRC + n + ".java" for n in
                                 ("XgimiFacts", "RestoreCatalog", "WayBack", "Guide", "AboutRows", "AboutProvider",
                                  "CatalogSource", "LightEngine"))
RU = "apps/core/lumen-about/res/values-ru/lumen_strings_about.xml"
COPY = ("apps/core", "apps/sdk", "apps/LumenInstaller/assets", "apps/LumenInstaller/src", "apps/LumenInstaller/res",
        "apps/LumenInstaller/app.layers", "apps/LumenInstaller/lumen-about.files", "apps/app_dirs.tsv",
        "tools/devices/assemble_app.py", "tools/devices/devices.py", "apps/ProjectorBase/AndroidManifest.xml",
        "apps/HbProjector/AndroidManifest.xml", "apps/X50Projector/AndroidManifest.xml",
        "apps/Play6Projector/AndroidManifest.xml", "devices", "tools/base_patch", "tools/i18n",
        "tools/devices/sign_device.py", "tools/play6/sign_play6.py")
MUTANTS = {
    # Lumen Installer takes the row texts in exactly its own languages (one language per screen)
    "installer_rows_lose_russian": ("apps/LumenInstaller/lumen-about.files", "res/values-ru/lumen_strings_about.xml\n", ""),
    "installer_rows_in_chinese": ("apps/LumenInstaller/lumen-about.files", "res/values/lumen_strings_about.xml\n",
                                  "res/values/lumen_strings_about.xml\nres/values-zh-rCN/lumen_strings_about.xml\n"),
    # the facts
    "no_old_model_prop": (F, "new String[] {MODEL_PROP, MODEL_PROP_OLD}", "new String[] {MODEL_PROP}"),
    "any_model_code": (F, 'Pattern.compile("[A-Z][0-9]{4}")', 'Pattern.compile(".+")'),
    "firmware_kept_raw": (F, 'return m.matches() ? "v" + m.group(1) : "";', 'return m.matches() ? trim(raw) : "";'),
    "any_bin_name": (F, 'Pattern.compile("XGIMI_[A-Z0-9_]{1,48}\\\\.bin")', 'Pattern.compile(".+\\\\.bin")'),
    "comments_read": (F, "BIN_LINE.matcher(hash < 0 ? line : line.substring(0, hash))", "BIN_LINE.matcher(line)"),
    "wrong_config_path": (F, 'file.apply(CONFIG_ROOT + m + "/" + SYSTEM_INI)', 'file.apply(CONFIG_ROOT + SYSTEM_INI)'),
    "config_without_model": (F, 'if (m.isEmpty()) return new XgimiFacts(m, fw, "");', ""),
    "dlp_wrong_folder": (F, "file.apply(CONFIG_ROOT + m + DLP_INI)", "file.apply(CONFIG_ROOT + DLP_INI)"),
    "flash_file_ignored": (F, "file.apply(CONFIG_ROOT + m + DLP_FLASH_INI)", "null"),
    # the light engine: names only from the file's own enum, nothing for an unknown value
    "dlp_any_name": (LE, 'new LightEngine(chip.isEmpty() ? "" : "TI DLPC" + chip, fw, fpga)', 'new LightEngine("TI DLPC" + chip, fw, fpga)'),
    "dlp_enum_value_ignored": (LE, "if (n.group(2).equals(v.group(1))) return n.group(1);", "return n.group(1);"),
    "dlp_commented_value": (LE, '"(?m)^\\\\s*DlpType', '"(?m)^.*DlpType'),
    "fpga_none_shown": (LE, 'if ("NONE".equals(fpga)) fpga = "";', ""),
    "firmware_without_chip": (LE, "if (!chip.isEmpty() && flashIni != null) {", "if (flashIni != null) {"),
    "odd_firmware_ok": (LE, '(?:\\\\.[0-9]{1,5}){1,3})\\\\s*(?:;|$)");', '(?:\\\\.[0-9]{1,5}){1,3})");'),
    # the rows next to the model code
    "dlp_row_dropped": (AR, "if (!le.controller.isEmpty()) rows.add(row(KEY_DLP, texts.apply(RowText.DLP), part(texts, le.controller, le.firmware)));", ""),
    "dlp_firmware_dropped": (AR, "part(texts, le.controller, le.firmware)", "part(texts, le.controller, \"\")"),
    "fpga_row_always": (AR, "if (!le.fpga.isEmpty()) rows.add(row(KEY_FPGA", "if (true) rows.add(row(KEY_FPGA"),
    "first_bad_name_wins": (F, 'if (m.matches()) return BIN.matcher(m.group(1)).matches() ? m.group(1) : "";',
                            'if (m.matches()) return m.group(1);'),
    "shown_lower": (F, 'return version.isEmpty() ? "" : "V" + version.substring(1).toUpperCase(Locale.ROOT);',
                    'return version;'),
    # the catalog
    "signature_ignored": (RC, "if (!s.verify(sig)) return null;", "s.verify(sig);"),
    "no_size_cap": (RC, " || json.length > MAX_BYTES", ""),
    "any_kind": (RC, ' || !KIND.equals(o.getString("kind"))', ""),
    "any_schema": (RC, 'o.getInt("schema") != 1 || ', ""),
    "serial_zero_ok": (RC, " || serial <= 0) return null;", ") return null;"),
    "older_replaces": (RC, "(kept == null || fresh.serial >= kept.serial)", "(true)"),
    "same_not_replaced": (RC, "fresh.serial >= kept.serial", "fresh.serial > kept.serial"),
    "any_host": (RC, "return url.startsWith(CDN) && ", "return "),
    "any_file_name": (RC, ' && url.endsWith("/" + name)', ""),
    "dots_ok": (RC, ' && !url.contains("..")', ""),
    "blanks_ok": (RC, " && SAFE_URL.matcher(url).matches()", ""),
    "any_stock": (RC, '!own.equalsIgnoreCase(f.optString("stock"))', "false"),
    "older_file_offered": (RC, " || older(version, own)) return null;", ") return null;"),
    "older_reversed": (RC, "older(version, own)) return null;", "older(own, version)) return null;"),
    "older_as_text": (RC, "        String[] x = a.substring(1).split", "        if (true) return a.compareTo(b) < 0;\n        String[] x = a.substring(1).split"),
    "older_common_parts_only": (RC, "i < Math.max(x.length, y.length)", "i < Math.min(x.length, y.length)"),
    "stock_kept_raw": (RC, "String own = XgimiFacts.firmware(stock);", "String own = stock;"),
    "size_zero_ok": (RC, "version.isEmpty() || size <= 0 || ", "version.isEmpty() || "),
    "no_version_ok": (RC, "if (version.isEmpty() || size <= 0", "if (size <= 0"),
    "odd_catalog_name": (RC, 'return XgimiFacts.BIN.matcher(f).matches() ? f : "";', "return f;"),
    # the screen
    "other_name_offered": (WB, "if (o != null && !o.name.equals(file)) o = null;", ""),
    "catalog_name_first": (WB, "String file = f.usbFile.isEmpty() && catalog != null ? catalog.usbFile(f.model) : f.usbFile;",
                           "String file = catalog != null && !catalog.usbFile(f.model).isEmpty() ? catalog.usbFile(f.model) : f.usbFile;"),
    "qr_always_guide": (WB, "o != null ? o.url : Guide.backToStock(language)", "Guide.backToStock(language)"),
    "guide_only_ru": (GD, 'Arrays.asList("ru", "uk", "be", "kk")', 'Arrays.asList("ru")'),
    "guide_no_section": (GD, 'SECTION = "#stock"', 'SECTION = ""'),
    # the rows
    "rows_without_model": (AR, "Function<RowText, String> texts) {\n        if (f.model.isEmpty()) return Collections.emptyList();", "Function<RowText, String> texts) {"),
    "versions_without_model": (AR, "boolean system) {\n        if (f.model.isEmpty()) return Collections.emptyList();", "boolean system) {"),
    "firmware_row_always": (AR, "if (!f.firmware.isEmpty()) {\n            rows.add(row(KEY_FIRMWARE", "if (true) {\n            rows.add(row(KEY_FIRMWARE"),
    "back_no_screen": (AR, "texts.apply(RowText.BACK), back, true)", "texts.apply(RowText.BACK), back, false)"),
    "back_summary_swapped": (AR, "String back = f.usbFile.isEmpty() ? texts.apply(RowText.BACK_SUMMARY)",
                             "String back = !f.usbFile.isEmpty() ? texts.apply(RowText.BACK_SUMMARY)"),
    "row_key_renamed": (AR, 'KEY_MODEL = "lumen_model_code"', 'KEY_MODEL = "model_code"'),
    # the app side and the wiring
    "provider_text_swapped": (AP, "case MODEL_CODE: return R.string.lumen_about_model_code;",
                              "case MODEL_CODE: return R.string.lumen_about_firmware;"),
    "provider_method_renamed": (AP, 'METHOD_ROWS = "rows"', 'METHOD_ROWS = "list"'),
    "provider_writes": (AP, "public int delete(Uri uri, String selection, String[] selectionArgs) {\n        return 0;",
                        "public int delete(Uri uri, String selection, String[] selectionArgs) {\n        return 1;"),
    "provider_runs_command": (AP, "if (!METHOD_ROWS.equals(method)) return null;",
                              "if (!METHOD_ROWS.equals(method)) return null;\n        try { Runtime.getRuntime().exec(\"true\"); } catch (Exception e) { }"),
    "cache_outside_app": (CS, "File d = new File(c.getFilesDir(), DIR);\n        if (!d.isDirectory()",
                          "File d = new File(\"/data/local/tmp\", DIR);\n        if (!d.isDirectory()"),
    "ru_english": (RU, '<string name="lumen_about_model_code">Код модели</string>',
                   '<string name="lumen_about_model_code">Model code</string>'),
    "ru_semicolon": (RU, '<string name="lumen_about_close">', '<string name="lumen_about_close">;'),
    "g0155_no_module": ("devices/g0155/app/X50Projector.layers", "module    lumen-about\n", ""),
    "hb_provider_closed": ("apps/HbProjector/AndroidManifest.xml",
                           'android:authorities="org.lumen.about"\n            android:directBootAware="false"\n            android:exported="true"',
                           'android:authorities="org.lumen.about"\n            android:directBootAware="false"\n            android:exported="false"'),
    "play6_no_screen": ("apps/Play6Projector/AndroidManifest.xml", 'android:name="org.lumen.about.XgimiSystemActivity"',
                        'android:name="org.lumen.about.XgimiSystemScreen"'),
    "fix_other_method": ("tools/base_patch/tvsettings_about/AboutFragment.java", 'LUMEN_ROWS = "rows"', 'LUMEN_ROWS = "list"'),
    "fix_row_dropped": ("tools/base_patch/patches.tsv", "\ttvsettings_about\t", "\ttvsettings_about_off\t"),
    "dsu_word_on_screen": (RU, '<string name="lumen_about_back_summary">', '<string name="lumen_about_back_summary">DSU '),
}


def run(tree):
    p = subprocess.run(["sh", os.path.join(tree, "apps/core/lumen-about/test/run.sh")], stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, text=True)
    return p.returncode, p.stdout


def main():
    work = tempfile.mkdtemp(prefix="lumen-about-mut-")
    try:
        tree = os.path.join(work, "g")
        for d in COPY:
            src, dst = os.path.join(G, d), os.path.join(tree, d)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if os.path.isdir(src):
                shutil.copytree(src, dst, ignore=shutil.ignore_patterns("__pycache__", "build"))
            else:
                shutil.copy2(src, dst)
        rc, out = run(tree)
        if rc != 0:
            print(out)
            print("run.sh is red without a mutant: fix that first")
            return 1
        alive = []
        for name, (rel, old, new) in MUTANTS.items():
            f = os.path.join(tree, rel)
            src = open(f, encoding="utf-8").read()
            if src.count(old) != 1:
                print(f"BROKEN {name}: anchor found {src.count(old)} times in {rel}")
                alive.append(name)
                continue
            open(f, "w", encoding="utf-8").write(src.replace(old, new))
            try:
                rc, out = run(tree)
            finally:
                open(f, "w", encoding="utf-8").write(src)
            lines = out.splitlines()
            why = next((ln for ln in lines if ln.startswith("FAIL")), "") or \
                next((ln for ln in lines if "error" in ln.lower()), "")
            print(("killed  " if rc else "ALIVE   ") + name + ("   (" + why.strip()[:100] + ")" if rc else ""))
            if not rc:
                alive.append(name)
        print(f"mutants: {len(MUTANTS) - len(alive)}/{len(MUTANTS)} killed" + (f", alive {alive}" if alive else ""))
        return 1 if alive else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
