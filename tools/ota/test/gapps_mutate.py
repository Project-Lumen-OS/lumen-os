#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the Google services add-on's boot script (tools/ota/image/z9x_gapps.sh) against its host
test (tools/ota/test/gapps.sh): every mutant below changes one rule of the update-slot exception or of the boot
log, and the cases named with it must turn red. A mutant that stays green means the test does not hold that
rule. Each mutant runs in its own copy of the files gapps.sh reads, under sh only. On the Mac, no projector:
    python3 tools/ota/test/gapps_mutate.py           all mutants
    python3 tools/ota/test/gapps_mutate.py 3 7       only these (1-based)
Exit status 0 only when every mutant was killed and the unchanged copy is green."""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
GSI = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
SCRIPT = "tools/ota/image/z9x_gapps.sh"
FILES = ["tools/ota/image", "tools/ota/test/gapps.sh", "tools/ota/test/stubs", "tools/ota/test/gapps_fixture.py",
         "tools/ota/gapps_allow.py", "tools/lumen/nogms_remove.txt", "installer/lib/gapps_fill.sh",
         "installer/lib/gapps_allow.txt"]

# (what it breaks, text in z9x_gapps.sh, replacement, case prefixes that must fail)
MUTANTS = [
    ("update slot never recognised", '[ "$upd" = 1 ] && [ "$(getprop ro.z9x.ota.gate)" != 0 ] || return 1',
     '[ "$upd" = 9 ] && [ "$(getprop ro.z9x.ota.gate)" != 0 ] || return 1', ["ota_slot_keeps_addon"]),
    ("every slot an update slot", '[ "$upd" = 1 ] && [ "$(getprop ro.z9x.ota.gate)" != 0 ] || return 1',
     '[ "$upd" = 1 ] && [ "$(getprop ro.z9x.ota.gate)" != 0 ] || return 0', ["ota_slot_indicator_own"]),
    ("gate switch ignored", '[ "$upd" = 1 ] && [ "$(getprop ro.z9x.ota.gate)" != 0 ] || return 1',
     '[ "$upd" = 1 ] || return 1', ["ota_slot_gate_off"]),
    ("no_rollback ignored", '[ "$nr" != "$cur" ]', '[ "$nr" != "_x" ]', ["ota_slot_no_rollback_off"]),
    ("indicator compared with itself", '[ -z "$src" ] || [ "$src" = "$cur" ] || upd=1',
     '[ -z "$src" ] || [ "$src" = "$src" ] || upd=1', ["ota_slot_keeps_addon", "log_update_slot"]),
    ("no indicator = update slot", '[ -z "$src" ] || [ "$src" = "$cur" ] || upd=1', '[ "$src" = "$cur" ] || upd=1',
     ["off_after_failed_starts", "log_off"]),
    ("update slot still writes the marker",
     "    if ota_waits; then\n", '    if ota_waits; then\n      echo "$rn $cur $bid" > "$OFF"\n',
     ["ota_slot_keeps_addon"]),
    ("update slot turned off for this start", "Google services stay on (the OTA gate rolls the update back by itself)\"\n",
     "Google services stay on (the OTA gate rolls the update back by itself)\"; fail off \"update slot\"\n",
     ["ota_slot_keeps_addon", "ota_slot_attempt4"]),
    ("threshold moved", 'if [ "$s" = "$cur" ] && [ "$rn" -ge 3 ]; then', 'if [ "$s" = "$cur" ] && [ "$rn" -ge 4 ]; then',
     ["off_after_failed_starts", "log_off"]),
    ("no log line for ok", 'glog "ok: $n files ($fs)"\n', '\n', ["log_ok", "log_two_boots", "log_rotate"]),
    ("no log line for a failure", '  glog "$1${2:+: $2}"\n', '', ["log_bad", "log_off", "log_slow"]),
    ("log before the partition check", "LOGON=                 #", "LOGON=1                #", ["log_none_nothing"]),
    ("log never rotated", '-size +16384c 2>/dev/null)" ] || mv -f', '-size +99999999c 2>/dev/null)" ] || mv -f',
     ["log_rotate"]),
    ("log keeps growing in place", 'mv -f "$LOG" "${LOG%.txt}.old" 2>/dev/null', ': "$LOG" 2>/dev/null', ["log_rotate"]),
    ("log without the time", ': $1 ($ms ms)"', ': $1"', ["log_ok", "log_bad"]),
    ("log without the update flag", "update ${upd:-0}:", "update 0:", ["log_update_slot"]),
    ("log without the start number", "start ${rn:-?}", "start ?", ["log_ok"]),
    ("log overwritten every start", '2>/dev/null >> "$LOG"', '2>/dev/null > "$LOG"', ["log_two_boots"]),
    ("log open error printed", '2>/dev/null >> "$LOG"', '>> "$LOG"', ["log_unwritable"]),
]


def copy_tree(dst):
    for f in FILES:
        s, d = os.path.join(GSI, f), os.path.join(dst, f)
        os.makedirs(os.path.dirname(d), exist_ok=True)
        if os.path.isdir(s):
            shutil.copytree(s, d)
        else:
            shutil.copy2(s, d)


def run(tree, prefixes):
    env = dict(os.environ, GAPPS_SHELLS="sh")
    for p in prefixes:
        r = subprocess.run(["sh", os.path.join(tree, "tools/ota/test/gapps.sh"), p], env=env,
                           capture_output=True, text=True, timeout=600)
        if r.returncode != 0:
            return False, p
    return True, None


def main(argv):
    pick = [int(a) for a in argv] or list(range(1, len(MUTANTS) + 1))
    killed = survived = 0
    with tempfile.TemporaryDirectory(prefix="gapps_mutate.") as tmp:
        base = os.path.join(tmp, "base")
        copy_tree(base)
        allp = sorted({p for m in MUTANTS for p in m[3]})
        ok, bad = run(base, allp)
        if not ok:
            print(f"FAIL the unchanged copy is red on {bad}")
            return 1
        print(f"ok   unchanged copy green on {len(allp)} case groups")
        for i in pick:
            what, old, new, cases = MUTANTS[i - 1]
            tree = os.path.join(tmp, f"m{i}")
            copy_tree(tree)
            path = os.path.join(tree, SCRIPT)
            text = open(path, encoding="utf-8").read()
            if text.count(old) != 1:
                print(f"FAIL {i:2} {what}: the text occurs {text.count(old)} times")
                survived += 1
                continue
            open(path, "w", encoding="utf-8").write(text.replace(old, new))
            green, _ = run(tree, cases)
            if green:
                survived += 1
                print(f"FAIL {i:2} {what}: SURVIVED ({', '.join(cases)} stay green)")
            else:
                killed += 1
                print(f"ok   {i:2} {what}: killed")
            shutil.rmtree(tree)
    print(f"gapps_mutate: {killed} killed, {survived} survived")
    return 0 if survived == 0 else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
