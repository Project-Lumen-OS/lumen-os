#!/usr/bin/env python3
"""The HDMI receiver bridge may only ever read the node or write the exact bytes "help" (2026-11-18): every other
command of /proc/utopia_mdb/HDMIRX (fixedeq, hdmiloglevel, hpddelay, rst, ptg, set_edid) and its conf node write the
hardware. Checks the script, its rc, the app side and the model data that ship it. test/run.sh runs it."""
import glob
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
G = os.path.abspath(os.path.join(MOD, "..", "..", ".."))
bad = []


def ok(what, cond):
    print(("ok   " if cond else "FAIL ") + what)
    if not cond:
        bad.append(what)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def code_of(java):
    """Java source without comments (a comment may name the node, code may not)."""
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", java, flags=re.S))


sh = read(os.path.join(MOD, "image/lumen_hdmirx.sh"))
code = [l for l in sh.splitlines() if l.strip() and not l.lstrip().startswith("#")]
ok("script: one HELP constant, exactly help", [l for l in code if l.startswith("HELP=")] == ["HELP=help"])
ok("script: NODE is the receiver node", [l for l in code if l.startswith("NODE=")] == ["NODE=$R/proc/utopia_mdb/HDMIRX"])
ok("script: utopia_mdb named only in NODE", [l for l in code if "utopia_mdb" in l] == ["NODE=$R/proc/utopia_mdb/HDMIRX"])
writes = [l.strip() for l in code if re.search(r'>>?\s*"?\$NODE', l) or ("$NODE" in l and "tee" in l)]
ok("script: the only write to the node is printf '%s' \"$HELP\"",
   writes == ['printf \'%s\' "$HELP" > "$NODE" 2>/dev/null'])
reads = [l.strip() for l in code if "$NODE" in l and not l.startswith("NODE=")]
ok("script: the node is otherwise only tested and read",
   sorted(reads) == sorted(['[ -e "$NODE" ] || answer none', 'cat "$NODE" > /dev/null 2>&1',
                             'printf \'%s\' "$HELP" > "$NODE" 2>/dev/null']))
WRITE_CMDS = re.compile(r"\b(fixedeq|hdmiloglevel|hpddelay|rst|ptg|set_edid|output=|riuinfo|WordW|dd)\b")
ok("script: no receiver write command outside comments", not any(WRITE_CMDS.search(l) for l in code))
ok("script: the floor between dumps is 0.9 s", "MIN_GAP_CS=90" in code)
ok("script: Z9X and Z9X HB only", 'MODELS=" G0082 G0130 G0148 "' in code)
ok("script: token checked before use", code.index('case "$req" in \'\'|*[!0-9a-z-]*) exit 0 ;; esac') < 
   min(i for i, l in enumerate(code) if "answer()" in l))
rc = read(os.path.join(MOD, "image/lumen_hdmirx.rc"))
rcc = [l.rstrip() for l in rc.splitlines() if l.strip() and not l.startswith("#")]
ok("rc: exact content", rcc == ["on boot", "    mkdir /dev/lumen_hdmirx 0755 root system",
                               "on property:sys.lumen.hdmirx.req=* && property:sys.boot_completed=1",
                               "    start lumen_hdmirx",
                               "service lumen_hdmirx /system/bin/sh /system/etc/lumen/lumen_hdmirx.sh",
                               "    user root", "    group root system log", "    seclabel u:r:su:s0", "    oneshot",
                               "    disabled"])
java = code_of(read(os.path.join(MOD, "src/org/lumen/signal/HdmiRxBridge.java")))
ok("app side: never touches the node itself", "utopia_mdb" not in java and "/proc/" not in java)
for j in glob.glob(os.path.join(G, "apps/**/*.java"), recursive=True):
    if "/test/" in j:
        continue
    t = code_of(read(j))
    if "utopia_mdb" in t:
        bad.append("names the receiver node: " + os.path.relpath(j, G))
ok("no app code opens /proc/utopia_mdb", not [b for b in bad if b.startswith("names the receiver node")])
# model data: hdmi_format only where the image ships the bridge
for tsv in glob.glob(os.path.join(G, "devices/*/signal.tsv")):
    c = tsv.split(os.sep)[-2]
    has = re.search(r"^hdmi_format\t", read(tsv), re.M) is not None
    conf = os.path.join(G, "devices", c, "build.conf")
    steps = os.path.join(G, "devices", c, "image/steps.tsv")
    st = read(steps) if os.path.exists(steps) else ""
    # the tar recipe (Z9X: tar_add PATH FILE MODE) or the tree recipe (HB: install MODE FILE {sys}/PATH)
    ships = os.path.exists(conf) and "apps/core/lumen-signal/image/lumen_hdmirx.rc" in read(conf) \
        and "apps/core/lumen-signal/image/lumen_hdmirx.sh" in read(conf) \
        and ("system/etc/init/lumen_hdmirx.rc\tlumen_hdmirx.rc" in st
             or "\tinstall\t0644\tlumen_hdmirx.rc\t{sys}/etc/init/lumen_hdmirx.rc" in st) \
        and ("system/etc/lumen/lumen_hdmirx.sh\tlumen_hdmirx.sh\t755" in st
             or "\tinstall\t0755\tlumen_hdmirx.sh\t{sys}/etc/lumen/lumen_hdmirx.sh" in st)
    ok("%s: hdmi_format %s and the image %s the bridge" % (c, "listed" if has else "not listed",
                                                           "ships" if ships else "does not ship"), has == ships)
    if has:
        ok("%s: hdmi_format names its port map" % c, re.search(r"^hdmi_format\t[^\t]*\bports=\d:\d(,\d:\d)*\b", read(tsv), re.M)
           is not None)
print(("ok   " if not bad else "FAIL ") + "check_hdmirx: %d problems" % len(bad))
sys.exit(1 if bad else 0)
