package org.lumen.projector.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lumen game menu, design A (bottom dock), decision 2026-11-11: the menu logic as plain Java (no Android, no
 * HAL), host tested by sh test/gamemenu/run.sh. Spec: the approved prototype qp_design/game_menu.py (A) and the stock
 * Z9X game menu (vendor xhplayer, list Product_dapeng, gsi-stock analysis).
 *
 * Items and vendor calls (Z9X HIDL, values from the stock code):
 *   lat  response speed   IGmpf 647/648 (speed bit of the option: 0/1 standard, 3/2 turbo)       proven (Lumen 1.0.4)
 *   hfr  high frame rate  IGmpf 647/648 (HFR bit: 1/2 on)                                          proven (Lumen 1.0.4)
 *   sr   super resolution IGmpf2 321/322                                                          proven (Lumen 1.0.4)
 *   ar   aspect ratio     IGmpf 664/665, Auto (wire 2) and 16:9 (wire 1)                           proven (Lumen 1.0.4)
 *   vrr  VRR              IGmpf2 300/301 (state 0/1, source 1/2)                                   NEW, gated
 *   pic  game picture     IGmpf2 298/299 (mode 0..4, source 1/2)                                   NEW, gated
 *   dark dark scenes      IGmpf2 296/297 (on, picture mode 0..4, source 1/2)                       NEW, gated
 *   snd  game sound       IGmpf2 1/2 (190 off, 191 surround, 192 story; 0 reads as off)            NEW, gated
 *                         (X50 stock also 193 clarity, 194 balanced: profile game_menu.sound, setSounds)
 *   aim  crosshair        no vendor call, our own window
 *   fps  frame counter    no write, our own window, the frame rate of 662 (proven)
 * A gated item is shown only after its getter answered a value inside the stock range on this unit (capability
 * check), and every write is read back: a read-back that disagrees hides the item for the rest of this boot.
 * 21:9 and 32:9 (stock 665 wire values on the mt5877 unknown: Lumen's 665 table is 0 4:3, 1 16:9, 2 Auto, 4 Original)
 * and the frame shift of the wide formats (659 / IGmpf2 320 / 273) are not offered: WIDE_WIRE is empty until a live
 * check reads 664 while stock shows 21:9 and 32:9. Their stock rules stay here (VRR not with a wide format, wide only
 * with a 3840 x 2160 signal) so they hold the day the values are known.
 */
final class GmModel {
    private GmModel() {}

    // ================================================================== items
    static final String LAT = "lat", VRR = "vrr", HFR = "hfr", PIC = "pic", DARK = "dark", SR = "sr", AR = "ar",
            SND = "snd", AIM = "aim", FPS = "fps";
    /**
     * HB stock game bar items (v3): colour space (menu values 0 Native, 1 BT.2020, 2 Adobe RGB, 3 DCI-P3, 4 Rec.709; the
     * device Io maps its wire values) and HDR (0 off, 1 dynamic tone mapping, 2 HGIG; offered only while the device
     * answers, e.g. HDR10 content). Gated like VRR: shown only after the device's getter answered.
     */
    static final String CS = "cs", HDR = "hdr";
    /** Dock groups of the prototype: Speed, Picture, Sound, Over the game. */
    static final String[][] GROUPS = {{LAT, VRR, HFR}, {PIC, CS, DARK, SR, AR, HDR}, {SND}, {AIM, FPS}};
    static final String[] ORDER = {LAT, VRR, HFR, PIC, CS, DARK, SR, AR, HDR, SND, AIM, FPS};
    /** Items whose vendor calls Lumen already used and proved, or that need no vendor call. */
    static final String[] PROVEN = {LAT, HFR, SR, AR, AIM, FPS};
    /** Items with vendor calls new in Lumen (the stock menu sends them): behind the capability check. */
    static final String[] GATED = {VRR, PIC, DARK, SND, CS, HDR};

    static final int BAD = Integer.MIN_VALUE;

    /** Snap.hdr codes (stock getHdrType, Z9X 620); every device's GameMenu.Io fills Snap.hdr in these. */
    static final int HDR_SDR = 0, HDR_HDR10 = 1, HDR_DOLBY_VISION = 2, HDR_HLG = 4, HDR_HDR10_PLUS = 5, HDR_VIVID = 6;
    /** Snap.ar raw aspect codes (stock 664: 0 4:3, 1 16:9, 2 Auto, 4 Original); the Io fills Snap.ar in these. */
    static final int AR_4_3 = 0, AR_16_9 = 1, AR_AUTO = 2, AR_ORIGINAL = 4;

    /** Values of an item in the menu (index into its option list); the aim tile has its own popup. */
    static int count(String k) {
        switch (k) {
            case PIC: return 5;
            case CS: return 5;
            case HDR: return 3;
            case SND: return sounds;
            case FPS: return 4;
            case AR: return 4;          // Auto, 16:9, 21:9, 32:9 (the wide two only with WIDE_WIRE)
            case AIM: return 0;
            default: return 2;
        }
    }

    /** Value indexes drawn in the performance yellow (stock Boost look): turbo, VRR on, HFR on. */
    static boolean isPerf(String k, int v) {
        return (LAT.equals(k) || VRR.equals(k) || HFR.equals(k)) && v == 1;
    }

    static boolean isGated(String k) {
        for (String g : GATED) if (g.equals(k)) return true;
        return false;
    }

    // ================================================================== capabilities
    static final int CAP_UNKNOWN = 0, CAP_OK = 1, CAP_FAILED = 2;

    /** Capability of every gated item on this unit, this boot. */
    static final class Caps {
        final Map<String, Integer> cap = new LinkedHashMap<>();

        Caps() { for (String g : GATED) cap.put(g, CAP_UNKNOWN); }

        int get(String k) { Integer v = cap.get(k); return v == null ? CAP_OK : v; }

        /** A getter answered: OK when the raw value is inside the stock range; a FAILED item stays failed. */
        void onRead(String k, int raw) {
            if (!cap.containsKey(k) || get(k) == CAP_FAILED) return;
            cap.put(k, plain(k, raw) == BAD ? CAP_UNKNOWN : CAP_OK);
        }

        /** The getter threw or the HAL did not answer: not proven, hidden (a FAILED item stays failed). */
        void onNoAnswer(String k) {
            if (cap.containsKey(k) && get(k) != CAP_FAILED) cap.put(k, CAP_UNKNOWN);
        }

        /** A write was read back. Returns true when it took; a disagreeing read-back hides the item. */
        boolean onReadBack(String k, int want, int rawBack) {
            boolean ok = plain(k, rawBack) == want;
            if (!ok && cap.containsKey(k)) cap.put(k, CAP_FAILED);
            return ok;
        }

        /** The device profile's game_menu.items (null: every item); an item the profile lacks is never shown. */
        private java.util.Set<String> allowed;

        void allow(java.util.Collection<String> items) {
            allowed = items == null ? null : new java.util.HashSet<>(items);
        }

        /** Items the device profile gates (game_menu.gated): hidden until their getter answered in range. */
        void gate(java.util.Collection<String> items) {
            if (items != null) for (String k : items) if (!cap.containsKey(k)) cap.put(k, CAP_UNKNOWN);
        }

        boolean visible(String k) {
            return (allowed == null || allowed.contains(k)) && (!cap.containsKey(k) || get(k) == CAP_OK);
        }
    }

    /** The visible tiles in dock order. */
    static List<String> visibleOrder(Caps c) {
        List<String> out = new ArrayList<>();
        for (String k : ORDER) if (c.visible(k)) out.add(k);
        return out;
    }

    /** Visible groups (index into GROUPS) with their visible tiles; an empty group is left out. */
    static List<String[]> visibleGroups(Caps c) {
        List<String[]> out = new ArrayList<>();
        for (int g = 0; g < GROUPS.length; g++) {
            List<String> ks = new ArrayList<>();
            for (String k : GROUPS[g]) if (c.visible(k)) ks.add(k);
            if (ks.isEmpty()) continue;
            String[] row = new String[ks.size() + 1];
            row[0] = Integer.toString(g);
            for (int i = 0; i < ks.size(); i++) row[i + 1] = ks.get(i);
            out.add(row);
        }
        return out;
    }

    // ================================================================== raw vendor values <-> menu values
    /**
     * Menu value of a raw getter reply, BAD when outside the stock range. Stock maps 300 / 298 -1 to 0 and the sound
     * getter's 0 to off (GameModeApi getVrrState / getPictureMode / getGameSoundEffect).
     */
    static int plain(String k, int raw) {
        switch (k) {
            case VRR: return raw == -1 ? 0 : (raw == 0 || raw == 1) ? raw : BAD;
            case PIC: return raw == -1 ? 0 : (raw >= 0 && raw <= 4) ? raw : BAD;
            case DARK: return (raw == 0 || raw == 1) ? raw : BAD;
            case SND: return (raw == 0 || raw == SND_OFF) ? 0 : (raw > SND_OFF && raw < SND_OFF + sounds) ? raw - SND_OFF : BAD;
            case CS: return raw >= 0 && raw <= 4 ? raw : BAD;
            case HDR: return raw >= 0 && raw <= 2 ? raw : BAD;
            default: return raw;
        }
    }

    /** The stock game sound values in menu order: wire SND_OFF + index (190 off, 191 surround, 192 story, 193, 194). */
    static final String[] SOUNDS = {"off", "surround", "story", "clarity", "balanced"};
    static final int SND_OFF = 190;
    /** How many of SOUNDS this device offers (profile game_menu.sound; the Z9X and the HB: the first 3). */
    private static int sounds = 3;

    /** GameMenu.install: the device profile's game sound list (a prefix of SOUNDS, at least off, surround, story). */
    static void setSounds(int n) {
        if (n < 3 || n > SOUNDS.length) throw new IllegalArgumentException("game sounds " + n);
        sounds = n;
    }

    /**
     * Wire value of setGamingSoundEffectMode for a menu value: 190 off, 191 surround, 192 story, and on a device whose
     * stock has them (X50) 193 clarity, 194 balanced.
     */
    static int sndWire(int v) {
        if (v < 0 || v >= sounds) throw new IllegalArgumentException("sound " + v);
        return SND_OFF + v;
    }

    /** Response speed of a 647 option: 1 turbo (2, 3), 0 standard (0, 1), BAD otherwise. */
    static int latOf(int opt) { return (opt == 2 || opt == 3) ? 1 : (opt == 0 || opt == 1) ? 0 : BAD; }

    /** HFR of a 647 option: 1 on (1, 2), 0 off (0, 3), BAD otherwise. */
    static int hfrOf(int opt) { return (opt == 1 || opt == 2) ? 1 : (opt == 0 || opt == 3) ? 0 : BAD; }

    /** 648 option for a speed and an HFR choice (stock setHighFrameRate: 0<->1, 3<->2). */
    static int option(int lat, int hfr) {
        if (lat == 1) return hfr == 1 ? 2 : 3;
        return hfr == 1 ? 1 : 0;
    }

    /** 665 wire of the menu aspect values; -1 = not offered (the wide two until the live check). */
    static final int[] AR_WIRE = {2, 1};
    /** Wire values of 21:9 and 32:9 on the Z9X: unknown, so empty (the two options stay hidden). */
    static final int[] WIDE_WIRE = {};

    static int arWire(int v) {
        if (v >= 0 && v < AR_WIRE.length) return AR_WIRE[v];
        if (v >= 2 && v - 2 < WIDE_WIRE.length) return WIDE_WIRE[v - 2];
        return -1;
    }

    /** Menu aspect of a 664 reply: 0 Auto, 1 16:9, 2 / 3 wide; -1 another value (4:3, Original: the HDMI page). */
    static int arOf(int raw) {
        for (int i = 0; i < AR_WIRE.length; i++) if (AR_WIRE[i] == raw) return i;
        for (int i = 0; i < WIDE_WIRE.length; i++) if (WIDE_WIRE[i] == raw) return 2 + i;
        return -1;
    }

    /** Whether option {@code i} of item {@code k} is offered on the Z9X. */
    static boolean optionOffered(String k, int i) {
        if (AR.equals(k)) return arWire(i) >= 0;
        return i >= 0 && i < count(k);
    }

    /** The offered options of an item, in order. */
    static List<Integer> options(String k) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count(k); i++) if (optionOffered(k, i)) out.add(i);
        return out;
    }

    // ================================================================== state and stock rules
    /** What the menu knows: value per item (BAD = unknown) and the HDMI signal. */
    static final class State {
        final Map<String, Integer> v = new LinkedHashMap<>();
        int width, height;

        State() { for (String k : ORDER) v.put(k, BAD); }

        int get(String k) { Integer x = v.get(k); return x == null ? BAD : x; }

        void set(String k, int x) { v.put(k, x); }

        boolean signal4k() { return width == 3840 && height == 2160; }
    }

    static final int OK = 0, R_VRR_WIDE = 1, R_AR_VRR = 2, R_AR_4K = 3, R_HFR_VRR = 4, R_NOT_OFFERED = 5;

    /**
     * Stock refusal (xhplayer MutexManager / OtherViewModel, prototype setItem): OK or the reason a change is refused.
     *  VRR on while a wide format is shown; any aspect change while VRR is on; a wide format without a 3840 x 2160
     *  signal; HFR off while VRR is on; a value the Z9X does not offer.
     */
    static int refuse(String k, int v, State s) {
        if (!optionOffered(k, v) && !AIM.equals(k)) return R_NOT_OFFERED;
        int vrr = s.get(VRR), ar = s.get(AR);
        if (VRR.equals(k) && v == 1 && ar >= 2) return R_VRR_WIDE;
        if (AR.equals(k) && vrr == 1 && v != ar) return R_AR_VRR;
        if (AR.equals(k) && v >= 2 && !s.signal4k()) return R_AR_4K;
        if (HFR.equals(k) && v == 0 && vrr == 1) return R_HFR_VRR;
        return OK;
    }

    /**
     * Stock asks before Turbo, which resets the keystone to the full frame (QuickPanelController.onGameSpeed, parity
     * game item 2.6): only when the keystone is not known to be the full frame already.
     */
    static boolean askTurbo(int curLat, int newLat, Boolean fullFrame) {
        return newLat == 1 && curLat != 1 && !Boolean.TRUE.equals(fullFrame);
    }

    // ================================================================== presets
    static final int P_COMPETE = 0, P_BALANCE = 1, P_STORY = 2, PRESETS = 3;

    /** Values of a preset (prototype PRESETS); items not listed keep their value. */
    static Map<String, Integer> preset(int p) {
        Map<String, Integer> m = new LinkedHashMap<>();
        switch (p) {
            case P_COMPETE:
                m.put(LAT, 1); m.put(VRR, 1); m.put(HFR, 1); m.put(PIC, 1); m.put(DARK, 1); m.put(SND, 1);
                break;
            case P_BALANCE:
                m.put(LAT, 0); m.put(VRR, 0); m.put(HFR, 1); m.put(PIC, 0); m.put(DARK, 0); m.put(SR, 0); m.put(SND, 0);
                break;
            case P_STORY:
                m.put(LAT, 0); m.put(VRR, 0); m.put(HFR, 0); m.put(PIC, 2); m.put(DARK, 0); m.put(SR, 1); m.put(SND, 2);
                break;
            default:
                throw new IllegalArgumentException("preset " + p);
        }
        return m;
    }

    /** Pseudo item of a plan step that writes the speed and HFR together (one 648 option). */
    static final String SPEED = "speed";

    /** One write of a preset: item (or SPEED) and its menu value (for SPEED: the 648 option). */
    static final class Step {
        final String key;
        final int value;

        Step(String key, int value) { this.key = key; this.value = value; }

        @Override public String toString() { return key + "=" + value; }
    }

    /**
     * The writes of preset {@code p} from state {@code s}, in a safe order, only for visible items whose value
     * differs: a wide format goes to Auto before VRR is turned on (prototype applyPreset), VRR goes off before HFR may
     * go off, speed and HFR are one 648 option, VRR on after it (the vendor raises HFR itself), the game picture before
     * dark scenes (dark scenes are stored per game picture), then super resolution and sound.
     */
    static List<Step> plan(int p, State s, Caps c) {
        Map<String, Integer> want = preset(p);
        List<Step> out = new ArrayList<>();
        int vrrNow = s.get(VRR);
        boolean vrrVisible = c.visible(VRR);
        Integer vrrWant = vrrVisible ? want.get(VRR) : null;
        if (vrrWant != null && vrrWant == 1 && s.get(AR) >= 2) out.add(new Step(AR, 0));
        if (vrrWant != null && vrrWant == 0 && vrrNow == 1) out.add(new Step(VRR, 0));
        int lat = s.get(LAT), hfr = s.get(HFR);
        int latW = want.containsKey(LAT) ? want.get(LAT) : lat;
        int hfrW = want.containsKey(HFR) ? want.get(HFR) : hfr;
        boolean vrrStaysOn = vrrNow == 1 && (vrrWant == null || vrrWant == 1);
        if (hfrW == 0 && vrrStaysOn) hfrW = 1;                   // stock: HFR cannot go off while VRR is on
        if (vrrWant != null && vrrWant == 1) hfrW = 1;            // VRR needs HFR
        if (latW != BAD && hfrW != BAD && (latW != lat || hfrW != hfr)) out.add(new Step(SPEED, option(latW, hfrW)));
        if (vrrWant != null && vrrWant == 1 && vrrNow != 1) out.add(new Step(VRR, 1));
        for (String k : new String[] {PIC, DARK, SR, SND}) {
            Integer w = want.get(k);
            if (w == null || !c.visible(k)) continue;
            if (w != s.get(k) || (DARK.equals(k) && hasStep(out, PIC))) out.add(new Step(k, w));
        }
        return out;
    }

    private static boolean hasStep(List<Step> l, String k) {
        for (Step st : l) if (st.key.equals(k)) return true;
        return false;
    }

    /** The preset whose every visible item equals the state, or -1. */
    static int presetMatch(State s, Caps c) {
        for (int p = 0; p < PRESETS; p++) {
            boolean all = true;
            for (Map.Entry<String, Integer> e : preset(p).entrySet()) {
                if (!c.visible(e.getKey())) continue;
                if (s.get(e.getKey()) != e.getValue()) { all = false; break; }
            }
            if (all) return p;
        }
        return -1;
    }

    // ================================================================== focus map (D-pad)
    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    static final int Z_DOCK = 0, Z_PRESETS = 1;

    /**
     * Next focus {zone, index, rememberedDockIndex} for a D-pad key. Dock: LEFT / RIGHT along the tiles (no wrap),
     * UP to the preset below the tile's third of the dock. Presets: LEFT / RIGHT, DOWN back to the remembered tile.
     */
    static int[] move(int zone, int idx, int lastDock, int key, int tiles, int presets) {
        if (tiles <= 0) return new int[] {Z_PRESETS, Math.max(0, Math.min(idx, presets - 1)), 0};
        if (zone == Z_DOCK) {
            switch (key) {
                case LEFT: return new int[] {Z_DOCK, Math.max(0, idx - 1), Math.max(0, idx - 1)};
                case RIGHT: return new int[] {Z_DOCK, Math.min(tiles - 1, idx + 1), Math.min(tiles - 1, idx + 1)};
                case UP: return new int[] {Z_PRESETS, presetBelow(idx, tiles, presets), idx};
                default: return new int[] {Z_DOCK, idx, idx};
            }
        }
        switch (key) {
            case LEFT: return new int[] {Z_PRESETS, Math.max(0, idx - 1), lastDock};
            case RIGHT: return new int[] {Z_PRESETS, Math.min(presets - 1, idx + 1), lastDock};
            case DOWN: return new int[] {Z_DOCK, Math.max(0, Math.min(tiles - 1, lastDock)), lastDock};
            default: return new int[] {Z_PRESETS, idx, lastDock};
        }
    }

    static int presetBelow(int idx, int tiles, int presets) {
        if (presets <= 0) return 0;
        return Math.max(0, Math.min(presets - 1, idx * presets / Math.max(1, tiles)));
    }

    /** Popup list: UP / DOWN clamp; returns the new row. */
    static int popupMove(int row, int key, int rows) {
        if (key == UP) return Math.max(0, row - 1);
        if (key == DOWN) return Math.min(rows - 1, row + 1);
        return row;
    }

    // ---- crosshair popup: rows toggle, styles (5 x 2), colours, move
    static final int AIM_ROW_TOGGLE = 0, AIM_ROW_STYLE = 1, AIM_ROW_COLOUR = 2, AIM_ROW_MOVE = 3, AIM_ROWS = 4;
    static final int AIM_STYLES = 10, AIM_COLS = 5;
    /** Colours (prototype COLORS; stock default #51E915 first). */
    static final int[] AIM_COLOURS = {0xFF51E915, 0xFFFFE600, 0xFF1A8DF2, 0xFFF62522, 0xFFFFFFFF};

    /**
     * Crosshair popup key {row, style, colour}: in the style grid LEFT / RIGHT walk all styles (wrapping), DOWN on the
     * first line goes to the second line, UP on the second line to the first; in the colours LEFT / RIGHT wrap; other
     * UP / DOWN move between rows.
     */
    static int[] aimMove(int row, int style, int colour, int key) {
        if (row == AIM_ROW_STYLE) {
            if (key == LEFT) return new int[] {row, (style + AIM_STYLES - 1) % AIM_STYLES, colour};
            if (key == RIGHT) return new int[] {row, (style + 1) % AIM_STYLES, colour};
            if (key == DOWN && style < AIM_COLS) return new int[] {row, style + AIM_COLS, colour};
            if (key == UP && style >= AIM_COLS) return new int[] {row, style - AIM_COLS, colour};
        }
        if (row == AIM_ROW_COLOUR) {
            int n = AIM_COLOURS.length;
            if (key == LEFT) return new int[] {row, style, (colour + n - 1) % n};
            if (key == RIGHT) return new int[] {row, style, (colour + 1) % n};
        }
        return new int[] {popupMove(row, key, AIM_ROWS), style, colour};
    }

    /** Crosshair position in design px (1920 x 1080), 6 px per key, kept 80 px off the edges. */
    static final int AIM_STEP = 6, AIM_MIN_X = 80, AIM_MAX_X = 1840, AIM_MIN_Y = 80, AIM_MAX_Y = 1000;

    static int[] aimNudge(int x, int y, int key) {
        int dx = key == LEFT ? -AIM_STEP : key == RIGHT ? AIM_STEP : 0;
        int dy = key == UP ? -AIM_STEP : key == DOWN ? AIM_STEP : 0;
        return new int[] {Math.max(AIM_MIN_X, Math.min(AIM_MAX_X, x + dx)), Math.max(AIM_MIN_Y, Math.min(AIM_MAX_Y, y + dy))};
    }

    // ================================================================== console theme and controller frames
    static final String TH_PS = "ps", TH_XBOX = "xbox", TH_SWITCH = "switch";
    /**
     * A console that does not say which one (no CEC name, or only the framework's placeholder, and no known vendor)
     * while it asks for game mode itself (ALLM) or the user marked the input as a console: the generic gamepad (the
     * same licensed paxillop pad as Xbox, graphite, no letters).
     */
    static final String TH_PAD = "pad";
    /** Computers (2026-11-11): no gamepad, a flat monochrome icon with a soft glow and no turntable. */
    static final String TH_APPLE = "apple", TH_WINDOWS = "windows", TH_COMPUTER = "computer";
    /** Every theme with controller frames under assets/gamemenu/. */
    static final String[] FRAME_THEMES = {TH_PS, TH_XBOX, TH_SWITCH, TH_PAD};
    /** CEC vendor id of Sony (ConsoleMatcher.VENDOR_SONY): a PS5 that sends no name. */
    static final int VENDOR_SONY = 0x080046;
    /** IEEE blocks of Microsoft and Nintendo (Z9xTvInput Brands): only for a device without a real name. */
    static final int[] VENDORS_MICROSOFT = {0x0050F2, 0x000D3A, 0x002248, 0x7C1E52, 0x985FD3};
    static final int[] VENDORS_NINTENDO = {0x0009BF, 0x0017AB, 0x001F32};

    /**
     * What the HDMI-CEC device list says about the device behind the port (HdmiDeviceInfo of the first source device
     * on that port). The PS5 first shows up as "Playback_1" with an unknown vendor, then about a second later as
     * Sony 0x080046 "PlayStation 5" (Play 6 log): read it again while the menu is
     * open instead of keeping the first answer.
     */
    static final class Source {
        static final Source NONE = new Source(null, 0, false);
        final String name;
        final int vendor;
        final boolean playback;

        Source(String name, int vendor, boolean playback) {
            this.name = name == null ? null : name.trim();
            this.vendor = vendor;
            this.playback = playback;
        }

        boolean same(Source o) {
            return o != null && vendor == o.vendor && playback == o.playback
                    && (name == null ? o.name == null : name.equals(o.name));
        }

        @Override public String toString() {
            return "'" + name + "' vendor=0x" + Integer.toHexString(vendor) + (playback ? " playback" : "");
        }
    }

    /** HdmiUtils default names of the framework's CEC list (Z9xTvInput Brands.PLACEHOLDER): not a device name. */
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile(
            "(?i)(tv|audio|audiosystem|secondary_tv|(playback|recorder|tuner|audio|backup|reserved)[_ ]\\d+)");

    /** The device's own name: null when empty or still the framework's placeholder ("Playback_1"). */
    static String realName(String cecName) {
        if (cecName == null) return null;
        String n = cecName.trim();
        return n.isEmpty() || PLACEHOLDER.matcher(n).matches() ? null : n;
    }

    private static boolean in(int v, int[] list) {
        for (int x : list) if (x == v) return true;
        return false;
    }

    /** {@link #theme(String, int, boolean, boolean)} without a console hint. */
    static String theme(String cecName, int vendorId, boolean playback) {
        return theme(cecName, vendorId, playback, false);
    }

    static String theme(Source s, boolean consoleHint) {
        Source x = s == null ? Source.NONE : s;
        return theme(x.name, x.vendor, x.playback, consoleHint);
    }

    /**
     * Picture for the device behind the HDMI port. Consoles (stock picks the theme from the device name: contains
     * playstation / switch / xbox; like ConsoleMatcher we also take PS4 / PS5 / Nintendo, and for a device without a
     * real name the vendor: a Sony playback device, a Microsoft or Nintendo block) get the licensed controller frames.
     * 2026-11-11: a computer never gets a gamepad: a Mac (name says Mac, MacBook, iMac or Apple) the Apple icon,
     * a Windows PC (Windows, Microsoft, PC as a word) the Windows icon. A device that names nothing known gets the
     * generic gamepad when {@code consoleHint} (it asks for game mode itself with ALLM, or the user marked the input
     * as a console: computers do neither), else the plain laptop icon.
     */
    static String theme(String cecName, int vendorId, boolean playback, boolean consoleHint) {
        String real = realName(cecName);
        String n = real == null ? "" : real.toLowerCase(Locale.ROOT);
        if (n.contains("playstation") || n.matches(".*\\bps[2-9]\\b.*")) return TH_PS;
        if (n.contains("xbox")) return TH_XBOX;
        if (n.contains("nintendo") || n.matches(".*\\bswitch\\b.*")) return TH_SWITCH;
        if (n.isEmpty()) {
            if (vendorId == VENDOR_SONY && playback) return TH_PS;
            if (in(vendorId, VENDORS_MICROSOFT)) return TH_XBOX;
            if (in(vendorId, VENDORS_NINTENDO)) return TH_SWITCH;
        }
        if (n.contains("apple") || n.contains("macbook") || n.contains("imac") || n.matches(".*\\bmac\\b.*")
                || n.contains("mac mini") || n.contains("mac studio") || n.contains("mac pro")) return TH_APPLE;
        if (n.contains("windows") || n.contains("microsoft") || n.matches(".*\\bpc\\b.*")) return TH_WINDOWS;
        return consoleHint ? TH_PAD : TH_COMPUTER;
    }

    /**
     * Name on the status card: the device's own name; a console found by its vendor alone gets the plain brand word;
     * null otherwise (the card shows the port). Never the framework's placeholder.
     */
    static String shownName(String cecName, String theme) {
        String real = realName(cecName);
        if (real != null) return real;
        switch (theme) {
            case TH_PS: return "PlayStation";
            case TH_XBOX: return "Xbox";
            case TH_SWITCH: return "Nintendo Switch";
            default: return null;
        }
    }

    /** Glow behind the picture (prototype DEVS[].glow; computers: a soft neutral glow). */
    static int glow(String theme) {
        switch (theme) {
            case TH_PS: return 0xFF4C7DFF;
            case TH_XBOX: return 0xFF3FBF5B;
            case TH_SWITCH: return 0xFFFF5A51;
            case TH_APPLE: return 0xFFB9C3D6;
            case TH_WINDOWS: return 0xFF2D8CFF;
            default: return 0xFF3B78E7;
        }
    }

    /** A console: the controller frames play (turn, then sway). Computers show a still icon. */
    static boolean isConsole(String theme) {
        return TH_PS.equals(theme) || TH_XBOX.equals(theme) || TH_SWITCH.equals(theme) || TH_PAD.equals(theme);
    }

    /** A known computer (Mac or Windows): the status line says "computer". */
    static boolean isKnownComputer(String theme) { return TH_APPLE.equals(theme) || TH_WINDOWS.equals(theme); }

    static final int TURN_FRAMES = 30, IDLE_FRAMES = 24;
    static final long TURN_MS = 1300, IDLE_STEP_MS = 140;
    /** frameAt result base of the idle frames: 100 + i = idle_i. */
    static final int IDLE_BASE = 100;

    /**
     * Frame of the controller {@code t} ms after the menu opened or the console changed (prototype FRAMES_JS): the
     * 30-frame turn plays once with an ease-out over 1.3 s and ends on frame 0 (the hero pose), then the 24-frame sway
     * loops at 140 ms per frame. Returns 0..29 (turn_NN) or IDLE_BASE + 0..23 (idle_NN).
     */
    static int frameAt(long t) {
        if (t < 0) t = 0;
        if (t < TURN_MS) {
            double k = t / (double) TURN_MS;
            double e = 1 - Math.pow(1 - k, 2.2);
            return (Math.min(TURN_FRAMES - 1, (int) Math.floor(e * TURN_FRAMES)) + 1) % TURN_FRAMES;
        }
        return IDLE_BASE + (int) (((t - TURN_MS) / IDLE_STEP_MS) % IDLE_FRAMES);
    }

    /** Asset path of a frameAt value under assets/gamemenu/. */
    static String framePath(String theme, int frame) {
        if (frame >= IDLE_BASE) return String.format(Locale.ROOT, "gamemenu/%s/idle_%02d.webp", theme, frame - IDLE_BASE);
        return String.format(Locale.ROOT, "gamemenu/%s/turn_%02d.webp", theme, frame);
    }

    // ================================================================== timers, frame counter
    /** Stock: the menu hides 10 s after the last key, 30 s the first time it opened by itself. */
    static long autoHideMs(boolean first) { return first ? 30_000 : 10_000; }

    static final int FPS_OFF = 0, FPS_LEFT = 1, FPS_CENTRE = 2, FPS_RIGHT = 3;

    /** Frames per second shown by the counter from 662's frame rate (1/100 Hz), -1 when there is no signal. */
    static int fpsShown(int frameRateCenti) {
        if (frameRateCenti <= 0 || frameRateCenti > 50000) return -1;
        return Math.round(frameRateCenti / 100f);
    }
}
