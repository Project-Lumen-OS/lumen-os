// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

/**
 * The sink's TCP connection to the source's RTSP control port, driving a {@link WfdSession}.
 * Blocking java.net only (runs on its own thread on the device; the host tests run it against a
 * scripted source on localhost). {@link #run} returns when the session ends for any reason.
 */
public final class SinkConnection implements WfdSession.Out {
    public interface Clock {
        long nowMs();
    }

    private final String host;
    private final int port;
    private final Clock clock;
    private volatile Socket socket;
    private volatile OutputStream os;
    private volatile boolean stopped;
    private WfdSession session;

    public SinkConnection(String host, int port, Clock clock) {
        this.host = host;
        this.port = port;
        this.clock = clock;
    }

    public void attach(WfdSession s) {
        session = s;
    }

    /** Use an already connected socket (found by a connect sweep) instead of connecting in {@link #run}. */
    public void adopt(Socket connected) {
        socket = connected;
    }

    /**
     * Connects (retrying for up to {@code connectWindowMs}: the source opens its port a moment after
     * the Wi-Fi Direct group forms), then reads and dispatches until the session closes, times out,
     * or {@link #stop} is called. Returns a short reason.
     */
    public String run(long connectWindowMs, int connectTimeoutMs) {
        long deadline = clock.nowMs() + connectWindowMs;
        IOException last = null;
        while (!stopped && socket == null) {
            Socket s = new Socket();
            try {
                s.setTcpNoDelay(true);
                s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
                socket = s;
                break;
            } catch (IOException e) {
                last = e;
                closeQuietly(s);
                if (clock.nowMs() >= deadline) return "connect failed: " + e.getMessage();
                sleep(300);
            }
        }
        if (stopped || socket == null) return "stopped" + (last != null ? " (" + last.getMessage() + ")" : "");
        try {
            socket.setSoTimeout(1000);
            os = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            RtspParser parser = new RtspParser();
            byte[] buf = new byte[8192];
            synchronized (this) {
                session.start(clock.nowMs());
            }
            while (!stopped) {
                int n;
                try {
                    n = in.read(buf);
                } catch (SocketTimeoutException e) {
                    synchronized (this) {
                        if (session.timedOut(clock.nowMs())) return "source silent (keep-alive timeout)";
                    }
                    continue;
                }
                if (n < 0) return "source closed the connection";
                List<RtspMessage> msgs = parser.feed(buf, 0, n);
                synchronized (this) {
                    for (RtspMessage m : msgs) session.onMessage(m, clock.nowMs());
                    if (session.state() == WfdSession.State.CLOSED) return "session closed";
                }
            }
            return "stopped";
        } catch (RtspParser.ProtocolException e) {
            return "protocol error: " + e.getMessage();
        } catch (IOException e) {
            return stopped ? "stopped" : "connection error: " + e.getMessage();
        } finally {
            closeQuietly(socket);
        }
    }

    /** Sends a message (called by the session under this object's lock). */
    @Override
    public void send(RtspMessage m) {
        OutputStream o = os;
        if (o == null) return;
        try {
            o.write(m.serialize());
            o.flush();
        } catch (IOException e) {
            closeQuietly(socket);
        }
    }

    /** M13 from the media thread. */
    public synchronized boolean requestIdr() {
        return session != null && session.requestIdr(clock.nowMs());
    }

    /** Polite end: M8 TEARDOWN, then the socket closes when the source answers or after a short wait. */
    public void teardown(long waitMs) {
        synchronized (this) {
            if (session != null) session.teardown();
        }
        long end = clock.nowMs() + waitMs;
        while (clock.nowMs() < end) {
            synchronized (this) {
                if (session == null || session.state() == WfdSession.State.CLOSED) break;
            }
            sleep(50);
        }
        stop();
    }

    public void stop() {
        stopped = true;
        closeQuietly(socket);
    }

    public String localAddress() {
        Socket s = socket;
        return s != null && s.getLocalAddress() != null ? s.getLocalAddress().getHostAddress() : null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(Socket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
