// Game menu 3D bake, step 2 (tools/gm3d/README.md): merge the exported parts per material, weld, simplify with
// meshoptimizer (absolute error in model units, borders locked, normals weighted), reorder for the vertex cache.
//   node bake_mesh.mjs EXPORT/<theme> OUT.json ERROR MESHOPT_DIR
// OUT.json lists the groups; OUT.bin holds per group float32 [pos3 nor3 uv2 tan4] x vcount then uint32 indices.
import fs from 'node:fs';
import path from 'node:path';

const [dir, outJson, errArg, mdir] = process.argv.slice(2);
const { MeshoptSimplifier, MeshoptEncoder } = await import(path.join(mdir, 'node_modules/meshoptimizer/index.module.js'));
await MeshoptSimplifier.ready;
await MeshoptEncoder.ready;
MeshoptSimplifier.useExperimentalFeatures = true;
const ERR = parseFloat(errArg);
const meta = JSON.parse(fs.readFileSync(path.join(dir, 'meta.json'), 'utf8'));
const groups = new Map();
meta.parts.forEach((p, i) => {
  const b = fs.readFileSync(path.join(dir, `part_${String(i).padStart(3, '0')}.bin`));
  const f = new Float32Array(b.buffer, b.byteOffset, (b.length - p.nidx * 4) / 4);
  const idx = new Uint32Array(b.buffer.slice(b.byteOffset + f.byteLength, b.byteOffset + b.length));
  const n = p.n;
  let o = 0;
  const pos = f.subarray(o, o += n * 3), nor = f.subarray(o, o += n * 3);
  const uv = p.hasUv ? f.subarray(o, o += n * 2) : null, tan = p.hasTangent ? f.subarray(o, o += n * 4) : null;
  const key = `${p.mat.uuid}|${p.hasUv ? 1 : 0}|${p.hasTangent ? 1 : 0}`;
  if (!groups.has(key)) groups.set(key, { key, mat: p.mat, hasUv: p.hasUv, hasTangent: p.hasTangent, parts: [] });
  groups.get(key).parts.push({ name: p.name, n, pos, nor, uv, tan, idx });
});
const S = 12;
const out = [], bins = [];
let totalIn = 0, totalOut = 0;
for (const g of groups.values()) {
  // merge + weld identical vertices (exact float bits of every attribute)
  const verts = [], map = new Map(), idx = [];
  for (const p of g.parts) {
    const local = new Uint32Array(p.n);
    for (let k = 0; k < p.n; k++) {
      const v = new Float32Array(S);
      v.set(p.pos.subarray(k * 3, k * 3 + 3), 0);
      v.set(p.nor.subarray(k * 3, k * 3 + 3), 3);
      if (p.uv) v.set(p.uv.subarray(k * 2, k * 2 + 2), 6);
      if (p.tan) v.set(p.tan.subarray(k * 4, k * 4 + 4), 8);
      const h = Buffer.from(v.buffer).toString('base64');
      let id = map.get(h);
      if (id === undefined) { id = verts.length; verts.push(v); map.set(h, id); }
      local[k] = id;
    }
    for (const i of p.idx) idx.push(local[i]);
  }
  // drop degenerate triangles (welding can make them)
  const tri = [];
  for (let t = 0; t < idx.length; t += 3) if (idx[t] !== idx[t + 1] && idx[t + 1] !== idx[t + 2] && idx[t] !== idx[t + 2]) tri.push(idx[t], idx[t + 1], idx[t + 2]);
  const nv = verts.length, all = new Float32Array(nv * S);
  verts.forEach((v, k) => all.set(v, k * S));
  const pos = new Float32Array(nv * 3), nrm = new Float32Array(nv * 3);
  for (let k = 0; k < nv; k++) { pos.set(all.subarray(k * S, k * S + 3), k * 3); nrm.set(all.subarray(k * S + 3, k * S + 6), k * 3); }
  const inIdx = Uint32Array.from(tri);
  let simp = inIdx, err = 0;
  if (ERR > 0 && inIdx.length > 300) {
    [simp, err] = MeshoptSimplifier.simplifyWithAttributes(inIdx, pos, 3, nrm, 3, [0.5, 0.5, 0.5], null, 3, ERR, ['LockBorder', 'ErrorAbsolute']);
    if (simp.length < 3) { simp = inIdx; err = 0; }
  }
  const ind = Uint32Array.from(simp);
  const [remap, unique] = MeshoptEncoder.reorderMesh(ind, true, false);
  const vb = new Float32Array(unique * S);
  // remap covers vertices 0..max index of the simplified mesh; later ones are unused
  for (let k = 0; k < remap.length; k++) if (remap[k] !== 0xffffffff) vb.set(all.subarray(k * S, k * S + S), remap[k] * S);
  totalIn += inIdx.length / 3; totalOut += ind.length / 3;
  out.push({ key: g.key, mat: g.mat, hasUv: g.hasUv, hasTangent: g.hasTangent, parts: g.parts.map((p) => p.name),
    vcount: unique, icount: ind.length, trisIn: inIdx.length / 3, error: err });
  bins.push(Buffer.from(vb.buffer), Buffer.from(ind.buffer));
}
fs.writeFileSync(outJson, JSON.stringify({ meta: { ...meta, parts: undefined }, groups: out }, null, 1));
fs.writeFileSync(outJson.replace(/\.json$/, '.bin'), Buffer.concat(bins));
console.log(`${path.basename(dir)}: ${groups.size} groups, ${totalIn} -> ${totalOut} triangles`);
