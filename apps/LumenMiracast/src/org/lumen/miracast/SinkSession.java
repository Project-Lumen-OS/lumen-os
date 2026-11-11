// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import org.lumen.miracast.core.AudioPayload;
import org.lumen.miracast.core.MediaPipeline;
import org.lumen.miracast.core.RtpReceiver;
import org.lumen.miracast.core.SinkConnection;
import org.lumen.miracast.core.TsDemuxer;
import org.lumen.miracast.core.WfdFormats;
import org.lumen.miracast.core.WfdSession;

import java.net.SocketException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One mirroring session: find the source, run the RTSP control connection, receive RTP, decode.
 * Lives on its own worker thread; the service hears back through {@link Host} (from any thread).
 */
final class SinkSession implements WfdSession.Listener, MediaPipeline.Output, VideoRenderer.Events {
    private static final String TAG = "LumenMiracast";
    /** No RTP for this long while playing = the source is gone (it may not have sent TEARDOWN). */
    static final long MEDIA_SILENCE_MS = 15_000;

    interface Host {
        void onSessionPlaying(SinkSession s);

        void onSessionFirstFrame(SinkSession s);

        void onSessionVideoSize(SinkSession s, int w, int h);

        void onSessionEnded(SinkSession s, String reason);
    }

    final String peerName;
    private final Context ctx;
    private final Host host;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean ended = new AtomicBoolean();
    private final SinkConnection.Clock clock = SystemClock::elapsedRealtime;
    private volatile SinkConnection conn;
    private RtpReceiver rx;
    private MediaPipeline pipeline;
    // volatile both ways (with pendingSurface): a surface that arrives while run() builds the renderer is
    // seen by one side or the other, never lost
    private volatile VideoRenderer video;
    private volatile AudioRenderer audio;
    private volatile boolean playing;
    private volatile Surface pendingSurface;

    SinkSession(Context ctx, Host host, String peerName) {
        this.ctx = ctx.getApplicationContext();
        this.host = host;
        this.peerName = peerName;
    }

    boolean playing() {
        return playing;
    }

    void start(WifiP2pInfo info, WifiP2pGroup group, int controlPort, WfdSession.Config cfg) {
        Thread t = new Thread(() -> run(info, group, controlPort, cfg), "miracast-session");
        t.start();
    }

    private void run(WifiP2pInfo info, WifiP2pGroup group, int controlPort, WfdSession.Config cfg) {
        String why = "ended";
        Thread rxThread = null;
        try {
            pipeline = new MediaPipeline(this);
            video = new VideoRenderer(this);
            audio = new AudioRenderer();
            Surface s = pendingSurface;
            if (s != null) video.setSurface(s);
            rx = bindRtp(cfg.rtpPort);
            if (rx == null) {
                why = "no free RTP port";
                return;
            }
            cfg.rtpPort = rx.localPort();
            rxThread = new Thread(rx, "miracast-rtp");
            rxThread.setPriority(Thread.MAX_PRIORITY);
            rxThread.start();
            SourceFinder.Result r = SourceFinder.find(ctx, info, group, controlPort, 20_000, cancelled);
            if (r == null) {
                why = cancelled.get() ? "cancelled" : "source not found";
                return;
            }
            Log.i(TAG, "rtsp to " + r.host + ":" + controlPort + " rtp port " + cfg.rtpPort);
            SinkConnection c = new SinkConnection(r.host, controlPort, clock);
            if (r.connected != null) c.adopt(r.connected);
            WfdSession ws = new WfdSession(cfg, r.host, c, this);
            c.attach(ws);
            conn = c;
            if (cancelled.get()) {
                why = "cancelled";
                return;
            }
            Thread watchdog = new Thread(this::watchMedia, "miracast-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            why = c.run(15_000, 3_000);
        } catch (Throwable t) {
            Log.e(TAG, "session failed", t);
            why = "error: " + t;
        } finally {
            if (rx != null) rx.close();
            if (rxThread != null) {
                try {
                    rxThread.join(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            if (pipeline != null) Log.i(TAG, "session stats: " + pipeline.stats());
            if (video != null) {
                Log.i(TAG, video.stats());
                video.release();
            }
            if (audio != null) {
                Log.i(TAG, audio.stats());
                audio.release();
            }
            playing = false;
            if (ended.compareAndSet(false, true)) host.onSessionEnded(this, why);
        }
    }

    private RtpReceiver bindRtp(int port) {
        for (int p = port; p < port + 20; p += 2) {
            try {
                return new RtpReceiver(p, pipeline, clock);
            } catch (SocketException e) {
                Log.w(TAG, "rtp port " + p + " busy");
            }
        }
        return null;
    }

    private void watchMedia() {
        while (!ended.get() && !cancelled.get()) {
            SystemClock.sleep(1000);
            RtpReceiver r = rx;
            if (playing && r != null) {
                long last = r.lastPacketMs();
                long since = clock.nowMs() - (last == 0 ? playingSinceMs : last);
                if (since > MEDIA_SILENCE_MS) {
                    Log.w(TAG, "no media for " + since + " ms; ending");
                    stop(false);
                    return;
                }
            }
        }
    }

    private volatile long playingSinceMs;

    /** polite: send TEARDOWN first (the user ended it); else just close. Any thread. */
    void stop(boolean polite) {
        cancelled.set(true);
        SinkConnection c = conn;
        if (c != null) {
            if (polite) new Thread(() -> c.teardown(800), "miracast-teardown").start();
            else c.stop();
        }
    }

    void setSurface(Surface s) {
        pendingSurface = s;
        VideoRenderer v = video;
        if (v != null) v.setSurface(s);
    }

    // ------------------------------------------------------------------ WfdSession.Listener (session thread)

    @Override
    public void onFormats(WfdFormats.Video v, WfdFormats.Audio a) {
        Log.i(TAG, "formats video=" + v + " audio=" + a);
        if (v != null && video != null) video.setSize(v.width, v.height);
    }

    @Override
    public void onPlaying() {
        playing = true;
        playingSinceMs = clock.nowMs();
        host.onSessionPlaying(this);
    }

    @Override
    public void onClosed(String reason) {
        Log.i(TAG, "rtsp closed: " + reason);
    }

    @Override
    public void onLog(String line) {
        Log.i(TAG, line);
    }

    // ------------------------------------------------------------------ MediaPipeline.Output (RTP thread)

    @Override
    public void onVideo(byte[] d, int off, int len, long ptsUs, boolean keyFrame) {
        VideoRenderer v = video;
        if (v != null) v.submit(d, off, len, ptsUs, keyFrame);
    }

    @Override
    public void onPcm(byte[] pcm, int len, int sampleRate, int channels, long ptsUs) {
        AudioRenderer a = audio;
        if (a != null) a.pcm(pcm, len, sampleRate, channels);
    }

    @Override
    public void onAac(byte[] d, int off, int len, AudioPayload.Adts hdr, long ptsUs) {
        AudioRenderer a = audio;
        if (a != null) a.aac(d, off, len, hdr, ptsUs);
    }

    /** From the pipeline (loss, no key frame yet) and the decoder (start, backlog). Rate limited by the session. */
    @Override
    public void onNeedKeyFrame(String why) {
        SinkConnection c = conn;
        if (c != null && c.requestIdr()) Log.i(TAG, "key frame requested: " + why);
    }

    @Override
    public void onStreams(List<TsDemuxer.Stream> streams) {
        Log.i(TAG, "streams " + streams);
    }

    // ------------------------------------------------------------------ VideoRenderer.Events (video thread)

    @Override
    public void onFirstFrame() {
        Log.i(TAG, "first frame");
        host.onSessionFirstFrame(this);
    }

    @Override
    public void onVideoSize(int width, int height) {
        host.onSessionVideoSize(this, width, height);
    }
}
