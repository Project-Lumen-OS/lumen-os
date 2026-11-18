#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""blobs_parity.py: pins what the image's blobs reader does on an INSTALLED unit (the z9x_blobs partition path) for
every model, so that one shared reader can stand in for the per-model copies without changing anything there.

    python3 tools/devices/test/blobs_parity.py [--record] [--shells sh,dash,bash,mksh] [PROFILE ...]

A profile is one model's reader as its image ships it: the reader script, its settings file (none for the readers
before the shared one), the model's allow-list under its image name and the partition size the installer makes.
Each case sets up a fake root (Z9X_ROOT): the 0-byte placeholders and bind targets of the image, the stale native-UI
fifos of an earlier boot, the boot props and a z9x_blobs partition made by the fixtures of the host tests
(tools/ota/test/blobs_fixture.py, devices/g0111/test/x50_fixture.py: made-up files, never a MediaTek / XGIMI one),
then runs the reader as init does (a second time for the *_again cases) with the stubs of tools/ota/test/stubs and
this file's own mount / umount. The trace of a case is every setprop, log line, bind, mount / umount, chcon, chown
and blockdev call, the reader's output and exit status, and every file left under /mnt and /data/vendor/tmp with its
type, mode, size and sha256. It must be the same under every shell and equal golden/blobs_parity/<profile>.txt byte
for byte. --record writes the golden files: only from the readers they pin (the per-model readers before the move),
never to make a changed reader green.
Only the partition path is pinned: a trial run (ro.gsid.image_running=1 without a z9x_blobs partition) is the new
part of the shared reader and has its own tests. Never touches anything outside its temp dir.
"""
import hashlib
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile

G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
GOLDEN = os.path.join(G, "tools", "devices", "test", "golden", "blobs_parity")
STUBS = os.path.join(G, "tools", "ota", "test", "stubs")
BF = os.path.join(G, "tools", "ota", "test", "blobs_fixture.py")
XF = os.path.join(G, "devices", "g0111", "test", "x50_fixture.py")

# name: (reader, its settings file or None, allow-list, its image name, partition MiB, fixture)
# The golden files were recorded from the per-model readers before the move (tools/ota/image/z9x_blobs.sh of
# 2026-11-14 for the Z9X and the HB, devices/g0111/image/x50_blobs.sh for the X50 family and the RS30); since then every
# profile runs the one shared reader with that model's blobs.conf.
READER = "tools/ota/image/z9x_blobs.sh"
PROFILES = {
    "z9x": (READER, "tools/ota/image/blobs.conf", "tools/ota/image/blobs_allow.txt", "blobs_allow.txt", 4, BF),
    "hb": (READER, "devices/goat_elegant/image/blobs.conf", "devices/goat_elegant/image/blobs_allow.txt",
           "blobs_allow.txt", 4, BF),
    "x50": (READER, "devices/g0111/image/blobs.conf", "devices/g0111/image/x50_blobs_allow.txt",
            "x50_blobs_allow.txt", 16, XF),
    "rs30": (READER, "devices/g0111/image/blobs.conf", "devices/g0155/image/rs30_blobs_allow.txt",
             "x50_blobs_allow.txt", 16, XF),
}

MOUNT = r'''#!/bin/sh
# parity stub of toybox mount: every call to $Z9X_T/mount.log; fails every call when $Z9X_T/mount_fail exists;
# '--bind SRC DST' records "<sha256 of SRC> DST" in $Z9X_T/binds (DST must exist; fails when DST is the path in
# $Z9X_T/mount_fail_dst); any other call (the tmpfs, a remount) only succeeds
echo "mount $*" >> "$Z9X_T/mount.log"
[ -e "$Z9X_T/mount_fail" ] && exit 1
[ "$1" = --bind ] || exit 0
[ -e "$2" ] && [ -e "$3" ] || { echo "mount: $3: No such file or directory" >&2; exit 1; }
[ "$(cat "$Z9X_T/mount_fail_dst" 2>/dev/null)" = "$3" ] && exit 1
printf '%s %s\n' "$(sha256sum "$2" | cut -d' ' -f1)" "$3" >> "$Z9X_T/binds"
'''
UMOUNT = r'''#!/bin/sh
# parity stub of toybox umount: every call to $Z9X_T/umount.log; drops the bind over $1 from $Z9X_T/binds
echo "umount $*" >> "$Z9X_T/umount.log"
grep -q " $1\$" "$Z9X_T/binds" 2>/dev/null || { echo "umount: $1: not mounted" >&2; exit 1; }
grep -v " $1\$" "$Z9X_T/binds" > "$Z9X_T/binds.tmp"; mv "$Z9X_T/binds.tmp" "$Z9X_T/binds"
'''
LOGS = ("setprop.log", "logcat", "binds", "mount.log", "umount.log", "chcon.log", "chown.log", "blockdev.log")


def lines(allow):
    """(sha256, path, source, options) of every file line"""
    out = []
    for raw in open(allow):
        w = raw.split()
        if len(w) >= 3 and re.fullmatch(r"[0-9a-f]{64}", w[0]):
            out.append((w[0], w[1], w[2], w[3:]))
    return out


def target(line):
    for o in line[3]:
        if o.startswith("bind="):
            return o[5:]
    return "/system/" + line[1]


class Case:
    def __init__(self, h, name):
        self.h, self.name = h, name
        self.T = os.path.join(h.work, h.shell_tag, h.profile, name)
        self.R = os.path.join(self.T, "root")
        self.outs = []

    # ---------------------------------------------------------------- setup
    def begin(self, *fixture):
        h = self.h
        os.makedirs(os.path.join(self.R, "system", "etc", "z9x"))
        for d in ("dev/block/mapper", "mnt", "data/vendor/tmp"):
            os.makedirs(os.path.join(self.R, d))
        shutil.copy(h.allow, os.path.join(self.R, "system", "etc", "z9x", h.image_allow))
        if h.conf:
            shutil.copy(os.path.join(G, h.conf), os.path.join(self.R, "system", "etc", "z9x", "blobs.conf"))
        for ln in h.lines:
            t = self.R + target(ln)
            os.makedirs(os.path.dirname(t), exist_ok=True)
            with open(t, "wb") as f:
                if t.startswith(os.path.join(self.R, "system") + "/"):
                    pass
                else:
                    f.write(b"the system's own %s\n" % target(ln).encode())
        for f in ("nuififo", "retnuififo"):
            os.mkfifo(os.path.join(self.R, "data", "vendor", "tmp", f))
        open(os.path.join(self.R, "data", "vendor", "tmp", "bootcomplete"), "w").write("vendor's own\n")
        self.props("ro.boot.slot_suffix=_b")
        for n in LOGS:
            open(os.path.join(self.T, n), "w").close()
        self.part(*fixture)
        return self

    def props(self, *kv):
        open(os.path.join(self.T, "props"), "w").write("".join(x + "\n" for x in kv))

    def addprop(self, kv):
        open(os.path.join(self.T, "props"), "a").write(kv + "\n")

    def dev(self, slot="b"):
        return os.path.join(self.R, "dev", "block", "mapper", "z9x_blobs_" + slot)

    def part(self, *opts, slot="b"):
        subprocess.run([sys.executable, self.h.fixture, "tar", self.h.files, self.h.test_allow, self.dev(slot)]
                       + list(opts), check=True)

    def raw_part(self, data):
        open(self.dev(), "wb").write(data)

    def allow_edit(self, fn):
        p = os.path.join(self.R, "system", "etc", "z9x", self.h.image_allow)
        open(p, "w").write(fn(open(p).read()))

    def write(self, rel, data):
        p = os.path.join(self.R, rel.lstrip("/"))
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "wb").write(data)

    def rm(self, rel):
        os.remove(os.path.join(self.R, rel.lstrip("/")))

    def flag(self, name, text=""):
        open(os.path.join(self.T, name), "w").write(text)

    # ---------------------------------------------------------------- run
    def run(self):
        h = self.h
        env = dict(os.environ, Z9X_ROOT=self.R, Z9X_T=self.T, PATH=h.bin + ":" + os.environ["PATH"], LC_ALL="C")
        try:
            p = subprocess.run([h.shell, os.path.join(G, h.reader)], env=env, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, timeout=20)
            self.outs.append("rc=%d %s" % (p.returncode, p.stdout.decode("utf-8", "replace")))
        except subprocess.TimeoutExpired:
            self.outs.append("TIMEOUT")
        return self

    def trace(self):
        parts = []
        for n in LOGS:
            parts.append("[%s]\n%s" % (n, open(os.path.join(self.T, n)).read()))
        parts.append("[out]\n" + "\n".join(self.outs))
        parts.append("[files]\n" + tree(self.R, ("mnt", "data/vendor/tmp")))
        s = "\n".join(parts)
        return s.replace(self.R, "<R>").replace(self.T, "<T>").replace(self.h.work, "<W>")


def tree(root, tops):
    out = []
    for top in tops:
        base = os.path.join(root, top)
        for d, dirs, files in os.walk(base):
            dirs.sort()
            for n in sorted(dirs) + sorted(files):
                p = os.path.join(d, n)
                st = os.lstat(p)
                kind = ("d" if stat.S_ISDIR(st.st_mode) else "f" if stat.S_ISREG(st.st_mode) else
                        "p" if stat.S_ISFIFO(st.st_mode) else "l" if stat.S_ISLNK(st.st_mode) else "?")
                extra = ""
                if kind == "f" and os.path.relpath(p, root) == "mnt/z9x_blobs/.img":
                    extra = " %d (the partition copy)" % st.st_size   # the case's own partition (a --symlink one names the temp dir)
                elif kind == "f":
                    extra = " %d %s" % (st.st_size, hashlib.sha256(open(p, "rb").read()).hexdigest()[:16])
                out.append("%s %s %04o%s" % (os.path.relpath(p, root), kind, stat.S_IMODE(st.st_mode), extra))
    return "\n".join(out) + "\n"


def stream(n, seed):
    """n deterministic bytes that are no tar"""
    b, i = b"", 0
    while len(b) < n:
        b += hashlib.sha256(b"%s %d" % (seed, i)).digest() * 64
        i += 1
    return b[:n]


# ---------------------------------------------------------------- cases: (name, needs, setup and run)
# needs: "place" a line with a 0-byte placeholder, "policy" the audio policy bind= line, "eye" the need=eye group,
# "alt" alt= copies of the eye group
def c_ok(c): c.begin().run()
def c_ok_again(c): c.begin().run(); c.flag("binds"); c.run()
def c_partial_again(c): c.begin("--corrupt", c.h.first).run(); c.run()
def c_missing_partition(c): c.begin(); os.remove(c.dev()); c.run()
def c_missing_partition_gsid_off(c):
    c.begin(); os.remove(c.dev()); c.props("ro.boot.slot_suffix=_b", "ro.gsid.image_running=0"); c.run()
def c_missing_slot(c): c.begin(); c.props("ro.boot.slot_suffix="); c.run()
def c_odd_slot(c): c.begin(); c.props("ro.boot.slot_suffix=_c"); c.run()
def c_slot_a(c):
    c.begin(); c.props("ro.boot.slot_suffix=_a"); os.rename(c.dev(), c.dev("a")); c.run()
def c_other_slot_only(c):
    c.begin(); os.rename(c.dev(), c.dev("a")); c.run()
def c_partition_in_trial(c):
    # a trial run on a unit that already has the partition (an installed Lumen OS): the partition, as installed
    c.begin(); c.addprop("ro.gsid.image_running=1"); c.addprop("ro.boot.xgimi.modelname=G0000"); c.run()
def c_bad_hash_first(c): c.begin("--corrupt", c.h.first).run()
def c_bad_hash_place(c): c.begin("--corrupt", c.h.place).run()
def c_bad_hash_policy(c): c.begin("--corrupt", c.h.policy).run()
def c_missing_policy(c): c.begin("--drop", c.h.policy).run()
def c_missing_first(c): c.begin("--drop", c.h.first).run()
def c_placeholder_not_empty(c): c.begin(); c.write("/system/" + c.h.place, b"not empty\n"); c.run()
def c_placeholder_missing(c): c.begin(); c.rm("/system/" + c.h.place); c.run()
def c_target_missing(c): c.begin(); c.rm("/vendor/etc/audio_policy_configuration.xml"); c.run()
def c_target_outside(c):
    c.begin(); c.write("/system/bin/sh", b"sh\n")
    c.allow_edit(lambda t: t.replace("bind=/vendor/etc/audio_policy_configuration.xml", "bind=/system/bin/sh")); c.run()
def c_target_apex_other(c):
    c.begin(); c.write("/apex/com.android.other/lib/x.so", b"x\n")
    c.allow_edit(lambda t: re.sub(r"bind=/apex/com\.android\.vndk\.v\d+/lib/", "bind=/apex/com.android.other/lib/", t, count=1))
    c.run()
def c_bad_tar(c): c.begin(); c.raw_part(stream(c.h.size, b"tar")); c.run()
def c_empty_partition(c): c.begin(); c.raw_part(b"\0" * c.h.size); c.run()
def c_short_partition(c):
    c.begin(); d = open(c.dev(), "rb").read(); c.raw_part(d[:1 << 20]); c.run()
def c_bad_symlink(c): c.begin("--symlink", c.h.first).run()
def c_bad_sparse(c): c.begin("--sparse", c.h.first).run()
def c_bad_dotdot(c): c.begin("--dotdot", "z9x_blobs_escape").run()
def c_set_mismatch(c): c.begin("--set", "other-set-a").run()
def c_mount_fail(c): c.begin(); c.flag("mount_fail"); c.run()
def c_mount_fail_policy(c):
    c.begin(); c.flag("mount_fail_dst", c.R + "/vendor/etc/audio_policy_configuration.xml"); c.run()
def c_big_member(c): c.begin("--big", c.h.nui).run()
def c_z9x_style_partition(c):
    c.begin("--drop", c.h.nui); d = open(c.dev(), "rb").read(); c.raw_part(d[:4 << 20]); c.run()
def c_eye_drawer_bad(c): c.begin("--corrupt", c.h.nui).run()
def c_eye_lib_bad(c): c.begin("--corrupt", c.h.lib).run()
def c_eye_lib_missing(c): c.begin("--drop", c.h.lib).run()
def c_eye_drawer_missing(c): c.begin("--drop", c.h.nui).run()
def c_eye_placeholder_not_empty(c): c.begin(); c.write("/system/" + c.h.nui, b"not empty\n"); c.run()
def c_eye_placeholder_missing(c): c.begin(); c.rm("/system/" + c.h.lib); c.run()
def c_eye_second_bind_fails(c): c.begin(); c.flag("mount_fail_dst", c.R + "/system/" + c.h.lib); c.run()
def c_eye_label_refused(c):
    c.begin(); c.allow_edit(lambda t: t.replace("label=nativeui_main_exec", "label=su_exec")); c.run()
def c_eye_mode_refused(c): c.begin(); c.allow_edit(lambda t: t.replace("mode=0755", "mode=4755")); c.run()
def c_eye_mode_without_label(c): c.begin(); c.allow_edit(lambda t: t.replace(" label=nativeui_main_exec", "")); c.run()
def c_eye_bind_refused(c):
    c.begin()
    c.allow_edit(lambda t: t.replace(c.h.lib + " /system/" + c.h.lib + " need=eye",
                                     c.h.lib + " /system/" + c.h.lib + " need=eye bind=/vendor/etc/audio_policy_configuration.xml"))
    c.run()
def c_eye_lines_absent(c):
    c.begin(); c.allow_edit(lambda t: "".join(l for l in t.splitlines(True) if "need=eye" not in l)); c.run()
def c_eye_alt(fw, vendor):
    def f(c):
        c.begin("--alt", c.h.nui + "," + c.h.lib, *(("--altfw", fw) if fw else ()))
        if vendor:
            c.addprop("ro.vendor.build.version.incremental=" + vendor)
        c.run()
    return f
def c_eye_alt_mixed(c): c.begin("--alt", c.h.nui); c.addprop("ro.vendor.build.version.incremental=v1.2.54"); c.run()
def c_eye_alt_mixed_lib(c): c.begin("--alt", c.h.lib); c.addprop("ro.vendor.build.version.incremental=v1.2.54"); c.run()
def c_eye_base_on_v1254(c): c.begin(); c.addprop("ro.vendor.build.version.incremental=v1.2.54"); c.run()
def c_once_per_boot(c):
    c.begin().run(); c.flag("binds"); os.mkfifo(os.path.join(c.R, "data", "vendor", "tmp", "nuififo")); c.run()
def c_only_fifos_removed(c):
    c.begin(); p = os.path.join(c.R, "data", "vendor", "tmp", "nuififo"); os.remove(p); open(p, "w").write("x\n"); c.run()
def c_fifo_dir(c):
    c.begin(); p = os.path.join(c.R, "data", "vendor", "tmp", "retnuififo"); os.remove(p); os.mkdir(p); c.run()


CASES = [
    ("ok", (), c_ok), ("ok_again", (), c_ok_again), ("partial_again", (), c_partial_again),
    ("missing_partition", (), c_missing_partition), ("missing_partition_gsid_off", (), c_missing_partition_gsid_off),
    ("missing_slot", (), c_missing_slot), ("odd_slot", (), c_odd_slot), ("slot_a", (), c_slot_a),
    ("other_slot_only", (), c_other_slot_only), ("partition_in_trial", (), c_partition_in_trial),
    ("bad_hash_first", (), c_bad_hash_first), ("bad_hash_place", ("place",), c_bad_hash_place),
    ("bad_hash_policy", ("policy",), c_bad_hash_policy), ("missing_policy", ("policy",), c_missing_policy),
    ("missing_first", (), c_missing_first), ("placeholder_not_empty", ("place",), c_placeholder_not_empty),
    ("placeholder_missing", ("place",), c_placeholder_missing), ("target_missing", ("policy",), c_target_missing),
    ("target_outside", ("policy",), c_target_outside), ("target_apex_other", (), c_target_apex_other),
    ("bad_tar", (), c_bad_tar), ("empty_partition", (), c_empty_partition), ("short_partition", (), c_short_partition),
    ("bad_symlink", (), c_bad_symlink), ("bad_sparse", (), c_bad_sparse), ("bad_dotdot", (), c_bad_dotdot),
    ("set_mismatch", (), c_set_mismatch), ("mount_fail", (), c_mount_fail),
    ("mount_fail_policy", ("policy",), c_mount_fail_policy),
    ("big_member", ("eye",), c_big_member), ("z9x_style_partition", ("eye",), c_z9x_style_partition),
    ("eye_drawer_bad", ("eye",), c_eye_drawer_bad), ("eye_lib_bad", ("eye",), c_eye_lib_bad),
    ("eye_lib_missing", ("eye",), c_eye_lib_missing), ("eye_drawer_missing", ("eye",), c_eye_drawer_missing),
    ("eye_placeholder_not_empty", ("eye",), c_eye_placeholder_not_empty),
    ("eye_placeholder_missing", ("eye",), c_eye_placeholder_missing),
    ("eye_second_bind_fails", ("eye",), c_eye_second_bind_fails), ("eye_label_refused", ("eye",), c_eye_label_refused),
    ("eye_mode_refused", ("eye",), c_eye_mode_refused), ("eye_mode_without_label", ("eye",), c_eye_mode_without_label),
    ("eye_bind_refused", ("eye",), c_eye_bind_refused), ("eye_lines_absent", ("eye",), c_eye_lines_absent),
    ("eye_alt_v1254", ("alt",), c_eye_alt(None, "v1.2.54")), ("eye_alt_v1299", ("alt",), c_eye_alt("v1.2.99", "v1.2.99")),
    ("eye_alt_v1299_on_v1254", ("alt",), c_eye_alt("v1.2.99", "v1.2.54")),
    ("eye_alt_v1254_on_v1299", ("alt",), c_eye_alt(None, "v1.2.99")),
    ("eye_alt_other_firmware", ("alt",), c_eye_alt(None, "v1.2.64")), ("eye_alt_no_firmware", ("alt",), c_eye_alt(None, None)),
    ("eye_alt_firmware_prefix", ("alt",), c_eye_alt(None, "v1.2.5")), ("eye_alt_mixed", ("alt",), c_eye_alt_mixed),
    ("eye_alt_mixed_lib", ("alt",), c_eye_alt_mixed_lib), ("eye_base_on_v1254", ("alt",), c_eye_base_on_v1254),
    ("once_per_boot", ("eye",), c_once_per_boot), ("only_fifos_removed", ("eye",), c_only_fifos_removed),
    ("fifo_dir", ("eye",), c_fifo_dir),
]


class Harness:
    def __init__(self, work, profile, shell):
        self.work, self.profile, self.shell = work, profile, shell
        self.shell_tag = os.path.basename(shell)
        self.reader, self.conf, allow, self.image_allow, mib, self.fixture = PROFILES[profile]
        self.size = mib << 20
        self.allow = os.path.join(G, allow)
        base = os.path.join(work, "fx", profile)
        self.files, self.test_allow = os.path.join(base, "files"), os.path.join(base, "allow.txt")
        if not os.path.exists(self.test_allow):
            os.makedirs(self.files)
            subprocess.run([sys.executable, self.fixture, "allow", self.allow, self.test_allow, self.files], check=True)
        self.allow = self.test_allow       # the image's allow-list in the fake root is the test one
        self.lines = lines(self.test_allow)
        eye = [l for l in self.lines if "need=eye" in l[3]]
        plain = [l for l in self.lines if "need=eye" not in l[3]]
        self.first = plain[0][1]
        place = [l for l in plain if not any(o.startswith("bind=") for o in l[3])]
        self.place = place[0][1] if place else None
        pol = [l for l in plain if target(l) == "/vendor/etc/audio_policy_configuration.xml"]
        self.policy = pol[0][1] if pol else None
        self.nui = next((l[1] for l in eye if l[1] == "bin/nativeui"), None)
        self.lib = next((l[1] for l in eye if l[1] != "bin/nativeui"), None)
        self.has = {"place": bool(place), "policy": bool(pol), "eye": bool(eye),
                    "alt": any(o.startswith("alt=") for l in eye for o in l[3])}
        self.bin = os.path.join(work, "bin")

    def run_all(self):
        out = []
        for name, needs, fn in CASES:
            if not all(self.has[n] for n in needs):
                continue
            c = Case(self, name)
            fn(c)
            out.append("## %s %s\n%s" % (self.profile, name, c.trace()))
        return "\n".join(out)


def main(a):
    record = "--record" in a
    shells = ["sh", "dash", "bash", "mksh"]
    names = []
    i = 0
    while i < len(a):
        if a[i] == "--shells":
            shells = a[i + 1].split(","); i += 2; continue
        if a[i] != "--record":
            names.append(a[i])
        i += 1
    names = names or list(PROFILES)
    found = []
    for s in shells:
        s = shutil.which(s) or os.environ.get("BLOBS_" + s.upper(), "")
        if s and os.path.realpath(s) not in [os.path.realpath(x) for x in found]:
            found.append(s)
    shells = found
    if not shells:
        sys.exit("no shell")
    os.umask(0o022)
    work = tempfile.mkdtemp(prefix="blobs_parity.")
    bad = 0
    try:
        b = os.path.join(work, "bin")
        os.makedirs(b)
        for f in ("getprop", "setprop", "log", "timeout", "chcon", "chown", "blockdev"):
            shutil.copy(os.path.join(STUBS, f), os.path.join(b, f))
            os.chmod(os.path.join(b, f), 0o755)
        for f, txt in (("mount", MOUNT), ("umount", UMOUNT)):
            open(os.path.join(b, f), "w").write(txt)
            os.chmod(os.path.join(b, f), 0o755)
        os.makedirs(GOLDEN, exist_ok=True)
        for p in names:
            traces = {}
            for sh in shells:
                traces[sh] = Harness(work, p, sh).run_all()
            first = traces[shells[0]]
            for sh in shells[1:]:
                if traces[sh] != first:
                    bad += 1
                    print("FAIL %s: %s differs from %s" % (p, sh, shells[0]))
                    print(diff(first, traces[sh]))
            g = os.path.join(GOLDEN, p + ".txt")
            if record:
                open(g, "w").write(first)
                print("recorded %s (%d cases, %s)" % (g, first.count("\n## ") + 1, ",".join(map(os.path.basename, shells))))
            elif not os.path.exists(g):
                bad += 1
                print("FAIL %s: no golden %s" % (p, g))
            elif open(g).read() != first:
                bad += 1
                print("FAIL %s: differs from %s" % (p, os.path.relpath(g, G)))
                print(diff(open(g).read(), first))
            else:
                print("ok   %s: %d cases equal to the golden under %s" % (p, first.count("\n## ") + 1,
                                                                        ",".join(map(os.path.basename, shells))))
    finally:
        shutil.rmtree(work, ignore_errors=True)
    print("blobs_parity: %s" % ("GREEN" if not bad else "RED (%d)" % bad))
    return 1 if bad else 0


def diff(a, b):
    import difflib
    return "".join(list(difflib.unified_diff(a.splitlines(True), b.splitlines(True), "golden", "now", n=2))[:80])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
