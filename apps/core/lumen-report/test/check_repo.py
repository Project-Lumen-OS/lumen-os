#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Repository checks of problem reports (run by test/run.sh): the texts and the wiring of every projector app.
  1. strings: every res/values*/lumen_strings_report.xml has the keys of the English base, the same placeholders,
     and plain text (no semicolon, no em or en dash: the plain text rule of user texts); 18 files (17 languages + pt-rBR);
  2. every projector app recipe (devices/*/app/*Projector.layers) names the module, and lumen-menu depends on it;
  3. every projector app manifest declares the screen, the job service (BIND_JOB_SERVICE), the boot and DropBox
     receiver, the provider that starts the unexpected-stop watch at every process start and the permissions the
     collector and the watch need;
  4. every menu profile names settings.report, the menu opens it as a module screen (no adapter names it);
  5. consent wiring of the Android side: automatic reports go out only through AutoPolicy.allowed, the sender drops
     automatic reports of a switch turned off, the switch row writes ReportPrefs.setAutoOn;
  6. nothing of the old Z9X-only package org.lumen.projector.report is left;
  7. the unexpected-stop watch: the broadcasts it listens to, the standby contract of the projector apps that send
     STANDBY_CHANGED, the standby screen and HDMI viewer it names.
    python3 apps/core/lumen-report/test/check_repo.py GSI_ROOT"""
import glob
import json
import os
import re
import sys

G = sys.argv[1]
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "tools", "devices"))
import menu_layers  # noqa: E402  (menu profiles have one reader, devices/README.md)
sys.path.insert(0, os.path.join(G, "tools", "devices"))
from assemble_app import app_dir  # noqa: E402  apps/<app>, or its renamed folder (apps/app_dirs.tsv)
M = os.path.join(G, "apps/core/lumen-report")
bad = []


def fail(msg):
    bad.append(msg)
    print("FAIL " + msg)


def read(p):
    return menu_layers.read_text(p)


STR = re.compile(r'<string name="([a-z0-9_]+)">(.*?)</string>', re.S)
PH = re.compile(r"%\d\$[sd]")
base = dict(STR.findall(read(os.path.join(M, "res/values/lumen_strings_report.xml"))))
files = sorted(glob.glob(os.path.join(M, "res/values*/lumen_strings_report.xml")))
if len(files) != 18:
    fail("strings: %d locale files, want 18" % len(files))
for f in files:
    loc = os.path.basename(os.path.dirname(f))
    s = dict(STR.findall(read(f)))
    if set(s) != set(base):
        fail("strings %s: keys differ: %s" % (loc, sorted(set(s) ^ set(base))))
    for k, v in s.items():
        if not k.startswith("lumen_report_"):
            fail("strings %s: %s lacks the module prefix" % (loc, k))
        if k in base and sorted(PH.findall(v)) != sorted(PH.findall(base[k])):
            fail("strings %s: %s placeholders differ" % (loc, k))
        if re.search("[;\u2014\u2013]", v):
            fail("strings %s: %s has a semicolon or a dash" % (loc, k))
        if not v.strip():
            fail("strings %s: %s is empty" % (loc, k))
print("ok   strings: %d files, %d keys each" % (len(files), len(base)))

import assemble_app  # noqa: E402  (the recipes each model builds, its own or its family's: devices/README.md)
built = [(c, p) for c, a, p in assemble_app.all_recipes(G) if a.endswith("Projector")]
recipes = sorted({p for _, p in built})
for r in recipes:
    if not re.search(r"^module\s+lumen-report\s*$", read(r), re.M):
        fail("recipe %s does not name lumen-report" % os.path.relpath(r, G))
if len({c for c, _ in built}) < 6:
    fail("only %d models build a projector app" % len({c for c, _ in built}))
if not re.search(r"^depends=.*\blumen-report\b", read(os.path.join(G, "apps/core/lumen-menu/module.conf")), re.M):
    fail("lumen-menu does not depend on lumen-report")
print("ok   recipes: %d projector apps carry the module" % len(recipes))

PERMS = ["INTERNET", "READ_LOGS", "DUMP", "PACKAGE_USAGE_STATS", "ACCESS_WIFI_STATE", "ACCESS_NETWORK_STATE",
         "RECEIVE_BOOT_COMPLETED", "REAL_GET_TASKS"]
apps = set()
for r in recipes:
    apps.add(re.search(r"^app\s+(\S+)", read(r), re.M).group(1))
import patch_file  # noqa: E402  (a model's manifest as a patch of the core one: assemble_app.py rule 10)


def manifest_of(a):
    """The app's manifest: its folder's, or the core one plus the model's patch (devices/<c>/app/<a>.manifest.diff)."""
    own = os.path.join(G, app_dir(G, a), "AndroidManifest.xml")
    if os.path.isfile(own):
        return read(own)
    for r in recipes:                  # a model's whole manifest as a recipe file row (Play 6, since apps/Play6Projector is gone)
        if re.search(r"^app\s+%s\s*$" % re.escape(a), read(r), re.M):
            m = re.search(r"^file\s+(\S+)\s+AndroidManifest\.xml\s*$", read(r), re.M)
            if m:
                return read(os.path.join(G, m.group(1)))
    diff = sorted(glob.glob(os.path.join(G, "devices", "*", "app", a + ".manifest.diff")))[0]
    return patch_file.apply(read(os.path.join(G, "apps/LumenProjector/core/AndroidManifest.xml")), read(diff))


report_pkgs = set()
for a in sorted(apps):
    man = manifest_of(a)
    for p in PERMS:
        if 'android:name="android.permission.%s"' % p not in man:
            fail("%s manifest lacks %s" % (a, p))
    if 'android:name="org.lumen.report.ReportActivity"' not in man:
        fail("%s manifest lacks the report screen" % a)
    svc = re.search(r'<service\s[^>]*android:name="org.lumen.report.ReportService"[^>]*>', man, re.S)
    if not svc or 'android:permission="android.permission.BIND_JOB_SERVICE"' not in svc.group(0):
        fail("%s manifest lacks the job service with BIND_JOB_SERVICE" % a)
    rcv = re.search(r'<receiver\s[^>]*android:name="org.lumen.report.ReportBoot".*?</receiver>', man, re.S)
    if not rcv or "android.intent.action.BOOT_COMPLETED" not in rcv.group(0) \
            or "android.intent.action.DROPBOX_ENTRY_ADDED" not in rcv.group(0):
        fail("%s manifest lacks the boot and DropBox receiver" % a)
    pkg = re.search(r'<manifest\s[^>]*package="([^"]+)"', man, re.S).group(1)
    report_pkgs.add(pkg)
    prov = re.search(r'<provider\s[^>]*android:name="org.lumen.report.AliveStart"[^>]*>', man, re.S)
    if not prov or 'android:exported="false"' not in prov.group(0) \
            or 'android:directBootAware="false"' not in prov.group(0) \
            or 'android:authorities="%s.alive"' % pkg not in prov.group(0):
        fail("%s manifest lacks the AliveStart provider (not exported, not direct boot aware, %s.alive)" % (a, pkg))
print("ok   manifests: %s" % " ".join(sorted(apps)))

profiles = sorted(glob.glob(os.path.join(G, "devices/*/menu.json")))
for p in profiles:
    j = json.loads(read(p))
    if "settings.report" not in j.get("features", {}):
        fail("%s does not name settings.report" % os.path.relpath(p, G))
    ad = os.path.join(G, j["adapter"])
    if os.path.isfile(ad) and "SETTINGS_REPORT" in read(ad):
        fail("%s: the adapter handles settings.report (the menu opens module screens itself)" % j["device"])
MC = read(os.path.join(G, "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java"))
# ModuleScreens.available takes the profile since hotspot (5b81d69f): a module row may read its profile options
for w in ["if (MenuSpec.module(f)) return profile.has(f) && ModuleScreens.available(app, profile, f);",
          "closeThen(() -> { if (!ModuleScreens.open(app, f)) host.toast(R.string.lumen_m_toast_failed); });"]:
    if w not in MC:
        fail("MenuController lost: " + w)
if "ReportScreen.open(c)" not in read(os.path.join(G, "apps/core/lumen-menu/src/org/lumen/projector/panel/ModuleScreens.java")):
    fail("ModuleScreens does not open the report screen")
print("ok   menu: %d profiles name settings.report, opened as a module screen" % len(profiles))

S = os.path.join(M, "src/org/lumen/report")
wiring = [
    ("AutoReports.java", "boolean allowed = AutoPolicy.allowed(ReportConfig.enabled(), ReportPrefs.autoOn(c), ReportPrefs.setupDone(c));"),
    ("AutoReports.java", "List<Incident> found = AutoPolicy.dropBoxScan(allowed, since, now) ? find(c, since, now, boot) : new ArrayList<>();"),
    ("AutoReports.java", "AliveWatch.awaitCheck();"),
    ("AutoReports.java", "Incident stop = AliveWatch.takeStop(c);"),
    ("AutoReports.java", "if (AutoPolicy.withStop(allowed, stop)) found.add(stop);"),
    ("AutoReports.java", "List<Incident> pick = AutoPolicy.pick(found, ReportPrefs.autoLog(c), now);"),
    ("AutoReports.java", "ReportPrefs.setAutoLog(c, AutoPolicy.record(ReportPrefs.autoLog(c), pick, now));"),
    ("ReportSender.java", "if (urls.isEmpty() || (e.kind.automatic() && !ReportPrefs.autoOn(c))) {"),
    ("ReportSender.java", "if (x.failure.retry()) q.failed(e, now);"),
    ("ReportSender.java", "String code = ReportUploader.uploadAny(urls, u -> new ReportUploader().upload(u, e.zip,"),
    ("ReportJob.java", "String c = ReportUploader.uploadAny(urls, u -> {"),
    ("ReportService.java", "ReportBoot.listen(app);"),
    ("ReportBoot.java", "new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED), Context.RECEIVER_NOT_EXPORTED);"),
    ("ReportActivity.java", "ReportPrefs.setAutoOn(this, on);"),
    ("ReportPrefs.java", "return prefs(c).getBoolean(K_AUTO, AutoPolicy.DEFAULT_ON);"),
    ("ReportPrefs.java", "if (!on) e.remove(K_AUTO_LOG).remove(K_INSTALL_ID);"),
    ("ReportCollector.java", "LogcatFilter f = new LogcatFilter(imageAppIds, android.os.Process.myUid(), manual);"),
    ("ReportCollector.java", "if (!manual && !bootRecord && IncidentScan.of(tag, f[0], text, 0, now) == null) continue;"),
    ("ReportCollector.java", "boolean bootRecord = stop != null && IncidentScan.bootRecord(tag, f[0], bootStart);"),
    ("ReportCollector.java", "final String[] tags = manual ? DROPBOX_TAGS : IncidentScan.autoTags(stop != null);"),
    ("ReportCollector.java", 'parts.add(ReportZip.Part.text("unexpected_stop.txt", scrubber.scrubText(stop.detail)));'),
    ("ReportCollector.java", "if (!PropFilter.keep(line)) continue;"),
    ("ReportCollector.java", "long max = manual ? ReportConfig.MAX_ZIP_BYTES : ReportConfig.AUTO_MAX_ZIP_BYTES;"),
    ("ReportConfig.java", "return ReportUrl.choose(prop(PROP_URL_DEBUG), prop(PROP_URL));"),
    ("ReportJob.java", "if (z != null && f.retry() && queue(app, z)) {"),
    ("ReportBoot.java", "AliveWatch.start(c);"),
    ("ReportService.java", "AliveWatch.start(app);"),
    ("AliveStart.java", "AliveWatch.start(getContext());"),
    ("AliveState.java", "static final long BEAT_MS = 10 * 60_000L;"),
    ("AliveWatch.java", "worker.post(this::check);"),
    ("AliveWatch.java", "sys.addAction(Intent.ACTION_SHUTDOWN);"),
    ("AliveWatch.java", "sys.addAction(Intent.ACTION_REBOOT);"),
    ("AliveWatch.java", "sys.addAction(Intent.ACTION_SCREEN_OFF);"),
    ("AliveWatch.java", "sys.addAction(Intent.ACTION_SCREEN_ON);"),
    ("AliveWatch.java", "sys.addAction(Intent.ACTION_DREAMING_STARTED);"),
    ("AliveWatch.java", "sys.addAction(Intent.ACTION_DREAMING_STOPPED);"),
    ("AliveWatch.java", "app.registerReceiver(r, sys, null, worker, Context.RECEIVER_EXPORTED);"),
    ("AliveWatch.java", "app.registerReceiver(r, new IntentFilter(ACTION_STANDBY_CHANGED), PERMISSION_STANDBY, worker,"),
    ("AliveWatch.java", "return i.getBooleanExtra(EXTRA_STANDBY, false) ? AliveState.STANDBY_ON : AliveState.STANDBY_OFF;"),
    ("AliveWatch.java", "if (Alive.bootCheck(marker, pending, boot, up, System.currentTimeMillis(), reasons)) {"),
    ("AliveWatch.java", "if (a != null && !AliveFile.write(marker, a.line())) Log.w("),
    ("AliveWatch.java", "if (AliveState.beats(interactive)) worker.postDelayed(beat, AliveState.BEAT_MS);"),
    ("ReportCollector.java", "            miracastDiag();\n            hdmi();\n"),
    ("ReportCollector.java", 'parts.add(ReportZip.Part.text("hdmi.txt", scrubber.scrubText(HdmiDump.text(cec, journal, audio))));'),
    ("ReportCollector.java", 'String cec = dumps.get("hdmi_control"), audio = dumps.get("audio"), power = dumps.get("power");'),
    ("ReportCollector.java", 'parts.add(ReportZip.Part.text("power.txt", scrubber.scrubText(PowerDump.text(power))));'),
    ("ReportCollector.java", "for (String s : RootLogs.DUMPSYS) dumps.put(s, readText(new File(root, s + \".txt\"), HdmiDump.CAP * 4));"),
]
for f, w in wiring:
    if w not in read(os.path.join(S, f)):
        fail("%s lost: %s" % (f, w))
print("ok   consent wiring: %d lines" % len(wiring))

left = []
for root, _, fs in os.walk(os.path.join(G, "apps")):
    for f in fs:
        if f.endswith((".java", ".xml", ".layers")):
            p = os.path.join(root, f)
            if "org.lumen.projector.report" in read(p) or "/projector/report/" in p:
                left.append(os.path.relpath(p, G))
if left:
    fail("old Z9X report package still referenced: %s" % left[:5])
print("ok   no old org.lumen.projector.report left" if not left else "")

# 7. the unexpected-stop watch against the projector apps that send STANDBY_CHANGED and the activities it names
AW = read(os.path.join(S, "AliveWatch.java"))
AL = read(os.path.join(S, "Alive.java"))
contract = {k: re.search(r'%s = "([^"]+)"' % k, AW).group(1) for k in ("ACTION_STANDBY_CHANGED", "EXTRA_STANDBY", "PERMISSION_STANDBY")}
senders = ["apps/LumenProjector/core/src/org/lumen/projector/power/StandbyController.java",
           "apps/LumenProjector/core/src/org/lumen/projector/power/LampStandby.java"]
for f in senders:
    src = read(os.path.join(G, f))
    for k, v in (("ACTION_STANDBY_CHANGED", contract["ACTION_STANDBY_CHANGED"]), ("EXTRA_STANDBY", contract["EXTRA_STANDBY"]),
                 ("PERMISSION_STANDBY_STATE", contract["PERMISSION_STANDBY"])):
        if '%s = "%s"' % (k, v) not in src:
            fail("%s: %s is not %s (the watch would miss lamp-only standby)" % (f, k, v))
    if "Intent.FLAG_RECEIVER_REGISTERED_ONLY" not in src:
        fail("%s: STANDBY_CHANGED is no longer for registered receivers" % f)
for k, path in (("STANDBY_ACTIVITY", "apps/%s/src/org/lumen/projector/power/StandbyActivity.java"),
                ("HDMI_VIEWER", "apps/LumenTvInput/src/org/lumen/tvinput/PassthroughActivity.java")):
    comp = re.search(r'%s = "([^"/]+)/([^"]+)"' % k, AL)
    pkg, cls = comp.group(1), comp.group(2)
    for app in (["LumenProjector/core"] if k == "STANDBY_ACTIVITY" else [None]):   # one StandbyActivity since step 2b
        p = os.path.join(G, path % app if app else path)
        if not os.path.isfile(p) or ("class " + cls.rsplit(".", 1)[1]) not in read(p) \
                or ("package " + cls.rsplit(".", 1)[0] + ";") not in read(p):
            fail("Alive.%s names %s/%s, not found at %s" % (k, pkg, cls, os.path.relpath(p, G)))
    man = os.path.join(G, "apps", "LumenTvInput" if k == "HDMI_VIEWER" else "LumenProjector/core", "AndroidManifest.xml")
    if 'package="%s"' % pkg not in read(man):
        fail("Alive.%s: package %s is not the one of %s" % (k, pkg, os.path.relpath(man, G)))
print("ok   unexpected-stop watch: standby contract of %d senders, standby screen and HDMI viewer" % len(senders))
# 8. the image's root log helper (image/lumen_report.sh): reports never ask for log access, every report app may read
#    the helper's files, every image stages it (tools/build/build.py COMMON_FILES; build.py check: every recipe places it)
HS = read(os.path.join(M, "image/lumen_report.sh"))
hosts = re.search(r'^HOSTS="([^"]+)"', HS, re.M).group(1).split()
for pkg in sorted(report_pkgs):
    if pkg not in hosts:
        fail("lumen_report.sh HOSTS lacks the report app %s (its group could not read the logs)" % pkg)
if 'MAX_TOKEN = 24' not in read(os.path.join(S, "RootLogs.java")) or '[ "${#1}" -le 24 ]' not in HS:
    fail("RootLogs.valid and lumen_report.sh valid() no longer share the 24 character rule")
for root, _, fs in os.walk(M):
    for f in fs:
        if f.endswith((".java", ".xml")) and "/test/" not in os.path.join(root, f):
            t = read(os.path.join(root, f))
            for w in ("lumen_report_log_hint", "lumen_report_inc_hdmi", "LOGCAT_TIMEOUT_MS", "access to device logs",
                      "serviceDump", "dumpAsync"):
                if w in t:
                    fail("%s: %s (reports never ask for log access)" % (os.path.relpath(os.path.join(root, f), G), w))
RC = read(os.path.join(S, "ReportCollector.java"))
for w in ["boolean root = rootLogs(since);",
          "if (a == RootLogs.Answer.OK && RootLogs.fresh(root, token)) {",
          "            } finally {\n                RootLogs.ack(PROPS, token);\n            }",
          'filtered("logcat.txt", ReportConfig.CAP_LOGCAT, new File(root, RootLogs.LOGCAT), true);',
          "LogcatFilter f = new LogcatFilter(imageAppIds, android.os.Process.myUid(), manual);",
          # the CEC journal: fed with every kept line of logcat.txt, its text in hdmi.txt with the unit's log.tag.HDMI
          "                if (LOGCAT.equals(name)) cec.add(kept);",
          "String journal = this.cec.text(ReportConfig.prop(CecJournal.TAG_PROP));",
          "HdmiDump.text(cec, journal, audio)"]:
    if w not in RC:
        fail("ReportCollector lost: " + w.strip())
if RC.index("boolean root = rootLogs(since);") > RC.index("logcat(\"logcat_kernel.txt\""):
    fail("ReportCollector runs its own logcat before it asks the log helper")
BP = read(os.path.join(G, "tools/build/build.py"))
for f in ("apps/core/lumen-report/image/lumen_report.rc", "apps/core/lumen-report/image/lumen_report.sh"):
    if '"%s"' % f not in BP:
        fail("tools/build/build.py COMMON_FILES lacks %s" % f)
print("ok   log helper: %d report apps may read it, no log access dialog path, staged for every image" % len(report_pkgs))
sys.exit(1 if bad else 0)
