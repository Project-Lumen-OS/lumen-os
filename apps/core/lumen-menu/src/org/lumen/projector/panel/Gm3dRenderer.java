package org.lumen.projector.panel;

import android.opengl.GLES30;

/**
 * Draws the game menu's controller with OpenGL ES 3.0 (GL thread of Gm3dView only): the model of Gm3dModel with the
 * shaders assets/gm3d/model.vert / model.frag, into a 4x multisampled target at SS times the view size, resolved and
 * scaled down by a linear blit into the view's surface (the frame strip was rendered larger and scaled down too).
 * Every GL object is created here and deleted by {@link #release} while the context is current.
 */
final class Gm3dRenderer {
    /** Supersampling of the target (2: a 2 x 2 box per screen pixel after the blit). */
    static final int SS = 2;
    static final int MAX_SAMPLES = 4;
    /** Uniform flag bits on top of the material flags (Gm3dModel.M_*), as model.frag reads them. */
    static final int U_BASE_MAP = 64, U_NORMAL_MAP = 128, U_TANGENT = 256;
    private static final int GL_INT_2_10_10_10_REV = 0x8D9F, GL_HALF_FLOAT = 0x140B,
            GL_UNSIGNED_INT_5_9_9_9_REV = 0x8C3E, GL_RGB9_E5 = 0x8C3D;

    static final class GlError extends Exception {
        GlError(String m) { super(m); }
    }

    private int prog, envTex;
    private int uModel, uViewProj, uPosScale, uPosOffset, uCam, uBase, uEmissive, uRMN, uAoCc, uFlags;
    private Gm3dModel model;
    private int[] vao, vbo, ibo, tex;
    private int samples;
    private int msFbo, msColour, msDepth, rsFbo, rsTex, w, h, iw, ih;
    private final float[] mModel = new float[16], mViewProj = new float[16], cam = new float[3];

    int samples() { return samples; }

    int width() { return iw; }

    int height() { return ih; }

    static void check(String what) throws GlError {
        int e = GLES30.glGetError();
        if (e != GLES30.GL_NO_ERROR) throw new GlError(what + ": GL error 0x" + Integer.toHexString(e));
    }

    private static int shader(int type, String src) throws GlError {
        int s = GLES30.glCreateShader(type);
        if (s == 0) throw new GlError("glCreateShader");
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new GlError((type == GLES30.GL_VERTEX_SHADER ? "vertex" : "fragment") + " shader: " + log);
        }
        return s;
    }

    /** Program, static uniforms and the environment. The context must be current. */
    void init(String vsSrc, String fsSrc, Gm3dModel.Env env, Gm3dModel.Scene scene) throws GlError {
        int vs = shader(GLES30.GL_VERTEX_SHADER, vsSrc), fs = shader(GLES30.GL_FRAGMENT_SHADER, fsSrc);
        prog = GLES30.glCreateProgram();
        GLES30.glAttachShader(prog, vs);
        GLES30.glAttachShader(prog, fs);
        GLES30.glLinkProgram(prog);
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
        int[] ok = new int[1];
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new GlError("link: " + GLES30.glGetProgramInfoLog(prog));
        GLES30.glUseProgram(prog);
        uModel = loc("uModel");
        uViewProj = loc("uViewProj");
        uPosScale = loc("uPosScale");
        uPosOffset = loc("uPosOffset");
        uCam = loc("uCam");
        uBase = loc("uBase");
        uEmissive = loc("uEmissive");
        uRMN = loc("uRMN");
        uAoCc = loc("uAoCc");
        uFlags = loc("uFlags");
        GLES30.glUniform1i(loc("uBaseMap"), 0);
        GLES30.glUniform1i(loc("uNormalMap"), 1);
        GLES30.glUniform1i(loc("uOrmMap"), 2);
        GLES30.glUniform1i(loc("uEnv"), 3);
        float[] er = new float[16];
        System.arraycopy(env.roughness, 0, er, 0, Math.min(16, env.layers));
        GLES30.glUniform1fv(loc("uEnvRough"), 16, er, 0);
        GLES30.glUniform1i(loc("uEnvLayers"), env.layers);
        envTex = gen(1, GLES30::glGenTextures)[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, envTex);
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GL_RGB9_E5, env.width, env.height, env.layers, 0,
                GLES30.GL_RGB, GL_UNSIGNED_INT_5_9_9_9_REV, env.data);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        int[] s = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, s, 0);
        samples = Math.max(0, Math.min(MAX_SAMPLES, s[0]));
        check("init");
    }

    private int loc(String n) { return GLES30.glGetUniformLocation(prog, n); }

    private interface Gen {
        void gen(int n, int[] out, int off);
    }

    private static int[] gen(int n, Gen g) throws GlError {
        int[] a = new int[n];
        if (n > 0) g.gen(n, a, 0);
        for (int x : a) if (x == 0) throw new GlError("no GL name");
        return a;
    }

    /** Uploads {@code m} (the previous model is deleted). The context must be current. */
    void setModel(Gm3dModel m) throws GlError {
        dropModel();
        model = m;
        Gm3dModel.Scene s = m.scene;
        GLES30.glUseProgram(prog);
        GLES30.glUniform1f(loc("uEnvIntensity"), s.envIntensity);
        GLES30.glUniform1f(loc("uExposure"), s.exposure);
        float[] ld = new float[24], lc = new float[24];
        System.arraycopy(s.lightDir, 0, ld, 0, s.lights * 3);
        System.arraycopy(s.lightColour, 0, lc, 0, s.lights * 3);
        GLES30.glUniform3fv(loc("uLightDir"), 8, ld, 0);
        GLES30.glUniform3fv(loc("uLightCol"), 8, lc, 0);
        GLES30.glUniform1i(loc("uLightCount"), s.lights);
        GLES30.glUniform3fv(uPosScale, 1, s.posScale, 0);
        GLES30.glUniform3fv(uPosOffset, 1, s.posOffset, 0);
        tex = gen(m.textures.length, GLES30::glGenTextures);
        for (int i = 0; i < m.textures.length; i++) {
            Gm3dModel.Texture t = m.textures[i];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[i]);
            for (int l = 0; l < t.levels.length; l++) {
                GLES30.glCompressedTexImage2D(GLES30.GL_TEXTURE_2D, l, t.format, Math.max(1, t.width >> l),
                        Math.max(1, t.height >> l), 0, t.levels[l].remaining(), t.levels[l]);
            }
            int wrap = (t.flags & Gm3dModel.TEX_REPEAT) != 0 ? GLES30.GL_REPEAT : GLES30.GL_CLAMP_TO_EDGE;
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap);
        }
        check("textures");
        int n = m.draws.length;
        vao = gen(n, GLES30::glGenVertexArrays);
        vbo = gen(n, GLES30::glGenBuffers);
        ibo = gen(n, GLES30::glGenBuffers);
        for (int i = 0; i < n; i++) {
            Gm3dModel.Draw d = m.draws[i];
            int stride = d.stride();
            GLES30.glBindVertexArray(vao[i]);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[i]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, d.vertices.remaining(), d.vertices, GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[i]);
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, d.indices.remaining(), d.indices, GLES30.GL_STATIC_DRAW);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 4, GLES30.GL_SHORT, true, stride, 0);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(1, 4, GL_INT_2_10_10_10_REV, true, stride, 8);
            int off = 12;
            if ((d.format & Gm3dModel.V_UV) != 0) {
                GLES30.glEnableVertexAttribArray(2);
                GLES30.glVertexAttribPointer(2, 2, GL_HALF_FLOAT, false, stride, off);
                off += 4;
            } else {
                GLES30.glDisableVertexAttribArray(2);
            }
            if ((d.format & Gm3dModel.V_TANGENT) != 0) {
                GLES30.glEnableVertexAttribArray(3);
                GLES30.glVertexAttribPointer(3, 4, GL_INT_2_10_10_10_REV, true, stride, off);
            } else {
                GLES30.glDisableVertexAttribArray(3);
            }
        }
        GLES30.glBindVertexArray(0);
        check("geometry");
    }

    /** Targets for a {@code width} x {@code height} surface. The context must be current. */
    void resize(int width, int height) throws GlError {
        if (width == w && height == h && msFbo != 0) return;
        dropTargets();
        w = Math.max(1, width);
        h = Math.max(1, height);
        iw = w * SS;
        ih = h * SS;
        int[] rb = gen(2, GLES30::glGenRenderbuffers);
        msColour = rb[0];
        msDepth = rb[1];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, msColour);
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, iw, ih);
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, msDepth);
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, iw, ih);
        int[] fb = gen(2, GLES30::glGenFramebuffers);
        msFbo = fb[0];
        rsFbo = fb[1];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, msFbo);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, msColour);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, msDepth);
        complete("multisampled target");
        rsTex = gen(1, GLES30::glGenTextures)[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rsTex);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, iw, ih);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, rsFbo);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, rsTex, 0);
        complete("resolve target");
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        check("targets");
    }

    private static void complete(String what) throws GlError {
        int st = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        if (st != GLES30.GL_FRAMEBUFFER_COMPLETE) throw new GlError(what + " incomplete 0x" + Integer.toHexString(st));
    }

    private static final int[] DISCARD = {GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_DEPTH_ATTACHMENT};

    /** One frame at {@code yawDeg} from the hero pose, into the current window surface (framebuffer 0). */
    void draw(float yawDeg, float bob) {
        Gm3dModel m = model;
        Gm3dMotion.camera(m.scene, yawDeg, bob, mModel, mViewProj, cam);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, msFbo);
        GLES30.glViewport(0, 0, iw, ih);
        GLES30.glDepthMask(true);
        GLES30.glClearColor(0, 0, 0, 0);
        GLES30.glClearDepthf(1);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glUseProgram(prog);
        GLES30.glUniformMatrix4fv(uModel, 1, false, mModel, 0);
        GLES30.glUniformMatrix4fv(uViewProj, 1, false, mViewProj, 0);
        GLES30.glUniform3fv(uCam, 1, cam, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, envTex);
        for (int i = 0; i < m.draws.length; i++) {
            Gm3dModel.Draw d = m.draws[i];
            Gm3dModel.Material mt = m.materials[d.material];
            int flags = mt.flags & 63;
            if (mt.baseTex >= 0) {
                flags |= U_BASE_MAP;
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[mt.baseTex]);
            }
            if (mt.normalTex >= 0) {
                flags |= U_NORMAL_MAP;
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[mt.normalTex]);
            }
            if (mt.ormTex >= 0) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE2);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[mt.ormTex]);
            }
            if ((d.format & Gm3dModel.V_TANGENT) != 0) flags |= U_TANGENT;
            GLES30.glUniform1i(uFlags, flags);
            GLES30.glUniform4f(uBase, mt.base[0], mt.base[1], mt.base[2], mt.base[3]);
            GLES30.glUniform3f(uEmissive, mt.emissive[0], mt.emissive[1], mt.emissive[2]);
            GLES30.glUniform4f(uRMN, mt.roughness, mt.metalness, mt.normalScaleX, mt.normalScaleY);
            GLES30.glUniform4f(uAoCc, mt.aoIntensity, mt.aoConst, mt.clearcoat, mt.clearcoatRoughness);
            if ((mt.flags & Gm3dModel.M_DOUBLE) != 0) {
                GLES30.glDisable(GLES30.GL_CULL_FACE);
            } else {
                GLES30.glEnable(GLES30.GL_CULL_FACE);
                GLES30.glCullFace(GLES30.GL_BACK);
            }
            if ((mt.flags & Gm3dModel.M_BLEND) != 0) {
                GLES30.glEnable(GLES30.GL_BLEND);
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            } else {
                GLES30.glDisable(GLES30.GL_BLEND);
            }
            GLES30.glDepthMask((mt.flags & Gm3dModel.M_DEPTH_WRITE) != 0);
            GLES30.glBindVertexArray(vao[i]);
            if ((d.format & Gm3dModel.V_UV) == 0) GLES30.glVertexAttrib4f(2, 0, 0, 0, 1);
            if ((d.format & Gm3dModel.V_TANGENT) == 0) GLES30.glVertexAttrib4f(3, 1, 0, 0, 1);
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, d.indexCount,
                    d.indexSize == 2 ? GLES30.GL_UNSIGNED_SHORT : GLES30.GL_UNSIGNED_INT, 0);
        }
        GLES30.glBindVertexArray(0);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDepthMask(true);
        // resolve the samples, drop them (tile memory never written back on a tiler), then scale into the surface
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, msFbo);
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, rsFbo);
        GLES30.glBlitFramebuffer(0, 0, iw, ih, 0, 0, iw, ih, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
        GLES30.glInvalidateFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 2, DISCARD, 0);
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, rsFbo);
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, w, h);
        GLES30.glBlitFramebuffer(0, 0, iw, ih, 0, 0, w, h, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    private void dropModel() {
        if (vao != null) GLES30.glDeleteVertexArrays(vao.length, vao, 0);
        if (vbo != null) GLES30.glDeleteBuffers(vbo.length, vbo, 0);
        if (ibo != null) GLES30.glDeleteBuffers(ibo.length, ibo, 0);
        if (tex != null && tex.length > 0) GLES30.glDeleteTextures(tex.length, tex, 0);
        vao = vbo = ibo = tex = null;
        model = null;
    }

    private void dropTargets() {
        if (msFbo != 0) GLES30.glDeleteFramebuffers(2, new int[] {msFbo, rsFbo}, 0);
        if (msColour != 0) GLES30.glDeleteRenderbuffers(2, new int[] {msColour, msDepth}, 0);
        if (rsTex != 0) GLES30.glDeleteTextures(1, new int[] {rsTex}, 0);
        msFbo = rsFbo = msColour = msDepth = rsTex = 0;
        w = h = 0;
    }

    boolean hasModel() { return model != null; }

    /** Deletes every GL object. The context must be current. */
    void release() {
        dropModel();
        dropTargets();
        if (envTex != 0) GLES30.glDeleteTextures(1, new int[] {envTex}, 0);
        if (prog != 0) GLES30.glDeleteProgram(prog);
        envTex = prog = 0;
    }
}
