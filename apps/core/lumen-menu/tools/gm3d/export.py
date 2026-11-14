#!/usr/bin/env python3
"""Game menu 3D bake, step 1: export each controller scene from three.js (tools/gm3d/README.md).

  python3 export.py --models ~/lumen-build-tmp/qp_design/models --three .../render/three_bundle.js \
      --snap ~/lumen-build-tmp/gm3d/bin/gm3dsnap --out ~/lumen-build-tmp/gm3d/export [themes...]

Writes OUT/<theme>/{meta.json,part_NNN.bin,tex_NN.png} and OUT/env/{env.f32,env.json} (the room environment)."""
import argparse, json, os, subprocess, sys

D = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--models', required=True)
    ap.add_argument('--three', required=True)
    ap.add_argument('--snap', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('themes', nargs='*')
    a = ap.parse_args()
    scenes = json.load(open(os.path.join(D, 'scenes.json')))
    themes = a.themes or list(scenes)
    for i, t in enumerate(themes):
        cfg = dict(W=600, H=360, N=30)
        cfg.update(json.loads(json.dumps(scenes[t])))
        for m in cfg['models']:
            m['url'] = 'file://' + os.path.abspath(os.path.join(os.path.expanduser(a.models), m['url']))
        out = os.path.join(a.out, t)
        os.makedirs(out, exist_ok=True)
        for f in os.listdir(out):
            os.remove(os.path.join(out, f))
        html = os.path.join(a.out, '_%s.html' % t)
        open(html, 'w').write(
            '<!doctype html><html><head><meta charset=utf-8><style>html,body{margin:0;background:transparent}</style></head>'
            '<body><canvas id=c></canvas><script src="file://%s"></script><script>window.CFG=%s;</script>'
            '<script src="file://%s"></script></body></html>'
            % (os.path.abspath(a.three), json.dumps(cfg), os.path.join(D, 'export.js')))
        env = i == 0
        r = subprocess.run([a.snap, html, out] + (['env'] if env else []), capture_output=True, text=True, timeout=600)
        print(t, r.stdout.strip()[-300:], r.stderr.strip()[-300:])
        if 'done' not in r.stdout:
            return 1
        if env:
            e = os.path.join(a.out, 'env')
            os.makedirs(e, exist_ok=True)
            for f in ('env.f32', 'env.json'):
                os.replace(os.path.join(out, f), os.path.join(e, f))
    return 0


if __name__ == '__main__':
    sys.exit(main())
