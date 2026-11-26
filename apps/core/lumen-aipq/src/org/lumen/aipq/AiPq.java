// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.aipq;

import java.util.function.Consumer;

/**
 * The AI picture quality of stock XGIMI XRMService (utils/AIPQ.java, the same logic on Z9X G0082 6.15.62, Z9X HB
 * 6.16.24 / 6.17.12, X50 1.2.64, RS30 1.1.42, Titan 1.2.99 and Play 6): the menu only ever writes picture mode AI (10);
 * this class moves it to the AI scene. The vendor sends "picturemode:N" (GmTvManager event 5) after every picture mode
 * change, its own ones too (source, HDR, signal); on "picturemode:10" coming from a non-AI mode it writes the default
 * scene, ro.xgimi.default.aipq ("film" = 16 on every model file). It writes the default scene again at start and at every
 * screen on when an AI scene (10..17) is set (reset), and a scene by video type when an app sends one
 * (com.xgimi.ai.NOTIFICATION video_type). Scene 10 ("tv") is never the end state: measured on the X50 (vendor OP2
 * capture, SDR HDMI frame) its colour is about a fifth paler than scene 16.
 *
 * One instance per process, every method on the app's HAL thread with the vendor of the current HAL client. Pure
 * logic: the vendor calls go through {@link AiPqVendor}, the Android side (receivers, the video monitor thread) is {@link AiPqDriver}. Not ported, on
 * purpose: the AudioSystem parameter xgimi_pq_state (no vendor library of these models reads it), the scene toast
 * broadcast com.xgimi.aipq.set (no Lumen app shows it) and /data/local/pq/typeinfo (a stock-only file).
 */
public final class AiPq {
    /** EN_XGIMI_PICTURE_MODE: user, the AI scenes 10..17 and the default scenes by name. */
    public static final int PIC_USER = 3, PIC_TV = 10, PIC_FILM = 16, PIC_SPORTS = 17;
    static final int AI_FIRST = PIC_TV, AI_LAST = PIC_SPORTS;
    /** Stock persist.xgimi.default.aipq default when no model file sets a scene. */
    public static final int PERSIST_DEFAULT = PIC_TV;
    /** EN_LUMENS_VIBRANT, EN_XGIMI_HDR_TYPE_DOLBY, EN_XGIMI_3D_NONE (stock constants). */
    static final int LUMENS_VIBRANT = 20, HDR_DOLBY = 2, NO_3D = 1;
    /** support_multipq_type 1: the MT9669 cropped PQ set, whose default scene is football. */
    public static final int MULTIPQ_CROPPED = 1;
    public static final String EVENT_PREFIX = "picturemode";
    /** GmTvManager event of a picture mode change (Msrv_Tv_Control::sendPicChgTvEvent). */
    public static final int EVENT_PICTURE_MODE = 5;
    static final String TYPE_TV = "tv", TYPE_SPORTS = "football", IGNORE = "ignore";
    public static final String YOUKU_PLAY = "youku_bluray_play", YOUKU_STOP = "youku_bluray_stop";

    /** res/raw/defaultvideoinfo of XRMService (byte-identical on every model): video type, picture mode. */
    static final String[] TYPES = {"tv", "variety", "children", "concert", "documentary", "anime", "film", "football",
            YOUKU_PLAY, YOUKU_STOP};
    static final int[] TYPE_MODES = {10, 11, 12, 13, 14, 15, 16, 17, 19, 20};

    private final Consumer<String> log;
    private final int multiPqType;
    private final String typeDefault;
    private final int picDefault;

    private int curPic = PIC_FILM;
    private int prePic;
    private boolean started;
    private boolean allowPopUps = true;
    private boolean unknownType;
    private boolean needReset;
    private boolean youkuPlaying;
    private String aiPkg = "";
    private boolean lastPlaying;

    /**
     * @param roDefault   ro.xgimi.default.aipq (empty when unset): a scene name of the table, as initDefaultAipq
     * @param persistPic  persist.xgimi.default.aipq ({@link #PERSIST_DEFAULT} when unset)
     * @param multiPqType support_multipq_type of the model's feature.xml (0 when absent)
     */
    public AiPq(String roDefault, int persistPic, int multiPqType, Consumer<String> log) {
        this.log = log != null ? log : s -> { };
        this.multiPqType = multiPqType;
        Integer named = mode(roDefault);
        this.typeDefault = named != null ? roDefault : TYPE_TV;
        this.picDefault = named != null ? named : persistPic;
    }

    public int defaultScene() { return picDefault; }

    String defaultType() { return typeDefault; }

    /** The scene of a video type of the table, null for an unknown type. */
    static Integer mode(String type) {
        if (type == null) return null;
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return TYPE_MODES[i];
        return null;
    }

    static boolean aiScene(int pm) { return pm >= AI_FIRST && pm <= AI_LAST; }

    private int modelDefault() { return multiPqType == MULTIPQ_CROPPED ? PIC_SPORTS : picDefault; }

    private void set(AiPqVendor v, int mode) throws Exception {
        log.accept("setPictureMode(0, " + mode + ")");
        v.setPictureMode(mode);
    }

    /**
     * XRMService start: the class init reads the mode (m_prepicmode), startVideoPlayMonitor takes the model's default
     * scene and its thread first runs reset().
     */
    public void start(AiPqVendor v) throws Exception {
        if (started) return;
        started = true;
        prePic = v.pictureMode();
        curPic = modelDefault();
        reset(v);
    }

    /** XRMService.onScreenOn and the monitor start: an AI scene goes back to the default scene. */
    public void reset(AiPqVendor v) throws Exception {
        int pm = v.pictureMode();
        if (aiScene(pm)) {
            log.accept("reset: mode " + pm + " -> default " + picDefault);
            set(v, picDefault);
            curPic = picDefault;
        }
    }

    /** SignalChangeReceiver: GmTvManager event 5 with its message, AIPQ.pictureModeChangedProcess. */
    public void onTvEvent(AiPqVendor v, int type, String msg) throws Exception {
        if (type != EVENT_PICTURE_MODE || msg == null) return;
        String[] p = msg.split(":");
        if (p.length < 2 || !EVENT_PREFIX.equals(p[0])) return;
        int hdr = v.hdrType();
        boolean vividAuto = v.hdrVividAuto(v.pictureMode());
        boolean vividPlaying = v.hdrVividPlaying();
        if ((vividAuto && vividPlaying) || hdr == HDR_DOLBY) return;
        if (String.valueOf(PIC_TV).equals(p[1])) {
            if (aiScene(prePic)) {
                log.accept("picturemode:10 while already on AI scene " + prePic);
                return;
            }
            if (!empty(aiPkg) && !aiPkg.equals(v.topPackage())) {
                aiPkg = "";
                curPic = modelDefault();
                allowPopUps = false;
            } else if (empty(aiPkg) || unknownType) {
                curPic = modelDefault();
                allowPopUps = false;
            } else {
                allowPopUps = true;
            }
            if (PIC_TV != curPic || needReset) {
                log.accept("picturemode:10 -> scene " + curPic);
                set(v, curPic);
                prePic = PIC_TV;
                allowPopUps = false;
                needReset = false;
                return;
            }
            allowPopUps = false;
            return;
        }
        int n;
        try {
            n = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return;
        }
        prePic = n;
        needReset = !aiScene(n);
    }

    /** AIReceiver video_type / target_pkgName (com.xgimi.ai.NOTIFICATION, Youku bluray play state). */
    public void onVideoType(AiPqVendor v, String type, String targetPkg, boolean playing) throws Exception {
        aiPkg = targetPkg;
        updatePQ(v, type, playing);
    }

    /** AIPQ.updatePQ: the scene of a video type while AI is on, the user mode memory while it is not. */
    void updatePQ(AiPqVendor v, String type, boolean playing) throws Exception {
        if (empty(type)) return;
        int pm = v.pictureMode();
        int fmt3d = v.current3DFormat();
        int hdr = v.hdrType();
        boolean imax = v.imaxPlaying();
        boolean vividPlaying = v.hdrVividPlaying();
        boolean vividAuto = v.hdrVividAuto(pm);
        if (hdr == HDR_DOLBY || imax || IGNORE.equals(type) || v.multiWindow()) return;
        Integer scene = mode(type);
        if (!aiScene(pm)) {
            if (scene == null) {
                unknownType = true;
                return;
            }
            unknownType = false;
            if (YOUKU_PLAY.equals(type)) {
                youkuPlaying = true;
                set(v, scene);
            } else if (YOUKU_STOP.equals(type)) {
                youkuPlaying = false;
                set(v, scene);
            } else if (playing) {
                curPic = scene;
            }
            return;
        }
        if (vividAuto && vividPlaying) return;
        if (scene != null) {
            unknownType = false;
            boolean youku = YOUKU_PLAY.equals(type) || YOUKU_STOP.equals(type);
            if (youku) youkuPlaying = YOUKU_PLAY.equals(type);
            if (!playing && !youku) {
                if (curPic != picDefault) {
                    set(v, picDefault);
                    curPic = picDefault;
                }
                allowPopUps = true;
            } else if (curPic != scene) {
                curPic = scene;
                set(v, scene);
                if (!youku) allowPopUps = false;
            } else if (allowPopUps) {
                allowPopUps = false;
            }
            return;
        }
        if (playing) {
            unknownType = true;
            if (fmt3d != NO_3D) return;
            curPic = picDefault;
            set(v, picDefault);
        }
    }

    /** AIPQ.needAIPQWork: not in the user mode, the vibrant light mode or Dolby Vision. */
    boolean needWork(AiPqVendor v) throws Exception {
        int pm = v.pictureMode();
        int lumens = v.lumensMode();
        int hdr = v.hdrType();
        return !(pm == PIC_USER || lumens == LUMENS_VIBRANT || hdr == HDR_DOLBY);
    }

    /**
     * One pass of the startVideoPlayMonitor loop with the video state it read (the second read 1.5 s after a first
     * \"not playing\"): on a stop the app's video type is forgotten and the default scene comes back.
     */
    public void monitorStep(AiPqVendor v, boolean playing) throws Exception {
        if (!needWork(v) || (playing == lastPlaying && (playing || allowPopUps))) return;
        if (!playing) {
            aiPkg = "";
            if (youkuPlaying) updatePQ(v, YOUKU_STOP, false);
            if (v.pictureMode() >= PIC_TV) updatePQ(v, multiPqType != MULTIPQ_CROPPED ? typeDefault : TYPE_SPORTS, false);
        }
        lastPlaying = playing;
    }

    private static boolean empty(String s) { return s == null || s.isEmpty(); }
}
