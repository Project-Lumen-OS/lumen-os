package org.lumen.projector.panel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The game menu's controller model in its packed form (assets/gm3d/&lt;theme&gt;.lgm, made by tools/gm3d/bake.py) and the
 * room environment (assets/gm3d/env.lge). Plain Java: reads and checks everything before the GL side sees a byte, so
 * a damaged or foreign file is refused here (Format) and the menu falls back to the frame strip.
 *
 * <pre>
 * LGM1, little endian, 4-byte aligned:
 *   header   magic 'LGM1', u16 version 1, u16 flags, u32 file size, u16 textures, u16 materials, u16 draws, u16 lights,
 *            u32 0, u32 vertex blob offset, u32 index blob offset                                         (32 bytes)
 *   scene    f32 fov, elevation, tilt, yaw0 (degrees), aspect, exposure, environment intensity,
 *            f32 centre x, y, hero distance, u16 n, u16 0, f32 step (degrees), f32[n] camera distance from yaw0,
 *            lights x (f32 direction xyz, f32 colour * intensity xyz), f32 position scale xyz, offset xyz
 *   textures x (u32 GL internal format, u16 levels, u16 width, u16 height, u16 flags) then per level u32 size + data
 *   materials x (f32 base rgba, emissive rgb, roughness, metalness, normal scale xy, ao intensity, constant ao,
 *            clearcoat, clearcoat roughness, i16 base, normal, orm texture (-1 none), u16 flags)          (68 bytes)
 *   draws    x (u16 material, u16 vertex format, u32 vertices, u32 indices, u32 index size 2|4,
 *            u32 vertex offset, u32 vertex bytes, u32 index offset, u32 index bytes)                      (32 bytes)
 *   blobs    vertices (pos 4 x i16 norm, normal 10:10:10:2, [uv 2 x f16], [tangent 10:10:10:2]), indices
 * LGE1: magic 'LGE1', u16 version 1, u16 width, u16 height, u16 layers, u32 0, f32[layers] roughness,
 *       layers x width x height RGB9_E5 (u32)
 * </pre>
 */
final class Gm3dModel {
    static final int MAGIC = 0x314D474C, ENV_MAGIC = 0x3145474C, VERSION = 1;
    // GL internal formats the reader accepts (GLES 3.0 core ETC2 / EAC)
    static final int RG11_EAC = 0x9272, RGB8_ETC2 = 0x9274, SRGB8_ETC2 = 0x9275, RGBA8_ETC2_EAC = 0x9278,
            SRGB8_ALPHA8_ETC2_EAC = 0x9279;
    // material flags
    static final int M_DOUBLE = 1, M_BLEND = 2, M_DEPTH_WRITE = 4, M_ROUGH_MAP = 8, M_METAL_MAP = 16, M_AO_MAP = 32;
    // vertex format flags
    static final int V_UV = 1, V_TANGENT = 2;
    // limits: a file outside them is not ours
    static final int MAX_FILE = 4 << 20, MAX_TEXTURES = 8, MAX_DRAWS = 64, MAX_LIGHTS = 8, MAX_DIST = 720,
            MAX_LEVELS = 12, MAX_TEX_SIZE = 2048, MAX_VERTICES = 200_000, MAX_INDICES = 600_000, MAX_ENV = 16;
    static final int TEX_REPEAT = 1;

    /** A file that is not a valid model (or environment). */
    static final class Format extends Exception {
        Format(String m) { super(m); }
    }

    static final class Scene {
        float fov, elev, tilt, yaw0, aspect, exposure, envIntensity, centerX, centerY, distHero, distStep;
        float[] dist;
        float[] lightDir, lightColour;
        int lights;
        final float[] posScale = new float[3], posOffset = new float[3];
    }

    static final class Texture {
        int format, width, height, flags;
        ByteBuffer[] levels;
    }

    static final class Material {
        final float[] base = new float[4], emissive = new float[3];
        float roughness, metalness, normalScaleX, normalScaleY, aoIntensity, aoConst, clearcoat, clearcoatRoughness;
        int baseTex, normalTex, ormTex, flags;
    }

    static final class Draw {
        int material, format, vertexCount, indexCount, indexSize;
        ByteBuffer vertices, indices;

        int stride() { return 12 + ((format & V_UV) != 0 ? 4 : 0) + ((format & V_TANGENT) != 0 ? 4 : 0); }
    }

    static final class Env {
        int width, height, layers;
        float[] roughness;
        ByteBuffer data;
    }

    final Scene scene;
    final Texture[] textures;
    final Material[] materials;
    final Draw[] draws;

    private Gm3dModel(Scene s, Texture[] t, Material[] m, Draw[] d) {
        scene = s;
        textures = t;
        materials = m;
        draws = d;
    }

    int triangles() {
        int n = 0;
        for (Draw d : draws) n += d.indexCount / 3;
        return n;
    }

    // ================================================================== reader
    private static void need(boolean ok, String what) throws Format {
        if (!ok) throw new Format(what);
    }

    private static float f(ByteBuffer b, String what) throws Format {
        float v = b.getFloat();
        need(!Float.isNaN(v) && !Float.isInfinite(v), what + " is not a number");
        return v;
    }

    private static int u16(ByteBuffer b) { return b.getShort() & 0xFFFF; }

    private static ByteBuffer slice(ByteBuffer src, int off, int len, String what) throws Format {
        need(off >= 0 && len >= 0 && (long) off + len <= src.limit(), what + " outside the file");
        ByteBuffer d = src.duplicate();
        d.position(off);
        d.limit(off + len);
        return d.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    /** 32-bit indices, or 16-bit ones when every vertex fits in them. */
    static boolean indexSizeOk(int size, int vertices) {
        return size == 4 || (size == 2 && vertices <= 65535);
    }

    /** Bytes of one compressed level of {@code format}. */
    static int levelBytes(int format, int w, int h) {
        int block = format == RGB8_ETC2 || format == SRGB8_ETC2 ? 8 : 16;
        return ((w + 3) / 4) * ((h + 3) / 4) * block;
    }

    static boolean knownFormat(int f) {
        return f == RG11_EAC || f == RGB8_ETC2 || f == SRGB8_ETC2 || f == RGBA8_ETC2_EAC || f == SRGB8_ALPHA8_ETC2_EAC;
    }

    /** Reads a whole .lgm file; any inconsistency is a Format. */
    static Gm3dModel parse(ByteBuffer in) throws Format {
        need(in != null && in.remaining() >= 32 && in.remaining() <= MAX_FILE, "size");
        ByteBuffer b = in.slice().order(ByteOrder.LITTLE_ENDIAN);
        need(b.getInt() == MAGIC, "magic");
        need(u16(b) == VERSION, "version");
        u16(b);
        need(b.getInt() == b.limit(), "file size");
        int nt = u16(b), nm = u16(b), nd = u16(b), nl = u16(b);
        need(nt <= MAX_TEXTURES && nm >= 1 && nm <= MAX_DRAWS && nd >= 1 && nd <= MAX_DRAWS && nl >= 1 && nl <= MAX_LIGHTS,
                "counts");
        b.getInt();
        int vbase = b.getInt(), ibase = b.getInt();
        need(vbase > 32 && vbase <= ibase && ibase <= b.limit() && vbase % 4 == 0 && ibase % 4 == 0, "blob offsets");
        Scene s = new Scene();
        s.fov = f(b, "fov");
        s.elev = f(b, "elevation");
        s.tilt = f(b, "tilt");
        s.yaw0 = f(b, "yaw0");
        s.aspect = f(b, "aspect");
        s.exposure = f(b, "exposure");
        s.envIntensity = f(b, "environment");
        s.centerX = f(b, "centre");
        s.centerY = f(b, "centre");
        s.distHero = f(b, "hero distance");
        need(s.fov > 1 && s.fov < 120 && s.aspect > 0.1f && s.aspect < 10 && s.distHero > 0, "camera");
        int n = u16(b);
        u16(b);
        s.distStep = f(b, "step");
        need(n >= 1 && n <= MAX_DIST && Math.abs(s.distStep * n - 360f) < 0.01f, "distance table");
        s.dist = new float[n];
        for (int i = 0; i < n; i++) {
            s.dist[i] = f(b, "distance");
            need(s.dist[i] >= s.distHero * 0.999f && s.dist[i] < s.distHero * 4, "distance range");
        }
        s.lights = nl;
        s.lightDir = new float[nl * 3];
        s.lightColour = new float[nl * 3];
        for (int i = 0; i < nl; i++) {
            for (int k = 0; k < 3; k++) s.lightDir[i * 3 + k] = f(b, "light");
            for (int k = 0; k < 3; k++) {
                s.lightColour[i * 3 + k] = f(b, "light");
                need(s.lightColour[i * 3 + k] >= 0, "light colour");
            }
        }
        for (int k = 0; k < 3; k++) { s.posScale[k] = f(b, "scale"); need(s.posScale[k] > 0, "scale"); }
        for (int k = 0; k < 3; k++) s.posOffset[k] = f(b, "offset");
        Texture[] tex = new Texture[nt];
        for (int i = 0; i < nt; i++) {
            need(b.remaining() >= 12, "texture header");
            Texture t = new Texture();
            t.format = b.getInt();
            int levels = u16(b);
            t.width = u16(b);
            t.height = u16(b);
            t.flags = u16(b);
            need(knownFormat(t.format), "texture format 0x" + Integer.toHexString(t.format));
            need(t.width >= 1 && t.height >= 1 && t.width <= MAX_TEX_SIZE && t.height <= MAX_TEX_SIZE, "texture size");
            int full = 1 + (31 - Integer.numberOfLeadingZeros(Math.max(t.width, t.height)));
            need(levels == full && levels <= MAX_LEVELS, "texture levels");
            t.levels = new ByteBuffer[levels];
            for (int l = 0; l < levels; l++) {
                need(b.remaining() >= 4, "texture level");
                int size = b.getInt();
                need(size == levelBytes(t.format, Math.max(1, t.width >> l), Math.max(1, t.height >> l)), "level size");
                t.levels[l] = slice(b, b.position(), size, "texture level");
                b.position(b.position() + size);
                while (b.position() % 4 != 0) b.get();
            }
            tex[i] = t;
        }
        Material[] mat = new Material[nm];
        for (int i = 0; i < nm; i++) {
            need(b.remaining() >= 68, "material");
            Material m = new Material();
            for (int k = 0; k < 4; k++) m.base[k] = f(b, "base colour");
            for (int k = 0; k < 3; k++) m.emissive[k] = f(b, "emissive");
            m.roughness = f(b, "roughness");
            m.metalness = f(b, "metalness");
            m.normalScaleX = f(b, "normal scale");
            m.normalScaleY = f(b, "normal scale");
            m.aoIntensity = f(b, "ao");
            m.aoConst = f(b, "ao");
            m.clearcoat = f(b, "clearcoat");
            m.clearcoatRoughness = f(b, "clearcoat");
            m.baseTex = b.getShort();
            m.normalTex = b.getShort();
            m.ormTex = b.getShort();
            m.flags = u16(b);
            need(m.roughness >= 0 && m.roughness <= 1 && m.metalness >= 0 && m.metalness <= 1, "material range");
            for (int t : new int[] {m.baseTex, m.normalTex, m.ormTex}) need(t >= -1 && t < nt, "material texture");
            need(m.normalTex < 0 || tex[m.normalTex].format == RG11_EAC, "normal map format");
            need((m.flags & (M_ROUGH_MAP | M_METAL_MAP | M_AO_MAP)) == 0 || m.ormTex >= 0, "orm map missing");
            mat[i] = m;
        }
        Draw[] dr = new Draw[nd];
        int vblob = ibase - vbase, iblob = b.limit() - ibase;
        for (int i = 0; i < nd; i++) {
            need(b.remaining() >= 32, "draw");
            Draw d = new Draw();
            d.material = u16(b);
            d.format = u16(b);
            d.vertexCount = b.getInt();
            d.indexCount = b.getInt();
            d.indexSize = b.getInt();
            int voff = b.getInt(), vsize = b.getInt(), ioff = b.getInt(), isize = b.getInt();
            need(d.material < nm && (d.format & ~(V_UV | V_TANGENT)) == 0, "draw material or format");
            Material m = mat[d.material];
            need((m.baseTex < 0 && m.normalTex < 0 && m.ormTex < 0) || (d.format & V_UV) != 0, "maps without UVs");
            need(d.vertexCount >= 3 && d.vertexCount <= MAX_VERTICES && d.indexCount >= 3
                    && d.indexCount <= MAX_INDICES && d.indexCount % 3 == 0, "draw counts");
            need(indexSizeOk(d.indexSize, d.vertexCount), "index size");
            need(vsize == d.vertexCount * d.stride() && isize == d.indexCount * d.indexSize, "draw sizes");
            need(voff >= 0 && (long) voff + vsize <= vblob && ioff >= 0 && (long) ioff + isize <= iblob, "draw range");
            d.vertices = slice(b, vbase + voff, vsize, "vertices");
            d.indices = slice(b, ibase + ioff, isize, "indices");
            // every index must name a vertex of its draw (the GPU never reads outside the buffer)
            long max = maxIndex(d);
            if (max >= d.vertexCount) throw new Format("index " + max + " >= " + d.vertexCount + " in draw " + i);
            dr[i] = d;
        }
        need(b.position() <= vbase, "tables overlap the blobs");
        return new Gm3dModel(s, tex, mat, dr);
    }

    /** The largest index of a draw (bulk copy, then a plain array loop: cheap before the JIT warms up). */
    static long maxIndex(Draw d) {
        ByteBuffer ix = d.indices.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        long max = 0;
        if (d.indexSize == 2) {
            short[] a = new short[d.indexCount];
            ix.asShortBuffer().get(a);
            int m = 0;
            for (short v : a) m = Math.max(m, v & 0xFFFF);
            max = m;
        } else {
            int[] a = new int[d.indexCount];
            ix.asIntBuffer().get(a);
            for (int v : a) max = Math.max(max, v & 0xFFFFFFFFL);
        }
        return max;
    }

    /** Reads a .lge environment. */
    static Env parseEnv(ByteBuffer in) throws Format {
        need(in != null && in.remaining() >= 16 && in.remaining() <= MAX_FILE, "size");
        ByteBuffer b = in.slice().order(ByteOrder.LITTLE_ENDIAN);
        need(b.getInt() == ENV_MAGIC, "magic");
        need(u16(b) == VERSION, "version");
        Env e = new Env();
        e.width = u16(b);
        e.height = u16(b);
        e.layers = u16(b);
        b.getInt();
        need(e.width >= 4 && e.width <= 1024 && e.height >= 2 && e.height <= 512 && e.layers >= 1 && e.layers <= MAX_ENV,
                "environment size");
        e.roughness = new float[e.layers];
        for (int i = 0; i < e.layers; i++) {
            e.roughness[i] = f(b, "roughness");
            need(e.roughness[i] > 0 && e.roughness[i] <= 1 && (i == 0 || e.roughness[i] > e.roughness[i - 1]),
                    "roughness order");
        }
        int bytes = e.width * e.height * e.layers * 4;
        need(b.remaining() == bytes, "environment data");
        e.data = slice(b, b.position(), bytes, "environment data");
        return e;
    }
}
