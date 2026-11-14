#!/usr/bin/env python3
"""Game menu 3D preview: render baked controllers with the device shaders (preview.js in an offscreen WKWebView via
framesnap) at the frame strip's poses and put them next to the shipped frames.

  python3 preview.py --gm3d assets/gm3d --frames assets/gamemenu --snap .../framesnap --out OUTDIR [--theme ps]
         [--size 600x360 | --card] [--turn 0,3,6] [--idle 0,6]

--card renders at the menu's card size (250 x 150, as GmView draws on a 1920 px wide screen) and shrinks the frames the
way GmView does (decoded at half size, then scaled), so both are compared as the projector shows them.
Writes OUTDIR/<theme>_{gl,old}_<pose>.png (over the card colour and its glow) and OUTDIR/<theme>_compare.png."""
import argparse, json, math, os, subprocess, sys

import numpy as np
from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
GLOW = {'ps': (0x4C, 0x7D, 0xFF), 'xbox': (0x3F, 0xBF, 0x5B), 'switch': (0xFF, 0x5A, 0x51), 'pad': (0x3B, 0x78, 0xE7)}
PANEL = (0x16, 0x17, 0x1A)


def card_bg(theme, w, h):
    """The status card under the picture: panel colour plus the radial glow of GmView.drawStatus."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float32) + 0.5
    cx, cy, r = w / 2, h * 0.46, w * 0.62
    t = np.clip(np.hypot(x - cx, y - cy) / r, 0, 1)
    a = (0x61 / 255) * (1 - t)
    bg = np.array(PANEL, np.float32)[None, None] * (1 - a[..., None]) + np.array(GLOW[theme], np.float32)[None, None] * a[..., None]
    return bg


def over(img, bg):
    a = img[..., 3:4].astype(np.float32) / 255
    return (img[..., :3].astype(np.float32) * a + bg * (1 - a)).clip(0, 255).astype(np.uint8)


def old_frame(frames, theme, kind, i, w, h, card):
    im = Image.open(os.path.join(frames, theme, '%s_%02d.webp' % (kind, i))).convert('RGBA')
    if card:
        im = im.resize((im.width // 2, im.height // 2), Image.BOX)          # inSampleSize 2
    im = im.resize((w, h), Image.BILINEAR)
    return np.asarray(im)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--gm3d', required=True)
    ap.add_argument('--frames', required=True)
    ap.add_argument('--snap', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--theme', action='append')
    ap.add_argument('--size', default='600x360')
    ap.add_argument('--card', action='store_true')
    ap.add_argument('--ss', type=int, default=2)
    ap.add_argument('--turn', default='0,3,6,9,12,15,18,21,24,27')
    ap.add_argument('--idle', default='6,18')
    ap.add_argument('--lgm-suffix', default='')
    a = ap.parse_args()
    w, h = (250, 150) if a.card else map(int, a.size.split('x'))
    os.makedirs(a.out, exist_ok=True)
    turn = [int(x) for x in a.turn.split(',') if x != '']
    idle = [int(x) for x in a.idle.split(',') if x != '']
    poses = [(12.0 * i, 0.0) for i in turn] + [(7.0 * math.sin(2 * math.pi * i / 24), 0.0) for i in idle]
    names = ['turn_%02d' % i for i in turn] + ['idle_%02d' % i for i in idle]
    for theme in a.theme or ['ps', 'xbox', 'switch', 'pad']:
        cfg = dict(lgm='file://' + os.path.abspath(os.path.join(a.gm3d, theme + a.lgm_suffix + '.lgm')),
                   env='file://' + os.path.abspath(os.path.join(a.gm3d, 'env.lge')),
                   vert='file://' + os.path.abspath(os.path.join(a.gm3d, 'model.vert')),
                   frag='file://' + os.path.abspath(os.path.join(a.gm3d, 'model.frag')),
                   W=w, H=h, SS=a.ss, poses=poses)
        tmp = os.path.join(a.out, '_snap_' + theme)
        os.makedirs(tmp, exist_ok=True)
        html = os.path.join(a.out, '_%s.html' % theme)
        open(html, 'w').write('<!doctype html><html><head><meta charset=utf-8></head><body><canvas id=c></canvas>'
                              '<script>window.CFG=%s;</script><script src="file://%s"></script></body></html>'
                              % (json.dumps(cfg), os.path.join(D, 'preview.js')))
        r = subprocess.run([a.snap, html, tmp, str(len(poses)), '120'], capture_output=True, text=True, timeout=300)
        if 'done' not in r.stdout:
            print(theme, 'FAILED', r.stdout[-600:], r.stderr[-300:])
            return 1
        bg = card_bg(theme, w, h)
        rows = []
        for k, name in enumerate(names):
            gl = np.asarray(Image.open(os.path.join(tmp, 'frame_%02d.png' % k)).convert('RGBA'))
            kind, i = name.split('_')
            old = old_frame(a.frames, theme, kind, int(i), w, h, a.card)
            g, o = over(gl, bg), over(old, bg)
            Image.fromarray(g).save(os.path.join(a.out, '%s_gl_%s.png' % (theme, name)))
            Image.fromarray(o).save(os.path.join(a.out, '%s_old_%s.png' % (theme, name)))
            # alpha coverage difference: silhouette and framing agree?
            ag, ao = gl[..., 3] > 128, old[..., 3] > 128
            iou = (ag & ao).sum() / max(1, (ag | ao).sum())
            diff = np.abs(g.astype(np.float32) - o.astype(np.float32)).mean()
            print('%s %s: silhouette IoU %.3f, mean abs diff %.1f' % (theme, name, iou, diff))
            rows.append(np.concatenate([o, np.full((h, 4, 3), 60, np.uint8), g], 1))
        sheet = np.concatenate([np.concatenate([r_, np.full((4, r_.shape[1], 3), 60, np.uint8)], 0) for r_ in rows], 0)
        Image.fromarray(sheet).save(os.path.join(a.out, '%s_compare.png' % theme))
        print(theme, 'sheet', os.path.join(a.out, '%s_compare.png' % theme), '(left: frames, right: real time)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
