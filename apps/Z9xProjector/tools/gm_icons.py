#!/usr/bin/env python3
"""Generates the Lumen game menu icons: res/drawable/gm_ic_<name>.xml (animated-vector) from the approved prototype's
24x24 SVG icons and their CSS focus animations (qp_design/game_menu.py, design A). Run from apps/Z9xProjector:
    python3 tools/gm_icons.py
The output is committed; this script only documents and repeats the port. Same port rules and writer as
tools/qpd_icons.py (draw-on = trimPathEnd, fill-box transforms = groups pivoted at the element's centre).
"""
import importlib.util
import os

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location('qpd_icons', os.path.join(HERE, 'qpd_icons.py'))
Q = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(Q)
P, G, A, hold, circ, rrect = Q.P, Q.G, Q.A, Q.hold, Q.circ, Q.rrect
RES = os.path.join(HERE, '..', 'res')

ZAP = 'M13,2.5L4.5,13.5h6.5l-1,8 8.5,-11h-6.5z'
ICONS = {}

# Response speed: the bolt draws itself
ICONS['zap'] = ([P('p_zf', ZAP, 'fill', fa=0.18), P('p_zs', ZAP)], [A('p_zs', 'trimPathEnd', 0, 1, 520)])

# VRR: the wave draws itself over a faint base line
ICONS['vrr'] = ([
    P('p_vw', 'M2,12c1.6,-4.4 3.4,-4.4 5,0s3.4,4.4 5,0c1.1,-2.9 2.3,-2.9 3.4,0s2.3,2.9 3.4,0c0.7,-1.8 1.5,-1.8 2.2,0'),
    P('p_vb', 'M2,20h20', sa=0.35),
], [A('p_vw', 'trimPathEnd', 0, 1, 700)])

# High frame rate: three frames slide in one after the other
ICONS['hfr'] = ([
    G('g_h1', 15, 8, [P('p_h1', rrect(9, 3.5, 12, 9, 2), sa=0.45)]),
    G('g_h2', 12, 11.5, [P('p_h2', rrect(6, 7, 12, 9, 2), sa=0.7)]),
    G('g_h3', 9, 15, [P('p_h3', rrect(3, 10.5, 12, 9, 2), 'both', fa=0.18)]),
], sum([[hold(f'g_h{i}', 'translateX', 6), hold(f'g_h{i}', 'translateY', -5),
         A(f'g_h{i}', 'translateX', 6, 0, 520, d, 'spring'), A(f'g_h{i}', 'translateY', -5, 0, 520, d, 'spring')]
        for i, d in ((1, 120), (2, 60), (3, 0))], []))

# Game picture: the mountains draw, the sun rises
ICONS['pic'] = ([
    P('p_pf', rrect(3, 4, 18, 16, 3), 'both', fa=0.18),
    P('p_pm', 'M3.5,17l5,-5.2 4,4 2.8,-2.8 5.2,5'),
    G('g_ps', 16, 8.8, [P('p_ps', circ(16, 8.8, 1.7), 'fill')]),
], [A('p_pm', 'trimPathEnd', 0, 1, 520), hold('g_ps', 'translateY', 5),
    A('g_ps', 'translateY', 5, 0, 620, 200, 'spring')])

# Dark scenes: the moon turns in, a spark pops
MOON = 'M20,14.6A8.2,8.2 0 1 1 9.4,4a6.6,6.6 0 0 0 10.6,10.6z'
ICONS['dark'] = ([
    G('g_mo', 12.3, 12.7, [P('p_mo', MOON, 'both', fa=0.18)]),
    G('g_sp', 17.5, 4.5, [P('p_sp', 'M17.5,2.8v3.4M15.8,4.5h3.4')]),
], [A('g_mo', 'rotation', -40, 0, 620, 0, 'spring'), A('g_mo', 'scaleX', 0.8, 1, 620, 0, 'spring'),
    A('g_mo', 'scaleY', 0.8, 1, 620, 0, 'spring'), hold('g_sp', 'scaleX', 0), hold('g_sp', 'scaleY', 0),
    A('g_sp', 'scaleX', 0, 1, 420, 260, 'spring'), A('g_sp', 'scaleY', 0, 1, 420, 260, 'spring')])

# Super resolution: the grid draws, then the finer grid
ICONS['sr'] = ([
    P('p_sf', rrect(3, 3, 18, 18, 3), 'both', fa=0.18),
    P('p_g1', 'M12,3v18M3,12h18'),
    P('p_g2', 'M7.5,3v18M16.5,3v18M3,7.5h18M3,16.5h18', sa=0.45, sw='1.4'),
], [A('p_g1', 'trimPathEnd', 0, 1, 380), hold('p_g2', 'trimPathEnd', 0), A('p_g2', 'trimPathEnd', 0, 1, 420, 220)])

# Aspect ratio: the arrows push out to the sides
ICONS['ar'] = ([
    P('p_af', rrect(2, 6, 20, 12, 2.5), 'both', fa=0.18),
    G('g_al', 7.25, 12, [P('p_al', 'M9,12H5.5m0,0 1.8,-1.8M5.5,12l1.8,1.8')]),
    G('g_ar', 16.75, 12, [P('p_ar', 'M15,12h3.5m0,0 -1.8,-1.8m1.8,1.8 -1.8,1.8')]),
], [A('g_al', 'translateX', 4, 0, 560, 0, 'spring'), A('g_ar', 'translateX', -4, 0, 560, 0, 'spring')])

# Game sound: the waves draw one after the other
ICONS['snd'] = ([
    P('p_sk', 'M3.5,9.5h3.5l5,-4v13l-5,-4H3.5z', 'fill'),
    P('p_w1', 'M15.5,9.2a4,4 0 0 1 0,5.6'),
    P('p_w2', 'M18.3,6.5a8,8 0 0 1 0,11'),
], [hold('p_w1', 'trimPathEnd', 0), A('p_w1', 'trimPathEnd', 0, 1, 320, 60),
    hold('p_w2', 'trimPathEnd', 0), A('p_w2', 'trimPathEnd', 0, 1, 380, 200)])

# Crosshair: the ring spins in, the ticks close in, the dot pops
ICONS['aim'] = ([
    G('g_r1', 12, 12, [P('p_r1', circ(12, 12, 7))]),
    G('g_at', 12, 12, [P('p_at', 'M12,2.5v4M12,17.5v4M2.5,12h4M17.5,12h4')]),
    G('g_ad', 12, 12, [P('p_ad', circ(12, 12, 1.6), 'fill')]),
], [A('g_r1', 'rotation', -90, 0, 620, 0, 'spring'), A('g_r1', 'scaleX', 0.6, 1, 620, 0, 'spring'),
    A('g_r1', 'scaleY', 0.6, 1, 620, 0, 'spring'), A('g_at', 'scaleX', 1.5, 1, 520, 0, 'spring'),
    A('g_at', 'scaleY', 1.5, 1, 520, 0, 'spring'), A('p_at', 'strokeAlpha', 0, 1, 520),
    hold('g_ad', 'scaleX', 0), hold('g_ad', 'scaleY', 0),
    A('g_ad', 'scaleX', 0, 1, 300, 300, 'spring'), A('g_ad', 'scaleY', 0, 1, 300, 300, 'spring')])

# Frame counter: the needle swings up
ICONS['fps'] = ([
    P('p_fa', 'M4.2,18.5a9,9 0 1 1 15.6,0'),
    G('g_nd', 12, 14.5, [P('p_nd', 'M12,14.5L16.2,9')]),
    P('p_fc', circ(12, 14.5, 1.7), 'fill'),
], [A('g_nd', 'rotation', -120, 0, 760, 0, 'spring')])

# Presets: Balance (sliders) and Story (film); Competitive uses zap
ICONS['sliders'] = ([
    P('p_sl', 'M4,7h9M17,7h3M4,17h3M11,17h9'),
    G('g_k1', 15, 7, [P('p_k1', circ(15, 7, 2.2), 'fill')]),
    G('g_k2', 9, 17, [P('p_k2', circ(9, 17, 2.2), 'fill')]),
], [A('g_k1', 'translateX', -4, 0, 520, 0, 'spring'), A('g_k2', 'translateX', 4, 0, 520, 0, 'spring')])
ICONS['film'] = ([
    P('p_ff', rrect(3, 4, 18, 16, 3), 'both', fa=0.18),
    P('p_fl', 'M7,4v16M17,4v16M3,9h4M3,15h4M17,9h4M17,15h4'),
], [A('p_fl', 'trimPathEnd', 0, 1, 520)])

# Static glyphs of the popups and notes
ICONS['move'] = ([P('p_mv', 'M12,3v18M3,12h18M12,3l-2.5,2.5M12,3l2.5,2.5M12,21l-2.5,-2.5M12,21l2.5,-2.5M3,12l2.5,-2.5'
                     'M3,12l2.5,2.5M21,12l-2.5,-2.5M21,12l-2.5,2.5')], [])
ICONS['info'] = ([P('p_ic', circ(12, 12, 9)), P('p_il', 'M12,11v5.5M12,7.6v0.4')], [])
# Computers in the card's picture slot (2026-11-11: no gamepad for a computer): flat monochrome, no animation
APPLE = ('M16.5,12.8c0,-2.2 1.8,-3.3 1.9,-3.4c-1,-1.5 -2.6,-1.7 -3.2,-1.7c-1.4,-0.1 -2.6,0.8 -3.3,0.8'
         'c-0.7,0 -1.7,-0.8 -2.8,-0.8c-1.5,0 -2.8,0.9 -3.6,2.2c-1.5,2.6 -0.4,6.5 1.1,8.6c0.7,1 1.6,2.2 2.7,2.2'
         'c1.1,0 1.5,-0.7 2.8,-0.7c1.3,0 1.7,0.7 2.8,0.7c1.2,0 1.9,-1.1 2.6,-2.1c0.8,-1.2 1.2,-2.3 1.2,-2.4'
         'c0,0 -2.2,-0.9 -2.2,-3.4z'
         'M14.4,6.2c0.6,-0.7 1,-1.7 0.9,-2.7c-0.9,0 -1.9,0.6 -2.5,1.3c-0.6,0.6 -1,1.6 -0.9,2.6c1,0.1 1.9,-0.5 2.5,-1.2z')
ICONS['apple'] = ([P('p_ap', APPLE, 'fill')], [])
ICONS['windows'] = ([P('p_wn', 'M3,3h8.5v8.5H3zM12.5,3H21v8.5h-8.5zM3,12.5h8.5V21H3zM12.5,12.5H21V21h-8.5z', 'fill')], [])
ICONS['laptop'] = ([P('p_lf', 'M6,5h12a2,2 0 0 1 2,2v9H4V7a2,2 0 0 1 2,-2z', 'fill', fa=0.18),
                    P('p_ls', 'M20,16V7a2,2 0 0 0 -2,-2H6a2,2 0 0 0 -2,2v9m16,0H4m16,0 1.28,2.55a1,1 0 0 1 -0.9,1.45'
                              'H3.62a1,1 0 0 1 -0.9,-1.45L4,16')], [])
ICONS['gear'] = ([G('g_gr', 12, 12, [P('p_gr', Q.GEAR), P('p_gc', circ(12, 12, 3))])],
                 [A('g_gr', 'rotation', 0, 90, 600, 0, 'spring')])


def main():
    os.makedirs(os.path.join(RES, 'drawable'), exist_ok=True)
    for name, (elems, anims) in ICONS.items():
        x = Q.avd(elems, anims).replace('tools/qpd_icons.py from the approved quick menu prototype (layout D)',
                                        'tools/gm_icons.py from the approved game menu prototype (design A)')
        with open(os.path.join(RES, 'drawable', f'gm_ic_{name}.xml'), 'w') as f:
            f.write(x)
    print(len(ICONS), 'icons')


if __name__ == '__main__':
    main()
