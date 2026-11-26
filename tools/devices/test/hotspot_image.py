#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The hotspot in every image (lumen-system Hotspot, the Network popup): a model whose profile has network.hotspot
must drop the emulator's tether overlay (EmulatorTetheringConfigOverlay: Wi-Fi tether list wlan only, a product
overlay that outranks the vendor TetheringRROOverlay with ap0), else Android starts the hotspot on ap0 and hands the
phones no address. Per recipe: Z9X tar_rm, HB and Play 6 the shared 32-bit debloat list, RS30 (Ultra Max and Ultra) its debloat
list, the X50 family remove.txt. Also: the profile's iface is one the vendor tether list (wlan, softap, ap + digit) matches and
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
# layered.py of this tree reads the descriptors of G (a scratch copy in the mutants) with their family files
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import layered  # noqa: E402
import menu_layers  # noqa: E402
def on_recipe(steps):
    """The models whose build.conf (with its family files) names STEPS: no hand list."""
    return [c for c in sorted(os.listdir(os.path.join(G, "devices"))) if os.path.isfile(os.path.join(G, "devices", c, "build.conf"))
            and layered.effective(os.path.join(G, "devices", c, "build.conf")).get("steps") == steps]


# every model on the X50 recipe
for x50 in on_recipe("devices/g0111/image/steps.tsv"):
    RECIPES[x50] = ("devices/g0111/image/remove.txt", "system/product/overlay/" + OVERLAY,
                    ("devices/%s/build.conf" % x50, "devices/g0111/image/remove.txt"))
# every other model on the RS30 recipe (its steps apply debloat.txt) with the RS30 debloat list in its build.conf
for rs30 in on_recipe("devices/g0155/image/steps.tsv"):
    if rs30 != "g0155":
        RECIPES[rs30] = ("devices/g0155/image/debloat.txt", "product/overlay/" + OVERLAY,
                         ("devices/%s/build.conf" % rs30, " devices/g0155/image/debloat.txt "))

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
    dc = os.path.join(G, "devices", d, "device.conf")
    if not os.path.isfile(mj):
        continue
    if os.path.isfile(dc) and layered.effective(dc).get("image") == "none":
        continue  # data without an image recipe yet (devices.py image=none): no image to drop the overlay from
    feats = menu_layers.effective(mj).get("features", {})
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
        if arel.endswith(".conf"):  # a descriptor value, its own or its family's (overlays=, include= chain)
            vals = layered.effective(os.path.join(G, arel)).values()
            found = awant.strip() in " ".join(v if isinstance(v, str) else " ".join(v) for v in vals).split()
        else:
            found = awant in open(os.path.join(G, arel), encoding="utf-8").read() + "\n"
        if not found:
            bad.append("%s: %s does not apply %s" % (d, arel, rel))
    iface = hs.get("iface", "")
    if not fullmatch(VENDOR, iface) or fullmatch(EMULATOR, iface):
        bad.append("%s: iface %r is not the vendor AP interface the vendor tether list adds" % (d, iface))
    conf = layered.text(os.path.join(G, "devices", d, "device.conf"))
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
