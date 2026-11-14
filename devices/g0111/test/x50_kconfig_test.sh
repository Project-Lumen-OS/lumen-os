#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# x50_kconfig_test.sh: devices/g0111/image/x50_kconfig.py on made-up boot images (no X50 file, no kernel of
# anyone's: a few bytes with the arm64 Image magic, a version string and a gzip configuration). On the Mac or Linux:
#   sh devices/g0111/test/x50_kconfig_test.sh
# Cases: header v2 and v4 with CONFIG_USERFAULTFD=y pass; a gzip kernel passes; '# ... is not set', =m, the option
# absent, no in-kernel configuration, a broken end mark, no ANDROID! magic, an unknown kernel format and a kernel
# that runs past the file fail; two images where the second fails, fail; a bad --need is refused; a dump's .gz copy
# of a boot image is read through gzip.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
K=$G/devices/g0111/image/x50_kconfig.py
W=$(mktemp -d "${TMPDIR:-/tmp}/x50_kconfig.XXXXXX") || exit 1
trap 'rm -rf "$W"' EXIT
python3 -B - "$W" <<'PY' || exit 1
import gzip, struct, sys
W = sys.argv[1]
def kernel(cfg, fmt="raw", end=True):
    k = bytearray(64)
    k[0x38:0x3c] = b"ARMd"
    k += b"\0Linux version 5.15.0-lumen-test (fixture) #1 SMP\n\0"
    if cfg is not None:
        k += b"IKCFG_ST" + gzip.compress(cfg.encode()) + (b"IKCFG_ED" if end else b"")
    k += bytes(100)
    if fmt == "gzip":
        return gzip.compress(bytes(k))
    if fmt == "other":
        k[0x38:0x3c] = b"\0\0\0\0"
        return b"\x02\x21\x4c\x18" + bytes(k[4:])
    return bytes(k)
def boot(k, hv, short=False):
    page = 4096 if hv >= 3 else 2048
    h = bytearray(page)
    h[0:8] = b"ANDROID!"
    struct.pack_into("<I", h, 8, len(k) + (1 << 20 if short else 0))
    struct.pack_into("<I", h, 40, hv)
    if hv < 3:
        struct.pack_into("<I", h, 36, page)
    return bytes(h) + k + bytes(-len(k) % page)
Y = "CONFIG_IKCONFIG=y\nCONFIG_USERFAULTFD=y\nCONFIG_HAVE_ARCH_USERFAULTFD_MINOR=y\n"
cases = {
    "v2_y": boot(kernel(Y), 2), "v4_y": boot(kernel(Y), 4), "gzip_y": boot(kernel(Y, "gzip"), 2),
    "not_set": boot(kernel("CONFIG_IKCONFIG=y\n# CONFIG_USERFAULTFD is not set\n"), 2),
    "module": boot(kernel("CONFIG_USERFAULTFD=m\n"), 2), "absent": boot(kernel("CONFIG_IKCONFIG=y\n"), 2),
    "no_ikconfig": boot(kernel(None), 2), "no_end": boot(kernel(Y, end=False), 2),
    "other_fmt": boot(kernel(Y, "other"), 2), "short": boot(kernel(Y), 2, short=True),
}
for n, b in cases.items():
    open("%s/%s.img" % (W, n), "wb").write(b)
open(W + "/no_magic.img", "wb").write(b"NOTANDROID" + bytes(5000))
open(W + "/dump_y.img.gz", "wb").write(gzip.compress(cases["v4_y"]))
open(W + "/dump_not_set.img.gz", "wb").write(gzip.compress(cases["not_set"]))
PY
PASS=0; FAIL=0
t() {  # name want-rc text args...
  n=$1 want=$2 txt=$3; shift 3
  python3 -B "$K" "$@" > "$W/$n.out" 2>&1; rc=$?
  if [ "$rc" = "$want" ] && grep -qF -- "$txt" "$W/$n.out"; then PASS=$((PASS + 1)); echo "ok   $n"
  else FAIL=$((FAIL + 1)); echo "FAIL $n (exit $rc, want $want, '$txt')"; sed 's/^/     | /' "$W/$n.out"; fi
}
N="--need CONFIG_USERFAULTFD=y"
# shellcheck disable=SC2086
{
t v2_y 0 "CONFIG_USERFAULTFD=y ok" "$W/v2_y.img" $N
t v4_y 0 "header v4, raw arm64 Image, Linux version 5.15.0-lumen-test" "$W/v4_y.img" $N
t gzip_y 0 "header v2, gzip" "$W/gzip_y.img" $N
t two_ok 0 "x50_kconfig: OK" "$W/v2_y.img" "$W/v4_y.img" $N --need CONFIG_IKCONFIG=y
t not_set 1 "CONFIG_USERFAULTFD=n NOT y" "$W/not_set.img" $N
t module 1 "CONFIG_USERFAULTFD=m NOT y" "$W/module.img" $N
t absent 1 "CONFIG_USERFAULTFD=(absent) NOT y" "$W/absent.img" $N
t no_ikconfig 1 "no in-kernel configuration (CONFIG_IKCONFIG)" "$W/no_ikconfig.img" $N
t no_end 1 "without its end mark" "$W/no_end.img" $N
t no_magic 1 "not an Android boot image" "$W/no_magic.img" $N
t other_fmt 1 "kernel format not known" "$W/other_fmt.img" $N
t short 1 "out of the file" "$W/short.img" $N
t second_bad 1 "FAILED (1)" "$W/v2_y.img" "$W/not_set.img" $N
t bad_need 1 "--need wants CONFIG_X=VALUE" "$W/v2_y.img" --need USERFAULTFD
t no_need 1 "usage:" "$W/v2_y.img"
t dump_gz 0 "header v4, raw arm64 Image" "$W/dump_y.img.gz" $N
t dump_gz_not_set 1 "CONFIG_USERFAULTFD=n NOT y" "$W/dump_not_set.img.gz" $N
}
echo "x50_kconfig_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
