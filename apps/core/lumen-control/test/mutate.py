#!/usr/bin/env python3
"""Targeted mutants of lumen-control (the command core and its phone remote adapter): each one change to the module,
its model table or a projector app's wiring must turn test/run.sh red.
  python3 apps/core/lumen-control/test/mutate.py [-j N] [NAME...]      (scratch copies, JDK 17+, python3)
The Android driver (org.lumen.control.android: AndroidSink, AndroidFacts, PhoneRemote, SysProp) only compiles on the
host: its behaviour needs a projector, so it has no mutants here.
"""
import os
import shutil
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(HERE))))
MODULE = "apps/core/lumen-control"
C = MODULE + "/src/org/lumen/control/"
V = C + "vcontrol/"
GEN = "tools/control/gen_vcontrol.py"

# name: (file, old text (once in the file), new text[, regenerate VcontrolModels.java after the change])
MUTANTS = {
    # Vcontrol: the action table
    "find_silent": (V + "Vcontrol.java", "case Action.FIND_DEVICE: return Replies.deviceInfo(x, facts, model);", "case Action.FIND_DEVICE: return null;"),
    "connect_silent": (V + "Vcontrol.java", "case Action.REQUEST_CONNECT: return Replies.connectInfo(x, facts, model, sink.volume());", "case Action.REQUEST_CONNECT: return null;"),
    "heartbeat_silent": (V + "Vcontrol.java", "case Action.HEART_REQUEST: return Replies.heartbeat(x);", "case Action.HEART_REQUEST: return null;"),
    "af_any_model": (V + "Vcontrol.java", "model.autofocus && click(RemoteKey.AUTOFOCUS)", "click(RemoteKey.AUTOFOCUS)"),
    "auth_shown_false": (V + "Vcontrol.java", "case Action.SHOW_AUTH_CODE_STATE: return Replies.status(x, r.action, true);", "case Action.SHOW_AUTH_CODE_STATE: return Replies.status(x, r.action, false);"),
    "auth_callback_any": (V + "Vcontrol.java", "r.statusState == STATE_ACCEPTED", "r.statusState != 0"),
    "mute_key": (V + "Vcontrol.java", "click(RemoteKey.MUTE)", "click(RemoteKey.VOLUME_DOWN)"),
    "dormancy_key": (V + "Vcontrol.java", "return Replies.status(x, r.action, click(RemoteKey.POWER));", "return Replies.status(x, r.action, click(RemoteKey.HOME));"),
    "stock_other_unknown": (V + "Vcontrol.java", "Action.stockOther(r.action) ? Replies.status(x, r.action, false) : Replies.unknown(x)", "Replies.unknown(x)"),
    "power_any_type": (V + "Vcontrol.java", "r.controlMode == ControlMode.POWER && r.controlType == ControlMode.POWER_KEY", "r.controlMode == ControlMode.POWER"),
    "power_no_reply": (V + "Vcontrol.java", "return Replies.controlDone(x);", "return null;"),
    "volume_nonnumber_answer": (V + "Vcontrol.java", "            return null;\n        }\n        return Replies.status(x, Action.VOLUME", "            return Replies.status(x, Action.VOLUME, false);\n        }\n        return Replies.status(x, Action.VOLUME"),
    "volume_missing_ok": (V + "Vcontrol.java", "if (v == null) return Replies.status(x, Action.VOLUME, false);", "if (v == null) return Replies.status(x, Action.VOLUME, true);"),
    "key_any_sender": (V + "Vcontrol.java", "StockKeys.SENDER.equals(type) && ", ""),
    "key_no_common": (V + "Vcontrol.java", "        if (!r.hasCommon) return null;\n", ""),
    "key_ok_ignores_sink": (V + "Vcontrol.java", "ok = c != null && sink.run(c);", "ok = c != null;\n            if (ok) sink.run(c);"),
    "intornull_plus": (V + "Vcontrol.java", "\"[+-]?[0-9]+\"", "\"-?[0-9]+\""),
    # StockKeys: the stock key tables
    "keymode_swap": (V + "StockKeys.java", "case 1: return RemoteKey.LEFT;\n            case 2: return RemoteKey.UP;", "case 1: return RemoteKey.UP;\n            case 2: return RemoteKey.LEFT;"),
    "gm_power13": (V + "StockKeys.java", "case 13: return RemoteKey.POWER;", "case 13: return null;"),
    "gm_gear": (V + "StockKeys.java", "case 12: return RemoteKey.GEAR;", "case 12: return RemoteKey.MENU;"),
    "keyaction_click": (V + "StockKeys.java", "case 3: return KeyAction.CLICK;", "case 3: return KeyAction.DOWN;"),
    "negative_passes": (V + "StockKeys.java", "return code < 0 ? null : Command.androidKey(code, action);", "return Command.androidKey(code, action);"),
    # Replies, Exchange, Feedback: the answers field for field
    "devicetype": (V + "Replies.java", "\"unkonwn\"", "\"unknown\""),
    "connect_ip": (V + "Replies.java", ".put(\"ipaddress\", x.peer.address)", ".put(\"ipaddress\", f.ip())"),
    "find_volume": (V + "Replies.java", ".put(\"currentVolume\", 0)", ".put(\"currentVolume\", 1)"),
    "connect_volume_swap": (V + "Replies.java", ".put(\"currentVolume\", volume[1])", ".put(\"currentVolume\", volume[0])"),
    "find_versioncode": (V + "Replies.java", ".put(\"versioncode\", 0);", ".put(\"versioncode\", m.versionCode);"),
    "unknown_action": (V + "Replies.java", "x.request.action + 1", "x.request.action"),
    "control_done_msgid": (V + "Replies.java", "new Feedback(Action.CONTROL_DONE, x.request.msgid,", "new Feedback(Action.CONTROL_DONE, x.msgid(),"),
    "msgid_blank": (V + "Exchange.java", "return m == null || m.trim().isEmpty() ? \"0\" : m;", "return m == null ? \"0\" : m;"),
    "istcp_peer": (V + "Exchange.java", ".put(\"isTcp\", tcpPort != null)", ".put(\"isTcp\", peer.tcp)"),
    "agreement_udp": (V + "Exchange.java", "peer.tcp ? AgreementType.TCP : AgreementType.UDP", "AgreementType.UDP"),
    "feedback_order": (V + "Feedback.java", ".put(\"msgid\", msgid)\n                .put(\"startScreenCapTime\", 0)", ".put(\"startScreenCapTime\", 0)\n                .put(\"msgid\", msgid)"),
    "feedback_state": (V + "Feedback.java", "ok ? 1 : 0", "ok ? 1 : 2"),
    # Request: reading the phone's message as stock's Gson binds it
    "int_double": (V + "Request.java", "                if (d == (int) d) return (int) d;\n", ""),
    "string_bool": (V + "Request.java", "v instanceof Json.Num || v instanceof Boolean", "v instanceof Json.Num"),
    "string_object": (V + "Request.java", "        throw new Json.BadJson(\"not a string: \" + v);", "        return String.valueOf(v);"),
    # Frame: TCP framing
    "frame_magic": (V + "Frame.java", "MAGIC = -8118", "MAGIC = -8119"),
    "frame_second": (V + "Frame.java", "SECOND = 0x4A", "SECOND = 0x4B"),
    "frame_empty_is_frame": (V + "Frame.java", "if (n <= 0) continue;", "if (n < 0) continue;"),
    "frame_cut_null": (V + "Frame.java", "if (!fill(in, body)) return \"\";", "if (!fill(in, body)) return null;"),
    "frame_no_bound": (V + "Frame.java", "if (n > MAX_BODY) throw", "if (n > Integer.MAX_VALUE - 8) throw"),
    # Json: lenient reading, nesting bound, Gson writing
    "depth_off": (V + "Json.java", "if (++depth > MAX_DEPTH)", "if (++depth > Integer.MAX_VALUE)"),
    "depth_leak": (V + "Json.java", "        depth--;\n", ""),
    "depth_off_by_one": (V + "Json.java", "if (++depth > MAX_DEPTH)", "if (++depth >= MAX_DEPTH)"),
    "json_trailing": (V + "Json.java", "if (j.i != j.s.length()) throw", "if (j.i > j.s.length()) throw"),
    "json_single_quote": (V + "Json.java", "c == '\"' || c == '\\'' ? quoted() : bare()", "c == '\"' ? quoted() : bare()"),
    "json_escape_amp": (V + "Json.java", "case '<': case '>': case '&': case '=':", "case '<': case '>': case '=':"),
    "json_null_written": (V + "Json.java", "if (value == null) return this;", "if (value == null) value = \"null\";"),
    # VcontrolServer: LAN only, connection cap, reply port, fault isolation
    "udp_lan_off": (V + "VcontrolServer.java", "            if (!lan.test(from)) continue;\n", ""),
    "tcp_lan_off": (V + "VcontrolServer.java", "if (!lan.test(c.getInetAddress()) || !admit(c)) {", "if (!admit(c)) {"),
    "lan_default_any": (V + "VcontrolServer.java", "this(vcontrol, ports, log, LanAddress::isLan);", "this(vcontrol, ports, log, a -> true);"),
    "cap_off": (V + "VcontrolServer.java", "        if (clients.size() >= MAX_CONNECTIONS) return false;\n", ""),
    "forget_off": (V + "VcontrolServer.java", "        clients.remove(c);\n", ""),
    "reply_port": (V + "VcontrolServer.java", "b.length, to, ports.phone)", "b.length, to, ports.commands)"),
    "tcp_unframed": (V + "VcontrolServer.java", "out.write(Frame.encode(reply));", "out.write(reply.getBytes(StandardCharsets.UTF_8));"),
    "guard_off": (V + "VcontrolServer.java", "            try {\n                r.run();\n            } catch (Throwable e) {\n                log.warn(name + \" stopped\", e);\n            }", "            r.run();"),
    "answer_catch_narrow": (V + "VcontrolServer.java", "} catch (RuntimeException e) {\n            log.warn(\"message\", e);", "} catch (IllegalArgumentException e) {\n            log.warn(\"message\", e);"),
    # ucontrolMDNS keys, LAN filter, hashed id
    "ucontrol_click": (C + "ucontrol/UcontrolKeys.java", "\"KEYPRESSES:\"", "\"KEYPRESS:\""),
    "ucontrol_max": (C + "ucontrol/UcontrolKeys.java", "MAX_SCANCODE = 255", "MAX_SCANCODE = 256"),
    "lan_ula_any": (C + "utils/LanAddress.java", "(a.getAddress()[0] & 0xFE) == 0xFC", "true"),
    "lan_site_local": (C + "utils/LanAddress.java", " || a.isSiteLocalAddress()", ""),
    "lan_ula_v4": (C + "utils/LanAddress.java", "a instanceof Inet6Address && ", ""),
    "lan_ula_mask": (C + "utils/LanAddress.java", "& 0xFE) == 0xFC", "& 0xF0) == 0xF0"),
    "pid_length": (C + "utils/HashedId.java", "i < bytes && i < h.length", "i <= bytes && i < h.length"),
    # the model table and every projector app's wiring
    "tsv_version": ("devices/_control/vcontrol.tsv", "G0082\t20500320\t", "G0082\t20500321\t", True),
    "tsv_play6_hidden": ("devices/_control/vcontrol.tsv", "G0058\t20500319\tv2.5.3.19", "G0058\t-\tv2.5.3.19", True),
    "wiring_g0155_recipe": ("devices/g0155/app/X50Projector.layers", "module    lumen-control\n", ""),
    "wiring_play6_start": ("apps/Play6Projector/src/org/lumen/projector/Play6App.java", "            org.lumen.control.android.PhoneRemote.start(app);", "            // no phone remote"),
    "wiring_hb_inject": ("apps/HbProjector/AndroidManifest.xml", "    <uses-permission android:name=\"android.permission.INJECT_EVENTS\" />\n", ""),
    "wiring_z9x_twice": ("apps/ProjectorBase/src/org/lumen/projector/App.java", "        org.lumen.control.android.PhoneRemote.start(this);", "        org.lumen.control.android.PhoneRemote.start(this);\n        org.lumen.control.android.PhoneRemote.start(this);"),
}

# copied for real into every scratch tree (the rest is symlinked: the test only reads it)
COPIED = (MODULE, "tools/control", "devices/_control")


def scratch(root, extra):
    copies = set(COPIED) | {extra}

    def fill(rel):
        os.makedirs(os.path.join(root, rel), exist_ok=True)
        for name in sorted(os.listdir(os.path.join(G, rel))):
            r = os.path.join(rel, name) if rel else name
            src, dst = os.path.join(G, r), os.path.join(root, r)
            if r in copies and os.path.isdir(src):
                shutil.copytree(src, dst, symlinks=True)
            elif r in copies:
                shutil.copy2(src, dst)
            elif any(c.startswith(r + "/") for c in copies):
                fill(r)
            else:
                os.symlink(src, dst)
    fill("")


def owner(f):
    """The smallest folder copied for a mutant of file f: its app (apps/X) or device (devices/c) or the file."""
    p = f.split("/")
    if p[0] in ("apps", "devices") and len(p) > 2 and not f.startswith(MODULE) and not f.startswith("devices/_control"):
        return "/".join(p[:2])
    return f


def run(name, spec):
    f, old, new = spec[:3]
    regen = len(spec) > 3 and spec[3]
    tmp = tempfile.mkdtemp(prefix="lc-mut-")
    try:
        root = os.path.join(tmp, "g")
        scratch(root, owner(f))
        if name is not None:
            path = os.path.join(root, f)
            if os.path.islink(path):
                return "ERROR (%s not copied)" % f
            text = open(path, encoding="utf-8").read()
            if text.count(old) != 1:
                return "ERROR (anchor found %d times)" % text.count(old)
            open(path, "w", encoding="utf-8").write(text.replace(old, new))
            if regen:
                subprocess.run([sys.executable, "-B", os.path.join(root, GEN), "--write"], capture_output=True, timeout=120)
        r = subprocess.run(["sh", os.path.join(root, MODULE, "test/run.sh")], capture_output=True, text=True, timeout=600)
        out = r.stdout + r.stderr
        why = next((ln for ln in out.splitlines() if ln.startswith(("FAIL", "gen_vcontrol: wiring", "Exception", "error:"))
                    or "is not the table's" in ln or ": error:" in ln), "")
        if not why and r.returncode != 0:
            why = (out.strip().splitlines() or ["rc %d" % r.returncode])[-1]
        return ("killed  " + why[:110]) if r.returncode != 0 else ("ALIVE" if name is not None else "green")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main(argv):
    jobs = 1
    if argv[:1] == ["-j"]:
        jobs, argv = int(argv[1]), argv[2:]
    todo = argv or sorted(MUTANTS)
    unknown = [n for n in todo if n not in MUTANTS]
    if unknown:
        print("unknown mutants: " + " ".join(unknown))
        return 2
    base = run(None, (MODULE + "/test/run.sh", "", ""))
    print("%-24s %s" % ("(unmutated)", base))
    if base != "green":
        print("the unmutated scratch tree is red: nothing proved")
        return 1
    with ThreadPoolExecutor(max_workers=jobs) as pool:
        results = list(pool.map(lambda n: (n, run(n, MUTANTS[n])), todo))
    bad = 0
    for n, res in results:
        print("%-24s %s" % (n, res))
        bad += not res.startswith("killed")
    print("mutants: %d, killed %d, not killed %d" % (len(todo), len(todo) - bad, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
