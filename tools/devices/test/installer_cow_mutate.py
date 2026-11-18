#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation test of the PC installer's *-cow rule (installer/lumen-install.sh and lumen-install.ps1).

Each mutant breaks one clause of the rule in a copy of the installer (the script, lib/cow_rules.conf) and
installer_cow_rules.sh (or installer_cow_rules.ps1 under pwsh) must turn red. The unmutated copy must be green first.
    python3 tools/devices/test/installer_cow_mutate.py            the sh mutants (bash)
    PWSH=/path/to/pwsh python3 tools/devices/test/installer_cow_mutate.py ps1     the ps1 mutants
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
GSI = os.path.abspath(os.path.join(HERE, "..", "..", ".."))

SH = [
    ("sh_slot_any", '  case "$b" in *"$2") ;; *) return 1 ;; esac\n', ""),
    ("sh_base_any", '  printf \'%s\\n\' "$b" | LC_ALL=C grep -Eqx -- "$COW_BASE" || return 1\n', ""),
    ("sh_base_icase", 'LC_ALL=C grep -Eqx -- "$COW_BASE"', 'LC_ALL=C grep -Eiqx -- "$COW_BASE"'),
    ("sh_size_0_ok", '  sz=$(gv_size "$b")\n  [ -n "$sz" ] && [ $((sz)) -gt 0 ]', '  sz=$(gv_size "$b")\n  [ -n "$sz" ]'),
    ("sh_base_not_in_table", '  sz=$(gv_size "$b")\n  [ -n "$sz" ] && [ $((sz)) -gt 0 ]\n', "  true\n"),
    ("sh_status_any", '  [ "$(gv_val snapshot-update-status)" = "$COW_STATUS" ] || return 1\n', ""),
    ("sh_odd_never", '  for p in $(cow_parts); do cow_leftover "$p" "$1" || { echo "$p"; return 0; }; done\n', ""),
    ("sh_unloaded_open", '[ -n "$COW_SFX" ] || { echo lib/cow_rules.conf; return 0; }', '[ -n "$COW_SFX" ] || return 0'),
    ("sh_load_soft", '[ -n "$v" ] || die "lib/cow_rules.conf has no $k: download the installer again"', ":"),
    ("sh_parts_lowercase", r"partition-size:\([^:]*$COW_SFX\):", r"partition-size:\([a-z0-9_]*$COW_SFX\):"),
    ("sh_z9x_allow_any", '    delete:*-cow) cow_leftover "$2" "$s" && return 0 ;;', "    delete:*-cow) return 0 ;;"),
    ("sh_abf_allow_any", 'delete:*-cow) cow_leftover "$2" "$ABF_S" && return 0 ;;', "delete:*-cow) return 0 ;;"),
    ("sh_z9x_no_stop", '{ fb reboot; die "$(m cow_partition_not_slot "$p" "${S#_}")"; }', '{ say "$(m cow_partition_not_slot "$p" "${S#_}")"; }'),
    ("sh_z9x_no_delete", 'fb_do delete "$p" || { fb reboot; die "delete $p"; }', 'true || { fb reboot; die "delete $p"; }'),
    ("sh_abf_no_delete", '    fb_do delete "$p" || abf_fb_stop "delete $p"\n', ""),
    # what adb showed before fastbootd (cow_adb_seen, COW_ADB): the wiring is checked by cow_rules_parity.py and the mock
    # projector suites, these break the rule itself
    ("sh_adb_kept_ignored", '  [ -z "${COW_ADB-}" ] || return 1\n', ""),
    ("sh_abf_adb_no_stop", '    abf_fb_stop "$(m fastboot_mode_check_failed): $why."\n  fi\n  c=$(cow_odd "_$cs")', '    :\n  fi\n  c=$(cow_odd "_$cs")'),
    ("sh_abf_adb_always", 'if [ -n "$COW_ADB" ] && [ -n "$(cow_parts)" ]; then', 'if [ -n "$COW_ADB" ]; then'),
    ("sh_abf_adb_stock_words", '    if [ "$(state_get dev_lumen)" = 1 ]; then why="$(m update_finishing) ($COW_ADB)', '    if false; then why="$(m update_finishing) ($COW_ADB)'),
    ("sh_dm_no_base", 'case "${d##*/}" in *-base|*-src|', 'case "${d##*/}" in *-src|'),
    ("sh_dm_no_user_cow_init", '|*-user-cow|*-user-cow-init)', '|*-user-cow)'),
    ("sh_dm_never", 'echo "/dev/block/mapper/${d##*/}"; return 0 ;; esac', ':;; esac'),
    ("sh_ota_boot_ignored", '*" snapshot-boot "*) echo /metadata/ota/snapshot-boot; return 0 ;; esac', '*" snapshot-boot "*) ;; esac'),
    ("sh_ota_boot_prefix", '*" snapshot-boot "*)', '*" snapshot-boot"*)'),
    ("sh_ota_state_any", "    ''|0800|0805) ;;", "    *) ;;"),
    ("sh_ota_initiated_ok", "    ''|0800|0805) ;;", "    ''|0800|0801|0805) ;;"),
    ("sh_ota_empty_busy", "    ''|0800|0805) ;;", "    0800|0805) ;;"),
    ("sh_seen_root_any", "  [ \"$(adbs shell id -u 2>/dev/null | tr -d '\\r')\" = 0 ] || return 0\n", ""),
    ("sh_seen_no_root_read", "  cow_ota_busy \"$(adbs shell 'ls /metadata/ota/", "  true \"$(adbs shell 'ls /metadata/ota/"),
]
PS = [
    ("ps_slot_any", "  if (-not $b.EndsWith($sx, [StringComparison]::Ordinal)) { return $false }\n", ""),
    ("ps_base_any", '  if ($b -cnotmatch "^(?:$($script:CowBase))$") { return $false }\n', ""),
    ("ps_base_icase", '$b -cnotmatch "^(?:$($script:CowBase))$"', '$b -notmatch "^(?:$($script:CowBase))$"'),
    ("ps_size_0_ok", "return ($null -ne $sz -and $sz -gt 0)", "return ($null -ne $sz)"),
    ("ps_status_any", '  if ((Gv-Val $gv "snapshot-update-status") -cne $script:CowStatus) { return $false }\n', ""),
    ("ps_odd_never", "  foreach ($p in (Cow-Parts $gv)) { if (-not (Cow-Leftover $gv $p $sx)) { return $p } }\n", ""),
    ("ps_unloaded_open", 'if (-not $script:CowSfx) { return "lib\\cow_rules.conf" }', 'if (-not $script:CowSfx) { return "" }'),
    ("ps_load_soft", '    if (-not $v) { Die "lib\\cow_rules.conf has no ${k}: download the installer again" }\n', ""),
    ("ps_parts_icase", 'ForEach-Object { if ("$_" -cmatch $re) { $Matches[1] } }', 'ForEach-Object { if ("$_" -match $re) { $Matches[1] } }'),
    ("ps_abf_allow_any", " -and (Cow-Leftover $script:AbfGv $part $sx)) { return $true }", ") { return $true }"),
    ("ps_z9x_allow_any", " -and (Cow-Leftover $script:FlashGv $part $s))", ")"),
    ("ps_z9x_no_stop", "if ($p) { Fb reboot | Out-Null; Die (M cow_partition_not_slot $p $S.TrimStart('_')) }", "if ($p) { Say (M cow_partition_not_slot $p $S.TrimStart('_')) }"),
    ("ps_z9x_no_delete", 'if (-not (Fb-Do delete $p)) { Fb reboot | Out-Null; Die "delete $p" }', 'if ($false) { Fb reboot | Out-Null; Die "delete $p" }'),
    ("ps_abf_no_delete", '    if (-not (Fb-Do delete $p)) { Abf-Fb-Stop "delete $p" }\n', ""),
    # R2: partition names case-sensitive, as gv_size
    ("ps_gvsize_icase", '"$_" -clike "(bootloader) partition-size:${name}:*"', '"$_" -like "(bootloader) partition-size:${name}:*"'),
    # what adb showed before fastbootd (Cow-Adb-Seen, CowAdb)
    ("ps_adb_kept_ignored", "  if ($script:CowAdb) { return $false }\n", ""),
    ("ps_abf_adb_no_stop", '    Abf-Fb-Stop ((M fastboot_mode_check_failed) + ": $why.")\n  }\n  $c = Cow-Odd $gv "_$cs"', '    $null = $why\n  }\n  $c = Cow-Odd $gv "_$cs"'),
    ("ps_abf_adb_always", "if ($script:CowAdb -and @(Cow-Parts $gv).Count) {", "if ($script:CowAdb) {"),
    ("ps_abf_adb_stock_words", '    if ($lum) { $why = (M update_finishing) + " ($($script:CowAdb)): "', '    if ($false) { $why = (M update_finishing) + " ($($script:CowAdb)): "'),
    ("ps_dm_no_base", '@("-base", "-src",', '@("-src",'),
    ("ps_dm_never", '{ return "/dev/block/mapper/$n" } }', '{ } }'),
    ("ps_ota_boot_ignored", '-ccontains "snapshot-boot") { return "/metadata/ota/snapshot-boot" }', '-ccontains "snapshot-boot") { }'),
    ("ps_ota_boot_like", '-ccontains "snapshot-boot")', '-clike "snapshot-boot*")'),
    ("ps_ota_state_any", 'if (@("", "08 00", "08 05") -ccontains $h) { return "" }', 'return ""'),
    ("ps_ota_initiated_ok", '@("", "08 00", "08 05")', '@("", "08 00", "08 01", "08 05")'),
    ("ps_ota_empty_busy", '@("", "08 00", "08 05")', '@("08 00", "08 05")'),
    ("ps_seen_root_any", '  if (((Adb shell id -u) | Out-String).Trim() -ne "0") { return "" }\n', ""),
]


def copy(w):
    inst = os.path.join(w, "installer")
    os.makedirs(os.path.join(inst, "lib"))
    for f in ("lumen-install.sh", "lumen-install.ps1", "lib/cow_rules.conf"):
        shutil.copy(os.path.join(GSI, "installer", f), os.path.join(inst, f))
    return inst


def run(kind, inst):
    if kind == "sh":
        cmd = ["bash", os.path.join(HERE, "installer_cow_rules.sh"), os.path.join(inst, "lumen-install.sh")]
    else:
        cmd = [os.environ.get("PWSH", "pwsh"), "-NoProfile", "-File", os.path.join(HERE, "installer_cow_rules.ps1"),
               "-Src", os.path.join(inst, "lumen-install.ps1")]
    r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    first = next((l for l in r.stdout.splitlines() if l.startswith("FAIL")), r.stdout.strip().splitlines()[-1:] or [""])
    return r.returncode, first if isinstance(first, str) else first[0]


def main():
    kind = sys.argv[1] if len(sys.argv) > 1 else "sh"
    muts, script = (SH, "lumen-install.sh") if kind == "sh" else (PS, "lumen-install.ps1")
    with tempfile.TemporaryDirectory() as w:
        inst = copy(w)
        rc, why = run(kind, inst)
        if rc != 0:
            print("baseline RED: " + why)
            return 2
        print("baseline green")
        path = os.path.join(inst, script)
        orig = open(path, encoding="utf-8").read()
        survived = []
        for name, old, new in muts:
            n = orig.count(old)
            if n != 1:
                print("BROKEN %-24s anchor found %d times" % (name, n))
                survived.append(name)
                continue
            open(path, "w", encoding="utf-8").write(orig.replace(old, new))
            rc, why = run(kind, inst)
            if rc == 0:
                print("SURVIVED %s" % name)
                survived.append(name)
            else:
                print("killed %-24s %s" % (name, why[:110]))
        open(path, "w", encoding="utf-8").write(orig)
    print("installer cow mutants (%s): %d, %d killed, survived: %s" % (kind, len(muts), len(muts) - len(survived), survived or "none"))
    return 1 if survived else 0


if __name__ == "__main__":
    sys.exit(main())
