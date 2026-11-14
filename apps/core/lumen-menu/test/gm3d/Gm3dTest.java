package org.lumen.projector.panel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Real-time game menu controller, plain Java parts (test/gm3d/run.sh): the packed model files (assets/gm3d) and their
 * reader, the motion and camera, the gate between the model and the frame strip.
 */
public final class Gm3dTest {
    private static int pass, fail;

    private static void ok(boolean c, String what) {
        if (c) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static ByteBuffer file(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        ByteBuffer d = ByteBuffer.allocateDirect(b.length).order(ByteOrder.LITTLE_ENDIAN);
        d.put(b).flip();
        return d;
    }

    private static ByteBuffer copy(ByteBuffer b) {
        ByteBuffer c = ByteBuffer.allocate(b.limit()).order(ByteOrder.LITTLE_ENDIAN);
        c.put(b.duplicate()).flip();
        return c;
    }

    private static boolean refused(ByteBuffer b, String why) {
        try {
            Gm3dModel.parse(b);
            System.out.println("  accepted: " + why);
            return false;
        } catch (Gm3dModel.Format e) {
            return true;
        }
    }

    static final String[] THEMES = GmModel.FRAME_THEMES;

    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]);
        // ---------------------------------------------------------------- every console theme has its model
        Gm3dModel.Env env = Gm3dModel.parseEnv(file(dir.resolve("env.lge")));
        ok(env.layers >= 4 && env.roughness[env.layers - 1] == 1f, "environment layers up to roughness 1");
        ok(env.width == 2 * env.height, "environment is an equirect (2:1)");
        for (String t : THEMES) {
            Path p = dir.resolve(t + ".lgm");
            ok(Files.exists(p), "no model for " + t);
            if (!Files.exists(p)) continue;
            long size = Files.size(p);
            ok(size <= 1_500_000, t + ": " + size + " bytes, budget 1.5 MB");
            long t0 = System.nanoTime();
            Gm3dModel m = Gm3dModel.parse(file(p));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            ok(ms < 150, t + ": parse " + ms + " ms");
            ok(m.triangles() >= 5_000 && m.triangles() <= 60_000, t + ": " + m.triangles() + " triangles");
            ok(m.scene.lights >= 3, t + ": studio lights");
            ok(Math.abs(m.scene.aspect - 600f / 360f) < 1e-4, t + ": aspect of the frames (600 x 360)");
            ok(Math.abs(m.scene.yaw0 + 22) < 1e-3 && Math.abs(m.scene.elev - 16) < 1e-3 && Math.abs(m.scene.fov - 24) < 1e-3,
                    t + ": camera of the frame render");
            boolean blendLast = true, seenBlend = false;
            for (Gm3dModel.Draw d : m.draws) {
                boolean bl = (m.materials[d.material].flags & Gm3dModel.M_BLEND) != 0;
                if (seenBlend && !bl) blendLast = false;
                seenBlend |= bl;
            }
            ok(blendLast, t + ": blended draws after the opaque ones");
            for (Gm3dModel.Texture tx : m.textures) {
                ok(tx.width <= 512 && tx.height <= 512, t + ": texture " + tx.width + " x " + tx.height);
            }
            framing(t, m);
            corrupt(t, file(p));
        }
        // ---------------------------------------------------------------- environment refusals
        ByteBuffer e = copy(file(dir.resolve("env.lge")));
        ByteBuffer e1 = copy(e);
        e1.putInt(0, 0x31454747);
        ok(refusedEnv(e1), "env: bad magic accepted");
        ByteBuffer e2 = copy(e);
        e2.limit(e2.limit() - 4);
        ok(refusedEnv(e2), "env: short data accepted");
        ByteBuffer e4 = ByteBuffer.allocate(e.limit() + 4).order(ByteOrder.LITTLE_ENDIAN);
        e4.put(e.duplicate()).putInt(0).flip();
        ok(refusedEnv(e4), "env: trailing bytes accepted");
        for (int f : new int[] {Gm3dModel.RG11_EAC, Gm3dModel.RGB8_ETC2, Gm3dModel.SRGB8_ETC2, Gm3dModel.RGBA8_ETC2_EAC,
                Gm3dModel.SRGB8_ALPHA8_ETC2_EAC}) ok(Gm3dModel.knownFormat(f), "ETC2/EAC format 0x" + Integer.toHexString(f));
        for (int f : new int[] {0x8D64, 0x93B0, 0x8058, 0x9270, 0x9276, 0x9277, 0x927A})
            ok(!Gm3dModel.knownFormat(f), "format 0x" + Integer.toHexString(f) + " accepted");
        ok(Gm3dModel.indexSizeOk(2, 65535) && !Gm3dModel.indexSizeOk(2, 65536) && Gm3dModel.indexSizeOk(4, 65536)
                && !Gm3dModel.indexSizeOk(1, 10) && !Gm3dModel.indexSizeOk(3, 10), "index size rule");
        ByteBuffer e3 = copy(e);
        e3.putFloat(16, 0.9f);   // first roughness above the second: order broken
        ok(refusedEnv(e3), "env: roughness order not checked");
        motion();
        gate();
        System.out.println("Gm3dTest: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** Refused as a format error (anything else thrown counts as accepted: the reader must say why). */
    private static boolean refusedQuietly(ByteBuffer b) {
        try {
            Gm3dModel.parse(b);
            return false;
        } catch (Gm3dModel.Format e) {
            return true;
        } catch (RuntimeException e) {
            System.out.println("  not a format error: " + e);
            return false;
        }
    }

    private static boolean refusedEnv(ByteBuffer b) {
        try {
            Gm3dModel.parseEnv(b);
            return false;
        } catch (Gm3dModel.Format x) {
            return true;
        }
    }

    // ---------------------------------------------------------------- damaged files are refused (strip fallback)
    private static int u32(ByteBuffer b, int at) { return b.getInt(at); }

    private static void corrupt(String t, ByteBuffer good) throws Exception {
        ByteBuffer b;
        b = copy(good); b.putInt(0, 0x324D474C); ok(refused(b, "magic"), t + ": wrong magic accepted");
        b = copy(good); b.putShort(4, (short) 2); ok(refused(b, "version"), t + ": version 2 accepted");
        b = copy(good); b.limit(b.limit() - 4); ok(refused(b, "short"), t + ": truncated file accepted");
        b = copy(good); b.putInt(8, b.limit() + 4); ok(refused(b, "size"), t + ": wrong size field accepted");
        b = copy(good); b.putShort(16, (short) 0); ok(refused(b, "draws"), t + ": no draws accepted");
        b = copy(good); b.putFloat(52, Float.NaN); ok(refused(b, "nan exposure"), t + ": NaN exposure accepted");
        b = copy(good); b.putInt(28, u32(good, 28) + 4); ok(refused(b, "ibase"), t + ": moved index blob accepted");
        // the last index of the last draw points past its vertices
        Gm3dModel m = Gm3dModel.parse(copy(good));
        Gm3dModel.Draw d = m.draws[m.draws.length - 1];
        int ibase = u32(good, 28);
        int drawTable = findDrawTable(good, m);
        int ioff = good.getInt(drawTable + (m.draws.length - 1) * 32 + 24);
        int last = ibase + ioff + (d.indexCount - 1) * d.indexSize;
        b = copy(good);
        if (d.indexSize == 2) b.putShort(last, (short) d.vertexCount); else b.putInt(last, d.vertexCount);
        ok(refused(b, "index"), t + ": index past the vertices accepted");
        // a material naming a texture that does not exist
        b = copy(good);
        int mat0 = drawTable - m.materials.length * 68;
        b.putShort(mat0 + 60, (short) m.textures.length);
        ok(refused(b, "texture ref"), t + ": material with a missing texture accepted");
        // one-byte indices (not a GL ES index type the renderer draws): size field and blob length agree
        b = copy(good);
        int dl = drawTable + (m.draws.length - 1) * 32;
        b.putInt(dl + 12, 1);
        b.putInt(dl + 28, d.indexCount);
        ok(refusedQuietly(b), t + ": one-byte indices accepted");
        // a draw whose vertices run past the vertex blob
        b = copy(good);
        b.putInt(drawTable + 16, ibase);
        ok(refused(b, "vertex range"), t + ": vertex range outside its blob accepted");
        if (m.textures.length > 0) {
            // a colour (not normal) map: only the format check can refuse its format
            int j = 0;
            for (; j < m.textures.length; j++) {
                boolean normal = false;
                for (Gm3dModel.Material mt : m.materials) normal |= mt.normalTex == j;
                if (!normal) break;
            }
            ok(j < m.textures.length, t + ": a colour map to corrupt");
            int texJ = texAt(m, j);
            b = copy(good);
            b.putInt(texJ, m.textures[j].format == Gm3dModel.RGB8_ETC2 || m.textures[j].format == Gm3dModel.SRGB8_ETC2 ? 0x8D64 : 0x93B0);
            ok(refused(b, "colour format"), t + ": unknown colour map format accepted");
            b = copy(good);
            int tex0 = texTable(good, m);
            // ASTC 4x4: not on the list, same block bytes as EAC RG11 / ETC2 RGBA (so only the format check refuses it)
            if (m.textures[0].format == Gm3dModel.RG11_EAC || m.textures[0].format == Gm3dModel.SRGB8_ALPHA8_ETC2_EAC) b.putInt(tex0, 0x93B0);
            else b.putInt(tex0, 0x8D64);
            ok(refused(b, "format"), t + ": unknown texture format accepted");
            b = copy(good);
            b.putInt(tex0 + 12, good.getInt(tex0 + 12) + 8);   // first level size
            ok(refused(b, "level"), t + ": wrong level size accepted");
        }
    }

    /** Offset of the texture table (after the scene block). */
    private static int texTable(ByteBuffer b, Gm3dModel m) {
        int n = m.scene.dist.length;
        return 32 + 40 + 8 + 4 * n + 24 * m.scene.lights + 24;
    }

    /** Offset of texture {@code j}'s header. */
    private static int texAt(Gm3dModel m, int j) {
        int o = texTable(null, m);
        for (int i = 0; i < j; i++) {
            o += 12;
            for (ByteBuffer l : m.textures[i].levels) {
                o += 4 + l.remaining();
                while (o % 4 != 0) o++;
            }
        }
        return o;
    }

    private static int findDrawTable(ByteBuffer b, Gm3dModel m) {
        int o = texTable(b, m);
        for (Gm3dModel.Texture t : m.textures) {
            o += 12;
            for (ByteBuffer l : t.levels) {
                o += 4 + l.remaining();
                while (o % 4 != 0) o++;
            }
        }
        return o + m.materials.length * 68;
    }

    // ---------------------------------------------------------------- framing: every pose keeps the model in the card
    private static void framing(String t, Gm3dModel m) {
        float worst = 0;
        float[] model = new float[16], vp = new float[16], cam = new float[3];
        float[] mvp = new float[16];
        Gm3dModel.Scene s = m.scene;
        for (int deg = 0; deg < 360; deg += 3) {
            for (float bob : new float[] {0, Gm3dMotion.BOB, -Gm3dMotion.BOB}) {
                Gm3dMotion.camera(s, deg, bob, model, vp, cam);
                Gm3dMotion.mul(mvp, vp, model);
                for (Gm3dModel.Draw d : m.draws) {
                    ByteBuffer v = d.vertices.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                    int st = d.stride();
                    for (int i = 0; i < d.vertexCount; i += 7) {
                        float x = v.getShort(i * st) / 32767f * s.posScale[0] + s.posOffset[0];
                        float y = v.getShort(i * st + 2) / 32767f * s.posScale[1] + s.posOffset[1];
                        float z = v.getShort(i * st + 4) / 32767f * s.posScale[2] + s.posOffset[2];
                        float cx = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12];
                        float cy = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13];
                        float cw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15];
                        worst = Math.max(worst, Math.max(Math.abs(cx / cw), Math.abs(cy / cw)));
                    }
                }
            }
        }
        ok(worst < 1f, t + ": the model leaves the card (|ndc| " + worst + ")");
        ok(worst > 0.85f, t + ": the model is far too small in the card (|ndc| " + worst + ")");
        // the hero pose and its sway (+-12 degrees, the render's framing) are centred and fill the card to the margin
        float x0 = 9, x1 = -9, y0 = 9, y1 = -9;
        for (float yaw : new float[] {-12, -6, 0, 6, 12}) {
            Gm3dMotion.camera(s, yaw, 0, model, vp, cam);
            Gm3dMotion.mul(mvp, vp, model);
            for (Gm3dModel.Draw d : m.draws) {
                ByteBuffer v = d.vertices.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                int st = d.stride();
                for (int i = 0; i < d.vertexCount; i++) {
                    float x = v.getShort(i * st) / 32767f * s.posScale[0] + s.posOffset[0];
                    float y = v.getShort(i * st + 2) / 32767f * s.posScale[1] + s.posOffset[1];
                    float z = v.getShort(i * st + 4) / 32767f * s.posScale[2] + s.posOffset[2];
                    float cw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15];
                    float nx = (mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12]) / cw;
                    float ny = (mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13]) / cw;
                    x0 = Math.min(x0, nx); x1 = Math.max(x1, nx); y0 = Math.min(y0, ny); y1 = Math.max(y1, ny);
                }
            }
        }
        // (the render found the centre on a vertex sample, so a few hundredths off in y)
        ok(Math.abs(x0 + x1) < 0.03f && Math.abs(y0 + y1) < 0.04f, t + ": hero pose not centred (" + x0 + ".." + x1 + ", " + y0 + ".." + y1 + ")");
        float half = Math.max(x1 - x0, y1 - y0) / 2;
        ok(half > 0.92f && half < 0.96f, t + ": hero pose fills " + half + " of the card (render margin 1.05: 0.952)");
    }

    // ---------------------------------------------------------------- motion
    private static void motion() {
        ok(Gm3dMotion.yawAt(0) == 0f, "turn starts at the hero pose");
        ok(Gm3dMotion.yawAt(-5) == 0f, "negative time");
        // the strip's turn frame shown at time t is about the continuous angle (one frame = 12 degrees ahead at most)
        for (long t = 0; t < Gm3dMotion.TURN_MS; t += 13) {
            float y = Gm3dMotion.yawAt(t);
            int f = GmModel.frameAt(t);
            float strip = (f == 0 ? 360f : 12f * f);
            ok(strip - y >= -0.01f && strip - y <= 12.01f, "turn at " + t + " ms: " + y + " vs frame " + f);
        }
        float prev = -1;
        boolean mono = true;
        for (long t = 0; t < Gm3dMotion.TURN_MS; t++) { float y = Gm3dMotion.yawAt(t); if (y < prev) mono = false; prev = y; }
        ok(mono, "turn runs one way");
        ok(Math.abs(Gm3dMotion.yawAt(Gm3dMotion.TURN_MS - 1) - 360f) < 0.5f, "turn ends on the hero pose");
        // no jump at the end of the turn: speed near zero, the sway eases in
        float a = Gm3dMotion.yawAt(Gm3dMotion.TURN_MS - 16), b = Gm3dMotion.yawAt(Gm3dMotion.TURN_MS) + 360f,
                c = Gm3dMotion.yawAt(Gm3dMotion.TURN_MS + 16) + 360f;
        ok(Math.abs(b - a) < 0.6f && Math.abs(c - b) < 0.05f, "a jolt where the turn meets the sway: " + a + " " + b + " " + c);
        ok(Math.abs(Gm3dMotion.bobAt(Gm3dMotion.TURN_MS + 16)) < 1e-4f && Gm3dMotion.bobAt(Gm3dMotion.TURN_MS) == 0f, "the float jumps in after the turn");
        float maxStep = 0, maxYaw = 0, maxBob = 0;
        for (long t = Gm3dMotion.TURN_MS; t < Gm3dMotion.TURN_MS + 20_000; t += 16) {
            maxStep = Math.max(maxStep, Math.abs(Gm3dMotion.yawAt(t + 16) - Gm3dMotion.yawAt(t)));
            maxYaw = Math.max(maxYaw, Math.abs(Gm3dMotion.yawAt(t)));
            maxBob = Math.max(maxBob, Math.abs(Gm3dMotion.bobAt(t)));
        }
        ok(maxStep < 0.3f, "sway step per 60 fps frame " + maxStep);
        ok(Math.abs(maxYaw - Gm3dMotion.SWAY_DEG) < 0.05f, "sway amplitude " + maxYaw + " (the strip's idle frames: 7)");
        ok(maxBob > 0 && maxBob <= Gm3dMotion.BOB + 1e-6f, "float amplitude " + maxBob);
        // after the ease-in the sway is the strip's idle angles: idle_i at 7 sin(2 pi i / 24)
        long base = Gm3dMotion.TURN_MS + 10 * Gm3dMotion.SWAY_MS;
        for (int i = 0; i < 24; i++) {
            float y = Gm3dMotion.yawAt(base + i * GmModel.IDLE_STEP_MS);
            ok(Math.abs(y - 7f * (float) Math.sin(2 * Math.PI * i / 24)) < 0.01f, "idle angle " + i + ": " + y);
        }
        ok(Gm3dMotion.bobAt(Gm3dMotion.TURN_MS - 1) == 0f, "no float during the turn");
        // distance table: exact at its steps, linear between, wraps around
        Gm3dModel.Scene s = new Gm3dModel.Scene();
        s.dist = new float[] {1, 2, 4, 3};
        s.distStep = 90;
        ok(Gm3dMotion.distAt(s, 0) == 1 && Gm3dMotion.distAt(s, 90) == 2 && Gm3dMotion.distAt(s, 180) == 4, "distance at steps");
        ok(Math.abs(Gm3dMotion.distAt(s, 45) - 1.5f) < 1e-5, "distance between steps");
        ok(Math.abs(Gm3dMotion.distAt(s, 315) - 2f) < 1e-5, "distance wraps 270..360");
        ok(Math.abs(Gm3dMotion.distAt(s, -45) - 2f) < 1e-5, "negative yaw (sway) wraps");
        ok(Math.abs(Gm3dMotion.distAt(s, 405) - 1.5f) < 1e-5, "yaw past 360");
        // camera: the origin is in front, the hero centre (cx, cy) lands in the middle of the card
        s.fov = 24; s.elev = 16; s.tilt = -10; s.yaw0 = -22; s.aspect = 600f / 360f; s.distHero = 2;
        s.dist = new float[] {2, 2, 2, 2};
        s.centerX = 0.05f; s.centerY = -0.07f;
        float[] model = new float[16], vp = new float[16], cam = new float[3];
        Gm3dMotion.camera(s, 0, 0, model, vp, cam);
        float[] o = Gm3dMotion.project(model, vp, 0, 0, 0);
        ok(o[3] > 0 && Math.abs(o[0] / o[3] + 0.05f) < 1e-4 && Math.abs(o[1] / o[3] - 0.07f) < 1e-4,
                "origin projects to minus the hero centre (" + o[0] / o[3] + ", " + o[1] / o[3] + ")");
        ok(Math.abs(cam[1] - 2 * (float) Math.sin(Math.toRadians(16))) < 1e-5 && Math.abs(cam[2] - 2 * (float) Math.cos(Math.toRadians(16))) < 1e-5,
                "camera on the elevated circle");
        // yaw turns about the vertical axis: a point on +x goes to -z at +90 degrees (from yaw0), tilt about x
        Gm3dMotion.camera(s, 22, 0, model, vp, cam);   // yaw0 + 22 = 0: model = tilt only
        float[] p = {model[0], model[1], model[2]};
        ok(Math.abs(p[0] - 1) < 1e-5 && Math.abs(p[1]) < 1e-5 && Math.abs(p[2]) < 1e-5, "x axis unchanged by the tilt");
        Gm3dMotion.camera(s, 112, 0, model, vp, cam);  // yaw 90
        ok(Math.abs(model[0]) < 1e-5 && Math.abs(model[2] + 1) < 1e-5, "yaw 90: +x to -z (three.js rotation.y)");
        // farther away (a side view) the centre shift shrinks with the distance, as the render's view offset
        s.dist = new float[] {4, 4, 4, 4};
        Gm3dMotion.camera(s, 0, 0, model, vp, cam);
        o = Gm3dMotion.project(model, vp, 0, 0, 0);
        ok(Math.abs(o[0] / o[3] + 0.025f) < 1e-4 && Math.abs(o[1] / o[3] - 0.035f) < 1e-4, "centre shift at twice the distance (" + o[0] / o[3] + ")");
        s.dist = new float[] {2, 2, 2, 2};
        Gm3dMotion.camera(s, 22, 0.5f, model, vp, cam);
        ok(Math.abs(model[13] - 0.5f) < 1e-6, "float moves the model up");
    }

    // ---------------------------------------------------------------- gate: the model first, the strip on any failure
    private static void gate() {
        Gm3dGate.sBroken = false;
        Gm3dGate g = new Gm3dGate();
        ok(g.state() == Gm3dGate.OFF && !g.frames() && !g.live(), "starts off");
        ok(g.begin("ps", true, true, 1000), "model tried first");
        ok(g.pending() && !g.frames(), "pending draws neither");
        ok(!g.timedOut(1000 + Gm3dGate.READY_MS - 1), "no timeout before READY_MS");
        ok(Gm3dGate.READY_MS >= 1000 && Gm3dGate.READY_MS <= 3000, "the strip takes over within 3 s (READY_MS " + Gm3dGate.READY_MS + ")");
        g.ready("xbox");
        ok(g.pending(), "ready of another theme ignored");
        g.ready("ps");
        ok(g.live() && !g.frames(), "ready: model on screen");
        ok(!g.timedOut(1000 + 10 * Gm3dGate.READY_MS), "no timeout once live");
        ok(!g.failed("xbox", false), "failure of an old theme: no strip");
        // that failure still keeps this menu on the strip for the next theme
        ok(!g.begin("switch", true, true, 5000) && g.frames(), "after a failure the menu stays on the strip");

        g = new Gm3dGate();
        ok(g.begin("ps", true, true, 0), "fresh menu tries again after a soft failure");
        ok(g.timedOut(Gm3dGate.READY_MS), "no first frame in READY_MS: strip");
        ok(g.frames(), "timeout: strip drawn");
        ok(!g.timedOut(Gm3dGate.READY_MS * 3), "timeout fires once");
        ok(!g.begin("xbox", true, true, 9000), "after a timeout the menu stays on the strip");

        g = new Gm3dGate();
        ok(!g.begin("ps", false, true, 0) && g.frames(), "switched off: strip");
        g = new Gm3dGate();
        ok(!g.begin("ps", true, false, 0) && g.frames(), "no GPU drawing in the window: strip");
        g = new Gm3dGate();
        ok(g.begin("ps", true, true, 0), "tries");
        ok(g.failed("ps", false), "failure of the shown theme: start the strip");
        ok(g.frames(), "failure: strip drawn");
        ok(!g.failed("ps", false), "a second failure does not restart the strip");
        ok(!Gm3dGate.sBroken, "a model failure does not stop GL for the process");
        g = new Gm3dGate();
        ok(g.begin("ps", true, true, 0) && g.failed("ps", true), "hard failure: strip");
        ok(Gm3dGate.sBroken, "hard failure stops GL for the process");
        ok(!new Gm3dGate().begin("xbox", true, true, 0), "no GL in later menus after a hard failure");
        Gm3dGate.sBroken = false;
        g = new Gm3dGate();
        g.begin("ps", true, true, 0);
        g.off();
        ok(g.state() == Gm3dGate.OFF && !g.frames() && !g.pending(), "computer: neither");
        ok(!g.failed("ps", false), "failure after the card left the console: no strip");
        ok(!g.timedOut(99_999), "no timeout when off");
    }
}
