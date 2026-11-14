/* Game menu 3D preview (tools/gm3d/README.md): renders a baked controller (.lgm + env.lge) with the device shaders
   (assets/gm3d/model.vert / model.frag) in WebGL2, through the same pipeline as Gm3dRenderer (MSAA 4x at SS x the
   size, resolve, linear 2:1 blit), at the yaw angles of the frame strip, for side-by-side checks with the frames.
   CFG (injected): {lgm, env, vert, frag, W, H, SS, poses: [[yawDeg, bob], ...]}; renderFrame(i) -> PNG data URL. */
(function () {
  const C = window.CFG;
  window.LOG = [];
  const fail = (e) => { window.ERR = String(e && e.stack || e); };
  const get = (u, type) => new Promise((ok, no) => {
    const x = new XMLHttpRequest(); x.open('GET', u); x.responseType = type;
    x.onload = () => ok(x.response); x.onerror = () => no(new Error('load ' + u)); x.send();
  });
  Promise.all([get(C.lgm, 'arraybuffer'), get(C.env, 'arraybuffer'), get(C.vert, 'text'), get(C.frag, 'text')]).then(([lgm, env, vs, fs]) => {
    try { start(lgm, env, vs, fs); } catch (e) { fail(e); }
  }, fail);

  function parse(buf) {
    const v = new DataView(buf); let o = 0;
    const u16 = () => { const r = v.getUint16(o, true); o += 2; return r; };
    const i16 = () => { const r = v.getInt16(o, true); o += 2; return r; };
    const u32 = () => { const r = v.getUint32(o, true); o += 4; return r; };
    const f32 = () => { const r = v.getFloat32(o, true); o += 4; return r; };
    if (u32() !== 0x314D474C) throw new Error('magic');
    u16(); u16(); u32();
    const nt = u16(), nm = u16(), nd = u16(), nl = u16(); u32();
    const vbase = u32(), ibase = u32();
    const s = { fov: f32(), elev: f32(), tilt: f32(), yaw0: f32(), aspect: f32(), exposure: f32(), envI: f32(), cx: f32(), cy: f32(), dHero: f32() };
    const n = u16(); u16(); s.step = f32(); s.dist = []; for (let i = 0; i < n; i++) s.dist.push(f32());
    s.lights = []; for (let i = 0; i < nl; i++) s.lights.push({ dir: [f32(), f32(), f32()], col: [f32(), f32(), f32()] });
    s.scale = [f32(), f32(), f32()]; s.offset = [f32(), f32(), f32()];
    const tex = [];
    for (let i = 0; i < nt; i++) {
      const t = { fmt: u32(), levels: [] }; const nlv = u16(); t.w = u16(); t.h = u16(); t.flags = u16();
      for (let l = 0; l < nlv; l++) { const sz = u32(); t.levels.push(new Uint8Array(buf, o, sz)); o += sz; while (o % 4) o++; }
      tex.push(t);
    }
    const mats = [];
    for (let i = 0; i < nm; i++) {
      const m = { base: [f32(), f32(), f32(), f32()], em: [f32(), f32(), f32()], rough: f32(), metal: f32(), ns: [f32(), f32()], aoI: f32(), aoC: f32(), cc: f32(), ccR: f32() };
      m.baseTex = i16(); m.normalTex = i16(); m.ormTex = i16(); m.flags = u16(); mats.push(m);
    }
    const draws = [];
    for (let i = 0; i < nd; i++) {
      const d = { mat: u16(), fmt: u16(), vc: u32(), ic: u32(), is: u32(), voff: u32(), vsize: u32(), ioff: u32(), isize: u32() };
      d.vb = new Uint8Array(buf, vbase + d.voff, d.vsize); d.ib = new Uint8Array(buf, ibase + d.ioff, d.isize); draws.push(d);
    }
    return { s, tex, mats, draws };
  }

  function parseEnv(buf) {
    const v = new DataView(buf);
    if (v.getUint32(0, true) !== 0x3145474C) throw new Error('env magic');
    const w = v.getUint16(6, true), h = v.getUint16(8, true), n = v.getUint16(10, true);
    const r = []; for (let i = 0; i < n; i++) r.push(v.getFloat32(16 + 4 * i, true));
    return { w, h, n, rough: r, data: new Uint32Array(buf, 16 + 4 * n, w * h * n) };
  }

  // ---- the camera of Gm3dMotion (column-major)
  const mul = (a, b) => { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let x = 0; for (let k = 0; k < 4; k++) x += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = x; } return o; };
  function distAt(s, yaw) { const n = s.dist.length; let a = yaw / s.step; a = ((a % n) + n) % n; const i = Math.floor(a), f = a - i; return s.dist[i % n] * (1 - f) + s.dist[(i + 1) % n] * f; }
  function camera(s, yawDeg, bob) {
    const rad = Math.PI / 180, yaw = (s.yaw0 + yawDeg) * rad, tilt = s.tilt * rad, el = s.elev * rad, d = distAt(s, yawDeg);
    const cy = Math.cos(yaw), sy = Math.sin(yaw), ct = Math.cos(tilt), st = Math.sin(tilt);
    const model = mul([cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1], [1, 0, 0, 0, 0, ct, st, 0, 0, -st, ct, 0, 0, 0, 0, 1]);
    model[13] += bob;
    const e = [0, Math.sin(el) * d, Math.cos(el) * d];
    const zl = Math.hypot(...e), z = e.map((x) => x / zl);
    let x = [z[2], 0, -z[0]]; const xl = Math.hypot(...x); x = x.map((q) => q / xl);
    const y = [z[1] * x[2] - z[2] * x[1], z[2] * x[0] - z[0] * x[2], z[0] * x[1] - z[1] * x[0]];
    const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    const view = [x[0], y[0], z[0], 0, x[1], y[1], z[1], 0, x[2], y[2], z[2], 0, -dot(x, e), -dot(y, e), -dot(z, e), 1];
    const near = d / 50, far = d * 4, fy = 1 / Math.tan(s.fov * rad / 2), fx = fy / s.aspect, k = s.dHero / d;
    const p = new Float32Array(16); p[0] = fx; p[5] = fy; p[10] = (far + near) / (near - far); p[11] = -1; p[14] = 2 * far * near / (near - far); p[8] = s.cx * k; p[9] = s.cy * k;
    return { model: new Float32Array(model), viewProj: mul(p, view), cam: e };
  }

  function start(lgmBuf, envBuf, vs, fs) {
    const M = parse(lgmBuf), E = parseEnv(envBuf);
    const W = C.W, H = C.H, SS = C.SS || 2, IW = W * SS, IH = H * SS;
    const canvas = document.getElementById('c'); canvas.width = W; canvas.height = H;
    const gl = canvas.getContext('webgl2', { antialias: false, alpha: true, premultipliedAlpha: true, preserveDrawingBuffer: true });
    gl.getExtension('WEBGL_compressed_texture_etc');
    const sh = (type, src) => { const x = gl.createShader(type); gl.shaderSource(x, src); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(x)); return x; };
    const prog = gl.createProgram(); gl.attachShader(prog, sh(gl.VERTEX_SHADER, vs)); gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, fs)); gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
    gl.useProgram(prog);
    const U = (n) => gl.getUniformLocation(prog, n);
    // textures
    const tex = M.tex.map((t) => {
      const id = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, id);
      t.levels.forEach((lv, l) => gl.compressedTexImage2D(gl.TEXTURE_2D, l, t.fmt, Math.max(1, t.w >> l), Math.max(1, t.h >> l), 0, lv));
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      const wrap = (t.flags & 1) ? gl.REPEAT : gl.CLAMP_TO_EDGE;
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, wrap); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, wrap);
      return id;
    });
    const envTex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D_ARRAY, envTex);
    gl.texImage3D(gl.TEXTURE_2D_ARRAY, 0, gl.RGB9_E5, E.w, E.h, E.n, 0, gl.RGB, gl.UNSIGNED_INT_5_9_9_9_REV, E.data);
    gl.texParameteri(gl.TEXTURE_2D_ARRAY, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D_ARRAY, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D_ARRAY, gl.TEXTURE_WRAP_S, gl.REPEAT); gl.texParameteri(gl.TEXTURE_2D_ARRAY, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    // geometry
    const vaos = M.draws.map((d) => {
      const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
      const vb = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, vb); gl.bufferData(gl.ARRAY_BUFFER, d.vb, gl.STATIC_DRAW);
      const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, d.ib, gl.STATIC_DRAW);
      const stride = 12 + ((d.fmt & 1) ? 4 : 0) + ((d.fmt & 2) ? 4 : 0);
      gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 4, gl.SHORT, true, stride, 0);
      gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 4, gl.INT_2_10_10_10_REV, true, stride, 8);
      let off = 12;
      if (d.fmt & 1) { gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 2, gl.HALF_FLOAT, false, stride, off); off += 4; } else { gl.disableVertexAttribArray(2); }
      if (d.fmt & 2) { gl.enableVertexAttribArray(3); gl.vertexAttribPointer(3, 4, gl.INT_2_10_10_10_REV, true, stride, off); } else { gl.disableVertexAttribArray(3); }
      gl.bindVertexArray(null);
      return vao;
    });
    // targets: MSAA colour + depth at IW x IH, resolve texture, then a linear blit to the canvas
    const msFbo = gl.createFramebuffer(), msCol = gl.createRenderbuffer(), msDep = gl.createRenderbuffer();
    gl.bindRenderbuffer(gl.RENDERBUFFER, msCol); gl.renderbufferStorageMultisample(gl.RENDERBUFFER, 4, gl.RGBA8, IW, IH);
    gl.bindRenderbuffer(gl.RENDERBUFFER, msDep); gl.renderbufferStorageMultisample(gl.RENDERBUFFER, 4, gl.DEPTH_COMPONENT24, IW, IH);
    gl.bindFramebuffer(gl.FRAMEBUFFER, msFbo);
    gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.RENDERBUFFER, msCol);
    gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, msDep);
    const rsFbo = gl.createFramebuffer(), rsTex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, rsTex); gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, IW, IH);
    gl.bindFramebuffer(gl.FRAMEBUFFER, rsFbo); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, rsTex, 0);
    // static uniforms
    gl.uniform1i(U('uBaseMap'), 0); gl.uniform1i(U('uNormalMap'), 1); gl.uniform1i(U('uOrmMap'), 2); gl.uniform1i(U('uEnv'), 3);
    const er = new Float32Array(16); E.rough.forEach((r, i) => { er[i] = r; });
    gl.uniform1fv(U('uEnvRough'), er); gl.uniform1i(U('uEnvLayers'), E.n);
    gl.uniform1f(U('uEnvIntensity'), M.s.envI); gl.uniform1f(U('uExposure'), M.s.exposure);
    const ld = new Float32Array(24), lc = new Float32Array(24);
    M.s.lights.forEach((L, i) => { ld.set(L.dir, i * 3); lc.set(L.col, i * 3); });
    gl.uniform3fv(U('uLightDir'), ld); gl.uniform3fv(U('uLightCol'), lc); gl.uniform1i(U('uLightCount'), M.s.lights.length);
    gl.uniform3fv(U('uPosScale'), M.s.scale); gl.uniform3fv(U('uPosOffset'), M.s.offset);
    window.LOG.push('draws ' + M.draws.length + ' tex ' + M.tex.length + ' env ' + E.w + 'x' + E.h + 'x' + E.n);

    window.renderFrame = (i) => {
      const [yaw, bob] = C.poses[i];
      const c = camera(M.s, yaw, bob);
      gl.bindFramebuffer(gl.FRAMEBUFFER, msFbo); gl.viewport(0, 0, IW, IH);
      gl.clearColor(0, 0, 0, 0); gl.clearDepth(1); gl.depthMask(true); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL);
      gl.useProgram(prog);
      gl.uniformMatrix4fv(U('uModel'), false, c.model); gl.uniformMatrix4fv(U('uViewProj'), false, c.viewProj); gl.uniform3fv(U('uCam'), c.cam);
      gl.activeTexture(gl.TEXTURE3); gl.bindTexture(gl.TEXTURE_2D_ARRAY, envTex);
      M.draws.forEach((d, di) => {
        const m = M.mats[d.mat];
        let flags = m.flags & 63;
        if (m.baseTex >= 0) { flags |= 64; gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex[m.baseTex]); }
        if (m.normalTex >= 0) { flags |= 128; gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, tex[m.normalTex]); }
        if (m.ormTex >= 0) { gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, tex[m.ormTex]); }
        if (d.fmt & 2) flags |= 256;
        gl.uniform1i(U('uFlags'), flags);
        gl.uniform4f(U('uBase'), ...m.base); gl.uniform3f(U('uEmissive'), ...m.em);
        gl.uniform4f(U('uRMN'), m.rough, m.metal, m.ns[0], m.ns[1]); gl.uniform4f(U('uAoCc'), m.aoI, m.aoC, m.cc, m.ccR);
        if (m.flags & 1) gl.disable(gl.CULL_FACE); else { gl.enable(gl.CULL_FACE); gl.cullFace(gl.BACK); }
        if (m.flags & 2) { gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); } else gl.disable(gl.BLEND);
        gl.depthMask(!!(m.flags & 4));
        gl.bindVertexArray(vaos[di]);
        if (!(d.fmt & 1)) gl.vertexAttrib2f(2, 0, 0);
        if (!(d.fmt & 2)) gl.vertexAttrib4f(3, 1, 0, 0, 1);
        gl.drawElements(gl.TRIANGLES, d.ic, d.is === 2 ? gl.UNSIGNED_SHORT : gl.UNSIGNED_INT, 0);
      });
      gl.bindVertexArray(null); gl.disable(gl.BLEND); gl.depthMask(true);
      gl.bindFramebuffer(gl.READ_FRAMEBUFFER, msFbo); gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, rsFbo);
      gl.blitFramebuffer(0, 0, IW, IH, 0, 0, IW, IH, gl.COLOR_BUFFER_BIT, gl.NEAREST);
      gl.bindFramebuffer(gl.READ_FRAMEBUFFER, rsFbo); gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, null);
      gl.blitFramebuffer(0, 0, IW, IH, 0, 0, W, H, gl.COLOR_BUFFER_BIT, gl.LINEAR);
      const err = gl.getError(); if (err) window.LOG.push('gl error ' + err);
      return canvas.toDataURL('image/png');
    };
    window.READY = true;
  }
})();
