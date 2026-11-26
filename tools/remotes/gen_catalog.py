#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Lumen OS: generates the remote catalog and the key table of the shared remote module from the remote tables.

  python3 tools/remotes/gen_catalog.py            write RemoteCatalog.java and keys/KeyTable.java
  python3 tools/remotes/gen_catalog.py --check    exit 1 when a committed file differs from the tables

Input: devices/_remotes/remotes.tsv (one row per XGIMI remote and remote-like input device, README.md next to it),
devices/_remotes/keys.tsv (the shortcut keys of every remote and the keyboard keys a user may give an app) and
devices/_remotes/fixed_keys.tsv (keys never given an app) and devices/_remotes/key_actions.tsv (projector actions a key
can do instead of opening an app). The Java files are data only; the rules that read them live
in RemoteMatch and keys/KeyRules. Never edit a generated file by hand: edit the table and run this script.
"""
import os
import re
import sys

sys.dont_write_bytecode = True
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TABLE = os.path.join(ROOT, "devices", "_remotes", "remotes.tsv")
OUT = os.path.join(ROOT, "apps", "core", "lumen-remote", "src", "org", "lumen", "remote", "RemoteCatalog.java")
KEYS = os.path.join(ROOT, "devices", "_remotes", "keys.tsv")
FIXED = os.path.join(ROOT, "devices", "_remotes", "fixed_keys.tsv")
KEYS_OUT = os.path.join(ROOT, "apps", "core", "lumen-remote", "src", "org", "lumen", "remote", "keys", "KeyTable.java")
ACTIONS = os.path.join(ROOT, "devices", "_remotes", "key_actions.tsv")
ACTION_COLUMNS = ["id", "keycode", "intent", "feature", "name", "hdmi", "icon", "stock", "note"]
KEY_COLUMNS = ["remote", "scancode", "stock_label", "stock_action", "keycode", "bind", "unbound", "name", "note"]
FIXED_COLUMNS = ["keycode", "group", "why"]
UNBOUND = {"host": "HOST", "all_apps": "ALL_APPS", "pick": "PICK", "signal": "SIGNAL"}
FIXED_GROUPS = ("control", "gear", "mic", "focus", "power", "volume")
# Android KeyEvent.KEYCODE_* values of every key the tables name (API 34; test/keys/run.sh compares them with the
# platform headers apps/sdk/framework-full-headers.jar)
KEYCODES = {
    "HOME": 3, "BACK": 4, "DPAD_UP": 19, "DPAD_DOWN": 20, "DPAD_LEFT": 21, "DPAD_RIGHT": 22, "DPAD_CENTER": 23,
    "VOLUME_UP": 24, "VOLUME_DOWN": 25, "POWER": 26, "EXPLORER": 64, "ENVELOPE": 65, "ENTER": 66, "FOCUS": 80,
    "MENU": 82, "SEARCH": 84, "MUTE": 91, "ESCAPE": 111, "F1": 131, "F2": 132, "F3": 133, "F4": 134, "F5": 135,
    "F6": 136, "F7": 137, "F8": 138, "F9": 139, "F10": 140, "F11": 141, "F12": 142, "NUMPAD_ENTER": 160,
    "VOLUME_MUTE": 164, "BOOKMARK": 174, "SETTINGS": 176, "TV_POWER": 177, "TV_INPUT": 178, "STB_POWER": 179,
    "CONTACTS": 207, "CALENDAR": 208, "MUSIC": 209, "CALCULATOR": 210, "ASSIST": 219, "SLEEP": 223, "WAKEUP": 224,
    "VOICE_ASSIST": 231, "TV_RADIO_SERVICE": 232, "TV_TERRESTRIAL_ANALOG": 235, "TV_TERRESTRIAL_DIGITAL": 236, "TV_SATELLITE": 237,
    "TV_SATELLITE_BS": 238, "TV_SATELLITE_CS": 239, "TV_SATELLITE_SERVICE": 240, "TV_NETWORK": 241,
    "TV_INPUT_HDMI_1": 243, "TV_INPUT_HDMI_2": 244, "TV_INPUT_HDMI_3": 245, "TV_AUDIO_DESCRIPTION": 252, "TV_CONTENTS_MENU": 256,
    "TV_MEDIA_CONTEXT_MENU": 257, "MEDIA_STEP_FORWARD": 274, "MEDIA_STEP_BACKWARD": 275, "SOFT_SLEEP": 276,
    "MACRO_1": 313, "MACRO_2": 314, "MACRO_3": 315, "MACRO_4": 316,
}
COLUMNS = ["id", "kind", "names", "vid_pid", "input", "combo", "voice", "battery", "art", "stock", "lumen_kl", "evidence"]
KINDS = {"ble_remote": "BLE_REMOTE", "ble_music": "BLE_MUSIC", "usb_dongle": "USB_DONGLE", "ir": "IR",
         "keypad": "KEYPAD", "generic": "GENERIC"}


def read_table(path=TABLE):
    rows = []
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            cells = line.split("\t")
            if cells[0] == "id":
                if cells != COLUMNS:
                    raise SystemExit("%s:%d: columns %s, expected %s" % (path, n, cells, COLUMNS))
                continue
            if len(cells) != len(COLUMNS):
                raise SystemExit("%s:%d: %d cells, expected %d" % (path, n, len(cells), len(COLUMNS)))
            r = dict(zip(COLUMNS, cells))
            if r["kind"] not in KINDS:
                raise SystemExit("%s:%d: unknown kind %r" % (path, n, r["kind"]))
            if r["vid_pid"] != "-":
                v, _, p = r["vid_pid"].partition(":")
                int(v, 16), int(p, 16)
            if r["lumen_kl"] not in ("0", "1"):
                raise SystemExit("%s:%d: lumen_kl must be 0 or 1" % (path, n))
            if any(x["id"] == r["id"] for x in rows):
                raise SystemExit("%s:%d: id %s twice" % (path, n, r["id"]))
            rows.append(r)
    return rows


def read_tsv(path, columns):
    rows = []
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            cells = line.split("\t")
            if cells[0] == columns[0]:
                if cells != columns:
                    raise SystemExit("%s:%d: columns %s, expected %s" % (path, n, cells, columns))
                continue
            if len(cells) != len(columns):
                raise SystemExit("%s:%d: %d cells, expected %d" % (path, n, len(cells), len(columns)))
            r = dict(zip(columns, cells))
            r["line"] = n
            rows.append(r)
    return rows


def read_fixed(path=FIXED):
    rows = read_tsv(path, FIXED_COLUMNS)
    seen = set()
    for r in rows:
        where = "%s:%d" % (path, r["line"])
        if r["keycode"] not in KEYCODES:
            raise SystemExit("%s: unknown key code %s (add it to KEYCODES)" % (where, r["keycode"]))
        if r["group"] not in FIXED_GROUPS:
            raise SystemExit("%s: unknown group %s" % (where, r["group"]))
        if r["keycode"] in seen:
            raise SystemExit("%s: %s twice" % (where, r["keycode"]))
        seen.add(r["keycode"])
    return rows


def read_keys(path=KEYS, remotes=None, fixed=None):
    """The key rows, checked: known remote and key code, one meaning per key code, no fixed key offered."""
    ids = {r["id"] for r in (remotes if remotes is not None else read_table())}
    fixed = {r["keycode"] for r in (fixed if fixed is not None else read_fixed())}
    rows = read_tsv(path, KEY_COLUMNS)
    seen, meaning = set(), {}
    for r in rows:
        where = "%s:%d" % (path, r["line"])
        if r["remote"] != "keyboard" and r["remote"] not in ids:
            raise SystemExit("%s: remote %s is not a remotes.tsv row" % (where, r["remote"]))
        if (r["remote"] == "keyboard") != (r["scancode"] == "-"):
            raise SystemExit("%s: a scancode belongs to remote rows only" % where)
        if r["scancode"] != "-":
            int(r["scancode"])
        key = (r["remote"], r["scancode"] if r["scancode"] != "-" else r["keycode"])
        if key in seen:
            raise SystemExit("%s: %s %s twice" % (where, key[0], key[1]))
        seen.add(key)
        if r["keycode"] != "-" and r["keycode"] not in KEYCODES:
            raise SystemExit("%s: unknown key code %s (add it to KEYCODES)" % (where, r["keycode"]))
        if r["bind"] == "app":
            if r["keycode"] == "-":
                raise SystemExit("%s: a key Lumen does not map cannot be given an app" % where)
            if r["keycode"] in fixed:
                raise SystemExit("%s: %s is a fixed key (fixed_keys.tsv)" % (where, r["keycode"]))
            if r["unbound"] not in UNBOUND or r["name"] == "-":
                raise SystemExit("%s: bind=app needs unbound (%s) and a name" % (where, "/".join(UNBOUND)))
            m = (r["unbound"], r["name"], r["remote"] == "keyboard")
            if meaning.setdefault(r["keycode"], m) != m:
                raise SystemExit("%s: %s means %s here and %s in another row" % (where, r["keycode"], m,
                                                                                 meaning[r["keycode"]]))
        elif r["bind"] == "no":
            if r["unbound"] != "-" or r["name"] != "-":
                raise SystemExit("%s: bind=no takes unbound - and name -" % where)
        else:
            raise SystemExit("%s: bind must be app or no" % where)
    return rows


def read_actions(path=ACTIONS, keys=None):
    """The action rows, checked: a known key code that no user may give an app (no loop) or an intent of the projector
    app, unique id, a name, an icon, a feature id of the device profile or none."""
    offered = {r["keycode"] for r in (keys if keys is not None else read_keys()) if r["bind"] == "app"}
    rows = read_tsv(path, ACTION_COLUMNS)
    seen = set()
    for r in rows:
        where = "%s:%d" % (path, r["line"])
        if (r["keycode"] == "-") == (r["intent"] == "-"):
            raise SystemExit("%s: an action presses a key or sends an intent, exactly one of them" % where)
        if r["intent"] != "-" and not re.fullmatch(r"org\.lumen\.[a-z0-9_.]+\.action\.[A-Z0-9_]+", r["intent"]):
            raise SystemExit("%s: intent %s is not an org.lumen.<app>.action.<NAME> action" % (where, r["intent"]))
        if r["feature"] != "-" and not re.fullmatch(r"[a-z0-9_]+(\.[a-z0-9_]+)*", r["feature"]):
            raise SystemExit("%s: feature %s is not a device profile feature id" % (where, r["feature"]))
        if r["keycode"] != "-" and r["keycode"] not in KEYCODES:
            raise SystemExit("%s: unknown key code %s (add it to KEYCODES)" % (where, r["keycode"]))
        if r["keycode"] in offered:
            raise SystemExit("%s: %s is a key a user may give an app: pressing it could loop" % (where, r["keycode"]))
        if not r["id"].replace("_", "").isalnum() or r["id"] in seen:
            raise SystemExit("%s: id %r empty, odd or twice" % (where, r["id"]))
        seen.add(r["id"])
        if r["name"] in ("", "-") or not r["hdmi"].isdigit():
            raise SystemExit("%s: needs a name and hdmi 0..3" % where)
        if not r["icon"].replace("_", "").isalnum():
            raise SystemExit("%s: needs an icon (res/drawable/lumen_ic_<icon>)" % where)
    return rows


def key_name(r):
    """(string base, number) of a bindable key: app_3 -> (app, 3), f on F7 -> (f, 7), ambient -> (ambient, 0)."""
    if r["name"] == "f":
        return "f", int(r["keycode"][1:])
    base, _, num = r["name"].rpartition("_")
    return (base, int(num)) if num.isdigit() and base else (r["name"], 0)


def render_keys(rows, fixed, actions=()):
    bind = {}
    for r in rows:
        if r["bind"] == "app" and r["keycode"] not in bind:
            bind[r["keycode"]] = r
    codes = sorted(bind, key=lambda k: KEYCODES[k])
    lines = [
        "package org.lumen.remote.keys;",
        "",
        "/**",
        " * GENERATED by tools/remotes/gen_catalog.py from devices/_remotes/keys.tsv, fixed_keys.tsv and key_actions.tsv:",
        " * do not edit, change the tables. The keys a user may give an app (one entry per Android key code, every remote",
        " * that sends it), the keys that never change and the projector actions a key can do. KeyRules and KeyActions read it.",
        " */",
        "final class KeyTable {",
        "    private KeyTable() {}",
        "",
        "    /** Android key codes a user may give an app. */",
        "    static final int[] CODES = {%s};" % ", ".join("%d" % KEYCODES[k] for k in codes),
        "    /** KEYCODE_ names of CODES (logs and tests). */",
        "    static final String[] LABELS = {%s};" % ", ".join(jstr(k) for k in codes),
        "    /** true = a remote key (a global key of the projector app), false = a keyboard key (the key filter). */",
        "    static final boolean[] REMOTE = {%s};" % ", ".join(
            "false" if bind[k]["remote"] == "keyboard" else "true" for k in codes),
        "    /** What each key does while no app is given. */",
        "    static final KeyUnbound[] UNBOUND = {%s};" % ", ".join(
            "KeyUnbound." + UNBOUND[bind[k]["unbound"]] for k in codes),
        "    /** Name on the screen, by what is printed on the key: string lumen_remote_keys_name_<NAME> with NUMBER as its",
        "     *  argument (0 = none), f = F<NUMBER>. */",
        "    static final String[] NAME = {%s};" % ", ".join(jstr(key_name(bind[k])[0]) for k in codes),
        "    static final int[] NUMBER = {%s};" % ", ".join("%d" % key_name(bind[k])[1] for k in codes),
        "    /** Keys never given an app, whatever sends them (fixed_keys.tsv). */",
        "    static final int[] FIXED = {%s};" % ", ".join("%d" % KEYCODES[r["keycode"]] for r in fixed),
        "",
        "    /** Projector actions a key can do instead of an app (key_actions.tsv): saved as do:<ID>. */",
        "    static final String[] ACTION_IDS = {%s};" % ", ".join(jstr(r["id"]) for r in actions),
        "    /** The key each action presses (its own global-key route runs), 0 = it sends ACTION_INTENT instead. */",
        "    static final int[] ACTION_CODES = {%s};" % ", ".join(
            "0" if r["keycode"] == "-" else "%d" % KEYCODES[r["keycode"]] for r in actions),
        "    /** The intent action sent to the projector app itself (a shared screen answers it), null = a key press. */",
        "    static final String[] ACTION_INTENT = {%s};" % ", ".join(jopt(r["intent"]) for r in actions),
        "    /** Shown only where the device profile (assets/lumen_menu/menu.json) has this feature, null = always. */",
        "    static final String[] ACTION_FEATURE = {%s};" % ", ".join(jopt(r["feature"]) for r in actions),
        "    /** Text: string lumen_remote_keys_do_<NAME> with NUMBER as its argument (0 = none). */",
        "    static final String[] ACTION_NAME = {%s};" % ", ".join(jstr(key_name(r)[0]) for r in actions),
        "    static final int[] ACTION_NUMBER = {%s};" % ", ".join("%d" % key_name(r)[1] for r in actions),
        "    /** Shown only with at least this many HDMI inputs, 0 = always. */",
        "    static final int[] ACTION_HDMI = {%s};" % ", ".join(r["hdmi"] for r in actions),
        "    /** Icon on the screen: res/drawable/lumen_ic_<ICON> (KeyIcons). */",
        "    static final String[] ACTION_ICON = {%s};" % ", ".join(jstr(r["icon"]) for r in actions),
        "}",
        "",
    ]
    return "\n".join(lines)


def jopt(s):
    """A Java string, or null for '-'."""
    return "null" if s == "-" else jstr(s)


def jstr(s):
    out = []
    for ch in s:
        if ch in '"\\':
            out.append("\\" + ch)
        elif ord(ch) < 0x20 or ord(ch) > 0x7e:
            out.append("\\u%04x" % ord(ch))
        else:
            out.append(ch)
    return '"' + "".join(out) + '"'


def render(rows):
    lines = [
        "package org.lumen.remote;",
        "",
        "/**",
        " * GENERATED by tools/remotes/gen_catalog.py from devices/_remotes/remotes.tsv: do not edit, change the table.",
        " * Every XGIMI remote and remote-like input device Lumen knows, one Row per table row (README of the table: the",
        " * evidence of each row). RemoteMatch holds the rules that read it.",
        " */",
        "public final class RemoteCatalog {",
        "    private RemoteCatalog() {}",
        "",
        "    /** One known device: names it advertises (matched by RemoteMatch), HID ids, pairing keys, picture. */",
        "    public static final class Row {",
        "        public final String id;",
        "        public final RemoteKind kind;",
        "        public final String[] names;",
        "        /** HID vendor / product id, -1 = unknown */",
        "        public final int vendor, product;",
        "        /** keys held for pairing mode, \"\" = none (HOME+BACK = Home and Back together) */",
        "        public final String combo;",
        "        public final boolean voice, battery;",
        "        /** picture of the pairing screens, \"\" = none */",
        "        public final String art;",
        "",
        "        Row(String id, RemoteKind kind, String[] names, int vendor, int product, String combo, boolean voice,",
        "                boolean battery, String art) {",
        "            this.id = id;",
        "            this.kind = kind;",
        "            this.names = names;",
        "            this.vendor = vendor;",
        "            this.product = product;",
        "            this.combo = combo;",
        "            this.voice = voice;",
        "            this.battery = battery;",
        "            this.art = art;",
        "        }",
        "    }",
        "",
        "    public static final Row[] ROWS = {",
    ]
    for r in rows:
        names = [] if r["names"] == "-" else r["names"].split("|")
        if r["vid_pid"] == "-":
            vid, pid = -1, -1
        else:
            v, _, p = r["vid_pid"].partition(":")
            vid, pid = int(v, 16), int(p, 16)
        dash = lambda s: "" if s == "-" else s
        lines.append("        new Row(%s, RemoteKind.%s, new String[] {%s}, %s, %s, %s, %s, %s, %s)," % (
            jstr(r["id"]), KINDS[r["kind"]], ", ".join(jstr(n) for n in names),
            "0x%04x" % vid if vid >= 0 else "-1", "0x%04x" % pid if pid >= 0 else "-1",
            jstr(dash(r["combo"])), "true" if r["voice"] != "-" else "false",
            "true" if r["battery"] != "-" else "false", jstr(dash(r["art"]))))
    lines += ["    };", "}", ""]
    return "\n".join(lines)


def main(argv):
    remotes = read_table()
    fixed = read_fixed()
    keys = read_keys(remotes=remotes, fixed=fixed)
    actions = read_actions(keys=keys)
    outputs = [(OUT, render(remotes), "%d rows" % len(remotes)),
               (KEYS_OUT, render_keys(keys, fixed, actions), "%d keys, %d fixed, %d actions" % (
                   len({r["keycode"] for r in keys if r["bind"] == "app"}), len(fixed), len(actions)))]
    if "--check" in argv:
        bad = 0
        for path, text, what in outputs:
            have = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
            if have != text:
                print("gen_catalog: %s is stale: run python3 tools/remotes/gen_catalog.py" % os.path.relpath(path, ROOT))
                bad = 1
            else:
                print("gen_catalog: %s matches the tables (%s)" % (os.path.basename(path), what))
        return bad
    for path, text, what in outputs:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        print("gen_catalog: wrote %s (%s)" % (os.path.relpath(path, ROOT), what))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
