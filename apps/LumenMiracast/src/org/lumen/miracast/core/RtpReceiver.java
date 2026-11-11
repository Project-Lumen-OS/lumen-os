// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Receives the RTP media datagrams on the sink's RTP port and feeds a {@link MediaPipeline}.
 * Bind before answering SETUP so no first packet is lost. Runs on its own thread until {@link #close}.
 */
public final class RtpReceiver implements Runnable {
    private final DatagramSocket socket;
    private final MediaPipeline pipeline;
    private final SinkConnection.Clock clock;
    private volatile boolean closed;
    private volatile long lastPacketMs;
    private long bytes;

    /** Binds UDP {@code port} on all addresses (the Wi-Fi Direct interface address is not known early). */
    public RtpReceiver(int port, MediaPipeline pipeline, SinkConnection.Clock clock) throws SocketException {
        DatagramSocket s = new DatagramSocket(null);
        s.setReuseAddress(true);
        s.setReceiveBufferSize(2 * 1024 * 1024);
        s.bind(new InetSocketAddress(port));
        s.setSoTimeout(20);
        this.socket = s;
        this.pipeline = pipeline;
        this.clock = clock;
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    @Override
    public void run() {
        byte[] buf = new byte[65536];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (!closed) {
            try {
                p.setLength(buf.length);
                socket.receive(p);
                long now = clock.nowMs();
                lastPacketMs = now;
                bytes += p.getLength();
                pipeline.onDatagram(buf, p.getLength(), now);
            } catch (SocketTimeoutException e) {
                pipeline.tick(clock.nowMs());
            } catch (IOException e) {
                if (!closed) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }
    }

    public long lastPacketMs() {
        return lastPacketMs;
    }

    public long bytes() {
        return bytes;
    }

    public void close() {
        closed = true;
        socket.close();
    }
}
