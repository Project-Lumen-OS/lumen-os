#!/usr/bin/env python3
"""devices.py: the Lumen OS device registry, devices/<codename>/device.conf.

  devices.py [--root DIR] list                      codename, installer id and name of each supported device,
                                                    one per line
  devices.py [--root DIR] identify FAMILY MODEL     the entry of (ro.product.device, ro.boot.xgimi.modelname):
                                                    codename and status, tab-separated; exit 1 when none
  devices.py [--root DIR] get CODENAME KEY          one value (scripts use this instead of sourcing a file)
  devices.py [--root DIR] validate                  every device.conf: format, keys, enums, uniqueness, paths
  devices.py [--root DIR] check-installer [CONF]    installer/lib/devices.conf agrees with the registry
  devices.py [--root DIR] check-wire                the OTA device ids written in the tools (or in the device's
                                                    ota.conf) agree with the registry

  devices.py [--root DIR] image CODENAME KEY        one value of devices/<codename>/image.conf

DIR defaults to the tree this script lives in. As a library: load(root), get(root, codename),
by_installer_id(root, id), by_model(root, model), identify(root, family, model), load_all(root); a device is a
dict of its keys, list keys split.

Identity: a projector is (family, model) = (ro.product.device, ro.boot.xgimi.modelname). XGIMI builds several
models from one product image, so one family can hold several entries; each (family, model) pair belongs to one
entry only. The folder name (codename) is the Lumen name of the entry; family defaults to it.

Status: supported entries are the devices the tools work with. A stock analysis entry (status=stock analysis) only names a
projector that is not supported (no image, no install, no OTA): load(), list, get, image and every tool on top of
them never return it, get and image refuse it by name, identify and load_all show it.

Format: data only, never sourced. One key=value per line, '#' starts a comment line, empty lines allowed.
A key is [a-z][a-z0-9_]* and appears at most once; a value holds only letters, digits, spaces and . _ : / - ~ ,
(the installer's set plus ~ and ,). Unknown keys are an error. The folder name must equal codename.
Keys (all required unless marked optional; a stock analysis entry needs only codename, family, status, name, models,
platform and hardware):
  codename          the Lumen name of the entry, equal to its folder; ro.product.device when family is absent
  family            optional: ro.product.device when it differs from codename (default: codename)
  status            optional: supported (the default) or stock analysis (a projector Lumen does not support)
  name              shown to users
  models            ro.boot.xgimi.modelname, the first one is the main one (list)
  platform          ro.board.platform
  hardware          ro.boot.hardware
  abi               userspace ABI of the system image: arm64, arm32
  base_product      what the system is built from
  vendor_sdk        ro.vndk.version of the stock vendor
  vendor_firmware   supported ro.vendor.build.version.incremental, the first is the recommended one (list)
  hal_transport     the vendor gmpf HAL: hidl, aidl
  image_fs          system image file system: erofs, ext4
  slots             single, ab, ab-virtual
  install_flow      z9x-usb, play6-wifi (the two built-in flows of the installer), ab-fastbootd
  ota_engine        update_engine, recovery-block, none
  ota_wire_id       device id in OTA metadata and manifests, never changed once shipped (none without OTA);
                    with OTA it equals installer_id
  prop_namespace    the properties this device's image sets (ro.z9x, ro.lumen)
  keys_dir          private keys, on the Mac only: under ~/.lumen-keys, never inside the tree
  app_prefix        folder prefix under apps/ for apps of this device only
  channel           update channels, or the release tag prefix (list; none)
  installer_id      the id in installer/lib/devices.conf ([a-z0-9]+, fixed by shipped downloads)
  fastboot_product  fastbootd 'getvar product'
  forks             the table of deliberate copies of another device's files (tools/devices/check_forks.py), or none
  hal_check         the HAL caller check (tools/devices/check_hal.py): z9x-callers, play6-hidl, aidl-table, or none
                    (the image carries no app of ours that calls the projector HAL; then hal_app is none)
  hal_app           the app that talks to the gmpf HAL
  hal_codes         the HAL code table, or none
  hal_codes_also    optional, aidl-table only: the code tables of the other supported stock firmware (list); every
                    call must be the same row in each of them and in hal_codes (check_aidl_hal.py, several --codes)
  hal_banned        the table of banned HAL calls, or none
  hal_allowed       optional, required with hal_check=aidl-table: the complete list of HAL calls the app may send
  hal_more          optional, aidl-table only: further HAL apps of the image, each APP:ALLOWED (its own allow list; the
                    same hal_codes and hal_banned), checked by tools/devices/check_hal.py like hal_app (X50: the menu)
  hal_unbanned      optional, aidl-table only: APP:UNBANNED entries, the maintainer's per-app exceptions to hal_banned (each
                    row with its argument range); APP must be hal_app or a hal_more app of this device and the list's
                    file name unbanned_<app in lower case>.tsv under devices/<codename>/research (t20, X50 menu)
  apps              optional: device-only apps under apps/<name> (list)
  ota_tools         optional, update_engine only: the folder of the device's OTA host tools, tools/ota (the
                    default when absent) or tools/devices/ota (they read the wire id from devices/<codename>/ota.conf)

devices/<codename>/image.conf (section 4), same format, how the system image is composed:
  generic     yes: tools/image/compose.sh composes it; no: it has its own entry and compose.sh refuses it
  entry       the script that builds the image
  compose     the composer run on the build host (generic only), or none
  host        the build host (ssh name), or builder.env (the entry reads it from ~/.config/lumen/builder.env)
  remote_dir  the folder under the build host's home: inputs in <remote_dir>/in, outputs next to it
  stage       the files staged into <remote_dir>/in (generic only), or none (the entry stages them) (list)
  variants    NAME:ARGS of each image variant (generic only; ARGS comma-separated, may be empty) (list)
  fs          the image's file system, must equal the device's image_fs
  stage_dir   optional: a folder (relative to gsi/) of signed build outputs that compose.sh copies whole into
              <remote_dir>/in/<its name>; signed on the Mac, never a key (compose.sh refuses key-like files)
"""
import os
import re
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

REQUIRED = ("codename name models platform hardware abi base_product vendor_sdk vendor_firmware hal_transport "
            "image_fs slots install_flow ota_engine ota_wire_id prop_namespace keys_dir app_prefix channel "
            "installer_id fastboot_product forks hal_check hal_app hal_codes hal_banned").split()
OPTIONAL = ("apps", "hal_allowed", "hal_more", "ota_tools", "family", "status", "hal_codes_also", "hal_unbanned")
# a stock analysis entry: only who the projector is, nothing to build or install
RESEARCH_REQUIRED = "codename family status name models platform hardware".split()
LISTS = ("models", "vendor_firmware", "channel", "apps", "hal_codes_also")
ENUMS = {
    "abi": ("arm64", "arm32"),
    "hal_transport": ("hidl", "aidl"),
    "image_fs": ("erofs", "ext4"),
    "slots": ("single", "ab", "ab-virtual"),
    "install_flow": ("z9x-usb", "play6-wifi", "ab-fastbootd"),
    "ota_engine": ("update_engine", "recovery-block", "none"),
    "hal_check": ("z9x-callers", "play6-hidl", "aidl-table", "none"),
    "status": ("supported", "research"),
}
PATHS = ("forks", "hal_app", "hal_codes", "hal_banned", "hal_allowed")
IMAGE_KEYS = ("generic", "entry", "compose", "host", "remote_dir", "stage", "variants", "fs")
IMAGE_LISTS = ("stage", "variants")
IMAGE_OPTIONAL = ("stage_dir",)
# a path under these folders is private (publish_audit.py excludes it): absent from a public checkout, reported only
PRIVATE_PATHS = ("research/",)
BUILT_IN_FLOWS = ("z9x-usb", "play6-wifi")
KEY_RE = re.compile(r"^[a-z][a-z0-9_]*$")
VALUE_RE = re.compile(r"^[A-Za-z0-9 ._:/~,-]*$")
INSTALLER_LINE = re.compile(r"^([a-z0-9]+)\.([A-Z0-9_]+)=(.*)$")
# where the tools write an OTA device id by hand, per ota_engine, for a device whose ota_tools is OTA_TOOLS:
# (file, text with {id}). check-wire reads them, never edits them (tools/ota is a Z9X gate input)
WIRE = {
    "update_engine": (("tools/ota/make_ota.py", "pre-device={id}"),
                      ("tools/ota/make_manifest.py", '"device": "{id}"')),
    "recovery-block": (("tools/play6/ota/make_manifest_play6.py", '"device": "{id}"'),
                       ("apps/Play6Updater/src/org/lumen/updater/Ota.java", 'DEVICE = "{id}"')),
}
# ota_tools when the key is absent: the Z9X tools, which write the wire id by hand (WIRE above)
OTA_TOOLS = "tools/ota"
# the other ota_tools folders: they take the device from the registry and read its wire id from this data file
# (relative to the tree, {codename} filled in), which must hold exactly one wire_id=<ota_wire_id> line
WIRE_TOOLS = {"tools/devices/ota": "devices/{codename}/ota.conf"}


class RegistryError(Exception):
    pass


def parse(path, keys=None):
    """One device.conf (or image.conf with keys=IMAGE_KEYS) -> (dict of raw string values, list of errors)."""
    vals, errs = {}, []
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            if "=" not in line:
                errs.append("%s:%d: not key=value: %r" % (path, n, line))
                continue
            k, v = line.split("=", 1)
            if not KEY_RE.match(k):
                errs.append("%s:%d: bad key %r" % (path, n, k))
            elif (k not in keys) if keys else (k not in REQUIRED and k not in OPTIONAL):
                errs.append("%s:%d: unknown key %r" % (path, n, k))
            elif k in vals:
                errs.append("%s:%d: %s given twice" % (path, n, k))
            elif not VALUE_RE.match(v):
                errs.append("%s:%d: %s: character outside the allowed set in %r" % (path, n, k, v))
            else:
                vals[k] = v
    return vals, errs


def _paths(root):
    d = os.path.join(root, "devices")
    if not os.path.isdir(d):
        return []
    return sorted(os.path.join(d, n, "device.conf") for n in os.listdir(d)
                  if os.path.isfile(os.path.join(d, n, "device.conf")))


def _device(vals):
    return {k: (v.split() if k in LISTS else v) for k, v in vals.items()}


def family(d):
    """ro.product.device of an entry: its family key, or its codename when the key is absent."""
    return d.get("family") or d["codename"]


def status(d):
    return d.get("status") or "supported"


def load_all(root=ROOT):
    """{codename: entry} of every entry, stock analysis ones included; raises RegistryError when a file does not
    parse (validate gives every reason)."""
    out = {}
    for p in _paths(root):
        vals, errs = parse(p)
        if errs:
            raise RegistryError(errs[0])
        need = RESEARCH_REQUIRED if vals.get("status") == "research" else REQUIRED
        missing = [k for k in need if k not in vals]
        if missing:
            raise RegistryError("%s: missing %s" % (p, " ".join(missing)))
        out[vals["codename"]] = _device(vals)
    return out


def load(root=ROOT):
    """{codename: device} of the supported devices (stock analysis entries left out)."""
    return {c: d for c, d in load_all(root).items() if status(d) == "supported"}


def _refuse_research(d):
    raise RegistryError("%s is not a supported device (status=%s: %s, ro.product.device %s, model %s): no image, "
                        "no install, no OTA" % (d["codename"], status(d), d["name"], family(d), d["models"][0]))


def get(root, codename):
    d = load_all(root).get(codename)
    if d is None:
        raise RegistryError("unknown device %r (devices.py list)" % codename)
    if status(d) != "supported":
        _refuse_research(d)
    return d


def identify(root, fam, model):
    """The entry (stock analysis ones included) whose family is fam and whose models hold model, or None."""
    return next((d for d in load_all(root).values() if family(d) == fam and model in d["models"]), None)


def by_installer_id(root, iid):
    return next((d for d in load(root).values() if d["installer_id"] == iid), None)


def by_model(root, model):
    return next((d for d in load(root).values() if model in d["models"]), None)


def ota_tools(d):
    """The folder of a device's OTA host tools, relative to the tree (OTA_TOOLS when ota_tools is absent)."""
    return d.get("ota_tools") or OTA_TOOLS


def image(root, codename):
    """devices/<codename>/image.conf -> dict (list keys split); RegistryError when absent or broken."""
    d = load_all(root).get(codename)
    if d is not None and status(d) != "supported":
        _refuse_research(d)
    p = os.path.join(root, "devices", codename, "image.conf")
    if not os.path.isfile(p):
        raise RegistryError("%s has no image.conf" % codename)
    vals, errs = parse(p, IMAGE_KEYS + IMAGE_OPTIONAL)
    if errs:
        raise RegistryError(errs[0])
    missing = [k for k in IMAGE_KEYS if k not in vals]
    if missing:
        raise RegistryError("%s: missing %s" % (p, " ".join(missing)))
    out = {k: (v.split() if k in IMAGE_LISTS else v) for k, v in vals.items()}
    for k in IMAGE_OPTIONAL:
        out.setdefault(k, "")
    return out


def validate_image(root, codename, image_fs, errs, notes):
    rel = "devices/%s/image.conf" % codename
    p = os.path.join(root, rel)
    if not os.path.isfile(p):
        return
    vals, e = parse(p, IMAGE_KEYS + IMAGE_OPTIONAL)
    errs += e
    for k in IMAGE_KEYS:
        if k not in vals:
            errs.append("%s: missing %s" % (rel, k))
    if vals.get("generic") not in (None, "yes", "no"):
        errs.append("%s: generic=%s, not yes or no" % (rel, vals["generic"]))
    if "fs" in vals and vals["fs"] != image_fs:
        errs.append("%s: fs=%s, but device.conf says image_fs=%s" % (rel, vals["fs"], image_fs))
    paths = [vals.get("entry", "")] + [vals.get("compose", "")] + vals.get("stage", "").split()
    for v in paths:
        if v and v != "none" and not os.path.isfile(os.path.join(root, v)):
            if vals.get("generic") == "no" and v == vals.get("entry"):
                # an own entry is only named here; the public repository may carry it under another name
                notes.append("%s: entry %s not in this tree" % (rel, v))
            else:
                errs.append("%s: %s does not exist" % (rel, v))
    # tools/image/compose.sh copies the stage files flat into one folder: two with one file name would overwrite
    names = {}
    for v in vals.get("stage", "").split():
        b = os.path.basename(v)
        if b in names:
            errs.append("%s: stage files %s and %s have the same file name (compose.sh stages flat)" % (rel, names[b], v))
        names.setdefault(b, v)
    if vals.get("generic") == "yes":
        for k in ("compose", "stage", "variants"):
            if vals.get(k, "none") in ("", "none"):
                errs.append("%s: generic=yes needs %s" % (rel, k))
        for v in vals.get("variants", "").split():
            if not re.match(r"^[a-z0-9]+:", v):
                errs.append("%s: variant %r is not NAME:ARGS" % (rel, v))
    sd = vals.get("stage_dir", "")
    if sd and (sd.startswith("/") or ".." in sd.split("/") or not re.match(r"^[A-Za-z0-9._/-]+$", sd)):
        errs.append("%s: stage_dir %r must be a plain relative path" % (rel, sd))


def validate(root):
    """-> (errors, notes) over every device.conf."""
    errs, notes, seen, fams = [], [], {}, {}
    for p in _paths(root):
        vals, e = parse(p)
        errs += e
        rel = os.path.relpath(p, root)
        research = vals.get("status") == "research"
        for k in (RESEARCH_REQUIRED if research else REQUIRED):
            if k not in vals:
                errs.append("%s: missing %s" % (rel, k))
        if research:
            # only who it is: nothing a tool could build, sign, install or update from
            for k in sorted(set(REQUIRED + list(OPTIONAL)) - set(RESEARCH_REQUIRED)):
                if k in vals:
                    errs.append("%s: %s given for a research entry (it has no image, install or OTA)" % (rel, k))
            if os.path.isfile(os.path.join(os.path.dirname(p), "image.conf")):
                errs.append("%s: a research entry has no image.conf" % rel)
        fam = vals.get("family") or vals.get("codename")
        if "family" in vals and not re.match(r"^[a-z0-9_]+$", vals["family"]):
            errs.append("%s: family %r is not [a-z0-9_]+" % (rel, vals["family"]))
        if fam:
            fams[rel] = (vals.get("codename"), fam)
        for m in (vals.get("models", "").split() if fam else ()):
            other = seen.setdefault(("identity", fam, m), rel)
            if other != rel:
                errs.append("%s: ro.product.device %s with model %s is also %s (one entry per family and model)"
                            % (rel, fam, m, other))
        folder = os.path.basename(os.path.dirname(p))
        if vals.get("codename") not in (None, folder):
            errs.append("%s: codename %s is not its folder %s" % (rel, vals["codename"], folder))
        for k, allowed in ENUMS.items():
            if k in vals and vals[k] not in allowed:
                errs.append("%s: %s=%s, not one of %s" % (rel, k, vals[k], " ".join(allowed)))
        if "installer_id" in vals and not re.match(r"^[a-z0-9]+$", vals["installer_id"]):
            errs.append("%s: installer_id %r is not [a-z0-9]+" % (rel, vals["installer_id"]))
        for k in ("codename", "installer_id", "app_prefix"):
            if k in vals:
                other = seen.setdefault((k, vals[k]), rel)
                if other != rel:
                    errs.append("%s: %s=%s is also in %s" % (rel, k, vals[k], other))
        if vals.get("install_flow") == "ab-fastbootd" and vals.get("slots") not in ("ab", "ab-virtual"):
            errs.append("%s: install_flow ab-fastbootd needs slots ab or ab-virtual, not %s" % (rel, vals.get("slots")))
        if vals.get("hal_check") == "aidl-table" and vals.get("hal_allowed", "none") == "none":
            errs.append("%s: hal_check aidl-table needs hal_allowed (the check refuses every call without it)" % rel)
        for ent in vals.get("hal_more", "").split():
            ap, _, al = ent.partition(":")
            if vals.get("hal_check") != "aidl-table":
                errs.append("%s: hal_more is for hal_check aidl-table" % rel)
            if not al or not os.path.isdir(os.path.join(root, ap)) or not os.path.isfile(os.path.join(root, al)):
                errs.append("%s: hal_more entry %r is not APP:ALLOWED with both present" % (rel, ent))
        hal_apps = [vals.get("hal_app", "")] + [e.partition(":")[0] for e in vals.get("hal_more", "").split()]
        for ent in vals.get("hal_unbanned", "").split():
            ap, _, ub = ent.partition(":")
            want = "devices/%s/research/unbanned_%s.tsv" % (vals.get("codename", ""), os.path.basename(ap.rstrip("/")).lower())
            if vals.get("hal_check") != "aidl-table":
                errs.append("%s: hal_unbanned is for hal_check aidl-table" % rel)
            if ap.rstrip("/") not in [a.rstrip("/") for a in hal_apps]:
                errs.append("%s: hal_unbanned entry %r: %s is not a HAL app of this device (hal_app, hal_more)" % (rel, ent, ap))
            if ub != want or not os.path.isfile(os.path.join(root, ub)):
                errs.append("%s: hal_unbanned entry %r: the list must be %s and exist" % (rel, ent, want))
        if (vals.get("hal_check") == "none") != (vals.get("hal_app") == "none"):
            errs.append("%s: hal_app must be none exactly when hal_check is none (no HAL caller app in the image)" % rel)
        if "ota_engine" in vals and (vals["ota_engine"] == "none") != (vals.get("ota_wire_id") == "none"):
            errs.append("%s: ota_wire_id must be none exactly when ota_engine is none" % rel)
        if vals.get("ota_engine", "none") != "none" and vals.get("ota_wire_id") != vals.get("installer_id"):
            errs.append("%s: ota_wire_id %s is not installer_id %s (one id per device in downloads and OTA)"
                        % (rel, vals.get("ota_wire_id"), vals.get("installer_id")))
        ot = vals.get("ota_tools")
        if ot is not None:
            if ot != OTA_TOOLS and ot not in WIRE_TOOLS:
                errs.append("%s: ota_tools=%s, not one of %s" % (rel, ot, " ".join((OTA_TOOLS,) + tuple(WIRE_TOOLS))))
            elif not os.path.isdir(os.path.join(root, ot)):
                errs.append("%s: ota_tools=%s does not exist" % (rel, ot))
            if vals.get("ota_engine") != "update_engine":
                errs.append("%s: ota_tools needs ota_engine update_engine, not %s" % (rel, vals.get("ota_engine")))
        kd = vals.get("keys_dir")
        if kd is not None:
            full = os.path.realpath(os.path.expanduser(kd))
            if not (kd == "~/.lumen-keys" or kd.startswith("~/.lumen-keys/")):
                errs.append("%s: keys_dir %s is not under ~/.lumen-keys" % (rel, kd))
            elif full == os.path.realpath(root) or full.startswith(os.path.realpath(root) + os.sep):
                errs.append("%s: keys_dir %s resolves inside the tree" % (rel, kd))
            elif ".." in kd.split("/"):
                errs.append("%s: keys_dir %s has a .. part" % (rel, kd))
        if "codename" in vals:
            validate_image(root, folder, vals.get("image_fs"), errs, notes)
        for k in PATHS + ("hal_codes_also",):
            for v in (vals.get(k) or "").split() if k == "hal_codes_also" else [vals.get(k)]:
                if v and v != "none" and not os.path.exists(os.path.join(root, v)):
                    if v.startswith(PRIVATE_PATHS):
                        notes.append("%s: %s=%s not in this tree (private)" % (rel, k, v))
                    else:
                        errs.append("%s: %s=%s does not exist" % (rel, k, v))
        if vals.get("hal_codes_also") and vals.get("hal_check") != "aidl-table":
            errs.append("%s: hal_codes_also is for hal_check=aidl-table only" % rel)
    # a codename names its own family or a family nobody else uses: horse_popular is the Z9X folder, so another
    # entry of that family has its own folder name and says family=horse_popular
    for rel, (code, fam) in sorted(fams.items()):
        for rel2, (code2, fam2) in sorted(fams.items()):
            if rel2 != rel and code is not None and code == fam2 and fam != fam2:
                errs.append("%s: codename %s is the family of %s, but its own family is %s" % (rel, code, rel2, fam))
    return errs, notes


def installer_profiles(conf):
    """installer/lib/devices.conf -> {id: {KEY: value}} (the installer parses it strictly; this reads it only)."""
    out = {}
    with open(conf, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            m = INSTALLER_LINE.match(line)
            if not m:
                raise RegistryError("%s:%d: not id.KEY=value" % (conf, n))
            out.setdefault(m.group(1), {})[m.group(2)] = m.group(3)
    return out


def check_installer(root, conf):
    """-> (errors, notes): every installer id is a registry device and says the same about it.

    A profile with FAMILY (a projector on another one's XGIMI product image: the registry entry's family is that
    device's codename) names no FB_PRODUCT of its own; the family's profile gives it, and must equal the entry's
    fastboot_product. Across profiles the identity must stay apart: (MODEL, BOARD), IMAGE_TOKEN, the LUMEN_PROP value
    and the CERT file (its certificate bytes) are each one projector's; a CERT next to which the registry keeps the
    device's own certificates (devices/<codename>/certs/ota.x509.pem) is that certificate byte for byte."""
    errs, notes = [], []
    reg = load(root)
    prof = installer_profiles(conf)
    by_iid = {d["installer_id"]: d for d in reg.values()}
    inst = os.path.dirname(os.path.dirname(os.path.abspath(conf)))   # installer/ (conf is installer/lib/devices.conf)
    for iid, p in sorted(prof.items()):
        d = by_iid.get(iid)
        if d is None:
            errs.append("installer id %s: no registry device has installer_id=%s" % (iid, iid))
            continue
        fb = p.get("FB_PRODUCT")
        if "FAMILY" in p:
            fam, fp = p["FAMILY"], prof.get(p["FAMILY"], {})
            fd = by_iid.get(fam)
            if "FB_PRODUCT" in p:
                errs.append("installer id %s: FAMILY and FB_PRODUCT (FAMILY gives the fastbootd product)" % iid)
            elif fam == iid or not fp or "FAMILY" in fp or not fp.get("FB_PRODUCT") or fp.get("MODEL") == p.get("MODEL"):
                errs.append("installer id %s: FAMILY=%s is not another profile with its own FB_PRODUCT and model" % (iid, fam))
            elif fd is None or family(d) != fd["codename"]:
                errs.append("installer id %s: FAMILY=%s, but the registry family of %s is %s" % (iid, fam, d["codename"], family(d)))
            else:
                fb = fp["FB_PRODUCT"]
                notes.append("installer id %s: fastbootd product %s of its family %s (%s)" % (iid, fb, fam, fd["codename"]))
        want = {"NAME": d["name"], "MODEL": d["models"][0], "BOARD": d["hardware"],
                "VENDOR_OK": " ".join(d["vendor_firmware"])}
        if "IMAGE_FS" in p:
            want["IMAGE_FS"] = d["image_fs"]
        if "FLOW" in p:
            want["FLOW"] = d["install_flow"]
        elif d["install_flow"] not in BUILT_IN_FLOWS:
            errs.append("installer id %s: no FLOW line, but %s is not a built-in flow" % (iid, d["install_flow"]))
        for k, v in want.items():
            if p.get(k) != v:
                errs.append("installer id %s: %s=%r, registry %s says %r" % (iid, k, p.get(k), d["codename"], v))
        # a model x firmware table (MODELS_OK): its models are the registry's models, its firmware the registry's
        if "MODELS_OK" in p:
            tm, tf = [], set()
            for e in p["MODELS_OK"].split():
                m, _, fl = e.partition(":")
                tm.append(m)
                tf.update(x for x in fl.split(",") if x)
            if tm != d["models"]:
                errs.append("installer id %s: MODELS_OK models %s, registry %s models %s" % (iid, tm, d["codename"], d["models"]))
            if tf != set(d["vendor_firmware"]):
                errs.append("installer id %s: MODELS_OK firmware %s, registry %s vendor_firmware %s"
                            % (iid, sorted(tf), d["codename"], d["vendor_firmware"]))
        if fb != d["fastboot_product"]:
            errs.append("installer id %s: FB_PRODUCT=%r, registry %s says %r" % (iid, fb, d["codename"], d["fastboot_product"]))
        ns = d["prop_namespace"] + "."
        for k in ("VERSION_PROP", "LUMEN_PROP"):
            if k in p and not p[k].split()[0].startswith(ns):
                errs.append("installer id %s: %s %s is not in %s" % (iid, k, p[k].split()[0], d["prop_namespace"]))
        own = os.path.join(root, "devices", d["codename"], "certs", "ota.x509.pem")
        if p.get("CERT") and os.path.isfile(own):
            cf = os.path.join(inst, p["CERT"])
            if not os.path.isfile(cf) or open(cf, "rb").read() != open(own, "rb").read():
                errs.append("installer id %s: CERT %s is not devices/%s/certs/ota.x509.pem" % (iid, p["CERT"], d["codename"]))
        notes.append("installer id %s = %s: agrees" % (iid, d["codename"]))
    seen = {}
    for iid, p in sorted(prof.items()):
        cf = os.path.join(inst, p["CERT"]) if p.get("CERT") else ""
        keys = [("MODEL+BOARD", "%s %s" % (p.get("MODEL"), p.get("BOARD"))), ("IMAGE_TOKEN", p.get("IMAGE_TOKEN")),
                ("LUMEN_PROP value", (p.get("LUMEN_PROP") or "").partition(" ")[2] or None),
                ("CERT", open(cf, "rb").read() if os.path.isfile(cf) else None)]
        for what, v in keys:
            if not v:
                continue
            other = seen.setdefault((what, v), iid)
            if other != iid:
                errs.append("installer ids %s and %s share their %s: one projector's identity each" % (other, iid, what))
    for d in reg.values():
        if d["installer_id"] not in prof:
            notes.append("%s: no installer lines (installer id %s)" % (d["codename"], d["installer_id"]))
    return errs, notes


def wire_conf(root, d):
    """-> (relative path of the device's wire id file, [its wire_id values]) for an ota_tools of WIRE_TOOLS;
    the values list is None when the file is missing."""
    rel = WIRE_TOOLS[ota_tools(d)].format(codename=d["codename"])
    p = os.path.join(root, rel)
    if not os.path.isfile(p):
        return rel, None
    vals = []
    with open(p, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("wire_id="):
                vals.append(line.split("=", 1)[1])
    return rel, vals


def check_wire(root):
    """-> (errors, notes): the OTA device ids written in the tools (WIRE), or in the device's ota.conf for an
    ota_tools of WIRE_TOOLS, equal the registry's ota_wire_id."""
    errs, notes = [], []
    for d in sorted(load(root).values(), key=lambda x: x["codename"]):
        if d["ota_engine"] == "update_engine" and ota_tools(d) in WIRE_TOOLS:
            rel, vals = wire_conf(root, d)
            if vals is None:
                errs.append("%s: %s is missing (ota_tools=%s)" % (d["codename"], rel, ota_tools(d)))
            elif vals != [d["ota_wire_id"]]:
                errs.append("%s: %s has wire_id %s, not exactly one wire_id=%s (ota_wire_id)"
                            % (d["codename"], rel, " ".join(vals) or "(none)", d["ota_wire_id"]))
            else:
                notes.append("%s: %s has wire_id=%s" % (d["codename"], rel, d["ota_wire_id"]))
            continue
        for rel, fmt in WIRE.get(d["ota_engine"], ()):
            text = fmt.format(id=d["ota_wire_id"])
            p = os.path.join(root, rel)
            if not os.path.isfile(p):
                errs.append("%s: %s is missing" % (d["codename"], rel))
            elif text not in open(p, encoding="utf-8").read():
                errs.append("%s: %s does not contain %r (ota_wire_id=%s)" % (d["codename"], rel, text, d["ota_wire_id"]))
            else:
                notes.append("%s: %s has %s" % (d["codename"], rel, text))
    return errs, notes


def _report(name, errs, notes):
    for n in notes:
        print("devices %s: %s" % (name, n))
    for e in errs:
        print("devices %s: ERROR %s" % (name, e))
    print("devices %s: %s" % (name, "FAILED" if errs else "OK"))
    return 1 if errs else 0


def main(argv):
    root = ROOT
    if argv[:1] == ["--root"] and len(argv) > 1:
        root, argv = os.path.abspath(argv[1]), argv[2:]
    cmd = argv[0] if argv else ""
    try:
        if cmd == "list" and len(argv) == 1:
            for c, d in sorted(load(root).items()):
                print("%s\t%s\t%s" % (c, d["installer_id"], d["name"]))
            return 0
        if cmd == "identify" and len(argv) == 3:
            d = identify(root, argv[1], argv[2])
            if d is None:
                print("devices identify: no entry for ro.product.device %s with model %s" % (argv[1], argv[2]),
                      file=sys.stderr)
                return 1
            print("%s\t%s" % (d["codename"], status(d)))
            return 0
        if cmd == "get" and len(argv) == 3:
            d = get(root, argv[1])
            if argv[2] not in REQUIRED + list(OPTIONAL):
                raise RegistryError("unknown key %r" % argv[2])
            if argv[2] == "family":
                v = family(d)
            elif argv[2] == "status":
                v = status(d)
            else:
                v = d.get(argv[2], "")
            print(" ".join(v) if isinstance(v, list) else v)
            return 0
        if cmd == "image" and len(argv) == 3:
            if argv[2] not in IMAGE_KEYS + IMAGE_OPTIONAL:
                raise RegistryError("unknown image.conf key %r" % argv[2])
            v = image(root, argv[1])[argv[2]]
            print(" ".join(v) if isinstance(v, list) else v)
            return 0
        if cmd == "validate" and len(argv) == 1:
            if not _paths(root):
                return _report(cmd, ["no devices/*/device.conf under %s" % root], [])
            return _report(cmd, *validate(root))
        if cmd == "check-installer" and len(argv) <= 2:
            conf = argv[1] if len(argv) == 2 else os.path.join(root, "installer/lib/devices.conf")
            return _report(cmd, *check_installer(root, conf))
        if cmd == "check-wire" and len(argv) == 1:
            return _report(cmd, *check_wire(root))
    except RegistryError as e:
        print("devices %s: ERROR %s" % (cmd, e))
        return 1
    print(__doc__.split("\n\n")[1], file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
