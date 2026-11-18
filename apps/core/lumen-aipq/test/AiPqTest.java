package org.lumen.aipq;

import java.util.ArrayList;
import java.util.List;

/**
 * AiPq against a recording vendor: every case is a path of stock XRMService AIPQ.java (decompiled from the Z9X, HB, X50,
 * RS30, Titan and Play 6 firmware, the same logic everywhere) with the vendor writes stock would make.
 */
public final class AiPqTest {
    private static int failed;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failed++;
    }

    /** A vendor whose getPictureMode answers the last write (the Z9X / HB reading; X50 maps 11..17 to 10). */
    static class Fake implements AiPqVendor {
        int pm, hdr, fmt3d = AiPq.NO_3D, lumens = 2;
        boolean vividAuto, vividPlaying, imax, playing, mw, x50Read;
        String top = "org.lumen.home";
        final List<Integer> sets = new ArrayList<>();

        @Override public int pictureMode() { return x50Read && AiPq.aiScene(pm) ? AiPq.PIC_TV : pm; }
        @Override public void setPictureMode(int mode) { sets.add(mode); pm = mode; }
        @Override public int hdrType() { return hdr; }
        @Override public boolean hdrVividAuto(int p) { return vividAuto; }
        @Override public boolean hdrVividPlaying() { return vividPlaying; }
        @Override public boolean imaxPlaying() { return imax; }
        @Override public int current3DFormat() { return fmt3d; }
        @Override public int lumensMode() { return lumens; }
        @Override public boolean videoPlaying() { return playing; }
        @Override public boolean multiWindow() { return mw; }
        @Override public String topPackage() { return top; }

        String sets() { String s = sets.toString(); sets.clear(); return s; }
    }

    static AiPq film(Fake f) { return new AiPq("film", AiPq.PERSIST_DEFAULT, 2, null); }

    /** The menu picks a mode: the vendor applies it and sends event 5 (what stock newsettings + the vendor do). */
    static void menu(AiPq a, Fake f, int mode) throws Exception {
        f.setPictureMode(mode);
        f.sets.clear();
        a.onTvEvent(f, AiPq.EVENT_PICTURE_MODE, "picturemode:" + mode);
    }

    /** The vendor answers our own write with its event, as Msrv_Tv_Control::_setPictureMode does. */
    static void echo(AiPq a, Fake f) throws Exception {
        if (!f.sets.isEmpty()) a.onTvEvent(f, AiPq.EVENT_PICTURE_MODE, "picturemode:" + f.sets.get(f.sets.size() - 1));
    }

    public static void main(String[] args) throws Exception {
        // default scene: ro.xgimi.default.aipq first, persist.xgimi.default.aipq else, 10 when neither is set
        Fake f = new Fake();
        check(film(f).defaultScene() == 16 && "film".equals(film(f).defaultType()), "ro film: default scene 16, type film");
        AiPq p6 = new AiPq("", 16, 0, null);
        check(p6.defaultScene() == 16 && "tv".equals(p6.defaultType()), "persist 16 without ro (Play 6 model file): scene 16, type tv");
        check(new AiPq("", AiPq.PERSIST_DEFAULT, 2, null).defaultScene() == 10, "no model file: scene 10 (stock fallback)");
        check(new AiPq("bogus", 16, 2, null).defaultScene() == 16, "unknown ro name: the persist value");
        check(new AiPq("documentary", 10, 2, null).defaultScene() == 14, "ro documentary: scene 14");

        // start: reset() of the monitor thread
        f = new Fake(); f.pm = 10; AiPq a = film(f); a.start(f);
        check("[16]".equals(f.sets()), "start in AI 10: the default scene 16 is written");
        a.start(f);
        check("[]".equals(f.sets()), "start runs once");
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        check("[]".equals(f.sets()), "start in standard: nothing written");
        f = new Fake(); f.pm = 25; a = film(f); a.start(f);
        check("[]".equals(f.sets()), "start in office (25, not an AI scene): nothing written");

        // the menu picks AI from standard: 10 -> 16 at once
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        menu(a, f, 10);
        check("[16]".equals(f.sets.toString()), "menu AI from standard: the vendor event 10 brings scene 16");
        echo(a, f); f.sets();
        menu(a, f, 10);
        check("[]".equals(f.sets()), "a second event 10 while on scene 16 (pre mode 16): left alone as stock");
        menu(a, f, 1);
        check("[]".equals(f.sets()), "menu standard: nothing written");
        menu(a, f, 10);
        check("[16]".equals(f.sets()), "menu AI again from standard: scene 16");

        // the vendor's own reload from 27 (Filmmaker memory of another source) to AI
        f = new Fake(); f.pm = 27; a = film(f); a.start(f);
        menu(a, f, 10);
        check("[16]".equals(f.sets()), "AI from Filmmaker 27: scene 16");

        // X50 reads 10 for every scene: still one write per change, never a loop
        f = new Fake(); f.x50Read = true; f.pm = 29; a = film(f); a.start(f);
        menu(a, f, 10);
        check("[16]".equals(f.sets.toString()), "X50 read: AI from colour accuracy -> 16");
        echo(a, f); f.sets();
        a.reset(f);
        check("[16]".equals(f.sets()), "X50 read: screen on writes 16 again (getter reads 10, as stock)");

        // Dolby Vision and HDR Vivid: the event is ignored, the pre mode is not updated
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        f.hdr = AiPq.HDR_DOLBY;
        menu(a, f, 10);
        check("[]".equals(f.sets()), "Dolby Vision: event 10 changes nothing");
        f.hdr = 1;
        menu(a, f, 10);
        check("[16]".equals(f.sets()), "after Dolby (HDR10 now): event 10 brings 16 (pre mode was not updated)");
        f = new Fake(); f.pm = 1; a = film(f); a.start(f); f.vividAuto = true; f.vividPlaying = true;
        menu(a, f, 10);
        check("[]".equals(f.sets()), "HDR Vivid auto and playing: event 10 changes nothing");
        f.vividPlaying = false;
        menu(a, f, 1); menu(a, f, 10);
        check("[16]".equals(f.sets()), "HDR Vivid auto, not playing: scene 16");

        // events that are not picture modes
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        a.onTvEvent(f, 14, "picturemode:10");
        a.onTvEvent(f, 5, "aisr:10");
        a.onTvEvent(f, 5, "picturemode:x");
        a.onTvEvent(f, 5, "picturemode");
        a.onTvEvent(f, 5, null);
        check("[]".equals(f.sets()), "other events, other keys, bad values: nothing written, no throw");

        // needReset: leaving AI for a non-AI mode and back
        f = new Fake(); f.pm = 16; a = film(f); a.start(f); f.sets();
        menu(a, f, 9); menu(a, f, 10);
        check("[16]".equals(f.sets()), "sport then AI: scene 16 (reset flag)");

        // screen on
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        f.pm = 10; a.reset(f);
        check("[16]".equals(f.sets()), "screen on in AI 10: 16");
        f.pm = 13; a.reset(f);
        check("[16]".equals(f.sets()), "screen on in concert 13: back to 16 (stock reset)");
        f.pm = 9; a.reset(f);
        check("[]".equals(f.sets()), "screen on in sport: nothing");
        f.pm = 17; a.reset(f);
        check("[16]".equals(f.sets()), "screen on in football 17 (the last AI scene): back to 16");
        f.pm = 18; a.reset(f);
        check("[]".equals(f.sets()), "screen on in 18 (not an AI scene): nothing");

        // no model file (stock fallback scene tv 10): AI from another mode writes 10 again (needReset)
        f = new Fake(); f.pm = 1; a = new AiPq("", AiPq.PERSIST_DEFAULT, 2, null); a.start(f);
        menu(a, f, 9); menu(a, f, 10);
        check("[10]".equals(f.sets()), "default scene tv: AI after sport writes the tv scene 10 (stock needReset)");
        echo(a, f); f.sets();
        menu(a, f, 10);
        check("[]".equals(f.sets()), "default scene tv: a second event 10 writes nothing");

        // cropped PQ set (support_multipq_type 1): football on the event
        f = new Fake(); f.pm = 1; a = new AiPq("film", 10, AiPq.MULTIPQ_CROPPED, null); a.start(f);
        menu(a, f, 10);
        check("[17]".equals(f.sets()), "cropped PQ: event 10 brings football 17");

        // video types (com.xgimi.ai.NOTIFICATION)
        f = new Fake(); f.pm = 1; a = film(f); a.start(f); menu(a, f, 10); echo(a, f); f.sets();
        f.playing = true;
        a.onVideoType(f, "football", "com.app", true);
        check("[17]".equals(f.sets()), "AI on, an app plays football: 17");
        a.onVideoType(f, "football", "com.app", true);
        check("[]".equals(f.sets()), "the same type again: nothing");
        a.onVideoType(f, "anime", "com.app", true);
        check("[15]".equals(f.sets()), "anime: 15");
        a.onVideoType(f, "unknown_kind", "com.app", true);
        check("[16]".equals(f.sets()), "unknown type while playing: the default scene");
        a.onVideoType(f, "children", "com.app", true);
        f.fmt3d = 2;
        a.onVideoType(f, "unknown_kind", "com.app", true);
        check("[12]".equals(f.sets()), "unknown type in 3D: the scene stays");
        f.fmt3d = AiPq.NO_3D;
        a.onVideoType(f, "film", "com.app", false);
        check("[16]".equals(f.sets()), "a known type, not playing: the default scene");
        f.mw = true; a.onVideoType(f, "anime", "com.app", true);
        check("[]".equals(f.sets()), "multi window: nothing");
        f.mw = false; f.imax = true; a.onVideoType(f, "anime", "com.app", true);
        check("[]".equals(f.sets()), "IMAX playing: nothing");
        f.imax = false; a.onVideoType(f, "ignore", "com.app", true);
        check("[]".equals(f.sets()), "type ignore: nothing");
        a.onVideoType(f, "", "com.app", true);
        check("[]".equals(f.sets()), "empty type: nothing");

        // the video monitor: a stop forgets the app and brings the default scene back
        f = new Fake(); f.pm = 1; a = film(f); a.start(f); menu(a, f, 10); echo(a, f); f.sets();
        a.onVideoType(f, "football", "com.app", true); f.sets();
        a.monitorStep(f, true);
        check("[]".equals(f.sets()), "monitor: playing starts, nothing written");
        a.monitorStep(f, false);
        check("[16]".equals(f.sets()), "monitor: playing stops, the default scene 16");
        a.monitorStep(f, false);
        check("[]".equals(f.sets()), "monitor: still stopped, nothing");
        f.pm = 3; a.onVideoType(f, "football", "com.app", true); a.monitorStep(f, true); a.monitorStep(f, false);
        check("[]".equals(f.sets()), "monitor: the user mode is left alone");
        f = new Fake(); f.pm = 1; a = film(f); a.start(f); menu(a, f, 10); echo(a, f); f.sets();
        a.onVideoType(f, "football", "com.app", true); f.sets();
        f.lumens = AiPq.LUMENS_VIBRANT; a.monitorStep(f, true); a.monitorStep(f, false);
        check("[]".equals(f.sets()), "monitor: the vibrant light mode (20) is left alone");
        f.lumens = 2; f.hdr = AiPq.HDR_DOLBY; a.monitorStep(f, true); a.monitorStep(f, false);
        check("[]".equals(f.sets()), "monitor: Dolby Vision is left alone");
        // the user mode stops the monitor before its Youku stop (needWork), not only the AI scene test after it
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        a.onVideoType(f, AiPq.YOUKU_PLAY, "com.youku", true); f.sets();
        f.pm = AiPq.PIC_USER; a.monitorStep(f, true); a.monitorStep(f, false);
        check("[]".equals(f.sets()), "monitor in the user mode: the Youku stop mode 20 is not written");
        // Dolby Vision stops the monitor before it forgets the app that sent the type (needWork)
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        a.onVideoType(f, "football", "com.app", true); f.sets();
        f.hdr = AiPq.HDR_DOLBY; a.monitorStep(f, true); a.monitorStep(f, false);
        f.hdr = 0; f.top = "com.app"; menu(a, f, 10);
        check("[17]".equals(f.sets()), "monitor in Dolby Vision keeps the app's type: AI in that app brings 17");

        // the app that sent the type is not in front any more when AI comes back
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        a.onVideoType(f, "football", "com.app", true);
        check("[]".equals(f.sets()), "type while not in AI: remembered, nothing written");
        f.top = "com.app";
        menu(a, f, 10);
        check("[17]".equals(f.sets()), "AI picked in the app that sent football: 17");
        echo(a, f); menu(a, f, 1); f.top = "com.other"; menu(a, f, 10);
        check("[16]".equals(f.sets()), "AI picked in another app: the default scene");

        // Youku bluray outside AI: its own picture modes
        f = new Fake(); f.pm = 1; a = film(f); a.start(f);
        a.onVideoType(f, AiPq.YOUKU_PLAY, "com.youku", true);
        a.onVideoType(f, AiPq.YOUKU_STOP, "com.youku", false);
        check("[19, 20]".equals(f.sets()), "Youku bluray outside AI: 19 then 20");

        // the vendor HAL fails: the exception goes to the caller, nothing is written
        f = new Fake() { @Override public int hdrType() { throw new IllegalStateException("dead"); } };
        f.pm = 1; a = film(f); a.start(f);
        boolean threw = false;
        try { a.onTvEvent(f, 5, "picturemode:10"); } catch (IllegalStateException e) { threw = true; }
        check(threw && f.sets.isEmpty(), "a failed vendor read: no write");

        if (failed == 0) System.out.println("AiPqTest: all green");
        System.exit(failed == 0 ? 0 : 1);
    }
}
