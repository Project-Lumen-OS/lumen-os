#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""delta.py: the incremental update (delta) of a release, beside its full package, for every model with updates.

A delta holds only the blocks that differ between the build a unit runs and the new build, so it installs only on a
system that is byte for byte its source. The source is the release a channel file names right now (the last
published build of that channel). Every file stays on GitHub: the source image is the published system.img of the
source release (its SHA256SUMS signed with the model's OTA key), the delta zip and its metadata become assets of the
new release, the channel manifest lists the delta in its optional "incremental" list beside the full package. The
updater takes a delta only when its running build is exactly the source and falls back to the full package when
the delta fails (apps/core/lumen-updater DeltaRule). Keys never leave the Mac: it signs two 32-byte digests.

The model's OTA tools come from its registry entry (device.conf ota_tools = tools/devices/ota, else tools/ota), so
no model is named here; `--like` is the new release's full package folder (its ota.json), whose timestamp and super
group the delta repeats.

  source CODENAME --channel URL|FILE [--tag TAG] --out SRC   build host: download and check the source release
  build  CODENAME --source SRC --like FULL_DIR|FULL.zip --new NEW.img --out DIR [--threads N]
                                                             build host: unsigned delta + apply check (host update_engine)
  sign   CODENAME DIR                                        Mac: payload.sig + metadata.sig from DIR's two digests
  finish CODENAME DIR                                        build host: the signed zip + side-car, apply check again
  entry  CODENAME DIR --manifest update-X.json --base-url URL
                                                             add DIR's delta to the channel manifest (before signing it)
  apply  PAYLOAD --old OLD.img --new NEW.img                 update_engine's apply code on the host (delta_generator
                                                             --in_file): the result must equal NEW byte for byte
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path[:0] = [os.path.join(G, "tools", "devices"), os.path.join(G, "tools", "devices", "ota")]
import devices  # noqa: E402
import ota_conf  # noqa: E402  (image_props: dump.erofs, read only)

DG_DEFAULT = os.path.expanduser("~/lumen-builder-backup/lineage-out/host/linux-x86/bin/delta_generator")
MAX_MANIFEST = 1 << 20


def die(m):
    sys.exit("delta: " + m)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def family(codename):
    """The model's OTA tool set: folder, make_ota command, certificate, key (Mac), version property namespace."""
    d = devices.get(G, codename)
    if d.get("ota_engine", "none") != "update_engine":
        die(f"{codename}: no update_engine updates (ota_engine={d.get('ota_engine')}), no delta")
    if d.get("ota_tools"):
        return {"tools": d["ota_tools"], "props": "ro.lumen", "cmd": ["tools/devices/ota/make_ota.py", "--device", codename],
                "cert": os.path.join(G, "devices", codename, "certs", "ota.x509.pem"),
                "key": os.path.join(os.path.expanduser(d["keys_dir"]), "ota", "ota.pk8")}
    return {"tools": "tools/ota", "props": "ro.z9x", "cmd": ["tools/ota/make_ota.py"],
            "cert": os.path.join(G, "tools", "sign", "release_certs", "ota.x509.pem"),
            "key": os.path.expanduser("~/.lumen-keys/ota.pk8")}


def other_certs(codename, own):
    """Certificates of the other models: a signature of this model's key must verify with none of them."""
    out = []
    for c in sorted(devices.load(G)):
        p = os.path.join(G, "devices", c, "certs", "ota.x509.pem")
        if c != codename and os.path.isfile(p) and open(p, "rb").read() != open(own, "rb").read():
            out.append(p)
    return out


def image_facts(img, ns):
    p = ota_conf.image_props(img)
    f = {k: p.get(f"{ns}.{k}", "") for k in ("version", "version_code", "build_id")}
    if not all(f.values()):
        die(f"{img}: no {ns}.version / version_code / build_id (not a Lumen system image of this model?)")
    f["version_code"] = int(f["version_code"])
    return f


def fetch(url, dst=None):
    if dst is None:
        with urllib.request.urlopen(url, timeout=60) as r:
            return r.read(MAX_MANIFEST + 1)
    subprocess.run(["curl", "-fsSL", "--retry", "5", "-C", "-", "-o", dst, url], check=True)
    return None


def verify_sig(cert, data_path, sig_path):
    with tempfile.TemporaryDirectory() as t:
        pub = os.path.join(t, "pub.pem")
        subprocess.run(["openssl", "x509", "-in", cert, "-pubkey", "-noout", "-out", pub], check=True)
        return subprocess.run(["openssl", "dgst", "-sha256", "-verify", pub, "-signature", sig_path, data_path],
                              capture_output=True).returncode == 0


# ------------------------------------------------------------------ 1. source
def cmd_source(a):
    fam = family(a.codename)
    os.makedirs(a.out, exist_ok=True)
    raw = open(a.channel, "rb").read() if os.path.isfile(a.channel) else fetch(a.channel)
    m = json.loads(raw)
    full = [p for p in m["packages"] if p["type"] == "full"]
    if not full:
        die("the channel file lists no full package")
    tag = a.tag or re.fullmatch(r"https://github\.com/[^/]+/[^/]+/releases/download/([^/]+)/[^/]+", full[0]["url"]).group(1)
    base = re.sub(r"/releases/download/.*", "/releases/download/" + tag + "/", full[0]["url"])
    for n in ("SHA256SUMS", "SHA256SUMS.sig"):
        fetch(base + n, os.path.join(a.out, n))
    if not verify_sig(fam["cert"], os.path.join(a.out, "SHA256SUMS"), os.path.join(a.out, "SHA256SUMS.sig")):
        die(f"{tag}: SHA256SUMS is not signed with {os.path.relpath(fam['cert'], G)}")
    sums = [l.split() for l in open(os.path.join(a.out, "SHA256SUMS")).read().splitlines() if l.strip()]
    imgs = [(h, n.lstrip("*")) for h, n in sums if n.endswith("-system.img")]
    if len(imgs) != 1:
        die(f"{tag}: SHA256SUMS lists {len(imgs)} system images")
    want, name = imgs[0]
    img = os.path.join(a.out, name)
    if not (os.path.isfile(img) and sha256(img) == want):
        fetch(base + name, img)
    got = sha256(img)
    if got != want:
        die(f"{name}: sha256 {got[:16]}..., SHA256SUMS says {want[:16]}...")
    f = image_facts(img, fam["props"])
    if not a.tag and (f["build_id"] != m["build_id"] or f["version_code"] != m["version_code"]):
        die(f"{name} is {f['build_id']} ({f['version_code']}), the channel names {m['build_id']} ({m['version_code']})")
    src = {"tag": tag, "image": name, "sha256": got, "size": os.path.getsize(img), **f}
    json.dump(src, open(os.path.join(a.out, "source.json"), "w"), indent=1)
    print(f"SOURCE_OK {tag} {f['build_id']} vc {f['version_code']} {name} {got[:16]}...")


# ------------------------------------------------------------------ 2. build (+ apply)
def delta_generator():
    for p in (os.environ.get("DELTA_GENERATOR"), shutil.which("delta_generator"), DG_DEFAULT):
        if p and os.path.isfile(p):
            return p
    die("no delta_generator ($DELTA_GENERATOR, PATH or " + DG_DEFAULT + ")")


def payload_parts(path):
    """old and new system sha256 (hex) a CrAU v2 payload names."""
    import sign_payload as spl  # tools/devices/ota: the protobuf wire reader
    manifest, _ = spl.read_manifest(path)
    parts = spl.top_fields(manifest).get(13, [])
    if len(parts) != 1:
        die(f"{path}: {len(parts)} partitions, a Lumen payload updates only system")
    pu = {n: v for n, _, v, _ in spl.fields(parts[0])}
    if pu.get(1) != b"system":
        die(f"{path}: updates {pu.get(1)!r}, not system")
    old = spl.partition_info(pu[6])[1] if 6 in pu else None
    return old, spl.partition_info(pu[7])[1]


def apply_check(payload, old, new):
    """Apply PAYLOAD to a copy-free read of OLD with update_engine's code on the host; the result must be NEW."""
    want_old, want_new = payload_parts(payload)
    if want_old is None:
        die(f"{payload}: a full payload (no old_partition_info), not a delta")
    if want_old != sha256(old):
        die(f"{payload}: its source is system {want_old[:16]}..., {old} is not")
    if want_new != sha256(new):
        die(f"{payload}: it installs system {want_new[:16]}..., {new} is not")
    with tempfile.TemporaryDirectory(dir=os.path.dirname(os.path.abspath(payload))) as t:
        out = os.path.join(t, "system.img")
        open(out, "wb").close()
        r = subprocess.run([delta_generator(), "--in_file=" + payload, "--partition_names=system",
                            "--old_partitions=" + old, "--new_partitions=" + out], capture_output=True, text=True)
        if r.returncode:
            die(f"apply failed (rc {r.returncode}): " + (r.stderr or r.stdout)[-2000:])
        got = sha256(out)
        if os.path.getsize(out) != os.path.getsize(new) or got != want_new:
            die(f"applied result {got[:16]}... ({os.path.getsize(out)} B) != {new} {want_new[:16]}...")
    print(f"APPLY_OK {os.path.basename(payload)}: {os.path.basename(old)} + payload = {os.path.basename(new)} "
          f"(sha256 {want_new[:16]}..., {os.path.getsize(new)} B)")


def like_facts(like):
    """The full package's facts the delta repeats: its build folder's ota.json, or the published full zip itself
    (post-timestamp of its metadata, the system it installs from its payload)."""
    if os.path.isdir(like):
        return json.load(open(os.path.join(like, "ota.json")))
    import sign_payload as spl
    with zipfile.ZipFile(like) as zf, tempfile.TemporaryDirectory() as t:
        md = dict(l.split("=", 1) for l in zf.read("META-INF/com/android/metadata").decode().splitlines() if "=" in l)
        zf.extract("payload.bin", t)
        manifest, _ = spl.read_manifest(os.path.join(t, "payload.bin"))
        new = spl.ota_new_hash(manifest)
    v = re.fullmatch(r"lumen-(?:[a-z0-9]+-)?(\d+\.\d+\.\d+(?:-(?:beta|rc)\d+)?)", md.get("post-build-incremental", ""))
    if not (v and new and md.get("post-timestamp", "").isdigit()):
        die(f"{like}: not a Lumen full package (post-build-incremental, post-timestamp, one system payload)")
    return {"type": "full", "version": v.group(1), "timestamp": int(md["post-timestamp"]), "new_sha256": new}


def cmd_build(a):
    fam = family(a.codename)
    src = json.load(open(os.path.join(a.source, "source.json")))
    like = like_facts(a.like)
    old = os.path.join(a.source, src["image"])
    if like.get("type") != "full" or like.get("new_sha256") != sha256(a.new):
        die(f"--like {a.like} is not the full package of {a.new}")
    new = image_facts(a.new, fam["props"])
    if new["version"] != like["version"] or new["version_code"] <= src["version_code"]:
        die(f"{new['version']} ({new['version_code']}) is not newer than the source {src['version']} ({src['version_code']})")
    if os.path.exists(a.out):
        die(f"{a.out} exists")
    cmd = [sys.executable, "-B"] + fam["cmd"] + ["unsigned", "--new", a.new, "--old", old, "--version", like["version"],
                                                "--out", a.out, "--threads", str(a.threads)]
    if like.get("timestamp"):
        cmd += ["--timestamp", str(like["timestamp"])]
    if fam["tools"] != "tools/ota":
        cmd += ["--old-published", os.path.join(a.source, "SHA256SUMS")]
    if fam["tools"] == "tools/ota" and like.get("super_group"):
        cmd += ["--super-group", "%s=%d" % (like["super_group"]["name"], like["super_group"]["size"])]
    print("+ " + " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=G, check=True)
    info = json.load(open(os.path.join(a.out, "ota.json")))
    if info.get("type") != "delta" or info.get("old_sha256") != src["sha256"]:
        die("make_ota did not build a delta from the source image")
    json.dump(src, open(os.path.join(a.out, "source.json"), "w"), indent=1)
    apply_check(os.path.join(a.out, "payload.unsigned.bin"), old, a.new)
    print(f"BUILD_OK delta {src['version']} -> {like['version']}: {os.path.getsize(os.path.join(a.out, 'payload.unsigned.bin'))} B"
          f" (sign on the Mac: delta.py sign {a.codename} DIR with DIR/ota.json, payload.hash, metadata.hash, source.json)")


# ------------------------------------------------------------------ 3. sign (Mac)
def cmd_sign(a):
    fam = family(a.codename)
    if not os.path.isfile(fam["key"]):
        die(f"no key {fam['key']} (sign on the Mac only)")
    info = json.load(open(os.path.join(a.dir, "ota.json")))
    src = json.load(open(os.path.join(a.dir, "source.json")))
    if info.get("type") != "delta" or info.get("old_sha256") != src["sha256"] or info.get("variant", "public") not in (
            "public", "public-" + info.get("device", "")):
        die("ota.json is not a public delta from source.json's image")
    with tempfile.TemporaryDirectory() as t:
        pub = os.path.join(t, "own.pem")
        subprocess.run(["openssl", "x509", "-in", fam["cert"], "-pubkey", "-noout", "-out", pub], check=True)
        others = []
        for i, c in enumerate(other_certs(a.codename, fam["cert"])):
            o = os.path.join(t, f"o{i}.pem")
            subprocess.run(["openssl", "x509", "-in", c, "-pubkey", "-noout", "-out", o], check=True)
            others.append((c, o))
        for n in ("payload", "metadata"):
            h = os.path.join(a.dir, n + ".hash")
            raw = open(h, "rb").read()
            if len(raw) != 32 or raw.hex() != info.get(n + "_hash"):
                die(f"{n}.hash is not the 32-byte digest ota.json names")
            s = os.path.join(a.dir, n + ".sig")
            subprocess.run(["openssl", "pkeyutl", "-sign", "-keyform", "DER", "-inkey", fam["key"],
                            "-pkeyopt", "digest:sha256", "-in", h, "-out", s], check=True)
            ok = lambda p: subprocess.run(["openssl", "pkeyutl", "-verify", "-pubin", "-inkey", p, "-pkeyopt",
                                           "digest:sha256", "-in", h, "-sigfile", s], capture_output=True).returncode == 0
            if os.path.getsize(s) != 512 or not ok(pub):
                die(f"{n}.sig does not verify with {fam['cert']}")
            bad = [c for c, o in others if ok(o)]
            if bad:
                os.remove(s)
                die(f"{n}.sig also verifies with {bad[0]}: wrong key")
            print(f"signed {n}.hash ({raw.hex()[:16]}...), refused by {len(others)} other model certificate(s)")
    print(f"SIGN_OK delta {src['version']} -> {info['version']}")


# ------------------------------------------------------------------ 4. finish
def cmd_finish(a):
    fam = family(a.codename)
    info = json.load(open(os.path.join(a.dir, "ota.json")))
    src = json.load(open(os.path.join(a.dir, "source.json")))
    cmd = [sys.executable, "-B"] + fam["cmd"] + ["finish", "--out", a.dir]
    if fam["tools"] == "tools/ota":
        name = "lumen-os-%s-nogms-from-%s-ota.zip" % (info["version"], src["version"])
        cmd += ["--cert", fam["cert"], "--name", name]
    subprocess.run(cmd, cwd=G, check=True)
    zips = [n for n in os.listdir(a.dir) if n.endswith("-ota.zip") and "-from-" in n]
    if len(zips) != 1:
        die(f"{a.dir}: {len(zips)} delta zips")
    z = os.path.join(a.dir, zips[0])
    with zipfile.ZipFile(z) as zf, tempfile.TemporaryDirectory(dir=a.dir) as t:
        zf.extract("payload.bin", t)
        if a.old and a.new:
            apply_check(os.path.join(t, "payload.bin"), a.old, a.new)
    print(f"FINISH_OK {zips[0]} {os.path.getsize(z)} B sha256 {sha256(z)}")


# ------------------------------------------------------------------ 5. entry
def stored_offset(z, member):
    with zipfile.ZipFile(z) as zf:
        i = zf.getinfo(member)
        if i.compress_type != zipfile.ZIP_STORED:
            die(f"{z}: {member} is compressed")
        with open(z, "rb") as f:
            f.seek(i.header_offset)
            h = f.read(30)
            n, e = struct.unpack("<HH", h[26:30])
            return i.header_offset + 30 + n + e, i.file_size


def entry(d, base_url):
    info = json.load(open(os.path.join(d, "ota.json")))
    src = json.load(open(os.path.join(d, "source.json")))
    zname = [n for n in os.listdir(d) if n.endswith("-ota.zip") and "-from-" in n][0]
    z = os.path.join(d, zname)
    metas = [n for n in os.listdir(d) if n.endswith("-payload_metadata.bin") and "-from-" in n]
    if len(metas) != 1:
        die(f"{d}: {len(metas)} delta metadata side-cars")
    meta = os.path.join(d, metas[0])
    off, size = stored_offset(z, "payload.bin")
    with zipfile.ZipFile(z) as zf:
        props = [l for l in zf.read("payload_properties.txt").decode().splitlines() if l.strip()]
    with open(z, "rb") as f:
        f.seek(off)
        prefix = f.read(os.path.getsize(meta))
    if prefix != open(meta, "rb").read():
        die(f"{metas[0]} is not the metadata of {zname}")
    return info, {
        "type": "delta", "from_build_id": src["build_id"], "from_version": src["version"],
        "from_version_code": src["version_code"], "from_sha256": src["sha256"],
        "url": base_url + zname, "size": os.path.getsize(z), "sha256": sha256(z),
        "payload_offset": off, "payload_size": size,
        "metadata_url": base_url + metas[0], "metadata_size": len(prefix),
        "metadata_sha256": hashlib.sha256(prefix).hexdigest(), "payload_properties": props,
    }


def cmd_entry(a):
    family(a.codename)
    if not a.base_url.startswith("https://") or not a.base_url.endswith("/"):
        die("--base-url https://.../releases/download/<tag>/")
    info, e = entry(a.dir, a.base_url)
    raw = open(a.manifest, "rb").read()
    m = json.loads(raw)
    if m["version"] != info["version"]:
        die(f"{a.manifest} is {m['version']}, the delta installs {info['version']}")
    if not any(p["type"] == "full" for p in m["packages"]):
        die(f"{a.manifest} lists no full package (the fall back of every delta)")
    full_url = next(p["url"] for p in m["packages"] if p["type"] == "full")
    if full_url.rsplit("/", 1)[0] + "/" != a.base_url:
        die(f"the delta must sit on the full package's release: {full_url.rsplit('/', 1)[0]}/")
    if m["version_code"] <= e["from_version_code"]:
        die("the source is not older than the manifest's build")
    m["incremental"] = [x for x in m.get("incremental", []) if x.get("from_build_id") != e["from_build_id"]] + [e]
    indent = 1 if raw.startswith(b'{\n "') else 2
    open(a.manifest, "wb").write((json.dumps(m, indent=indent, ensure_ascii=False) + "\n").encode("utf-8"))
    for s in (a.manifest + ".sig",):
        if os.path.exists(s):
            os.remove(s)   # the bytes changed: the Mac signs the manifest again
    print(f"ENTRY_OK {os.path.basename(a.manifest)}: delta from {e['from_build_id']} {e['size']} B beside the full "
          f"package; sign the manifest on the Mac")


def cmd_apply(a):
    apply_check(a.payload, a.old, a.new)


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = ap.add_subparsers(dest="cmd", required=True)
    s = sp.add_parser("source"); s.add_argument("codename"); s.add_argument("--channel", required=True)
    s.add_argument("--tag"); s.add_argument("--out", required=True)
    b = sp.add_parser("build"); b.add_argument("codename"); b.add_argument("--source", required=True)
    b.add_argument("--like", required=True); b.add_argument("--new", required=True); b.add_argument("--out", required=True)
    b.add_argument("--threads", type=int, default=8)
    g = sp.add_parser("sign"); g.add_argument("codename"); g.add_argument("dir")
    f = sp.add_parser("finish"); f.add_argument("codename"); f.add_argument("dir")
    f.add_argument("--old"); f.add_argument("--new")
    e = sp.add_parser("entry"); e.add_argument("codename"); e.add_argument("dir")
    e.add_argument("--manifest", required=True); e.add_argument("--base-url", required=True)
    p = sp.add_parser("apply"); p.add_argument("payload"); p.add_argument("--old", required=True)
    p.add_argument("--new", required=True)
    a = ap.parse_args(argv)
    {"source": cmd_source, "build": cmd_build, "sign": cmd_sign, "finish": cmd_finish, "entry": cmd_entry,
     "apply": cmd_apply}[a.cmd](a)


if __name__ == "__main__":
    main(sys.argv[1:])
