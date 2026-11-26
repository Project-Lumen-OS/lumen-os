#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""blobs_reader_mutate.py: every mutant of the one blobs reader and of its per-model data must turn a test red.

    python3 tools/devices/test/blobs_reader_mutate.py [-j N] [NAME_PREFIX]

Each mutant edits a hard-linked scratch copy of the tree (the edited file is a new file, the tree itself is never
written) and runs, under one shell: tools/devices/test/blobs_trial_test.sh (the trial-run path), tools/ota/test/blobs.sh
(the Z9X settings and partition path), devices/g0111/test/x50_blobs_test.sh (the X50 family, its trial-run path with
lpdump / dmctl) and tools/devices/test/blobs_parity.py --shells sh (every model's installed path against the golden of
the readers before the move), tools/devices/test/installer_x50_blobs.sh (the X50 installer's 16 MiB partition through the reader) and
tools/ota/test/public_members.sh (tools/lumen_v1.sh's checks of the reader in a Z9X public image). A mutant is killed
when one of them fails. Exit 0 only when the unchanged copy is green
and every mutant is killed. Equivalent mutants are listed with the reason and not run.
"""
import concurrent.futures
import os
import shutil
import subprocess
import sys
import tempfile

G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
R = "tools/ota/image/z9x_blobs.sh"
TESTS = [["sh", "tools/devices/test/blobs_trial_test.sh"], ["sh", "tools/ota/test/blobs.sh"],
         ["sh", "devices/g0111/test/x50_blobs_test.sh"], ["python3", "tools/devices/test/blobs_parity.py", "--shells", "sh"],
         ["bash", "tools/devices/test/installer_x50_blobs.sh"], ["bash", "tools/ota/test/public_members.sh"]]
FROM = '[ "${_fr%%:*}" = "$_ch" ] && { _sc=${_fr#*:}; break; }'
BINDOK = '0:/vendor/etc/*|0:/apex/com.android.vndk.v*/lib/*|0:/apex/com.android.vndk.v*/lib64/*) ;;'

# (name, [(file, old, new), ...]): every old must occur exactly once in its file
MUTANTS = [
    ("trial_on_installed", [(R, '  [ "$(getprop ro.gsid.image_running)" = 1 ] || fail missing "no $name"\n', "")]),
    ("no_models_check", [(R, 'case " $(getprop ro.lumen.models) " in *" $mdl "*) ;;', 'case " $(getprop ro.lumen.models) " in *) ;;')]),
    ("no_code_check", [(R, "case $mdl in [A-Z][0-9][0-9][0-9][0-9]) ;;", "case $mdl in *) ;;")]),
    ("stock_over_partition", [(R, 'if ! { [ -b "$dev" ] || { [ -n "$R" ] && [ -f "$dev" ]; }; }; then', "if true; then")]),
    ("src_never", [(R, '[ "$src" = partition ] && [ "$SRCP" = 0 ] || setprop', '[ "$src" = partition ] || setprop')]),
    ("src_always", [(R, '[ "$src" = partition ] && [ "$SRCP" = 0 ] || setprop', "setprop")]),
    ("no_sha", [(R, '  if [ "$got" = "$h" ]; then', "  if true; then")]),
    ("no_lib64_bind", [(R, BINDOK, "0:/vendor/etc/*|0:/apex/com.android.vndk.v*/lib/*) ;;")]),
    ("any_bind", [(R, BINDOK, "0:*) ;;")]),
    ("from_first_only", [(R, FROM, '[ "${_fr%%:*}" = "$_ch" ] && _sc=${_fr#*:}; break')]),
    ("from_no_sha", [(R, FROM, "{ _sc=${_fr#*:}; break; }")]),
    ("from_last_wins", [(R, FROM, "_sc=${_fr#*:}")]),
    ("from_any_script", [(R, "        case \"$_sc\" in ''|*[!0-9,d\\;]*) continue ;; esac\n", "")]),
    ("flat_no_manifest_name", [(R, ' \\\n            && grep -q -F "$1" "$_d/$_n/apex_manifest.pb" 2>/dev/null; then', "; then")]),
    ("flat_symlinked_folder", [(R, 'if [ -d "$_d/$_n" ] && [ ! -L "$_d/$_n" ] && stock_regular', 'if [ -d "$_d/$_n" ] && stock_regular'),
                               (R, '  [ -f "$1" ] && [ ! -L "$1" ] || return 1', '  [ -f "$1" ] || return 1'),
                               (R, '  [ "$_rp" = "$_rt/${1#"$2"/}" ]\n}', "  :\n}"),
                               (R, '  case "$_rp" in "$_rt"/*) ;; *) return 1 ;; esac\n', "")]),
    ("no_symlink_checks", [(R, '  [ -f "$1" ] && [ ! -L "$1" ] || return 1', '  [ -f "$1" ] || return 1'),
                           (R, '  [ "$_rp" = "$_rt/${1#"$2"/}" ]\n}', "  :\n}")]),
    ("no_maxf_stock", [(R, ' && [ $(($(wc -c < "$1"))) -le "$MAXF" ]', "")]),
    ("no_umount", [(R, '  for _d in $_mnts; do umount "$_d" 2>/dev/null || { _left=1; say "$_d stays mounted (umount failed)"; }; done\n', "")]),
    ("no_setro", [(R, '    blockdev --setro "$1" 2>/dev/null\n', "")]),
    ("rw_mount", [(R, "  _o=ro,nodev,nosuid,noexec\n", "  _o=rw,nodev,nosuid,noexec\n")]),
    ("block_ext4_journal", [(R, '|| timeout 3 mount -t ext4 -o "$_o,noload"', '|| timeout 3 mount -t ext4 -o "$_o"')]),
    ("loop_ext4_journal", [(R, '    timeout 3 mount -t ext4 -o "$_o,noload" "$1" "$2" 2>/dev/null || timeout 3 mount -t erofs',
                            '    timeout 3 mount -t ext4 -o "$_o" "$1" "$2" 2>/dev/null || timeout 3 mount -t erofs')]),
    ("collect_fail_ignored", [(R, '    || fail missing "no $name, and the stock slot $cur of this trial run was not read ($STOCK_WHY)"', "    || :")]),
    ("lpdump_each_call", [(R, '  [ -n "$_lpdone" ] || { _lp=$(timeout 3 lpdump --slot="$_sn" 2>/dev/null); _lpdone=1; }',
                           '  _lp=$(timeout 3 lpdump --slot="$_sn" 2>/dev/null)')]),
    ("dm_not_deleted", [(R, '  for _d in $_dms; do timeout 3 dmctl delete "$_d" >/dev/null 2>&1 || say "dm device $_d stays (delete failed)"; done\n', "")]),
    ("dm_rw", [(R, 'timeout 3 dmctl create "lumen_ro_$1" -ro $_t', 'timeout 3 dmctl create "lumen_ro_$1" $_t')]),
    ("apex_always_flat", [(R, '      if [ "$APEXK" = flat ]; then', "      if true; then")]),
    ("apex_never_flat", [(R, '      if [ "$APEXK" = flat ]; then', "      if false; then")]),
    ("once_per_boot_gone", [(R, '  [ "$(getprop sys.z9x.blobs)" = ok ] && exit 0\n', "")]),
    ("eye_once_gone", [(R, "  [ -n \"$(getprop sys.lumen.nui)\" ] && exit 0   # a second run must not touch a running drawer's fifos\n", "")]),
    ("conf_unknown_key_ok", [(R, "''|\\#*) ;; *) cbad=${cbad:-key:$k} ;;", "*) ;;")]),
    ("conf_allow_fixed", [(R, "ALLOW=$R/system/etc/z9x/$allown", "ALLOW=$R/system/etc/z9x/blobs_allow.txt")]),
    ("conf_blocks_fixed", [(R, "count=$BLOCKS ", "count=64 ")]),
    ("conf_maxf_fixed", [(R, "-size +${MAXF}c", "-size +4194304c")]),
    ("conf_tmpfs_fixed", [(R, "size=$TMPFS,nosuid,nodev tmpfs $M", "size=8m,nosuid,nodev tmpfs $M")]),
    ("conf_eye_ignored", [(R, 'case "$eye" in 0|1) EYE=$eye ;;', 'case "$eye" in 0|1) EYE=0 ;;')]),
    ("conf_no_apex_check", [(R, "case \"$APEXK\" in zip|flat) ;; *) cbad=${cbad:-stock_apex} ;; esac\n", "")]),
    ("eye_line_on_eye0", [(R, '  if [ $e = 1 ] && [ "$EYE" = 0 ]; then bad=${bad:-option:$p}; continue; fi\n', "")]),
    ("tag_ignored", [(R, "*) TAG=$tag ;; esac", "*) ;; esac")]),
    # the per-model data
    ("hb_conf_zip", [("devices/goat_elegant/image/blobs.conf", "stock_apex=flat\n", "stock_apex=zip\n")]),
    ("z9x_conf_flat", [("tools/ota/image/blobs.conf", "stock_apex=zip\n", "stock_apex=flat\n")]),
    ("x50_conf_eye0", [("devices/g0111/image/blobs.conf", "eye=1\n", "eye=0\n")]),
    ("x50_conf_src0", [("devices/g0111/image/blobs.conf", "src_partition=1\n", "src_partition=0\n")]),
    ("hb_models_short", [("devices/goat_elegant/image/hb_prop.txt", "ro.lumen.models=G0130 G0148\n", "ro.lumen.models=G0130\n")]),
    ("x50_models_gone", [("devices/g0111/image/x50_prop_pub.txt", "+ro.lumen.models=G0111\n", "")]),
    ("x50max_models_x50", [("devices/g0111/image/x50max_prop_pub.txt", "+ro.lumen.models=G0100\n", "+ro.lumen.models=G0111\n")]),
    ("titan_models_gone", [("devices/h0062/image/h0062_prop_pub.txt", "+ro.lumen.models=H0062\n", "")]),
    ("rs30_models_gone", [("devices/g0155/image/steps.tsv", "*\tline\t{mn}/system/product/etc/build.prop\tro.lumen.models={MODEL}\n", "")]),
    ("rs30u_model_other", [("devices/g0170/build.conf", "var.MODEL=G0170\n", "var.MODEL=G0155\n")]),
    ("z9x_models_gone", [("devices/horse_popular/image/steps.tsv",
                          "*\ttar_line\t{tar}\tsystem/product/etc/build.prop\tro.lumen.models=G0082\n", "")]),
    ("z9x_reader_not_added", [("devices/horse_popular/image/steps.tsv", "*\ttar_add\tsystem/etc/z9x/blobs.conf\tblobs.conf\t644\n", "")]),
    ("hb_steps_no_conf", [("devices/goat_elegant/image/steps.tsv", "lumen,pub\tinstall\t0644\tblobs.conf\t{sys}/etc/z9x/blobs.conf\n", "")]),
    ("titan_old_reader", [("devices/_family/titan/titan.build.conf", "tools/ota/image/z9x_blobs.sh", "devices/g0111/image/x50_blobs.sh")]),
]
EQUIVALENT = {
    "the final remount ro of the tmpfs": "the tmpfs is not mounted in the host tests (Z9X_ROOT set), as before the move",
    "the scratch tmpfs of a trial run": "mounted only on the projector (Z9X_ROOT empty); the host tests check the folder is gone",
}


def run_tests(tree):
    env = dict(os.environ, BLOBS_TRIAL_SHELLS="sh", BLOBS_SHELLS="sh")
    for t in TESTS:
        p = subprocess.run(t, cwd=tree, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=900)
        if p.returncode != 0:
            return t[1], p.stdout
    return None, ""


def mutate(base, clean, name, edits):
    tree = os.path.join(base, name)
    subprocess.run(["cp", "-al", clean, tree], check=True)
    for rel, old, new in edits:
        p = os.path.join(tree, rel)
        s = open(p, encoding="utf-8").read()
        if s.count(old) != 1:
            return name, "BROKEN", "%s: %r found %d times" % (rel, old[:60], s.count(old))
        mode = os.stat(p).st_mode
        os.unlink(p)   # a new file: the hard link to the clean copy stays as it is
        with open(p, "w", encoding="utf-8") as f:
            f.write(s.replace(old, new))
        os.chmod(p, mode)
    killer, out = run_tests(tree)
    shutil.rmtree(tree, ignore_errors=True)
    return name, ("killed" if killer else "LIVE"), killer or ""


def main(argv):
    jobs, only = 8, ""
    a = argv[1:]
    while a:
        if a[0] == "-j":
            jobs = int(a[1]); a = a[2:]
        else:
            only = a[0]; a = a[1:]
    base = tempfile.mkdtemp(prefix="blobs_reader_mut.")
    try:
        clean = os.path.join(base, "clean")
        files = subprocess.run(["git", "ls-files", "-co", "--exclude-standard"], cwd=G, stdout=subprocess.PIPE,
                               text=True, check=True).stdout.split("\n")
        for rel in files:
            src = os.path.join(G, rel)
            if not rel or not os.path.isfile(src) or os.path.islink(src):
                continue
            dst = os.path.join(clean, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
        killer, out = run_tests(clean)
        if killer:
            print(out[-3000:])
            print("the unchanged copy is not green (%s)" % killer)
            return 1
        print("unchanged copy: green")
        todo = [(n, e) for n, e in MUTANTS if n.startswith(only)]
        bad = 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as ex:
            for name, st, why in ex.map(lambda m: mutate(base, clean, *m), todo):
                print("%-7s %-24s %s" % (st, name, why))
                bad += st != "killed"
        for k, v in EQUIVALENT.items():
            print("equiv   %s: %s" % (k, v))
        print("blobs_reader_mutate: %d of %d killed" % (len(todo) - bad, len(todo)))
        return 1 if bad else 0
    finally:
        shutil.rmtree(base, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
