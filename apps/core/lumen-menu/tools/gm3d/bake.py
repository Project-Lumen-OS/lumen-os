#!/usr/bin/env python3
"""Game menu 3D bake, step 3 (tools/gm3d/README.md): pack one controller into assets/gm3d/<theme>.lgm and the room
environment into assets/gm3d/env.lge (formats: Gm3dModel.java, the one reader on the device).

  python3 bake.py model --mesh MESH/<theme>.json --export EXPORT/<theme> --out assets/gm3d/<theme>.lgm
  python3 bake.py env --export EXPORT/env --out assets/gm3d/env.lge

Needs numpy, Pillow and etcpak (ETC2 / EAC encoder). Textures: maps that are constant (or flat normals) inside the
UV area the mesh really uses are folded into the material; the rest is cropped to that area, scaled to at most
TEXELS_PER_UNIT texels per model unit (the model is 1 unit wide, the menu draws it about 250 px wide), mipmapped
and stored as ETC2 (colour, sRGB where three.js decoded sRGB) or EAC RG11 (normal maps, z rebuilt in the shader).
"""
import argparse, hashlib, json, math, os, struct, sys

import numpy as np
from PIL import Image, ImageDraw

MAGIC_MODEL, MAGIC_ENV, VERSION = b'LGM1', b'LGE1', 1
# GL internal formats (GLES 3.0 core)
GL_COMPRESSED_RG11_EAC = 0x9272
GL_COMPRESSED_RGB8_ETC2 = 0x9274
GL_COMPRESSED_SRGB8_ETC2 = 0x9275
GL_COMPRESSED_RGBA8_ETC2_EAC = 0x9278
GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC = 0x9279
BLOCK_BYTES = {GL_COMPRESSED_RG11_EAC: 16, GL_COMPRESSED_RGB8_ETC2: 8, GL_COMPRESSED_SRGB8_ETC2: 8,
               GL_COMPRESSED_RGBA8_ETC2_EAC: 16, GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC: 16}
# material flags
F_DOUBLE, F_BLEND, F_DEPTH_WRITE, F_ROUGH_MAP, F_METAL_MAP, F_AO_MAP = 1, 2, 4, 8, 16, 32
# draw vertex format flags
V_UV, V_TANGENT = 1, 2
TEXELS_PER_UNIT = 1024
MAX_TEX = 512
MAX_NORMAL = 384                # normal maps: subtle at the menu's size
FLAT_NORMAL_DEV = 0.03          # 99th percentile of |xy - 0.5| * scale inside the used area for a normal map to count as flat
CONST_STD = 0.006               # std of a channel inside the used area for a map to count as constant


def srgb_to_linear(c):
    c = np.maximum(np.asarray(c, np.float64), 0)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


# ---------------------------------------------------------------------------------------------------- mesh input
def load_groups(mesh_json):
    j = json.load(open(mesh_json))
    blob = open(mesh_json[:-5] + '.bin', 'rb').read()
    off, groups = 0, []
    for g in j['groups']:
        nv, ni = g['vcount'], g['icount']
        v = np.frombuffer(blob, np.float32, nv * 12, off).reshape(nv, 12).copy()
        off += nv * 48
        i = np.frombuffer(blob, np.uint32, ni, off).reshape(-1, 3).copy()
        off += ni * 4
        groups.append(dict(g, v=v, tri=i))
    assert off == len(blob), 'mesh blob size'
    return j['meta'], groups


# ---------------------------------------------------------------------------------------------------- textures
def uv_mask(groups, tex, res):
    """Texels of texture {tex} that some triangle using it covers (UV wrapped into 0..1)."""
    w, h = res
    m = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(m)
    for g in groups:
        if tex not in [x['tex'] for x in g['mat']['maps'].values()]:
            continue
        uv = g['v'][:, 6:8]
        for t in g['tri']:
            p = uv[t]
            base = np.floor(p.min(0))
            q = (p - base) * [w, h]
            d.polygon([tuple(x) for x in q], fill=255)
    return np.asarray(m) > 0


def load_tex(export, i):
    return np.asarray(Image.open(os.path.join(export, 'tex_%02d.png' % i)).convert('RGBA')).astype(np.float32) / 255


def texel_density(groups, tex, res):
    """Median texels per model unit of the triangles that use texture {tex} (sqrt of the area ratio)."""
    r = []
    for g in groups:
        if tex not in [x['tex'] for x in g['mat']['maps'].values()]:
            continue
        p, uv = g['v'][:, 0:3], g['v'][:, 6:8] * res
        a, b, c = g['tri'][:, 0], g['tri'][:, 1], g['tri'][:, 2]
        wa = 0.5 * np.linalg.norm(np.cross(p[b] - p[a], p[c] - p[a]), axis=1)
        e1, e2 = uv[b] - uv[a], uv[c] - uv[a]
        ta = 0.5 * np.abs(e1[:, 0] * e2[:, 1] - e1[:, 1] * e2[:, 0])
        ok = wa > 1e-9
        r.append(np.sqrt(ta[ok] / wa[ok]))
    r = np.concatenate(r) if r else np.zeros(0)
    return float(np.median(r)) if len(r) else 0.0


def mips(img):
    """Mip chain (float RGBA, box filter) down to 1 x 1."""
    out = [img]
    while img.shape[0] > 1 or img.shape[1] > 1:
        h, w = img.shape[:2]
        img = _half(img, max(1, w // 2), max(1, h // 2))
        out.append(img)
    return out


def _half(img, nw, nh):
    h, w = img.shape[:2]
    if h > 1 and w > 1 and h % 2 == 0 and w % 2 == 0:
        return img.reshape(nh, 2, nw, 2, 4).mean((1, 3))
    return np.stack([np.asarray(Image.fromarray(img[..., c]).resize((nw, nh), Image.BILINEAR)) for c in range(4)], -1)


def encode_level(img, fmt):
    import etcpak
    h, w = img.shape[:2]
    pw, ph = (w + 3) // 4 * 4, (h + 3) // 4 * 4
    a = np.pad(img, ((0, ph - h), (0, pw - w), (0, 0)), mode='edge')
    u8 = (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)
    if fmt in (GL_COMPRESSED_RGB8_ETC2, GL_COMPRESSED_SRGB8_ETC2):
        u8[..., 3] = 255
        data = etcpak.compress_etc2_rgb(u8.tobytes(), pw, ph)
    elif fmt in (GL_COMPRESSED_RGBA8_ETC2_EAC, GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC):
        data = etcpak.compress_etc2_rgba(u8.tobytes(), pw, ph)
    elif fmt == GL_COMPRESSED_RG11_EAC:
        data = etcpak.compress_eac_rg(u8.tobytes(), pw, ph)
    else:
        raise ValueError(fmt)
    assert len(data) == (pw // 4) * (ph // 4) * BLOCK_BYTES[fmt], (fmt, w, h, len(data))
    return data


# ---------------------------------------------------------------------------------------------------- model
def bake_model(a):
    meta, groups = load_groups(a.mesh)
    export = a.export
    xm = json.load(open(os.path.join(export, 'meta.json')))
    texinfo = xm['textures']
    report = []

    # 1. per material: fold constant maps, drop flat normal maps
    texcache = {}

    def tex(i):
        if i not in texcache:
            texcache[i] = load_tex(export, i)
        return texcache[i]

    masks = {}

    def mask(i):
        if i not in masks:
            t = tex(i)
            masks[i] = uv_mask(groups, i, (t.shape[1], t.shape[0]))
        return masks[i]

    # identical source images (the two Joy-Cons each carry a copy) become one texture
    canon, seen_px = {}, {}
    for i in range(len(texinfo)):
        h = hashlib.sha256(open(os.path.join(export, 'tex_%02d.png' % i), 'rb').read() + texinfo[i]['cs'].encode()).hexdigest()
        canon[i] = seen_px.setdefault(h, i)
    for g in groups:
        for mp in g['mat']['maps'].values():
            mp['tex'] = canon[mp['tex']]
    mats = []
    for g in groups:
        m = json.loads(json.dumps(g['mat']))
        maps = m['maps']
        for k in list(maps):
            if k not in ('map', 'normalMap', 'roughnessMap', 'metalnessMap', 'aoMap'):
                raise SystemExit('unsupported map %s on %s' % (k, m['name']))
            mp = maps[k]
            if mp['channel'] != 0 or mp['repeat'] != [1, 1] or mp['offset'] != [0, 0] or mp['rot'] != 0:
                raise SystemExit('unsupported map transform %s on %s' % (k, m['name']))
        if maps and not g['hasUv']:
            raise SystemExit('maps without UVs on %s' % m['name'])
        for k in list(maps):
            t, mk = tex(maps[k]['tex']), mask(maps[k]['tex'])
            sel = t[mk]
            if k == 'normalMap':
                dev = np.percentile(np.abs(sel[:, :2] - 0.5), 99, axis=0) * np.abs(m['normalScale'])
                if dev.max() <= FLAT_NORMAL_DEV or sel[:, :2].std(0).max() <= CONST_STD:
                    report.append('%s: normal map flat in its area (dev %.3f), dropped' % (m['name'], dev.max()))
                    del maps[k]
            elif k == 'map':
                if sel.std(0).max() <= CONST_STD:
                    c = sel.mean(0)
                    lin = srgb_to_linear(c[:3]) if texinfo[maps[k]['tex']]['cs'] == 'srgb' else c[:3]
                    m['color'] = list(np.asarray(m['color']) * lin)
                    m['opacity'] = m['opacity'] * float(c[3])
                    report.append('%s: base colour map constant %s, folded' % (m['name'], np.round(c, 3)))
                    del maps[k]
            else:
                ch = {'roughnessMap': 1, 'metalnessMap': 2, 'aoMap': 0}[k]
                if sel[:, ch].std() <= CONST_STD:
                    v = float(sel[:, ch].mean())
                    if k == 'roughnessMap':
                        m['roughness'] *= v
                    elif k == 'metalnessMap':
                        m['metalness'] *= v
                    else:
                        m['aoConst'] = (v - 1) * m['aoMapIntensity'] + 1
                    report.append('%s: %s constant %.3f, folded' % (m['name'], k, v))
                    del maps[k]
        orm = {maps[k]['tex'] for k in ('roughnessMap', 'metalnessMap', 'aoMap') if k in maps}
        if len(orm) > 1:
            raise SystemExit('%s: rough/metal/ao maps from different textures' % m['name'])
        mats.append(m)

    # 2. textures still used: clusters that share a draw get one crop box (one UV set per draw)
    used = sorted({mp['tex'] for m in mats for mp in m['maps'].values()})
    parent = {t: t for t in used}

    def find(x):
        while parent[x] != x:
            x = parent[x]
        return x

    for m in mats:
        ts = [mp['tex'] for mp in m['maps'].values()]
        for t in ts[1:]:
            parent[find(t)] = find(ts[0])
    clusters = {}
    for t in used:
        clusters.setdefault(find(t), []).append(t)
    box = {}
    for root, ts in clusters.items():
        lo, hi = np.array([1e9, 1e9]), np.array([-1e9, -1e9])
        for gi, g in enumerate(groups):
            if not any(mp['tex'] in ts for mp in mats[gi]['maps'].values()):
                continue
            uv = g['v'][np.unique(g['tri']), 6:8]
            lo, hi = np.minimum(lo, uv.min(0)), np.maximum(hi, uv.max(0))
        wrapped = lo.min() < 0 or hi.max() > 1
        if wrapped:                           # wrapped UVs: keep the whole texture, repeat
            lo, hi = np.zeros(2), np.ones(2)
        else:                                 # 2 texels of margin on the coarsest texture's grid of the cluster
            res = np.array([min(tex(t).shape[1] for t in ts), min(tex(t).shape[0] for t in ts)], np.float64)
            lo = np.maximum(0, np.floor(lo * res - 2) / res)
            hi = np.minimum(1, np.ceil(hi * res + 2) / res)
        for t in ts:
            box[t] = (lo, hi, wrapped)
    # 3. crop, scale, mip, encode; identical results are stored once
    tex_out, tex_index, stored = [], {}, {}
    for t in used:
        img = tex(t)
        H, W = img.shape[:2]
        lo, hi, wrapped = box[t]
        x0, y0 = int(round(lo[0] * W)), int(round(lo[1] * H))
        x1, y1 = int(round(hi[0] * W)), int(round(hi[1] * H))
        assert abs(x0 - lo[0] * W) < 1e-6 and abs(x1 - hi[0] * W) < 1e-6 and abs(y0 - lo[1] * H) < 1e-6, 'crop grid'
        crop = img[y0:y1, x0:x1]
        dens = texel_density(groups, t, np.array([W, H]))
        roles = {k2 for m in mats for k2, mp in m['maps'].items() if mp['tex'] == t}
        cap = MAX_NORMAL if roles == {'normalMap'} else MAX_TEX
        k = min(1.0, TEXELS_PER_UNIT / max(dens, 1e-6), cap / max(crop.shape[:2]))
        nw, nh = max(4, int(round(crop.shape[1] * k / 4)) * 4), max(4, int(round(crop.shape[0] * k / 4)) * 4)
        if (nw, nh) != (crop.shape[1], crop.shape[0]):
            crop = np.stack([np.asarray(Image.fromarray(crop[..., c]).resize((nw, nh), Image.LANCZOS)) for c in range(4)], -1)
        srgb = texinfo[t]['cs'] == 'srgb'
        blend = any(m['transparent'] for m in mats for mp in m['maps'].values() if mp['tex'] == t and 'map' in m['maps'] and m['maps']['map']['tex'] == t)
        if roles == {'normalMap'}:
            fmt = GL_COMPRESSED_RG11_EAC
        elif roles == {'map'}:
            fmt = (GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC if blend else GL_COMPRESSED_SRGB8_ETC2) if srgb else \
                  (GL_COMPRESSED_RGBA8_ETC2_EAC if blend else GL_COMPRESSED_RGB8_ETC2)
        elif roles <= {'roughnessMap', 'metalnessMap', 'aoMap'}:
            fmt = GL_COMPRESSED_RGB8_ETC2
        else:
            raise SystemExit('texture %d has mixed roles %s' % (t, roles))
        levels = mips(crop)
        if fmt in (GL_COMPRESSED_SRGB8_ETC2, GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC):
            # filter in linear light, store sRGB
            lin = srgb_to_linear(crop[..., :3])
            levels = []
            for lv in mips(np.concatenate([lin, crop[..., 3:]], -1).astype(np.float32)):
                s = np.where(lv[..., :3] <= 0.0031308, lv[..., :3] * 12.92, 1.055 * np.power(np.maximum(lv[..., :3], 0), 1 / 2.4) - 0.055)
                levels.append(np.concatenate([s, lv[..., 3:]], -1))
        data = [encode_level(lv, fmt) for lv in levels]
        key = hashlib.sha256(b''.join(data) + struct.pack('<I', fmt)).hexdigest()
        if key not in stored:
            stored[key] = len(tex_out)
            tex_out.append(dict(fmt=fmt, w=nw, h=nh, levels=data, src=t, dens=dens, flags=1 if wrapped else 0))
            report.append('texture %d: crop %dx%d of %dx%d, density %.0f/unit -> %dx%d %s, %d levels, %d bytes'
                          % (t, x1 - x0, y1 - y0, W, H, dens, nw, nh, hex(fmt), len(data), sum(map(len, data))))
        tex_index[t] = stored[key]

    # 4. draws: opaque first, blended last; vertices quantised
    order = sorted(range(len(groups)), key=lambda i: (bool(mats[i]['transparent']), i))
    allp = np.concatenate([g['v'][:, :3] for g in groups])
    pmin, pmax = allp.min(0), allp.max(0)
    scale = (pmax - pmin) / 2
    offset = (pmax + pmin) / 2
    scale[scale == 0] = 1
    vb, ib, draws = bytearray(), bytearray(), []
    for gi in order:
        g, m = groups[gi], mats[gi]
        v = g['v'].copy()
        has_uv = bool(m['maps'])
        has_tan = g['hasTangent'] and 'normalMap' in m['maps']
        if has_uv:
            t0 = next(iter(m['maps'].values()))['tex']
            lo, hi, _ = box[t0]
            v[:, 6:8] = (v[:, 6:8] - lo) / (hi - lo)
        q = np.clip(np.round((v[:, :3] - offset) / scale * 32767), -32767, 32767).astype(np.int16)
        cols = [np.concatenate([q, np.zeros((len(v), 1), np.int16)], 1).view(np.uint8).reshape(len(v), 8),
                pack1010102(v[:, 3:6], np.zeros(len(v)))]
        if has_uv:
            cols.append(v[:, 6:8].astype(np.float16).view(np.uint8).reshape(len(v), 4))
        if has_tan:
            cols.append(pack1010102(v[:, 8:11], np.sign(v[:, 11]) + (v[:, 11] == 0)))
        vert = np.concatenate(cols, 1)
        it = np.uint16 if len(v) <= 65535 else np.uint32
        idx = g['tri'].astype(it).tobytes()
        while len(vb) % 4: vb.append(0)
        while len(ib) % 4: ib.append(0)
        draws.append(dict(mat=len(draws), fmt=(V_UV if has_uv else 0) | (V_TANGENT if has_tan else 0), vcount=len(v),
                          icount=g['tri'].size, itype=2 if it == np.uint16 else 4, voff=len(vb), vsize=vert.nbytes,
                          ioff=len(ib), isize=len(idx), m=m))
        vb += vert.tobytes()
        ib += idx

    # 5. write
    cfg = xm['cfg']
    out = bytearray()
    lights = xm['lights']
    out += struct.pack('<4sHHIHHHHIII', MAGIC_MODEL, VERSION, 0, 0, len(tex_out), len(draws), len(draws), len(lights), 0, 0, 0)
    tab = xm['disttab']
    out += struct.pack('<7f', cfg['fov'], cfg['elev'], cfg['tilt'], cfg['yaw0'], cfg['W'] / cfg['H'], cfg['exposure'], cfg['env'])
    out += struct.pack('<3f', *xm['center'])
    out += struct.pack('<HHf', len(tab), 0, 360.0 / len(tab)) + struct.pack('<%df' % len(tab), *tab)
    for L in lights:
        out += struct.pack('<6f', *L['dir'], *(np.asarray(L['color']) * L['intensity']))
    out += struct.pack('<6f', *scale, *offset)
    for t in tex_out:
        out += struct.pack('<IHHHH', t['fmt'], len(t['levels']), t['w'], t['h'], t['flags'])
        for lv in t['levels']:
            out += struct.pack('<I', len(lv)) + lv
            while len(out) % 4: out.append(0)
    for d in draws:
        m = d['m']
        flags = (F_DOUBLE if m['side'] == 2 else 0) | (F_BLEND if m['transparent'] else 0) | (F_DEPTH_WRITE if m['depthWrite'] else 0)
        mp = m['maps']
        orm = next((mp[k]['tex'] for k in ('roughnessMap', 'metalnessMap', 'aoMap') if k in mp), None)
        flags |= (F_ROUGH_MAP if 'roughnessMap' in mp else 0) | (F_METAL_MAP if 'metalnessMap' in mp else 0) | (F_AO_MAP if 'aoMap' in mp else 0)
        ti = lambda k: tex_index[mp[k]['tex']] if k in mp else -1
        em = np.asarray(m['emissive']) * m['emissiveIntensity']
        ns = m['normalScale'] or [1, 1]
        ao = m.get('aoConst', 1.0)
        out += struct.pack('<4f3f8f3hH', *m['color'], m['opacity'], *em, m['roughness'], m['metalness'], ns[0], ns[1],
                           m['aoMapIntensity'], ao, m['clearcoat'], m['clearcoatRoughness'],
                           ti('map'), ti('normalMap'), tex_index[orm] if orm is not None else -1, flags)
    for d in draws:
        out += struct.pack('<HHIIIIIII', d['mat'], d['fmt'], d['vcount'], d['icount'], d['itype'], d['voff'], d['vsize'],
                           d['ioff'], d['isize'])
    while len(out) % 4: out.append(0)
    vbase = len(out)
    out += vb
    while len(out) % 4: out.append(0)
    ibase = len(out)
    out += ib
    # patch the header: file size, blob bases
    struct.pack_into('<I', out, 8, len(out))
    struct.pack_into('<II', out, 24, vbase, ibase)
    open(a.out, 'wb').write(out)
    tris = sum(d['icount'] for d in draws) // 3
    verts = sum(d['vcount'] for d in draws)
    report.append('%s: %d draws, %d tris, %d verts, vb %d, ib %d, textures %d bytes, file %d bytes'
                  % (os.path.basename(a.out), len(draws), tris, verts, len(vb), len(ib),
                     sum(sum(map(len, t['levels'])) for t in tex_out), len(out)))
    print('\n'.join(report))
    if a.textures_dir:
        os.makedirs(a.textures_dir, exist_ok=True)
        for i, t in enumerate(tex_out):
            json.dump(dict(fmt=t['fmt'], w=t['w'], h=t['h']), open(os.path.join(a.textures_dir, 't%d.json' % i), 'w'))


def pack1010102(xyz, w):
    xyz = np.clip(np.asarray(xyz, np.float64), -1, 1)
    q = np.round(xyz * 511).astype(np.int64) & 0x3FF
    qw = np.asarray(np.round(w), np.int64) & 0x3
    p = (q[:, 0] | (q[:, 1] << 10) | (q[:, 2] << 20) | (qw << 30)).astype(np.uint32)
    return p.view(np.uint8).reshape(len(p), 4)


# ---------------------------------------------------------------------------------------------------- env
def rgb9e5(rgb):
    N, B, EMAX = 9, 15, 31
    mx = (2 ** N - 1) / 2 ** N * 2 ** (EMAX - B)
    c = np.clip(np.nan_to_num(rgb.astype(np.float64)), 0, mx)
    m = c.max(-1)
    e = np.maximum(-B - 1, np.floor(np.log2(np.maximum(m, 1e-30)))) + 1 + B
    ms = np.floor(m / 2.0 ** (e - B - N) + 0.5)
    e = np.where(ms == 2 ** N, e + 1, e)
    s = np.floor(c / (2.0 ** (e - B - N))[..., None] + 0.5).astype(np.uint32)
    s = np.minimum(s, 511)
    return (s[..., 0] | (s[..., 1] << 9) | (s[..., 2] << 18) | (e.astype(np.uint32) << 27)).astype(np.uint32)


def bake_env(a):
    j = json.load(open(os.path.join(a.export, 'env.json')))
    w, h, rough = j['w'], j['h'], j['rough']
    env = np.fromfile(os.path.join(a.export, 'env.f32'), np.float32).reshape(len(rough), h, w, 3)
    keep = [i for i, r in enumerate(rough) if r > 0]          # PMREM's lowest level starts at 0.05
    r = [rough[i] for i in keep]
    out = bytearray(struct.pack('<4sHHHHI', MAGIC_ENV, VERSION, w, h, len(keep), 0))
    out += struct.pack('<%df' % len(r), *r)
    for i in keep:
        out += rgb9e5(env[i]).tobytes()
    open(a.out, 'wb').write(out)
    print('%s: %dx%d x %d layers (roughness %s), %d bytes' % (os.path.basename(a.out), w, h, len(r), r, len(out)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('what', choices=['model', 'env'])
    ap.add_argument('--mesh')
    ap.add_argument('--export', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--textures-dir')
    a = ap.parse_args()
    (bake_model if a.what == 'model' else bake_env)(a)


if __name__ == '__main__':
    main()
