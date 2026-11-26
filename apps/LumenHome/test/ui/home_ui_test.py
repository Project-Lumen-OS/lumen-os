#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Lumen Home key walk on an emulator (or a test unit): presses remote keys with adb and CHECKS what Home and its side
panel do, read from `dumpsys activity org.lumen.home/.HomeActivity` (HomeActivity.dump). Renders alone never showed the
Customize Home bugs (the panel closing and opening again, the check mark left on the old look, switches that did not
move): every step here asserts the panel, its marks, the focus and the stored layout.

python3 home_ui_test.py SERIAL [--only NAME,...]   (adb from $ADB or PATH; the stored layout and theme are put back)
Exit 0 when every check passed, 1 otherwise. One line per check: PASS/FAIL <test> <what>.
"""
import json
import os
import re
import struct
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("SERIAL", "emulator-5554")
ONLY = None
if "--only" in sys.argv:
    ONLY = set(sys.argv[sys.argv.index("--only") + 1].split(","))
PKG = "org.lumen.home"
ACT = PKG + "/.HomeActivity"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4, "menu": 82, "home": 3}
SETTLE = float(os.environ.get("SETTLE", "1.6"))
RESULTS = []


def sh(cmd, timeout=40):
    r = subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=timeout)
    return r.stdout.replace("\r", "")


def key(*names):
    sh("input keyevent " + " ".join(str(KEY[n]) for n in names))


def fields(rest):
    """key=value pairs of one dump line; a value runs to the next ' key=' (titles and texts have spaces)."""
    out = {}
    parts = re.split(r"(?:^| )([a-zA-Z]+)=", rest)
    for i in range(1, len(parts) - 1, 2):
        out[parts[i]] = parts[i + 1]
    return out


class State:
    def __init__(self, text):
        self.home, self.layout, self.plan, self.theme, self.status, self.panel, self.page = {}, {}, {}, {}, {}, {}, {}
        self.overlay, self.keys, self.cont = {}, {}, {}
        self.items, self.dock = [], []
        for line in text.splitlines():
            line = line.strip()
            for tag, attr in (("lumen.home ", "home"), ("lumen.layout ", "layout"), ("lumen.plan ", "plan"),
                              ("lumen.theme ", "theme"), ("lumen.status ", "status"), ("lumen.panel ", "panel"),
                              ("lumen.panel.page ", "page"), ("lumen.overlay ", "overlay"), ("lumen.keys ", "keys"),
                              ("lumen.cont ", "cont")):
                if line.startswith(tag):
                    setattr(self, attr, fields(line[len(tag):]))
            if line.startswith("lumen.item "):
                f = fields(line[len("lumen.item "):].split(" ", 1)[1])
                self.items.append(f)
            if line.startswith("lumen.dock "):
                self.dock = [t.strip() for t in line[len("lumen.dock "):].strip("[] ").split(",") if t.strip()]

    @property
    def open(self):
        return self.panel.get("open") == "true"

    @property
    def opens(self):
        return int(self.panel.get("opens", "-1"))

    @property
    def depth(self):
        return int(self.panel.get("depth", "0"))

    def focused(self):
        for it in self.items:
            if it.get("focused") == "true":
                return it.get("tag")
        return None

    def item(self, tag):
        for it in self.items:
            if it.get("tag") == tag:
                return it
        return None

    def checked(self, prefix):
        return [it["tag"] for it in self.items if it.get("tag", "").startswith(prefix) and it.get("on") == "true"]


def state():
    s = State("")
    for _ in range(3):
        s = State(sh("dumpsys activity " + ACT))
        if s.home:
            return s
        time.sleep(1)
    return s


def window():
    out = sh("dumpsys window | grep -m1 mCurrentFocus")
    return out.strip()


def input_focus():
    """The window the input dispatcher sends keys to. It follows the window manager's focus only once the new window
    has drawn: an app that starts cold shows in mCurrentFocus while keys still go to Home for a few hundred ms."""
    return sh("dumpsys input | grep -A1 FocusedWindows: | tail -1").strip()


HID_USAGE = {"ok": 0x41, "up": 0x42, "down": 0x43, "left": 0x44, "right": 0x45, "menu": 0x40, "back": 0x224}
HID_DESC = [0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x19, 0x00, 0x2A, 0x3C, 0x02, 0x15, 0x00, 0x26, 0x3C, 0x02,
            0x95, 0x01, 0x75, 0x10, 0x81, 0x00, 0xC0]


def hold(name, ms):
    """Holds a key on a virtual HID remote (the `hid` tool): Android's own key repeats, as a real remote held down."""
    u = HID_USAGE[name]
    steps = [{"id": 1, "command": "register", "name": "Lumen test remote", "vid": 0x18d1, "pid": 0x7a11, "bus": "usb",
              "descriptor": HID_DESC}, {"id": 1, "command": "delay", "duration": 1500},
             {"id": 1, "command": "report", "report": [u & 0xFF, u >> 8]}, {"id": 1, "command": "delay", "duration": ms},
             {"id": 1, "command": "report", "report": [0, 0]}, {"id": 1, "command": "delay", "duration": 300}]
    data = "\n".join(json.dumps(x) for x in steps) + "\n"
    subprocess.run([ADB, "-s", SERIAL, "shell", "cat > /data/local/tmp/home_ui_hid.json"], input=data, text=True, timeout=30)
    sh("hid /data/local/tmp/home_ui_hid.json; rm -f /data/local/tmp/home_ui_hid.json", timeout=60)


def strip(top, left):
    """Raw screencap rows 0..top from x = left to the right edge (the panel title band), bytes."""
    raw = subprocess.run([os.environ.get("ADB", "adb"), "-s", SERIAL, "exec-out", "screencap"], capture_output=True,
                         timeout=40).stdout
    w, h = struct.unpack("<II", raw[:8])
    head = len(raw) - w * h * 4
    if top <= 0 or head < 12 or left >= w:
        return b""
    return b"".join(raw[head + (y * w + left) * 4: head + (y * w + w) * 4] for y in range(min(top, h)))


def check(test, ok, what, got=None):
    RESULTS.append((test, bool(ok), what))
    print(("PASS " if ok else "FAIL ") + test + ": " + what + ("" if ok else "  [got " + str(got) + "]"), flush=True)
    return ok


def settle(t=None):
    time.sleep(SETTLE if t is None else t)


# ------------------------------------------------------------------ navigation


def start_home():
    sh("am force-stop " + PKG)
    sh("am start -W -n " + ACT)
    for _ in range(30):
        s = state()
        if s.home.get("ready") == "true":
            settle(2)
            return state()
        time.sleep(1)
    raise SystemExit("Home did not get ready")


def close_panel():
    for _ in range(5):
        s = state()
        if not s.open:
            return s
        key("back")
        settle(0.8)
    return state()


def focus_home(label_prefix, moves):
    """Moves Home's focus (panel closed) with the given direction keys until it starts with label_prefix."""
    for d in moves:
        s = state()
        if s.home.get("focus", "").startswith(label_prefix):
            return s
        key(d)
        time.sleep(0.5)
    return state()


def to_round(name):
    s = close_panel()
    if s.home.get("page") != "home":
        key("back")
        settle(1)
    s = focus_home("round:", ["down"] * 8)
    for _ in range(8):
        f = s.home.get("focus", "")
        if f == "round:" + name:
            return s
        order = ["apps", "layout", "settings"]
        here = order.index(f[6:]) if f.startswith("round:") and f[6:] in order else -1
        key("left" if here > order.index(name) else "right")
        time.sleep(0.5)
        s = state()
    return s


def open_customize():
    s = state()
    if s.open and s.item("look:classic") is not None and s.depth == 1:
        return s
    s = to_round("layout")
    if not check("open", s.home.get("focus") == "round:layout", "focus reaches the Customize Home button", s.home.get("focus")):
        return s
    key("ok")
    settle()
    s = state()
    check("open", s.open and s.depth == 1 and s.item("look:classic") is not None, "OK opens Customize Home", s.panel)
    return s


def focus_item(tag):
    """Moves the panel focus to the item with this tag (UP/DOWN, the panel skips headers)."""
    for _ in range(40):
        s = state()
        tags = [it.get("tag") for it in s.items]
        if tag not in tags or s.focused() is None:
            return s
        here, there = tags.index(s.focused()), tags.index(tag)
        if here == there:
            return s
        key("down" if there > here else "up")
        time.sleep(0.35)
    return state()


def press_item(test, tag):
    """Focus + OK on a panel item; returns (before, after) states."""
    s0 = focus_item(tag)
    if not check(test, s0.focused() == tag, "focus reaches " + tag, s0.focused()):
        return s0, s0
    key("ok")
    settle()
    return s0, state()


def panel_kept(test, s0, s1, tag):
    """The panel stayed open on the same row and did not close and open again."""
    check(test, s1.open, tag + ": the panel stays open", s1.panel)
    check(test, s1.opens == s0.opens, tag + ": the panel did not close and open again", (s0.opens, s1.opens))
    check(test, s1.focused() == tag, tag + ": the focus stays on the row", s1.focused())


# ------------------------------------------------------------------ tests


def t_looks():
    t = "looks"
    open_customize()
    looks = [it["tag"] for it in state().items if it.get("tag", "").startswith("look:")]
    check(t, len(looks) >= 3, "the looks are listed", looks)
    order = [x for x in ("look:compact", "look:minimal", "look:classic") if x in looks]
    for tag in order:
        s0, s1 = press_item(t, tag)
        panel_kept(t, s0, s1, tag)
        check(t, s1.layout.get("look") == tag[5:], tag + ": stored", s1.layout.get("look"))
        check(t, s1.checked("look:") == [tag], tag + ": the check mark moved to it", s1.checked("look:"))
        for k in ("clock", "cont", "showcase", "weather"):
            it = s1.item(k)
            check(t, it is not None and it.get("on") == s1.layout.get(k), tag + ": switch " + k + " shows the stored value",
                  (it or {}).get("on"), )
        rows = s1.item("rows")
        check(t, rows is not None and rows.get("value") == s1.layout.get("rows"), tag + ": rows shows the stored value",
              ((rows or {}).get("value"), s1.layout.get("rows")))
        check(t, s1.plan.get("stored") == "true", tag + ": Home behind was built with the stored layout", s1.plan)
        b0, b1 = int(s0.home.get("binds", 0)), int(s1.home.get("binds", 0))
        check(t, b1 - b0 == 1, tag + ": the screen was built once (no flicker)", b1 - b0)
        check(t, s1.home.get("alpha") == "1.0", tag + ": Home faded fully back in", s1.home.get("alpha"))


def t_toggles():
    t = "toggles"
    open_customize()
    for tag in ("clock", "cont", "showcase", "weather"):
        for round_ in (1, 2):
            s0, s1 = press_item(t, tag)
            before, after = s0.layout.get(tag), s1.layout.get(tag)
            panel_kept(t, s0, s1, tag)
            check(t, before != after and after in ("true", "false"), tag + " #" + str(round_) + ": stored value flipped", (before, after))
            it = s1.item(tag)
            check(t, it is not None and it.get("on") == after, tag + " #" + str(round_) + ": the switch shows the stored value",
                  ((it or {}).get("on"), after))
            check(t, s1.plan.get("stored") == "true", tag + " #" + str(round_) + ": Home behind was built with the stored layout", s1.plan)
            look = s1.layout.get("look", "")
            checked = s1.checked("look:")
            check(t, checked == ([] if look == "custom" else ["look:" + look]), tag + " #" + str(round_) + ": look marks follow",
                  (look, checked))


def t_rows():
    t = "rows"
    open_customize()
    for i in range(3):
        s0, s1 = press_item(t, "rows")
        panel_kept(t, s0, s1, "rows")
        r0, r1 = s0.layout.get("rows"), s1.layout.get("rows")
        check(t, r0 != r1, "rows #" + str(i + 1) + ": stored count changed", (r0, r1))
        it = s1.item("rows")
        check(t, it is not None and it.get("value") == r1, "rows #" + str(i + 1) + ": the row shows the stored count", ((it or {}).get("value"), r1))


def t_theme():
    t = "theme"
    open_customize()
    orig = state().theme.get("mode", "").lower()
    for m in ("dark", "light", orig or "auto"):
        tag = "theme:" + m
        s0, s1 = press_item(t, tag)
        panel_kept(t, s0, s1, tag)
        check(t, s1.theme.get("mode", "").lower() == m, tag + ": stored", s1.theme.get("mode"))
        check(t, s1.checked("theme:") == [tag], tag + ": the check mark moved to it", s1.checked("theme:"))
        check(t, s1.page.get("bg") == s1.page.get("surface"), tag + ": the panel took the new colours", (s1.page.get("bg"), s1.page.get("surface")))


def t_memory():
    t = "memory"
    open_customize()
    s0, s1 = press_item(t, "memory")
    panel_kept(t, s0, s1, "memory")
    check(t, PKG + "/" in window() or ".HomeActivity" in window(), "Home stays in front", window())


def t_backup():
    t = "backup"
    open_customize()
    top = state()
    band = (int(top.page.get("clipTop", "0")), int(top.page.get("left", "0")))
    title0 = strip(*band)        # the title band with the list at its top
    s0, s1 = press_item(t, "backup")
    settle(1)
    s1 = state()
    check(t, s1.open and s1.depth == 2, "the backup page opens", s1.panel)
    check(t, s1.opens == s0.opens, "the panel did not close and open again", (s0.opens, s1.opens))
    save = [it["tag"] for it in s1.items if it.get("tag", "").startswith("bk:save:")]
    check(t, len(save) >= 1, "a place to save is offered", save)
    if save:
        a, b = press_item(t, save[-1])
        settle(1.5)
        b = state()
        check(t, b.open and b.depth == 2, "after saving the backup page stays", b.panel)
        check(t, b.opens == a.opens, "the panel did not close and open again", (a.opens, b.opens))
        check(t, b.item("bk:restore") is not None, "the saved backup is offered for restore", [i.get("tag") for i in b.items])
    key("back")
    settle(1)
    s2 = state()
    check(t, s2.open and s2.depth == 1 and s2.focused() == "backup", "BACK goes back to Customize Home on Backup", (s2.panel, s2.focused()))
    # HB live audit 2026-11-24: the rows came back scrolled to the top, Backup (the last row) under the screen edge
    bk = s2.item("backup") or {}
    check(t, bk.get("shown") == "true", "the focused Backup row is on screen after BACK", bk)
    clip, title = int(s2.page.get("clipTop", "-1")), int(s2.page.get("titleBottom", "0"))
    check(t, clip >= title, "the list window starts under the title", (clip, title))
    title1 = strip(*band)        # the same band with the list scrolled to its last row
    check(t, len(title0) > 0 and title0 == title1, "rows scrolled up are not drawn over the title", (band, len(title0)))
    up = [it.get("tag") for it in s2.items if it.get("shown") == "false"]
    check(t, len(up) >= 1, "the page is longer than the screen, so the check above means something", up)
    key("back")
    settle(1)
    s3 = state()
    check(t, not s3.open and s3.opens == s0.opens, "BACK closes the panel, it stays closed", s3.panel)
    check(t, s3.home.get("focus") == "round:layout", "focus back on the Customize Home button", s3.home.get("focus"))


def first_app():
    """Focus on the first app (not a folder) of the dock row above the bottom line."""
    s = close_panel()
    s = to_round("layout")
    s = focus_home("app:", ["up"] * 4)
    if s.home.get("focus", "").startswith("folder:"):
        s = focus_home("app:", ["right"] * 8)
    return s


def in_home():
    w = window()
    return ".HomeActivity" in w and PKG in w


def t_folders():
    t = "folders"
    s = first_app()
    f = s.home.get("focus", "")
    if not check(t, f.startswith("app:"), "focus reaches an app of the dock", f):
        return
    pkg = f[4:]
    key("menu")
    settle()
    s0 = state()
    check(t, s0.open and s0.item("app:folder") is not None, "MENU opens the app menu", s0.panel)
    a, b = press_item(t, "app:folder")
    check(t, b.depth == 2 and b.item("pick:new") is not None, "the folder picker opens", b.panel)
    a, b = press_item(t, "pick:new")
    check(t, b.depth == 3 and b.item("name:0") is not None, "the folder names open", b.panel)
    name = (b.item("name:0") or {}).get("text", "")
    a, s1 = press_item(t, "name:0")
    check(t, not s1.open and s1.opens == s0.opens, "new folder: the panel closes and stays closed", s1.panel)
    check(t, s1.home.get("page") == "home" and s1.home.get("folder") == "-", "new folder: no folder opened by the same press",
          (s1.home.get("page"), s1.home.get("folder")))
    check(t, in_home(), "new folder: no app started by the same press", window())
    folders = s1.layout.get("folders", "")
    check(t, ("=" + name) in folders and pkg not in s1.dock, "new folder stored with the app in it", (folders, s1.dock))
    fid = folders.split("=")[0] if folders else ""
    s1 = focus_home("folder:" + fid, ["left"] * 6 + ["right"] * 12)
    if not check(t, s1.home.get("focus") == "folder:" + fid, "focus reaches the folder", s1.home.get("focus")):
        return
    # rename
    key("menu")
    settle()
    s0 = state()
    a, b = press_item(t, "folder:rename")
    a, s2 = press_item(t, "name:1")
    new = (a.item("name:1") or {}).get("text", "")
    check(t, not s2.open and s2.opens == s0.opens, "rename: the panel closes and stays closed", s2.panel)
    check(t, s2.home.get("page") == "home" and s2.home.get("folder") == "-", "rename: no folder opened by the same press",
          (s2.home.get("page"), s2.home.get("folder")))
    check(t, (fid + "=" + new) in s2.layout.get("folders", ""), "rename stored", s2.layout.get("folders"))
    # move
    s2 = focus_home("folder:" + fid, ["left"] * 6 + ["right"] * 12)
    idx = s2.dock.index("folder:" + fid) if ("folder:" + fid) in s2.dock else -1
    key("menu")
    settle()
    s0 = state()
    a, s3 = press_item(t, "folder:move")
    check(t, not s3.open and s3.opens == s0.opens, "move: the panel closes and stays closed", s3.panel)
    check(t, s3.home.get("moving") != "-1", "move: the folder is picked up (the same press did not drop it)", s3.home.get("moving"))
    step = "right" if idx < len(s2.dock) - 1 else "left"
    key(step)
    settle(0.8)
    key("ok")
    settle()
    s4 = state()
    want = idx + (1 if step == "right" else -1)
    check(t, s4.home.get("moving") == "-1", "move: OK drops it", s4.home.get("moving"))
    got = s4.dock.index("folder:" + fid) if ("folder:" + fid) in s4.dock else -1
    check(t, got == want, "move: the folder moved one place", (idx, got, s4.dock))
    check(t, in_home() and s4.home.get("folder") == "-", "move: nothing opened by the drop", (window(), s4.home.get("folder")))
    # delete
    s4 = focus_home("folder:" + fid, ["left"] * 6 + ["right"] * 12)
    key("menu")
    settle()
    s0 = state()
    a, s5 = press_item(t, "folder:delete")
    check(t, not s5.open and s5.opens == s0.opens, "delete: the panel closes and stays closed", s5.panel)
    check(t, s5.layout.get("folders", "") == "" and pkg in s5.dock, "delete: the folder is gone, its app is back", (s5.layout.get("folders"), s5.dock))
    check(t, in_home() and s5.home.get("page") == "home", "delete: no app started by the same press", window())


def t_hidden():
    t = "hidden"
    s = first_app()
    f = s.home.get("focus", "")
    if not check(t, f.startswith("app:"), "focus reaches an app of the dock", f):
        return
    pkg = f[4:]
    s = open_customize()
    n0 = int((s.item("hidden") or {}).get("value", "0"))
    s = first_app()
    key("menu")
    settle()
    s0 = state()
    a, s1 = press_item(t, "app:hide")
    check(t, not s1.open and s1.opens == s0.opens, "hide: the panel closes and stays closed", s1.panel)
    check(t, pkg in s1.layout.get("hidden", "") and pkg not in s1.dock, "hide: stored, the app left the dock", (s1.layout.get("hidden"), s1.dock))
    check(t, in_home(), "hide: no app started by the same press", window())
    s = open_customize()
    it = s.item("hidden")
    check(t, it is not None and it.get("value") == str(n0 + 1), "Hidden apps counts it", ((it or {}).get("value"), n0))
    a, b = press_item(t, "hidden")
    check(t, b.depth == 2 and b.item("show:" + pkg) is not None, "the hidden apps page lists it", [i.get("tag") for i in b.items])
    a, c = press_item(t, "show:" + pkg)
    check(t, pkg not in c.layout.get("hidden", "") and pkg in c.dock, "show again: stored, back in the dock", (c.layout.get("hidden"), c.dock))
    check(t, c.opens == a.opens, "show again: the panel did not open again", (a.opens, c.opens))
    check(t, in_home(), "show again: no app started by the same press", window())


def t_longpress():
    t = "longpress"
    s = first_app()
    f = s.home.get("focus", "")
    if not check(t, f.startswith("app:"), "focus reaches an app of the dock", f):
        return
    o0 = s.opens
    hold("ok", 1000)     # `input keyevent --longpress` holds only ~0.5 s, less than Home's 600 ms: a real HID hold
    settle()
    s1 = state()
    check(t, s1.open and s1.opens == o0 + 1 and s1.item("app:open") is not None, "a held OK opens the app menu once", s1.panel)
    check(t, in_home(), "the held OK started no app", window())
    close_panel()


def t_network():
    t = "network"
    s = state()
    net = int(s.status.get("net", "-1"))
    online = "VALIDATED" in sh("dumpsys connectivity | grep -m3 -E 'NetworkAgentInfo.*VALIDATED'")
    check(t, net >= 0, "the corner shows a network mark", net)
    check(t, (net > 0) == online, "the mark matches the connection (online " + str(online) + ")", net)


def t_intent():
    """The projector menu's row Backup of the menus and Home opens Customize Home on its backup page."""
    t = "intent"
    close_panel()
    s0 = state()
    sh("am start -a org.lumen.home.action.CUSTOMIZE --es page backup -n " + ACT)
    settle(2.5)
    s1 = state()
    check(t, s1.open and s1.depth == 2 and any(i.get("tag", "").startswith("bk:") for i in s1.items),
          "the backup page opens over Customize Home", (s1.panel, [i.get("tag") for i in s1.items]))
    check(t, s1.opens == s0.opens + 1, "the panel opened once", (s0.opens, s1.opens))
    key("back")
    settle(1)
    s2 = state()
    check(t, s2.open and s2.depth == 1 and s2.item("look:classic") is not None, "BACK shows Customize Home", s2.panel)
    close_panel()


# ------------------------------------------------------------------ a press that ends somewhere else
# Android hands a window the release of a key that went down in another window (InputDispatcher lets such a release
# through). The user saw a tile start after a settings backup and after a restore: the press went down on a confirm
# row, on the projector menu's backup row or in a Home that a restore built anew, and came up on Home.


class Remote:
    """A virtual HID remote that stays plugged in, so the press and the release are two steps (`input keyevent`
    sends both at once): a key goes down in one window and comes up in another."""

    def __init__(self):
        self.p = subprocess.Popen([ADB, "-s", SERIAL, "shell", "hid", "-"], stdin=subprocess.PIPE,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, text=True)
        self.send({"id": 2, "command": "register", "name": "Lumen test remote 2", "vid": 0x18d1, "pid": 0x7a12,
                   "bus": "usb", "descriptor": HID_DESC})
        time.sleep(2)

    def send(self, o):
        self.p.stdin.write(json.dumps(o) + "\n")
        self.p.stdin.flush()

    def down(self, name):
        u = HID_USAGE[name]
        self.send({"id": 2, "command": "report", "report": [u & 0xFF, u >> 8]})

    def up(self):
        self.send({"id": 2, "command": "report", "report": [0, 0]})

    def tap(self, name):
        self.down(name)
        time.sleep(0.08)
        self.up()

    def close(self):
        try:
            self.p.stdin.close()
            self.p.wait(10)
        except Exception:
            self.p.kill()


_REMOTE = []


def remote():
    if not _REMOTE:
        _REMOTE.append(Remote())
    return _REMOTE[0]


SETTINGS_APP = "com.android.tv.settings"


def creates():
    """HomeActivity instances created so far (event log): a Home built anew adds one."""
    out = sh("logcat -d -b events | grep wm_on_create_called | grep -c org.lumen.home.HomeActivity")
    return int(out.strip() or "0")


def wait(cond, secs=8.0):
    end = time.time() + secs
    while time.time() < end:
        s = state()
        if cond(s):
            return s
        time.sleep(0.4)
    return state()


def other_app():
    """An app in front of Home (TV Settings stands in for the projector menu and its All settings editor)."""
    sh("am start -W -a android.settings.SETTINGS")
    for _ in range(25):
        if SETTINGS_APP in window() and SETTINGS_APP in input_focus():
            return True
        time.sleep(0.4)
    return False


def back_in_home(secs=8.0):
    end = time.time() + secs
    while time.time() < end:
        if in_home() and PKG in input_focus():
            return True
        time.sleep(0.3)
    return False


def overlay(s, name):
    """The film card or the source list open (dump line lumen.overlay); None on a Home without that line."""
    v = s.overlay.get(name)
    return None if v is None else v == "true"


def press(how, between=None):
    """OK on what has the focus: tap (input keyevent), hid (a remote tap), hold (a remote held 1.3 s), repeat (three
    taps), split (down, then `between`, then up)."""
    if how == "tap":
        key("ok")
    elif how == "repeat":
        key("ok", "ok", "ok")
    elif how == "hid":
        remote().tap("ok")
    elif how == "hold":
        remote().down("ok")
        time.sleep(1.3)
        remote().up()
    else:
        remote().down("ok")
        landed()
        between()
        remote().up()


def landed():
    """A remote press is delivered before the next step changes the window in front: the report goes to the device by
    one adb channel and `am start` or `am force-stop` by another, and a window switch that overtakes the report makes
    the press go down in the new window (then it is that window's own press: Home opens a held tile's menu as the
    stock launcher does). Android starts its key repeats 400 ms after the press, so 0.25 s is well before them."""
    time.sleep(0.25)


def recover():
    """After a check that saw an app start (a leaked press), Home comes back for the next case."""
    if not in_home():
        start_home()
    close_panel()


def open_backup(t):
    s = open_customize()
    if s.depth != 2 or s.item("bk:reset") is None:
        press_item(t, "backup")
        s = wait(lambda x: x.depth == 2 and x.item("bk:reset") is not None)
    check(t, s.open and s.depth == 2 and s.item("bk:reset") is not None, "the backup page is open", s.panel)
    return s


def first_action(s, prefix="bk:"):
    for it in s.items:
        if it.get("tag", "").startswith(prefix):
            return it.get("tag")
    return None


def toast_shown():
    return "Toast" in sh("dumpsys window windows | grep -E 'Window\\{.*Toast' | head -3")


def bk_kept(t, what, a, b, tag):
    check(t, b.open and b.depth == a.depth, what + ": the backup page stays", b.panel)
    check(t, b.opens == a.opens, what + ": the panel did not close and open again", (a.opens, b.opens))
    check(t, b.focused() == tag, what + ": the focus stays on " + tag, b.focused())
    check(t, b.home.get("focus") == a.home.get("focus"), what + ": Home's focus under the panel did not move",
          (a.home.get("focus"), b.home.get("focus")))
    check(t, in_home(), what + ": no app started", window())


def t_bk_save():
    """Save on every place, by a tap, a remote tap, a held OK and three quick presses: the page stays on its row."""
    t = "bk_save"
    s = open_backup(t)
    saves = [it["tag"] for it in s.items if it.get("tag", "").startswith("bk:save:")]
    check(t, len(saves) >= 1, "a place to save is offered", saves)
    for tag in saves:
        for how in ("tap", "hid", "hold", "repeat"):
            a = focus_item(tag)
            press(how)
            shown = any(toast_shown() or time.sleep(0.3) for _ in range(6))
            settle(1.5)
            b = state()
            bk_kept(t, tag + " " + how, a, b, tag)
            if how == "tap":
                check(t, shown, tag + ": a toast says it was saved", shown)
            check(t, b.item("bk:restore") is not None, tag + " " + how + ": the saved file is offered", [i.get("tag") for i in b.items])
    # a place that cannot be written: the failure toast, the page stays
    sh("rm -f /sdcard/Download/lumen-settings.json; mkdir -p /sdcard/Download/lumen-settings.json")
    try:
        b0 = open_backup(t)
        tag = "bk:save:local"
        a = focus_item(tag)
        press("tap")
        shown = any(toast_shown() or time.sleep(0.3) for _ in range(6))
        settle(1.5)
        b = state()
        bk_kept(t, "failed save", a, b, tag)
        check(t, shown, "failed save: a toast says so", shown)
    finally:
        sh("rmdir /sdcard/Download/lumen-settings.json")
    close_panel()


def confirm_row(t, row):
    """The backup page with a saved file, the confirm page of `row` open on Cancel."""
    s = open_backup(t)
    if s.item("bk:restore") is None:
        press_item(t, "bk:save:local")
        s = wait(lambda x: x.item("bk:restore") is not None)
    a, b = press_item(t, row)
    b = wait(lambda x: x.depth == 3)
    check(t, b.depth == 3 and b.focused() == "confirm:cancel", row + ": the confirm page opens on Cancel", (b.panel, b.focused()))
    return b


def t_bk_confirm():
    """Restore and Reset: Cancel goes back to the row, Go closes the panel and the same press starts nothing on Home,
    by a tap, a remote tap, a held OK, and a press whose release reaches a Home built anew (the config changes
    while OK is down, as when a restored setting does it)."""
    t = "bk_confirm"
    for row in ("bk:restore", "bk:reset"):
        b = confirm_row(t, row)
        a, c = press_item(t, "confirm:cancel")
        c = wait(lambda x: x.depth == 2)
        check(t, c.open and c.depth == 2 and c.focused() == row, row + " cancel: back on the backup page on its row", (c.panel, c.focused()))
        check(t, c.opens == a.opens and in_home(), row + " cancel: no reopen, no app", (a.opens, c.opens, window()))
        for how in ("tap", "hid", "hold", "rebuilt"):
            b = confirm_row(t, row)
            a = focus_item("confirm:go")
            n0 = creates()
            seen = []

            def rebuild():
                time.sleep(0.6)
                sh("settings put system font_scale 1.15")
                s1 = wait(lambda x: creates() > n0 and x.home.get("ready") == "true", 15)
                seen.append(s1)
                sh("settings put system font_scale 1.0")
                s2 = wait(lambda x: creates() > n0 + 1 and x.home.get("ready") == "true", 15)
                time.sleep(1.5)
                seen.append(state())

            press(how if how != "rebuilt" else "split", rebuild)
            shown = any(toast_shown() or time.sleep(0.3) for _ in range(6))
            settle(2)
            c = state()
            what = row + " " + how
            check(t, not c.open, what + ": the panel is closed", c.panel)
            if how == "rebuilt":
                check(t, creates() >= n0 + 2, what + ": Home was built anew while OK was down", creates() - n0)
                pre = seen[-1] if seen else c
                check(t, c.opens == 0, what + ": the new Home opened no panel", c.opens)
                check(t, c.home.get("focus") == pre.home.get("focus"), what + ": the release moved or pressed nothing",
                      (pre.home.get("focus"), c.home.get("focus")))
            else:
                check(t, c.opens == a.opens, what + ": the panel did not open again", (a.opens, c.opens))
                check(t, c.home.get("focus") == a.home.get("focus"), what + ": the focus is back on the Customize Home button",
                      (a.home.get("focus"), c.home.get("focus")))
                check(t, shown, what + ": a toast says it is done", shown)
            check(t, c.home.get("page") == "home" and c.home.get("folder") == "-", what + ": no folder or page opened", c.home)
            check(t, in_home(), what + ": no app started", window())
            check(t, c.plan.get("stored") == "true", what + ": Home shows the stored layout", c.plan)
            recover()


def t_bk_from_app():
    """The projector menu's row (All settings editor) closes its window on OK down and sends Home's backup intent:
    the release lands on Home. The backup page opens once on its first row, nothing starts, a held OK clicks no row."""
    t = "bk_from_app"
    for how in ("tap", "hold"):
        close_panel()
        s0 = first_app()
        if not check(t, other_app(), how + ": an app is in front of Home", window()):
            return

        def send():
            sh("am start -a org.lumen.home.action.CUSTOMIZE --es page backup -n " + ACT)
            back_in_home()
            wait(lambda x: x.depth == 2 and x.item("bk:reset") is not None)
            time.sleep(1.3 if how == "hold" else 0.1)

        press("split", send)
        settle(2)
        s1 = state()
        check(t, s1.open and s1.depth == 2 and s1.item("bk:reset") is not None, how + ": the backup page is open", s1.panel)
        check(t, s1.opens == s0.opens + 1, how + ": the panel opened once", (s0.opens, s1.opens))
        check(t, s1.focused() == first_action(s1), how + ": the focus is on the page's first row (no row was pressed)", s1.focused())
        check(t, in_home(), how + ": no app started", window())
        recover()


def t_back_to_home():
    """An app in front closes on the press (OK or BACK down) and Home comes back with the release: Home's focus stays,
    no panel opens, no app starts, the page does not change."""
    t = "back_to_home"
    for name, back in (("ok", lambda: sh("am force-stop " + SETTINGS_APP)),
                       ("ok intent", lambda: sh("am start -n " + ACT)),
                       ("back", lambda: sh("am force-stop " + SETTINGS_APP))):
        close_panel()
        first_app()
        if not check(t, other_app(), name + ": an app is in front of Home", window()):
            return
        pre = []

        def between():
            back()
            back_in_home()
            time.sleep(0.8)
            pre.append(state())

        k = name.split()[0]
        n0 = state().keys.get("okdowns")
        remote().down(k)
        landed()
        between()
        remote().up()
        settle(1.5)
        s1 = state()
        p = pre[0] if pre else s1
        check(t, in_home(), name + ": no app started", window())
        if k == "ok" and n0 is not None and s1.keys.get("okdowns") != n0:
            # the window switch beat the report: the press went down on Home itself, and a held OK on a tile opens
            # its menu as on the stock launcher. Not the case under test: no panel check for this round.
            print("NOTE back_to_home: %s: the press went down on Home (okdowns %s -> %s), its own long press" % (
                name, n0, s1.keys.get("okdowns")), flush=True)
        else:
            check(t, not s1.open and s1.opens == p.opens, name + ": no panel opened", (p.opens, s1.opens))
        check(t, s1.home.get("focus") == p.home.get("focus") and s1.home.get("page") == p.home.get("page"),
              name + ": the release moved or pressed nothing", ((p.home.get("page"), p.home.get("focus")),
                                                                 (s1.home.get("page"), s1.home.get("focus"))))
        recover()


def t_source_back():
    """The source list stays open under an app that closes on the press: the release does not pick its row."""
    t = "source_back"
    close_panel()
    # the emulator has no TV inputs and its input key is a global key (com.google.android.tv GlobalKeyReceiver), so
    # the list opens from the chip of a USB drive (a virtual disk) by MENU, as on a unit with one drive plugged in
    if "public:" not in sh("sm list-volumes public"):
        sh("sm set-virtual-disk true")
        time.sleep(3)
        disk = sh("sm list-disks").split()
        if disk:
            sh("sm partition " + disk[0] + " public", timeout=90)
        wait(lambda x: "mounted" in sh("sm list-volumes public"), 20)
    try:
        start_home()
        s = focus_home("chip:", ["down"] * 5)
        if overlay(s, "source") is None:
            print("SKIP source_back: this Home does not show its overlays in dumpsys")
            return
        if not check(t, s.home.get("focus", "").startswith("chip:"), "a USB drive chip is on the bottom line", s.home):
            return
        key("menu")
        s = wait(lambda x: overlay(x, "source") is True, 4)
        if not check(t, overlay(s, "source"), "MENU on a chip opens the source list", s.overlay):
            return
        source_closes(t)
    finally:
        sh("sm set-virtual-disk false")
        time.sleep(2)
        start_home()


def source_closes(t):
    other_app()

    def between():
        sh("am force-stop " + SETTINGS_APP)
        back_in_home()
        time.sleep(0.8)

    press("split", between)
    settle(1.5)
    s1 = state()
    check(t, overlay(s1, "source") is True, "the source list is still open (the release picked nothing)", s1.overlay)
    check(t, in_home(), "no app started", window())
    key("back")
    settle(1)
    check(t, overlay(state(), "source") is False, "BACK closes the source list", state().overlay)


# ------------------------------------------------------------------ Continue watching and the way into Home
# The owner (Z9X 1.1.0-beta2): Delete in a Continue card's menu also started the film (a second OK), Home looked as if
# it had restarted, and every way back into Home put the focus on the hero, not on the first app. The fake app
# (test/ui/fakewn) puts Watch Next rows into TvProvider and is the player its cards open.

FAKE = "org.lumen.home.fakewn"


def cont_ids(s):
    return [x for x in s.cont.get("ids", "").split(",") if x]


def seed(n):
    """n Continue watching cards of the fake app, newest first; Home shows them once its reload ran."""
    sh("am start -W -n %s/.FakeWnSeed --ei n %d" % (FAKE, n))
    return wait(lambda x: len(cont_ids(x)) == n or (n <= 2 and x.plan.get("cont") == "false"), 10)


def pid():
    return sh("pidof " + PKG).strip()


def playing():
    return FAKE + "/" in window() or ".FakeWnPlayer" in window()


def to_cont(i):
    """Focus on Continue card i (the row sits between the hero and the apps)."""
    s = focus_home("continue:", ["down", "up", "up", "up", "up"])
    for _ in range(12):
        f = s.home.get("focus", "")
        if f == "continue:%d" % i or not f.startswith("continue:"):
            return s
        key("right" if int(f.split(":")[1]) < i else "left")
        time.sleep(0.5)
        s = state()
    return s


def apps_row_first(s):
    """The focus labels of the first item of the first row of apps (dock order: an app or a folder)."""
    return ("app:" + s.dock[0], s.dock[0]) if s.dock else ()


def t_cont_delete():
    """Hold OK on a Continue card, Delete with a tap and with a remote press: the card goes, nothing plays, Home is not
    created or built anew, the focus moves to the neighbour card (the next one, the previous one after the last)."""
    t = "cont_delete"
    if FAKE not in sh("pm list packages " + FAKE):
        print("SKIP cont_delete: the fake Continue app is not installed (test/ui/fakewn/build_fake.sh)")
        return
    for how, n, at in (("tap", 4, 1), ("hid", 4, 1), ("hid", 4, 3), ("hid", 3, 0)):
        name = "%s, card %d of %d" % (how, at + 1, n)
        try:
            start_home()
        except SystemExit as e:
            check(t, False, name + ": Home starts", e)
            time.sleep(61)  # three crashes in a minute put Home in its safe mode; the counter forgets after one
            start_home()
        s = seed(n)
        if not check(t, len(cont_ids(s)) == n, name + ": the Continue row shows the cards", s.cont):
            return
        s = to_cont(at)
        if not check(t, s.home.get("focus") == "continue:%d" % at, name + ": focus on the card", s.home.get("focus")):
            continue
        ids = cont_ids(s)
        hold("ok", 1000)
        settle()
        s1 = state()
        if not check(t, s1.open and s1.item("cont:remove") is not None, name + ": a held OK opens the card menu", s1.panel):
            recover()
            continue
        focus_item("cont:remove")
        c0, b0, p0 = creates(), int(state().home.get("binds", "0")), pid()
        press(how)
        settle(3)
        s2 = state()
        want = ids[:at] + ids[at + 1:]
        nb = min(at, len(want) - 1)
        check(t, not playing(), name + ": Delete started no playback", window())
        died = pid() != p0
        check(t, not died, name + ": Home's process lived on (no crash)", (p0, pid()))
        check(t, in_home(), name + ": Home is in front", window())
        check(t, creates() == c0, name + ": Home was not created anew", (c0, creates()))
        check(t, not s2.open, name + ": the menu closed", s2.panel)
        check(t, cont_ids(s2) == want, name + ": the card left the row, the others kept their order", (ids, cont_ids(s2)))
        check(t, int(s2.home.get("binds", "0")) == b0, name + ": the row changed in place (the screen was not built anew)",
              (b0, s2.home.get("binds")))
        check(t, s2.home.get("focus") == "continue:%d" % nb, name + ": the focus is on the neighbour card",
              (s2.home.get("focus"), "continue:%d" % nb))
        if died:
            time.sleep(61)
            continue
        if (how, n, at) == ("hid", 4, 3):
            # Home reloads (back from a full screen app: TV Settings is a side panel, Home would not stop) before the
            # provider took the change: the card stays out
            sh("am start -W -n %s/.FakeWnPlayer" % FAKE)
            settle(2)
            key("back")
            back_in_home(10)
            settle(3)
            s5 = state()
            check(t, cont_ids(s5) == want, name + ": a reload before the provider's change keeps the card out",
                  (want, cont_ids(s5)))
            s2 = s5
        # the provider's change (on a projector Home clears the browsable flag itself): the same cards, nothing changes
        sh("am broadcast -n %s/.FakeWnDrop --el id %s" % (FAKE, ids[at].split(":")[-1]))
        settle(3)
        s3 = state()
        check(t, cont_ids(s3) == want and s3.home.get("binds") == s2.home.get("binds")
              and s3.home.get("focus") == s2.home.get("focus"), name + ": the provider's change rebuilds and moves nothing",
              ((cont_ids(s2), s2.home.get("binds"), s2.home.get("focus")), (cont_ids(s3), s3.home.get("binds"), s3.home.get("focus"))))
        if len(want) <= 2:
            key("home")
            settle(2.5)
            s4 = state()
            check(t, s4.plan.get("cont") == "false" and s4.home.get("focus") in apps_row_first(s4),
                  name + ": HOME lays the two cards out in the hero, the focus on the first app", (s4.plan, s4.home.get("focus")))
        recover()


def t_entry():
    """Every way into Home starts on the first app of the apps row: HOME on Home, HOME from an app, BACK from an app a
    card started, a cold start by the HOME intent (boot). Hero with buttons (two Continue cards) and a Continue row."""
    t = "entry"
    fake = FAKE in sh("pm list packages " + FAKE)
    for n in ((2, 4) if fake else (0,)):
        if fake:
            start_home()
            seed(n)
        cases = [("HOME on Home", lambda: key("home")),
                 ("HOME from an app", lambda: (other_app(), key("home"))),
                 ("cold start", lambda: (sh("am force-stop " + PKG),
                                         sh("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")))]
        if fake and n == 2:
            # no saved screen yet (a first boot): Home has no data when the HOME intent comes and places the focus
            # once the first data is bound
            cases.append(("first start", lambda: (sh("pm clear " + PKG), regrant(),
                                                  sh("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"))))
        if fake:
            cases.append(("BACK from a film", lambda: (to_cont(0) if n > 2 else focus_home("hero:", ["up"] * 5),
                                                       key("ok"), wait_for(playing), settle(2), key("back"))))
        for name, go in cases:
            name = "%d cards, %s" % (n, name)
            close_panel()
            s = focus_home("round:", ["down"] * 6)    # the focus away from the apps first
            go()
            back_in_home(10)
            s1 = wait(lambda x: x.home.get("ready") == "true", 10)
            settle(2)
            s1 = state()
            check(t, in_home(), name + ": Home is in front", window())
            check(t, s1.home.get("page") == "home" and s1.home.get("focus") in apps_row_first(s1),
                  name + ": focus on the first app of the apps row", (s1.home.get("focus"), apps_row_first(s1)))
            recover()


def regrant():
    """What test/ui/run.sh grants, again after pm clear."""
    for p in ("android.permission.READ_TV_LISTINGS", "android.permission.WRITE_SECURE_SETTINGS"):
        sh("pm grant %s %s" % (PKG, p))
    sh("appops set %s MANAGE_EXTERNAL_STORAGE allow" % PKG)
    sh("cmd role add-role-holder android.app.role.HOME %s 0" % PKG)


def wait_for(cond, secs=8.0):
    end = time.time() + secs
    while time.time() < end and not cond():
        time.sleep(0.3)
    return cond()


TESTS = [("looks", t_looks), ("toggles", t_toggles), ("rows", t_rows), ("theme", t_theme), ("memory", t_memory),
         ("backup", t_backup), ("folders", t_folders), ("hidden", t_hidden), ("longpress", t_longpress), ("intent", t_intent),
         ("network", t_network), ("bk_save", t_bk_save), ("bk_confirm", t_bk_confirm), ("bk_from_app", t_bk_from_app),
         ("back_to_home", t_back_to_home), ("source_back", t_source_back), ("cont_delete", t_cont_delete),
         ("entry", t_entry)]


def main():
    lay0 = sh("settings get secure lumen_home_layout").strip()
    theme0 = sh("settings get secure lumen_ui_theme").strip()
    try:
        start_home()
        for name, fn in TESTS:
            if ONLY and name not in ONLY:
                continue
            print("== " + name, flush=True)
            try:
                fn()
            except Exception as e:  # a broken step fails its test, the next test starts clean
                check(name, False, "ran without error", repr(e))
            if not in_home():
                check(name, False, "Home is still in front after the test", window())
                start_home()
    finally:
        for r in _REMOTE:
            r.close()
        sh("settings put system font_scale 1.0")
        close_panel()
        for k, v in (("lumen_home_layout", lay0), ("lumen_ui_theme", theme0)):
            if v in ("", "null"):
                sh("settings delete secure " + k)
            else:
                sh("settings put secure " + k + " '" + v.replace("'", "'\\''") + "'")
    bad = [r for r in RESULTS if not r[1]]
    print("SUMMARY home ui: %d checks, %d failed%s" % (len(RESULTS), len(bad),
          "" if not bad else " (" + ", ".join(sorted(set(r[0] for r in bad))) + ")"))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
