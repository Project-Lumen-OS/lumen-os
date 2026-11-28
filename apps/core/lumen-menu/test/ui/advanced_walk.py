#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of General > Advanced settings > Power on and off (the stock more_setting and boot_and_power_off_settings
pages) on an emulator (test APK of test/ui/build.sh with MenuWalkActivity, never a projector), with remote keys
through adb, CHECKING after each step what the walk activity dumps (panel, focus, rows).
    python3 advanced_walk.py SERIAL PACKAGE [ru|en|ar]   (adb from $ADB; SHOTS=dir also saves screenshots)
Checks: General has the Advanced settings row under the Other header and none of the rows that moved behind it;
OK opens Advanced settings with Power on and off first; OK opens Power on and off with the power-on melody and the
rows in the stock order (what opens at power-on, melody, power on with mains); OK on power on with mains (the stand-in
adapter answers it as a real one) flips it and the page, the focus and the row stay; BACK goes one page up each time with the focus on the row that opened the page,
then closes the menu and nothing else comes to the front. One PASS/FAIL line per check."""
import sys
import time

_lang = sys.argv[3:4]
sys.argv[3:4] = ["en"]                     # optionb_walk reads its texts at import: en, ours below
import optionb_walk as ob  # noqa: E402
sys.argv[3:4] = _lang

LANG = sys.argv[3] if len(sys.argv) > 3 else "en"
T = {
    "en": dict(adv="Advanced settings", pof="Power on and off", other="Other", alt="High altitude mode",
               boot="Open at power-on", music="Sound at power-on", ac="Power on auto boot"),
    "ru": dict(adv="Расширенные настройки", pof="Включение и выключение", other="Прочее", alt="Режим большой высоты",
               boot="Открывать при включении", music="Мелодия при включении", ac="Включение питания и автозапуск"),
    "ar": dict(adv="إعدادات متقدمة", pof="التشغيل والإيقاف", other="أخرى", alt="وضع الارتفاعات العالية",
               boot="الفتح عند التشغيل", music="صوت عند التشغيل", ac="التشغيل التلقائي عند توصيل الكهرباء"),
}[LANG]
check, key, state, title, is_header, shot = ob.check, ob.key, ob.state, ob.title, ob.is_header, ob.shot
FWD = "left" if LANG == "ar" else "right"       # right-to-left: the menu mirrors LEFT and RIGHT


def focus_title():
    return title(state()["focus"])


def down_to(name, limit=60):
    for _ in range(limit):
        if focus_title() == name:
            return True
        before = focus_title()
        key("down", 0.5)
        if focus_title() == before:
            return False
    return False


def page_titles():
    return [title(r) for r in state()["rows"] if not is_header(r)]


def open_all():
    """optionb_walk.open_all with the stand-in adapter answering power on with mains as a real adapter (echo)."""
    ob.sh("am force-stop " + ob.PKG)
    ob.sh("input keyevent 3")
    time.sleep(1)
    # right-to-left: All settings opened on General by the walk activity (the card's key path differs there)
    ob.sh("am start -n %s/%s --es echo power.on_ac%s" % (ob.PKG, ob.ACT, " --es section general" if LANG == "ar" else ""))
    time.sleep(4)
    if LANG == "ar":
        return
    check("the card opens", "showing=true" in state()["menu"], state()["menu"])
    for k, n in (("up", 5), (FWD, 4)):            # the gear is the last item of the card's top row
        for _ in range(n):
            if "key=general" in state()["focus"]:
                break
            key(k)
    check("UP and the forward key reach the gear", "key=general" in state()["focus"], state()["focus"])
    key("ok", 2.0)


def main():
    open_all()
    rows = state()["rows"]
    titles = [title(r) for r in rows]
    check("General has the Advanced settings row", T["adv"] in titles, titles)
    i = titles.index(T["adv"]) if T["adv"] in titles else -1
    check("Advanced settings stands under the Other header", i > 0 and is_header(rows[i - 1]) and titles[i - 1] == T["other"],
          titles[max(0, i - 2):i + 1])
    moved = [t for t in (T["alt"], T["boot"], T["music"], T["ac"]) if t and t in titles]
    check("the moved rows are no rows of General", not moved, moved)
    key(FWD, 1.0)
    check("DOWN reaches Advanced settings", down_to(T["adv"]), state()["focus"])
    shot("01_general_advanced")
    key("ok", 1.5)
    adv = page_titles()
    print("advanced: " + " | ".join(adv))
    check("OK opens Advanced settings with Power on and off first", adv[:1] == [T["pof"]], adv)
    check("the focus is on Power on and off", focus_title() == T["pof"], state()["focus"])
    check("Advanced settings has no row of General", T["adv"] not in adv, adv)
    shot("02_advanced")
    key("ok", 1.5)
    pof = page_titles()
    print("power on and off: " + " | ".join(pof))
    want = [t for t in (T["boot"], T["music"], T["ac"]) if t and t in pof]
    check("OK opens Power on and off", T["pof"] not in pof and len(pof) >= 1, pof)
    if T["music"]:
        check("the power-on melody is there (every model)", T["music"] in pof, pof)
    check("its rows in the stock order (power-on source, melody, mains)", [t for t in pof if t in want] == want, pof)
    shot("03_power_on_off")
    if T["ac"] in pof:                          # a device switch (stand-in adapter echo): OK flips it in place
        check("DOWN reaches power on with mains", down_to(T["ac"], 6), state()["focus"])
        before = state()["focus"]
        key("ok", 1.5)
        after = state()["focus"]
        check("OK flips power on with mains and the focus stays on it", title(after) == T["ac"]
              and ("checked=true" in before) != ("checked=true" in after), (before, after))
        check("the page stays open", page_titles() == pof, page_titles())
        key("ok", 1.5)
        check("a second OK puts it back", ("checked=true" in state()["focus"]) == ("checked=true" in before), state()["focus"])
        for _ in range(6):
            if focus_title() == pof[0]:
                break
            key("up", 0.5)
    key("back", 1.2)
    check("BACK goes back to Advanced settings, focus on Power on and off",
          page_titles() == adv and focus_title() == T["pof"], (page_titles(), state()["focus"]))
    key("back", 1.2)
    check("BACK goes back to General, focus on Advanced settings", focus_title() == T["adv"], state()["focus"])
    for _ in range(4):
        if "showing=true" not in state()["menu"]:
            break
        key("back", 1.2)
    check("BACK closes the menu", "showing=true" not in state()["menu"], state()["menu"])
    fr = ob.front()
    check("nothing else came to the front", any(ob.ACT in x for x in fr), fr)
    print("RESULT %s: %d failed" % ("FAIL" if ob.FAILS else "PASS", len(ob.FAILS)))
    return 1 if ob.FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
