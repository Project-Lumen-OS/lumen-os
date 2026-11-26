// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.data;

import org.lumen.files.model.Entry;
import org.lumen.files.model.Fs;
import org.lumen.files.model.Kind;
import org.lumen.files.model.Kinds;
import org.lumen.files.model.Names;
import org.lumen.files.net.NetServers;
import org.lumen.files.net.Smb2;
import org.lumen.files.net.SmbException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A saved SMB server (Home's own SMB 2/3 client, moved with Files): "/" lists its shares, "/share/a/b" a folder.
 * Read only (copy from it to a drive works). One connection for listing, renewed once when the server dropped the
 * session; every file read opens its own, so a copy can run while the user keeps browsing.
 */
public final class SmbFs implements Fs {
    private interface Op<T> {
        T run() throws IOException;
    }

    private final NetServers.Server server;
    private final Map<String, Integer> trees = new HashMap<>();
    private final Map<String, Entry> seen = new HashMap<>();
    private Smb2 conn;

    public SmbFs(NetServers.Server s) {
        server = s.copy();
    }

    public NetServers.Server server() {
        return server;
    }

    /** Where the server opens: its saved share, else the share list. */
    public String start() {
        return server.share.isEmpty() ? "/" : "/" + server.share;
    }

    @Override
    public String id() {
        return "smb:" + server.id;
    }

    @Override
    public boolean writable() {
        return false;
    }

    private synchronized <T> T call(Op<T> op) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                if (conn == null || conn.isClosed()) {
                    trees.clear();
                    conn = Smb2.connect(server.host, server.port, server.login, server.password);
                }
                return op.run();
            } catch (SmbException e) {
                if (attempt > 0 || !e.sessionLost()) throw e;
                close();
            } catch (IOException e) {
                if (attempt > 0 || conn == null || !conn.isClosed()) throw e;
                close();
            }
        }
    }

    public synchronized void close() {
        if (conn != null) conn.close();
        conn = null;
        trees.clear();
    }

    private int tree(String share) throws IOException {
        Integer t = trees.get(share);
        if (t == null) {
            t = conn.treeConnect(share);
            trees.put(share, t);
        }
        return t;
    }

    static String[] split(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int s = p.indexOf('/');
        return s < 0 ? new String[]{p, ""} : new String[]{p.substring(0, s), p.substring(s + 1)};
    }

    @Override
    public List<Entry> list(String dir) throws IOException {
        return call(() -> {
            List<Entry> out = new ArrayList<>();
            if ("/".equals(dir)) {
                for (String s : conn.listShares()) out.add(keep(new Entry(s, "/" + s, Kind.FOLDER, 0, 0)));
                return out;
            }
            String[] sp = split(dir);
            for (Smb2.Entry e : conn.list(tree(sp[0]), sp[1])) {
                if (e.hidden() || Names.hidden(e.name)) continue;
                out.add(keep(new Entry(e.name, child(dir, e.name), e.dir ? Kind.FOLDER : Kinds.of(e.name), e.dir ? 0 : e.size,
                        e.modified)));
            }
            return out;
        });
    }

    private Entry keep(Entry e) {
        seen.put(e.path, e);
        return e;
    }

    @Override
    public Entry stat(String path) {
        return seen.get(path);
    }

    @Override
    public InputStream read(String path) throws IOException {
        String[] sp = split(path);
        final Smb2 c = Smb2.connect(server.host, server.port, server.login, server.password);
        final Smb2.Handle h;
        try {
            h = c.openFile(c.treeConnect(sp[0]), sp[1]);
        } catch (IOException e) {
            c.close();
            throw e;
        }
        return new InputStream() {
            long off;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] b, int o, int n) throws IOException {
                if (off >= h.size || n <= 0) return off >= h.size ? -1 : 0;
                int want = (int) Math.min(Math.min(n, c.maxRead()), h.size - off);
                int r = c.read(h, off, b, o, want);
                if (r <= 0) return -1;
                off += r;
                return r;
            }

            @Override
            public void close() {
                try {
                    c.closeFile(h);
                } catch (IOException ignored) {
                }
                c.close();
            }
        };
    }

    @Override
    public OutputStream create(String path) throws IOException {
        throw new IOException("read only");
    }

    @Override
    public void mkdir(String path) throws IOException {
        throw new IOException("read only");
    }

    @Override
    public void remove(String path) throws IOException {
        throw new IOException("read only");
    }

    @Override
    public boolean rename(String from, String to) {
        return false;
    }

    @Override
    public long free(String dir) {
        return -1;
    }

    @Override
    public String child(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    @Override
    public String parent(String path) {
        if ("/".equals(path)) return null;
        int s = path.lastIndexOf('/');
        return s <= 0 ? "/" : path.substring(0, s);
    }

    @Override
    public boolean same(Fs other) {
        return other instanceof SmbFs && ((SmbFs) other).server.id.equals(server.id);
    }
}
