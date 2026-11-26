// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Files' rules without Android: kinds, names, order, search, walks, picking, copy / move / delete, archives, DLNA. */
public final class ModelTest {
    static int passed, failed;

    static void ok(boolean c, String what) {
        if (c) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static void eq(Object got, Object want, String what) {
        ok(String.valueOf(got).equals(String.valueOf(want)), what + ": got <" + got + "> want <" + want + ">");
    }

    public static void main(String[] a) throws Exception {
        kinds();
        names();
        sorter();
        search();
        selection();
        places();
        nav();
        File tmp = Files.createTempDirectory("files-test").toFile();
        try {
            transfer(tmp);
            walk(tmp);
            archives(tmp);
        } finally {
            rm(tmp);
        }
        dlna();
        System.out.println("files model tests passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static void kinds() {
        eq(Kinds.of("Film.MKV"), Kind.VIDEO, "mkv any case");
        eq(Kinds.of("song.flac"), Kind.MUSIC, "flac");
        eq(Kinds.of("IMG_1.heic"), Kind.PHOTO, "heic");
        eq(Kinds.of("kodi.apk"), Kind.APK, "apk");
        eq(Kinds.of("a.tar.gz"), Kind.ARCHIVE, "tar.gz");
        eq(Kinds.of("doc.pdf"), Kind.DOCUMENT, "pdf");
        eq(Kinds.of("noext"), Kind.OTHER, "no ending");
        eq(Kinds.of("trail."), Kind.OTHER, "dot at the end");
        eq(Kinds.mime("a.mkv"), "video/x-matroska", "mkv type");
        eq(Kinds.mime("a.apk"), Kinds.APK_MIME, "apk type");
        eq(Kinds.mime("a.xyz"), null, "unknown type left to the system");
        eq(Kinds.fallback(Kind.VIDEO), "video/*", "video wildcard");
        eq(Kinds.fallback(Kind.DOCUMENT), "application/octet-stream", "other wildcard");
        eq(Kinds.ofMime("video/mp4"), Kind.VIDEO, "server type video");
        eq(Kinds.ofMime("image/jpeg"), Kind.PHOTO, "server type photo");
        eq(Kinds.ofMime(null), Kind.OTHER, "no server type");
        eq(Kinds.extOf("video/mp4"), "mp4", "ending of a type");
        eq(ArchiveFormat.of("x.ZIP"), ArchiveFormat.ZIP, "zip format");
        eq(ArchiveFormat.of("x.tgz"), ArchiveFormat.TAR_GZ, "tgz format");
        eq(ArchiveFormat.of("x.tar.gz"), ArchiveFormat.TAR_GZ, "tar.gz format");
        eq(ArchiveFormat.of("x.tar"), ArchiveFormat.TAR, "tar format");
        eq(ArchiveFormat.of("x.rar"), null, "rar is not unpacked");
    }

    static void names() {
        ok(Names.hidden(".git") && Names.hidden("._a.mkv") && Names.hidden("$RECYCLE.BIN") && Names.hidden("System Volume Information")
                && Names.hidden("LOST.DIR") && Names.hidden(""), "hidden names");
        ok(!Names.hidden("Movies") && !Names.hidden("a.b"), "shown names");
        List<String> have = Arrays.asList("a.txt", "a (2).txt", "b", "c.tar.gz");
        eq(Names.free("new.txt", have::contains), "new.txt", "free name kept");
        eq(Names.free("a.txt", have::contains), "a (3).txt", "clash: next free number");
        eq(Names.free("b", have::contains), "b (2)", "clash without an ending");
        eq(Names.free("c.tar.gz", have::contains), "c (2).tar.gz", "double ending stays whole");
        eq(Names.free(".env", s -> s.equals(".env")), ".env (2)", "dot file has no ending");
        eq(Names.stem("Movies.zip"), "Movies", "stem of zip");
        eq(Names.stem("x.tar.gz"), "x", "stem of tar.gz");
        eq(Names.stem(".zip"), ".zip", "stem never empty");
        eq(Names.check("  ", null), NameProblem.EMPTY, "empty");
        eq(Names.check("..", null), NameProblem.DOTS, "dots");
        eq(Names.check("a/b", null), NameProblem.BAD_CHAR, "slash");
        eq(Names.check("a:b", null), NameProblem.BAD_CHAR, "colon (FAT)");
        eq(Names.check("a?b", null), NameProblem.BAD_CHAR, "question mark (FAT)");
        char[] l = new char[200];
        Arrays.fill(l, '\u0436');
        eq(Names.check(new String(l), null), NameProblem.TOO_LONG, "over 255 bytes in UTF-8");
        eq(Names.check("Фильмы", s -> s.equals("Фильмы")), NameProblem.TAKEN, "taken");
        eq(Names.check("Фильмы 2024", s -> false), null, "a good name");
    }

    static Entry e(String name, Kind k, long size, long date) {
        return new Entry(name, "/x/" + name, k, size, date);
    }

    static String names(List<Entry> l) {
        StringBuilder b = new StringBuilder();
        for (Entry x : l) b.append(b.length() == 0 ? "" : ",").append(x.name);
        return b.toString();
    }

    static void sorter() {
        List<Entry> l = new ArrayList<>(Arrays.asList(e("b.mkv", Kind.VIDEO, 5, 3), e("Z", Kind.FOLDER, 0, 1), e("a.mkv", Kind.VIDEO, 9, 1),
                e("c.mkv", Kind.VIDEO, 1, 9), e("A", Kind.FOLDER, 0, 2)));
        Sorter.sort(l, Sort.NAME, String::compareToIgnoreCase);
        eq(names(l), "A,Z,a.mkv,b.mkv,c.mkv", "by name, folders first");
        Sorter.sort(l, Sort.DATE, String::compareToIgnoreCase);
        eq(names(l), "A,Z,c.mkv,b.mkv,a.mkv", "by date, newest first, folders first");
        Sorter.sort(l, Sort.SIZE, String::compareToIgnoreCase);
        eq(names(l), "A,Z,a.mkv,b.mkv,c.mkv", "by size, largest first, folders by name");
        eq(Sorter.cap(l, 3), 2, "cut count");
        eq(names(l), "A,Z,a.mkv", "cut keeps the first rows");
        eq(Sorter.next(Sort.NAME), Sort.DATE, "next order");
        eq(Sorter.next(Sort.SIZE), Sort.NAME, "order wraps");
    }

    static void search() {
        eq(Search.norm("  Ёлки_2.Новый-ГОД "), "елки 2 новыи год", "norm: case, ё, separators, accents");
        ok(Search.matches("Ёлки 2 (2011).mkv", Search.norm("елки 2011")), "every word somewhere in the name");
        ok(!Search.matches("Ёлки 2 (2011).mkv", Search.norm("елки 2012")), "a missing word");
        ok(Search.matches("Amélie.mkv", Search.norm("amelie")), "accents do not matter");
        ok(!Search.matches("anything", ""), "empty query finds nothing");
    }

    static void selection() {
        Selection s = new Selection();
        Entry a = e("a", Kind.VIDEO, 10, 0), b = e("b", Kind.FOLDER, 0, 0), c = e("c", Kind.MUSIC, 5, 0);
        ok(s.toggle(a) && s.toggle(b) && s.has(a), "toggle on");
        ok(!s.toggle(a) && !s.has(a), "toggle off");
        s.toggle(c);
        eq(names(s.items()), "b,c", "picking order");
        eq(s.bytes(), 5, "bytes of picked files");
        s.keepOnly(Arrays.asList(c));
        eq(names(s.items()), "c", "gone rows drop out");
        s.all(Arrays.asList(a, b));
        eq(s.count(), 3, "select all adds");
        s.clear();
        ok(s.empty(), "cleared");
    }

    static void places() {
        List<Place> l = Arrays.asList(new Place(PlaceKind.USB, "Kingston", "/storage/B", 100, 40), new Place(PlaceKind.SD, "Card", "/storage/C", -1, -1),
                new Place(PlaceKind.INTERNAL, "Projector storage", "/storage/emulated/0", 10, 5), new Place(PlaceKind.USB, "Card", "/storage/D", 1, 1));
        List<Place> o = Places.order(l, String::compareToIgnoreCase);
        eq(o.get(0).root + o.get(1).root + o.get(2).root + o.get(3).root, "/storage/emulated/0/storage/D/storage/C/storage/B",
                "internal first, then by name, USB before SD on a tie");
        eq(Places.of(l, "/storage/B/x/y").label, "Kingston", "place of a path");
        ok(Places.of(l, "/storage/Bx/y") == null, "a longer root name is not inside");
        eq(Math.round(l.get(0).used() * 100), 60, "used share");
        eq(l.get(1).used(), 0.0f, "unknown space");
        eq(Sizes.format(1_234_567_890L, new String[]{"KB", "MB", "GB", "TB"}, Locale.ROOT), "1.2 GB", "GB");
        eq(Sizes.format(734_000_000L, new String[]{"KB", "MB", "GB", "TB"}, Locale.ROOT), "734 MB", "MB");
        eq(Sizes.format(1L, new String[]{"KB", "MB", "GB", "TB"}, Locale.ROOT), "1 KB", "a byte shows as 1 KB");
        eq(Sizes.format(0L, new String[]{"KB", "MB", "GB", "TB"}, Locale.ROOT), "0 KB", "empty");
        eq(Sizes.format(2_500_000_000_000L, new String[]{"KB", "MB", "GB", "TB"}, Locale.ROOT), "2.5 TB", "TB");
        eq(Fit.width(300, 200.4f, 44, 560), 300, "a short line keeps the design width");
        eq(Fit.width(300, 290.2f, 44, 560), 335, "a long free space line widens the card to fit it (rounded up)");
        eq(Fit.width(300, 900f, 44, 560), 560, "never wider than the cap");
        eq(Fit.width(300, 256f, 44, 560), 300, "exactly fitting keeps the design width");
        eq(Sizes.percent(50, 200), 25, "percent");
        eq(Sizes.percent(5, 0), 0, "percent of nothing");
        eq(Sizes.percent(300, 200), 100, "percent capped");
    }

    static void nav() {
        Nav n = new Nav();
        LocalFs fs = new LocalFs(new File("/tmp"));
        n.root(new Nav.Loc(LocKind.FOLDER, fs, "/tmp", "USB"));
        ok(!n.canUp() && n.up() == null, "root has no up");
        n.open(new Nav.Loc(LocKind.FOLDER, fs, "/tmp/a", "a"), "a");
        n.open(new Nav.Loc(LocKind.FOLDER, fs, "/tmp/a/b", "b"), "b");
        eq(String.join("/", n.crumbs()), "USB/a/b", "crumbs");
        Nav.Loc up = n.up();
        eq(up.path + " " + up.focus, "/tmp/a b", "BACK focuses the folder it came from");
        ok(n.uses(new LocalFs(new File("/tmp"))) && !n.uses(new LocalFs(new File("/other"))), "drive in use");
    }

    static void write(File f, int bytes) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            byte[] b = new byte[bytes];
            for (int i = 0; i < bytes; i++) b[i] = (byte) (i * 7);
            o.write(b);
        }
    }

    /** A store over another one: other free space, read only. */
    static final class Fake implements Fs {
        final Fs in;
        final long free;
        final boolean rw;

        Fake(Fs in, long free, boolean rw) {
            this.in = in;
            this.free = free;
            this.rw = rw;
        }

        public String id() { return in.id(); }
        public boolean writable() { return rw; }
        public List<Entry> list(String d) throws IOException { return in.list(d); }
        public Entry stat(String p) throws IOException { return in.stat(p); }
        public InputStream read(String p) throws IOException { return in.read(p); }
        public OutputStream create(String p) throws IOException { return in.create(p); }
        public void mkdir(String p) throws IOException { in.mkdir(p); }
        public void remove(String p) throws IOException { in.remove(p); }
        public boolean rename(String a, String b) { return in.rename(a, b); }
        public long free(String d) { return free; }
        public String child(String d, String n) { return in.child(d, n); }
        public String parent(String p) { return in.parent(p); }
        public boolean same(Fs o) { return in.same(o instanceof Fake ? ((Fake) o).in : o); }
    }

    static void transfer(File tmp) throws Exception {
        File d1 = new File(tmp, "drive1"), d2 = new File(tmp, "drive2");
        write(new File(d1, "Films/a.mkv"), 200_000);
        write(new File(d1, "Films/sub/b.srt"), 10);
        write(new File(d1, "c.txt"), 5);
        write(new File(d2, "c.txt"), 1);
        new File(d2, "to").mkdirs();
        LocalFs one = new LocalFs(d1), two = new LocalFs(d2);
        AtomicBoolean stop = new AtomicBoolean();
        List<Entry> films = Arrays.asList(one.stat(d1 + "/Films"), one.stat(d1 + "/c.txt"));
        long[] last = {0, 0};
        Transfer t = new Transfer(one, two, TransferOp.COPY, films, d2.getPath(), stop::get, (done, total, n) -> {
            last[0] = done;
            last[1] = total;
        });
        eq(t.run(), Outcome.DONE, "copy to another drive");
        ok(new File(d2, "Films/a.mkv").length() == 200_000 && new File(d2, "Films/sub/b.srt").length() == 10, "folder copied whole");
        ok(new File(d2, "c (2).txt").length() == 5 && new File(d2, "c.txt").length() == 1, "clash gets a free name, old file kept");
        ok(new File(d1, "Films/a.mkv").exists(), "copy keeps the source");
        eq(last[0] + "/" + last[1], "200015/200015", "progress reaches the total");
        eq(t.made.size(), 2, "made rows");

        eq(new Transfer(one, one, TransferOp.COPY, Arrays.asList(one.stat(d1 + "/Films")), d1 + "/Films/sub", stop::get, null).run(),
                Outcome.INTO_ITSELF, "a folder into itself");
        eq(new Transfer(one, new Fake(two, 100, true), TransferOp.COPY, films, d2 + "/to", stop::get, null).run(), Outcome.NO_SPACE,
                "free space checked first");
        eq(new File(d2, "to").list().length, 0, "nothing written without space");
        eq(new Transfer(one, new Fake(two, -1, false), TransferOp.COPY, films, d2 + "/to", stop::get, null).run(), Outcome.READ_ONLY,
                "read only target");
        eq(new Transfer(new Fake(one, -1, false), two, TransferOp.MOVE, films, d2 + "/to", stop::get, null).run(), Outcome.READ_ONLY,
                "move from a read only store");

        AtomicBoolean halt = new AtomicBoolean();
        Transfer c = new Transfer(one, two, TransferOp.COPY, Arrays.asList(one.stat(d1 + "/Films")), d2 + "/to", halt::get,
                (done, total, n) -> halt.set(true));
        eq(c.run(), Outcome.CANCELLED, "cancel during a copy");
        eq(new File(d2, "to").list().length, 0, "a cancelled copy leaves nothing");

        File m = new File(d1, "c.txt");
        ok(m.setLastModified(1_000_000_000_000L), "old date set");
        new File(d1, "dest").mkdirs();
        eq(new Transfer(one, one, TransferOp.MOVE, Arrays.asList(one.stat(m.getPath())), d1 + "/dest", stop::get, null).run(), Outcome.DONE,
                "move on the same drive");
        File moved = new File(d1, "dest/c.txt");
        ok(moved.exists() && !m.exists() && moved.lastModified() == 1_000_000_000_000L, "a move on one drive is a rename (date kept)");
        eq(new Transfer(one, one, TransferOp.MOVE, Arrays.asList(one.stat(moved.getPath())), d1 + "/dest", stop::get, null).run(), Outcome.DONE,
                "move into its own folder");
        ok(moved.exists() && new File(d1, "dest").list().length == 1, "nothing happens in its own folder");

        eq(new Transfer(one, two, TransferOp.MOVE, Arrays.asList(one.stat(moved.getPath())), d2 + "/to", stop::get, null).run(), Outcome.DONE,
                "move to another drive");
        ok(!moved.exists() && new File(d2, "to/c.txt").length() == 5, "copied, then the source removed");

        Clip copy = new Clip(TransferOp.COPY, one, Arrays.asList(one.stat(d1 + "/Films")));
        ok(copy.fits(one, d1.getPath()), "copy into its own folder (a second copy)");
        ok(!copy.fits(one, d1 + "/Films/sub"), "no copy into itself");
        ok(copy.fits(two, d2.getPath()), "copy to another drive");
        ok(!copy.fits(new Fake(two, -1, false), d2.getPath()), "no paste into a read only place");
        Clip move = new Clip(TransferOp.MOVE, one, Arrays.asList(one.stat(d1 + "/Films")));
        ok(!move.fits(one, d1.getPath()), "no move into the folder it is in");
        ok(move.fits(one, d1 + "/dest"), "move to another folder");

        eq(Transfer.delete(one, Arrays.asList(one.stat(d1 + "/Films")), stop::get), Outcome.DONE, "delete a folder");
        ok(!new File(d1, "Films").exists(), "folder and its files gone");
        eq(Transfer.delete(new Fake(two, -1, false), Arrays.asList(two.stat(d2 + "/to")), stop::get), Outcome.READ_ONLY, "no delete on read only");
        ok(one.parent(d1.getPath()) == null && one.parent(d1 + "/dest").equals(d1.getPath()), "parent stops at the drive");
        ok(one.holds(d1 + "/dest") && !one.holds(d1 + "/../drive2"), "a drive holds only its own paths");
    }

    static void walk(File tmp) throws Exception {
        File d = new File(tmp, "walk");
        write(new File(d, "Android/data/x.mkv"), 1);
        write(new File(d, "a/1.mkv"), 1);
        write(new File(d, "a/b/2.mkv"), 1);
        write(new File(d, "a/b/c.jpg"), 1);
        write(new File(d, ".hidden/3.mkv"), 1);
        write(new File(d, "4.MKV"), 1);
        LocalFs fs = new LocalFs(d);
        Walk.Found f = Walk.run(fs, d.getPath(), "USB", e -> e.kind == Kind.VIDEO, 10, () -> false);
        List<String> n = new ArrayList<>();
        for (Entry e : f.entries) n.add(e.name);
        n.sort(null);
        eq(String.join(",", n), "1.mkv,2.mkv,4.MKV", "kind view: no hidden folders, no Android/ at the top");
        Entry two = null;
        for (Entry e : f.entries) if (e.name.equals("2.mkv")) two = e;
        eq(two == null ? null : two.where, "USB / a / b", "where it lies");
        Walk.Found g = Walk.run(fs, d.getPath(), "USB", e -> e.kind == Kind.VIDEO, 2, () -> false);
        ok(g.entries.size() == 2 && g.more, "limit reached");
        ok(Walk.run(fs, d.getPath(), "USB", e -> true, 10, () -> true).entries.isEmpty(), "stop at once");
        String q = Search.norm("c");
        eq(Walk.run(fs, d.getPath(), "USB", e -> Search.matches(e.name, q), 10, () -> false).entries.size(), 1, "search by name");
    }

    static byte[] zip(String[][] files) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (String[] f : files) {
                z.putNextEntry(new ZipEntry(f[0]));
                if (f.length > 1) z.write(f[1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }

    static void header(ByteArrayOutputStream o, String name, long size, char type, String prefix) {
        byte[] h = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(n, 0, h, 0, Math.min(100, n.length));
        byte[] s = String.format("%011o", size).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(s, 0, h, 124, s.length);
        h[156] = (byte) type;
        System.arraycopy("ustar".getBytes(StandardCharsets.US_ASCII), 0, h, 257, 5);
        if (prefix != null) {
            byte[] p = prefix.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(p, 0, h, 345, p.length);
        }
        o.write(h, 0, 512);
    }

    static void data(ByteArrayOutputStream o, byte[] d) {
        o.write(d, 0, d.length);
        int pad = (512 - d.length % 512) % 512;
        o.write(new byte[pad], 0, pad);
    }

    static byte[] tar(boolean evil) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        header(o, "dir", 0, '5', null);
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        header(o, "a.txt", a.length, '0', "dir");
        data(o, a);
        StringBuilder longName = new StringBuilder("deep/");
        for (int i = 0; i < 10; i++) longName.append("long_part_").append(i).append('_');
        longName.append(".txt");
        byte[] ln = (longName + "\0").getBytes(StandardCharsets.UTF_8);
        header(o, "././@LongLink", ln.length, 'L', null);
        data(o, ln);
        header(o, "ignored", 2, '0', null);
        data(o, "xy".getBytes(StandardCharsets.UTF_8));
        header(o, "link", 0, '2', null);
        if (evil) {
            header(o, "../escape.txt", 1, '0', null);
            data(o, "!".getBytes(StandardCharsets.UTF_8));
        }
        o.write(new byte[1024], 0, 1024);
        return o.toByteArray();
    }

    static void archives(File tmp) throws Exception {
        eq(Archive.safe("a/b.txt"), "a/b.txt", "plain name");
        eq(Archive.safe("./a//b/./c"), "a/b/c", "dots and doubled slashes");
        eq(Archive.safe("..\\evil"), null, "backslash dots");
        eq(Archive.safe("a/../../evil"), null, "dots inside");
        eq(Archive.safe("/etc/passwd"), null, "absolute");
        eq(Archive.safe("C:/x"), null, "drive letter");
        eq(Archive.safe(""), null, "empty");
        File d = new File(tmp, "arch");
        d.mkdirs();
        LocalFs fs = new LocalFs(d);
        byte[] z = zip(new String[][]{{"Movies/"}, {"Movies/a.txt", "one"}, {"Movies/sub/b.txt", "two"}, {"top.txt", "3"}});
        new File(d, "z").mkdirs();
        long[] p = {0, 0};
        eq(Archive.extract(new ByteArrayInputStream(z), ArchiveFormat.ZIP, fs, d + "/z", z.length, Long.MAX_VALUE, () -> false, (a, b, n) -> {
            p[0] = a;
            p[1] = b;
        }), Outcome.DONE, "zip unpacked");
        ok(new File(d, "z/Movies/sub/b.txt").length() == 3 && new File(d, "z/top.txt").length() == 1, "zip folders and files");
        ok(p[1] == z.length && p[0] > 0, "progress over the archive bytes");
        byte[] slip = zip(new String[][]{{"ok.txt", "1"}, {"../../slip.txt", "x"}});
        new File(d, "s").mkdirs();
        eq(Archive.extract(new ByteArrayInputStream(slip), ArchiveFormat.ZIP, fs, d + "/s", slip.length, Long.MAX_VALUE, () -> false, null),
                Outcome.UNSAFE, "zip slip refused");
        ok(!new File(tmp, "slip.txt").exists() && !new File(d, "slip.txt").exists(), "nothing written outside");
        byte[] big = zip(new String[][]{{"big.txt", new String(new char[5000]).replace('\0', 'a')}});
        new File(d, "b").mkdirs();
        eq(Archive.extract(new ByteArrayInputStream(big), ArchiveFormat.ZIP, fs, d + "/b", big.length, 1000, () -> false, null), Outcome.NO_SPACE,
                "stops at the free space");
        new File(d, "c").mkdirs();
        eq(Archive.extract(new ByteArrayInputStream(z), ArchiveFormat.ZIP, fs, d + "/c", z.length, Long.MAX_VALUE, () -> true, null),
                Outcome.CANCELLED, "cancel");
        new File(d, "t").mkdirs();
        byte[] t = tar(false);
        eq(Archive.extract(new ByteArrayInputStream(t), ArchiveFormat.TAR, fs, d + "/t", t.length, Long.MAX_VALUE, () -> false, null),
                Outcome.DONE, "tar unpacked");
        ok(new File(d, "t/dir/a.txt").length() == 5, "ustar prefix joins the name");
        File[] deep = new File(d, "t/deep").listFiles();
        ok(deep != null && deep.length == 1 && deep[0].getName().startsWith("long_part_0") && deep[0].length() == 2, "GNU long name");
        ok(!new File(d, "t/link").exists() && !new File(d, "t/ignored").exists(), "links skipped, long name replaces the short one");
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
            g.write(t);
        }
        new File(d, "g").mkdirs();
        eq(Archive.extract(new ByteArrayInputStream(gz.toByteArray()), ArchiveFormat.TAR_GZ, fs, d + "/g", gz.size(), Long.MAX_VALUE, () -> false,
                null), Outcome.DONE, "tar.gz unpacked");
        ok(new File(d, "g/dir/a.txt").length() == 5, "tar.gz content");
        byte[] et = tar(true);
        new File(d, "e").mkdirs();
        eq(Archive.extract(new ByteArrayInputStream(et), ArchiveFormat.TAR, fs, d + "/e", et.length, Long.MAX_VALUE, () -> false, null),
                Outcome.UNSAFE, "tar escape refused");
        ok(!new File(d, "escape.txt").exists(), "no tar escape written");
        eq(Archive.pax("30 path=a/very/long/name.mkv\n20 mtime=1234567\n"), "a/very/long/name.mkv", "pax path record");
        byte[] neg = new byte[12];
        neg[0] = (byte) 0x80;
        neg[11] = 7;
        eq(Archive.number(neg, 0, 12), 7, "base-256 size");
    }

    static void dlna() throws Exception {
        String ssdp = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.0.2.7:8200/rootDesc.xml\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
        eq(Upnp.location(ssdp), "http://192.0.2.7:8200/rootDesc.xml", "SSDP answer of a media server");
        eq(Upnp.location(ssdp.replace("MediaServer", "MediaRenderer")), null, "a renderer is not a server");
        eq(Upnp.location(ssdp.replace("http://", "file://")), null, "only http descriptions");
        eq(Upnp.location("M-SEARCH * HTTP/1.1\r\n"), null, "a search is not an answer");
        ok(Upnp.search().contains("ST: " + Upnp.ST) && Upnp.search().endsWith("\r\n\r\n"), "search message");
        String desc = "<?xml version=\"1.0\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\"><URLBase>http://192.0.2.7:8200/</URLBase>"
                + "<device><friendlyName>NAS &amp; Films</friendlyName><UDN>uuid:4d696e69</UDN><serviceList>"
                + "<service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>/cm</controlURL></service>"
                + "<service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>ctl/ContentDir</controlURL></service>"
                + "</serviceList></device></root>";
        Upnp.Device dv = Upnp.describe(desc, "http://192.0.2.7:8200/rootDesc.xml");
        eq(dv.name + "|" + dv.udn + "|" + dv.control, "NAS & Films|uuid:4d696e69|http://192.0.2.7:8200/ctl/ContentDir", "description");
        ok(Upnp.describe(desc.replace("ContentDirectory", "AVTransport"), "http://192.0.2.7/") == null, "no ContentDirectory: no server");
        ok(Upnp.browse("64$1 <&>", 0, 200).contains("<ObjectID>64$1 &lt;&amp;&gt;</ObjectID>"), "object id escaped");
        String didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                + "<container id=\"64\" parentID=\"0\"><dc:title>Video</dc:title><upnp:class>object.container.storageFolder</upnp:class></container>"
                + "<item id=\"64$0\" parentID=\"64\"><dc:title>Film</dc:title><upnp:class>object.item.videoItem</upnp:class>"
                + "<res protocolInfo=\"http-get:*:video/x-matroska:*\" size=\"1234\">http://192.0.2.7:8200/MediaItems/1.mkv</res></item>"
                + "<item id=\"64$1\"><dc:title>song.mp3</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class>"
                + "<res protocolInfo=\"rtsp-rtp-udp:*:audio/mpeg:*\">rtsp://x</res><res protocolInfo=\"http-get:*:audio/mpeg:*\">http://192.0.2.7/2.mp3</res></item>"
                + "<item id=\"64$2\"><dc:title>no res</dc:title><upnp:class>object.item.imageItem</upnp:class></item>"
                + "<item id=\"64$3\"><dc:title>Pic</dc:title><upnp:class>object.item.imageItem.photo</upnp:class>"
                + "<res protocolInfo=\"http-get:*:image/jpeg:*\">http://192.0.2.7/3.jpg</res></item></DIDL-Lite>";
        String soap = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                + "<u:BrowseResponse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\"><Result>"
                + didl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</Result><NumberReturned>4</NumberReturned><TotalMatches>9</TotalMatches></u:BrowseResponse></s:Body></s:Envelope>";
        Upnp.Page p = Upnp.result(soap);
        eq(p.returned + "/" + p.total, "4/9", "page counts");
        List<Entry> l = p.entries;
        eq(l.size(), 4, "items without a resource are left out");
        eq(l.get(0).kind + " " + l.get(0).path + " " + l.get(0).name, "FOLDER 64 Video", "container");
        eq(l.get(1).kind + " " + l.get(1).name + " " + l.get(1).size + " " + l.get(1).mime, "VIDEO Film.mkv 1234 video/x-matroska",
                "video item, ending from its type");
        eq(l.get(1).url, "http://192.0.2.7:8200/MediaItems/1.mkv", "item address");
        eq(l.get(2).kind + " " + l.get(2).url, "MUSIC http://192.0.2.7/2.mp3", "the http resource wins over rtsp");
        eq(l.get(3).kind + " " + l.get(3).name, "PHOTO Pic.jpg", "photo item");
        ok(Didl.parse("").isEmpty(), "empty result");
    }

    static void rm(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File c : l) rm(c);
        f.delete();
    }
}
