// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** A local drive (the projector's storage, a USB drive, an SD card): plain file paths under its root. */
public final class LocalFs implements Fs {
    private final File root;
    private final String rootPath;

    public LocalFs(File root) {
        this.root = root;
        this.rootPath = root.getAbsolutePath();
    }

    public File root() {
        return root;
    }

    @Override
    public String id() {
        return "local:" + rootPath;
    }

    @Override
    public boolean writable() {
        return root.canWrite();
    }

    /** True when {@code path} lies on this drive (no ".." out of it). */
    public boolean holds(String path) {
        try {
            String p = new File(path).getCanonicalPath(), r = root.getCanonicalPath();
            return p.equals(r) || p.startsWith(r + "/");
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<Entry> list(String dir) throws IOException {
        File[] all = new File(dir).listFiles();
        if (all == null) throw new IOException("cannot read " + dir);
        List<Entry> out = new ArrayList<>(all.length);
        for (File f : all) {
            if (Names.hidden(f.getName())) continue;
            out.add(entry(f));
        }
        return out;
    }

    static Entry entry(File f) {
        boolean d = f.isDirectory();
        return new Entry(f.getName(), f.getAbsolutePath(), d ? Kind.FOLDER : Kinds.of(f.getName()), d ? 0 : f.length(),
                f.lastModified());
    }

    @Override
    public Entry stat(String path) {
        File f = new File(path);
        return f.exists() ? entry(f) : null;
    }

    @Override
    public InputStream read(String path) throws IOException {
        return new FileInputStream(path);
    }

    @Override
    public OutputStream create(String path) throws IOException {
        File f = new File(path);
        if (f.isDirectory()) throw new IOException("a folder is there: " + path);
        return new FileOutputStream(f);
    }

    @Override
    public void mkdir(String path) throws IOException {
        File f = new File(path);
        if (!f.mkdir() && !f.isDirectory()) throw new IOException("cannot create " + path);
    }

    @Override
    public void remove(String path) throws IOException {
        File f = new File(path);
        if (!f.delete() && f.exists()) throw new IOException("cannot delete " + path);
    }

    @Override
    public boolean rename(String from, String to) {
        File t = new File(to);
        return !t.exists() && new File(from).renameTo(t);
    }

    @Override
    public long free(String dir) {
        File f = new File(dir);
        while (f != null && !f.exists()) f = f.getParentFile();
        return f == null ? -1 : f.getUsableSpace();
    }

    @Override
    public String child(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    @Override
    public String parent(String path) {
        if (path.equals(rootPath)) return null;
        String p = new File(path).getParent();
        return p == null || p.length() < rootPath.length() ? null : p;
    }

    @Override
    public boolean same(Fs other) {
        return other instanceof LocalFs && ((LocalFs) other).rootPath.equals(rootPath);
    }
}
