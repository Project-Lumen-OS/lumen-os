package org.lumen.projector.panel;

/**
 * Motion and camera of the real-time controller (Gm3dRenderer), plain Java. The same turntable the frame strip shows
 * (GmModel.frameAt: one 360 degree turn with an ease-out over 1.3 s ending on the hero pose, then a +-7 degree sway
 * every 3.36 s), but continuous at the display rate, with the sway and a slight float easing in after the turn so it
 * starts without a jolt. The camera is the one of the render (tools/gm3d/export.js): vertical field of view, elevated
 * look at the model centre, per-angle distance from the scene's table, the hero centre shifted to the middle.
 * Matrices are column-major float[16] (GLES).
 */
final class Gm3dMotion {
    private Gm3dMotion() {}

    static final long TURN_MS = GmModel.TURN_MS;
    static final long SWAY_MS = GmModel.IDLE_FRAMES * GmModel.IDLE_STEP_MS;   // one sway period, 3.36 s
    static final float SWAY_DEG = 7f;
    /** The sway and float reach full size this long after the turn. */
    static final long EASE_IN_MS = 700;
    /** Vertical float of the idle model, in model units (the model is 1 unit wide): about 1 px of the 150 px card. */
    static final float BOB = 0.006f;

    /** Yaw in degrees from the hero pose {@code t} ms after the turn started. */
    static float yawAt(long t) {
        if (t <= 0) return 0f;
        if (t < TURN_MS) {
            double k = t / (double) TURN_MS;
            return (float) (360.0 * (1 - Math.pow(1 - k, 2.2)));
        }
        long u = t - TURN_MS;
        return (float) (SWAY_DEG * easeIn(u) * Math.sin(2 * Math.PI * (u % SWAY_MS) / SWAY_MS));
    }

    /** Vertical offset of the model {@code t} ms after the turn started (0 during the turn). */
    static float bobAt(long t) {
        if (t < TURN_MS) return 0f;
        long u = t - TURN_MS;
        return (float) (BOB * easeIn(u) * Math.sin(2 * Math.PI * ((u + SWAY_MS / 4) % SWAY_MS) / SWAY_MS));
    }

    private static double easeIn(long u) {
        double k = Math.min(1.0, u / (double) EASE_IN_MS);
        return k * k * (3 - 2 * k);
    }

    /** Camera distance at {@code yawDeg} from the hero pose: the scene's table, linear between its steps. */
    static float distAt(Gm3dModel.Scene s, float yawDeg) {
        int n = s.dist.length;
        double a = yawDeg / s.distStep;
        a = ((a % n) + n) % n;
        int i = (int) Math.floor(a);
        float f = (float) (a - i);
        return s.dist[i % n] * (1 - f) + s.dist[(i + 1) % n] * f;
    }

    /**
     * Fills the model matrix (yaw then tilt, as the render's pivot and tilt groups), the view-projection matrix and
     * the camera position for the pose {@code yawDeg} from the hero pose with a vertical offset {@code bob}.
     */
    static void camera(Gm3dModel.Scene s, float yawDeg, float bob, float[] model, float[] viewProj, float[] cam) {
        double yaw = Math.toRadians(s.yaw0 + yawDeg), tilt = Math.toRadians(s.tilt), el = Math.toRadians(s.elev);
        float d = distAt(s, yawDeg);
        // model = T(0, bob, 0) * Ry(yaw) * Rx(tilt)
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw), ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float[] ry = {cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1};
        float[] rx = {1, 0, 0, 0, 0, ct, st, 0, 0, -st, ct, 0, 0, 0, 0, 1};
        mul(model, ry, rx);
        model[13] += bob;
        // camera on the elevated circle looking at the origin, up = +y
        float ex = 0, ey = (float) (Math.sin(el) * d), ez = (float) (Math.cos(el) * d);
        cam[0] = ex;
        cam[1] = ey;
        cam[2] = ez;
        float[] view = lookAt(ex, ey, ez);
        // perspective (vertical fov), near d/50, far d*4, then the hero centre moved to the middle of the frame
        float near = d / 50f, far = d * 4f;
        float fy = (float) (1.0 / Math.tan(Math.toRadians(s.fov) / 2)), fx = fy / s.aspect;
        float k = s.distHero / d;
        float[] proj = new float[16];
        proj[0] = fx;
        proj[5] = fy;
        proj[10] = (far + near) / (near - far);
        proj[11] = -1;
        proj[14] = 2 * far * near / (near - far);
        // x' = x - cx * k * w, y' = y - cy * k * w (w = -z_eye)
        proj[8] = s.centerX * k;
        proj[9] = s.centerY * k;
        mul(viewProj, proj, view);
    }

    private static float[] lookAt(float ex, float ey, float ez) {
        float zx = ex, zy = ey, zz = ez;                       // eye - target
        float zl = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
        zx /= zl; zy /= zl; zz /= zl;
        float xx = zz, xy = 0, xz = -zx;                       // up(0,1,0) x z
        float xl = (float) Math.sqrt(xx * xx + xz * xz);
        xx /= xl; xz /= xl;
        float yx = zy * xz - zz * xy, yy = zz * xx - zx * xz, yz = zx * xy - zy * xx;
        return new float[] {xx, yx, zx, 0, xy, yy, zy, 0, xz, yz, zz, 0,
                -(xx * ex + xy * ey + xz * ez), -(yx * ex + yy * ey + yz * ez), -(zx * ex + zy * ey + zz * ez), 1};
    }

    /** out = a * b (column-major 4 x 4); out may not be a or b. */
    static void mul(float[] out, float[] a, float[] b) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float v = 0;
                for (int k = 0; k < 4; k++) v += a[k * 4 + r] * b[c * 4 + k];
                out[c * 4 + r] = v;
            }
        }
    }

    /** Clip-space position of model-space point p (for tests). */
    static float[] project(float[] model, float[] viewProj, float x, float y, float z) {
        float[] m = new float[16];
        mul(m, viewProj, model);
        float[] o = new float[4];
        for (int r = 0; r < 4; r++) o[r] = m[r] * x + m[4 + r] * y + m[8 + r] * z + m[12 + r];
        return o;
    }
}
