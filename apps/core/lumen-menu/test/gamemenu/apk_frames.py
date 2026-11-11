#!/usr/bin/env python3
"""Controller frames of the Lumen game menu: every theme has its 30 turn + 24 sway frames, each a static WebP with
alpha (VP8X alpha flag, no animation flag, no ANIM/ANMF chunk) of 600 x 360, decodable with a visible controller in it
(Pillow, when installed). With --apk the built APK must hold every frame byte-identical to assets/ (a missing or
altered asset in the image leaves the card empty on the projector).

  python3 test/gamemenu/apk_frames.py --assets assets/gamemenu [--apk build/Z9xProjector.apk] [--need-pil]
"""
import argparse, io, os, struct, sys, zipfile

THEMES = ['ps', 'xbox', 'switch', 'pad']          # GmModel.FRAME_THEMES
NAMES = ['turn_%02d.webp' % i for i in range(30)] + ['idle_%02d.webp' % i for i in range(24)]
W, H = 600, 360


def webp_problem(b):
    """None when b is a static 600 x 360 WebP with alpha, else what is wrong."""
    if len(b) < 30 or b[:4] != b'RIFF' or b[8:12] != b'WEBP':
        return 'not a WebP'
    if struct.unpack('<I', b[4:8])[0] + 8 != len(b):
        return 'RIFF size %d does not match the file (%d)' % (struct.unpack('<I', b[4:8])[0] + 8, len(b))
    chunks, i = [], 12
    while i + 8 <= len(b):
        t, n = b[i:i + 4], struct.unpack('<I', b[i + 4:i + 8])[0]
        chunks.append((t, b[i + 8:i + 8 + n]))
        i += 8 + n + (n & 1)
    kinds = [t for t, _ in chunks]
    if kinds[:1] != [b'VP8X']:
        return 'no VP8X header (no alpha): %s' % kinds
    d = chunks[0][1]
    flags = d[0]
    if flags & 0x02:
        return 'animated WebP (BitmapFactory shows only frame 0)'
    if not flags & 0x10:
        return 'no alpha flag'
    w, h = 1 + int.from_bytes(d[4:7], 'little'), 1 + int.from_bytes(d[7:10], 'little')
    if (w, h) != (W, H):
        return 'canvas %dx%d (want %dx%d)' % (w, h, W, H)
    if b'ANIM' in kinds or b'ANMF' in kinds:
        return 'animation chunks'
    if not (b'VP8 ' in kinds or b'VP8L' in kinds):
        return 'no image chunk'
    return None


def decode_problem(b, pil):
    if pil is None:
        return None
    try:
        im = pil.open(io.BytesIO(b))
        im.load()
    except Exception as e:  # noqa: BLE001
        return 'decoder: %s' % e
    if im.size != (W, H) or im.mode != 'RGBA':
        return 'decoded %s %s (want RGBA %dx%d)' % (im.mode, im.size, W, H)
    a = im.getchannel('A').point(lambda v: 255 if v > 8 else 0)
    box = a.getbbox()
    seen = a.histogram()[255]
    # a turn frame seen from the side is narrow (~120 px): ask for a solid body, not a width
    if box is None or (box[3] - box[1]) < H // 3 or seen < W * H // 50:
        return 'no visible controller (alpha box %s, %d px)' % (box, seen)
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--assets', required=True)
    ap.add_argument('--apk')
    ap.add_argument('--need-pil', action='store_true')
    a = ap.parse_args()
    try:
        from PIL import Image, features
        pil = Image if features.check('webp') else None
    except Exception:  # noqa: BLE001
        pil = None
    if pil is None and a.need_pil:
        print('FAIL frames: Pillow with WebP is needed (--need-pil)')
        return 1
    bad, n = 0, 0
    z = zipfile.ZipFile(a.apk) if a.apk else None
    for t in THEMES:
        d = os.path.join(a.assets, t)
        have = sorted(os.listdir(d)) if os.path.isdir(d) else []
        if sorted(have) != sorted(NAMES):
            print('FAIL frames %s: files %s (want turn_00..29 + idle_00..23)' % (t, sorted(set(have) ^ set(NAMES))[:6]))
            bad += 1
        for name in NAMES:
            p = os.path.join(d, name)
            if not os.path.isfile(p):
                continue
            b = open(p, 'rb').read()
            why = webp_problem(b) or decode_problem(b, pil)
            if why:
                print('FAIL frames %s/%s: %s' % (t, name, why)); bad += 1
            if z is not None:
                entry = 'assets/gamemenu/%s/%s' % (t, name)
                try:
                    zb = z.read(entry)
                except KeyError:
                    print('FAIL apk: %s missing' % entry); bad += 1; continue
                if zb != b:
                    print('FAIL apk: %s differs from assets/' % entry); bad += 1
            n += 1
    if z is not None:
        extra = [e for e in z.namelist() if e.startswith('assets/gamemenu/')
                 and e.split('/')[2] not in THEMES]
        if extra:
            print('FAIL apk: frames of an unknown theme: %s' % extra[:4]); bad += 1
    print('frames: %d checked (%s%s), %d problems' % (n, 'decoded with Pillow' if pil else 'structure only, no Pillow',
                                                     ', APK ' + os.path.basename(a.apk) if a.apk else '', bad))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
