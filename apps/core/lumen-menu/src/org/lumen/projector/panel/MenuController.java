// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.menu.Feature;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.CheckRow;
import org.lumen.projector.ui.DialogPanel;
import org.lumen.projector.ui.EqBandsRow;
import org.lumen.projector.ui.HeaderRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.OverlayPanelHost;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.PageAccess;
import org.lumen.projector.ui.PanelHost;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.SliderRow;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.Theme;
import org.lumen.projector.ui.ToggleRow;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shared quick panel D of every projector: the card, the popups, the All
 * settings pages and the light slider, built ONLY from the device profile (MenuSpec) and driven ONLY through the
 * device adapter (ProjectorFeatures). It never calls a HAL and never asks which projector it runs on. A feature the
 * profile does not name gets nothing; a row whose value the device did not read shows no value (never invented).
 *
 * <p>v3 carries every behaviour of the Z9X 1.0.10 quick panel, now for every device: the
 * stock questions ({@link ProjectorFeatures#confirm}) and refusals ({@link ProjectorFeatures#blocked}, one note per
 * press, the slider does not move), the runtime rows (hidden, option subsets, runtime labels, placeholders, tile
 * values, notes: {@link MenuState}), Boost with two presses beyond the top level and two to leave it, the slim
 * brightness bar, the game popup (mode radios and the rest of the game page), the remembered tile across opens,
 * BACK from a screen to the row that opened it, the restore point after the autofocus card, the rows other modules
 * add ({@link Extension}) and the pages API for device pages ({@link #push}, {@link #confirm}, {@link #closeThen}).
 *
 * <p>Windows: through a {@link PanelHost}: the overlay host by default (Z9X, HB), or the
 * activity host of a device app that adds no overlay windows (X50). The views and every rule are the same in both.
 * An adapter that also implements {@link ProjectorFeatures.Live} is told the section on screen and may push live
 * values for it (the X50 level angles).
 */
public final class MenuController {
    static final String TAG = "LumenMenu";
    /** Where the recipe puts the device profile: file devices/&lt;c&gt;/menu.json assets/lumen_menu/menu.json. */
    public static final String PROFILE_ASSET = "lumen_menu/menu.json";
    /** Preferences of the menu (the Z9X panel's file, so the last tile survives the switch to the shared menu). */
    public static final String PREFS = "z9x_panel";
    static final String KEY_LAST_TILE = "last_tile";

    static final long CHOICE_COMMIT_MS = 500, PICTURE_MODE_COMMIT_MS = 700, SLIDER_GUARD_MS = 800;
    static final long BOOST_PRESS_WINDOW_MS = 3_000, CONFIRM_RECHECK_MS = 300;

    /** Choices shown as radio rows on their page (stock projection page), not as a stepping row. */
    static final Set<String> INLINE_RADIOS = set(Feature.PROJECTION_MODE);
    /** Choosing the value shown already does nothing (stock: picture mode, home screen, projection). */
    static final Set<String> SKIP_SAME = set(Feature.PICTURE_MODE, Feature.SETTINGS_LAUNCHER, Feature.PROJECTION_MODE);
    /** These close the menu first and act when it is gone (they show their own overlay or leave the menu). */
    static final Set<String> CLOSE_FIRST = set(Feature.KEYSTONE_AK_NOW, Feature.KEYSTONE_FIT_NOW, Feature.FOCUS_AF_NOW,
            Feature.FOCUS_MANUAL, Feature.SETTINGS_SYSTEM, Feature.SETTINGS_PROJECTOR, Feature.SETTINGS_RECENTS,
            Feature.SETTINGS_LAUNCHER, Feature.SETTINGS_SLEEP_TIMER, Feature.SETTINGS_REMOTE);
    /** These leave the menu for another app or screen: refused while the first-run setup runs. */
    static final Set<String> SETUP_GATED = set(Feature.INPUT, Feature.BLUETOOTH, Feature.NETWORK, Feature.USB_FILES,
            Feature.SETTINGS_SYSTEM, Feature.SETTINGS_PROJECTOR, Feature.SETTINGS_CEC, Feature.SETTINGS_RECENTS,
            Feature.SETTINGS_LAUNCHER);

    /** Device pages shown inside the panel (null: none, the menu then asks ProjectorFeatures.open). */
    public interface Pages {
        Page page(String id, MenuController menu);
    }

    /** Rows another module adds to a section page (Z9X / HB QuickPanel.addExtension): UI resolution, screensaver. */
    public interface Extension {
        /** Once per built menu (again after a locale change): add rows with {@code page.add(...)}. */
        void addRows(Context app, Page page);

        /** Each time the page shows: load the values of your rows. */
        default void onPageShown() {}
    }

    private static final Map<String, List<Extension>> EXTENSIONS = new LinkedHashMap<>();

    /** Registers rows for {@code section} (a MenuSpec section id) in every menu built from now on and in the shown one. */
    public static void addExtension(String section, Extension x, MenuController built) {
        if (section == null || x == null) return;
        EXTENSIONS.computeIfAbsent(section, k -> new ArrayList<>()).add(x);
        if (built != null) built.extensionAdded(section, x);
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final MenuProfile profile;
    private final MenuSpec spec;
    private final ProjectorFeatures dev;
    private final Pages devicePages;
    private final PanelHost host;
    private final Map<String, Page> pages = new LinkedHashMap<>();       // section -> full page
    private final Map<String, Page> popups = new HashMap<>();            // wide tile -> radio popup
    private final Map<String, View> rows = new LinkedHashMap<>();        // feature (picture.pq.<item>) -> row
    private final Map<View, String> featureOf = new HashMap<>();
    private final Map<String, List<CheckRow>> radios = new HashMap<>();  // choice feature -> its popup radios
    private final Map<String, List<CheckRow>> inline = new HashMap<>();  // inline radio choice -> its rows
    private final Map<String, TextRow> notes = new HashMap<>();          // feature or section -> its note line
    private final Map<HeaderRow, List<String>> headers = new LinkedHashMap<>();
    private final Map<View, View> tips = new HashMap<>();                // row -> hint picture
    private final Map<Page, View> pageTips = new HashMap<>();
    private final Map<String, List<String>> shownOptions = new HashMap<>();   // choice -> the tokens its row shows
    private final Map<String, Long> steppedAt = new HashMap<>();
    private List<CheckRow> gameRadios = new ArrayList<>();
    private Page root;
    private QpdCard card;
    private QpdLampRow lamp;
    private QpdMirror mirror;
    private QpdPanel panel;
    private MenuState state;
    private String lastAllCat;
    private int refreshGen;
    private boolean eyeOffShown;
    private Boolean lastBoost;
    private int boostArmDir;
    private long boostArmAt;
    /** apply() is re-rendering the open page: its shown hook must not read again (that read would apply again). */
    private boolean applying;
    /** The section last told to a ProjectorFeatures.Live adapter, and the generation of its live callback. */
    private String liveSection;
    private int liveGen;

    /** Reads the profile from the app's assets; problems are logged and give an empty menu (fail closed). */
    public static MenuProfile profile(Context c) {
        String json = "";
        try (InputStream in = c.getAssets().open(PROFILE_ASSET)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            json = new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "no menu profile " + PROFILE_ASSET + ": " + e);
        }
        MenuProfile p = MenuProfile.parse(json);
        for (String s : p.problems()) Log.e(TAG, "menu profile: " + s);
        return p;
    }

    /** The menu in the overlay host (Z9X, HB). */
    public MenuController(Context c, ProjectorFeatures dev, Pages devicePages) {
        this(c, dev, devicePages, null);
    }

    /** The menu in {@code host} (null: the overlay host); the X50 gives its ActivityPanelHost. */
    public MenuController(Context c, ProjectorFeatures dev, Pages devicePages, PanelHost host) {
        this.app = c.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.dev = dev;
        this.devicePages = devicePages;
        this.host = host != null ? host : OverlayPanelHost.get(app);
        this.profile = profile(app);
        this.spec = MenuSpec.of(profile);
        for (String missing : missingInAdapter()) Log.e(TAG, "adapter lacks " + missing + ": its rows are not shown");
        for (String sec : spec.sections.keySet()) pages.put(sec, buildSection(sec));
        buildPopups();
        root = buildRoot();
        lastAllCat = spec.hasSection(MenuSpec.PICTURE) ? MenuSpec.PICTURE : null;
        String last = prefs.getString(KEY_LAST_TILE, null);
        if ("manual_keystone".equals(last)) last = MenuSpec.KEYSTONE;           // the v6.3 tile is the Keystone tile now
        if (last != null) card.remember(last);
        for (Map.Entry<String, Page> e : pages.entrySet()) {
            for (Extension x : extensions(e.getKey())) addRows(e.getKey(), e.getValue(), x);
        }
    }

    private static Set<String> set(String... s) { return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(s))); }

    private static List<Extension> extensions(String section) {
        List<Extension> l = EXTENSIONS.get(section);
        return l == null ? Collections.emptyList() : l;
    }

    /** Features of the profile the adapter does not implement (shown nowhere). */
    private List<String> missingInAdapter() {
        List<String> out = new ArrayList<>();
        for (String f : profile.features()) if (!dev.implemented().contains(f)) out.add(f);
        return out;
    }

    private boolean has(String f) {
        if (!profile.has(f) || !dev.implemented().contains(f)) return false;
        try {
            return dev.available(f);
        } catch (RuntimeException e) {
            Log.w(TAG, "available " + f + ": " + e);
            return false;
        }
    }

    private String s(int res) { return res == 0 ? "" : app.getString(res); }

    /** A string of the app or the module by resource name ("" when it does not exist). */
    private String named(String name) {
        int id = name == null ? 0 : app.getResources().getIdentifier(name, "string", app.getPackageName());
        if (id == 0 && name != null) Log.w(TAG, "no string " + name);
        return id == 0 ? "" : app.getString(id);
    }

    // ================================================================== building

    private Page buildSection(String sec) {
        Page p = new Page(s(MenuLabels.page(sec)));
        TextRow top = new TextRow(app, "");                                   // the section's note line (state.note(sec))
        top.setVisibility(View.GONE);
        p.add(top);
        notes.put(sec, top);
        for (MenuSpec.Group g : spec.sections.get(sec)) {
            List<String> present = new ArrayList<>();
            for (String f : g.rows) if (has(f)) present.add(f);
            if (present.isEmpty()) continue;
            if (g.header != null) {
                int h = MenuLabels.header(g.header);
                HeaderRow hr = p.add(new HeaderRow(app, h != 0 ? s(h) : g.header.toUpperCase()));
                headers.put(hr, present);
            }
            for (String f : present) addRow(p, f);
        }
        String foot = dev.footer(sec);
        if (foot != null) p.add(new TextRow(app, named(foot)));
        p.setOnShown(() -> onPageShown(sec));
        return p;
    }

    private void onPageShown(String sec) {
        refresh();
        live(sec);
        for (Extension x : extensions(sec)) {
            try {
                x.onPageShown();
            } catch (Throwable t) {
                Log.e(TAG, "extension onPageShown " + sec, t);
            }
        }
    }

    private void addRows(String sec, Page p, Extension x) {
        try {
            x.addRows(app, p);
        } catch (Throwable t) {
            Log.e(TAG, "extension addRows " + sec, t);
        }
    }

    private void extensionAdded(String sec, Extension x) {
        Page p = pages.get(sec);
        if (p == null) {
            Log.w(TAG, "extension for " + sec + " not shown: this profile has no such section");
            return;
        }
        addRows(sec, p, x);
        if (MenuSpec.GAME.equals(sec)) refreshGamePopup();
        if (panel != null && panel.isShowing() && panel.currentPage() == p) panel.refreshPage();
    }

    /** A CHOICE whose options are exactly off, on (Z9X eye protection) is one switch, as stock. */
    private boolean switchChoice(String f) {
        return Feature.def(f).kind == Feature.Kind.CHOICE && profile.options(f).equals(Arrays.asList("off", "on"));
    }

    private void addRow(Page p, String f) {
        Feature.Def d = Feature.def(f);
        if (Feature.BOOST.equals(f)) {                        // Boost is the slider's top step; here only its hint
            p.add(new TextRow(app, s(MenuLabels.boostSummary(profile.boostSummary()))));
            return;
        }
        CharSequence title = s(MenuLabels.title(f));
        View row = null;
        switch (d.kind) {
            case TOGGLE:
                row = p.add(new ToggleRow(app, title, (r, wanted) -> safe("toggle " + f, () -> onToggle(f, r, wanted))));
                break;
            case CHOICE: {
                if (switchChoice(f)) {
                    row = p.add(new ToggleRow(app, title, (r, wanted) -> safe("switch " + f, () -> onToggle(f, r, wanted))));
                    break;
                }
                List<String> opts = profile.options(f);
                shownOptions.put(f, opts);
                if (INLINE_RADIOS.contains(f)) {
                    List<CheckRow> list = new ArrayList<>();
                    for (String o : opts) {
                        CheckRow cr = p.add(new CheckRow(app, optionLabel(f, o), false, () -> safe("radio " + f, () -> choose(f, o))));
                        list.add(cr);
                        featureOf.put(cr, f);
                    }
                    inline.put(f, list);
                    return;
                }
                ChoiceRow cr = new ChoiceRow(app, title, labels(f, opts), (r, i) -> safe("choice " + f, () -> {
                    List<String> shown = shownOptions.get(f);
                    if (shown != null && i >= 0 && i < shown.size()) choose(f, shown.get(i));
                    else refresh();
                }));
                cr.setCommitDelay(Feature.PICTURE_MODE.equals(f) ? PICTURE_MODE_COMMIT_MS : CHOICE_COMMIT_MS);
                if (Feature.PICTURE_MODE.equals(f)) cr.setOnOpen(() -> safe("picture mode list", this::openModeList));
                row = p.add(cr);
                TextRow note = p.add(new TextRow(app, ""));
                note.setVisibility(View.GONE);
                notes.put(f, note);
                break;
            }
            case SLIDER:
                if (Feature.PICTURE_PQ.equals(f)) {
                    int mn = profile.number(f, "min", 0), mx = profile.number(f, "max", 100);
                    for (String item : profile.items(f)) {
                        String id = f + "." + item;
                        SliderRow sr = p.add(new SliderRow(app, s(MenuLabels.pq(item)), mn, mx, slide(id)));
                        rows.put(id, sr);
                        featureOf.put(sr, id);
                    }
                    return;
                }
                if (Feature.LAMP.equals(f)) {                 // the bottom slider of the card; here only its hints
                    p.add(new TextRow(app, s(MenuLabels.summary(f))));
                    return;
                }
                row = p.add(new SliderRow(app, title, profile.number(f, "min", 0), profile.number(f, "max", 100), slide(f)));
                break;
            case ACTION:
                row = p.add(new NavRow(app, title, () -> safe("action " + f, () -> action(f))).setChevron(false));
                break;
            case SCREEN:
                row = p.add(new NavRow(app, title, () -> safe("screen " + f, () -> screen(f))));
                break;
            case INFO:
                row = p.add(new NavRow(app, title).setChevron(false));
                break;
            default:
                return;
        }
        rows.put(f, row);
        featureOf.put(row, f);
        int sum = MenuLabels.summary(f);
        if (sum != 0) p.add(new TextRow(app, s(sum)));
        View tip = tip(f);
        if (tip != null) {
            tips.put(row, tip);
            pageTips.put(p, tip);
        }
    }

    private SliderRow.OnSlide slide(String f) {
        return new SliderRow.OnSlide() {
            @Override public void onChanging(SliderRow row, int value) { touch(row); }
            @Override public void onCommit(SliderRow row, int value) {
                safe("slider " + f, () -> {
                    Note n = dev.blocked(f, Integer.toString(value), state);
                    if (n != null) { note(n, f); refresh(); return; }
                    set(f, Integer.toString(value));
                });
            }
        };
    }

    private static void touch(SliderRow r) { r.setTag(SystemClock.uptimeMillis()); }

    /** Do not move a slider under the user's finger (a read-back of an older commit). */
    private static boolean sliderBusy(SliderRow r) {
        Object t = r.getTag();
        return r.isFocused() && t instanceof Long && SystemClock.uptimeMillis() - (Long) t < SLIDER_GUARD_MS;
    }

    /** The hint picture of a row: only where the profile names one and the module can draw it. */
    private View tip(String f) {
        String h = profile.hint(f);
        if (h == null) return null;
        int kind = "picture_mode".equals(h) ? QpdTip.PICTURE_MODE : "rear_projection".equals(h) ? QpdTip.REAR_PROJECTION : 0;
        if (kind == 0) {
            Log.w(TAG, "hint picture " + h + " of " + f + " is not drawable yet: no picture");
            return null;
        }
        return new QpdTip(app, kind, s(MenuLabels.tipText(h)));
    }

    private CharSequence optionLabel(String f, String token) {
        String rt = state == null ? null : state.label(f, token);
        if (rt != null) return rt;
        int r = MenuLabels.option(f, token);
        if (r == 0) Log.w(TAG, "no label for " + f + " option " + token);
        return r == 0 ? token : s(r);
    }

    private CharSequence[] labels(String f, List<String> opts) {
        CharSequence[] out = new CharSequence[opts.size()];
        for (int i = 0; i < out.length; i++) out[i] = optionLabel(f, opts.get(i));
        return out;
    }

    /** The wide tiles of picture, sound and game open a radio list (layout D); mirroring has its receiver popup. */
    private void buildPopups() {
        radioPopup(MenuSpec.PICTURE, Feature.PICTURE_MODE, R.string.lumen_m_panel_picture_mode, R.string.lumen_qpd_picture_more);
        radioPopup(MenuSpec.SOUND, Feature.SOUND_MODE, R.string.lumen_m_panel_sound_mode, R.string.lumen_qpd_sound_more);
        if (pages.containsKey(MenuSpec.GAME)) {
            Page g = new Page(s(MenuLabels.section(MenuSpec.GAME)));
            g.setOnShown(() -> onPageShown(MenuSpec.GAME));
            popups.put(MenuSpec.GAME, g);
            if (has(Feature.GAME_MODE) && !switchChoice(Feature.GAME_MODE)) {
                for (String o : profile.options(Feature.GAME_MODE)) {
                    gameRadios.add(new CheckRow(app, optionLabel(Feature.GAME_MODE, o), false,
                            () -> safe("game mode item", () -> choose(Feature.GAME_MODE, o))));
                }
            }
            refreshGamePopup();
        }
        if (has(Feature.MIRRORING) && QpdMirror.installed(app)) {
            mirror = new QpdMirror(app, s(R.string.lumen_qpd_mirroring), new QpdMirror.Host() {
                @Override public void closeThen(Runnable r) { MenuController.this.closeThen(r); }
                @Override public boolean setupDone(String what) { return MenuController.this.setupDone(what); }
            });
        }
    }

    private void radioPopup(String sec, String f, int titleRes, int moreRes) {
        if (!has(f) || !pages.containsKey(sec)) return;
        Page p = new Page(s(titleRes));
        p.setOnShown(() -> { refresh(); live(sec); });
        popups.put(sec, p);
        rebuildRadios(sec, f, moreRes, profile.options(f));
    }

    private void rebuildRadios(String sec, String f, int moreRes, List<String> opts) {
        Page p = popups.get(sec);
        if (p == null) return;
        p.clear();
        List<CheckRow> list = new ArrayList<>();
        String cur = state == null ? null : state.get(f);
        for (String o : opts) {
            list.add(p.add(new CheckRow(app, optionLabel(f, o), o.equals(cur), () -> safe("radio " + f, () -> choose(f, o)))));
        }
        p.add(new NavRow(app, s(moreRes), () -> safe("more " + sec, () -> push(pages.get(sec)))));
        View tip = tip(f);
        if (tip != null) pageTips.put(p, tip);
        radios.put(f, list);
    }

    /** Game popup: the mode radios, then every other row of the game page (speed, auto switch, console rows). */
    private void refreshGamePopup() {
        Page g = popups.get(MenuSpec.GAME), full = pages.get(MenuSpec.GAME);
        if (g == null || full == null) return;
        int f = PageAccess.focusIndex(g);
        g.clear();
        View mode = rows.get(Feature.GAME_MODE);
        for (View v : full.rows()) {
            if (v == mode && !gameRadios.isEmpty()) {
                for (CheckRow r : gameRadios) g.add(r);
            } else if (v != notes.get(Feature.GAME_MODE) || gameRadios.isEmpty()) {
                g.add(v);
            }
        }
        g.setInitialFocus(f);
    }

    private Page buildRoot() {
        Page p = new Page(s(R.string.lumen_m_panel_title));
        card = new QpdCard(app);
        for (MenuSpec.Tile t : spec.round) tile(t.id, QpdTile.ROUND);
        for (List<MenuSpec.Tile> row : spec.rows) {
            for (MenuSpec.Tile t : row) {
                String id = t.id;
                if (Feature.MIRRORING.equals(id) || Feature.USB_FILES.equals(id)) {
                    String last = lastSmall();
                    if (last == null) continue;
                    id = last;
                } else if (!tilePresent(id)) {
                    continue;
                }
                tile(id, MenuSpec.WIDE.equals(t.size) ? QpdTile.WIDE : QpdTile.SMALL);
            }
        }
        if (has(Feature.LAMP)) {
            lamp = new QpdLampRow(app, s(MenuLabels.title(Feature.LAMP)), new SliderRow.OnSlide() {
                @Override public void onChanging(SliderRow row, int value) { touch(row); }
                @Override public void onCommit(SliderRow row, int value) {
                    safe("lamp", () -> {
                        if (boostOn() || value > lampMax() || value < lampMin()) return;
                        set(Feature.LAMP, Integer.toString(value));
                    });
                }
            });
            lamp.setRange(lampMin(), lampMax());
            final int bl = spec.boostLevel();
            lamp.setFormatter(v -> bl > 0 && v >= bl ? s(R.string.lumen_m_panel_boost) : Integer.toString(v));
            lamp.setEdgeListener((row, dir) -> {
                // Boost only where this projector reads it: never a dead Boost step
                if (dir > 0 && bl > 0 && has(Feature.BOOST) && !boostOn() && state != null && state.on(Feature.BOOST) != null) {
                    safe("boost", () -> boostPress(1));
                }
                return true;
            });
            card.setLamp(lamp);
        }
        p.setOnShown(() -> { refresh(); live(null); });
        return p;
    }

    /** A feature tile exists when the feature is usable; a section tile when its section has rows. */
    private boolean tilePresent(String id) {
        return Feature.known(id) ? has(id) : spec.hasSection(id);
    }

    /** The last small tile: screen mirroring where the receiver is installed, else USB files, else none. */
    private String lastSmall() {
        String l = QpdLayout.lastSmallTile(mirror != null, has(Feature.USB_FILES) && usbFilesAvailable());
        return QpdLayout.TILE_MIRROR.equals(l) ? Feature.MIRRORING : QpdLayout.TILE_USB.equals(l) ? Feature.USB_FILES : null;
    }

    private void tile(String id, int kind) {
        int label = MenuLabels.tile(id);
        CharSequence title = label != 0 ? s(label) : s(MenuLabels.title(id));
        QpdTile t = new QpdTile(app, id, kind, MenuLabels.icon(id), title, () -> safe("tile " + id, () -> onTile(id)));
        if (Feature.KEYSTONE_AK_NOW.equals(id)) t.setValue(s(R.string.lumen_qpd_autocorrect_value));
        card.addTile(t);
    }

    private int lampMin() { return profile.number(Feature.LAMP, "min", 1); }

    private int lampMax() { return profile.number(Feature.LAMP, "max", 10); }

    private boolean boostOn() { return state != null && Boolean.TRUE.equals(state.on(Feature.BOOST)) && spec.boostLevel() > 0; }

    // ================================================================== showing

    private boolean ours() { return panel != null && panel.isShowing() && host.current() == panel; }

    public void toggle() {
        if (ours() && !panel.isBar()) {
            host.dismiss(panel);
            return;
        }
        show(null);
    }

    /** Opens the card, or the popup of a section ("general" = All settings, "lamp" = the light bar). */
    public void show(String section) {
        if (section != null && spec.hasSection(section) && refusedPage(section)) return;
        show(section, null);
    }

    /** {@code section}'s popup with the focus on {@code focusRow} (a row of that page), or on its first row. */
    private void show(String section, View focusRow) {
        if (MenuSpec.GENERAL.equals(section)) { openAll(section, focusRow); return; }
        if (MenuSpec.LAMP.equals(section)) { showBar(); return; }
        if (Feature.MIRRORING.equals(section) && mirror == null) section = null;
        openSection(section, focusRow);
    }

    private Page popupPage(String key) {
        if (key == null) return null;
        if (Feature.INPUT.equals(key)) return devicePages == null ? null : devicePages.page(Feature.INPUT, this);
        if (Feature.MIRRORING.equals(key)) return mirror == null ? null : mirror.page;
        if (MenuSpec.LAMP.equals(key) || MenuSpec.GENERAL.equals(key)) return null;
        Page p = popups.get(key);
        return p != null ? p : pages.get(key);
    }

    private void openSection(String key, View focusRow) {
        List<Page> open = new ArrayList<>();
        Page target = popupPage(key);
        if (target != null) {
            card.remember(key);
            Page full = pages.get(key);
            if (focusRow != null && full != null && full != target && !target.rows().contains(focusRow)
                    && full.rows().contains(focusRow)) {
                target.setInitialFocus(-1);
                open.add(target);
                full.setInitialFocus(full.rows().indexOf(focusRow));
                open.add(full);
            } else {
                target.setInitialFocus(focusRow == null ? checkedIndex(target) : target.rows().indexOf(focusRow));
                open.add(target);
            }
            if (target == popups.get(MenuSpec.GAME)) refreshGamePopup();
        }
        if (ours() && !panel.isBar()) {
            while (panel.pop()) { /* back to the card */ }
            for (Page p : open) panel.push(p);
            panel.resetAutoHide();
            return;
        }
        QpdPanel v = new QpdPanel(root, card, qpdHost, false);   // fresh page stack each open; rows are reused
        for (Page p : open) v.push(p);                          // before the window exists: no slide
        card.settle();
        if (panel != null && panel.isShowing()) host.dismiss(panel);
        panel = v;
        if (!host.show(v)) Log.w(TAG, "menu window could not be added");
    }

    /** Remote brightness: the slider alone in a slim bar, closing a moment after the last key. */
    private void showBar() {
        if (lamp == null) { show(null, null); return; }
        if (ours() && panel.isBar()) {
            panel.resetAutoHide();
            return;
        }
        QpdPanel v = new QpdPanel(root, card, qpdHost, true);
        if (panel != null && panel.isShowing()) host.dismiss(panel);
        panel = v;
        if (!host.show(v)) Log.w(TAG, "brightness bar window could not be added");
    }

    /** Gear key, short press (profile keys.gear_short). */
    public void onGearShort() { toggle(); }

    /** Gear key, long press (profile keys.gear_long): the game menu, the corner reset or nothing. */
    public void onGearLong(Runnable fallback) {
        String what = profile.gearLong();
        if ("game_menu".equals(what) && profile.gameMenu()) GameMenu.onGearLong(app, fallback);
        else if ("keystone_reset".equals(what) && has(Feature.KEYSTONE_RESTORE)) run(Feature.KEYSTONE_RESTORE);
        else if (fallback != null) fallback.run();
    }

    private void openAll(String category, View focusRow) {
        List<QpdPanel.Cat> cats = new ArrayList<>();
        for (String c : spec.allSettings) {
            Page p = pages.get(c);
            if (p != null) cats.add(new QpdPanel.Cat(c, s(MenuLabels.category(c)), MenuLabels.icon(tileOf(c)), p));
        }
        if (cats.isEmpty()) return;
        String want = category != null ? category : lastAllCat;
        int idx = 0;
        for (int i = 0; i < cats.size(); i++) if (cats.get(i).key.equals(want)) idx = i;
        lastAllCat = cats.get(idx).key;
        card.remember(MenuSpec.GENERAL);                      // BACK from All settings lands on the gear
        if (ours() && !panel.isBar()) {
            panel.openAll(cats, idx, MenuSpec.GENERAL, focusRow);
            panel.resetAutoHide();
            return;
        }
        QpdPanel v = new QpdPanel(root, card, qpdHost, false);
        v.openAll(cats, idx, MenuSpec.GENERAL, focusRow);     // before the window exists: no slide
        card.settle();
        if (panel != null && panel.isShowing()) host.dismiss(panel);
        panel = v;
        if (!host.show(v)) Log.w(TAG, "all settings window could not be added");
    }

    private static String tileOf(String section) {
        return MenuSpec.GENERAL.equals(section) ? "general" : section;
    }

    private void onTile(String id) {
        if (panel == null || !panel.isShowing()) return;      // closed during the press
        card.remember(id);
        switch (id) {
            case MenuSpec.GENERAL: openAll(null, null); return;
            case Feature.KEYSTONE_AK_NOW: closeThen(() -> run(id)); return;
            case Feature.ZOOM:
            case Feature.ZOOM_DIGITAL:
                if (!dev.open(id, () -> show(null, null))) Log.w(TAG, id + " cannot open now");   // BACK comes back here
                return;
            case Feature.BLUETOOTH:
                if (setupDone(id)) closeThen(() -> openSystemPage(Settings.ACTION_BLUETOOTH_SETTINGS));
                return;
            case Feature.NETWORK:
                if (setupDone(id)) closeThen(() -> openSystemPage(Settings.ACTION_WIFI_SETTINGS));
                return;
            case Feature.USB_FILES:
                if (setupDone(id)) closeThen(this::openUsbFiles);
                return;
            case Feature.INPUT:
                if (!setupDone(id)) return;
                if (popupPage(id) == null) {                  // no input popup of the device: its input screen (HB, Play 6)
                    closeThen(() -> { if (!dev.open(id, () -> show(null, null))) Log.w(TAG, "input cannot open now"); });
                    return;
                }
                break;
            default:
                break;
        }
        if (spec.hasSection(id) && refusedPage(id)) return;
        Page p = popupPage(id);
        if (p == null) return;
        if (p == popups.get(MenuSpec.GAME)) refreshGamePopup();
        while (panel.depth() > 1 && panel.pop()) { /* one popup at a time */ }
        card.settle();
        p.setInitialFocus(checkedIndex(p));                   // radio lists open on the current choice, others on row 1
        panel.push(p);
    }

    /** The stock refusal of a whole section page (the game page off HDMI, with Dolby Vision, in 3D). */
    private boolean refusedPage(String section) {
        Note n = dev.blocked(section, null, state);
        if (n == null) return false;
        note(n, null, s(MenuLabels.section(section)));
        return true;
    }

    private static int checkedIndex(Page p) {
        List<View> r = p.rows();
        for (int i = 0; i < r.size(); i++) {
            View v = r.get(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            if (v instanceof CheckRow && ((CheckRow) v).isChecked()) return i;
            if (v instanceof QpdRowFrame.Radio && ((QpdRowFrame.Radio) v).isSelected()) return i;
        }
        return -1;
    }

    /** The picture mode list (OK on the stepping row): the modes of this content, the current one marked. */
    private void openModeList() {
        String f = Feature.PICTURE_MODE;
        String cur = state == null ? null : state.get(f);
        if (cur == null) { readyOrNote(); return; }
        Page p = new Page(s(MenuLabels.title(f)));
        int focus = -1;
        List<String> opts = shownOptions.get(f);
        for (int i = 0; i < opts.size(); i++) {
            final String o = opts.get(i);
            if (o.equals(cur)) focus = i;
            p.add(new CheckRow(app, optionLabel(f, o), o.equals(cur), () -> safe("mode list item", () -> {
                if (panel != null) panel.pop();
                choose(f, o);
            })));
        }
        p.setInitialFocus(focus);
        push(p);
    }

    // ================================================================== the pages API (device pages, R6)

    /** Pushes a device page onto the shown menu (no-op when it is closed). */
    public void push(Page p) {
        if (p != null && panel != null && panel.isShowing()) panel.push(p);
    }

    /** A stock question inside the menu (focus starts on Cancel); {@code onOk} runs on yes. */
    public void confirm(CharSequence title, CharSequence msg, CharSequence ok, Runnable onOk) {
        if (panel == null || !panel.isShowing()) return;
        panel.push(DialogPanel.confirmPage(app, panel, title, msg, ok, s(R.string.lumen_m_cancel),
                () -> safe("confirm", onOk)));
    }

    /** {@link #confirm} with string resource ids. */
    public void confirm(int titleRes, CharSequence msg, int okRes, Runnable onOk) { confirm(s(titleRes), msg, s(okRes), onOk); }

    /** Repaints {@code page} when it is the page shown. */
    public void refreshPage(Page page) {
        if (panel != null && panel.isShowing() && (page == null || panel.currentPage() == page)) panel.refreshPage();
    }

    /** Closes the menu and runs {@code r} when its exit animation ended. */
    public void closeThen(Runnable r) {
        if (panel != null) host.dismiss(panel);
        Ui.main().postDelayed(() -> safe("after close", r), Theme.PANEL_EXIT_MS + 60);
    }

    /** True while this menu is the panel on screen. */
    public boolean isShowing() { return ours(); }

    /** Closes the menu if it is shown. */
    public void dismiss() { if (panel != null && panel.isShowing()) host.dismiss(panel); }

    /** The host the menu's windows live in. */
    public PanelHost host() { return host; }

    /** False (and the "finish the setup first" note) while the first-run setup runs. */
    public boolean setupDone(String what) {
        if (dev.setupDone()) return true;
        Log.i(TAG, "setup not complete: " + what + " ignored");
        host.toast(R.string.lumen_m_toast_setup_running);
        return false;
    }

    /**
     * After the autofocus card (R7): reopens the menu where it is now, the section page with its focused row, All
     * settings on its category, or the card; null when the menu is not the panel shown.
     */
    public Runnable restorer() {
        if (!ours()) return null;
        Page top = panel.currentPage();
        if (panel.isAllMode()) {
            final String cat = panel.allCategory();
            final View r = focused(top);
            return () -> openAll(cat, r);
        }
        String section = top == root ? null : sectionOf(top);
        final View row = section == null ? null : focused(top);
        final String sec = section;
        Log.i(TAG, "restorer: " + (sec == null ? "card" : sec + (row == null ? "" : ", focused row")));
        if (Feature.INPUT.equals(sec) || Feature.MIRRORING.equals(sec)) return () -> openSection(sec, row);
        return () -> show(sec, row);
    }

    private Runnable onClosed;

    /**
     * Called on the main thread when the last menu window closed (the card, a page or All settings). A low-memory
     * owner (Play 6) drops the controller here and builds a new one on the next open.
     */
    public void setOnClosed(Runnable r) { onClosed = r; }

    /** Locale change (R7): closes the menu if it is shown; the owner builds a new one with the new texts. */
    public void close() {
        if (ours()) host.dismiss(panel);
    }

    /** Re-reads the values only while the menu is on screen (game profile, voice). */
    public void refreshIfShowing() {
        if (ours()) refresh();
    }

    private static View focused(Page p) {
        if (p == null) return null;
        for (View v : p.rows()) if (v.isFocused()) return v;
        return null;
    }

    private String sectionOf(Page p) {
        if (p == null) return null;
        for (Map.Entry<String, Page> e : popups.entrySet()) if (e.getValue() == p) return e.getKey();
        if (mirror != null && p == mirror.page) return Feature.MIRRORING;
        if (devicePages != null && p == devicePages.page(Feature.INPUT, this)) return Feature.INPUT;
        for (Map.Entry<String, Page> e : pages.entrySet()) if (e.getValue() == p) return e.getKey();
        return null;
    }

    /** BACK of a screen opened by the focused row of {@code section}: that page with the focus on that row. */
    private Runnable backToRow(String section) {
        Page p = section == null ? null : pages.get(section);
        final View row = p == null ? null : focused(p);
        final boolean all = panel != null && panel.isShowing() && panel.isAllMode() && section != null;
        return () -> {
            if (all) openAll(section, row);
            else show(section, row);
        };
    }

    private void openSystemPage(String action) {
        try {
            Ui.wakeFromDream(app);
            Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(app.getPackageManager()) == null) i = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, action + ": " + t);
            host.toast(R.string.lumen_m_toast_failed);
        }
    }

    /** Lumen Home's USB files screen (its exported FilesLauncher alias). */
    private static final String HOME_PKG = "org.lumen.home", HOME_FILES = "org.lumen.home.FilesLauncher";

    private Intent usbFilesIntent() {
        return new Intent(Intent.ACTION_MAIN).setClassName(HOME_PKG, HOME_FILES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private boolean usbFilesAvailable() {
        try {
            return usbFilesIntent().resolveActivity(app.getPackageManager()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private void openUsbFiles() {
        try {
            Ui.wakeFromDream(app);
            app.startActivity(usbFilesIntent());
        } catch (Throwable t) {
            Log.w(TAG, "usb files: " + t);
            host.toast(R.string.lumen_m_toast_failed);
        }
    }

    // ================================================================== keys

    /** Every key of the menu window, before the rows (QpdPanel.onKeyEvent). */
    private boolean onKey(KeyEvent ev) {
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN, first = down && ev.getRepeatCount() == 0;
        View f = focusedView();
        // BACK while an equalizer band is being adjusted only ends adjusting
        if (f instanceof EqBandsRow && ((EqBandsRow) f).isEditing() && (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE)) {
            if (ev.getAction() == KeyEvent.ACTION_UP && !ev.isCanceled()) ((EqBandsRow) f).stopEditing();
            return true;
        }
        boolean step = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (step && f instanceof SliderRow) {
            String feat = f == lamp ? Feature.LAMP : featureOf.get(f);
            Note n = feat == null ? null : dev.blocked(feat, null, state);
            if (n != null) {                                  // stock: the slider does not move, one note per press
                if (first) note(n, feat);
                return true;
            }
        }
        if (step && f instanceof ChoiceRow && down) {          // the option list the user steps through stays until commit
            String feat = featureOf.get(f);
            if (feat != null) steppedAt.put(feat, SystemClock.uptimeMillis());
        }
        // leaving Boost: at "Boost" a press towards the start arms, a second one within 3 s turns it off
        int towardsStart = lamp != null && lamp.isRtl() ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        if (boostOn() && f == lamp && k == towardsStart) {
            if (first) boostPress(-1);
            return true;
        }
        return false;
    }

    private View focusedView() {
        if (lamp != null && lamp.isFocused()) return lamp;
        Page top = panel == null ? null : panel.currentPage();
        View v = focused(top);
        if (v != null) return v;
        for (View r : rows.values()) if (r.isFocused()) return r;
        return null;
    }

    /** Boost: two presses beyond the top level to enter, two below Boost to leave. dir +1 enter, -1 leave. */
    private void boostPress(int dir) {
        long now = SystemClock.uptimeMillis();
        if (boostArmDir == dir && now - boostArmAt < BOOST_PRESS_WINDOW_MS) {
            boostArmDir = 0;
            String v = dir > 0 ? "on" : "off";
            Note n = dev.blocked(Feature.BOOST, v, state);
            if (n != null) { note(n, Feature.BOOST); return; }
            set(Feature.BOOST, v);
            return;
        }
        boostArmDir = dir;
        boostArmAt = now;
        host.note(s(dir > 0 ? R.string.lumen_m_panel_boost_press_again : R.string.lumen_m_panel_boost_leave_press_again));
    }

    // ================================================================== user actions

    private void onToggle(String f, ToggleRow row, boolean wanted) {
        String v = wanted ? "on" : "off";
        Note n = dev.blocked(f, v, state);
        if (n != null) {
            note(n, f);
            row.setChecked(!wanted);
            refresh();
            return;
        }
        ProjectorFeatures.Confirm c = dev.confirm(f, v, state);
        if (c != null) {
            row.setChecked(!wanted);                          // until confirmed; the read-back after OK shows the result
            ask(c, () -> setConfirmed(f, v));
            return;
        }
        set(f, v);
    }

    private void choose(String f, String token) {
        if (SKIP_SAME.contains(f) && state != null && token.equals(state.get(f))) return;
        if (SETUP_GATED.contains(f) && !setupDone(f)) { refresh(); return; }
        Note n = dev.blocked(f, token, state);
        if (n != null) {
            note(n, f);
            refresh();
            return;
        }
        ProjectorFeatures.Confirm c = dev.confirm(f, token, state);
        if (c != null) {
            Runnable yes = CLOSE_FIRST.contains(f) ? () -> closeThen(() -> setConfirmed(f, token)) : () -> setConfirmed(f, token);
            ask(c, yes);
            Ui.main().postDelayed(this::refresh, CONFIRM_RECHECK_MS);   // a canceled question leaves the real value
            return;
        }
        if (CLOSE_FIRST.contains(f)) closeThen(() -> set(f, token));
        else set(f, token);
    }

    private void action(String f) {
        Note n = dev.blocked(f, null, state);
        if (n != null) { note(n, f); return; }
        ProjectorFeatures.Confirm c = dev.confirm(f, null, state);
        if (c != null) {
            ask(c, () -> { if (dev.ready()) dev.runConfirmed(f, r -> after(f, r)); else readyOrNote(); });
            return;
        }
        if (CLOSE_FIRST.contains(f)) closeThen(() -> run(f));
        else run(f);
    }

    private void screen(String f) {
        if (SETUP_GATED.contains(f) && !setupDone(f)) return;
        Note n = dev.blocked(f, null, state);
        if (n != null) { note(n, f); return; }
        Page p = devicePages == null ? null : devicePages.page(f, this);
        if (p != null) {
            p.setInitialFocus(-1);
            push(p);
            return;
        }
        final Runnable back = backToRow(lastSectionOf(f));
        if (CLOSE_FIRST.contains(f)) {
            closeThen(() -> { if (!dev.open(f, back)) Log.w(TAG, "screen " + f + " cannot open now"); });
            return;
        }
        if (!dev.open(f, back)) Log.w(TAG, "screen " + f + " cannot open now");   // it replaces the menu
    }

    private void ask(ProjectorFeatures.Confirm c, Runnable yes) {
        confirm(named(c.title), named(c.message), named(c.ok), yes);
    }

    private String lastSectionOf(String f) {
        for (String sec : spec.sections.keySet()) if (spec.rowsOf(sec).contains(f)) return sec;
        return null;
    }

    // ================================================================== device calls

    private boolean readyOrNote() {
        if (dev.ready()) return true;
        host.toast(R.string.lumen_m_toast_not_ready);
        refresh();
        return false;
    }

    private void set(String f, String value) {
        if (!readyOrNote()) return;
        dev.set(f, value, r -> after(f, value, r));
    }

    private void setConfirmed(String f, String value) {
        if (!readyOrNote()) return;
        dev.setConfirmed(f, value, r -> after(f, value, r));
    }

    private void run(String f) {
        if (!readyOrNote()) return;
        dev.run(f, r -> after(f, r));
    }

    private void after(String f, ProjectorFeatures.Result r) { after(f, null, r); }

    private void after(String f, String value, ProjectorFeatures.Result r) {
        if (r == null) { refresh(); return; }
        switch (r.kind) {
            case REFUSED:
                if (r.note != null) note(r.note, f);
                else if (r.reason != null) note(Note.of(r.reason), f);
                break;
            case FAILED: host.toast(R.string.lumen_m_toast_failed); break;
            case NOT_READY: host.toast(R.string.lumen_m_toast_not_ready); break;
            case ASK:
                if (r.confirm != null && value != null) ask(r.confirm, () -> setConfirmed(f, value));
                break;
            default:
                if (r.note != null) note(r.note, f);
                break;
        }
        if (r.after != null) apply(r.after); else refresh();
    }

    /** Shows a device note (R8: formatted, titled); {@code f} names the row for a row title. */
    private void note(ProjectorFeatures.Note n, String f) { note(n, f, null); }

    private void note(ProjectorFeatures.Note n, String f, CharSequence fallbackTitle) {
        if (n == null || n.res == null && n.text == null) return;   // a silent refusal: the rows only re-read
        if (n.toast) {
            int id = app.getResources().getIdentifier(n.res, "string", app.getPackageName());
            if (id != 0) host.toast(id);
            return;
        }
        CharSequence text = n.text;
        if (text == null) {
            Object[] args = new Object[n.args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = n.args[i];
                args[i] = a instanceof String && ((String) a).startsWith("@") ? named(((String) a).substring(1)) : a;
            }
            int id = n.res == null ? 0 : app.getResources().getIdentifier(n.res, "string", app.getPackageName());
            if (id == 0) { Log.w(TAG, "note without a text: " + n); return; }
            text = args.length == 0 ? app.getString(id) : app.getString(id, args);
        }
        CharSequence title = null;
        if (ProjectorFeatures.Note.ROW_TITLE.equals(n.title)) title = rowTitle(f, fallbackTitle);
        else if (n.title != null && n.title.startsWith("@")) title = named(n.title.substring(1));
        else if (n.title != null && n.title.startsWith("=")) title = n.title.substring(1);
        if (title == null || title.length() == 0) host.note(text);
        else host.note(title, text);
    }

    private CharSequence rowTitle(String f, CharSequence fallback) {
        View v = f == null ? null : rows.get(f);
        if (v instanceof Row) return ((Row) v).getTitle();
        if (f != null && f.startsWith(Feature.PICTURE_PQ + ".")) {
            return s(MenuLabels.pq(f.substring(Feature.PICTURE_PQ.length() + 1)));
        }
        if (f != null && Feature.known(f)) return s(MenuLabels.title(f));
        return fallback;
    }

    /** Re-reads every value and updates the rows (main thread); an older read arriving late is dropped. */
    public void refresh() {
        if (applying) return;               // the shown hook of apply()'s own re-render: the values are fresh
        if (!dev.ready()) { apply(null); return; }
        final int gen = ++refreshGen;
        dev.read(st -> {
            if (gen == refreshGen) apply(st);
        });
    }

    private void apply(MenuState st) {
        state = st;
        boolean ok = st != null;
        for (Map.Entry<String, View> e : rows.entrySet()) {
            String f = e.getKey();
            View v = e.getValue();
            if (ok) v.setVisibility(st.hidden(f) ? View.GONE : View.VISIBLE);
            boolean usable = ok && !st.disabled(f);
            String val = ok ? st.get(f) : null;
            if (v instanceof ToggleRow) {
                Boolean on = !ok ? null : switchChoice(f) ? (val == null ? null : "on".equals(val)) : st.on(f);
                ((ToggleRow) v).setChecked(on);
                ((ToggleRow) v).setRowEnabled(usable && on != null);
            } else if (v instanceof ChoiceRow) {
                applyChoice(f, (ChoiceRow) v, st, usable);
            } else if (v instanceof SliderRow) {
                Integer n = ok ? st.number(f) : null;
                if (!sliderBusy((SliderRow) v)) ((SliderRow) v).setValue(n);
                ((SliderRow) v).setRowEnabled(usable && n != null);
            } else if (v instanceof NavRow) {
                Feature.Def d = Feature.def(f);
                if (d != null && (d.kind == Feature.Kind.INFO || d.kind == Feature.Kind.SCREEN)) ((NavRow) v).setValue(val);
                if (d != null && d.kind == Feature.Kind.SCREEN) ((NavRow) v).setRowEnabled(st == null || !st.disabled(f));
            }
        }
        for (Map.Entry<String, List<CheckRow>> e : inline.entrySet()) {
            String val = ok ? st.get(e.getKey()) : null;
            List<String> opts = profile.options(e.getKey());
            for (int i = 0; i < e.getValue().size(); i++) {
                CheckRow r = e.getValue().get(i);
                r.setChecked(val != null && opts.get(i).equals(val));
                r.setRowEnabled(val != null);
                if (ok) r.setVisibility(st.hidden(e.getKey()) ? View.GONE : View.VISIBLE);
            }
        }
        for (Map.Entry<String, TextRow> e : notes.entrySet()) {
            String t = ok ? st.note(e.getKey()) : null;
            e.getValue().setText(t);
            e.getValue().setVisibility(t == null ? View.GONE : View.VISIBLE);
        }
        for (Map.Entry<HeaderRow, List<String>> e : headers.entrySet()) {
            if (!ok) continue;
            boolean any = false;
            for (String f : e.getValue()) any |= !st.hidden(f) && !(Feature.PICTURE_PQ.equals(f) && allPqHidden(st));
            e.getKey().setVisibility(any ? View.VISIBLE : View.GONE);
        }
        applyRadios(st);
        applyLamp(st);
        applyTiles(st);
        if (panel != null && panel.isShowing()) {
            applying = true;
            try {
                panel.refreshPage();
            } finally {
                applying = false;
            }
        }
    }

    /** Tells a Live adapter the section on screen (null: the card or no menu); a change starts a new callback. */
    private void live(String section) {
        if (!(dev instanceof ProjectorFeatures.Live)) return;
        if (section == null ? liveSection == null : section.equals(liveSection)) return;
        liveSection = section;
        final int gen = ++liveGen;
        try {
            ((ProjectorFeatures.Live) dev).onScreen(section, section == null ? null : part -> {
                if (gen == liveGen) applyLive(part);
            });
        } catch (Throwable t) {
            Log.w(TAG, "live " + section + ": " + t);
        }
    }

    /** A live update: only the INFO rows it names change, nothing is re-rendered or read again. */
    private void applyLive(MenuState part) {
        if (part == null) return;
        state = state == null ? part : state.with(part);
        for (Map.Entry<String, String> e : part.asMap().entrySet()) {
            View v = rows.get(e.getKey());
            Feature.Def d = Feature.def(e.getKey());
            if (v instanceof NavRow && d != null && d.kind == Feature.Kind.INFO) ((NavRow) v).setValue(e.getValue());
        }
    }

    private boolean allPqHidden(MenuState st) {
        for (String item : profile.items(Feature.PICTURE_PQ)) if (!st.hidden(Feature.PICTURE_PQ + "." + item)) return false;
        return true;
    }

    private void applyChoice(String f, ChoiceRow r, MenuState st, boolean usable) {
        List<String> want = st == null || st.options(f) == null ? profile.options(f) : st.options(f);
        Long at = steppedAt.get(f);
        boolean stepping = at != null && SystemClock.uptimeMillis() - at < CHOICE_COMMIT_MS + 300;
        if (!stepping && (!want.equals(shownOptions.get(f)) || st != null && hasRuntimeLabels(f, want, st))) {
            shownOptions.put(f, want);
            r.setOptions(labels(f, want));
        }
        List<String> shown = shownOptions.get(f);
        String val = st == null ? null : st.get(f);
        int i = val == null ? -1 : shown.indexOf(val);
        if (!stepping) r.setSelected(i);
        r.setPlaceholder(st == null ? null : st.placeholder(f));
        r.setRowEnabled(usable && val != null);
    }

    private static boolean hasRuntimeLabels(String f, List<String> opts, MenuState st) {
        for (String o : opts) if (st.label(f, o) != null) return true;
        return false;
    }

    private void applyRadios(MenuState st) {
        boolean ok = st != null;
        String pm = ok ? st.get(Feature.PICTURE_MODE) : null;
        List<CheckRow> pr = radios.get(Feature.PICTURE_MODE);
        if (pr != null) {
            List<String> opts = ok && st.options(Feature.PICTURE_MODE) != null ? st.options(Feature.PICTURE_MODE)
                    : profile.options(Feature.PICTURE_MODE);
            if (opts.size() != pr.size() || !opts.equals(currentRadioTokens(Feature.PICTURE_MODE))) {
                rebuildRadios(MenuSpec.PICTURE, Feature.PICTURE_MODE, R.string.lumen_qpd_picture_more, opts);
                radioTokens.put(Feature.PICTURE_MODE, opts);
                pr = radios.get(Feature.PICTURE_MODE);
                if (panel != null && panel.isShowing() && panel.currentPage() == popups.get(MenuSpec.PICTURE)) panel.refreshPage();
            }
            for (int i = 0; i < pr.size(); i++) pr.get(i).setChecked(pm != null && opts.get(i).equals(pm));
        }
        List<CheckRow> sr = radios.get(Feature.SOUND_MODE);
        if (sr != null) {
            String sm = ok ? st.get(Feature.SOUND_MODE) : null;
            List<String> opts = profile.options(Feature.SOUND_MODE);
            for (int i = 0; i < sr.size() && i < opts.size(); i++) {
                sr.get(i).setTitle(optionLabel(Feature.SOUND_MODE, opts.get(i)));   // "AI · <sub-mode>" follows the app
                sr.get(i).setChecked(opts.get(i).equals(sm));
            }
        }
        if (!gameRadios.isEmpty()) {
            String gm = ok ? st.get(Feature.GAME_MODE) : null;
            View mode = rows.get(Feature.GAME_MODE);
            List<String> opts = profile.options(Feature.GAME_MODE);
            for (int i = 0; i < gameRadios.size(); i++) {
                gameRadios.get(i).setChecked(opts.get(i).equals(gm));
                if (mode != null) gameRadios.get(i).setVisibility(mode.getVisibility());
            }
        }
    }

    private final Map<String, List<String>> radioTokens = new HashMap<>();

    private List<String> currentRadioTokens(String f) {
        List<String> l = radioTokens.get(f);
        return l != null ? l : profile.options(f);
    }

    private void applyLamp(MenuState st) {
        if (lamp == null) return;
        boolean ok = st != null;
        boolean boost = boostOn();
        if (!sliderBusy(lamp)) {
            if (boost) lamp.setRange(lampMin(), spec.boostLevel()).setValue(spec.boostLevel());
            else lamp.setRange(lampMin(), lampMax()).setValue(ok ? st.number(Feature.LAMP) : null);
        }
        lamp.setRowEnabled(ok && (st.number(Feature.LAMP) != null || boost));
        Boolean b = ok && has(Feature.BOOST) ? st.on(Feature.BOOST) : null;
        if (QpdLayout.boostNoticeDue(lastBoost, b)) host.note(s(R.string.lumen_m_panel_boost), s(R.string.lumen_qpd_boost_note));
        if (b != null) lastBoost = b;
        lamp.invalidate();
    }

    private void applyTiles(MenuState st) {
        boolean ok = st != null;
        QpdTile pic = card.tile(MenuSpec.PICTURE);
        if (pic != null) {
            String m = ok ? st.get(Feature.PICTURE_MODE) : null;
            String over = ok ? st.tile(MenuSpec.PICTURE) : null;
            pic.setValue(over != null ? over : m == null ? null : tileLabel(Feature.PICTURE_MODE, m));
        }
        for (String id : new String[]{Feature.INPUT, MenuSpec.GAME, MenuSpec.SOUND, MenuSpec.PROJECTION}) {
            QpdTile t = card.tile(id);
            if (t != null && (ok && st.tile(id) != null || Feature.INPUT.equals(id) || MenuSpec.GAME.equals(id))) {
                t.setValue(ok ? st.tile(id) : null);
            }
        }
        QpdTile eye = card.tile(MenuSpec.EYE);
        boolean eyeOff = ok && "off".equals(st.get(Feature.EYE));   // crossed out only when it reads off
        if (eye != null && eyeOff != eyeOffShown) {
            eyeOffShown = eyeOff;
            eye.setIcon(eyeOff ? R.drawable.lumen_qpd_ic_eyeoff : R.drawable.lumen_qpd_ic_eye);
        }
    }

    /** Tile value line: the short form where the full name does not fit a square tile in some languages. */
    private CharSequence tileLabel(String f, String token) {
        int r = MenuLabels.tileValue(f, token);
        return r != 0 ? s(r) : optionLabel(f, token);
    }

    private static void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.e(TAG, what + " failed", t);
        }
    }

    private final QpdPanel.Host qpdHost = new QpdPanel.Host() {
        @Override public boolean onKey(KeyEvent ev) { return MenuController.this.onKey(ev); }
        @Override public void onClosed(QpdPanel p) {
            if (p.allCategory() != null) lastAllCat = p.allCategory();
            boostArmDir = 0;
            card.settle();
            String last = card.lastKey();
            if (last != null && !last.equals(prefs.getString(KEY_LAST_TILE, null))) prefs.edit().putString(KEY_LAST_TILE, last).apply();
            if (panel == p) {
                panel = null;
                live(null);
            }
            if (panel == null && onClosed != null) safe("on closed", onClosed);   // no menu window left
        }
        @Override public View tipFor(Page p) { return pageTips.get(p); }
        @Override public View tipForRow(View row) { return tips.get(row); }
        @Override public String anchorFor(Page sectionPage) { return sectionOf(sectionPage); }
        @Override public boolean hasPopup(String key) { return !MenuSpec.GENERAL.equals(key) && popupPage(key) != null; }
        @Override public void openPopup(String key) { onTile(key); }
    };

    /** The profile this menu shows (tests, device glue). */
    public MenuProfile profile() { return profile; }

    /** The last state read (null before the first read). */
    public MenuState state() { return state; }
}
