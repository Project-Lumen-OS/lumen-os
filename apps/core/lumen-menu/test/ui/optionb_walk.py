#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of All settings in its option B layout (2026-11-26) on an emulator (test APK of test/ui/build.sh with
MenuWalkActivity, never a projector), with remote keys through adb, CHECKING after each step what the walk activity
dumps (panel, focus, rows).

    python3 optionb_walk.py SERIAL PACKAGE [ru|en]   (adb from $ADB; SHOTS=dir also saves screenshots)

Checks: the gear opens All settings on General; the category rail starts with General and ends with For developers;
General's group headers come in the option B order (Language and time ... Help), Help is the last group and opens
with Send a problem report; picture off and the interface resolution are no rows of General; DOWN walks every row of
General to its end and never stops on a header; For developers has its System group with the interface resolution
where the model has it; BACK closes the menu and nothing else comes to the front. One PASS/FAIL line per check."""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL, PKG = sys.argv[1], sys.argv[2]
LANG = sys.argv[3] if len(sys.argv) > 3 else "en"
SHOTS = os.environ.get("SHOTS")
ACT = "org.lumen.menu.walk.MenuWalkActivity"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4}
TEXT = {
    "en": dict(general="General", dev="For developers", report="Send a problem report", lamp_off="Turn off screen",
               uires="Interface resolution", system="System",
               headers=["Language and time", "Power", "Display and menu", "Remote and voice", "Apps and devices", "Other",
                        "Storage and updates", "Help"]),
    "ru": dict(general="Общие", dev="Для разработчиков", report="Отправить отчёт о проблеме",
               lamp_off="Отключить изображение", uires="Разрешение интерфейса", system="Система",
               headers=["Язык и время", "Питание", "Экран и меню", "Пульт и голос", "Приложения и устройства", "Прочее",
                        "Память и обновления", "Помощь"]),
}[LANG]
FAILS = []


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=40).stdout.replace("\r", "")


def key(n, wait=0.8):
    sh("input keyevent %d" % KEY[n])
    time.sleep(wait)


def shot(name):
    if not SHOTS:
        return
    os.makedirs(SHOTS, exist_ok=True)
    sh("screencap -p /sdcard/ob.png")
    subprocess.run([ADB, "-s", SERIAL, "pull", "-q", "/sdcard/ob.png", os.path.join(SHOTS, name + ".png")], timeout=60)


def state():
    t = sh("dumpsys activity %s/%s" % (PKG, ACT))
    s = {"rows": [], "menu": "", "focus": ""}
    for line in t.splitlines():
        line = line.strip()
        if line.startswith("walk.menu "):
            s["menu"] = line
        elif line.startswith("walk.focus "):
            s["focus"] = line
        elif line.startswith("walk.row "):
            s["rows"].append(line)
    return s


def check(name, ok, detail: object = ""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + str(detail)[:400] + ")"))
    if not ok:
        FAILS.append(name)
    return ok


def title(line):
    """A row's title; a header's text (HeaderRow keeps it in its text field)."""
    m = re.search(r" title=(.*?)(?: value=| text=| checked=| index=| desc=|$)", line)
    if not m and is_header(line):
        m = re.search(r" text=(.*?)(?: checked=| index=| desc=|$)", line)
    return m.group(1).strip() if m else ""


def desc(line):
    m = re.search(r" desc=(.*)$", line)
    return m.group(1).strip() if m else ""


def is_header(line):
    return "class=HeaderRow" in line


def front():
    return re.findall(r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}", sh("dumpsys window"))


def open_all():
    sh("am force-stop " + PKG)
    sh("input keyevent 3")
    time.sleep(1)
    sh("am start -n %s/%s" % (PKG, ACT))
    time.sleep(4)
    check("the card opens", "showing=true" in state()["menu"], state()["menu"])
    for _ in range(5):
        if "key=general" in state()["focus"]:
            break
        key("up")
    for _ in range(4):
        if "key=general" in state()["focus"]:
            break
        key("right")
    check("UP and RIGHT reach the gear", "key=general" in state()["focus"], state()["focus"])
    key("ok", 2.0)


def rail():
    """The rail from the focused item down to its end (DOWN until the focus stays), then UP back to the top."""
    seen = [desc(state()["focus"])]
    for _ in range(14):
        key("down", 0.6)
        d = desc(state()["focus"])
        if d == seen[-1]:
            break
        seen.append(d)
    for _ in range(len(seen) + 1):
        key("up", 0.5)
    return seen


def main():
    open_all()
    s = state()
    check("All settings opens on General (the first category)", desc(s["focus"]) == TEXT["general"], s["focus"])
    shot("01_general_top")
    names = rail()
    check("the rail starts with General", names[:1] == [TEXT["general"]], names)
    check("General is in the rail once", names.count(TEXT["general"]) == 1, names)
    check("For developers is the last category", names[-1:] == [TEXT["dev"]], names)
    print("rail: " + " | ".join(names))
    s = state()
    check("UP brings the rail back to General", desc(s["focus"]) == TEXT["general"], s["focus"])
    rows = s["rows"]
    heads = [title(r) for r in rows if is_header(r)]
    titles = [title(r) for r in rows]
    print("general headers: " + " | ".join(heads))
    want = [h for h in TEXT["headers"] if h in heads]
    check("General's headers in the option B order", heads[:len(want)] == want and len(want) >= 5, heads)
    check("Language and time opens General", heads[:1] == [TEXT["headers"][0]], heads)
    check("Help is the last group of General", heads[-1:] == [TEXT["headers"][-1]], heads)
    hi = max(i for i, r in enumerate(rows) if is_header(r))
    after = [title(r) for r in rows[hi + 1:]]
    if TEXT["report"] in titles:
        check("Send a problem report opens Help", after[:1] == [TEXT["report"]], after)
    check("no picture off row on General", TEXT["lamp_off"] not in titles, titles)
    check("no interface resolution row on General", TEXT["uires"] not in titles, titles)
    # DOWN through every row of General: never a header, every focusable row once, ends on the last row
    key("right", 1.0)
    walked = []
    for _ in range(len(rows) + 6):
        f = state()["focus"]
        if is_header(f):
            check("DOWN never stops on a header", False, f)
            break
        t = title(f)
        if walked and t == walked[-1]:
            break
        walked.append(t)
        if t == TEXT["report"]:
            shot("02_general_help")
        key("down", 0.6)
    focusable = [title(r) for r in rows if not is_header(r) and "class=TextRow" not in r]
    check("DOWN walks General to its last row", walked and walked[-1] == focusable[-1], (walked[-3:], focusable[-3:]))
    if TEXT["report"] in titles:
        check("DOWN reaches Send a problem report", TEXT["report"] in walked, walked)
    shot("03_general_end")
    print("general walked %d rows, last %s" % (len(walked), walked[-1] if walked else None))
    # For developers: back to the rail, down to its end
    key("left", 1.0)
    for _ in range(len(names) + 1):
        if desc(state()["focus"]) == TEXT["dev"]:
            break
        key("down", 0.6)
    s = state()
    check("the rail reaches For developers", desc(s["focus"]) == TEXT["dev"], s["focus"])
    drows = s["rows"]
    dheads = [title(r) for r in drows if is_header(r)]
    dtitles = [title(r) for r in drows]
    print("developer rows: " + " | ".join(dtitles))
    check("For developers has its System group last", dheads[-1:] == [TEXT["system"]], dheads)
    if TEXT["uires"] in dtitles and TEXT["system"] in dtitles:
        check("the interface resolution opens the System group", dtitles.index(TEXT["uires"]) == dtitles.index(TEXT["system"]) + 1,
              dtitles)
    shot("04_developer")
    for _ in range(4):
        if "showing=true" not in state()["menu"]:
            break
        key("back", 1.2)
    check("BACK closes the menu", "showing=true" not in state()["menu"], state()["menu"])
    fr = front()
    check("nothing else came to the front", any(ACT in x for x in fr), fr)
    sh("am force-stop " + PKG)
    print("optionb_walk %s %s: %s" % (PKG, LANG, "PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
