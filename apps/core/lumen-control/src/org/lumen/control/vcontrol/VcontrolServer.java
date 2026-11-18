// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.lumen.control.utils.LanAddress;

/**
 * The sockets of the stock phone remote: UDP discovery and commands answered to the phone's UDP port, TCP framed
 * commands answered on the same connection. LAN senders only; each socket has its own daemon thread.
 */
public final class VcontrolServer {
    /** Stock FindDevice and UdpThread receive buffers. */
    static final int UDP_BUFFER = 1024 * 8;
    /** A phone that sends nothing (stock heartbeats every few seconds) is dropped after this. */
    static final int TCP_IDLE_MS = 5 * 60 * 1000;
    /** Open TCP connections at most; the next one is closed at once. */
    static final int MAX_CONNECTIONS = 8;

    /** Where the server writes what goes wrong (a log on Android, nothing in tests). */
    public interface Log {
        void warn(String what, Throwable t);
    }

    private final Vcontrol vcontrol;
    private final Endpoints ports;
    private final Log log;
    /** Which senders are answered: {@link LanAddress#isLan} (tests pass their own). */
    final Predicate<InetAddress> lan;
    private final List<DatagramSocket> udp = new ArrayList<>();
    private final List<Socket> clients = new ArrayList<>();
    private DatagramSocket replies;
    private volatile ServerSocket tcp;
    private volatile boolean running;

    public VcontrolServer(Vcontrol vcontrol, Endpoints ports, Log log) {
        this(vcontrol, ports, log, LanAddress::isLan);
    }

    VcontrolServer(Vcontrol vcontrol, Endpoints ports, Log log, Predicate<InetAddress> lan) {
        this.vcontrol = vcontrol;
        this.ports = ports;
        this.log = log;
        this.lan = lan;
    }

    /** Opens every socket it can; a port taken by someone else leaves that input off and the others on. */
    public synchronized void start() {
        if (running) return;
        running = true;
        try {
            replies = new DatagramSocket();
        } catch (IOException e) {
            log.warn("reply socket", e);
        }
        listenUdp(ports.discovery, "vcontrol-discovery");
        listenUdp(ports.commands, "vcontrol-udp");
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(ports.tcp));
            tcp = s;
            thread("vcontrol-tcp", () -> accept(s));
        } catch (IOException e) {
            log.warn("tcp " + ports.tcp, e);
        }
    }

    public synchronized void stop() {
        running = false;
        for (DatagramSocket s : udp) s.close();
        udp.clear();
        if (replies != null) replies.close();
        closeQuietly(tcp);
        for (Socket c : clients) closeQuietly(c);
        clients.clear();
    }

    /** The port the TCP input listens on, null while it does not (stock Params.isTcp and port). */
    public Integer tcpPort() {
        ServerSocket s = tcp;
        return s == null || s.isClosed() ? null : Integer.valueOf(s.getLocalPort());
    }

    private void listenUdp(int port, String name) {
        try {
            DatagramSocket s = new DatagramSocket(null);
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port));
            udp.add(s);
            thread(name, () -> receive(s));
        } catch (IOException e) {
            log.warn("udp " + port, e);
        }
    }

    private void receive(DatagramSocket s) {
        byte[] buf = new byte[UDP_BUFFER];
        while (running && !s.isClosed()) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                s.receive(p);
            } catch (IOException e) {
                if (running) log.warn("udp receive", e);
                return;
            }
            InetAddress from = p.getAddress();
            if (!lan.test(from)) continue;
            String text = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
            String reply = answer(text, Peer.udp(from.getHostAddress()));
            if (reply != null) sendUdp(reply, from);
        }
    }

    private void sendUdp(String reply, InetAddress to) {
        DatagramSocket r = replies;
        if (r == null) return;
        byte[] b = reply.getBytes(StandardCharsets.UTF_8);
        try {
            r.send(new DatagramPacket(b, b.length, to, ports.phone));
        } catch (IOException e) {
            log.warn("udp send", e);
        }
    }

    private void accept(ServerSocket s) {
        while (running && !s.isClosed()) {
            Socket c;
            try {
                c = s.accept();
            } catch (IOException e) {
                if (running) log.warn("tcp accept", e);
                return;
            }
            if (!lan.test(c.getInetAddress()) || !admit(c)) {
                closeQuietly(c);
                continue;
            }
            thread("vcontrol-tcp-client", () -> serve(c));
        }
    }

    private synchronized boolean admit(Socket c) {
        if (clients.size() >= MAX_CONNECTIONS) return false;
        clients.add(c);
        return true;
    }

    private synchronized void forget(Socket c) {
        clients.remove(c);
    }

    private void serve(Socket c) {
        Peer peer = Peer.tcp(c.getRemoteSocketAddress().toString());
        try {
            c.setSoTimeout(TCP_IDLE_MS);
            c.setTcpNoDelay(true);
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();
            String body;
            while (running && (body = Frame.read(in)) != null) {
                String reply = answer(body, peer);
                if (reply != null) {
                    out.write(Frame.encode(reply));
                    out.flush();
                }
            }
        } catch (IOException e) {
            if (running) log.warn("tcp client", e);
        } finally {
            forget(c);
            closeQuietly(c);
        }
    }

    /** One message to its answer; nothing a phone sends may stop an input. */
    private String answer(String text, Peer peer) {
        try {
            return vcontrol.handle(Request.parse(text), peer, tcpPort());
        } catch (RuntimeException e) {
            log.warn("message", e);
            return null;
        }
    }

    /**
     * A daemon thread whose end, whatever the cause, is only logged: an uncaught throwable in any thread ends the
     * Android process, and this one is the projector app (menu, keys).
     */
    private void thread(String name, Runnable r) {
        Thread t = new Thread(() -> {
            try {
                r.run();
            } catch (Throwable e) {
                log.warn(name + " stopped", e);
            }
        }, name);
        t.setDaemon(true);
        t.start();
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
