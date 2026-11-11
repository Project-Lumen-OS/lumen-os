#!/usr/bin/env python3
"""Generates the quick panel (layout D) icons: res/drawable/qpd_ic_<name>.xml (animated-vector) from the approved
prototype's 24x24 SVG icons and their CSS focus animations (qp_design/proto_d3.py). Run from apps/Z9xProjector:
    python3 tools/qpd_icons.py
The output is committed; this script only documents and repeats the port.

Port rules (CSS -> AnimatedVectorDrawable):
  * transform-box: fill-box + transform-origin: center -> a group with pivotX/pivotY at the element's own centre
  * stroke-dasharray on pathLength=1 ("draw")          -> trimPathEnd 0 -> 1
  * animation-fill-mode both with a delay              -> a 0 ms animator at t=0 to the start value, then the real one
  * cubic-bezier(.3,1.45,.55,1) "spring" / (0,0,.58,1)  -> @interpolator/qpd_spring / @interpolator/qpd_ease
The static drawable is the unfocused look; QpdTile starts the animation on focus and resets it on blur.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'res')
SW = '2.1'


def circ(cx, cy, r):
    return f'M{cx - r:g},{cy:g}a{r:g},{r:g} 0 1,0 {2 * r:g},0a{r:g},{r:g} 0 1,0 {-2 * r:g},0z'


def rrect(x, y, w, h, r):
    return (f'M{x + r:g},{y:g}h{w - 2 * r:g}a{r:g},{r:g} 0 0 1 {r:g},{r:g}v{h - 2 * r:g}a{r:g},{r:g} 0 0 1 {-r:g},{r:g}'
            f'h{-(w - 2 * r):g}a{r:g},{r:g} 0 0 1 {-r:g},{-r:g}v{-(h - 2 * r):g}a{r:g},{r:g} 0 0 1 {r:g},{-r:g}z')


def P(name, d, kind='stroke', fa=1.0, sa=1.0, sw=SW, trim=1.0):
    """kind: stroke | fill | both"""
    return dict(t='p', name=name, d=d, kind=kind, fa=fa, sa=sa, sw=sw, trim=trim)


def G(name, px, py, kids, **kw):
    return dict(t='g', name=name, px=px, py=py, kids=kids, **kw)


def A(target, prop, frm, to, dur, delay=0, ease='ease'):
    return (target, prop, frm, to, dur, delay, ease)


def hold(target, prop, v):
    """CSS fill-mode 'both' before a delayed start: jump to the start value at t=0."""
    return (target, prop, v, v, 0, 0, 'ease')


TRAP = 'M7,6h10l3,12H4z'
ICONS = {}

# Autocorrect: the trapezoid fades out, a rectangle draws itself, then the four corners pop in
ICONS['ak'] = ([
    G('g_tz', 12, 12, [P('p_tz', TRAP)]),
    P('p_rc', rrect(4, 6, 16, 12, 1.5), sa=0.0),
    G('g_c1', 4, 6, [P('p_c1', circ(4, 6, 1.7), 'fill', fa=0.0)]),
    G('g_c2', 20, 6, [P('p_c2', circ(20, 6, 1.7), 'fill', fa=0.0)]),
    G('g_c3', 20, 18, [P('p_c3', circ(20, 18, 1.7), 'fill', fa=0.0)]),
    G('g_c4', 4, 18, [P('p_c4', circ(4, 18, 1.7), 'fill', fa=0.0)]),
], [
    A('p_tz', 'strokeAlpha', 1, 0, 360), A('g_tz', 'scaleX', 1, 1.15, 360), A('g_tz', 'scaleY', 1, 0.85, 360),
    hold('p_rc', 'trimPathEnd', 0), A('p_rc', 'strokeAlpha', 0, 1, 1, 140), A('p_rc', 'trimPathEnd', 0, 1, 460, 140),
] + sum([[hold(f'g_c{i}', 'scaleX', 0), hold(f'g_c{i}', 'scaleY', 0),
          A(f'p_c{i}', 'fillAlpha', 0, 1, 1, d),
          A(f'g_c{i}', 'scaleX', 0, 1, 360, d, 'spring'), A(f'g_c{i}', 'scaleY', 0, 1, 360, d, 'spring')]
         for i, d in ((1, 420), (2, 470), (3, 520), (4, 570))], []))

# Keystone: the corner handles pop in one after the other
_k = []
for i, (x, y, d) in enumerate(((7, 6, 0), (17, 6, 70), (20, 18, 140), (4, 18, 210)), 1):
    if d:
        _k += [hold(f'g_k{i}', 'scaleX', 0), hold(f'g_k{i}', 'scaleY', 0)]
    _k += [A(f'g_k{i}', 'scaleX', 0, 1, 380, d, 'spring'), A(f'g_k{i}', 'scaleY', 0, 1, 380, d, 'spring')]
ICONS['kst'] = ([
    P('p_tf', TRAP, 'fill', fa=0.18), P('p_ts', TRAP),
] + [G(f'g_k{i}', x, y, [P(f'p_k{i}', circ(x, y, 1.9), 'fill')])
     for i, (x, y) in enumerate(((7, 6), (17, 6), (20, 18), (4, 18)), 1)], _k)

# Zoom: the lens springs out, the plus spins in
ICONS['zoom'] = ([
    G('g_lens', 10.5, 10.5, [P('p_lf', circ(10.5, 10.5, 6.5), 'fill', fa=0.18), P('p_ls', circ(10.5, 10.5, 6.5))]),
    P('p_h', 'M15.5,15.5l5,5'),
    G('g_plus', 10.5, 10.5, [P('p_plus', 'M10.5,7.8v5.4M7.8,10.5h5.4')]),
], [
    A('g_lens', 'scaleX', 0.8, 1, 520, 0, 'spring'), A('g_lens', 'scaleY', 0.8, 1, 520, 0, 'spring'),
    A('g_plus', 'rotation', -90, 0, 520, 0, 'spring'),
    A('g_plus', 'scaleX', 0.4, 1, 520, 0, 'spring'), A('g_plus', 'scaleY', 0.4, 1, 520, 0, 'spring'),
])

# Picture: three colour discs settle into place
ICONS['pic'] = ([
    G('g_p1', 0, 0, [P('p_p1', circ(12, 8.5, 5), 'fill', fa=0.95)]),
    G('g_p2', 0, 0, [P('p_p2', circ(8.5, 14.5, 5), 'fill', fa=0.6)]),
    G('g_p3', 0, 0, [P('p_p3', circ(15.5, 14.5, 5), 'fill', fa=0.35)]),
], [
    A('g_p1', 'translateY', 3.5, 0, 600, 0, 'spring'),
    A('g_p2', 'translateX', 3, 0, 600, 0, 'spring'), A('g_p2', 'translateY', -2, 0, 600, 0, 'spring'),
    A('g_p3', 'translateX', -3, 0, 600, 0, 'spring'), A('g_p3', 'translateY', -2, 0, 600, 0, 'spring'),
])

# Sound: the two waves draw out of the speaker
ICONS['snd'] = ([
    P('p_body', 'M3.5,9.5h3.5l5,-4v13l-5,-4H3.5z', 'both'),
    P('p_w1', 'M15.5,9.2a4,4 0 0 1 0,5.6'),
    P('p_w2', 'M18.3,6.5a8,8 0 0 1 0,11'),
], [
    hold('p_w1', 'trimPathEnd', 0), A('p_w1', 'trimPathEnd', 0, 1, 320, 60),
    hold('p_w2', 'trimPathEnd', 0), A('p_w2', 'trimPathEnd', 0, 1, 380, 200),
])

# Focus: the brackets close in, then the dot pops
ICONS['focus'] = ([
    G('g_b1', 0, 0, [P('p_b1', 'M4,9V5.5A1.5,1.5 0 0 1 5.5,4H9')]),
    G('g_b2', 0, 0, [P('p_b2', 'M15,4h3.5A1.5,1.5 0 0 1 20,5.5V9')]),
    G('g_b3', 0, 0, [P('p_b3', 'M20,15v3.5a1.5,1.5 0 0 1 -1.5,1.5H15')]),
    G('g_b4', 0, 0, [P('p_b4', 'M9,20H5.5A1.5,1.5 0 0 1 4,18.5V15')]),
    G('g_dot', 12, 12, [P('p_dot', circ(12, 12, 2.6), 'fill')]),
], [
    A('g_b1', 'translateX', -3, 0, 460, 0, 'spring'), A('g_b1', 'translateY', -3, 0, 460, 0, 'spring'),
    A('g_b2', 'translateX', 3, 0, 460, 0, 'spring'), A('g_b2', 'translateY', -3, 0, 460, 0, 'spring'),
    A('g_b3', 'translateX', 3, 0, 460, 0, 'spring'), A('g_b3', 'translateY', 3, 0, 460, 0, 'spring'),
    A('g_b4', 'translateX', -3, 0, 460, 0, 'spring'), A('g_b4', 'translateY', 3, 0, 460, 0, 'spring'),
    hold('g_dot', 'scaleX', 0), hold('g_dot', 'scaleY', 0),
    A('g_dot', 'scaleX', 0, 1, 360, 260, 'spring'), A('g_dot', 'scaleY', 0, 1, 360, 260, 'spring'),
])

# Source: the cable slides into the socket
ICONS['src'] = ([
    P('p_bf', rrect(3, 7, 18, 10, 2.5), 'fill', fa=0.18), P('p_bs', rrect(3, 7, 18, 10, 2.5)),
    G('g_plug', 12, 12, [P('p_plug', 'M7.5,12h9', sw='2.6')]),
], [
    A('g_plug', 'scaleX', 0, 1, 520, 0, 'spring'), A('p_plug', 'strokeAlpha', 0, 1, 520, 0, 'spring'),
])

# Bluetooth: the rune draws itself
ICONS['bt'] = ([P('p_bt', 'M7,7.5l10,9 -5,4.5V3l5,4.5 -10,9')], [A('p_bt', 'trimPathEnd', 0, 1, 620)])

# Network: dot first, then the arcs from small to big
ICONS['wifi'] = ([
    P('p_w3', 'M2.5,9a14,14 0 0 1 19,0'), P('p_w2', 'M5.8,12.4a9.3,9.3 0 0 1 12.4,0'),
    P('p_w1', 'M9,15.7a4.5,4.5 0 0 1 6,0'), G('g_w0', 12, 19, [P('p_w0', circ(12, 19, 1.5), 'fill')]),
], [
    A('g_w0', 'scaleX', 0, 1, 260, 0, 'spring'), A('g_w0', 'scaleY', 0, 1, 260, 0, 'spring'),
    hold('p_w1', 'trimPathEnd', 0), A('p_w1', 'trimPathEnd', 0, 1, 260, 120),
    hold('p_w2', 'trimPathEnd', 0), A('p_w2', 'trimPathEnd', 0, 1, 280, 230),
    hold('p_w3', 'trimPathEnd', 0), A('p_w3', 'trimPathEnd', 0, 1, 300, 340),
])

PAD = ('M7,7h10a5,5 0 0 1 4.8,6.4l-1,3.3a2.6,2.6 0 0 1 -4.4,1L14.6,16H9.4l-1.8,1.7a2.6,2.6 0 0 1 -4.4,-1l-1,-3.3'
       'A5,5 0 0 1 7,7z')
# Game mode: the pad tilts back, the buttons blink
ICONS['game'] = ([
    G('g_pad', 12, 12.6, [
        P('p_pf', PAD, 'fill', fa=0.18), P('p_ps', PAD), P('p_dp', 'M8,10v3.6M6.2,11.8h3.6'),
        P('p_g1', circ(16, 10.6, 1.1), 'fill'), P('p_g2', circ(17.6, 13, 1.1), 'fill'),
    ]),
], [
    A('g_pad', 'rotation', -12, 0, 620, 0, 'spring'),
    A('p_g1', 'fillAlpha', 1, 0.15, 300, 200, 'linear'), A('p_g1', 'fillAlpha', 0.15, 1, 300, 500, 'linear'),
    A('p_g2', 'fillAlpha', 1, 0.15, 300, 330, 'linear'), A('p_g2', 'fillAlpha', 0.15, 1, 300, 630, 'linear'),
])

# Projection: the beam opens out of the lens
ICONS['proj'] = ([
    G('g_beam', 14, 12, [P('p_beam', 'M14,9 22,4v16l-8,-5z', 'fill', fa=0.0)]),
    P('p_rf', rrect(2, 8, 13, 8, 2), 'fill', fa=0.18), P('p_rs', rrect(2, 8, 13, 8, 2)),
    P('p_lens', circ(11, 12, 1.8), 'fill'),
], [
    A('p_beam', 'fillAlpha', 0, 0.55, 700), A('g_beam', 'scaleX', 0, 1, 700),
])

# USB: the stick plugs in
ICONS['usb'] = ([
    G('g_st', 0, 0, [
        P('p_uf', rrect(8, 9, 8, 12, 2), 'fill', fa=0.18), P('p_us', rrect(8, 9, 8, 12, 2)),
        P('p_uh', 'M9.5,9V4.5h5V9'), P('p_ud', 'M11,6.2h0.01M13,6.2h0.01', sw='2.6'),
    ]),
], [A('g_st', 'translateY', 5, 0, 560, 0, 'spring')])

# Screen mirroring (2026-11-11, this tile replaces USB; not in the prototype, drawn in its style): the screen
# with a cast corner (Material cast proportions). Focus: the signal pops out of the corner, ring after ring, then the
# screen draws itself round and its face lights up. The face is cut along y = x + 3.5, which only touches the outer
# ring, so the fill never sits behind the signal; every stroke end keeps 1.2 px or more of air.
MIRROR_FRAME = 'M3,8V6.5A2.5,2.5 0 0 1 5.5,4h13A2.5,2.5 0 0 1 21,6.5v11A2.5,2.5 0 0 1 18.5,20H15'
MIRROR_FACE = 'M5.5,4h13A2.5,2.5 0 0 1 21,6.5v11A2.5,2.5 0 0 1 18.5,20H16.5L3,6.5A2.5,2.5 0 0 1 5.5,4z'
ICONS['mirror'] = ([
    P('p_mf', MIRROR_FACE, 'fill', fa=0.18),
    P('p_ms', MIRROR_FRAME),
    G('g_m0', 3, 20, [P('p_m0', 'M3,17.6a2.4,2.4 0 0 1 2.4,2.4H3z', 'fill')]),
    G('g_m1', 3, 20, [P('p_m1', 'M3,14.8a5.2,5.2 0 0 1 5.2,5.2')]),
    G('g_m2', 3, 20, [P('p_m2', 'M3,11.5a8.5,8.5 0 0 1 8.5,8.5')]),
], [
    A('g_m0', 'scaleX', 0, 1, 300, 0, 'spring'), A('g_m0', 'scaleY', 0, 1, 300, 0, 'spring'),
    hold('g_m1', 'scaleX', 0), hold('g_m1', 'scaleY', 0),
    A('g_m1', 'scaleX', 0, 1, 360, 90, 'spring'), A('g_m1', 'scaleY', 0, 1, 360, 90, 'spring'),
    hold('g_m2', 'scaleX', 0), hold('g_m2', 'scaleY', 0),
    A('g_m2', 'scaleX', 0, 1, 380, 180, 'spring'), A('g_m2', 'scaleY', 0, 1, 380, 180, 'spring'),
    hold('p_ms', 'trimPathEnd', 0), A('p_ms', 'trimPathEnd', 0, 1, 460, 260),
    hold('p_mf', 'fillAlpha', 0), A('p_mf', 'fillAlpha', 0, 0.18, 300, 560),
])

EYE = 'M2,12s3.6,-6.5 10,-6.5S22,12 22,12s-3.6,6.5 -10,6.5S2,12 2,12z'
# Eye protection: one blink
ICONS['eye'] = ([
    G('g_lid', 12, 12, [P('p_ef', EYE, 'fill', fa=0.18), P('p_es', EYE), P('p_ep', circ(12, 12, 3.2), 'fill')]),
], [
    A('g_lid', 'scaleY', 1, 0.08, 182), A('g_lid', 'scaleY', 0.08, 1, 338, 182),
])
ICONS['eyeoff'] = ([
    P('p_o1', 'M2,12s3.6,-6.5 10,-6.5c1.7,0 3.2,0.4 4.5,1M22,12s-1.3,2.3 -3.6,4.1M9.6,18.1c0.8,0.3 1.6,0.4 2.4,0.4 '
              '6.4,0 10,-6.5 10,-6.5'),
    P('p_o2', 'M3.5,3.5l17,17'),
], [A('p_o2', 'trimPathEnd', 0, 1, 360)])

GEAR = ('M12.22,2h-0.44a2,2 0 0 0 -2,2v0.18a2,2 0 0 1 -1,1.73l-0.43,0.25a2,2 0 0 1 -2,0l-0.15,-0.08a2,2 0 0 0 '
        '-2.73,0.73l-0.22,0.38a2,2 0 0 0 0.73,2.73l0.15,0.1a2,2 0 0 1 1,1.72v0.51a2,2 0 0 1 -1,1.74l-0.15,0.09a2,2 0 0 0 '
        '-0.73,2.73l0.22,0.38a2,2 0 0 0 2.73,0.73l0.15,-0.08a2,2 0 0 1 2,0l0.43,0.25a2,2 0 0 1 1,1.73V20a2,2 0 0 0 2,2h0.44'
        'a2,2 0 0 0 2,-2v-0.18a2,2 0 0 1 1,-1.73l0.43,-0.25a2,2 0 0 1 2,0l0.15,0.08a2,2 0 0 0 2.73,-0.73l0.22,-0.39a2,2 0 0 0 '
        '-0.73,-2.73l-0.15,-0.08a2,2 0 0 1 -1,-1.74v-0.5a2,2 0 0 1 1,-1.74l0.15,-0.09a2,2 0 0 0 0.73,-2.73l-0.22,-0.38'
        'a2,2 0 0 0 -2.73,-0.73l-0.15,0.08a2,2 0 0 1 -2,0l-0.43,-0.25a2,2 0 0 1 -1,-1.73V4a2,2 0 0 0 -2,-2z')
# All settings: the gear turns
ICONS['gear'] = ([G('g_gr', 12, 12, [P('p_gr', GEAR), P('p_gc', circ(12, 12, 3))])],
                 [A('g_gr', 'rotation', -60, 0, 700, 0, 'spring')])

# Brightness: the core springs, the rays turn in
ICONS['sun'] = ([
    G('g_sc', 12, 12, [P('p_sc', circ(12, 12, 4.2), 'fill')]),
    G('g_sr', 12, 12, [P('p_sr', 'M12,2.5v2M12,19.5v2M2.5,12h2M19.5,12h2M5.3,5.3l1.4,1.4M17.3,17.3l1.4,1.4M5.3,18.7l1.4,-1.4'
                             'M17.3,6.7l1.4,-1.4')]),
], [
    A('g_sc', 'scaleX', 0.8, 1, 500, 0, 'spring'), A('g_sc', 'scaleY', 0.8, 1, 500, 0, 'spring'),
    A('g_sr', 'rotation', -45, 0, 700, 0, 'spring'),
    A('g_sr', 'scaleX', 0.7, 1, 700, 0, 'spring'), A('g_sr', 'scaleY', 0.7, 1, 700, 0, 'spring'),
    A('p_sr', 'strokeAlpha', 0.3, 1, 700, 0, 'spring'),
])

INTERP = {'ease': '@interpolator/qpd_ease', 'spring': '@interpolator/qpd_spring',
          'linear': '@android:interpolator/linear'}


def node(n, ind):
    sp = '    ' * ind
    if n['t'] == 'g':
        attrs = f'android:name="{n["name"]}" android:pivotX="{n["px"]:g}" android:pivotY="{n["py"]:g}"'
        inner = ''.join(node(k, ind + 1) for k in n['kids'])
        return f'{sp}<group {attrs}>\n{inner}{sp}</group>\n'
    a = [f'android:name="{n["name"]}"', f'android:pathData="{n["d"]}"']
    if n['kind'] in ('fill', 'both'):
        a += ['android:fillColor="#FFFFFFFF"', f'android:fillAlpha="{n["fa"]:g}"']
    if n['kind'] in ('stroke', 'both'):
        a += ['android:strokeColor="#FFFFFFFF"', f'android:strokeAlpha="{n["sa"]:g}"',
              f'android:strokeWidth="{n["sw"]}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    return f'{sp}<path ' + ('\n' + sp + '      ').join(a) + ' />\n'


def avd(elems, anims):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated by tools/qpd_icons.py from the approved quick menu prototype (layout D). Do not edit. -->',
           '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt">',
           '    <aapt:attr name="android:drawable">',
           '        <vector android:width="24dp" android:height="24dp" android:viewportWidth="24"'
           ' android:viewportHeight="24">']
    out.append(''.join(node(e, 3) for e in elems).rstrip('\n'))
    out += ['        </vector>', '    </aapt:attr>']
    by = {}
    for t in anims:
        by.setdefault(t[0], []).append(t)
    for tgt, lst in by.items():
        out += [f'    <target android:name="{tgt}">', '        <aapt:attr name="android:animation">',
                '            <set android:ordering="together">']
        for (_, prop, frm, to, dur, delay, ease) in lst:
            out.append(f'                <objectAnimator android:propertyName="{prop}" android:valueType="floatType"'
                       f' android:valueFrom="{frm:g}" android:valueTo="{to:g}" android:duration="{dur}"'
                       f' android:startOffset="{delay}" android:interpolator="{INTERP[ease]}" />')
        out += ['            </set>', '        </aapt:attr>', '    </target>']
    out.append('</animated-vector>')
    return '\n'.join(out) + '\n'


def main():
    os.makedirs(os.path.join(RES, 'drawable'), exist_ok=True)
    os.makedirs(os.path.join(RES, 'interpolator'), exist_ok=True)
    for name, (elems, anims) in ICONS.items():
        with open(os.path.join(RES, 'drawable', f'qpd_ic_{name}.xml'), 'w') as f:
            f.write(avd(elems, anims))
    for n, c in (('qpd_spring', (0.3, 1.45, 0.55, 1)), ('qpd_ease', (0, 0, 0.58, 1))):
        with open(os.path.join(RES, 'interpolator', n + '.xml'), 'w') as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/qpd_icons.py -->\n'
                    '<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"\n'
                    f'    android:controlX1="{c[0]:g}" android:controlY1="{c[1]:g}" android:controlX2="{c[2]:g}"'
                    f' android:controlY2="{c[3]:g}" />\n')
    print(len(ICONS), 'icons')


if __name__ == '__main__':
    main()
