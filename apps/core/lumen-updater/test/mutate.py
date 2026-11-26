#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""mutate.py: targeted mutants of the shared updater and its model data (apps/core/lumen-updater). Each mutant
changes one line of the module or of a model's Target, runs the tests that own it and must make them fail; the
file is restored after every mutant, also on an error. Run from the tree root:

  python3 apps/core/lumen-updater/test/mutate.py [NAME...]

Prints KILLED / ALIVE per mutant and exits 1 when one stays alive or a mutation no longer applies.
"""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
CORE = "apps/core/lumen-updater/src/org/lumen/updater/"
Z9X = "apps/Z9xUpdater/src/org/lumen/updater/Target.java"
HB = "apps/LumenUpdater/src/org/lumen/updater/Target.java"
P6 = "apps/Play6Updater/src/org/lumen/updater/Target.java"
T_CORE = "sh apps/core/lumen-updater/test/run.sh"
T_HB = "sh apps/LumenUpdater/test/ota/run.sh"
T_Z9X = "sh apps/Z9xUpdater/test/run.sh"
T_P6 = "sh apps/Play6Updater/test/run.sh"
T_HOOKS = "bash apps/LumenDsuInstall/test/hooks_tests.sh"
T_WIRE = "python3 -B tools/devices/devices.py check-wire"
RES = "apps/core/lumen-updater/res/"
T_WORDS = "python3 -B tools/i18n/test/ui_words_test.py"
T_PARITY = "sh apps/core/lumen-updater/test/parity/run.sh"
T_DL = "sh apps/core/lumen-updater/test/download.sh"
# the parity run must turn red with the German consent text gone (only the "key still there" rule catches it):
# green when it does, red when the rewritten-text rule lets a missing text through
T_PARITY_GONE = ("cp " + RES + "values-de/strings.xml /tmp/lumen_mut_de.xml && "
                 "grep -v 'name=.consent_body.>' /tmp/lumen_mut_de.xml > " + RES + "values-de/strings.xml && "
                 "{ sh apps/core/lumen-updater/test/parity/run.sh; r=$?; cp /tmp/lumen_mut_de.xml " + RES
                 + "values-de/strings.xml; [ $r -ne 0 ]; }")

# name, file, old, new, tests (any failing test kills it)
MUTANTS = [
    ("z9x_wire_id", Z9X, '.device("z9x")', '.device("z9y")', [T_CORE]),
    ("z9x_checks", Z9X, ".checks(InstallChecks.Z9X_GATE)", ".checks(InstallChecks.LUMEN_GATE)", [T_CORE]),
    ("z9x_versions", Z9X, ".versions(VersionScheme.LEGACY)", ".versions(VersionScheme.O1)", [T_CORE]),
    ("z9x_beta_file", Z9X, '"update-public-beta.json")', '"update-public-test.json")', [T_CORE, T_Z9X]),
    ("z9x_dsu", Z9X, ".dsuHandover(false)", ".dsuHandover(true)", [T_CORE, T_HOOKS]),
    ("hb_rule", HB, ".channels(ChannelRule.PAIR)", ".channels(ChannelRule.FIXED)", [T_CORE, T_HB]),
    ("hb_dsu", HB, ".dsuHandover(true)", ".dsuHandover(false)", [T_CORE, T_HOOKS]),
    ("hb_hide", HB, ".hideWithoutDevice(true)", ".hideWithoutDevice(false)", [T_CORE]),
    ("hb_default_gate", HB, "InstallChecks.LUMEN_GATE)", "InstallChecks.Z9X_GATE)", [T_CORE, T_HB]),
    ("hb_versions", HB, ".versions(VersionScheme.O1)", ".versions(VersionScheme.LEGACY)", [T_CORE, T_HB]),
    ("p6_install", P6, ".install(InstallKind.RECOVERY)", ".install(InstallKind.UPDATE_ENGINE)", [T_CORE]),
    ("p6_dir", P6, ".dir(OtaDir.CACHE)", ".dir(OtaDir.APP_DATA)", [T_CORE]),
    ("p6_wire_id", P6, '.device("play6")', '.device("play7")', [T_CORE, T_P6, T_WIRE]),
    ("kind_skip", CORE + "InstallKind.java", "RECOVERY(false, false, true, true)", "RECOVERY(false, false, false, true)",
     [T_CORE]),
    ("kind_payload", CORE + "InstallKind.java", "UPDATE_ENGINE(true, true, false, false)",
     "UPDATE_ENGINE(false, true, false, false)", [T_CORE]),
    ("kind_meta_named", CORE + "InstallKind.java", "return payloadFacts || !p.metadataUrl.isEmpty();",
     "return payloadFacts;", [T_CORE]),
    ("pair_beta_suffix", CORE + "ChannelRule.java", 'beta = "update-" + d + "-test.json"',
     'beta = "update-" + d + "-beta.json"', [T_CORE, T_HB]),
    ("gate_name", CORE + "InstallChecks.java", 'LUMEN_GATE("lumen_ota",', 'LUMEN_GATE("lumen",', [T_HB]),
    ("first_refusal", CORE + "Preflight.java", "            if (b != null) return b;\n        }\n        return null;",
     "            if (b != null) continue;\n        }\n        return null;", [T_CORE, T_HB]),
    ("vbmeta_bit", CORE + "Preflight.java", "if ((flags & 2) == 0)", "if ((flags & 1) == 0)", [T_CORE, T_HB]),
    ("guide_format", CORE + "UpdateTarget.java", 'return g + "/" + lang + ".md#" + section;',
     'return g + "/" + lang + "#" + section;', [T_CORE, T_HB]),
    ("dsu_guard", CORE + "UpdateService.java", "if (Ota.T.dsuHandover && Dsu.running())", "if (Dsu.running())",
     [T_HOOKS]),
    ("pair_needs_wire_id", CORE + "ChannelRule.java", "return !t.device().isEmpty() && u.startsWith(\"https://\") ? u : \"\";",
     "return u.startsWith(\"https://\") ? u : \"\";", [T_HB]),
    ("accepts_any", CORE + "Channels.java", "if (want.equals(f)) return true;", "if (!f.isEmpty()) return true;",
     [T_HB]),
    ("vendor_check", CORE + "Preflight.java", "!vendorIncremental.contains(vendor)", "vendorIncremental.isEmpty()",
     [T_HB]),
    ("recovery_drop", CORE + "ViaRecovery.java", "{ return h.busy(); }", "{ return h.repeat(ServiceJob.INSTALL); }",
     [T_CORE]),
    # STORAGE: the vendor's Virtual A/B compression must be what the model's payloads use (X50 on, HB off)
    ("vabc_ignored", CORE + "Preflight.java", "if (on != Ota.T.compressedSnapshots())", "if (on)", [T_HB]),
    ("vabc_one_way", CORE + "Preflight.java", "if (on != Ota.T.compressedSnapshots())",
     "if (on && !Ota.T.compressedSnapshots())", [T_HB]),
    ("vabc_arg", CORE + "Preflight.java", 'on ? "compression on" : "compression off"', '"compression on"', [T_HB]),
    ("vabc_any_device", CORE + "UpdateTarget.java", "return compressedSnapshots.contains(device());",
     "return !compressedSnapshots.isEmpty();", [T_HB]),
    ("vabc_builder", CORE + "UpdateTarget.java", "compressedSnapshots = new TreeSet<>(Arrays.asList(wireIds));",
     "compressedSnapshots = new TreeSet<>();", [T_CORE, T_HB]),
    ("vabc_no_x50", HB, '.compressedSnapshots("x50", "x50max")', '.compressedSnapshots("x50max")', [T_CORE, T_HB]),
    ("vabc_hb_too", HB, '.compressedSnapshots("x50", "x50max")', '.compressedSnapshots("hb", "x50", "x50max")',
     [T_CORE, T_HB]),
    ("vabc_conf_side", "apps/LumenUpdater/test/ota/run.sh", 'if c["vabc"] == "yes":', 'if c["vabc"] == "no":', [T_HB]),
    # PLAIN WORDS: no DSU, vbmeta or slot on screen, in any language (tools/i18n/ui_words.py), and the parity of the
    # updaters accepts a rewritten text only while the key keeps a text in that folder (test/parity/rewritten.txt)
    ("words_consent_slot", RES + "values/strings.xml", "copy the projector’s own XGIMI system files to the update",
     "copy the projector’s own XGIMI system files to the other slot", [T_WORDS]),
    ("words_consent_vbmeta_ru", RES + "values-ru/strings.xml", "Ничего никуда не отправляется.",
     "Ничего никуда не отправляется, vbmeta не записывается.", [T_WORDS]),
    ("words_block_slot_ja", RES + "values-ja/strings_lumen_ota.xml", "プロジェクターは再起動を待っています。",
     "プロジェクターは次にシステムスロット B から起動します。", [T_WORDS]),
    ("words_block_slot_zh", RES + "values-zh-rCN/strings_lumen_ota.xml", "投影仪正在等待重启。",
     "投影仪下次将从系统槽位 B 启动。", [T_WORDS]),
    ("words_block_dsu_de", RES + "values-de/strings_lumen_ota.xml",
     "Updates lassen sich in dieser Version nicht sicher installieren.",
     "Updates lassen sich in dieser DSU-Version nicht sicher installieren.", [T_WORDS]),
    ("words_no_russian", "tools/i18n/ui_words.py", '("slot (ru, uk, be, kk)", re.compile(r"слот", re.I)),', "",
     [T_WORDS]),
    ("words_no_arrays", "tools/i18n/ui_words.py", 'elif el.tag in ("plurals", "string-array"):',
     'elif el.tag in ("plurals",):', [T_WORDS]),
    ("words_refs_any", "tools/i18n/ui_words.py", 'if REF.fullmatch(t):', 'if REF.match(t):', [T_WORDS]),
    ("parity_rewrite_any", "apps/core/lumen-updater/test/parity/run.sh",
     "if l and not (m and m.group(2) in keys and m.group(1) in now):", "if l and not (m and m.group(2) in keys):",
     [T_PARITY_GONE]),
    ("parity_rewrite_unlisted", "apps/core/lumen-updater/test/parity/rewritten.txt", "\nconsent_body\n", "\n",
     [T_PARITY]),
    ("consent_args_back", RES + "values/strings.xml", "Your calibration and settings stay untouched.",
     "Your calibration and settings stay untouched (%1$s).", [T_CORE]),
    # CHANNEL BY BUILD TYPE, VERSION FIRST, FIRMWARE TEXT (updater-channel-fix): a beta build reads the beta channel
    # when never chosen, a manifest with nothing newer is up to date before any requirement, the firmware refusal
    # names the own and the supported firmware in plain words
    ("beta_default_off", CORE + "Ota.java", "return choice != null ? choice : betaBuild();",
     "return choice != null ? choice : false;", [T_HB, T_Z9X]),
    ("beta_default_url", CORE + "Ota.java", "return choice != null ? choice : betaBuild();",
     "return choice != null ? choice : Ota.channelFile(image).endsWith(\"-test.json\");", [T_HB, T_Z9X]),
    ("prerelease_no_rc", CORE + "VersionScheme.java", '-(beta|rc)[1-9][0-9]?"', '-(beta)[1-9][0-9]?"', [T_HB, T_Z9X]),
    ("prerelease_any_dash", CORE + "VersionScheme.java", '.matches(".*[0-9]-(beta|rc)[1-9][0-9]?")',
     '.contains("-")', [T_HB]),
    ("vendor_before_version", CORE + "Preflight.java",
     "        if (versionCode <= cur) return new Block(\"uptodate\", \"\");\n",
     "        if (!vendorIncremental.isEmpty() && !vendorIncremental.contains(Ota.prop(\"ro.vendor.build.version.incremental\")))"
     " return new Block(\"blk_vendor\", \"\");\n        if (versionCode <= cur) return new Block(\"uptodate\", \"\");\n",
     [T_HB]),
    ("channel_before_version", CORE + "Preflight.java",
     "        if (versionCode <= cur) return new Block(\"uptodate\", \"\");\n",
     "        if (!Channels.accepts(channel)) return new Block(\"blk_channel\", channel);\n"
     "        if (versionCode <= cur) return new Block(\"uptodate\", \"\");\n", [T_HB, T_Z9X]),
    ("vendor_arg_swapped", CORE + "Preflight.java", 'args(vendor.isEmpty() ? "?" : vendor, String.join(", ", vendorIncremental))',
     'args(String.join(", ", vendorIncremental), vendor.isEmpty() ? "?" : vendor)', [T_HB]),
    ("format_no_pad", CORE + "Preflight.java", 'out[i] = i < p.length ? p[i] : "?";', "out[i] = i < p.length ? p[i] : \"\";",
     [T_HB]),
    ("firmware_body_one_arg", RES + "values-fr/strings.xml", "Elle fonctionne avec le firmware %2$s.",
     "Elle fonctionne avec le firmware.", [T_CORE]),
    ("firmware_body_dash", RES + "values/strings.xml", "Nothing changes on your projector.",
     "Nothing changes on your projector \u2014 promise.", [T_CORE]),
    ("firmware_body_slot_ru", RES + "values-ru/strings.xml", "На проекторе ничего не изменится.",
     "Слот проектора не изменится.", [T_CORE]),
    # PACKAGE DOWNLOAD: the sha256 pass and the bad file (the download itself: apps/core/lumen-download/test/mutate.py)
    ("dl_no_hash_retry", CORE + "PackageDownload.java", "static final int HASH_RETRIES = 1;",
     "static final int HASH_RETRIES = 0;", [T_DL]),
    ("dl_keep_bad_file", CORE + "PackageDownload.java", "            ParallelDownload.discard(dst);\n", "", [T_DL]),
    # always the newest build (Newest, NewestTest against the fake server, the wiring checks of test/run.sh)
    ("newest_stale_copy", CORE + "Newest.java", "if (saved != null && m.versionCode < saved.versionCode) {",
     "if (saved != null && m.versionCode < 0) {", [T_CORE]),
    ("newest_replace_downloading", CORE + "Newest.java", "} else if (before == UpdateState.DOWNLOADING) {",
     "} else if (before == UpdateState.ERROR) {", [T_CORE]),
    ("newest_replace_deletes", CORE + "Newest.java", """            } else {
                o.deleteFiles();
                o.setOffer(m.json, p.json, usb, usbDir);""", """            } else {
                o.setOffer(m.json, p.json, usb, usbDir);""", [T_CORE]),
    ("newest_no_checking_while_dl", CORE + "Newest.java", "if (before != UpdateState.DOWNLOADING) o.setState(UpdateState.CHECKING);",
     "o.setState(UpdateState.CHECKING);", [T_CORE]),
    ("newest_busy_downloading", CORE + "Newest.java", "if (before == UpdateState.INSTALLING || before == UpdateState.READY) return",
     "if (before == UpdateState.INSTALLING || before == UpdateState.READY || before == UpdateState.DOWNLOADING) return",
     [T_CORE]),
    ("newest_pair_once", CORE + "Newest.java", "static final int PAIR_TRIES = 3;", "static final int PAIR_TRIES = 1;", [T_CORE]),
    ("newest_failed_hides", CORE + "Newest.java", "            if (keeps(o, before)) o.setState(before);\n            else o.setError(res, \"\");",
     "            o.setError(res, \"\");", [T_CORE]),
    ("newest_404_idle", CORE + "Newest.java", "o.setState(keeps(o, before) ? before : UpdateState.IDLE);", "o.setState(UpdateState.IDLE);", [T_CORE]),
    ("newest_drop_channel", CORE + "Newest.java", "boolean channel = saved != null && !Channels.accepts(saved.channel);",
     "boolean channel = false;", [T_CORE]),
    ("newest_drop_equal", CORE + "Outcome.java", "return offered >= 0 && offered <= current;",
     "return offered >= 0 && offered < current;", [T_CORE]),
    ("newest_superseded", CORE + "Newest.java", "return !working.equals(o.packageJson());", "return false;", [T_CORE]),
    ("newest_commit_superseded", CORE + "Newest.java", "                if (!superseded(pkg, o)) {\n                    if (ok)",
     "                if (true) {\n                    if (ok)", [T_CORE]),
    ("newest_before_install", CORE + "Newest.java", "            check.run();\n            UpdateState s = o.state();",
     "            UpdateState s = o.state();", [T_CORE]),
    ("newest_recheck_gap", CORE + "Newest.java", "if (!start && !gap && nowMs - last < EVERY_MS && nowMs >= last) return false;",
     "if (!start && nowMs - last < EVERY_MS && nowMs >= last) return false;", [T_CORE]),
    ("newest_recheck_start", CORE + "Newest.java", "if (!start && !gap && nowMs - last < EVERY_MS && nowMs >= last) return false;",
     "if (!gap && nowMs - last < EVERY_MS && nowMs >= last) return false;", [T_CORE]),
    ("newest_recheck_interval", CORE + "Newest.java", "if (!start && !gap && nowMs - last < EVERY_MS && nowMs >= last) return false;",
     "if (!start && !gap) return false;", [T_CORE]),
    ("newest_recheck_offline", CORE + "Newest.java", "if (!online) return false;", "if (false) return false;", [T_CORE]),
    ("keep_available", CORE + "Outcome.java", 'return "AVAILABLE".equals(before) || "DOWNLOADING".equals(before)',
     'return "DOWNLOADING".equals(before)', [T_CORE]),
    ("fresh_query", CORE + "Http.java", "HttpURLConnection c = open(fresh(url));", "HttpURLConnection c = open(url);", [T_CORE]),
    ("fresh_pragma", CORE + "Http.java", 'c.setRequestProperty("Pragma", "no-cache");', "", [T_CORE]),
    ("check_fresh", CORE + "Checker.java", "CheckResult r = Newest.check(st, urls, Http::fetchFresh,", "CheckResult r = Newest.check(st, urls, Http::fetch,",
     [T_CORE]),
    ("usb_kept", CORE + "Checker.java", "if (r.kept) return r;", "", [T_CORE]),
    ("svc_install_newest", CORE + "UpdateService.java", "if (newest()) install();", "install();", [T_CORE]),
    ("svc_dl_install_newest", CORE + "UpdateService.java", "if (download() && then && newest()) install();",
     "if (download() && then) install();", [T_CORE]),
    ("svc_superseded", CORE + "UpdateService.java", "return jobs.stopRequested() || Newest.superseded(p.json, st); }",
     "return jobs.stopRequested(); }", [T_CORE]),
    ("svc_recheck", CORE + "UpdateService.java", "Checker.check(UpdateService.this);", ";", [T_CORE]),
    ("job_paused_first", CORE + "CheckJob.java", "        // the check first, also for a paused download: a newer build replaces it before anything is resumed\n        CheckResult r = Checker.check(this);",
     "        if (st.state() == UpdateState.PAUSED) UpdateService.start(this, UpdateService.ACTION_DOWNLOAD, false);\n        CheckResult r = Checker.check(this);", [T_CORE]),
    ("job_period", CORE + "CheckJob.java", ".setPeriodic(CHECK_EVERY_MS, CHECK_FLEX_MS)", ".setPeriodic(24 * 3600 * 1000L, 6 * 3600 * 1000L)",
     [T_CORE]),
    ("screen_every_open", CORE + "UpdaterActivity.java", "} else if (s != UpdateState.READY && s != UpdateState.CHECKING && autoCheckDue()) {",
     "} else if (s == UpdateState.IDLE && autoCheckDue()) {", [T_CORE]),
]


def run(cmd):
    r = subprocess.run(cmd, shell=True, cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return r.returncode


def main(names):
    alive, broken, killed = [], [], 0
    for name, path, old, new, tests in MUTANTS:
        if names and name not in names:
            continue
        p = os.path.join(ROOT, path)
        src = open(p, encoding="utf-8").read()
        if src.count(old) != 1:
            print("BROKEN %s: %s does not hold the line once" % (name, path))
            broken.append(name)
            continue
        try:
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            by = next((t for t in tests if run(t) != 0), None)
        finally:
            open(p, "w", encoding="utf-8").write(src)
        if by:
            killed += 1
            print("KILLED %s by %s" % (name, by.split()[1]))
        else:
            alive.append(name)
            print("ALIVE  %s" % name)
        sys.stdout.flush()
    print("updater mutants killed=%d alive=%d broken=%d" % (killed, len(alive), len(broken)))
    return 1 if alive or broken else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
