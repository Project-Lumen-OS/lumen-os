#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The icons of the problem report screen (res/drawable/lumen_report_ic_<name>.xml), from the Lucide line icons of
the approved mock (Lucide, ISC licence, the same family as the quick menu tiles of lumen-menu
tools/qpd_icons.py). Stroke 1.75 on the 24 grid as in the mock, white, tinted by the screen.
    python3 tools/report_icons.py write | check      (run from apps/core/lumen-report)
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "res", "drawable")
SW = "1.75"

# name: (lucide name, mirrored in a right to left UI, [elements]); element = ("path", d) | ("circle", cx, cy, r) |
# ("rect", x, y, w, h, rx) | ("line", x1, y1, x2, y2) | ("polyline", "x y x y ...")
ICONS = {
    "send": ("send", False, [
        ("path", "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z"),
        ("path", "m21.854 2.147-10.94 10.939")]),
    "refresh": ("refresh-cw", False, [
        ("path", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"), ("path", "M21 3v5h-5"),
        ("path", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"), ("path", "M8 16H3v5")]),
    "shield_check": ("shield-check", False, [
        ("path", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"),
        ("path", "m9 12 2 2 4-4")]),
    "shield": ("shield", False, [
        ("path", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z")]),
    "chevron": ("chevron-right", True, [("path", "m9 18 6-6-6-6")]),
    "back": ("arrow-left", True, [("path", "m12 19-7-7 7-7"), ("path", "M19 12H5")]),
    "close": ("x", False, [("path", "M18 6 6 18"), ("path", "m6 6 12 12")]),
    "check": ("check", False, [("path", "M20 6 9 17l-5-5")]),
    "lock": ("lock", False, [("rect", 3, 11, 18, 11, 2), ("path", "M7 11V7a5 5 0 0 1 10 0v4")]),
    "logs": ("scroll-text", False, [
        ("path", "M15 12h-5"), ("path", "M15 8h-5"), ("path", "M19 17V5a2 2 0 0 0-2-2H4"),
        ("path", "M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3")]),
    "alert": ("triangle-alert", False, [
        ("path", "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"), ("path", "M12 9v4"),
        ("path", "M12 17h.01")]),
    "kernel": ("cpu", False, [
        ("rect", 4, 4, 16, 16, 2), ("rect", 9, 9, 6, 6, 1), ("path", "M15 2v2"), ("path", "M15 20v2"),
        ("path", "M2 15h2"), ("path", "M2 9h2"), ("path", "M20 15h2"), ("path", "M20 9h2"), ("path", "M9 2v2"),
        ("path", "M9 20v2")]),
    "devices": ("cable", False, [
        ("path", "M17 21v-2a1 1 0 0 1-1-1v-1a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v1a1 1 0 0 1-1 1"),
        ("path", "M19 15V6.5a1 1 0 0 0-7 0v11a1 1 0 0 1-7 0V9"), ("path", "M21 21v-2h-4"), ("path", "M3 5h4V3"),
        ("path", "M7 5a1 1 0 0 1 1 1v1a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a1 1 0 0 1 1-1V3")]),
    "screen": ("monitor", False, [("rect", 2, 3, 20, 14, 2), ("line", 8, 21, 16, 21), ("line", 12, 17, 12, 21)]),
    "wifi_off": ("wifi-off", False, [
        ("path", "M12 20h.01"), ("path", "M8.5 16.429a5 5 0 0 1 7 0"), ("path", "M5 12.859a10 10 0 0 1 5.17-2.69"),
        ("path", "M19 12.859a10 10 0 0 0-2.007-1.523"), ("path", "M2 8.82a15 15 0 0 1 4.177-2.643"),
        ("path", "M22 8.82a15 15 0 0 0-11.288-3.764"), ("path", "m2 2 20 20")]),
    "package": ("package", False, [
        ("path", "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z"),
        ("path", "M12 22V12"), ("path", "m3.3 7 7.703 4.734a2 2 0 0 0 1.994 0L20.7 7"), ("path", "m7.5 4.27 9 5.15")]),
    "image": ("image", False, [
        ("rect", 3, 3, 18, 18, 2), ("circle", 9, 9, 2), ("path", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21")]),
}


def g(v):
    return ("%.3f" % v).rstrip("0").rstrip(".")


def to_path(e):
    k = e[0]
    if k == "path":
        return e[1]
    if k == "circle":
        _, cx, cy, r = e
        return "M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 %s,0z" % (g(cx - r), g(cy), g(r), g(r), g(2 * r), g(r), g(r), g(-2 * r))
    if k == "rect":
        _, x, y, w, h, r = e
        return ("M%s,%sh%sa%s,%s 0 0 1 %s,%sv%sa%s,%s 0 0 1 %s,%sh%sa%s,%s 0 0 1 %s,%sv%sa%s,%s 0 0 1 %s,%sz"
                % (g(x + r), g(y), g(w - 2 * r), g(r), g(r), g(r), g(r), g(h - 2 * r), g(r), g(r), g(-r), g(r),
                   g(-(w - 2 * r)), g(r), g(r), g(-r), g(-r), g(-(h - 2 * r)), g(r), g(r), g(r), g(-r)))
    if k == "line":
        _, x1, y1, x2, y2 = e
        return "M%s,%sL%s,%s" % (g(x1), g(y1), g(x2), g(y2))
    if k == "polyline":
        p = e[1].split()
        pts = ["%s,%s" % (p[i], p[i + 1]) for i in range(0, len(p), 2)]
        return "M" + "L".join(pts)
    raise ValueError(k)


def render(name):
    lucide, mirror, els = ICONS[name]
    paths = "".join(
        '    <path android:pathData="%s"\n'
        '          android:strokeColor="#FFFFFFFF" android:strokeWidth="%s"\n'
        '          android:strokeLineCap="round" android:strokeLineJoin="round" />\n' % (to_path(e), SW) for e in els)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/report_icons.py: Lucide "%s" (ISC). Do not edit. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"%s>\n'
            '%s</vector>\n') % (lucide, '\n    android:autoMirrored="true"' if mirror else "", paths)


def main(argv):
    mode = argv[1] if len(argv) > 1 else "check"
    bad = 0
    for n in ICONS:
        f = os.path.join(OUT, "lumen_report_ic_%s.xml" % n)
        want = render(n)
        if mode == "write":
            open(f, "w").write(want)
        elif not os.path.isfile(f) or open(f).read() != want:
            print("report_icons: %s differs from the generator" % os.path.basename(f))
            bad = 1
    have = {x for x in os.listdir(OUT) if x.startswith("lumen_report_ic_")}
    extra = have - {"lumen_report_ic_%s.xml" % n for n in ICONS}
    for x in sorted(extra):
        print("report_icons: %s is not generated (remove it or add it to ICONS)" % x)
        bad = 1
    if not bad:
        print("ok   report icons: %d generated drawables" % len(ICONS))
    return bad


if __name__ == "__main__":
    sys.exit(main(sys.argv))
