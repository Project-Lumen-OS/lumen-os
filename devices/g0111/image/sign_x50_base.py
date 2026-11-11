#!/usr/bin/env python3
"""sign_x50_base.py: the X50 (g0111) image's platform set on the g0111 keys (t12). Mac only for every step that
reads a key; the two key-free APEX steps run on the build host (image.conf host) with tools/sign/apex_laptop.py.

Why: the X50 image is composed from the published Z9X 1.0.9 image, whose framework-res and every other APK of the
platform set are signed with the Z9X release platform key (~/.lumen-keys/platform.pk8). An X50 app signed with the
g0111 platform key therefore was never a platform app (no android.uid.system, no signature permissions). This stage
moves the platform set to the g0111 platform key, so the X50 apps (built with that key by tools/devices/build_apps.sh
g0111) are platform apps of the X50 image and a Z9X-signed app is not.

  sign_x50_base.py import [TAR]   the APKs, APEXes and mac_permissions files of the Z9X 1.0.9 signed system tar
                                  (default: gsi/build/lumen_v1-1.0.9/system_tv_lumen_v1_public_nogms_signed.tar, pinned
                                  sha256 below) -> build/g0111/base_platform; each file must equal the one in the
                                  pinned base image on the build host (sha256 listing of the mounted image)
  sign_x50_base.py apex-prepare   the APEXes whose payload holds an APK on the Z9X platform certificate
                                  (x50_apex_modules.txt): that APK re-signed with the g0111 platform key -> job
  sign_x50_base.py apex-repack    (build host, key-free) payloads rebuilt unsigned with the APKs swapped
  sign_x50_base.py apex-sign      payload + container signed with the g0111 APEX keys (gen_x50_apex_keys.sh)
  sign_x50_base.py apex-verify    (build host, key-free) deapexer comparison of original and final
  sign_x50_base.py base           every system APK on the Z9X platform certificate re-signed with the g0111 platform
                                  key (except what remove.txt drops), the four verified APEXes, the mac_permissions
                                  files with the g0111 certificate -> build/g0111/stage/base_signed (base_signed.tsv)
  sign_x50_base.py all            apex-prepare .. base
  options: --jobs N

The APEX steps are the ones of tools/devices/sign_device.py (imported read only, never edited; HB unchanged), driven
by an X50 context: the source platform certificate is the Z9X release one (tools/sign/release_certs), the APEX keys
are the X50's own (~/.lumen-keys/g0111/apex, public parts devices/g0111/certs/apex). What stays as in the base, on
purpose: the APKs on the Z9X shared / media / networkstack / releasekey / bluetooth / sdk_sandbox keys and the 31
other APEXes (the g0111 key set has only the platform key; none of them gives an X50 app any right). Re-signing keeps
every zip entry outside META-INF byte for byte (sign_tar.resign_apk), so dex, resources and the base's odex stay valid.
Exit 0 = OK, 1 = stopped.
"""
import argparse
import binascii
import concurrent.futures as cf
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
sys.path.insert(0, HERE)
import sign_device  # noqa: E402  (read only)
import x50_apk_check  # noqa: E402  (signers() of the APK signing block)
apexlib, sign_tar, sign_play6, devices = sign_device.apexlib, sign_device.sign_tar, sign_device.sign_play6, sign_device.devices

# the X50 model whose platform set this is: g0111 X50 Ultra (default) or g0100 X50 Ultra Max (its own keys, 2026-11-11):
#   X50_CODENAME=g0100 python3 devices/g0111/image/sign_x50_base.py all  ->  build/g0100/stage/base_signed
C = os.environ.get("X50_CODENAME", "g0111")
if C not in ("g0111", "g0100"):
    sys.exit("sign_x50_base: X50_CODENAME %s is not an X50 model (g0111, g0100)" % C)
TAR_DEFAULT = os.path.join(os.path.dirname(G), "gsi", "build", "lumen_v1-1.0.9",
                           "system_tv_lumen_v1_public_nogms_signed.tar")
# the Z9X 1.0.9 signed tar the published image lumen-os-1.0.9-nogms-system.img (base.sha256) was made from
# (gsi/build/lumen_v1-1.0.9/outputs.sha256)
TAR_SHA256 = "48099c2b59511c9c63b6290718479c215887fb28b3629392ff0c2ca0bbb12117"
BASE_REMOTE = "x50-out/base/z9x-1.0.9-anp-system.img"
APEX_WORK_REMOTE = "x50-out/apex_sign" if C == "g0111" else "x50max-out/apex_sign"
HOST_BIN_REMOTE = "lumen-builder-backup/lineage-out/host/linux-x86/bin"   # the host tools of the Z9X base build
Z9X_PLATFORM = os.path.join(G, "tools", "sign", "release_certs", "platform.x509.pem")
MINE = os.path.join(G, "devices", C, "certs", "platform.x509.pem")
APEX_PUB = os.path.join(G, "devices", C, "certs", "apex")
APEX_KEYS = os.path.expanduser("~/.lumen-keys/%s/apex" % C)
MODULES = os.path.join(HERE, "x50_apex_modules.txt")
REMOVE = os.path.join(HERE, "remove.txt")
OUT = os.path.join(G, "build", C)
BASE = os.path.join(OUT, "base_platform")
STAGE = os.path.join(OUT, "stage", "base_signed")
MACS = ("system/etc/selinux/plat_mac_permissions.xml", "system/system_ext/etc/selinux/system_ext_mac_permissions.xml",
        "system/product/etc/selinux/product_mac_permissions.xml")
MARKER = sign_device.MARKER
die, log, run, sha256_file = sign_device.die, sign_device.log, sign_device.run, sign_device.sha256_file


def der_sha(pem):
    return hashlib.sha256(sign_tar.pem_to_der(pem)).hexdigest()


def modules():
    return [m for m in (ln.split("#", 1)[0].strip() for ln in open(MODULES)) if m]


class X50:
    """The context sign_device.py's APEX stages read (the attributes of its Ctx), for the X50."""

    def __init__(self, jobs):
        self.c, self.jobs = C, jobs
        self.reg = devices.get(G, C)
        self.img = devices.image(G, C)
        self.host = self.img["host"]
        self.out, self.base = OUT, BASE
        self.job = os.path.join(OUT, "apex_job")
        self.mkeys = APEX_PUB            # public files only: check_final reads them
        self.signed = os.path.join(OUT, "signed")
        self.conf = {"base_sha256": "devices/g0111/image/base.sha256", "apex_work_remote": APEX_WORK_REMOTE,
                     "host_bin_remote": HOST_BIN_REMOTE}
        self.tools, self.env, self.openssl = sign_tar.find_tools()
        self.km, self.test = sign_tar.load_keymap()
        self.foreign, self.s1test, self.sdk, self.dev = sign_device.check_device_signers.foreign_sets(G, C)
        self.tp = der_sha(Z9X_PLATFORM)   # the platform certificate the base carries: the Z9X release one
        if self.tp not in self.foreign or self.tp == self.dev:
            die("the Z9X release platform certificate is not a foreign one of " + C)
        self._key = None

    @property
    def key(self):
        if self._key is None:
            kd = os.path.expanduser(self.reg["keys_dir"])
            sign_tar.check_keys_dir(kd)
            k = sign_tar.release_cert(kd, "platform", self.openssl)
            if sign_tar.pem_to_der(MINE) != k["der"] or k["sha256"] != self.dev:
                die(f"{MINE} is not the certificate of {k['pk8']}")
            k["name"], k["dir"] = C + "-platform", kd
            self._key = k
        return self._key

    def ssh(self, cmd, check=True):
        r = _RetrySubprocess().run(["ssh", self.host, cmd], capture_output=True, text=True)
        if check and r.returncode != 0:
            die(f"ssh {self.host} {cmd[:200]!r} failed ({r.returncode}):\n{(r.stdout + r.stderr)[-3000:]}")
        return r

    def check(self, paths):
        errs = check_outputs(self, paths)
        if errs:
            die("the signed outputs are not what the X50 platform set must be:\n  " + "\n  ".join(errs))


def x50_module_keys(x, mods, base_apex):
    """The X50's own APEX keys of each module (gen_x50_apex_keys.sh), checked like sign_play6.play6_apex_keys: private
    files mode 600, payload key RSA-4096 matching its public forms, the public files equal to devices/g0111/certs/apex,
    and none of them a key another projector trusts or an AOSP test key. base_apex is not compared: these APEXes get
    new keys (a preinstalled APEX is trusted by its own apex_pubkey on the verified system partition)."""
    want = set(modules())
    if set(mods) - want:
        die(f"APEXes to re-sign that x50_apex_modules.txt does not list: {sorted(set(mods) - want)}")
    if not os.path.isdir(APEX_KEYS) or os.stat(APEX_KEYS).st_mode & 0o077:
        die(f"{APEX_KEYS} missing or not mode 700 (bash devices/g0111/image/gen_x50_apex_keys.sh)")
    keys = {}
    tmp = tempfile.mkdtemp(prefix="sign-x50-key-")
    try:
        for m in mods:
            k = {"module": m}
            for ext in ("pem", "pk8", "x509.pem", "avbpubkey", "pubkey.pem"):
                k[ext] = os.path.join(APEX_KEYS, f"{m}.{ext}")
                if not os.path.isfile(k[ext]):
                    die(f"missing {k[ext]} (bash devices/g0111/image/gen_x50_apex_keys.sh)")
            for ext in ("pem", "pk8"):
                if os.stat(k[ext]).st_mode & 0o077:
                    die(f"{k[ext]} must be mode 600")
            t = run([x.openssl, "pkey", "-in", k["pem"], "-noout", "-text"])
            if not t.startswith("Private-Key: (4096 bit"):
                die(f"{k['pem']}: not an RSA-4096 key")
            apexlib.run_avb(["extract_public_key", "--key", k["pem"], "--output", os.path.join(tmp, "a")])
            if open(os.path.join(tmp, "a"), "rb").read() != open(k["avbpubkey"], "rb").read():
                die(f"{k['avbpubkey']} does not match {k['pem']}")
            for ext in ("x509.pem", "avbpubkey", "pubkey.pem"):
                q = os.path.join(APEX_PUB, f"{m}.{ext}")
                if not os.path.isfile(q) or open(q, "rb").read() != open(k[ext], "rb").read():
                    die(f"{q} missing or different from {k[ext]}")
            c = sign_tar.release_cert(APEX_KEYS, m, x.openssl)
            k.update(cert_sha256=c["sha256"], avbpubkey_bytes=open(k["avbpubkey"], "rb").read())
            k["payload_key_sha256"] = apexlib.sha256_bytes(k["avbpubkey_bytes"])
            for d, what in ((k["cert_sha256"], "container certificate"), (k["payload_key_sha256"], "payload key")):
                if d in x.foreign or d in x.s1test:
                    die(f"{m}: the {what} is trusted by another projector or an AOSP test key (rule S1)")
            if k["cert_sha256"] in (x.sdk, x.dev, x.tp):
                die(f"{m}: the container certificate is a platform certificate")
            keys[m] = k
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return keys


def apk_signers(path):
    """{scheme: [cert sha256]} from the APK signing block (x50_apk_check.signers, the same reader the build host uses)
    plus 'v1' when only a JAR signature exists (apksigner is the judge on the Mac)."""
    return x50_apk_check.signers(open(path, "rb").read())


def check_outputs(x, paths):
    """Every output APK (also inside an output APEX) that is a re-signed one is signed by exactly the g0111 platform
    certificate; an output APEX container and payload key are the g0111 APEX keys of its module; no output carries
    the Z9X release platform certificate."""
    errs = []
    files = []
    for p in paths:
        if os.path.isdir(p):
            for r, _, fs in os.walk(p):
                files += [os.path.join(r, f) for f in fs if f.endswith((".apk", ".apex", ".capex"))]
        else:
            files.append(p)
    work = tempfile.mkdtemp(prefix="sign-x50-chk-")
    try:
        for f in sorted(files):
            if f.endswith((".apex", ".capex")):
                m = apexlib.module_of(os.path.basename(f))
                pub = apexlib.public_apex_key(APEX_PUB, m)
                certs = [d for d, _ in (sign_tar.apk_certs(x.tools, x.env)(f) or [])]
                if certs != [pub["cert_sha256"]]:
                    errs.append(f"{f}: container {certs}, not the {C} APEX certificate of {m}")
                if apexlib.read_apex(f)["inner"].read(apexlib.PUBKEY) != pub["avbpubkey"]:
                    errs.append(f"{f}: apex_pubkey is not the {C} payload key of {m}")
                inner = sign_tar.apex_apks([(f, f)], x.tools, os.path.join(work, os.path.basename(f)))
                if not any([d for d, _ in sign_tar.inspect_apk(q, x.tools, x.env)["signers"]] == [x.dev]
                           for _, _, q in inner):
                    errs.append(f"{f}: no APK inside is on the {C} platform certificate")
                for _, ip, q in inner:
                    s = [d for d, _ in sign_tar.inspect_apk(q, x.tools, x.env)["signers"]]
                    if x.tp in s:
                        errs.append(f"{f}!{ip}: still on the Z9X release platform certificate")
            else:
                i = sign_tar.inspect_apk(f, x.tools, x.env)
                if not i["ok"] or [d for d, _ in i["signers"]] != [x.dev]:
                    errs.append(f"{f}: not signed by exactly the {C} platform certificate ({i['signers']})")
    finally:
        shutil.rmtree(work, ignore_errors=True)
    if not errs:
        log(f"check: {len(files)} signed file(s) on the {C} keys, none on the Z9X platform certificate")
    return errs


# ------------------------------------------------------------------ import
SEL = re.compile(r"\.(apk|apex|capex)$|(^|/)(plat|system_ext|product)_mac_permissions\.xml$")


def stage_import(x, tar):
    tar = os.path.abspath(tar or TAR_DEFAULT)
    log(f"checking the sha256 of {tar}")
    if sha256_file(tar) != TAR_SHA256:
        die(f"{tar} is not the pinned Z9X 1.0.9 signed tar ({TAR_SHA256[:16]})")
    want = open(os.path.join(G, x.conf["base_sha256"])).read().split()[0]
    cmd = ("set -e; I=~/%s; [ \"$(sha256sum $I | cut -d' ' -f1)\" = %s ] || { echo 'base sha256 mismatch' >&2; exit 3; }; "
           "M=$(mktemp -d); sudo mount -t erofs -o loop,ro $I $M; trap 'cd /; sudo umount $M; rmdir $M' EXIT; cd $M; "
           "sudo find system -type f \\( -name '*.apk' -o -name '*.apex' -o -name '*.capex' -o -name '*_mac_permissions.xml' \\) "
           "-print0 | LC_ALL=C sort -z | sudo xargs -0 sha256sum" % (BASE_REMOTE, want))
    log(f"listing the pinned base {BASE_REMOTE} on {x.host}")
    rows = {}
    for line in x.ssh(cmd).stdout.splitlines():
        h, p = line.split(None, 1)
        rows[p.strip()] = h
    if len(rows) < 100 or "system/framework/framework-res.apk" not in rows:
        die(f"the listing of the base has only {len(rows)} files")
    part = sign_device.new_part(x.base, MARKER, "import (sign_x50_base.py)")
    n = 0
    with tarfile.open(tar) as t:
        for m in t:
            name = m.name[2:] if m.name.startswith("./") else m.name
            if not m.isreg() or not SEL.search(name):
                continue
            dst = os.path.join(part, name)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with t.extractfile(m) as src, open(dst, "wb") as o:
                shutil.copyfileobj(src, o)
            n += 1
    mine = {}
    for r, _, fs in os.walk(os.path.join(part, "system")):
        for fn in fs:
            p = os.path.join(r, fn)
            mine[os.path.relpath(p, part)] = sha256_file(p)
    if set(mine) != set(rows):
        die(f"the tar and the base image differ in their file set: only in the tar {sorted(set(mine) - set(rows))[:5]}, "
            f"only in the image {sorted(set(rows) - set(mine))[:5]}")
    diff = sorted(p for p in rows if rows[p] != mine[p])
    if diff:
        die(f"{len(diff)} files of the tar differ from the base image: {diff[:5]}")
    with open(os.path.join(part, "base_files.sha256"), "w") as o:
        o.write(f"# the pinned base image {BASE_REMOTE} (sha256 {want}) via the signed tar {TAR_SHA256}, listed on "
                f"{x.host} {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        for p in sorted(rows):
            o.write(f"{rows[p]}  {p}\n")
    sign_device.replace_dir(part, x.base, MARKER)
    log(f"OK: {n} files of the base in {x.base}, each equal to the image's (sha256)")
    return 0


# ------------------------------------------------------------------ base
def removed_paths():
    out = []
    for ln in open(REMOVE, encoding="utf-8"):
        s = ln.rstrip("\n").split("\t", 1)[0]
        if s.strip() and not s.startswith("#"):
            out.append(s)
    return out


def stage_base(x):
    sign_device.check_base(x)
    key = x.key
    apks, apex, texts = sign_play6.read_base(x.base)
    gone = removed_paths()
    is_gone = lambda p: any(p == r or p.startswith(r + "/") for r in gone)  # noqa: E731
    work = tempfile.mkdtemp(prefix="sign-x50-")
    try:
        inner, info = sign_play6.scan(apks, apex, x.tools, x.env, x.jobs, work)
        aerr, need = sign_play6.apex_needs(inner, info, x.tp)
        if aerr:
            die("APKs inside APEXes that do not verify:\n  " + "\n  ".join(aerr))
        mods = sorted(apexlib.module_of(os.path.basename(n)) for n in need)
        if mods != sorted(modules()):
            die(f"the APEXes with a Z9X platform APK are {mods}, x50_apex_modules.txt lists {sorted(modules())}")
        apex_final, why = sign_device.verified_apex(x, need, dict(apex),
                                                    {n: [(ip, f) for ip, f, _ in v] for n, v in need.items()})
        if apex_final is None:
            die(f"the APEX stage is not complete: {why} (sign_x50_base.py apex-prepare .. apex-verify)")
        todo, groups = [], {}
        for n, p in apks:
            i = info[n]
            if not i["ok"] or len(i["signers"]) != 1:
                die(f"{n}: does not verify or has {len(i['signers'])} signers")
            s = i["signers"][0][0]
            if s == x.tp and not is_gone(n):
                todo.append((n, p, i))
            if i["shared_uid"] and not is_gone(n):
                groups.setdefault(i["shared_uid"], set()).add(key["sha256"] if s == x.tp else s)
        resigned_inner = {(an, ip) for an in need for ip, _, _ in need[an]}
        for an, ip, _ in inner:
            su = info[(an, ip)]["shared_uid"]
            if su:
                groups.setdefault(su, set()).add(key["sha256"] if (an, ip) in resigned_inner
                                                 else info[(an, ip)]["signers"][0][0])
        mixed = {su: s for su, s in groups.items() if len(s) > 1}
        if mixed:
            die("sharedUserId groups with mixed final signers: " + "; ".join(f"{su}: {len(s)}" for su, s in mixed.items()))
        if sign_device.FRAMEWORK_RES not in {n for n, _, _ in todo}:
            die(f"{sign_device.FRAMEWORK_RES} is not on the Z9X release platform certificate")
        part = sign_device.new_part(STAGE, MARKER, "base (sign_x50_base.py)")
        log(f"re-sign {len(todo)} APKs of the platform set with the {C} platform key")
        rows = []
        with cf.ThreadPoolExecutor(x.jobs) as ex:
            futs = {}
            for n, p, i in todo:
                dst = os.path.join(part, n)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                futs[ex.submit(sign_tar.resign_apk, p, dst, key, i["schemes"], x.tools, x.env)] = (n, p, dst)
            for f in cf.as_completed(futs):
                f.result()
                n, p, dst = futs[f]
                rows.append((n, "apk", sha256_file(p), sha256_file(dst), n))
        for norm, a in sorted(apex_final.items()):
            dst = os.path.join(part, norm)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(a["file"], dst)
            rows.append((norm, "apex", a["base_sha256"], sha256_file(dst), a["base_file"]))
        old = binascii.hexlify(sign_tar.pem_to_der(Z9X_PLATFORM)).decode()
        new = binascii.hexlify(sign_tar.pem_to_der(MINE)).decode()
        for mp in MACS:
            if mp not in texts and not os.path.isfile(os.path.join(x.base, mp)):
                continue
            src = os.path.join(x.base, mp)
            t = open(src, encoding="utf-8").read()
            cnt = len(re.findall(old, t, re.I))
            if not cnt:
                if mp == MACS[0]:
                    die(f"{mp} does not name the Z9X platform certificate")
                continue
            t2 = re.sub(old, new, t, flags=re.I)
            dst = os.path.join(part, mp)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            open(dst, "w", encoding="utf-8").write(t2)
            rows.append((mp, "mac_permissions", sha256_file(src), sha256_file(dst), mp))
            log(f"{mp}: {cnt} x Z9X platform certificate -> {C}")
        x.check([part])
        # nothing of the output names the Z9X platform certificate any more
        with open(os.path.join(part, "base_signed.tsv"), "w") as o:
            o.write(f"# sign_x50_base.py base {time.strftime('%Y-%m-%d %H:%M:%S')}: {C} platform {key['sha256']} replaces "
                    f"the Z9X release platform {x.tp}\n")
            o.write("# path\tkind\tbase sha256\tsigned sha256\tbase path it replaces\n")
            for r in sorted(rows):
                o.write("\t".join(r) + "\n")
        sign_device.no_keys([part])
        sign_device.replace_dir(part, STAGE, MARKER)
        kinds = {}
        for r in rows:
            kinds[r[1]] = kinds.get(r[1], 0) + 1
        log(f"OK: {len(rows)} files in {STAGE} ({', '.join(f'{k} {v}' for k, v in sorted(kinds.items()))})")
        return 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


class _RetrySubprocess:
    """subprocess for sign_device.py's host steps: every scp / ssh through the jump host gets a timeout and up to six
    tries (a dropped jump host otherwise hangs the stage for ever); everything else passes through unchanged."""

    def __getattr__(self, name):
        return getattr(subprocess, name)

    def run(self, cmd, *a, **kw):
        if not (isinstance(cmd, list) and cmd and cmd[0] in ("scp", "ssh")):
            return subprocess.run(cmd, *a, **kw)
        kw.setdefault("timeout", 900 if cmd[0] == "ssh" else 300)
        for i in range(6):
            try:
                r = subprocess.run(cmd, *a, **kw)
                if r.returncode == 0 or (cmd[0] == "ssh" and r.returncode != 255):
                    return r
            except subprocess.TimeoutExpired:
                r = None
            log(f"{cmd[0]} failed (try {i + 1} of 6), again in 10 s")
            time.sleep(10)
        if r is None:
            raise SystemExit(f"sign_x50_base: {cmd[0]} timed out six times")
        return r


def _run_retry(cmd, **kw):
    if cmd and cmd[0] == "scp":
        r = _RetrySubprocess().run(cmd, capture_output=True, text=True, **kw)
        if r.returncode != 0:
            die(f"scp failed six times:\n{(r.stdout + r.stderr)[-2000:]}")
        return r.stdout
    return _run_orig(cmd, **kw)


_run_orig = sign_device.run


def main():
    sign_device.subprocess = _RetrySubprocess()
    sign_device.run = _run_retry
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("stage", choices=("import", "apex-prepare", "apex-repack", "apex-sign", "apex-verify", "base", "all"))
    ap.add_argument("arg", nargs="?")
    ap.add_argument("--jobs", type=int, default=min(6, os.cpu_count() or 2))
    a = ap.parse_args()
    x = X50(a.jobs)
    sign_device.module_keys = x50_module_keys      # the X50's own APEX keys in the shared APEX stages
    if a.stage == "import":
        return stage_import(x, a.arg)
    steps = {"apex-prepare": [sign_device.stage_apex_prepare], "apex-repack": [lambda x: sign_device.remote_job(x, "repack")],
             "apex-sign": [sign_device.stage_apex_sign], "apex-verify": [lambda x: sign_device.remote_job(x, "verify")],
             "base": [stage_base],
             "all": [sign_device.stage_apex_prepare, lambda x: sign_device.remote_job(x, "repack"),
                     sign_device.stage_apex_sign, lambda x: sign_device.remote_job(x, "verify"), stage_base]}[a.stage]
    for s in steps:
        s(x)
    return 0


if __name__ == "__main__":
    sys.exit(main())
