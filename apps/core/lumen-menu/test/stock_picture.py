#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""stock_picture.py GSI_ROOT [--print TREE]: the Picture section of every X50 family devices/*/menu.json is its model's
stock image page.

test/stock/picture_image.json holds the stock image page of each X50 family model (the children of image in the
model's own newsettings tree, with its picture settings sub page image_mode_setting). This derives the Lumen order from
it (rows, groups, headers and the picture settings page) and checks that sections.picture of each profile with a
picture.settings page is exactly that: the trees its "stock" names (settings_tree_product_<tree>) all give the same
order, and the profile's groups equal it, rows it lacks included (MenuSpec skips them). It also checks the stock facts
the profile's rows carry: the dynamic black level tuning's range is the stock slider's (-40..40) and is on a model only
when its tree has image_mode_vibrant_correct; the Boost step only on a model with it (support_brightness_boost 2, the
X50 and TITAN trees, not the RS30's).
  --print TREE   prints the derived groups of one tree as menu.json lines.
Exit 1 on any difference."""
import glob
import json
import os
import re
import sys

# stock node -> what Lumen shows for it: a row (one or more features: the four picture sliders are one Lumen row with
# four sliders), a header, the picture settings page (its rows open behind one Lumen row), or nothing (the row lives in
# another Lumen section, the page title says it, or Lumen has no such row).
ROW, HEADER, PAGE, ELSEWHERE = "row", "header", "page", "elsewhere"
NODES = {
    # the image page
    "projector_brightness_title": (HEADER, "projector_brightness"),
    "env_adapt_settings_kirin": (ELSEWHERE, "no Lumen row (the ambient light adaptation stays out, owner decision)"),
    "brightness_adjustment": (ROW, ["lamp"]),                     # the BrightnessCell slider; Boost = its step 11
    "picture_iris_ring_adjustment_diana": (ROW, ["picture.iris"]),
    "picture_iris_ring_adjustment_athena3ultramax": (ROW, ["picture.iris"]),
    "image_title_v4": (HEADER, "image"),
    "image_mode_4_state": (ROW, ["picture.mode", "picture.custom_new"]),   # the list ends with the "Custom" entry
    "image_mode_setting": (PAGE, "picture.settings"),
    "reset_image_mode_all_setting": (ROW, ["picture.reset_all"]),
    "brightness_more_title": (HEADER, "image_more"),
    "image_3d_info_state": (ROW, ["picture.3d"]),
    "game_enhance_mode_entrance": (ELSEWHERE, "the Game section (its tile and page)"),
    "picture_3d_lut_bt": (ROW, ["picture.style_filter"]),
    "picture_anti_rbe": (ROW, ["picture.anti_rbe"]),
    "picture_anti_rbe_entrance": (ROW, ["picture.anti_rbe"]),     # TITAN: a sub page with the switch and two modes
    # the picture settings page (image_mode_setting)
    "image_setting_title": (ELSEWHERE, "the page title"),
    "image_mode_two_title": (ELSEWHERE, "the page title (the picture mode's name)"),
    "image_mode_custom_base_diana": (ROW, ["picture.custom_base"]),     # user picture modes: the mode it is based on
    "image_mode_custom_base_dianahy": (ROW, ["picture.custom_base"]),     # user picture modes: the mode it is based on
    "image_mode_custom_base_athena3": (ROW, ["picture.custom_base"]),     # user picture modes: the mode it is based on
    "small_title_basic": (HEADER, "basic"),
    "image_set_mode_auto_switch": (ROW, ["picture.auto_switch"]),
    "image_mode_bright": (ROW, ["picture.pq"]),
    "image_mode_constrast_ratio": (ROW, ["picture.pq"]),
    "image_mode_saturation": (ROW, ["picture.pq"]),
    "image_mode_sharpness": (ROW, ["picture.pq"]),
    "image_mode_motion_compensation_v4": (ROW, ["picture.memc"]),
    "color_title_text_title": (HEADER, "colour"),
    "image_mode_4_color_temp_diana": (ROW, ["picture.color_temp"]),
    "image_mode_4_color_temp_athena35": (ROW, ["picture.color_temp"]),
    "image_mode_prof_setting_whiteBalance": (ROW, ["picture.wb"]),          # MenuSpec's white balance page
    "picture_gamut_diana": (ROW, ["picture.gamut"]),
    "picture_gamut_athenaultra": (ROW, ["picture.gamut"]),
    "image_mode_custom_color_optimization": (ROW, ["picture.color_opt"]),
    "image_mode_custom_color_correction": (ROW, ["picture.cc"]),           # MenuSpec's colour correction page
    "image_set_mode_constrast_ratio_text_title": (HEADER, "contrast"),
    "image_mode_gamma": (ROW, ["picture.gamma"]),
    "image_mode_custom_gamma_athenaultra": (ROW, ["picture.gamma_index"]),
    "picture_hdr_enhancement": (ROW, ["picture.hdr"]),                    # MenuSpec's HDR enhancement page
    "image_mode_part_constrast_ratio": (ROW, ["picture.local_contrast"]),
    "image_mode_custom_dynamic_contrast": (ROW, ["picture.dynamic_contrast"]),
    "image_ai_contrast": (ROW, ["picture.ai_contrast"]),
    "image_dynamic_black_bit_enhancement": (ROW, ["picture.dynamic_black"]),
    "image_mode_vibrant_correct": (ROW, ["picture.black_tuning"]),
    "image_clarity_text": (HEADER, "clarity"),
    "image_mode_noise_reduct": (ROW, ["picture.noise"]),
    "picture_super_resolution_switch": (ROW, ["picture.super_res"]),
    "small_title_more": (HEADER, "image_more"),
    "reset_image_mode_setting": (ROW, ["picture.reset"]),
    "delete_image_mode_user_setting": (ROW, ["picture.custom_delete"]),
}
# Lumen rows the stock image page does not hold: each stands right after the row it belongs with
#  - picture.dv_screen: stock picture_dolby_curtain, the last node inside the picture mode list (image_mode_4_state)
LUMEN_AFTER = [("picture.dv_screen", "picture.mode")]
# and the group Lumen keeps at the end of Picture: the HDMI signal rows and the aspect ratio (stock: the HDMI input
# page and the signal source settings, not the image page)
LUMEN_TAIL = ("hdmi", ["hdmi.range", "hdmi.edid", "picture.aspect"])
# the stock slider of image_mode_vibrant_correct (VibrantProgressCell setMinProgress / setMaxProgress)
BLACK_TUNING = {"min": -40, "max": 40}


def name_of(n):
    return (n, None) if isinstance(n, str) else next(iter(n.items()))


def derive_groups(tree, nodes, inside=False):
    """[(header or None, [features or {"page": id, "groups": [...]}])] of one stock list (the image page or its page)."""
    groups, cur = [], None
    for n in nodes:
        name, kids = name_of(n)
        if name == "setting_common_back":
            continue
        if name not in NODES:
            raise SystemExit("stock_picture: %s: stock node %s has no Lumen meaning in NODES (decide it)" % (tree, name))
        kind, what = NODES[name]
        if kind == HEADER:
            cur = [what, []]
            groups.append(cur)
        elif kind == PAGE:
            if inside:
                raise SystemExit("stock_picture: %s: page %s inside a page" % (tree, name))
            if cur is None:
                cur = [None, []]
                groups.append(cur)
            cur[1].append({"page": what, "groups": derive_groups(tree, kids or [], True)})
        elif kind == ROW:
            if cur is None:
                cur = [None, []]
                groups.append(cur)
            for f in what:
                if f not in cur[1]:
                    cur[1].append(f)
    for row, anchor in LUMEN_AFTER:
        for g in groups:
            if anchor in g[1]:
                g[1].insert(g[1].index(anchor) + 1, row)
    if not inside:
        groups.append([LUMEN_TAIL[0], list(LUMEN_TAIL[1])])
    return [{"header": h, "rows": rows} if h else {"rows": rows} for h, rows in groups if rows]


def has_node(nodes, want):
    for n in nodes:
        name, kids = name_of(n)
        if name == want or (kids and has_node(kids, want)):
            return True
    return False


def as_json(groups):
    return ",\n".join("        " + json.dumps(g, ensure_ascii=False) for g in groups)


def main(argv):
    root = argv[1]
    stock = json.load(open(os.path.join(root, "apps/core/lumen-menu/test/stock/picture_image.json"), encoding="utf-8"))
    trees = {t: v["image"] for t, v in stock.items() if t != "note"}
    if len(argv) == 4 and argv[2] == "--print":
        print(as_json(derive_groups(argv[3], trees[argv[3]])))
        return 0
    bad = n = 0
    for p in sorted(glob.glob(os.path.join(root, "devices/*/menu.json"))):
        dev = os.path.basename(os.path.dirname(p))
        prof = json.load(open(p, encoding="utf-8"))
        feats = prof.get("features", {})
        if "picture.settings" not in feats:
            continue
        n += 1
        order = (prof.get("sections") or {}).get("picture")
        if order is None:
            print("FAIL stock picture %s: a picture.settings page but no sections.picture (its model's stock order)" % dev)
            bad = 1
            continue
        names = re.findall(r"settings_tree_product_([a-z0-9]+)", order.get("stock", ""))
        if not names or any(t not in trees for t in names):
            print("FAIL stock picture %s: stock %r names no tree of test/stock/picture_image.json" % (dev, order.get("stock")))
            bad = 1
            continue
        same = True
        for t in names:
            want = derive_groups(t, trees[t])
            if order["groups"] != want:
                print("FAIL stock picture %s: sections.picture is not the %s image page\n  profile %s\n  stock   %s"
                      % (dev, t, json.dumps(order["groups"]), json.dumps(want)))
                same = False
            tuning = has_node(trees[t], "image_mode_vibrant_correct")
            if ("picture.black_tuning" in feats) != tuning:
                print("FAIL stock picture %s: picture.black_tuning %s, the %s tree %s image_mode_vibrant_correct"
                      % (dev, "present" if "picture.black_tuning" in feats else "absent", t, "has" if tuning else "lacks"))
                same = False
            elif tuning and {k: feats["picture.black_tuning"].get(k) for k in BLACK_TUNING} != BLACK_TUNING:
                print("FAIL stock picture %s: picture.black_tuning %s, stock %s" % (dev, feats["picture.black_tuning"], BLACK_TUNING))
                same = False
            # the Boost step: support_brightness_boost 2 on the dianapro / dianamax / dianahy builds, 0 on athena35ultramax
            boost = not t.startswith("athena")
            if ("boost" in feats) != boost:
                print("FAIL stock picture %s: boost %s, the %s build %s the Boost step"
                      % (dev, "present" if "boost" in feats else "absent", t, "has" if boost else "lacks"))
                same = False
        if same:
            print("ok   stock picture %s: the image page of %s" % (dev, " = ".join(names)))
        else:
            bad = 1
    if n == 0:
        print("FAIL stock picture: no profile with a picture.settings page")
        bad = 1
    return bad


if __name__ == "__main__":
    sys.exit(main(sys.argv))
