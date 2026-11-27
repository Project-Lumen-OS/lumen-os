#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""cec_defaults.py: the HDMI-CEC defaults of every Lumen image are the stock XGIMI ones, one table for all models
(2026-11-15: do it the way stock does it; stock sends no <Standby> to the HDMI devices when it sleeps).

  cec_defaults.py [--root DIR] check        exit 0 when everything below holds, 1 with every reason otherwise
  cec_defaults.py [--root DIR] write        (re)writes res/values/lumen_cec.xml of every framework RRO
  cec_defaults.py stock APK...              the covered values of stock framework-res.apk files, one column each
                                            (how the columns of devices/_image/cec_stock.tsv were read)

Data (DIR defaults to the tree this script lives in):
  devices/_image/cec_defaults.tsv   the table: 'default SETTING VALUE' (system_server HdmiCecConfig name and value)
                                    and 'reset DEVICE SETTING' (apps/core/lumen-cec CecDefaults: after an update the
                                    stock value is put back once on that device). Every projector app with the
                                    lumen-cec module ships it as the asset lumen_cec/defaults.tsv.
  devices/_image/cec_stock.tsv      the evidence: 'dump ID DEVICE SHA256' per stock framework-res.apk, then a 'cols'
                                    line and one line per covered resource with its value in each column.

check:
  - the table names every covered setting once with a valid value; a reset row names a supported device and a
    setting the app can write
  - every stock dump column (and only those columns) agrees with the table for every covered resource, every
    supported device has a dump of its own stock firmware
  - every supported device's image has exactly one framework RRO (an app of its build.conf whose overlay targets
    "android"); its res/values/lumen_cec.xml equals the rendered table; no other resource file under apps/ sets a
    config_cec* value (no device keeps its own copy)
  - every device recipe with the lumen-cec module ships the table as assets/lumen_cec/defaults.tsv
The RRO states every _default bool of every covered setting (not only those that differ from LineageOS): an older
overlay of lower priority in the same image (the Z9X 1.0.9 base of the X50 images) cannot leave a value of its own.
"""
import hashlib
import os
import re
import sys
import zipfile

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "lumen"))
import devices  # noqa: E402

TABLE = "devices/_image/cec_defaults.tsv"
STOCK = "devices/_image/cec_stock.tsv"
ASSET = "assets/lumen_cec/defaults.tsv"
RRO_FILE = "res/values/lumen_cec.xml"
# setting -> ((value, resource bool of that value being the default), ...) as HdmiCecConfig reads them (LineageOS 21
# frameworks/base services/core/java/com/android/server/hdmi/HdmiCecConfig.java, values of HdmiControlManager)
SETTINGS = (
    ("hdmi_cec_enabled", (("1", "config_cecHdmiCecControlEnabled_default"),
                          ("0", "config_cecHdmiCecControlDisabled_default"))),
    ("tv_send_standby_on_sleep", (("1", "config_cecTvSendStandbyOnSleepEnabled_default"),
                                  ("0", "config_cecTvSendStandbyOnSleepDisabled_default"))),
    ("tv_wake_on_one_touch_play", (("1", "config_cecTvWakeOnOneTouchPlayEnabled_default"),
                                   ("0", "config_cecTvWakeOnOneTouchPlayDisabled_default"))),
    ("power_control_mode", (("to_tv", "config_cecPowerControlModeTv_default"),
                            ("to_tv_and_audio_system", "config_cecPowerControlModeTvAndAudioSystem_default"),
                            ("broadcast", "config_cecPowerControlModeBroadcast_default"),
                            ("none", "config_cecPowerControlModeNone_default"))),
    ("power_state_change_on_active_source_lost", (
        ("none", "config_cecPowerStateChangeOnActiveSourceLostNone_default"),
        ("standby_now", "config_cecPowerStateChangeOnActiveSourceLostStandbyNow_default"))),
    ("set_menu_language", (("1", "config_cecSetMenuLanguageEnabled_default"),
                           ("0", "config_cecSetMenuLanguageDisabled_default"))),
    # the codecs RequestSadAction asks the audio system for, four per <Request Short Audio Descriptor>
    ("query_sad_lpcm", (("1", "config_cecQuerySadLpcmEnabled_default"), ("0", "config_cecQuerySadLpcmDisabled_default"))),
    ("query_sad_dd", (("1", "config_cecQuerySadDdEnabled_default"), ("0", "config_cecQuerySadDdDisabled_default"))),
    ("query_sad_mpeg1", (("1", "config_cecQuerySadMpeg1Enabled_default"), ("0", "config_cecQuerySadMpeg1Disabled_default"))),
    ("query_sad_mp3", (("1", "config_cecQuerySadMp3Enabled_default"), ("0", "config_cecQuerySadMp3Disabled_default"))),
    ("query_sad_mpeg2", (("1", "config_cecQuerySadMpeg2Enabled_default"), ("0", "config_cecQuerySadMpeg2Disabled_default"))),
    ("query_sad_aac", (("1", "config_cecQuerySadAacEnabled_default"), ("0", "config_cecQuerySadAacDisabled_default"))),
    ("query_sad_dts", (("1", "config_cecQuerySadDtsEnabled_default"), ("0", "config_cecQuerySadDtsDisabled_default"))),
    ("query_sad_atrac", (("1", "config_cecQuerySadAtracEnabled_default"), ("0", "config_cecQuerySadAtracDisabled_default"))),
    ("query_sad_onebitaudio", (("1", "config_cecQuerySadOnebitaudioEnabled_default"), ("0", "config_cecQuerySadOnebitaudioDisabled_default"))),
    ("query_sad_ddp", (("1", "config_cecQuerySadDdpEnabled_default"), ("0", "config_cecQuerySadDdpDisabled_default"))),
    ("query_sad_dtshd", (("1", "config_cecQuerySadDtshdEnabled_default"), ("0", "config_cecQuerySadDtshdDisabled_default"))),
    ("query_sad_truehd", (("1", "config_cecQuerySadTruehdEnabled_default"), ("0", "config_cecQuerySadTruehdDisabled_default"))),
    ("query_sad_dst", (("1", "config_cecQuerySadDstEnabled_default"), ("0", "config_cecQuerySadDstDisabled_default"))),
    ("query_sad_wmapro", (("1", "config_cecQuerySadWmaproEnabled_default"), ("0", "config_cecQuerySadWmaproDisabled_default"))),
    ("query_sad_max", (("1", "config_cecQuerySadMaxEnabled_default"), ("0", "config_cecQuerySadMaxDisabled_default"))),
)
# the switches the projector app writes itself (lumen-cec CecSettings), so a reset row may name only these
RESETTABLE = ("tv_send_standby_on_sleep", "tv_wake_on_one_touch_play")
CEC_RES = re.compile(r'name="(config_cec[A-Za-z0-9_]*)"')
# a resource line value: the stock bool, or no copy of that stock file left when the row was added (no evidence)
UNREAD = "unread"
STOCK_VALUES = ("true", "false", UNREAD)


def settings():
    return dict(SETTINGS)


def resources():
    """{resource: (setting, value)} of every covered _default bool."""
    return {res: (s, v) for s, vals in SETTINGS for v, res in vals}


def _value_error(known, s, v):
    if s not in known:
        return "unknown setting %s" % s
    if v not in [x for x, _ in known[s]]:
        return "%s has no value %s (one of %s)" % (s, v, " ".join(x for x, _ in known[s]))
    return None


class Table:
    """devices/_image/cec_defaults.tsv: the defaults, the per-device stock values and the one-time resets."""

    def __init__(self, text, where=TABLE):
        known = settings()
        self.defaults, self.devices, self.resets, self.errs = {}, {}, [], []
        self.owner = {}
        self.stock_again = {}
        for n, line in enumerate(text.splitlines(), 1):
            f = line.split("#", 1)[0].split()
            if not f:
                continue
            at = "%s:%d" % (where, n)
            if f[0] == "default" and len(f) == 3:
                bad = _value_error(known, f[1], f[2])
                if bad:
                    self.errs.append("%s: %s" % (at, bad))
                elif f[1] in self.defaults:
                    self.errs.append("%s: %s given twice" % (at, f[1]))
                else:
                    self.defaults[f[1]] = f[2]
            elif f[0] == "device" and len(f) == 4:
                bad = _value_error(known, f[2], f[3])
                if bad:
                    self.errs.append("%s: %s" % (at, bad))
                elif f[2] in self.devices.get(f[1], {}):
                    self.errs.append("%s: %s %s given twice" % (at, f[1], f[2]))
                else:
                    self.devices.setdefault(f[1], {})[f[2]] = f[3]
            elif f[0] == "owner" and len(f) == 3:
                bad = _value_error(known, f[1], f[2])
                if bad:
                    self.errs.append("%s: %s" % (at, bad))
                elif f[1] in self.owner:
                    self.errs.append("%s: owner %s given twice" % (at, f[1]))
                else:
                    self.owner[f[1]] = f[2]
            elif f[0] == "stock_again" and len(f) == 3:
                if f[1] not in RESETTABLE:
                    self.errs.append("%s: stock_again of %s: the app writes only %s" % (at, f[1], " ".join(RESETTABLE)))
                elif f[2] not in ("0", "1"):
                    self.errs.append("%s: stock_again %s is 0 or 1" % (at, f[1]))
                elif f[1] in self.stock_again:
                    self.errs.append("%s: stock_again %s given twice" % (at, f[1]))
                else:
                    self.stock_again[f[1]] = f[2] == "1"
            elif f[0] == "reset" and len(f) == 3:
                if f[2] not in RESETTABLE:
                    self.errs.append("%s: reset of %s: the app writes only %s" % (at, f[2], " ".join(RESETTABLE)))
                elif (f[1], f[2]) in self.resets:
                    self.errs.append("%s: reset %s %s given twice" % (at, f[1], f[2]))
                else:
                    self.resets.append((f[1], f[2]))
            else:
                self.errs.append("%s: expected 'default SETTING VALUE', 'device DEVICE SETTING VALUE' or 'reset DEVICE "
                                 "SETTING': %s" % (at, line.strip()))
        for s in self.stock_again:
            if s not in self.owner:
                self.errs.append("%s: stock_again %s without an owner line" % (where, s))
        for s in known:
            if s not in self.defaults:
                self.errs.append("%s: no default for %s" % (where, s))
        for dev, vals in sorted(self.devices.items()):
            for s, v in sorted(vals.items()):
                if self.defaults.get(s) == v:
                    self.errs.append("%s: device %s %s %s equals the default (drop the line)" % (where, dev, s, v))

    def values(self, device):
        """{setting: value} of the stock system of one device (the evidence the stock dumps are checked against)."""
        out = dict(self.defaults)
        out.update(self.devices.get(device, {}))
        return out

    def image_values(self, device):
        """{setting: value} the image of one device ships: the stock values with the maintainer's lines on top."""
        out = self.values(device)
        out.update({s: v for s, v in self.owner.items() if not self.stock_again.get(s)})
        return out


class Stock:
    """devices/_image/cec_stock.tsv: the stock dumps, their resource columns and their Android 11 set lines."""

    def __init__(self, text, where=STOCK):
        known = settings()
        self.dumps, self.cols, self.rows, self.sets, self.errs = {}, None, {}, {}, []
        for n, line in enumerate(text.splitlines(), 1):
            f = line.split("#", 1)[0].split()
            if not f:
                continue
            at = "%s:%d" % (where, n)
            if f[0] == "dump" and len(f) == 4 and re.match(r"^[0-9a-f]{64}$", f[3]):
                self.dumps[f[1]] = (f[2].split(","), f[3])
            elif f[0] == "cols" and self.cols is None:
                self.cols = f[1:]
            elif f[0] == "set" and len(f) == 4:
                bad = _value_error(known, f[2], f[3])
                if bad:
                    self.errs.append("%s: %s" % (at, bad))
                else:
                    self.sets.setdefault(f[1], {})[f[2]] = f[3]
            elif self.cols is not None and len(f) == len(self.cols) + 1 and all(x in STOCK_VALUES for x in f[1:]):
                self.rows[f[0]] = dict(zip(self.cols, f[1:]))
            else:
                self.errs.append("%s: expected 'dump ID DEVICES SHA256', 'cols ID...', 'set ID SETTING VALUE' or a "
                                 "resource line: %s" % (at, line.strip()))
        if self.cols is None:
            self.errs.append("%s: no cols line" % where)
            self.cols = []


def stock_errors(table, stock, known_devices):
    """Every disagreement of a stock dump with the table, every device without a dump of its own stock."""
    errs = []
    for col in stock.cols:
        if col not in stock.dumps and col != "lineage-21":
            errs.append("%s: column %s has no dump line" % (STOCK, col))
    for d in stock.sets:
        if d not in stock.dumps:
            errs.append("%s: set lines of %s, which has no dump line" % (STOCK, d))
    for d, (devs, _sha) in sorted(stock.dumps.items()):
        if d not in stock.cols and d not in stock.sets:
            errs.append("%s: dump %s has neither a column nor set lines" % (STOCK, d))
        if d in stock.sets:
            for s in ("hdmi_cec_enabled",) + RESETTABLE:
                if s not in stock.sets[d]:
                    errs.append("%s: %s (Android 11) has no set line for %s" % (STOCK, d, s))
        for dev in devs:
            if dev not in known_devices:
                errs.append("%s: dump %s names %s, no supported device" % (STOCK, d, dev))
                continue
            vals = table.values(dev)
            for res, (s, v) in sorted(resources().items()):
                if d not in stock.cols:
                    break
                got = stock.rows.get(res, {}).get(d)
                want = "true" if vals[s] == v else "false"
                if got is None:
                    errs.append("%s: no line for %s" % (STOCK, res))
                elif got == UNREAD:
                    continue
                elif got != want:
                    errs.append("%s: %s %s = %s, the table gives %s %s = %s" % (STOCK, d, res, got, dev, s, vals[s]))
            for s, v in sorted(stock.sets.get(d, {}).items()):
                if vals[s] != v:
                    errs.append("%s: %s %s = %s, the table gives %s %s = %s" % (STOCK, d, s, v, dev, s, vals[s]))
    have = {dev for devs, _ in stock.dumps.values() for dev in devs}
    for c in sorted(known_devices):
        if c not in have:
            errs.append("%s: no stock dump of %s" % (STOCK, c))
    return sorted(set(errs))


def render(vals, sources):
    """The RRO values file of the table (byte for byte what check compares)."""
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           "<!-- Generated by tools/devices/cec_defaults.py from %s: do not edit." % TABLE,
           "     For %s." % sources,
           "     The HDMI-CEC defaults of the stock XGIMI system (evidence %s), read by system_server's" % STOCK,
           "     HdmiCecConfig; a value the user stored wins. Every value of each setting is stated, so no overlay of",
           "     lower priority in the image keeps one of its own. -->",
           "<resources>"]
    for s, choices in SETTINGS:
        out.append("    <!-- %s = %s -->" % (s, vals[s]))
        for v, res in choices:
            out.append('    <bool name="%s">%s</bool>' % (res, "true" if v == vals[s] else "false"))
    out.append("</resources>")
    return "\n".join(out) + "\n"


def framework_rro(root, app):
    """True when apps/<app> is a static overlay on framework-res ("android")."""
    m = os.path.join(root, "apps", app, "AndroidManifest.xml")
    return os.path.isfile(m) and re.search(r'android:targetPackage="android"', open(m, encoding="utf-8").read())


def device_apps(root, codename):
    p = os.path.join(root, "devices", codename, "build.conf")
    if not os.path.isfile(p):
        return None
    vals, errs = devices.parse(p, ("apps",), others=True)
    if errs:
        raise devices.RegistryError(errs[0])
    return vals.get("apps", "").split()


def rros(root, errs):
    """{rro app: [codenames]} of every supported device with a build.conf."""
    out = {}
    for c in sorted(devices.load(root)):
        apps = device_apps(root, c)
        if apps is None:
            continue
        fw = [a for a in apps if framework_rro(root, a)]
        if len(fw) != 1:
            errs.append("devices/%s/build.conf: %d framework RROs (%s): the CEC defaults need exactly one"
                        % (c, len(fw), " ".join(fw) or "none"))
            continue
        out.setdefault(fw[0], []).append(c)
    return out


def recipes_with_cec(root):
    """[(recipe path, text)] of every device app recipe that builds the lumen-cec module in."""
    import assemble_app  # every recipe a model builds, its own or its family's (devices/README.md)
    out = []
    for c, _, f in assemble_app.all_recipes(root):
        t = open(f, encoding="utf-8").read()
        rel = os.path.relpath(f, root)
        if re.search(r"^module\s+lumen-cec\s*$", t, re.M):
            out.append((rel if rel.startswith("devices/%s/" % c) else "%s (%s)" % (rel, c), t))
    return out


def check(root, write=False):
    errs, notes = [], []
    table = Table(open(os.path.join(root, TABLE), encoding="utf-8").read())
    stock_ = Stock(open(os.path.join(root, STOCK), encoding="utf-8").read())
    errs += table.errs + stock_.errs
    known = devices.load(root)
    for dev, _s in table.resets:
        if dev not in known:
            errs.append("%s: reset on %s: no such supported device" % (TABLE, dev))
    for dev in table.devices:
        if dev not in known:
            errs.append("%s: device line of %s: no such supported device" % (TABLE, dev))
    if errs:
        return errs, notes
    # ---- the stock evidence
    errs += stock_errors(table, stock_, known)
    # ---- the framework RRO of every image
    by_rro = rros(root, errs)
    for rro, cs in sorted(by_rro.items()):
        vals = [table.image_values(c) for c in cs]
        if any(v != vals[0] for v in vals):
            errs.append("apps/%s serves %s, whose stock CEC values differ: one RRO cannot carry both"
                        % (rro, " ".join(cs)))
            continue
        want = render(vals[0], ", ".join("devices/%s" % c for c in cs))
        path = os.path.join(root, "apps", rro, RRO_FILE)
        if write:
            if not os.path.isfile(path) or open(path, encoding="utf-8").read() != want:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(want)
                notes.append("wrote apps/%s/%s" % (rro, RRO_FILE))
        elif not os.path.isfile(path):
            errs.append("apps/%s/%s missing (cec_defaults.py write)" % (rro, RRO_FILE))
        elif open(path, encoding="utf-8").read() != want:
            errs.append("apps/%s/%s differs from %s (cec_defaults.py write)" % (rro, RRO_FILE, TABLE))
        else:
            notes.append("apps/%s: the stock CEC defaults for %s" % (rro, " ".join(cs)))
    generated = {os.path.join("apps", r, RRO_FILE) for r in by_rro}
    for top, dirs, files in os.walk(os.path.join(root, "apps")):
        dirs[:] = sorted(d for d in dirs if d not in ("build", "test", ".git"))
        for f in sorted(files):
            if not f.endswith(".xml") or "/res/values" not in top.replace(os.sep, "/") + "/":
                continue
            rel = os.path.relpath(os.path.join(top, f), root)
            if rel in generated:
                continue
            hit = CEC_RES.findall(open(os.path.join(top, f), encoding="utf-8", errors="replace").read())
            if hit:
                errs.append("%s sets %s: the CEC defaults live only in %s" % (rel, ", ".join(sorted(set(hit))), TABLE))
    # ---- the table as the asset of every app with the CEC page
    recipes = recipes_with_cec(root)
    for rel, t in recipes:
        if not re.search(r"^file\s+%s\s+%s\s*$" % (re.escape(TABLE), re.escape(ASSET)), t, re.M):
            errs.append("%s: has the lumen-cec module but does not ship '%s %s'" % (rel, TABLE, ASSET))
    if not errs:
        notes.append("cec defaults: %d settings, %d device value(s), %d reset(s), %d stock dumps, %d framework RROs, "
                     "%d app recipes" % (len(table.defaults), sum(len(v) for v in table.devices.values()),
                                         len(table.resets), len(stock_.dumps), len(by_rro), len(recipes)))
    return errs, notes


def stock(apks):
    import apkinfo  # tools/lumen/apkinfo.py
    print("# " + "  ".join(apks))
    for apk in apks:
        print("# %s sha256 %s" % (apk, hashlib.sha256(open(apk, "rb").read()).hexdigest()))
    blobs = []
    for apk in apks:
        with open(apk, "rb") as f:
            blobs.append(f.read())
    for res in sorted(resources()):
        line = [res]
        for b in blobs:
            v = [x for x in apkinfo.resource_values(b, "bool", res) if x["default"]]
            line.append("missing" if not v else ("true" if v[0]["data"] else "false"))
        print("  ".join(line))
    return 0


def main(argv):
    root = os.path.dirname(os.path.dirname(HERE))
    if argv[:1] == ["--root"] and len(argv) >= 2:
        root, argv = os.path.abspath(argv[1]), argv[2:]
    if argv[:1] == ["stock"] and len(argv) > 1:
        return stock(argv[1:])
    if argv not in (["check"], ["write"]):
        print(__doc__)
        return 2
    try:
        errs, notes = check(root, write=argv == ["write"])
    except (OSError, devices.RegistryError, zipfile.BadZipFile) as e:
        print("cec_defaults: %s" % e)
        return 1
    for n in notes:
        print("ok   " + n)
    for e in errs:
        print("RED  " + e)
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
