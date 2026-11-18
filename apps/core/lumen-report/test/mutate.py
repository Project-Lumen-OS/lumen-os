#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutants of problem reports (apps/core/lumen-report) that test/run.sh must kill: redaction rules, which incidents
are reported, consent and limits of automatic reports, the queue, the address, and the consent wiring of the Android
side, the unexpected stop (Alive, AliveState, AliveFile and the watch wiring). Mutates the files IN PLACE and
restores them: run it on a frozen snapshot (a builder clone or a detached worktree), never on a tree someone edits.
    python3 apps/core/lumen-report/test/mutate.py [NAME_PART...]   (only the mutants whose name holds a part)"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = "src/org/lumen/report/"
MUTANTS = [
    (S + "Scrubber.java", "gsf id", "|gsf[_-]?id|gservices[_-]?id", "|gservices[_-]?id"),
    (S + "Scrubber.java", "e-mail", 'sb.append(s, last, m.start()).append("<email>");', "sb.append(s, last, m.start()).append(m.group());"),
    (S + "Scrubber.java", "IPv4", 'if (a.equals("0.0.0.0") || a.startsWith("127.") || a.startsWith("255.255.255.")) continue;', "if (true) continue;"),
    (S + "Scrubber.java", "quoted SSID", 's = sub(SSID_QUOTED, s, "$1$2\\"<ssid>\\"");', ""),
    (S + "Scrubber.java", "MAC", "if (colons >= 5 || count(s, '-') >= 5) s = sub(MAC, s, \"<mac>\");", ""),
    (S + "Scrubber.java", "known names", "s = s.replace(l[0], l[1]);", "s = s.replace(l[0], l[0]);"),
    (S + "Scrubber.java", "account object", 's = sub(ACCOUNT_OBJ, s, "$1<account>");', ""),
    (S + "Scrubber.java", "serial keys", 's = sub(SERIAL_KEY, s, "$1$2<serial>");', ""),
    (S + "LogcatFilter.java", "automatic crash buffer", "boolean ok = (crashBuffer && crashOfAnyApp) || allowed(uid);",
     "boolean ok = crashBuffer || allowed(uid);"),
    (S + "LogcatFilter.java", "user apps", "return appId == ownAppId || allowedAppIds.contains(appId);", "return true;"),
    (S + "PropFilter.java", "deny wins", "if (key == null || DENY.matcher(key).find()) return false;", "if (key == null) return false;"),
    (S + "PropFilter.java", "gsf prop", "|android_?id|gsf|account", "|android_?id|account"),
    (S + "OwnApps.java", "Google apps", "if (NOT_OWN.contains(pkg)) return false;", ""),
    (S + "OwnApps.java", "android prefix", '"org.lumen.", "com.android.", "android.",', '"org.lumen.", "com.android.", "android",'),
    (S + "IncidentScan.java", "image apps only", "return p != null && OwnApps.own(p) ? new Incident(tag, time, k, p) : null;",
     "return p != null ? new Incident(tag, time, k, p) : null;"),
    (S + "IncidentScan.java", "new entries only", "if (k == null || time <= since || now - time > MAX_AGE_MS) return null;",
     "if (k == null) return null;"),
    (S + "IncidentScan.java", "panic only", 'return text != null && PANIC.matcher(text).find() ? new Incident(tag, time, k, "kernel") : null;',
     'return new Incident(tag, time, k, "kernel");'),
    (S + "AutoPolicy.java", "setup gate", "return configured && on && setupDone;", "return configured && on;"),
    (S + "AutoPolicy.java", "default like stats", "static final boolean DEFAULT_ON = true;", "static final boolean DEFAULT_ON = false;"),
    (S + "AutoPolicy.java", "day cap", "if (today >= MAX_PER_DAY) return out;", ""),
    (S + "AutoPolicy.java", "once per key a day", "if (last.containsKey(i.key()) || !keys.add(i.key())) continue;",
     "if (!keys.add(i.key())) continue;"),
    (S + "AutoPolicy.java", "day window", "if (now - t >= DAY_MS || t > now) continue;", "if (t > now) continue;"),
    (S + "ReportQueue.java", "automatic out first", "for (Entry e : all) if (e.kind.automatic() && !e.id.equals(keep)) { drop = e; break; }", ""),
    (S + "ReportQueue.java", "age expiry", "if (now - e.created > MAX_AGE_MS || e.attempts >= MAX_ATTEMPTS) {", "if (e.attempts >= MAX_ATTEMPTS) {"),
    (S + "ReportQueue.java", "retry wait", "writeMeta(new Entry(e.id, e.kind, n, now + waitMs(n), e.created, e.zip));",
     "writeMeta(new Entry(e.id, e.kind, n, now, e.created, e.zip));"),
    (S + "ReportUrl.java", "https only", 'if (!u.startsWith("https://") || u.length() == "https://".length()) return Collections.emptyList();',
     'if (!u.startsWith("https://") || u.length() == "https://".length()) continue;'),
    (S + "ReportUrl.java", "addresses once each", "if (!out.contains(u)) out.add(u);", "out.add(u);"),
    (S + "ReportUploader.java", "next address", "if (!tryNext(e)) throw e;", "throw e;"),
    (S + "ReportUploader.java", "frozen = next", "return e.failure == ReportFailure.NETWORK || e.failure == ReportFailure.SERVER",
     "return e.failure == ReportFailure.SERVER"),
    (S + "ReportUploader.java", "429 stays", "|| e.failure == ReportFailure.SERVER || s == 404", "|| e.failure.retry() || s == 404"),
    (S + "ReportUploader.java", "404 = next", "s == 404 || s == 405", "s == 405"),
    (S + "ReportUploader.java", "report judged = stop", "(s >= 300 && s < 400)", "(s >= 300 && s < 500)"),
    (S + "ReportUploader.java", "retryable failure kept", "if (keep == null && e.failure.retry()) keep = e;", ""),
    (S + "ReportUploader.java", "deadline cuts", "cut.set(true);\n            abort();", "cut.set(true);"),
    (S + "ReportUploader.java", "sockets cut first", "if (s != null) s.cut();", ""),
    (S + "ReportUploader.java", "hard close", "s.setSoLinger(true, 0);", ""),
    (S + "ReportUploader.java", "own socket factory", "if (c instanceof HttpsURLConnection) ((HttpsURLConnection) c).setSSLSocketFactory(cs);", ""),
    (S + "ReportUploader.java", "deadline rate", "return 30_000L + bytes / 16;", "return 30_000L + bytes / 2;"),
    (S + "ReportFailure.java", "refusals not retried", "return this == NO_NETWORK || this == NETWORK || this == RATE_LIMITED || this == SERVER;",
     "return true;"),
    (S + "AutoPolicy.java", "consent before the scan", "return allowed && since > 0 && since <= now;", "return since > 0 && since <= now;"),
    (S + "ReportSender.java", "switch off drops queued", "if (urls.isEmpty() || (e.kind.automatic() && !ReportPrefs.autoOn(c))) {", "if (urls.isEmpty()) {"),
    (S + "ReportService.java", "dropbox listener", "ReportBoot.listen(app);", ""),
    (S + "ReportPrefs.java", "off forgets", "if (!on) e.remove(K_AUTO_LOG).remove(K_INSTALL_ID);", ""),
    (S + "ReportCollector.java", "automatic logcat filter", "LogcatFilter f = new LogcatFilter(imageAppIds, android.os.Process.myUid(), manual);",
     "LogcatFilter f = new LogcatFilter(imageAppIds, android.os.Process.myUid(), true);"),
    (S + "ReportCollector.java", "automatic dropbox filter", "if (!manual && !bootRecord && IncidentScan.of(tag, f[0], text, 0, now) == null) continue;", ""),
    # the unexpected stop: the record and the rules
    (S + "Alive.java", "stop: UP only", "return prev != null && UP.equals(prev.state) && !sameBoot", "return prev != null && !sameBoot"),
    (S + "Alive.java", "stop: another start", " && !sameBoot(prev, boot, up) && !orderly(last, reason, bootloader);", " && !orderly(last, reason, bootloader);"),
    (S + "Alive.java", "stop: orderly reason", "!sameBoot(prev, boot, up) && !orderly(last, reason, bootloader);", "!sameBoot(prev, boot, up);"),
    (S + "Alive.java", "orderly: reboot,", 'if (!head.equals("reboot") && !head.equals("shutdown")) return false;', 'if (!head.equals("shutdown")) return false;'),
    (S + "Alive.java", "orderly: shutdown,", 'if (!head.equals("reboot") && !head.equals("shutdown")) return false;', 'if (!head.equals("reboot")) return false;'),
    (S + "Alive.java", "orderly: plain words", 'if (r.equals("shutdown") || r.equals("bootloader") || r.equals("recovery") || r.equals("fastboot")) return true;', ""),
    (S + "Alive.java", "orderly: hardware", "for (String h : HARDWARE) if (sub.startsWith(h)) return false;", ""),
    (S + "Alive.java", "orderly: bootloader echo", "if (r.isEmpty() || echo(r, bootloader)) return false;", "if (r.isEmpty()) return false;"),
    (S + "Alive.java", "echo: unknown words only", "for (String w : b.split(\"[,_-]\")) for (String k : KNOWN) if (k.equals(w)) return false;", ""),
    (S + "Alive.java", "orderly: .last first", "String r = norm(last).isEmpty() ? norm(reason) : norm(last);",
     "String r = norm(reason).isEmpty() ? norm(last) : norm(reason);"),
    (S + "Alive.java", "boot id decides", "if (!m.boot.isEmpty() && boot != null && !boot.isEmpty()) return m.boot.equals(boot);", ""),
    (S + "Alive.java", "elapsed rule", "return m.up <= up;", "return true;"),
    (S + "Alive.java", "pending written", "AliveFile.write(pending, sb.append('\\n').toString());", ""),
    (S + "Alive.java", "once per start", 'AliveFile.write(marker, new Alive(CLEAN, "start", now, up, boot, "", false).line());', ""),
    (S + "Alive.java", "same start untouched", "if (prev != null && sameBoot(prev, boot, up)) return false;", ""),
    (S + "Alive.java", "damaged record", 'if (!"1".equals(kv.get("v")) || !(UP.equals(state) || CLEAN.equals(state)) || why == null || why.isEmpty()) {',
     'if (!(UP.equals(state) || CLEAN.equals(state))) {'),
    (S + "Alive.java", "stop time = last beat", "return new Incident(STOP_TAG, prev.time, ReportKind.BOOT, STOP_TAG, text(prev, kv, tz));",
     "return new Incident(STOP_TAG, System.currentTimeMillis(), ReportKind.BOOT, STOP_TAG, text(prev, kv, tz));"),
    (S + "Alive.java", "text: input", 'sb.append("Input: ").append(prev.hdmi ? "HDMI" : "an app")', 'sb.append("Input: ").append("an app")'),
    (S + "AliveState.java", "shutdown is final", "if (down) return null;", ""),
    (S + "AliveState.java", "shutdown cleans", "down = true;\n                clean = event;", "down = true;"),
    (S + "AliveState.java", "screen off ends standby", "standby = false;                    // a real screen off", "// a real screen off"),
    (S + "AliveState.java", "lamp-only standby", "            case STANDBY_ON:\n                standby = true;", "            case STANDBY_ON:\n"),
    (S + "AliveState.java", "standby ends", "            case STANDBY_OFF:\n                standby = false;", "            case STANDBY_OFF:\n"),
    (S + "AliveState.java", "standby screen", "Alive.STANDBY_ACTIVITY.equals(front) ? STANDBY_SCREEN : null", "null"),
    (S + "AliveState.java", "no beats in standby", "if (BEAT.equals(event) && Alive.CLEAN.equals(next.state) && last != null && Alive.CLEAN.equals(last.state)) return null;", ""),
    (S + "AliveState.java", "standby restored", "standby = Alive.CLEAN.equals(prev.state) && STANDBY_ON.equals(prev.why);", "standby = false;"),
    (S + "AliveState.java", "screensaver flag", "            case DREAM_ON:\n                dreaming = true;", "            case DREAM_ON:\n"),
    (S + "AliveState.java", "beats while on", "        return interactive;", "        return true;"),
    (S + "AliveFile.java", "take deletes", "if (f.exists() && !f.delete()) return null;", ""),
    (S + "AutoPolicy.java", "clock went back", "return allowed && since > 0 && since <= now;", "return allowed && since > 0;"),
    (S + "AutoPolicy.java", "stop needs consent", "return allowed && stop != null;", "return stop != null;"),
    (S + "IncidentScan.java", "boot records of this start", '"SYSTEM_LAST_KMSG".equals(tag)) && time >= bootStart;', '"SYSTEM_LAST_KMSG".equals(tag));'),
    (S + "IncidentScan.java", "SYSTEM_BOOT after a stop", 'all[t.length] = "SYSTEM_BOOT";', 'all[t.length] = "SYSTEM_RESTART";'),
    # the unexpected stop: Android wiring (check_repo)
    (S + "AutoReports.java", "stop taken always", "Incident stop = AliveWatch.takeStop(c);", "Incident stop = null;"),
    (S + "AutoReports.java", "stop in the report", "if (AutoPolicy.withStop(allowed, stop)) found.add(stop);", ""),
    (S + "AutoReports.java", "check before the scan", "AliveWatch.awaitCheck();", ""),
    (S + "ReportCollector.java", "stop section", 'parts.add(ReportZip.Part.text("unexpected_stop.txt", scrubber.scrubText(stop.detail)));', ""),
    (S + "AliveWatch.java", "shutdown heard", "sys.addAction(Intent.ACTION_SHUTDOWN);", ""),
    (S + "AliveWatch.java", "screen off heard", "sys.addAction(Intent.ACTION_SCREEN_OFF);", ""),
    (S + "AliveWatch.java", "standby heard", "app.registerReceiver(r, new IntentFilter(ACTION_STANDBY_CHANGED), PERMISSION_STANDBY, worker,",
     "app.registerReceiver(r, new IntentFilter(ACTION_STANDBY_CHANGED), null, worker,"),
    (S + "AliveWatch.java", "check first", "worker.post(this::check);", ""),
    (S + "AliveWatch.java", "beats planned", "if (AliveState.beats(interactive)) worker.postDelayed(beat, AliveState.BEAT_MS);", ""),
    (S + "AliveState.java", "beat interval", "static final long BEAT_MS = 10 * 60_000L;", "static final long BEAT_MS = 60 * 60_000L;"),
    (S + "AliveStart.java", "provider starts the watch", "AliveWatch.start(getContext());", ""),
    (S + "ReportService.java", "jobs start the watch", "AliveWatch.start(app);", ""),
    ("res/values-ru/lumen_strings_report.xml", "plain text", "Отчёт отправится позже", "Отчёт отправится позже; скоро"),
    ("res/values-de/lumen_strings_report.xml", "every key translated", '<string name="lumen_report_queued_title">', '<string name="lumen_report_queued_x">'),
]


def run():
    return subprocess.run(["sh", os.path.join(HERE, "test", "run.sh")], capture_output=True, text=True)


def main():
    files = {f: open(os.path.join(HERE, f), encoding="utf-8").read() for f in {m[0] for m in MUTANTS}}
    bad = [n for f, n, a, _ in MUTANTS if files[f].count(a) != 1]
    names = [m[1] for m in MUTANTS]
    if len(set(names)) != len(names):
        bad.append("duplicate names")
    picked = [m for m in MUTANTS if not sys.argv[1:] or any(p in m[1] for p in sys.argv[1:])]
    if bad:
        print("BROKEN anchors: %s" % ", ".join(bad))
        return 2
    r = run()
    if r.returncode != 0:
        print("FAIL unmutated:\n" + r.stdout[-1500:] + r.stderr[-1500:])
        return 1
    alive = []
    try:
        for f, n, a, b in picked:
            open(os.path.join(HERE, f), "w", encoding="utf-8").write(files[f].replace(a, b, 1))
            r = run()
            open(os.path.join(HERE, f), "w", encoding="utf-8").write(files[f])
            print("%-28s %s" % (n, "killed" if r.returncode else "ALIVE"))
            if r.returncode == 0:
                alive.append(n)
    finally:
        for f, s in files.items():
            open(os.path.join(HERE, f), "w", encoding="utf-8").write(s)
    print("mutants: %d killed, %d alive" % (len(picked) - len(alive), len(alive)))
    return 1 if alive else 0


if __name__ == "__main__":
    sys.exit(main())
