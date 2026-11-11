// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * H.264 decoder for the mirrored picture: MediaCodec (the MTK hardware decoder on XGIMI units, as on
 * stock) in asynchronous mode on its own thread, output rendered to the receiver's Surface as soon as
 * it is decoded (no presentation clock: lowest latency, as the stock sink does).
 *
 * Latency guard: when more than {@link #MAX_BACKLOG} access units wait for input buffers, the backlog
 * is dropped down to the next key frame and a key frame is requested. A codec error restarts the codec
 * and waits for a key frame.
 */
final class VideoRenderer {
    interface Events {
        void onFirstFrame();

        void onVideoSize(int width, int height);

        void onNeedKeyFrame(String why);
    }

    private static final String TAG = "LumenMiracast";
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    static final int MAX_BACKLOG = 20;

    private static final class Unit {
        byte[] data = new byte[0];
        int len;
        long ptsUs;
        boolean key;
    }

    private final Events events;
    private final HandlerThread thread = new HandlerThread("miracast-video");
    private final Handler h;
    private final ArrayDeque<Unit> pending = new ArrayDeque<>();
    private final ArrayDeque<Unit> pool = new ArrayDeque<>();
    private final ArrayDeque<Integer> inputs = new ArrayDeque<>();
    private MediaCodec codec;
    private Surface surface;
    private int width = 1920, height = 1080;
    private boolean firstFrame, needKey = true;
    private long lastPts;
    private long rendered, dropped;
    private volatile boolean released;

    VideoRenderer(Events events) {
        this.events = events;
        thread.start();
        h = new Handler(thread.getLooper());
    }

    /** Size chosen in M4; a running codec adapts by itself (adaptive playback), so only the next start uses it. */
    void setSize(int w, int hgt) {
        h.post(() -> {
            width = w;
            height = hgt;
        });
    }

    void setSurface(Surface s) {
        h.post(() -> {
            if (s == surface) return;
            stopCodec();
            surface = s;
            if (s != null && s.isValid()) startCodec();
        });
    }

    /** Called on the RTP thread; the data is copied. */
    void submit(byte[] d, int off, int len, long ptsUs, boolean key) {
        if (released) return;
        Unit u;
        synchronized (pool) {
            u = pool.poll();
        }
        if (u == null) u = new Unit();
        if (u.data.length < len) u.data = new byte[Math.max(len, 64 * 1024)];
        System.arraycopy(d, off, u.data, 0, len);
        u.len = len;
        u.ptsUs = ptsUs;
        u.key = key;
        Unit fu = u;
        h.post(() -> enqueue(fu));
    }

    void release() {
        released = true;
        h.post(() -> {
            stopCodec();
            surface = null;
            thread.quitSafely();
        });
    }

    String stats() {
        return "video rendered=" + rendered + " dropped=" + dropped + " backlog=" + pending.size();
    }

    // ------------------------------------------------------------------ codec thread

    private void enqueue(Unit u) {
        if (codec == null) {
            recycle(u);
            needKey = true;
            return;
        }
        if (needKey && !u.key) {
            recycle(u);
            dropped++;
            return;
        }
        needKey = false;
        pending.add(u);
        if (pending.size() > MAX_BACKLOG) {
            // keep the newest key frame (if any) and what follows it; else drop all and ask for one
            Unit keep = null;
            for (Unit x : pending) if (x.key) keep = x;
            while (!pending.isEmpty() && pending.peekFirst() != keep) {
                recycle(pending.pollFirst());
                dropped++;
            }
            if (keep == null) {
                needKey = true;
                events.onNeedKeyFrame("decoder backlog");
            }
            Log.w(TAG, "video backlog dropped; " + stats());
        }
        feed();
    }

    private void feed() {
        while (codec != null && !pending.isEmpty() && !inputs.isEmpty()) {
            int idx = inputs.poll();
            Unit u = pending.poll();
            try {
                ByteBuffer b = codec.getInputBuffer(idx);
                if (b == null || b.capacity() < u.len) {
                    Log.w(TAG, "input buffer too small for " + u.len);
                    codec.queueInputBuffer(idx, 0, 0, lastPts, 0);
                } else {
                    b.clear();
                    b.put(u.data, 0, u.len);
                    long pts = u.ptsUs >= 0 ? u.ptsUs : lastPts + 16_666;
                    lastPts = pts;
                    codec.queueInputBuffer(idx, 0, u.len, pts, u.key ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "queueInputBuffer: " + e);
            } finally {
                recycle(u);
            }
        }
    }

    private void recycle(Unit u) {
        synchronized (pool) {
            if (pool.size() < 32) pool.add(u);
        }
    }

    private void startCodec() {
        try {
            MediaFormat f = MediaFormat.createVideoFormat(MIME, width, height);
            f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            f.setInteger("lowlatency", 1);   // the key the stock XGIMI sink also sets for the MTK decoder
            f.setInteger(MediaFormat.KEY_PRIORITY, 0);
            f.setInteger(MediaFormat.KEY_MAX_WIDTH, 1920);
            f.setInteger(MediaFormat.KEY_MAX_HEIGHT, 1200);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);
            codec = MediaCodec.createDecoderByType(MIME);
            MediaCodecInfo info = codec.getCodecInfo();
            Log.i(TAG, "video decoder " + info.getName() + " " + width + "x" + height);
            codec.setCallback(callback, h);
            codec.configure(f, surface, null, 0);
            codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            codec.start();
            needKey = true;
            firstFrame = false;
            events.onNeedKeyFrame("decoder started");
        } catch (Exception e) {
            Log.e(TAG, "video decoder start failed", e);
            stopCodec();
        }
    }

    private void stopCodec() {
        inputs.clear();
        while (!pending.isEmpty()) recycle(pending.poll());
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception ignored) {
                // releasing anyway
            }
            codec.release();
            codec = null;
        }
    }

    private final MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec c, int index) {
            if (c != codec) return;
            inputs.add(index);
            feed();
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec c, int index, MediaCodec.BufferInfo info) {
            if (c != codec) return;
            try {
                c.releaseOutputBuffer(index, info.size > 0 || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0);
                rendered++;
                if (!firstFrame) {
                    firstFrame = true;
                    events.onFirstFrame();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "releaseOutputBuffer: " + e);
            }
        }

        @Override
        public void onOutputFormatChanged(MediaCodec c, MediaFormat f) {
            if (c != codec) return;
            int w = f.getInteger(MediaFormat.KEY_WIDTH), hh = f.getInteger(MediaFormat.KEY_HEIGHT);
            if (f.containsKey("crop-right") && f.containsKey("crop-left")) {
                w = f.getInteger("crop-right") - f.getInteger("crop-left") + 1;
            }
            if (f.containsKey("crop-bottom") && f.containsKey("crop-top")) {
                hh = f.getInteger("crop-bottom") - f.getInteger("crop-top") + 1;
            }
            Log.i(TAG, "video format " + w + "x" + hh);
            events.onVideoSize(w, hh);
        }

        @Override
        public void onError(MediaCodec c, MediaCodec.CodecException e) {
            if (c != codec) return;
            Log.e(TAG, "video decoder error; restarting", e);
            stopCodec();
            if (surface != null && surface.isValid() && !released) startCodec();
        }
    };
}
