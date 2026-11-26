#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Every animated quick menu icon stays inside its own 24 x 24 box at every frame: an AnimatedVectorDrawable clips what
leaves its viewport, so a group that slides, grows or turns past the edge is drawn cut (the 3D icon's two screens were
cut at both sides while the tile took the focus). python3 icon_bounds.py RES_DIR [NAME...] (default every lumen_qpd_ic_*).
Translate and scale are followed over the animation with the interpolator's overshoot, rotation by the reach from the
pivot; paths are sampled (lines, curves, arcs), strokes add half their width."""
import glob, math, os, re, sys
import xml.etree.ElementTree as ET

AN = '{http://schemas.android.com/apk/res/android}'
AA = '{http://schemas.android.com/aapt}'
NUM = re.compile(r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?')


def sample_path(d):
    """Points along the path data (absolute), 12 per curve or arc."""
    toks = re.findall(r'[MmLlHhVvCcSsQqTtAaZz]|' + NUM.pattern, d)
    pts, i, cmd = [], 0, None
    x = y = sx = sy = 0.0
    last_c = None
    def nums(n):
        nonlocal i
        v = [float(t) for t in toks[i:i + n]]
        i += n
        return v
    def bez(p):
        for k in range(1, 13):
            t = k / 12.0
            if len(p) == 3:
                pts.append(tuple((1 - t) ** 2 * p[0][j] + 2 * (1 - t) * t * p[1][j] + t * t * p[2][j] for j in (0, 1)))
            else:
                pts.append(tuple((1 - t) ** 3 * p[0][j] + 3 * (1 - t) ** 2 * t * p[1][j] + 3 * (1 - t) * t * t * p[2][j]
                                 + t ** 3 * p[3][j] for j in (0, 1)))
    while i < len(toks):
        if re.match(r'[A-Za-z]', toks[i]):
            cmd = toks[i]
            i += 1
            if cmd in 'Zz':
                x, y = sx, sy
                pts.append((x, y))
                continue
        rel = cmd.islower()
        c = cmd.upper()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if c == 'M':
            a, b = nums(2); x, y = ox + a, oy + b; sx, sy = x, y; pts.append((x, y)); cmd = 'l' if rel else 'L'; last_c = None
        elif c == 'L':
            a, b = nums(2); x, y = ox + a, oy + b; pts.append((x, y)); last_c = None
        elif c == 'H':
            (a,) = nums(1); x = ox + a; pts.append((x, y)); last_c = None
        elif c == 'V':
            (b,) = nums(1); y = oy + b; pts.append((x, y)); last_c = None
        elif c in 'CS':
            if c == 'C':
                v = nums(6); c1 = (ox + v[0], oy + v[1]); c2 = (ox + v[2], oy + v[3]); e = (ox + v[4], oy + v[5])
            else:
                v = nums(4); c1 = (2 * x - last_c[0], 2 * y - last_c[1]) if last_c else (x, y)
                c2 = (ox + v[0], oy + v[1]); e = (ox + v[2], oy + v[3])
            bez([(x, y), c1, c2, e]); last_c = c2; x, y = e
        elif c in 'QT':
            if c == 'Q':
                v = nums(4); q = (ox + v[0], oy + v[1]); e = (ox + v[2], oy + v[3])
            else:
                v = nums(2); q = (2 * x - last_c[0], 2 * y - last_c[1]) if last_c else (x, y); e = (ox + v[0], oy + v[1])
            bez([(x, y), q, e]); last_c = q; x, y = e
        elif c == 'A':
            rx, ry, rot, large, sweep, ex, ey = nums(7)
            ex, ey = ox + ex, oy + ey
            pts.extend(arc(x, y, abs(rx), abs(ry), rot, int(large), int(sweep), ex, ey)); x, y = ex, ey; last_c = None
    return pts


def arc(x1, y1, rx, ry, rot, fa, fs, x2, y2):
    """SVG arc to points (endpoint to centre form, SVG 1.1 F.6.5)."""
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    p = math.radians(rot)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = math.cos(p) * dx + math.sin(p) * dy, -math.sin(p) * dx + math.cos(p) * dy
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    co = math.sqrt(max(0, num / (rx * rx * y1p * y1p + ry * ry * x1p * x1p))) * (-1 if fa == fs else 1)
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx = math.cos(p) * cxp - math.sin(p) * cyp + (x1 + x2) / 2
    cy = math.sin(p) * cxp + math.cos(p) * cyp + (y1 + y2) / 2
    ang = lambda ux, uy, vx, vy: math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    out = []
    for k in range(1, 13):
        t = t1 + dt * k / 12
        out.append((cx + rx * math.cos(t) * math.cos(p) - ry * math.sin(t) * math.sin(p),
                    cy + rx * math.cos(t) * math.sin(p) + ry * math.sin(t) * math.cos(p)))
    return out


def overshoot(res, name):
    """The interpolated value range of 0..1 for an interpolator resource (a cubic pathInterpolator may overshoot)."""
    f = os.path.join(res, 'interpolator', name + '.xml')
    if not os.path.exists(f):
        return 0.0, 1.0
    e = ET.parse(f).getroot()
    if e.get(AN + 'controlX1') is None:
        return 0.0, 1.0
    y1, y2 = float(e.get(AN + 'controlY1')), float(e.get(AN + 'controlY2'))
    ys = [3 * (1 - t) ** 2 * t * y1 + 3 * (1 - t) * t * t * y2 + t ** 3 for t in (k / 200 for k in range(201))]
    return min(ys), max(ys)


def check(res, path):
    root = ET.parse(path).getroot()
    vec = root.find(AA + 'attr').find('vector')
    vw, vh = float(vec.get(AN + 'viewportWidth')), float(vec.get(AN + 'viewportHeight'))
    ranges = {}                                   # (target, property) -> (lo, hi) over every animator and overshoot
    for t in root.findall('target'):
        name = t.get(AN + 'name')
        for oa in t.iter('objectAnimator'):
            prop = oa.get(AN + 'propertyName')
            fr, to = oa.get(AN + 'valueFrom'), oa.get(AN + 'valueTo')
            if fr is None or to is None or prop not in ('translateX', 'translateY', 'scaleX', 'scaleY', 'rotation'):
                continue
            fr, to = float(fr), float(to)
            interp = (oa.get(AN + 'interpolator') or '').split('/')[-1]
            lo, hi = overshoot(res, interp)
            vals = [fr + (to - fr) * lo, fr + (to - fr) * hi, fr, to]
            r = ranges.setdefault((name, prop), [min(vals), max(vals)])
            r[0], r[1] = min(r[0], *vals), max(r[1], *vals)
    bad = []
    for g in vec.iter('group'):
        gname = g.get(AN + 'name')
        px, py = float(g.get(AN + 'pivotX') or 0), float(g.get(AN + 'pivotY') or 0)
        rx = ranges.get((gname, 'translateX'), [0, 0]); ry = ranges.get((gname, 'translateY'), [0, 0])
        sx = ranges.get((gname, 'scaleX'), [1, 1]); sy = ranges.get((gname, 'scaleY'), [1, 1])
        turns = (gname, 'rotation') in ranges
        if (rx, ry, sx, sy) == ([0, 0], [0, 0], [1, 1], [1, 1]) and not turns:
            continue
        for p in g.iter('path'):
            half = float(p.get(AN + 'strokeWidth') or 0) / 2 if p.get(AN + 'strokeColor') else 0.0
            for x, y in sample_path(p.get(AN + 'pathData')):
                if turns:                         # any angle: the reach from the pivot
                    reach = math.hypot(x - px, y - py) * max(abs(v) for v in sx + sy) + half
                    xs, ys = [px - reach, px + reach], [py - reach, py + reach]
                else:
                    xs = [px + (x - px) * s + t + e for s in sx for t in rx for e in (-half, half)]
                    ys = [py + (y - py) * s + t + e for s in sy for t in ry for e in (-half, half)]
                if min(xs) < -0.01 or max(xs) > vw + 0.01 or min(ys) < -0.01 or max(ys) > vh + 0.01:
                    bad.append('%s %s: reaches x %.2f..%.2f y %.2f..%.2f of %gx%g'
                               % (gname, p.get(AN + 'name'), min(xs), max(xs), min(ys), max(ys), vw, vh))
                    break
    return bad


def main():
    res = sys.argv[1]
    names = sys.argv[2:] or [os.path.basename(f)[:-4] for f in sorted(glob.glob(os.path.join(res, 'drawable', 'lumen_qpd_ic_*.xml')))]
    fails, n = 0, 0
    for name in names:
        f = os.path.join(res, 'drawable', name + '.xml')
        if ET.parse(f).getroot().tag != 'animated-vector':
            continue
        n += 1
        for b in check(res, f):
            print('FAIL icon %s: %s' % (name, b))
            fails += 1
    print('icon bounds: %d animated icons, %d cut' % (n, fails))
    sys.exit(1 if fails else 0)


if __name__ == '__main__':
    main()
