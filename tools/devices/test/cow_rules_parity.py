#!/usr/bin/env python3
"""The rules for the *-cow leftovers of a finished update, kept the same in every installer (reads only).

The in-trial installer (apps/LumenDsuInstall Snapshots.java) is the reference: it reads /metadata/ota like stock
libsnapshot and clears only what Snapshots.clearable proves stale. It and Lumen Installer's early word (core/StockUpdate.java
judging the probe of assets/lumen_installer.sh) read the state file and name the files and snapshot devices through one
module (apps/core/lumen-snapshot UpdateState); the probe script must name the same files and devices.
The PC installer (installer/lumen-install.sh and .ps1, Z9X flow in fastbootd) sees only 'getvar all': it deletes a
*-cow only when snapshot-update-status is none and the name is <partition><running slot>-cow with that partition in
the table, the part of the rule fastbootd can see. Before fastbootd the ab-fastbootd flow also reads what adb shows of
an update in use (the snapshot devices of UpdateState.SNAPSHOT_DM, as root the boot indicator and the state file) and
then keeps every *-cow.
In a tree without the in-trial installer and Lumen Installer (the public one) only the PC installer's part is checked.

    python3 tools/devices/test/cow_rules_parity.py
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
MOD = "apps/core/lumen-snapshot/src/org/lumen/snapshot/UpdateState.java"
SNAP = "apps/LumenDsuInstall/src/org/lumen/dsuinstall/Snapshots.java"
STOCK = "apps/LumenInstaller/src/org/lumen/installer/core/StockUpdate.java"
LISH = "apps/LumenInstaller/assets/lumen_installer.sh"
PCSH = "installer/lumen-install.sh"
PCPS = "installer/lumen-install.ps1"
CCT = "apps/LumenDsuInstall/test/CowClearTests.java"
HAVE = all(os.path.isfile(os.path.join(ROOT, x)) for x in (MOD, SNAP, STOCK, LISH, CCT))
BAD = []


def read(p):
    with open(os.path.join(ROOT, p), encoding="utf-8") as f:
        return f.read()


def strings(block):
    return re.findall(r'"([^"]*)"', block)


def array(src, name, path):
    m = re.search(r"\b" + name + r"\s*=\s*\{(.*?)\};", src, re.S)
    if not m:
        BAD.append("%s: %s not found" % (path, name))
        return []
    return strings(m.group(1))


def same(what, a, b):
    if a != b:
        BAD.append("%s differ: %s vs %s" % (what, a, b))
    else:
        print("ok   " + what + ": " + ", ".join(a))


pcsh, pcps = read(PCSH), read(PCPS)
DM = ["-base", "-src", "-cow-img", "-user-cow", "-user-cow-init"]   # UpdateState.SNAPSHOT_DM, checked against it below


def private_checks():
    """the in-trial installer and Lumen Installer against the module (only where they are in the tree)"""
    global DM
    mod, snap, stock, lish = read(MOD), read(SNAP), read(STOCK), read(LISH)

    # one reading of the state file and one set of names: both apps use the module and keep no copy of their own
    for path, src in ((SNAP, snap), (STOCK, stock)):
        if "import org.lumen.snapshot.UpdateState;" not in src:
            BAD.append(path + ": does not use the lumen-snapshot module")
        for own in ("varint(", '"merge-completed"', '"-user-cow-init"', '"rollback-indicator"'):
            if own in src:
                BAD.append("%s: keeps its own copy (%s) of what apps/core/lumen-snapshot holds" % (path, own))
    if not BAD:
        print("ok   one state reader and one set of names (apps/core/lumen-snapshot) in Snapshots and StockUpdate")

    # update states by number (snapshot.proto), and which ones are pending
    names = array(mod, "NAMES", MOD)
    if names != ["none", "initiated", "unverified", "merging", "merge-needs-reboot", "merge-completed", "merge-failed", "cancelled"]:
        BAD.append(MOD + ": NAMES not the snapshot.proto order: %s" % names)
    for n, v in (("NONE", 0), ("INITIATED", 1), ("UNVERIFIED", 2), ("MERGING", 3), ("MERGE_COMPLETED", 5), ("MERGE_FAILED", 6)):
        if not re.search(r"\b%s = %d\b" % (n, v), mod):
            BAD.append("%s: %s is not %d" % (MOD, n, v))
    if "UpdateState.pending(state)" not in snap or "UpdateState.pending(state)" not in stock:
        BAD.append("Snapshots / StockUpdate do not judge pending states by UpdateState.pending")
    else:
        print("ok   pending states: UpdateState.pending in both")
    if "if (!UpdateState.finished(u.state))" not in snap or "if (!UpdateState.finished(state)) r.why" not in stock:
        BAD.append("clearing not limited to the finished states (UpdateState.finished) in Snapshots / StockUpdate")
    else:
        print("ok   clearing only in a finished state (UpdateState.finished: none, merge-completed) in both")

    # the root probe of Lumen Installer (a shell script) names the same files and snapshot devices
    markers = array(mod, "MARKERS", MOD)
    lm = re.search(r"for f in ([a-z\- ]+); do\n\s*\[ -e \"\$METADATA/ota/\$f\" \]", lish)
    same("update files (UpdateState / lumen_installer.sh)", markers, lm.group(1).split() if lm else [])
    dm_snap = array(mod, "SNAPSHOT_DM", MOD)
    cm = re.search(r"case \"\$\{d##\*/\}\" in ([^)]+)\)", lish)
    same("snapshot devices (UpdateState / lumen_installer.sh)", dm_snap, [x.lstrip("*") for x in cm.group(1).split("|")] if cm else [])

    # the running slot's suffix, the in-trial part of the rule
    if 'if (!base.endsWith(suffix)) return' not in snap or 'n.endsWith(slot + UpdateState.COW)' not in stock:
        BAD.append("running-slot rule missing in Snapshots / StockUpdate")
    cow = re.search(r'\bCOW = "([^"]+)"', mod)
    if not cow or cow.group(1) != "-cow":
        BAD.append(MOD + ": COW is not -cow")
    if dm_snap != DM:
        BAD.append("%s: SNAPSHOT_DM %s is not the PC installer's list %s" % (MOD, dm_snap, DM))
    DM = dm_snap


if HAVE:
    private_checks()
else:
    print("skip the installers that run on the projector: not in this tree")

# the PC installer: its rule data (lib/cow_rules.conf) against the in-trial rule; both installers read it and gate
# every *-cow delete with it (the rule itself runs in installer_cow_rules.sh / .ps1 on cow_cases.tsv)
CONF = "installer/lib/cow_rules.conf"
conf = dict(l.split("=", 1) for l in read(CONF).splitlines() if "=" in l and not l.startswith("#"))
if conf.get("suffix") != "-cow":
    BAD.append("%s: suffix %r is not libsnapshot's -cow (UpdateState.COW)" % (CONF, conf.get("suffix")))
if conf.get("status") != "none":
    BAD.append(CONF + ": status is not none (fastbootd's word for the boot HAL's MergeStatus::NONE)")
if conf.get("base") != "[a-z0-9_]+":
    BAD.append(CONF + ": base is not [a-z0-9_]+ (fastbootd's partition names)")
else:
    print("ok   %s: suffix %s (UpdateState.COW), base %s, status %s" % (CONF, conf["suffix"], conf["base"], conf["status"]))
SH_GATES = ('    delete:*-cow) cow_leftover "$2" "$s" && return 0 ;;', 'delete:*-cow) cow_leftover "$2" "$ABF_S" && return 0 ;;',
            '  cow_load\n  p=$(cow_odd "$S")\n  [ -z "$p" ] || { fb reboot; die "$(m cow_partition_not_slot "$p" "${S#_}")"; }',
            '  cow_load\n  if [ -n "$COW_ADB" ] && [ -n "$(cow_parts)" ]; then', '  fi\n  c=$(cow_odd "_$cs")', '"$HERE/lib/cow_rules.conf"',
            '  [ -z "${COW_ADB-}" ] || return 1\n', '    COW_ADB=$(cow_adb_seen)\n    say "$(m restarting_fastboot_mode_about_30)"',
            '  COW_ADB=$(cow_adb_seen)\n  say "$(m restarting_fastboot_mode_about_30)"')
PS_GATES = ('-and (Cow-Leftover $script:FlashGv $part $s))', '-and (Cow-Leftover $script:AbfGv $part $sx)) { return $true }',
            "  Cow-Load\n  $script:FlashGv = $gv\n  $p = Cow-Odd $gv $S\n  if ($p) { Fb reboot | Out-Null; Die (M cow_partition_not_slot $p $S.TrimStart('_')) }",
            '  Cow-Load\n  if ($script:CowAdb -and @(Cow-Parts $gv).Count) {', '  }\n  $c = Cow-Odd $gv "_$cs"', '(Join-Path $Here "lib") "cow_rules.conf"',
            '  if ($script:CowAdb) { return $false }\n', '    $script:CowAdb = Cow-Adb-Seen\n    Say (M restarting_fastboot_mode_about_30)',
            '  $script:CowAdb = Cow-Adb-Seen\n  Say (M restarting_fastboot_mode_about_30)')
for path, src, gates in ((PCSH, pcsh, SH_GATES), (PCPS, pcps, PS_GATES)):
    miss = [g for g in gates if g not in src]
    if miss:
        BAD.append("%s: the *-cow rule is not wired as expected: %s" % (path, miss[0]))
    if re.search(r"-cow\b[^\n]*\) return 0 ;;|-clike \"\*\$s-cow\"|-match \"partition-size:\(\[a-z0-9_\]\+-cow\)", src):
        BAD.append(path + ": a *-cow delete outside the rule")
if not [b for b in BAD if "the *-cow rule" in b or "outside the rule" in b]:
    print("ok   PC installer (sh, ps1): every *-cow delete gated by the rule data, STOP before the first write otherwise")

# what adb shows of an update in use: the snapshot devices of UpdateState.SNAPSHOT_DM, finished states none and
# merge-completed (empty, 08 00, 08 05: UpdateState.NONE 0, MERGE_COMPLETED 5)
shm = re.search(r'case "\$\{d##\*/\}" in ([^)]+)\) echo "/dev/block/mapper/', pcsh)
psm = re.search(r'foreach \(\$x in @\(([^)]*)\)\) \{ if \(\$n\.EndsWith', pcps)
same("snapshot devices (UpdateState / lumen-install.sh)", DM, [x.lstrip("*") for x in shm.group(1).split("|")] if shm else [])
same("snapshot devices (UpdateState / lumen-install.ps1)", DM, strings(psm.group(1)) if psm else [])
if "    ''|0800|0805) ;;" not in pcsh or '@("", "08 00", "08 05") -ccontains $h' not in pcps:
    BAD.append("the PC installer does not take exactly none and merge-completed as a finished state")
else:
    print("ok   PC installer (sh, ps1): finished states none and merge-completed, boot indicator snapshot-boot")

# the shared cases: every clause of the rule has a case, and the in-trial installer runs the java=yes rows
CASES = "tools/devices/test/cow_cases.tsv"
rows = [l.split("\t") for l in read(CASES).splitlines() if l and not l.startswith("#")]
names = {r[0] for r in rows}
need = {"stale_two", "no_cow", "other_slot", "orphan", "prefix_trick", "upper_case", "base_of_size_0", "cow_of_cow",
        "no_slot", "snapshotted", "merging", "upper_suffix"}
if need - names or any(len(r) != 5 for r in rows):
    BAD.append("%s: missing cases %s or a row without 5 columns" % (CASES, sorted(need - names)))
elif HAVE and "cow_cases.tsv" not in read(CCT):
    BAD.append("CowClearTests does not run " + CASES)
else:
    print("ok   %s: %d cases, %d run by the in-trial installer too" % (CASES, len(rows), sum(r[4] == "yes" for r in rows)))
for path, src in ((PCSH, pcsh), (PCPS, pcps)):
    if src.count("cow_partition_not_slot") < 1:
        BAD.append(path + ": no STOP text for another *-cow")

if BAD:
    for b in BAD:
        print("FAIL " + b)
    print("cow rules parity: FAILED")
    sys.exit(1)
print("cow rules parity: OK")
