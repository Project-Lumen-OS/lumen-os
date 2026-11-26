#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Disabled row look on an EMULATOR, never a user's projector (test/ui/run_grey.sh builds the APKs).

  python3 grey_render.py SERIAL PACKAGE GSI OUT NAME [signal ...]

Per theme (dark, light) and content kind the GreyRowsActivity of the model's test APK opens the picture mode list
(SignalRule greys the modes the content does not allow), the screen is captured to OUT/NAME_<theme>_<signal>.png and
every picture mode row is read off the pixels: a greyed row's title is the menu's disabled grey (QpdLayout.disabled,
opaque and neutral, within 12 per channel) and every enabled row's title stands clearly apart from it (contrast 3:1
or more); the number of greyed rows is the number SignalRule greys, all of them stay listed, the focus is on a usable
row. Exit 1 on any failure. No dependencies beyond the standard library (own PNG reader).
"""
import os, re, struct, subprocess, sys, time, zlib

ADB = os.environ.get('ADB', 'adb')
TOL = 12


def adb(serial, *a, out=False):
    r = subprocess.run([ADB, '-s', serial] + list(a), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return r.stdout if out else r.returncode


def png_rgb(data):
    """8-bit RGB / RGBA non-interlaced PNG (screencap) -> (w, h, rows of bytes, bytes per pixel)."""
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    i, idat, w, h, bpp = 8, b'', 0, 0, 3
    while i < len(data):
        n, t = struct.unpack('>I4s', data[i:i + 8])
        body = data[i + 8:i + 8 + n]
        if t == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
            assert depth == 8 and ctype in (2, 6), 'PNG type %d/%d' % (depth, ctype)
            bpp = 3 if ctype == 2 else 4
        elif t == b'IDAT':
            idat += body
        i += 12 + n
    raw, stride, rows, prev = zlib.decompress(idat), w * bpp, [], bytearray(w * bpp)
    for y in range(h):
        f, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b, c = prev[x], prev[x - bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line))
        prev = line
    return w, h, rows, bpp


_LUM = {}


def lum(rgb):
    if rgb in _LUM: return _LUM[rgb]
    def lin(c):
        c /= 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    _LUM[rgb] = v = 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2])
    return v


def contrast(a, b):
    x, y = lum(a), lum(b)
    return (max(x, y) + 0.05) / (min(x, y) + 0.05)


def ink(img, box):
    """The title colour of a row: the pixel of the row's text half farthest in luminance from its background."""
    w, h, rows, bpp = img
    l, t, r, b = box
    px = lambda x, y: tuple(rows[y][x * bpp:x * bpp + 3])
    cy = (t + b) // 2
    bgc = px(l + 6, cy)
    best, far = bgc, 0.0
    for y in range(t + 4, b - 4):
        for x in range(l + (r - l) // 20, l + (r - l) * 3 // 5):
            p = px(x, y)
            d = abs(lum(p) - lum(bgc))
            if d > far: best, far = p, d
    return best, bgc


def greys(gsi):
    src = open(os.path.join(gsi, 'apps/core/lumen-menu/src/org/lumen/projector/panel/QpdLayout.java')).read()
    m = re.search(r'DISABLED_DARK = 0x([0-9A-Fa-f]{8}), DISABLED_LIGHT = 0x([0-9A-Fa-f]{8});', src)
    return {'dark': tuple(bytes.fromhex(m.group(1))[1:]), 'light': tuple(bytes.fromhex(m.group(2))[1:])}


def main():
    serial, pkg, gsi, out, name = sys.argv[1:6]
    signals = sys.argv[6:] or ['sdr', 'dolby']
    want = greys(gsi)
    act = pkg + '/org.lumen.menu.walk.GreyRowsActivity'
    bad = 0
    for theme in ('dark', 'light'):
        adb(serial, 'shell', 'settings', 'put', 'secure', 'lumen_ui_theme', theme)
        for sig in signals:
            run = '%s-%s-%s-%d' % (name, theme, sig, int(time.time() * 1000) % 100000000)
            adb(serial, 'shell', 'am', 'force-stop', pkg)
            adb(serial, 'shell', 'am', 'start', '-n', act, '--es', 'signal', sig, '--es', 'run', run, '--es', 'view', 'list')
            lines = []
            for _ in range(20):
                time.sleep(1)
                log = adb(serial, 'logcat', '-d', '-s', 'GreyRows:*', out=True).decode('utf-8', 'replace')
                lines = [l.split(run + ' ', 1)[1] for l in log.splitlines() if (run + ' ') in l]
                if any(l.startswith('done') or l.startswith('fail') for l in lines): break
            png = adb(serial, 'exec-out', 'screencap', '-p', out=True)
            path = os.path.join(out, '%s_%s_%s.png' % (name, theme, sig))
            open(path, 'wb').write(png)
            tag = '%s %s %s' % (name, theme, sig)
            fails = [l for l in lines if l.startswith('fail')]
            exp = [l.split() for l in lines if l.startswith('expect')]
            rows = [l.split(' ', 7) for l in lines if l.startswith('row ')]
            if fails or not exp or not rows:
                print('FAIL %s: harness %s, %d rows' % (tag, fails or 'no expect line', len(rows)))
                bad = 1
                continue
            greyed, listed = int(exp[0][1]), int(exp[0][2])
            img = png_rgb(png)
            g = want[theme]
            shown = off = 0
            for r in rows:
                l, t, rr, b, en, fo, title = int(r[1]), int(r[2]), int(r[3]), int(r[4]), r[5] == '1', r[6] == '1', r[7]
                if t < 0 or b > img[1]: continue                          # scrolled out of the screen
                shown += 1
                c, bgc = ink(img, (l, t, rr, b))
                if not en:
                    off += 1
                    if fo: print('FAIL %s: greyed %s has the focus' % (tag, title)); bad = 1
                    if max(abs(a - e) for a, e in zip(c, g)) > TOL:
                        print('FAIL %s: greyed %s drawn #%02X%02X%02X, want the grey #%02X%02X%02X'
                              % ((tag, title) + c + g)); bad = 1
                elif not fo and contrast(c, g) < 3.0:
                    print('FAIL %s: usable %s drawn #%02X%02X%02X, too close to the grey (%.2f:1)'
                          % ((tag, title) + c + (contrast(c, g),))); bad = 1
            if len(rows) != listed:
                print('FAIL %s: %d mode rows listed, the profile has %d (a mode hidden)' % (tag, len(rows), listed)); bad = 1
            if sum(1 for r in rows if r[5] == '0') != greyed:
                print('FAIL %s: %d rows greyed, SignalRule greys %d' % (tag, sum(1 for r in rows if r[5] == '0'), greyed)); bad = 1
            print('%s: %d modes, %d greyed (%d on screen), grey #%02X%02X%02X -> %s'
                  % ((tag, listed, greyed, off) + g + (os.path.basename(path),)))
    adb(serial, 'shell', 'settings', 'put', 'secure', 'lumen_ui_theme', 'dark')
    return bad


if __name__ == '__main__':
    sys.exit(main())
