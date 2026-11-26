// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import org.lumen.home.data.Card;
import org.lumen.home.data.HomeData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * The last screen's data, kept so the next start draws it at once (Lumen Home 1.x did this, Home 2.0 had lost it):
 * magic, version, the stamp of the APK build (resource ids inside are valid only for that build) and the UI locale,
 * then every list of {@link HomeData} with every card field. {@link #same} says whether fresh data would change
 * anything on screen. Pure Java (host test test/home2 snapshot()); the file is data/SnapshotFile.
 */
public final class HomeSnapshot {
    private HomeSnapshot() {}

    static final int MAGIC = 0x4C484D32; // "LHM2"
    static final int VERSION = 3; // 2: the showcase's watch app, button, rating and where to watch; 3: the dock with folders
    private static final int MAX_LIST = 2000;

    /** The snapshot bytes of {@code d}. */
    public static byte[] encode(HomeData d, long apkStamp, String locale) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(16 * 1024);
            DataOutputStream o = new DataOutputStream(bo);
            o.writeInt(MAGIC);
            o.writeInt(VERSION);
            o.writeLong(apkStamp);
            o.writeUTF(s(locale));
            o.write(body(d));
            o.flush();
            return bo.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The data in {@code b}, or null when it is not a snapshot of this APK build and locale, or broken. */
    public static HomeData decode(byte[] b, long apkStamp, String locale) {
        if (b == null) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;
            if (in.readLong() != apkStamp || !in.readUTF().equals(s(locale))) return null;
            HomeData d = new HomeData();
            cards(in, d.apps);
            cards(in, d.dock);
            cards(in, d.hidden);
            cards(in, d.cont);
            cards(in, d.hero);
            cards(in, d.inputs);
            d.online = in.readBoolean();
            if (in.read() != -1) return null;
            d.plan = RowPlan.of(d.hero.size(), d.cont.size(), d.dock.size());
            return d;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** True when {@code a} and {@code b} show and do exactly the same (every list, every card field). */
    public static boolean same(HomeData a, HomeData b) {
        if (a == null || b == null) return a == b;
        return Arrays.equals(body(a), body(b));
    }

    private static byte[] body(HomeData d) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(16 * 1024);
            DataOutputStream o = new DataOutputStream(bo);
            cards(o, d.apps);
            cards(o, d.dock);
            cards(o, d.hidden);
            cards(o, d.cont);
            cards(o, d.hero);
            cards(o, d.inputs);
            o.writeBoolean(d.online);
            o.flush();
            return bo.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void cards(DataOutputStream o, List<Card> l) throws IOException {
        o.writeInt(l.size());
        for (Card k : l) card(o, k);
    }

    private static void cards(DataInputStream in, List<Card> l) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > MAX_LIST) throw new IOException("list " + n);
        for (int i = 0; i < n; i++) l.add(card(in));
    }

    private static void card(DataOutputStream o, Card k) throws IOException {
        o.writeInt(k.kind);
        o.writeUTF(s(k.id));
        o.writeUTF(s(k.title));
        o.writeUTF(s(k.meta));
        o.writeUTF(s(k.left));
        o.writeUTF(s(k.desc));
        o.writeUTF(s(k.pkg));
        o.writeUTF(s(k.appLabel));
        n(o, k.image);
        o.writeInt(k.aspect);
        n(o, k.image2);
        o.writeInt(k.aspect2);
        o.writeInt(k.color);
        n(o, k.intent);
        o.writeInt(k.progress);
        o.writeByte((k.live ? 1 : 0) | (k.isNew ? 2 : 0) | (k.system ? 4 : 0) | (k.showing ? 8 : 0)
                | (k.episodic ? 16 : 0));
        o.writeInt(k.table);
        o.writeLong(k.programId);
        o.writeLong(k.channelId);
        o.writeInt(k.wnType);
        o.writeLong(k.engaged);
        o.writeLong(k.weight);
        n(o, k.video);
        o.writeUTF(s(k.contentId));
        o.writeInt(k.icon);
        o.writeInt(k.state);
        o.writeUTF(s(k.season));
        o.writeUTF(s(k.episode));
        o.writeUTF(s(k.episodeTitle));
        o.writeLong(k.durationMs);
        o.writeLong(k.positionMs);
        o.writeUTF(s(k.year));
        o.writeUTF(s(k.watchPkg));
        o.writeUTF(s(k.watchLabel));
        o.writeUTF(s(k.rating));
        o.writeUTF(s(k.where));
        o.writeUTF(s(k.members));
    }

    private static Card card(DataInputStream in) throws IOException {
        Card k = new Card();
        k.kind = in.readInt();
        k.id = in.readUTF();
        k.title = in.readUTF();
        k.meta = in.readUTF();
        k.left = in.readUTF();
        k.desc = in.readUTF();
        k.pkg = in.readUTF();
        k.appLabel = in.readUTF();
        k.image = readN(in);
        k.aspect = in.readInt();
        k.image2 = readN(in);
        k.aspect2 = in.readInt();
        k.color = in.readInt();
        k.intent = readN(in);
        k.progress = in.readInt();
        int f = in.readByte();
        k.live = (f & 1) != 0;
        k.isNew = (f & 2) != 0;
        k.system = (f & 4) != 0;
        k.showing = (f & 8) != 0;
        k.episodic = (f & 16) != 0;
        k.table = in.readInt();
        k.programId = in.readLong();
        k.channelId = in.readLong();
        k.wnType = in.readInt();
        k.engaged = in.readLong();
        k.weight = in.readLong();
        k.video = readN(in);
        k.contentId = in.readUTF();
        k.icon = in.readInt();
        k.state = in.readInt();
        k.season = in.readUTF();
        k.episode = in.readUTF();
        k.episodeTitle = in.readUTF();
        k.durationMs = in.readLong();
        k.positionMs = in.readLong();
        k.year = in.readUTF();
        k.watchPkg = in.readUTF();
        k.watchLabel = in.readUTF();
        k.rating = in.readUTF();
        k.where = in.readUTF();
        k.members = in.readUTF();
        return k;
    }

    /** writeUTF holds 64 KB of modified UTF-8: longer texts (a synopsis never is) are cut, never a failed save. */
    private static String s(String v) {
        if (v == null) return "";
        return v.length() > 8000 ? v.substring(0, 8000) : v;
    }

    private static void n(DataOutputStream o, String v) throws IOException {
        o.writeBoolean(v != null);
        if (v != null) o.writeUTF(s(v));
    }

    private static String readN(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }
}
