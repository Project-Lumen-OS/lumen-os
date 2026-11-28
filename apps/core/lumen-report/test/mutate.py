#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of problem reports (apps/core/lumen-report) that test/run.sh must kill: redaction rules, which incidents
are reported, consent and limits of automatic reports, the queue, the address, and the consent wiring of the Android
side, the unexpected stop (Alive, AliveState, AliveFile and the watch wiring), the persistent log of the last starts
(lumen_report.sh keep, BootLogs). Mutates the files IN PLACE and
restores them: run it on a frozen snapshot (a builder clone or a detached worktree), never on a tree someone edits.
    python3 apps/core/lumen-report/test/mutate.py [NAME_PART...]   (only the mutants whose name holds a part)"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = "src/org/lumen/report/"
MUTANTS = [
    (S + "CecJournal.java", "cec polls counted", "            r.count++;\n", ""),
    (S + "CecJournal.java", "cec answer in the key", 'String key = ask.text + "\\n" + (answer == null ? "" : answer.text);', "String key = ask.text;"),
    (S + "CecJournal.java", "cec other message ends the polls", "            flush();\n            emit(time + \" \" + text);\n        }", "            emit(time + \" \" + text);\n        }"),
    (S + "CecJournal.java", "cec answer goes back", "asks.containsKey(dst + src)", "asks.containsKey(src + dst)"),
    (S + "CecJournal.java", "cec newest kept", "            out.removeFirst();", "            out.removeLast();"),
    (S + "CecJournal.java", "cec failed sends", "(HDMI|HdmiCecController)", "(HDMI)"),
    (S + "CecJournal.java", "cec unanswered closed", "            if (open != null) pair(open, null);\n", ""),
    (S + "HdmiDump.java", "hdmi.txt journal part", "        sb.append(journal == null ? \"(not read)\\n\" : cap(journal));\n", ""),
    (S + "ReportCollector.java", "cec fed from logcat.txt", "                if (LOGCAT.equals(name)) cec.add(kept);\n", ""),
    ("image/lumen_report.rc", "rc CEC lines on", "    setprop log.tag.HDMI D\n", ""),
    # evleo790 LR-9MEQZP: settings.txt, the journal without HdmiCecConfig's read lines, the manual wait, the late answer
    ("src/org/lumen/report/SettingsDump.java", "settings arc whole word", '|(^|_)e?arc(_|$)|', '|e?arc|'),
    ("src/org/lumen/report/SettingsDump.java", "settings hdmi kept", '"(?i)hdmi|cec|', '"(?i)cec|'),
    ("src/org/lumen/report/SettingsDump.java", "settings sorted", "        Collections.sort(kept);\n", ""),
    ("src/org/lumen/report/SettingsDump.java", "settings refusal named", '.append(why == null ? "" : ": " + why)', ''),
    ("src/org/lumen/report/SettingsDump.java", "settings cec block ends", '            if (!line.startsWith(" ") || line.trim().isEmpty()) break;\n', ''),
    ("src/org/lumen/report/SettingsDump.java", "settings prefs backup", 'fileName.endsWith(".xml") && ', ''),
    ("src/org/lumen/report/SettingsDump.java", "settings prefs string", 'String v = m.group(3) != null ? m.group(3) : m.group(4);', 'String v = m.group(3);'),
    ("src/org/lumen/report/CecJournal.java", "journal setting reads", "        if (noise(text)) return;\n", ""),
    ("src/org/lumen/report/CecJournal.java", "journal setting written kept", 'return text.startsWith("Getting CEC setting value ")', 'return text.startsWith("Updating CEC setting") || text.startsWith("Getting CEC setting value ")'),
    ("src/org/lumen/report/RootLogs.java", "manual waits longer", "return manual ? MANUAL_TIMEOUT_MS : TIMEOUT_MS;", "return TIMEOUT_MS;"),
    ("src/org/lumen/report/RootLogs.java", "late needs the files", "return parse(p.get(PROP_DONE), token) == Answer.OK && fresh(dir, token);", "return parse(p.get(PROP_DONE), token) == Answer.OK;"),
    ("src/org/lumen/report/RootLogs.java", "late needs ok", "return parse(p.get(PROP_DONE), token) == Answer.OK && fresh(dir, token);", "return fresh(dir, token);"),
    ("src/org/lumen/report/ReportCollector.java", "late answer taken", "if (a == RootLogs.Answer.TIMEOUT && RootLogs.late(PROPS, root, token)) {", "if (false) {"),
    ("src/org/lumen/report/ReportCollector.java", "cut log kept", "if (kill == null || !kill.fired) throw e;", "throw e;"),
    ("src/org/lumen/report/ReportCollector.java", "settings in manual reports", "            hdmi();\n            settings();\n", "            hdmi();\n"),
    ("image/lumen_report.rc", "rc CEC lines before zygote", "on early-init\n    setprop log.tag.HDMI D", "on property:sys.boot_completed=1\n    setprop log.tag.HDMI D"),
    (S + "ReportView.java", "screen focus on send", "Action.SEND, Action.SEND, Action.AUTO", "Action.CLOSE, Action.SEND, Action.AUTO"),
    (S + "ReportView.java", "screen no details row", "Action.SEND, Action.AUTO, Action.DETAILS, Action.CLOSE", "Action.SEND, Action.AUTO, Action.CLOSE"),
    (S + "ReportView.java", "screen retry first", "Action.RETRY, Action.RETRY, Action.CLOSE", "Action.CLOSE, Action.RETRY, Action.CLOSE"),
    (S + "ReportView.java", "screen back cancels", "case PROGRESS: return Back.CANCEL;", "case PROGRESS: return Back.LEAVE;"),
    (S + "ReportView.java", "screen back from code", "case START: return Back.LEAVE;\n            default: return Back.START;", "default: return Back.LEAVE;"),
    (S + "ReportView.java", "screen details refocus", "return from == Screen.DETAILS ? Action.DETAILS : Action.SEND;", "return Action.SEND;"),
    (S + "ReportView.java", "screen bar cap", "Math.min(Math.max(0, sent), t) / t", "Math.max(0, sent) / t"),
    (S + "ReportView.java", "screen newer auto", "return a && (!m || autoTime > manualTime);", "return a;"),
    (S + "ReportView.java", "screen queued", "return new ReportView(Screen.QUEUED, Action.CLOSE, Action.CLOSE);", "return new ReportView(Screen.FAILED, Action.CLOSE, Action.CLOSE);"),
    (S + "PartUpload.java", "choked lanes", "i < (choked ? LANES_CHOKED : LANES); i++", "i < LANES; i++"),
    (S + "PartUpload.java", "lanes not grown", "            if (nowChoked) grow();\n", ""),
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
    (S + "HdmiDump.java", 'hdmi arc as a word', '|\\\\be?arc\\\\b|', '|arc|'),
    (S + "HdmiDump.java", 'hdmi audio unfiltered', "if (AUDIO_KEYS.matcher(line).find()) sb.append(line).append('\\n');", "sb.append(line).append('\\n');"),
    (S + "HdmiDump.java", 'hdmi cap', 'if (s.length() <= max) return', 'if (true) return'),
    (S + "HdmiDump.java", 'hdmi part missing named', 'sb.append(hdmiControl == null ? "(not readable)\\n" : cap(hdmiControl));', 'sb.append(hdmiControl == null ? "" : cap(hdmiControl));'),
    (S + "HdmiDump.java", 'hdmi cec whole', 'sb.append(hdmiControl == null ? "(not readable)\\n" : cap(hdmiControl));', 'sb.append(hdmiControl == null ? "(not readable)\\n" : cap(audioLines(hdmiControl)));'),
    (S + "ReportCollector.java", 'hdmi section wired', '            miracastDiag();\n            hdmi();\n', '            miracastDiag();\n'),
    # the image's root log helper (image/lumen_report.sh) and its app side (RootLogs, ReportCollector, PowerDump)
    ("image/lumen_report.sh", "helper token alphabet", "''|*[!0-9a-z-]*) return 1 ;;", "'') return 1 ;;"),
    ("image/lumen_report.sh", "helper token length", '[ "${#1}" -le 24 ]', 'true'),
    ("image/lumen_report.sh", "helper e-mail", '  if (index(s, "@")) s = email(s)\n', ''),
    ("image/lumen_report.sh", "helper hidl kept", 'if (substr(m, at + 1) ~ /^[0-9]+\\.[0-9]/)', 'if (0)'),
    ("image/lumen_report.sh", "helper mac", '  if (index(s, ":")) s = whole(s, MAC, "<mac>")\n', ''),
    ("image/lumen_report.sh", "helper ssid key", '    s = keyed(s, "ssid" SEP, VAL, "<ssid>")\n', ''),
    ("image/lumen_report.sh", "helper ssid quoted", '    s = keyed(s, "ssid[ \\t]+", ', '    s = keyed(s, "ssidx[ \\t]+", '),
    ("image/lumen_report.sh", "helper ssid config key", '  if (index(s, "\\"")) s = wifikey(s)\n', ''),
    ("image/lumen_report.sh", "helper id_str", '  if (index(lc, "id_str=")) s = keyed(s, "id_str=", "[^ \\t]+", "<ssid>")\n', ''),
    ("image/lumen_report.sh", "helper secrets", '    s = keyed(s, KEYS SEP, "((bearer|basic|digest|token)[ \\t]+)?" VAL, "<secret>")\n', ''),
    ("image/lumen_report.sh", "helper bearer", '"((bearer|basic|digest|token)[ \\t]+)?" VAL', 'VAL'),
    ("image/lumen_report.sh", "helper url secrets", '    if (index(s, "=")) s = keyed(s, "[?&;]" QKEYS "=", ', '    if (0) s = keyed(s, "[?&;]" QKEYS "=", '),
    ("image/lumen_report.sh", "helper account", '  if (index(lc, "account")) s = keyed(', '  if (0) s = keyed('),
    ("image/lumen_report.sh", "helper case", 'while (match(tolower(s), pre val)) {', 'while (match(s, pre val)) {'),
    ("image/lumen_report.sh", "helper key kept", 'o = o substr(s, 1, st - 1) substr(s, st, RLENGTH) mark;', 'o = o substr(s, 1, st - 1) mark;'),
    ("image/lumen_report.sh", "helper scrubbed log", '  scrubbed "$OUT/.raw" "$OUT/.scrubbed"\n  tail -c', '  cp "$OUT/.raw" "$OUT/.scrubbed"\n  tail -c'),
    ("image/lumen_report.sh", "helper scrubbed dumpsys", '    scrubbed "$OUT/.raw" "$OUT/.scrubbed"\n    head', '    cp "$OUT/.raw" "$OUT/.scrubbed"\n    head'),
    ("image/lumen_report.sh", "helper left out", 'echo "(left out: the text could not be scrubbed)" > "$2"', ':'),
    ("image/lumen_report.sh", "helper log cap", '  tail -c "$cap" "$OUT/.scrubbed" > "$OUT/$name.new"', '  cat "$OUT/.scrubbed" > "$OUT/$name.new"'),
    ("image/lumen_report.sh", "helper no raw copy", '    rm -f "$OUT/.raw" "$OUT/.scrubbed"\n    put "$s.txt"', '    put "$s.txt"'),
    ("image/lumen_report.sh", "helper floor private", 'chmod 0600 "$OUT/.last"', ':'),
    ("image/lumen_report.sh", "helper events buffer", 'logcat -d -D -b main,system,events', 'logcat -d -D -b main,system'),
    ("image/lumen_report.sh", "helper app group", 'chown "0:$gid" "$OUT/$1.new" && ', ''),
    ("image/lumen_report.sh", "helper file mode", 'chmod 0640 "$OUT/$1.new"', 'chmod 0644 "$OUT/$1.new"'),
    ("image/lumen_report.sh", "helper exact package", 's/^$re \\([0-9][0-9]*\\) .*/\\1/p', 's/^$re.* \\([0-9][0-9]*\\) .*/\\1/p'),
    ("image/lumen_report.sh", "helper floor", '[ "$gap" -lt "$MIN_GAP_CS" ]', 'false'),
    ("image/lumen_report.sh", "helper off switch", '[ "$(getprop ro.lumen.report.url)" = off ] && {', 'false && {'),
    ("image/lumen_report.sh", "helper cleanup", '    sleep 0.25\n    n=$((n + 1))\n  done\n  clear_out\n', '    sleep 0.25\n    n=$((n + 1))\n  done\n'),
    ("image/lumen_report.sh", "helper newest request", '[ "$next" != "$req" ] && valid "$next" || break', 'break'),
    # the persistent log of the last starts (lumen_report.sh keep, logkeep_test.sh) and its report side (BootLogs)
    ("image/lumen_report.sh", "keep scrub on write", "  line = scrub(line)\n  put(line)\n", "  put(line)\n"),
    ("image/lumen_report.sh", "keep flush every line", "print line > cur; fflush(cur)", "print line > cur"),
    ("image/lumen_report.sh", "keep tag cap", "&& ++cnt[tag] > cap) { drop[tag]++; next }", "&& ++cnt[tag] > cap * 100) { drop[tag]++; next }"),
    ("image/lumen_report.sh", "keep crash never capped", 'keep = buf == "crash" || (tag != "" && tag ~ imp)', 'keep = (tag != "" && tag ~ imp)'),
    ("image/lumen_report.sh", "keep open minutes", "&& wins > open &&", "&&"),
    ("image/lumen_report.sh", "keep cap counted", "    put(m)\n", ""),
    ("image/lumen_report.sh", "keep imp errors", '(keep || lvl == "E" || lvl == "F")', "keep"),
    ("image/lumen_report.sh", "keep chunk rotation", "if (cur == \"\" || bytes >= chunk) next_chunk()", "if (cur == \"\") next_chunk()"),
    ("image/lumen_report.sh", "keep newest open chunk", 'case "$f" in "$ol"|"$oi"|"$ok"|"$os"|*/info.txt) continue ;; esac', 'case "$f" in */info.txt) continue ;; esac'),
    ("image/lumen_report.sh", "keep starts kept", "awk -v k=\"$KEEP_BOOTS\" 'NR > k'", "awk -v k=\"$KEEP_BOOTS\" 'NR > k + 1'"),
    ("image/lumen_report.sh", "keep resume same start", '    RESUME=1\n    return 0\n', '    RESUME=0\n'),
    ("image/lumen_report.sh", "keep pstore into the start before", '[ -n "$prev" ] && lk_pstore "$prev"', '[ -n "$prev" ] && lk_pstore "$D"'),
    ("image/lumen_report.sh", "keep budget percent", "cap = pool * (live ? pctl : pct) / 100", "cap = pool * pct / 100"),
    ("image/lumen_report.sh", "keep budget ceiling", "    if (cap > m) cap = m\n", ""),
    ("image/lumen_report.sh", "keep budget low ram", "    if (low) m = m / 2\n", ""),
    ("image/lumen_report.sh", "keep budget floor", "    if (cap > pool - floor) cap = pool - floor\n", ""),
    ("image/lumen_report.sh", "keep evict older log first", "if (T[i] == \"log\" && mid) c = old ? 1 : 2", "if (T[i] == \"log\" && mid) c = old ? 2 : 1"),
    ("image/lumen_report.sh", "keep evict head kept", "pos[i] > head &&", "pos[i] > 0 &&"),
    ("image/lumen_report.sh", "keep export hard link", '*.gz) ln "$f" "$X/$name" 2> /dev/null || cp "$f" "$X/$name" ;;', '*.gz) cp "$f" "$X/$name" ;;'),
    ("image/lumen_report.sh", "keep export cleanup", '  rm -rf "$LK/export"\n}\n', '}\n'),
    ("image/lumen_report.rc", "rc keeper started", "    start lumen_logkeep\n", ""),
    (S + "BootLogs.java", "bootlogs head", "while (i < n && head > 0 && (p.head.isEmpty() || h + chunks.get(i).raw() <= head))", "while (i < n && (p.head.isEmpty() || h + chunks.get(i).raw() <= head))"),
    (S + "BootLogs.java", "bootlogs tail newest", "p.tail.add(0, chunks.get(--j));", "p.tail.add(chunks.get(--j));"),
    (S + "BootLogs.java", "bootlogs filter", "                if (lf != null && !lf.keep(line)) {", "                if (false) {"),
    (S + "BootLogs.java", "bootlogs scrub", "String s = scrub == null ? line : scrub.scrub(line);", "String s = line;"),
    (S + "BootLogs.java", "bootlogs left-out line", "            if (p.skipped > 0) {", "            if (p.skipped > 99) {"),
    (S + "BootLogs.java", "bootlogs newest first", "new TreeMap<>(Collections.reverseOrder())", "new TreeMap<>()"),
    (S + "ReportZip.java", "zip raw outside the limit", "<= maxBytes + raw) return shrink;", "<= maxBytes) return shrink;"),
    (S + "ReportZip.java", "zip raw whole", "copyTail(p.file, Long.MAX_VALUE, z, buf);", "copyTail(p.file, Math.max(4096, p.cap / shrink), z, buf);"),
    (S + "RootLogs.java", "rootlogs alphabet", "if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || c == '-')) return false;", ""),
    (S + "RootLogs.java", "rootlogs own token only", 'if (done == null || token == null || !done.startsWith(token + " ")) return null;',
     'if (done == null || token == null || done.indexOf(" ") < 0) return null;'),
    (S + "RootLogs.java", "rootlogs timeout", "if (clock.now() >= end) return Answer.TIMEOUT;", ""),
    (S + "RootLogs.java", "rootlogs cancel", "if (!clock.sleep(POLL_MS)) return Answer.CANCELED;", "clock.sleep(POLL_MS);"),
    (S + "RootLogs.java", "rootlogs fresh", "return new String(b, StandardCharsets.UTF_8).trim().equals(token);", "return true;"),
    (S + "ReportCollector.java", "collector asks the helper", "        boolean root = rootLogs(since);\n", "        boolean root = false;\n"),
    (S + "ReportCollector.java", "collector checks the token", "if (a == RootLogs.Answer.OK && RootLogs.fresh(root, token)) {", "if (a == RootLogs.Answer.OK) {"),
    (S + "ReportCollector.java", "collector acks", "        } finally {\n            RootLogs.ack(PROPS, token);\n        }", "        } finally {\n        }"),
    (S + "PowerDump.java", "power app lists out", "                if (!line.trim().isEmpty() && indent > skipIndent) {", "                if (false) {"),
    (S + "PowerDump.java", "power cap", "return HdmiDump.cap(sb.toString().replaceAll(\"\\n+$\", \"\\n\"), CAP);", "return sb.toString();"),
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
