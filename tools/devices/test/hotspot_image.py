#!/usr/bin/env python3
"""The hotspot in every image (lumen-system Hotspot, the Network popup): a model whose profile has network.hotspot
must drop the emulator's tether overlay (EmulatorTetheringConfigOverlay: Wi-Fi tether list wlan only, a product
overlay that outranks the vendor TetheringRROOverlay with ap0), else Android starts the hotspot on ap0 and hands the
phones no address. Per recipe: Z9X tar_rm, HB and Play 6 the shared 32-bit debloat list, RS30 its debloat list, the
X50 family remove.txt. Also: the profile's iface is one the vendor tether list (wlan, softap, ap + digit) matches and
the emulator's list does not, and the default name is "Lumen " + the registry name without "XGIMI ".

  python3 tools/devices/test/hotspot_image.py [REPO]
"""
import json, os, re, sys

G = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", ".."))
OVERLAY = "EmulatorTetheringConfigOverlay.apk"
VENDOR = [r"wlan\d", r"softap\d", r"ap\d"]        # TetheringRROOverlay config_tether_wifi_regexs of the stock dumps
EMULATOR = [r"wlan\d"]                            # EmulatorTetheringConfigOverlay
# how each recipe drops the overlay: (file, the line that must be there, the step that applies the file)
RECIPES = {
    "horse_popular": ("devices/horse_popular/image/steps.tsv", "*\ttar_rm\tsystem/product/overlay/" + OVERLAY, None),
    "goat_elegant": ("devices/_image/debloat_gsi_tv_arm.txt", "product/overlay/" + OVERLAY,
                     ("devices/goat_elegant/image/steps.tsv", "*\tremove\tdebloat_gsi_tv_arm.txt\t{sys}")),
    "dragon_healthy": ("devices/_image/debloat_gsi_tv_arm.txt", "product/overlay/" + OVERLAY,
                       ("devices/dragon_healthy/image/steps.tsv", "*\tremove\tdebloat_gsi_tv_arm.txt\t{sys}")),
    "g0155": ("devices/g0155/image/debloat.txt", "product/overlay/" + OVERLAY,
              ("devices/g0155/image/steps.tsv", "\tremove\tdebloat.txt\t{sys}")),
}
for x50 in ("g0111", "g0100", "h0062", "h0063"):
    RECIPES[x50] = ("devices/g0111/image/remove.txt", "system/product/overlay/" + OVERLAY,
                    ("devices/%s/build.conf" % x50, "devices/g0111/image/remove.txt"))

bad = []


def lines(rel):
    with open(os.path.join(G, rel), encoding="utf-8") as f:
        return f.read().split("\n")


def has_line(rel, want):
    return any(l == want or (want.startswith("\t") and l.endswith(want)) or l.split("\t")[0] == want for l in lines(rel))


def fullmatch(res, iface):
    return any(re.fullmatch(r, iface) for r in res)


n = 0
for d in sorted(os.listdir(os.path.join(G, "devices"))):
    mj = os.path.join(G, "devices", d, "menu.json")
    if not os.path.isfile(mj):
        continue
    feats = json.load(open(mj, encoding="utf-8")).get("features", {})
    hs = feats.get("network.hotspot")
    if hs is None:
        continue
    n += 1
    if d not in RECIPES:
        bad.append("%s: profile has network.hotspot, but no recipe rule drops %s" % (d, OVERLAY))
        continue
    rel, want, applied = RECIPES[d]
    if not has_line(rel, want):
        bad.append("%s: %s does not drop the emulator tether overlay (%s)" % (d, rel, want))
    if applied is not None:
        arel, awant = applied
        text = open(os.path.join(G, arel), encoding="utf-8").read()
        if awant not in text:
            bad.append("%s: %s does not apply %s" % (d, arel, rel))
    iface = hs.get("iface", "")
    if not fullmatch(VENDOR, iface) or fullmatch(EMULATOR, iface):
        bad.append("%s: iface %r is not the vendor AP interface the vendor tether list adds" % (d, iface))
    conf = open(os.path.join(G, "devices", d, "device.conf"), encoding="utf-8").read()
    m = re.search(r"^name=XGIMI (.+)$", conf, re.M)
    want_name = "Lumen " + m.group(1).strip() if m else None
    if hs.get("name") != want_name:
        bad.append("%s: default hotspot name %r, want %r (device.conf name)" % (d, hs.get("name"), want_name))

if n == 0:
    bad.append("no profile has network.hotspot")
for b in bad:
    print("FAIL hotspot image: " + b)
if not bad:
    print("ok   hotspot image: %d models drop the emulator tether overlay, iface and default name match" % n)
sys.exit(1 if bad else 0)
