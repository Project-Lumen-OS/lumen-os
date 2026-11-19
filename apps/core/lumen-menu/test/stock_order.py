#!/usr/bin/env python3
"""stock_order.py GSI_ROOT [--print TREE]: the Keystone section of every devices/*/menu.json is its model's stock page.

test/stock/projection_screen.json holds the stock Keystone page of each model (the children of projection_screen in
the model's own newsettings tree). This derives the Lumen order from it (rows, groups and headers) and checks that
sections.keystone of each profile is exactly that: the trees its "stock" names (settings_tree_product_<tree>) all
give the same order, and the profile's groups equal it, rows it lacks included (MenuSpec skips them).
  --print TREE   prints the derived groups of one tree as menu.json lines.
Exit 1 on any difference."""
import glob
import json
import os
import re
import sys

# stock node -> what Lumen shows for it: a row (one or more features), a header (None: a group without one), the
# line, the correction settings sub page shown in place as a group, or nothing (the row lives in another Lumen
# section or Lumen has no such row). The stock Keystone small title only parts the page from the Focus group above it,
# which is a section of its own in Lumen: the section title says it, never a header repeating the title.
ROW, HEADER, LINE, SUBPAGE, ELSEWHERE = "row", "header", "line", "subpage", "elsewhere"
NODES = {
    "keystone_non_inductive_real_time": (ROW, ["keystone.realtime"]),
    "auto_keystone": (ROW, ["keystone.ak_now"]),
    "auto_keystone_ak3": (ROW, ["keystone.ak_now"]),
    "auto_keystone_ak3_diana": (ROW, ["keystone.ak_now"]),
    "boot_autoKeystone_v5": (ROW, ["keystone.on_power"]),
    "displacement_autoKeystone_v5": (ROW, ["keystone.on_move"]),
    "projection_area_self_adapt": (ROW, ["keystone.obstacle"]),
    "projection_area_self_adapt_diana": (ROW, ["keystone.obstacle"]),
    "curtain_projection_self_adapt": (ROW, ["keystone.curtain"]),
    "curtain_projection_self_adapt_diana": (ROW, ["keystone.curtain"]),
    "manual_keystone": (ROW, ["keystone.manual"]),
    "zoom_and_move": (ROW, ["zoom"]),
    "zoom_and_optical_shift": (ROW, ["zoom"]),
    "zoom_and_displacement": (ROW, ["zoom.digital"]),
    "move_only": (ROW, ["zoom.move"]),
    "picture_screen_rotation": (ROW, ["keystone.rotation"]),
    "lens_memory": (ROW, ["zoom.memory"]),                       # a stock sub page, one Lumen screen
    "reset_keystone": (ROW, ["keystone.restore"]),
    "angle_monitor": (ROW, ["keystone.angle_h", "keystone.angle_v"]),
    "small_title_keystone": (HEADER, None),                      # the section's own title: the page and popup title
    "title_size_and_location": (HEADER, "size_position"),
    "title_smart": (HEADER, "smart"),
    "brightness_more_title": (HEADER, "more"),
    "setting_common_line": (LINE, None),
    "correcting_setting_v5": (SUBPAGE, "auto_correction"),
    "small_title_focus": (ELSEWHERE, "the Focus section"),
    "focus_tab": (ELSEWHERE, "the Focus section"),
    "projection_settings": (ELSEWHERE, "the Projection section"),
    "wall_color_calibration": (ROW, ["keystone.wall_color"]),        # lumen-wallcolor, 2026-11-19
    "color_uniformity_adjustment": (ELSEWHERE, "no Lumen row"),
    "setting_common_back": (ELSEWHERE, "the back key"),
}
# inside a sub page shown in place: its back row, lines and small titles are not drawn (it is one group)
IN_SUBPAGE_SKIP = {"setting_common_back", "setting_common_line", "picture_adjustment_enhancement_title"}
# Lumen rows no stock tree has: each stands right after the row it belongs with, where that row is on the page
LUMEN_ONLY = [("keystone.fit_now", "keystone.ak_now")]
# stock rows Lumen shows in another group of the same page, each right after its anchor (TITAN testers 2026-11-19: the
# level angles, stock "Horizontal Auxiliary Installation", belong with the size and position rows)
MOVED = [("keystone.angle_h", "keystone.rotation"), ("keystone.angle_v", "keystone.angle_h")]


def focus_up(groups, features):
    """Lumen's focus rows right after manual keystone (TITAN testers 2026-11-19): auto focus now, manual focus, then
    the focus.settings sub page with smart focus and the remote picture, each where the profile has it. The stock focus
    group (small_title_focus, focus_tab) stays ELSEWHERE in NODES."""
    rows = [f for f in ("focus.af_now", "focus.manual") if f in features]
    sub = [f for f in ("focus.smart", "focus.guide") if f in features]
    if sub:
        rows.append({"page": "focus.settings", "groups": [{"rows": sub}]})
    out = [(h, list(r)) for h, r in groups]
    for h, r in out:
        if "keystone.manual" in r:
            i = r.index("keystone.manual") + 1
            r[i:i] = rows
            break
    return out


def derive(tree, nodes):
    """The Lumen groups [(header or None, [features])] of one stock projection_screen list."""
    groups, cur, after_sub = [], None, False

    def start(header):
        g = [header, []]
        groups.append(g)
        return g

    for n in nodes:
        name, kids = (n, None) if isinstance(n, str) else next(iter(n.items()))
        if name not in NODES:
            raise SystemExit("stock_order: %s: stock node %s has no Lumen meaning in NODES (decide it)" % (tree, name))
        kind, what = NODES[name]
        if kind == HEADER:
            cur, after_sub = start(what), False
        elif kind == LINE:
            cur, after_sub = start("line"), False
        elif kind == SUBPAGE:
            g = start(what)
            for k in kids or []:
                if isinstance(k, str) and k in IN_SUBPAGE_SKIP:
                    continue
                kn = k if isinstance(k, str) else next(iter(k))
                if kn not in NODES or NODES[kn][0] != ROW:
                    raise SystemExit("stock_order: %s: %s inside %s is no row" % (tree, kn, name))
                g[1].extend(NODES[kn][1])
            cur, after_sub = None, True
        elif kind == ROW:
            if cur is None:
                if after_sub:
                    raise SystemExit("stock_order: %s: rows after the %s sub page without a title or line" % (tree, name))
                cur = start(None)
            cur[1].extend(what)
    for row, anchor in LUMEN_ONLY:
        for g in groups:
            if anchor in g[1]:
                g[1].insert(g[1].index(anchor) + 1, row)
    for row, anchor in MOVED:
        src = next((g for g in groups if row in g[1]), None)
        dst = next((g for g in groups if anchor in g[1]), None)
        if src is not None and dst is not None:
            src[1].remove(row)
            dst[1].insert(dst[1].index(anchor) + 1, row)
    out = []
    for h, rows in groups:
        if rows:
            out.append((None if h == "line" and not out else h, rows))   # a line never opens the page
    return out


def as_json(groups):
    lines = []
    for h, rows in groups:
        g = ({"header": h} if h else {})
        g["rows"] = rows
        lines.append("        " + json.dumps(g, ensure_ascii=False))
    return ",\n".join(lines)


def main(argv):
    root = argv[1]
    stock = json.load(open(os.path.join(root, "apps/core/lumen-menu/test/stock/projection_screen.json"), encoding="utf-8"))
    trees = {t: v["projection_screen"] for t, v in stock.items() if t != "note"}
    if len(argv) == 4 and argv[2] == "--print":
        print(as_json(derive(argv[3], trees[argv[3]])))
        return 0
    bad = n = 0
    for p in sorted(glob.glob(os.path.join(root, "devices/*/menu.json"))):
        dev = os.path.basename(os.path.dirname(p))
        prof = json.load(open(p, encoding="utf-8"))
        ks = [f for f in prof.get("features", {}) if f.startswith("keystone.") or f.startswith("zoom")]
        order = (prof.get("sections") or {}).get("keystone")
        if not ks:
            continue
        n += 1
        if order is None:
            print("FAIL stock order %s: keystone rows but no sections.keystone (its model's stock order)" % dev)
            bad = 1
            continue
        names = re.findall(r"settings_tree_product_([a-z0-9]+)", order.get("stock", ""))
        if not names or any(t not in trees for t in names):
            print("FAIL stock order %s: stock %r names no tree of test/stock/projection_screen.json" % (dev, order.get("stock")))
            bad = 1
            continue
        have = [(g.get("header"), g["rows"]) for g in order["groups"]]
        same = True
        for t in names:
            want = focus_up(derive(t, trees[t]), prof.get("features", {}))
            if have != want:
                print("FAIL stock order %s: sections.keystone is not the %s page\n  profile %s\n  stock   %s" % (dev, t, have, want))
                same = False
        if same:
            print("ok   stock order %s: the keystone page of %s" % (dev, " = ".join(names)))
        else:
            bad = 1
    if n == 0:
        print("FAIL stock order: no profile with keystone rows")
        bad = 1
    return bad


if __name__ == "__main__":
    sys.exit(main(sys.argv))
