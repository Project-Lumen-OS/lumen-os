#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""stock_modes.py GSI_ROOT: the picture mode list of every X50 family profile, per kind of content, is its stock tree's.

Stock (newsettings PictureUtils.getCurSourceType, PictureFactory.imageMode3ItemCheckVisible) shows a picture mode of
image_mode_4_state only while the content kind is in the node's support list: Dolby Vision, IMAX Enhanced, HDR Vivid,
Filmmaker, HDR10, HDR, SDR. Lumen offers an option by its profile support list (lumen-menu SignalRule: an option without
one is offered for every kind but Dolby Vision). For each profile whose picture page names a tree of
test/stock/picture_modes.json, and each kind, the options Lumen offers equal the stock nodes offered, in stock order
(nodes Lumen does not port, and the modes a model's Lumen list leaves out on purpose, named below with the reason).
IMAX Enhanced is in the list exactly when the model's stock feature.xml has support_imax true.
Exit 1 on any difference."""
import json
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "tools", "devices"))
import menu_layers  # noqa: E402  (menu profiles have one reader, devices/README.md)

# stock node -> Lumen picture.mode token
TOKEN = {
    "image_mode_ai": "ai", "image_mode_sport": "sport", "image_mode_soft_athena": "soft",
    "image_mode_imax_enhanced": "imax", "picture_filmmaker_mode": "filmmaker", "image_mode_isf_light": "isf_day",
    "image_mode_isf_night": "isf_night", "image_mode_prof_color_accuracy": "color_accuracy",
    "image_mode_office_platform_9270": "office", "image_mode_office_athena3": "office",
    "image_mode_performance": "performance", "image_mode_picture_dolby_bright": "dv_bright",
    "image_mode_picture_dolby_soft": "dv_dark", "image_mode_movice_v3": "movie", "image_mode_light_athena3": "bright",
    "image_mode_standard_hw": "standard", "image_mode_movie_oversea_xw": "movie", "image_mode_cinema": "cinema",
    "image_mode_cinema_bollywood": "cinema", "image_mode_gorgeous": "gorgeous", "image_mode_keep_eye": "eye_care",
}
# stock nodes that are no Lumen picture mode, every tree
NOT_PORTED = {
    "image_mode_hdr_vivid": "HDR Vivid mode, not ported (Sequences: a signal's own mode)",
    "picture_dolby_curtain": "the Dolby Vision screen row, picture.dv_screen, not a mode",
    "image_mode_brights_xw": "not in the v1.3.1 oscar build the AURA 3 Max runs",
    "image_mode_picture_dolby_gamming": "not in the v1.3.1 oscar build the AURA 3 Max runs",
}
# modes a model's Lumen list leaves out on purpose
LEFT_OUT = {
    "dianapro": {"performance": "Sequences.PICTURE_MODES: stock asks first with its warning and a Boost switch"},
    "dianamax": {"performance": "Sequences.PICTURE_MODES: stock asks first with its warning and a Boost switch"},
    "bollywood": {"dv_bright": "feature.xml support_dolby_vision false", "dv_dark": "feature.xml support_dolby_vision false"},
    "bollywoodpro": {"dv_bright": "feature.xml support_dolby_vision false", "dv_dark": "feature.xml support_dolby_vision false"},
}
# stock content kind -> Lumen Signal token
KINDS = {"SDR": "sdr", "HDR": "hdr", "HDR10": "hdr10", "DOLBY": "dolby", "HdrVivid": "hdr_vivid", "IMAX": "imax",
         "Filmmaker": "filmmaker"}


def lumen_offered(pm, kind):
    sup = pm.get("support", {})
    return [o for o in pm["options"] if (kind in sup[o] if sup.get(o) else kind != "dolby")]


def main(argv):
    root = argv[1] if len(argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..")
    stock = json.load(open(os.path.join(root, "apps/core/lumen-menu/test/stock/picture_modes.json"), encoding="utf-8"))
    bad, seen = [], 0
    for dev in sorted(os.listdir(os.path.join(root, "devices"))):
        path = os.path.join(root, "devices", dev, "menu.json")
        if not os.path.isfile(path):
            continue
        prof = menu_layers.effective(path)
        trees = sorted(set(re.findall(r"settings_tree_product_([a-z0-9]+) image", json.dumps(prof))) & set(stock["trees"]))
        pm = prof.get("features", {}).get("picture.mode")
        if not trees or not pm:
            continue
        seen += 1
        for tree in trees:
            nodes = stock["trees"][tree]
            unknown = [n for n, _ in nodes if n not in TOKEN and n not in NOT_PORTED]
            if unknown:
                bad.append("%s %s: stock nodes without a Lumen token %s" % (dev, tree, unknown))
            left = LEFT_OUT.get(tree, {})
            for sk, lk in KINDS.items():
                want = [TOKEN[n] for n, s in nodes if n in TOKEN and TOKEN[n] not in left and sk in (s or [])]
                have = lumen_offered(pm, lk)
                if want != have:
                    bad.append("%s %s with %s content: stock %s, Lumen %s" % (dev, tree, sk, want, have))
        imax = stock["support_imax"].get(prof.get("model"))
        if imax is None:
            bad.append("%s: no stock support_imax of %s in the fixture" % (dev, prof.get("model")))
        elif imax[1] != ("imax" in pm["options"]):
            bad.append("%s: IMAX Enhanced %s, stock %s (%s)" % (dev, "listed" if "imax" in pm["options"] else "missing",
                                                               imax[1], imax[0]))
    if seen < 14:
        bad.append("only %d X50 family profiles name a stock tree (14 expected)" % seen)
    for b in bad:
        print("FAIL stock modes " + b)
    print(("ok   " if not bad else "FAIL ") + "stock_modes: %d profiles, picture mode list per content kind" % seen)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
