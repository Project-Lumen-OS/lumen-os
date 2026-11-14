/* Game menu 3D bake, step 1 (tools/gm3d/README.md): the scene of the pre-rendered controller frames, built by the SAME
   code as qp_design/models/render/render.js (load, hide, drop, paint, cap, recolour, normalise, framing), exported for
   the real-time renderer: meshes in the tilt group's space, materials, textures, the framing table and the room
   environment sampled through three.js PMREM. The frame renderer below stays for reference renders.
   Studio turntable renderer for controller models (three.js, WebGL, transparent background).
   CFG (injected): {W,H,N,models:[{url,pos:[x,y,z],rot:[x,y,z] deg,hide:"regex",tint:{matRegex:hex}}],
   accent:"#hex", accent2:"#hex"|null, tilt:deg, elev:deg, yaw0:deg, fov:deg, margin:1.1, exposure:1, env:0.6,
   preview:true -> renderFrame returns a contact sheet frame }  */
(function () {
  const C = window.CFG;
  const log = [];
  window.LOG = log;
  try {
    const canvas = document.getElementById('c');
    canvas.width = C.W; canvas.height = C.H;
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true, premultipliedAlpha: false });
    renderer.setPixelRatio(1);
    renderer.setSize(C.W, C.H, false);
    renderer.setClearColor(0x000000, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE[C.tone || 'ACESFilmicToneMapping'];
    renderer.toneMappingExposure = C.exposure ?? 1.0;
    renderer.shadowMap.enabled = false;

    const scene = new THREE.Scene();
    const pm = new THREE.PMREMGenerator(renderer);
    scene.environment = pm.fromScene(new RoomEnvironment(), 0.03).texture;
    scene.environmentIntensity = C.env ?? 0.55;

    // studio lights: soft key top-left-front, low fill, two accent rims from behind, a cool top kicker
    const add = (col, inten, x, y, z) => { const l = new THREE.DirectionalLight(col, inten); l.position.set(x, y, z); scene.add(l); return l; };
    add(0xffffff, C.key ?? 2.4, -2.2, 3.2, 3.0);
    add(0xdfe6ff, C.fill ?? 0.55, 3.0, 0.6, 2.2);
    const acc = new THREE.Color(C.accent || '#3B78E7');
    const acc2 = new THREE.Color(C.accent2 || C.accent || '#3B78E7');
    add(acc, C.rim ?? 5.0, ...(C.rimPos || [-4.0, 1.3, -1.6]));
    add(acc2, C.rim2 ?? (C.rim ?? 5.0) * 0.85, ...(C.rim2Pos || [4.0, 1.7, -1.3]));
    add(0xffffff, C.top ?? 0.6, 0, 5, -1);

    const pivot = new THREE.Group();   // turntable (yaw)
    const tilt = new THREE.Group();    // product tilt toward camera
    pivot.add(tilt); scene.add(pivot);
    const content = new THREE.Group(); tilt.add(content);

    const camera = new THREE.PerspectiveCamera(C.fov ?? 24, C.W / C.H, 0.01, 100);
    const loader = new GLTFLoader();
    let pending = C.models.length;
    const deg = Math.PI / 180;

    C.models.forEach((m, mi) => {
      loader.load(m.url, (g) => {
        const root = g.scene;
        const hide = m.hide ? new RegExp(m.hide, 'i') : null;
        const keep = m.keep ? new RegExp(m.keep, 'i') : null;
        root.updateMatrixWorld(true);
        // drop triangles whose centroid lies in a box (glTF world coords), e.g. a logo merged into a d-pad mesh
        if (m.drop) root.traverse((o) => {
          if (!o.isMesh) return;
          for (const d of m.drop) {
            if (!new RegExp(d.obj).test(o.name)) continue;
            let g = o.geometry; if (g.index) g = g.toNonIndexed();
            const P = g.attributes.position, keepIdx = [], v = new THREE.Vector3(), c = new THREE.Vector3();
            const [a, b] = d.box;
            for (let t = 0; t < P.count; t += 3) {
              c.set(0, 0, 0);
              for (let k = 0; k < 3; k++) { v.fromBufferAttribute(P, t + k).applyMatrix4(o.matrixWorld); c.add(v); }
              c.multiplyScalar(1 / 3);
              const inside = c.x >= a[0] && c.x <= b[0] && c.y >= a[1] && c.y <= b[1] && c.z >= a[2] && c.z <= b[2];
              if (!inside) keepIdx.push(t, t + 1, t + 2);
            }
            g.setIndex(keepIdx); o.geometry = g; log.push(`drop ${o.name} ${P.count / 3} -> ${keepIdx.length / 3}`);
          }
        });
        // paint: move triangles of a mesh whose centroid is in a box into a new mesh with its own colour
        if (m.paint) {
          const adds = [];
          root.traverse((o) => {
            if (!o.isMesh) return;
            for (const d of m.paint) {
              if (!new RegExp(d.obj).test(o.name)) continue;
              let g = o.geometry; if (g.index) g = g.toNonIndexed();
              const P = g.attributes.position, inIdx = [], outIdx = [], v = new THREE.Vector3(), c = new THREE.Vector3();
              const [a, b] = d.box;
              for (let t = 0; t < P.count; t += 3) {
                c.set(0, 0, 0);
                for (let k = 0; k < 3; k++) { v.fromBufferAttribute(P, t + k).applyMatrix4(o.matrixWorld); c.add(v); }
                c.multiplyScalar(1 / 3);
                const inside = c.x >= a[0] && c.x <= b[0] && c.y >= a[1] && c.y <= b[1] && c.z >= a[2] && c.z <= b[2];
                (inside ? inIdx : outIdx).push(t, t + 1, t + 2);
              }
              const g2 = g.clone(); g2.setIndex(inIdx); g.setIndex(outIdx); o.geometry = g;
              const mat = (Array.isArray(o.material) ? o.material[0] : o.material).clone();
              mat.color = new THREE.Color(d.color); mat.map = null; if (d.rough !== undefined) { mat.roughness = d.rough; mat.roughnessMap = null; }
              const mesh = new THREE.Mesh(g2, mat); mesh.name = o.name + '-paint';
              adds.push([o, mesh]); log.push(`paint ${o.name} ${inIdx.length / 3} tris ${d.color}`);
            }
          });
          for (const [o, mesh] of adds) { o.parent.add(mesh); mesh.position.copy(o.position); mesh.quaternion.copy(o.quaternion); mesh.scale.copy(o.scale); }
          root.updateMatrixWorld(true);
        }
        // caps: a plain round button over the area of matching meshes (replaces a brand logo button)
        if (m.cap) for (const cp of m.cap) {
          const re = new RegExp(cp.of), box = new THREE.Box3(), nsum = new THREE.Vector3();
          const A = new THREE.Vector3(), B = new THREE.Vector3(), Cc = new THREE.Vector3();
          root.traverse((o) => {
            if (!o.isMesh || !re.test(o.name)) return;
            const g = o.geometry, P = g.attributes.position, I = g.index;
            const n = I ? I.count : P.count;
            for (let t = 0; t < n; t += 3) {
              const ia = I ? I.getX(t) : t, ib = I ? I.getX(t + 1) : t + 1, ic = I ? I.getX(t + 2) : t + 2;
              A.fromBufferAttribute(P, ia).applyMatrix4(o.matrixWorld); B.fromBufferAttribute(P, ib).applyMatrix4(o.matrixWorld); Cc.fromBufferAttribute(P, ic).applyMatrix4(o.matrixWorld);
              box.expandByPoint(A); box.expandByPoint(B); box.expandByPoint(Cc);
              nsum.add(new THREE.Vector3().subVectors(B, A).cross(new THREE.Vector3().subVectors(Cc, A)));
            }
          });
          const nrm = cp.n ? new THREE.Vector3(...cp.n).normalize() : nsum.normalize(); if (cp.flip) nrm.negate();
          const sz = box.getSize(new THREE.Vector3()), ctr = cp.at ? new THREE.Vector3(...cp.at) : box.getCenter(new THREE.Vector3());
          // radius = extent across the plane perpendicular to the normal
          const ext = cp.r ? cp.r * 2 : Math.max(sz.x * Math.sqrt(1 - nrm.x * nrm.x), sz.y * Math.sqrt(1 - nrm.y * nrm.y), sz.z * Math.sqrt(1 - nrm.z * nrm.z));
          const r = ext / 2 * (cp.scale ?? 1.1), h = r * (cp.h ?? 0.25);
          const geo = new THREE.CylinderGeometry(r, r * 0.96, h, 48, 1);
          const mat = new THREE.MeshPhysicalMaterial({ color: cp.color || '#111214', roughness: cp.rough ?? 0.35, metalness: 0, clearcoat: cp.clearcoat ?? 0.6, clearcoatRoughness: 0.25 });
          const mesh = new THREE.Mesh(geo, mat);
          mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), nrm);
          mesh.position.copy(ctr).addScaledVector(nrm, (cp.off ?? 0) * r);
          mesh.name = 'lumen-cap'; root.add(mesh); mesh.updateMatrixWorld(true);
          log.push(`cap ${cp.of} r=${r.toFixed(3)} n=${nrm.toArray().map(x => x.toFixed(2))}`);
        }
        root.traverse((o) => {
          if (!o.isMesh) return;
          const mats = Array.isArray(o.material) ? o.material : [o.material];
          const names = [o.name, o.parent && o.parent.name, ...mats.map((x) => x && x.name)].join(' ');
          if (hide && hide.test(names)) { o.visible = false; log.push('hide ' + names); return; }
          if (keep && !keep.test(names)) { o.visible = false; return; }
          for (const mat of mats) {
            if (!mat) continue;
            if (m.tint) for (const [re, hex] of Object.entries(m.tint)) {
              if (new RegExp(re, 'i').test(mat.name + ' ' + o.name)) { mat.color = new THREE.Color(hex); if (m.tintNoMap) mat.map = null; mat.needsUpdate = true; }
            }
            if (m.mat) for (const r of m.mat) {
              if (r.obj) { if (!new RegExp(r.obj).test(o.name)) continue; }
              else if (!new RegExp(r.re, 'i').test(mat.name + ' ' + o.name)) continue;
              if (r.obj && !o.userData.cloned) { const cm = mat.clone(); o.material = cm; o.userData.cloned = true; applyRule(cm, r); continue; }
              applyRule(mat, r);
            }
            function applyRule(mat, r) {
              if (r.color) mat.color = new THREE.Color(r.color);
              if (r.nomap) mat.map = null;
              if (r.noemit && mat.emissive) { mat.emissive.setRGB(0, 0, 0); mat.emissiveMap = null; }
              if (r.emit) { mat.emissive = new THREE.Color(r.emit); mat.emissiveIntensity = r.emitI ?? 1; mat.emissiveMap = null; }
              if (r.rough !== undefined) { mat.roughness = r.rough; mat.roughnessMap = null; }
              if (r.metal !== undefined) { mat.metalness = r.metal; mat.metalnessMap = null; }
              if (r.nonormal) mat.normalMap = null;
              mat.needsUpdate = true;
            }
            if (m.rough) for (const [re, v] of Object.entries(m.rough)) if (new RegExp(re, 'i').test(mat.name + ' ' + o.name)) { mat.roughness = v; mat.roughnessMap = null; mat.metalness = 0; mat.metalnessMap = null; }
            if (m.noEmissive && mat.emissive) { mat.emissive.setRGB(0, 0, 0); mat.emissiveMap = null; }
            if (mat.map) mat.map.anisotropy = 8;
          }
          if (m.clip) {  // keep only triangles fully inside a local-space box (drops e.g. a cable)
            const g = o.geometry, P = g.attributes.position, I = g.index;
            const [a, b] = m.clip, inb = (k) => { const x = P.getX(k), y = P.getY(k), z = P.getZ(k); return x >= a[0] && x <= b[0] && y >= a[1] && y <= b[1] && z >= a[2] && z <= b[2]; };
            const src = I ? I.array : [...Array(P.count).keys()], out = [];
            for (let t = 0; t < src.length; t += 3) if (inb(src[t]) && inb(src[t + 1]) && inb(src[t + 2])) out.push(src[t], src[t + 1], src[t + 2]);
            g.setIndex(out); o.geometry = g.toNonIndexed(); o.geometry.computeBoundingBox(); log.push(`clip ${src.length / 3} -> ${out.length / 3}`);
          }
        });
        const holder = new THREE.Group();
        const r = m.rot || [0, 0, 0];
        root.rotation.set(r[0] * deg, r[1] * deg, r[2] * deg);
        holder.add(root);
        // per-model normalise to unit width so pairs can be laid out in model units
        root.updateMatrixWorld(true);
        const b = new THREE.Box3().setFromObject(root, true);
        const sz = b.getSize(new THREE.Vector3()), ct = b.getCenter(new THREE.Vector3());
        const s = (m.scale ?? 1) / Math.max(sz.x, 1e-6);
        root.position.sub(ct); holder.scale.setScalar(s);
        const p = m.pos || [0, 0, 0]; holder.position.set(p[0], p[1], p[2]);
        content.add(holder);
        log.push(`model ${mi} size ${sz.x.toFixed(3)} ${sz.y.toFixed(3)} ${sz.z.toFixed(3)}`);
        if (--pending === 0) finish();
      }, undefined, (e) => { window.ERR = 'load ' + m.url + ' ' + (e && e.message); });
    });

    function finish() {
      content.updateMatrixWorld(true);
      const b = new THREE.Box3().setFromObject(content, true);
      const ct = b.getCenter(new THREE.Vector3());
      content.position.sub(ct);
      tilt.rotation.x = (C.tilt ?? 0) * deg;
      tilt.updateMatrixWorld(true);
      const el = (C.elev ?? 12) * deg;
      const vf = (C.fov ?? 24) * deg;
      // tight framing over every turntable angle: sample vertices, binary-search camera distance
      const pts = [];
      content.traverse((o) => {
        if (!o.isMesh || !o.visible) return;
        let vis = true; for (let p = o; p; p = p.parent) if (p.visible === false) vis = false; if (!vis) return;
        const P = o.geometry.attributes.position, step = Math.max(1, Math.floor(P.count / 20000));
        for (let k = 0; k < P.count; k += step) pts.push(new THREE.Vector3().fromBufferAttribute(P, k).applyMatrix4(o.matrixWorld));
      });
      if (pts.length > 30000) { const st = pts.length / 30000; const sub = []; for (let f = 0; f < pts.length; f += st) sub.push(pts[Math.floor(f)]); pts.length = 0; pts.push(...sub); }
      const deg0 = (C.yaw0 ?? 0) * deg, M = 1 / (C.margin ?? 1.06);
      const angleOf = (i) => C.angles ? deg0 + C.angles[i] * deg : deg0 + (C.dir ?? 1) * i * 2 * Math.PI / C.N;
      const place = (dist) => {
        camera.position.set(0, Math.sin(el) * dist, Math.cos(el) * dist); camera.lookAt(0, 0, 0);
        camera.near = dist / 50; camera.far = dist * 4; camera.updateProjectionMatrix(); camera.updateMatrixWorld(true);
      };
      const q = new THREE.Quaternion(), ax = new THREE.Vector3(0, 1, 0), v = new THREE.Vector3();
      // NDC bounds of the model at the given yaw angles and camera distance
      const bounds = (dist, angs) => {
        place(dist); let x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
        for (const a of angs) { q.setFromAxisAngle(ax, a);
          for (const p of pts) { v.copy(p).applyQuaternion(q).project(camera); x0 = Math.min(x0, v.x); x1 = Math.max(x1, v.x); y0 = Math.min(y0, v.y); y1 = Math.max(y1, v.y); } }
        return [x0, x1, y0, y1];
      };
      const sph = new THREE.Box3().setFromObject(content, true).getBoundingSphere(new THREE.Sphere());
      const search = (ok) => { let lo = sph.radius * 1.01, hi = sph.radius / Math.sin(vf / 2) * 3;
        for (let it = 0; it < 24; it++) { const mid = (lo + hi) / 2; if (ok(mid)) hi = mid; else lo = mid; } return hi; };
      // hero pose (+- sway) is centred and fills the frame; every other frame keeps that centre and backs off only as needed
      const sway = C.sway ?? 12, heroAngs = [-sway, -sway / 2, 0, sway / 2, sway].map((d) => deg0 + d * deg);
      const dHero = search((d) => { const [x0, x1, y0, y1] = bounds(d, heroAngs); return (x1 - x0) / 2 <= M && (y1 - y0) / 2 <= M; });
      const hb = bounds(dHero, heroAngs), cx = (hb[0] + hb[1]) / 2, cy = (hb[2] + hb[3]) / 2;
      const fitsAt = (d, angs) => { const [x0, x1, y0, y1] = bounds(d, angs);
        const k = dHero / d;  // centre shift scales with distance (perspective, near enough)
        return Math.max(Math.abs(x0 - cx * k), Math.abs(x1 - cx * k)) <= M && Math.max(Math.abs(y0 - cy * k), Math.abs(y1 - cy * k)) <= M; };
      const N = C.angles ? C.angles.length : C.N;
      const raw = [];
      for (let i = 0; i < N; i++) { const a = angleOf(i); raw.push(Math.max(dHero, search((d) => fitsAt(d, [a - 4 * deg, a, a + 4 * deg])))); }
      // smooth: max over neighbours so the dolly has no steps
      window.DIST = raw;
      window.CENTER = [cx, cy, dHero];
      log.push(`dHero ${dHero.toFixed(3)} c ${cx.toFixed(3)},${cy.toFixed(3)} dist ${window.DIST.map((x) => (x / dHero).toFixed(2)).join(' ')}`);
      // framing table for the real-time turn: the same fit as DIST (window a-4..a+4 deg) every 3 deg from yaw0
      window.DISTTAB = [];
      for (let j = 0; j < 120; j++) { const a = deg0 + j * 3 * deg; window.DISTTAB.push(Math.max(dHero, search((d) => fitsAt(d, [a - 4 * deg, a, a + 4 * deg])))); }
      window.READY = true;
    }


    // ================================================================== export
    const visibleChain = (o) => { for (let p = o; p; p = p.parent) if (p.visible === false) return false; return true; };
    const texIds = new Map(), texList = [];
    const texRef = (t) => {
      if (!t) return -1;
      const key = t.source.uuid;
      if (!texIds.has(key)) { texIds.set(key, texList.length); texList.push(t); }
      return texIds.get(key);
    };
    const lin = (c) => c ? [c.r, c.g, c.b] : null;
    let parts = null;
    const collect = () => {
      if (parts) return parts;
      parts = [];
      scene.updateMatrixWorld(true);
      const tiltInv = new THREE.Matrix4().copy(tilt.matrixWorld).invert();
      content.traverse((o) => {
        if (!o.isMesh || !visibleChain(o)) return;
        const mats = Array.isArray(o.material) ? o.material : [o.material];
        if (mats.length !== 1) { log.push('multi-material mesh ' + o.name + ' (first used)'); }
        const m = mats[0];
        const M = new THREE.Matrix4().multiplyMatrices(tiltInv, o.matrixWorld);
        const N = new THREE.Matrix3().getNormalMatrix(M);
        const g = o.geometry, P = g.attributes.position, Nn = g.attributes.normal, U = g.attributes.uv, T = g.attributes.tangent;
        const n = P.count, v = new THREE.Vector3(), t4 = new THREE.Vector4();
        const pos = new Float32Array(n * 3), nor = new Float32Array(n * 3), uv = U ? new Float32Array(n * 2) : null, tan = T ? new Float32Array(n * 4) : null;
        for (let k = 0; k < n; k++) {
          v.fromBufferAttribute(P, k).applyMatrix4(M); pos.set([v.x, v.y, v.z], k * 3);
          if (Nn) { v.fromBufferAttribute(Nn, k).applyMatrix3(N).normalize(); nor.set([v.x, v.y, v.z], k * 3); }
          if (U) { uv[k * 2] = U.getX(k); uv[k * 2 + 1] = U.getY(k); }
          if (T) { v.set(T.getX(k), T.getY(k), T.getZ(k)).transformDirection(M); tan.set([v.x, v.y, v.z, T.getW(k)], k * 4); }
        }
        let idx;
        if (g.index) idx = Uint32Array.from(g.index.array); else { idx = new Uint32Array(n); for (let k = 0; k < n; k++) idx[k] = k; }
        if (M.determinant() < 0) for (let k = 0; k < idx.length; k += 3) { const s = idx[k + 1]; idx[k + 1] = idx[k + 2]; idx[k + 2] = s; }
        const maps = {};
        for (const key of ['map', 'normalMap', 'roughnessMap', 'metalnessMap', 'aoMap', 'emissiveMap', 'alphaMap', 'clearcoatMap', 'clearcoatNormalMap', 'clearcoatRoughnessMap', 'bumpMap', 'lightMap', 'specularIntensityMap', 'specularColorMap', 'sheenColorMap', 'transmissionMap']) {
          if (m[key]) maps[key] = { tex: texRef(m[key]), channel: m[key].channel || 0, cs: m[key].colorSpace, repeat: [m[key].repeat.x, m[key].repeat.y], offset: [m[key].offset.x, m[key].offset.y], rot: m[key].rotation, flipY: m[key].flipY, wrap: [m[key].wrapS, m[key].wrapT] };
        }
        parts.push({
          name: o.name, n, nidx: idx.length, hasNormal: !!Nn, hasUv: !!U, hasTangent: !!T, det: M.determinant(),
          mat: { uuid: m.uuid, name: m.name, type: m.type, color: lin(m.color), opacity: m.opacity, transparent: m.transparent,
            depthWrite: m.depthWrite, depthTest: m.depthTest, side: m.side, alphaTest: m.alphaTest, blending: m.blending,
            roughness: m.roughness, metalness: m.metalness, emissive: lin(m.emissive), emissiveIntensity: m.emissiveIntensity,
            normalScale: m.normalScale ? [m.normalScale.x, m.normalScale.y] : null, aoMapIntensity: m.aoMapIntensity,
            clearcoat: m.clearcoat || 0, clearcoatRoughness: m.clearcoatRoughness || 0, ior: m.ior, specularIntensity: m.specularIntensity,
            specularColor: lin(m.specularColor), sheen: m.sheen || 0, transmission: m.transmission || 0, iridescence: m.iridescence || 0,
            envMapIntensity: m.envMapIntensity, flatShading: m.flatShading, vertexColors: m.vertexColors, toneMapped: m.toneMapped, maps },
          data: { pos, nor, uv, tan, idx },
        });
      });
      return parts;
    };
    const b64 = (u8) => { let s = ''; const CH = 0x8000; for (let i = 0; i < u8.length; i += CH) s += String.fromCharCode.apply(null, u8.subarray(i, i + CH)); return btoa(s); };
    window.exportMeta = () => {
      const ps = collect();
      const lights = [];
      scene.traverse((o) => { if (o.isDirectionalLight) { const d = new THREE.Vector3().subVectors(o.position, o.target.position).normalize(); lights.push({ color: lin(o.color), intensity: o.intensity, dir: [d.x, d.y, d.z] }); } });
      return JSON.stringify({
        cfg: { W: C.W, H: C.H, fov: C.fov ?? 24, elev: C.elev ?? 12, tilt: C.tilt ?? 0, yaw0: C.yaw0 ?? 0, margin: C.margin ?? 1.06,
          exposure: renderer.toneMappingExposure, tone: C.tone || 'ACESFilmicToneMapping', env: scene.environmentIntensity, N: C.N },
        center: window.CENTER, dist: window.DIST, disttab: window.DISTTAB, lights, log,
        parts: ps.map((p) => ({ name: p.name, n: p.n, nidx: p.nidx, hasNormal: p.hasNormal, hasUv: p.hasUv, hasTangent: p.hasTangent, det: p.det, mat: p.mat })),
        textures: texList.map((t) => ({ w: t.image.width, h: t.image.height, cs: t.colorSpace, flipY: t.flipY, wrap: [t.wrapS, t.wrapT], name: t.name })),
      });
    };
    // one part as float32 pos | nor | uv | tan, then uint32 indices
    window.exportPart = (i) => {
      const d = collect()[i].data;
      const fl = [d.pos, d.nor, d.uv, d.tan].filter(Boolean);
      const nf = fl.reduce((s, a) => s + a.length, 0);
      const out = new Uint8Array(nf * 4 + d.idx.length * 4);
      let o = 0;
      for (const a of fl) { out.set(new Uint8Array(a.buffer, a.byteOffset, a.byteLength), o); o += a.byteLength; }
      out.set(new Uint8Array(d.idx.buffer, d.idx.byteOffset, d.idx.byteLength), o);
      return b64(out);
    };
    window.exportTex = (i) => {
      const t = texList[i], im = t.image, cv = document.createElement('canvas');
      cv.width = im.width; cv.height = im.height;
      const cx = cv.getContext('2d'); cx.drawImage(im, 0, 0);
      return cv.toDataURL('image/png');
    };
    // the room environment as three.js samples it (textureCubeUV of the PMREM texture) on an equirect grid:
    // u = atan(x, z) / 2pi + 0.5, v = acos(y) / pi (row 0 = up), one float32 RGB layer per roughness
    window.exportEnv = (w, h, roughList) => {
      const envTex = scene.environment, H = envTex.image.height;
      const maxMip = Math.log2(H) - 2, texH = 1.0 / H, texW = 1.0 / (3 * Math.max(Math.pow(2, maxMip), 7 * 16));
      const mat = new THREE.ShaderMaterial({
        defines: { ENVMAP_TYPE_CUBE_UV: '', CUBEUV_TEXEL_WIDTH: texW, CUBEUV_TEXEL_HEIGHT: texH, CUBEUV_MAX_MIP: maxMip.toFixed(1) },
        uniforms: { envMap: { value: envTex }, rough: { value: 1 } },
        vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }',
        fragmentShader: '#include <common>\nuniform sampler2D envMap; uniform float rough; varying vec2 vUv;\n#include <cube_uv_reflection_fragment>\n' +
          'void main(){ float ph = (vUv.x - 0.5) * 2.0 * PI; float th = (1.0 - vUv.y) * PI;\n' +
          ' vec3 d = vec3(sin(th) * sin(ph), cos(th), sin(th) * cos(ph));\n' +
          ' gl_FragColor = vec4(textureCubeUV(envMap, d, rough).rgb, 1.0); }',
        depthTest: false, depthWrite: false,
      });
      const quad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat), qs = new THREE.Scene(); qs.add(quad);
      const cam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
      const rt = new THREE.WebGLRenderTarget(w, h, { type: THREE.FloatType, format: THREE.RGBAFormat, depthBuffer: false });
      const out = new Float32Array(roughList.length * w * h * 3), px = new Float32Array(w * h * 4);
      const tm = renderer.toneMapping; renderer.toneMapping = THREE.NoToneMapping;
      const ocs = renderer.outputColorSpace;
      roughList.forEach((r, li) => {
        mat.uniforms.rough.value = r;
        renderer.setRenderTarget(rt); renderer.render(qs, cam);
        renderer.readRenderTargetPixels(rt, 0, 0, w, h, px);
        // readPixels row 0 is the bottom (vUv.y = 0, th = pi): flip so row 0 is up
        for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
          const s = ((h - 1 - y) * w + x) * 4, d = ((li * h + y) * w + x) * 3;
          out[d] = px[s]; out[d + 1] = px[s + 1]; out[d + 2] = px[s + 2];
        }
      });
      renderer.setRenderTarget(null); renderer.toneMapping = tm; renderer.outputColorSpace = ocs;
      return b64(new Uint8Array(out.buffer));
    };
    window.renderFrame = (i) => {
      const a = C.angles ? (C.yaw0 ?? 0) * deg + C.angles[i] * deg : (C.yaw0 ?? 0) * deg + (C.dir ?? 1) * i * 2 * Math.PI / C.N;
      pivot.rotation.y = a;
      const d = window.DIST[i], [cx, cy, dH] = window.CENTER, k = dH / d;
      camera.position.set(0, Math.sin((C.elev ?? 12) * deg) * d, Math.cos((C.elev ?? 12) * deg) * d); camera.lookAt(0, 0, 0);
      camera.near = d / 50; camera.far = d * 4;
      // shift the image so the hero centre sits in the middle (NDC shift -> pixel view offset)
      camera.setViewOffset(C.W, C.H, cx * k * C.W / 2, -cy * k * C.H / 2, C.W, C.H);
      camera.updateProjectionMatrix();
      renderer.render(scene, camera);
      return canvas.toDataURL('image/png');
    };
  } catch (e) { window.ERR = String(e && e.stack || e); }
})();
