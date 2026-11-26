#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Quick menu key walk on an EMULATOR (test APK of test/ui/build.sh with WALK=QuickWalkActivity, never a projector):
every setting of the full menu can be put into the quick menu (2026-11-24). Presses remote keys with adb and
CHECKS after each step what test/ui/QuickWalkActivity dumps (card, editor list, strip, the stand-in projector's values,
the full menu's rows).

    python3 quick_walk.py SERIAL PACKAGE CHOICE TOGGLE [--also ID ...] [--absent ID ...] [--seed JSON] [--shots DIR]
                          [--via TITLE]

CHOICE is the choice row to put on the card (picture.memc), TOGGLE the switch row (picture.dynamic_black on X50,
another one where the model has no dynamic black), --also more switch rows (AI contrast). Each is added from the
editor's own list (a full card gives up tiles first), changed from its tile and compared with the full menu's row, the
menu survives an app restart and Reset gives the default card back. With CHOICE "-" the walk only checks that the
--absent rows are not offered (a model without MEMC) and that a stored menu with them (--seed) shows no such tile. One
PASS/FAIL line per check, exit 1 on any failure."""
import json
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
A = sys.argv[1:]
SERIAL, PKG, CHOICE, TOGGLE = A[0], A[1], A[2], A[3]
ABSENT = [a for i, a in enumerate(A) if i > 0 and A[i - 1] == "--absent"]
ALSO = [a for i, a in enumerate(A) if i > 0 and A[i - 1] == "--also"]
SPARE = ["game", "mirroring", "eye", "picture.3d", "bluetooth", "input", "focus", "sound"]   # tiles given up for room
SEED = next((A[i + 1] for i, a in enumerate(A) if a == "--seed"), None)
SHOTS = next((A[i + 1] for i, a in enumerate(A) if a == "--shots"), None)
VIA = next((A[i + 1] for i, a in enumerate(A) if a == "--via"), None)
ACT = "org.lumen.menu.walk.QuickWalkActivity"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4, "enter": 66}
SLIDER = "picture.pq.contrast"
FAILS = []


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=60).stdout.replace("\r", "")


def key(n, wait=0.8):
    sh("input keyevent %d" % KEY[n])
    time.sleep(wait)


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + str(detail)[:400] + ")"))
    if not ok:
        FAILS.append(name)
        if os.environ.get("QUICK_WALK_FIRST_FAIL"):           # the mutant runs: one red is enough
            sys.exit(finish())
    return ok


def st():
    t = sh("dumpsys activity %s/%s" % (PKG, ACT))
    s = {"tiles": {}, "watch": {}, "picks": [], "rows": [], "raw": t}
    for line in t.splitlines():
        line = line.strip()
        for tag in ("menu", "focus", "cell", "edit", "adjust", "sets", "cardrows"):
            if line.startswith("walk.%s " % tag):
                s[tag] = line[len(tag) + 6:]
        if line.startswith("walk.tile "):
            m = dict(re.findall(r"(\w+)=(.*?)(?= \w+=|$)", line[10:]))
            s["tiles"][m["key"]] = m
        elif line.startswith("walk.watch "):
            k, rest = line[11:].split(" ", 1)
            s["watch"][k] = rest
        elif line.startswith("walk.picks "):
            s["picks"] = [p.split("~") for p in line[11:].split(" | ") if p]
        elif line.startswith("walk.row "):
            s["rows"].append(line[9:])
    s["grid"] = [[k.strip() for k in r.split(",") if k.strip()] for r in re.findall(r"\[([^\[\]]*)\]", s.get("cardrows", ""))]
    return s


def mode(s):
    m = re.search(r"mode=(\d+)", s.get("edit", ""))
    return int(m.group(1)) if m else None


def focused_pick(s):
    for p in s["picks"]:
        if p[-1] == "FOCUS":
            return p[0]
    return None


def pick_keys(s):
    return [p[0] for p in s["picks"]]


def card_go(target, tries=24):
    """Moves the card's focus to the tile `target` (rows up and down first, then along the row)."""
    for _ in range(tries):
        s = st()
        cell = s.get("cell", "none")
        pos = [(r, c) for r, row in enumerate(s["grid"]) for c, k in enumerate(row) if k == target]
        if not pos or cell == "none":
            return False
        r, c = map(int, cell.split(","))
        tr, tc = pos[0]
        if (r, c) == (tr, tc):
            return True
        if r != tr:
            key("down" if tr > r else "up", 0.6)
        else:
            key("right" if tc > c else "left", 0.6)
    return False


def sheet_go(target, tries=320):
    """Moves the editor sheet's focus to the pick `target` (DOWN to the end, then UP)."""
    last = None
    direction = "down"
    missing = 0
    for _ in range(tries):
        s = st()
        f = focused_pick(s)
        if f == target:
            return True
        missing = missing + 1 if target not in pick_keys(s) else 0
        if missing >= 3:
            return False
        if f == last and direction == "down":
            direction = "up"
        last = f
        key(direction, 0.3)
    return False


def tile(s, k):
    return s["tiles"].get(k)


def watch(s, k, field):
    m = re.search(r"\b%s=(.*?)(?= \w+=|$)" % field, s["watch"].get(k, ""))
    return m.group(1) if m else None


def shot(name):
    if not SHOTS:
        return
    sh("screencap -p /sdcard/qw.png")
    subprocess.run([ADB, "-s", SERIAL, "pull", "/sdcard/qw.png", os.path.join(SHOTS, name)], capture_output=True)


def stored():
    return sh("settings get secure lumen_quick_menu").strip()


def items(js):
    """The item ids of a stored quick menu, in order (the seen list is not the menu)."""
    try:
        return [i["id"] for i in json.loads(js)["items"]]
    except (ValueError, KeyError, TypeError):
        return []


def open_menu():
    sh("am start -n %s/%s" % (PKG, ACT))
    time.sleep(2.5)


def close_menu():
    for _ in range(4):
        if "showing=true" not in st().get("menu", ""):
            return
        key("back", 0.8)


def editor_open():
    if not card_go("edit"):
        return False
    key("ok", 1.2)
    return mode(st()) == 0


def to_sheet():
    """RIGHT from the card until the editor's sheet has the focus (the card's rightmost tile, then the sheet)."""
    for _ in range(6):
        if focused_pick(st()) is not None:
            return True
        key("right", 0.6)
    return focused_pick(st()) is not None


def editor_list():
    """The editor's catalogue with the sheet focused: every item, the full menu's rows too, in the list itself."""
    to_sheet()
    return mode(st()) == 0


def add_from_list(item):
    if not sheet_go(item):
        return "absent"
    key("ok", 1.0)
    if mode(st()) != 2:
        return "refused"
    key("ok", 1.0)                                         # put it where the editor placed it
    return "added" if item in [k for r in st()["grid"] for k in r] else "lost"


def add_making_room(item):
    """Adds `item` from the list; a full card refuses with its note, then the next spare tile goes and it tries again."""
    r = add_from_list(item)
    while r == "refused" and SPARE:
        check("a full card refuses %s with a note, the editor stays in the list" % item, mode(st()) == 0, st().get("edit"))
        gone = SPARE.pop(0)
        if gone in [k for row in st()["grid"] for k in row]:
            check("the %s tile removed to make room" % gone, remove_tile(gone), st()["grid"])
        to_sheet()
        r = add_from_list(item)
    return r


def remove_tile(item):
    """In the editor: the tile on the card, OK, Remove."""
    s = st()
    if focused_pick(s) is not None:
        key("left", 0.8)
    if not card_go(item):
        return False
    key("ok", 1.0)
    if mode(st()) != 1 or not sheet_go("remove"):
        return False
    key("ok", 1.0)
    return item not in [k for r in st()["grid"] for k in r]


def done_editor():
    for _ in range(5):
        if mode(st()) is None:
            return True
        key("back", 1.0)
    return mode(st()) is None


def has_row(s, title):
    return any((" title=%s " % title) in r + " " for r in s["rows"])


def full_menu_row(title, via=None):
    """All settings by the gear: the category whose page has the row `title` (or the page row `via` that opens the
    page with it), the focus on that row."""
    if not card_go("general"):
        return None
    key("ok", 2.0)
    for k in ["down"] * 10 + ["up"] * 12:
        s = st()
        if has_row(s, title) or via and has_row(s, via):
            break
        key(k, 0.7)
    key("right", 0.8)
    if via and not has_row(st(), title):
        for _ in range(45):
            if (" title=%s " % via) in st().get("focus", "") + " ":
                break
            key("down", 0.4)
        key("ok", 1.5)
    for _ in range(60):
        s = st()
        if (" title=%s " % title) in s.get("focus", "") + " ":
            return s
        key("down", 0.4)
    return None


def leave_all():
    for _ in range(4):
        if "all=true" not in st().get("menu", ""):
            return
        key("back", 1.0)


def main():
    sh("am force-stop " + PKG)
    sh("pm grant %s android.permission.WRITE_SECURE_SETTINGS" % PKG)
    sh("settings delete secure lumen_quick_menu")
    sh("settings put global lumen_walk_hide ''")
    sh("settings put global lumen_walk_watch %s" % ",".join([x for x in (CHOICE, TOGGLE, SLIDER) if x != "-"] + ALSO + ABSENT))
    if SEED:
        sh("settings put secure lumen_quick_menu '%s'" % SEED)
    sh("input keyevent 3")
    time.sleep(1)
    open_menu()
    s = st()
    check("the card opens", "showing=true" in s.get("menu", ""), s.get("menu"))
    if CHOICE == "-":
        for a in ABSENT:
            check("a model without %s: not in the catalogue" % a, watch(s, a, "catalogue") == "false", s["watch"].get(a))
            check("a model without %s: no tile although the stored menu has one" % a, tile(s, a) is None, sorted(s["tiles"]))
        check("the editor opens", editor_open(), s.get("edit"))
        check("the editor's list is focused", editor_list(), st().get("edit"))
        s = st()
        for a in ABSENT:
            check("a model without %s: the editor does not offer it" % a, a not in pick_keys(s), pick_keys(s))
        check("the other picture rows are offered", any(k.startswith("picture.") for k in pick_keys(s)), pick_keys(s))
        done_editor()
        return finish()
    start_grid = st()["grid"]
    # --- the editor: every setting of the full menu in its own list, the choice and the switches added
    check("the pencil opens the editor", editor_open(), st().get("edit"))
    check("the editor's list is focused", editor_list(), st().get("edit"))
    s = st()
    picks = pick_keys(s)
    for item in [CHOICE, TOGGLE] + ALSO:
        check("%s is in the editor's list itself with Add" % item, any(p[0] == item and p[2] != "null" for p in s["picks"]), picks)
    check("no detour list: no All settings entry in the editor", "all" not in picks, picks)
    for a in ABSENT:
        check("this model does not offer %s (its profile has none)" % a, a not in picks, picks)
    sheet_go(CHOICE)
    shot("editor_list.png")
    for item in [CHOICE, TOGGLE] + ALSO:
        r = add_making_room(item)
        check("%s added to the card" % item, r == "added", r)
        s = st()
        check("back in the list with %s In menu" % item, mode(s) == 0 and any(p[0] == item and p[1] for p in s["picks"]),
              (s.get("edit"), [p for p in s["picks"] if p[0] == item]))
    shot("editor_added.png")
    # the search
    sheet_go("search")
    key("ok", 2.5)
    front = sh("dumpsys window | grep mCurrentFocus")
    check("the search opens the text screen", "TextEditActivity" in front, front.strip())
    sh("input text memc")
    time.sleep(0.6)
    key("enter", 2.5)
    s = st()
    check("the search finds %s" % CHOICE, mode(s) == 4 and "query=memc" in s.get("edit", "") and CHOICE in pick_keys(s),
          (s.get("edit"), pick_keys(s)))
    check("the editor stays open after the text screen", "showing=true" in s.get("menu", ""), s.get("menu"))
    sheet_go(CHOICE)
    key("ok", 1.0)
    check("OK on a found item in the menu opens its inspector", mode(st()) == 1, st().get("edit"))
    key("back", 1.0)
    s = st()
    check("BACK from the inspector: the search results again", mode(s) == 4 and "query=memc" in s.get("edit", ""), s.get("edit"))
    key("back", 0.8)
    check("BACK: the editor's list again", mode(st()) == 0, st().get("edit"))
    check("the editor closes with BACK and stores the menu", done_editor(), st().get("edit"))
    js = stored()
    ids = items(js)
    check("the stored menu has every added tile in order", all(i in ids for i in [CHOICE, TOGGLE] + ALSO)
          and ids.index(CHOICE) < ids.index(TOGGLE), js)
    # --- each switch tile flips its row, the tile and the full menu's row agree
    for item in ALSO:
        card_go(item)
        key("ok", 1.4)
        s = st()
        check("OK on the %s tile turns it on on the projector" % item, watch(s, item, "value") == "on", s["watch"].get(item))
        check("the %s tile is lit and says On" % item, tile(s, item)["active"] == "true" and tile(s, item)["value"] == "On", tile(s, item))
        check("the %s row of the full menu is on" % item, "checked=true" in s["watch"].get(item, ""), s["watch"].get(item))
        key("ok", 1.4)
        s = st()
        check("OK again turns %s off, tile and row agree" % item, watch(s, item, "value") == "off"
              and tile(s, item)["active"] == "false" and "checked=false" in s["watch"].get(item, ""), (tile(s, item), s["watch"].get(item)))
    # --- the menu survives a restart of the app: the same tiles, each showing what its full menu row shows
    sh("am force-stop " + PKG)
    open_menu()
    s = st()
    check("after a restart the stored menu is the same", stored() == js, stored())
    for item in [CHOICE, TOGGLE] + ALSO:
        t = tile(s, item)
        check("after a restart the %s tile is there with its row's value" % item, t is not None
              and t["value"] == (watch(s, item, "text") if item == CHOICE else ("On" if "checked=true" in s["watch"].get(item, "") else "Off")),
              (t, s["watch"].get(item)))
    # --- the choice tile cycles
    s = st()
    first = tile(s, CHOICE)
    check("the %s tile shows the value of its row" % CHOICE, first is not None and first["value"] == watch(s, CHOICE, "text"),
          (first, s["watch"].get(CHOICE)))
    card_go(CHOICE)
    shot("memc_tile.png")
    key("ok", 1.4)
    s = st()
    v1 = watch(s, CHOICE, "value")
    check("OK on the choice tile steps to the next option on the projector", v1 not in (None, "null") and v1 != "off", s["watch"].get(CHOICE))
    check("the tile shows what the full menu's row shows", tile(s, CHOICE)["value"] == watch(s, CHOICE, "text") != "Off",
          (tile(s, CHOICE), s["watch"].get(CHOICE)))
    shot("memc_tile_changed.png")
    n = 0
    for _ in range(8):                                    # round to the first option again
        key("ok", 1.2)
        n += 1
        if watch(st(), CHOICE, "value") == "off":
            break
    check("the choice tile wraps round to the first option", watch(st(), CHOICE, "value") == "off", (n, st()["watch"].get(CHOICE)))
    key("ok", 1.4)                                         # back to the second option for the full menu check
    want = watch(st(), CHOICE, "text")
    # --- the switch tile flips
    card_go(TOGGLE)
    shot("memc_tile_value.png")                            # the choice tile at rest with the value set from it
    key("ok", 1.4)
    s = st()
    check("OK on the switch tile turns it on on the projector", watch(s, TOGGLE, "value") == "on", s["watch"].get(TOGGLE))
    check("the switch tile is lit and says On", tile(s, TOGGLE)["active"] == "true" and tile(s, TOGGLE)["value"] == "On", tile(s, TOGGLE))
    check("the switch row of the full menu is on", "checked=true" in s["watch"].get(TOGGLE, ""), s["watch"].get(TOGGLE))
    # --- the full menu shows the same and a change there reaches the tile
    title = re.search(r"title=(.*?) enabled=", st()["watch"][CHOICE]).group(1)
    s = full_menu_row(title, VIA)
    check("All settings: the %s row is reached by keys" % title, s is not None)
    if s:
        shown = re.search(r"text=(.*)$", s["focus"]).group(1).strip()
        check("All settings shows the value set from the tile (%s)" % want, shown == want, shown)
        key("right", 1.4)
        s = st()
        now = re.search(r"text=(.*)$", s["focus"]).group(1).strip()
        leave_all()
        s = st()
        check("a change in All settings shows on the tile", tile(s, CHOICE) is not None and tile(s, CHOICE)["value"] == now != want,
              (now, tile(s, CHOICE)))
    # --- a live rule: the row hidden now (a signal), the tile greyed and inert
    close_menu()
    sh("settings put global lumen_walk_hide %s" % CHOICE)
    before = watch(st(), CHOICE, "value")
    open_menu()
    s = st()
    check("a row hidden now greys its tile", tile(s, CHOICE) is not None and float(tile(s, CHOICE)["alpha"]) < 1, tile(s, CHOICE))
    card_go(CHOICE)
    key("ok", 1.4)
    check("OK on the greyed tile changes nothing", watch(st(), CHOICE, "value") == before, st()["watch"].get(CHOICE))
    close_menu()
    sh("settings put global lumen_walk_hide ''")
    open_menu()
    check("the rule gone: the tile usable again", float(tile(st(), CHOICE)["alpha"]) == 1.0, tile(st(), CHOICE))
    # --- remove the choice tile, put a picture slider as a small tile, the strip
    check("the editor opens again", editor_open())
    check("the %s tile removed from the card" % CHOICE, remove_tile(CHOICE), st()["grid"])
    done_editor()
    js = stored()
    check("stored: %s gone, the switch kept" % CHOICE, CHOICE not in items(js) and TOGGLE in items(js), js)
    # a picture slider as a small tile (the full X50 card has no room for its slider look: put in the stored menu)
    close_menu()
    menu = json.loads(js)
    menu["items"] = [i for i in menu["items"] if i["id"] not in ALSO]
    last = max(n for n, i in enumerate(menu["items"]) if i["as"] in ("small", "wide"))
    menu["items"].insert(last + 1, {"id": SLIDER, "as": "small"})
    sh("settings put secure lumen_quick_menu '%s'" % json.dumps(menu))
    open_menu()
    s = st()
    check("the card has no %s tile any more" % CHOICE, tile(s, CHOICE) is None, sorted(s["tiles"]))
    check("the slider is a tile now", tile(s, SLIDER) is not None, sorted(s["tiles"]))
    card_go(SLIDER)
    key("ok", 1.2)
    s = st()
    check("OK on the slider tile opens the strip", "strip=true" in s.get("menu", "") and "active=true" in s.get("adjust", "")
          and "walk=true" in s.get("adjust", ""), (s.get("menu"), s.get("adjust")))
    key("right", 1.0)
    key("right", 1.4)
    s = st()
    check("RIGHT in the strip changes the value on the projector", watch(s, SLIDER, "value") == "52", s["watch"].get(SLIDER))
    check("the full menu's slider row shows it", "number=52" in s["watch"].get(SLIDER, ""), s["watch"].get(SLIDER))
    shot("slider_strip.png")
    key("down", 1.0)
    s = st()
    check("DOWN: the strip moves to the next row of the page", "row=Contrast" not in s.get("adjust", "") and "active=true" in s.get("adjust", ""),
          s.get("adjust"))
    key("up", 1.0)
    key("ok", 1.2)
    s = st()
    check("OK closes the strip, the menu is back on the slider tile", "strip=false" in s.get("menu", "") and "key=" + SLIDER in s.get("focus", ""),
          (s.get("menu"), s.get("focus")))
    # --- Reset: the default card again, nothing stored
    check("the editor opens for the reset", editor_open(), st().get("edit"))
    to_sheet()
    check("Reset is reached in the editor", sheet_go("reset"), focused_pick(st()))
    key("ok", 1.2)
    check("the editor closes and deletes the stored menu", done_editor() and stored() in ("null", ""), stored())
    s = st()
    check("reset: the default card is back", s["grid"] == start_grid, (s["grid"], start_grid))
    sh("am force-stop " + PKG)
    open_menu()
    check("reset: the default card after a restart too", st()["grid"] == start_grid, st()["grid"])
    key("back", 1.0)
    return finish()


def finish():
    sh("settings put global lumen_walk_hide ''")
    sh("am force-stop " + PKG)
    print("quick_walk %s: %s" % (PKG, "PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
