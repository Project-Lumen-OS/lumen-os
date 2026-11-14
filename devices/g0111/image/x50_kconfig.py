#!/usr/bin/env python3
"""x50_kconfig.py: kernel options of the X50's own boot images (the dump on the build server, read only).

  x50_kconfig.py BOOT_IMG... --need CONFIG_X=y [--need ...]

Every BOOT_IMG is an Android boot image (header v0 to v4, magic ANDROID!; a name ending in .gz is read through gzip,
as a dump keeps it) whose kernel is a raw arm64 Image or a gzip one; its configuration is the in-kernel copy (CONFIG_IKCONFIG: the gzip data between IKCFG_ST and IKCFG_ED).
Prints the kernel version string and every --need line as found; exit 0 only when every image has every --need
option with exactly that value ('# CONFIG_X is not set' counts as 'n').

Why (compose_x50.sh step 0): the X50 image keeps the Z9X 1.0.9 base's ART boot image, compiled for the userfaultfd
GC (boot.oat: concurrent-copying=false), and ro.dalvik.vm.enable_uffd_gc=true. ART's odrefresh on Android 14 accepts
the boot image on /system only when that property equals what the kernel offers (odr_common.cc
CheckBuildUserfaultFdGc: on U the runtime uses userfaultfd whenever the kernel has it); a kernel without
CONFIG_USERFAULTFD makes it compile the whole boot classpath before zygote-start at the first boot (Play 6 t4), and
init reaches early-boot, where the vendor's PWM module loads, only after that (the PWM-wait reboot of the Z9X, 12 s;
not measured on the X50). The stock X50 says false, but its own boot image is compiled for the read-barrier GC
(concurrent-copying=true): the value belongs to the boot image it ships with, not to the projector.
Standard library only.
"""
import gzip
import re
import struct
import sys

sys.dont_write_bytecode = True


def die(msg):
    print("x50_kconfig: ERROR " + msg, file=sys.stderr)
    sys.exit(1)


def kernel_of(path):
    b = open(path, "rb").read()
    if path.endswith(".gz"):
        b = gzip.decompress(b)
    if b[:8] != b"ANDROID!":
        raise ValueError("not an Android boot image (no ANDROID! magic)")
    ksz = struct.unpack_from("<I", b, 8)[0]
    hv = struct.unpack_from("<I", b, 40)[0]
    if hv > 4:
        raise ValueError("boot image header version %d" % hv)
    page = 4096 if hv >= 3 else struct.unpack_from("<I", b, 36)[0]
    if not ksz or not page or page + ksz > len(b):
        raise ValueError("kernel size %d / page %d out of the file" % (ksz, page))
    k = b[page:page + ksz]
    if k[0x38:0x3c] == b"ARMd":
        return hv, "raw arm64 Image", k
    if k[:2] == b"\x1f\x8b":
        return hv, "gzip", gzip.decompress(k)
    raise ValueError("kernel format not known (neither a raw arm64 Image nor gzip)")


def config_of(k):
    i = k.find(b"IKCFG_ST")
    if i < 0:
        raise ValueError("no in-kernel configuration (CONFIG_IKCONFIG)")
    j = k.find(b"IKCFG_ED", i)
    if j < 0:
        raise ValueError("in-kernel configuration without its end mark")
    out = {}
    for line in gzip.decompress(k[i + 8:j]).decode("utf-8", "replace").splitlines():
        m = re.match(r"^(CONFIG_[A-Za-z0-9_]+)=(.*)$", line)
        if m:
            out[m.group(1)] = m.group(2)
        m = re.match(r"^# (CONFIG_[A-Za-z0-9_]+) is not set$", line)
        if m:
            out[m.group(1)] = "n"
    return out


def main(argv):
    imgs, need = [], []
    it = iter(argv)
    for a in it:
        if a == "--need":
            v = next(it, "")
            if not re.match(r"^CONFIG_[A-Za-z0-9_]+=\S+$", v):
                die("--need wants CONFIG_X=VALUE, not %r" % v)
            need.append(tuple(v.split("=", 1)))
        else:
            imgs.append(a)
    if not imgs or not need:
        die("usage: x50_kconfig.py BOOT_IMG... --need CONFIG_X=y [--need ...]")
    bad = 0
    for p in imgs:
        try:
            hv, fmt, k = kernel_of(p)
            cfg = config_of(k)
        except (OSError, ValueError, EOFError, struct.error) as e:
            print("x50_kconfig: %s: %s" % (p, e))
            bad += 1
            continue
        m = re.search(rb"Linux version [^\x00\n]{1,200}", k)
        print("x50_kconfig: %s: header v%d, %s, %s" % (p, hv, fmt, m.group(0).decode("utf-8", "replace") if m else "no version string"))
        for key, want in need:
            got = cfg.get(key, "(absent)")
            ok = got == want
            bad += not ok
            print("x50_kconfig:   %s=%s %s" % (key, got, "ok" if ok else "NOT %s" % want))
    print("x50_kconfig: %s" % ("OK" if not bad else "FAILED (%d)" % bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
