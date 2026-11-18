// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.icons.IconText;
import org.lumen.menu.ChoiceStep;
import org.lumen.menu.FeatureId;
import org.lumen.menu.FeatureKind;
import org.lumen.menu.IrisRule;
import org.lumen.menu.LockedRow;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.PressAgain;
import org.lumen.menu.ProjectionSetup;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.menu.SignalRule;
import org.lumen.menu.SliderOff;
import org.lumen.menu.ThreeDMode;
import org.lumen.menu.quick.Density;
import org.lumen.menu.quick.Placement;
import org.lumen.menu.quick.Presentation;
import org.lumen.menu.quick.QuickCatalog;
import org.lumen.menu.quick.QuickDefaults;
import org.lumen.menu.quick.QuickLayout;
import org.lumen.menu.quick.QuickLayoutJson;
import org.lumen.menu.quick.QuickPacker;
import org.lumen.menu.quick.QuickStore;
import org.lumen.menu.utils.Texts;
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
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.PanelHost;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.SliderRow;
import org.lumen.projector.ui.TextRow;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.ThemeMode;
import org.lumen.projector.ui.Theme;
import org.lumen.projector.ui.ToggleRow;
import org.lumen.system.SettingsPage;
import org.lumen.system.SystemPages;

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
 * values, notes: {@link MenuState}), Boost with two presses beyond the top level and two to leave it, the light-off
 * with two presses below the lowest level ({@link SliderOff}), the slim
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
    /** The eye protection mode used last (a model with modes): the tile's OK turns it back on. */
    static final String KEY_EYE_ON = "eye_on";

    static final long CHOICE_COMMIT_MS = 500, PICTURE_MODE_COMMIT_MS = 700, SLIDER_GUARD_MS = 800;
    static final long BOOST_PRESS_WINDOW_MS = 3_000, CONFIRM_RECHECK_MS = 300;

    /** Choices shown as radio rows on their page (stock projection page), not as a stepping row. */
    static final Set<FeatureId> INLINE_RADIOS = set(FeatureId.PROJECTION_MODE);
    /** Choosing the value shown already does nothing (stock: picture mode, home screen, projection). */
    static final Set<FeatureId> SKIP_SAME = set(FeatureId.PICTURE_MODE, FeatureId.SETTINGS_LAUNCHER, FeatureId.PROJECTION_MODE,
            FeatureId.PICTURE_3D);
    /** These close the menu first and act when it is gone (they show their own overlay or leave the menu). */
    static final Set<FeatureId> CLOSE_FIRST = set(FeatureId.KEYSTONE_AK_NOW, FeatureId.KEYSTONE_FIT_NOW, FeatureId.FOCUS_AF_NOW,
            FeatureId.FOCUS_MANUAL, FeatureId.SETTINGS_SYSTEM, FeatureId.SETTINGS_PROJECTOR, FeatureId.SETTINGS_HDMI,
            FeatureId.SETTINGS_RECENTS, FeatureId.SETTINGS_LAUNCHER, FeatureId.SETTINGS_SLEEP_TIMER, FeatureId.SETTINGS_REMOTE,
            FeatureId.SETTINGS_LICENCES,
            FeatureId.LAMP_OFF);
    /** A closed panel's exit animation and a frame more: then the action behind it runs. */
    static final long AFTER_CLOSE_MS = Theme.PANEL_EXIT_MS + 60;
    /** These leave the menu for another app or screen: refused while the first-run setup runs. */
    static final Set<FeatureId> SETUP_GATED = set(FeatureId.INPUT, FeatureId.BLUETOOTH, FeatureId.NETWORK,
            FeatureId.SETTINGS_SYSTEM, FeatureId.SETTINGS_PROJECTOR, FeatureId.SETTINGS_CEC, FeatureId.SETTINGS_HDMI,
            FeatureId.SETTINGS_RECENTS, FeatureId.SETTINGS_LAUNCHER);

    /** Device pages shown inside the panel (null: none, the menu then asks ProjectorFeatures.open). */
    public interface Pages {
        Page page(FeatureId id, MenuController menu);
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
    private final Map<String, Page> popups = new HashMap<>();            // wide tile -> radio popup, section tile -> popup
    private final Set<String> tilePopups = new HashSet<>();               // popups of tilePopup
    private final Map<FeatureId, View> rows = new LinkedHashMap<>();        // feature (picture.pq.<item>) -> row
    private final Map<View, FeatureId> featureOf = new HashMap<>();
    private final Map<FeatureId, List<CheckRow>> radios = new HashMap<>();  // choice feature -> its popup radios
    private final Map<FeatureId, List<CheckRow>> inline = new HashMap<>();  // inline radio choice -> its rows
    private final Map<String, TextRow> notes = new HashMap<>();          // feature or section -> its note line
    private final Map<HeaderRow, List<FeatureId>> headers = new LinkedHashMap<>();
    private final Map<HeaderRow, List<FeatureId>> lineAbove = new HashMap<>();   // a line header -> the page's rows above it
    private final Map<View, View> tips = new HashMap<>();                // row -> hint picture
    private final Map<Page, View> pageTips = new HashMap<>();
    private final List<QpdTip> setupTips = new ArrayList<>();               // projection pictures: redrawn by apply
    private final Map<FeatureId, List<String>> shownOptions = new HashMap<>();   // choice -> the tokens its row shows
    private final Map<FeatureId, Long> steppedAt = new HashMap<>();
    private List<CheckRow> gameRadios = new ArrayList<>();
    private Page root;
    private QpdCard card;
    private QpdSliderRow lamp;
    /** The card's choice slider tiles (the iris under the brightness slider), by feature. */
    private final Map<FeatureId, QpdSliderRow> choiceSliders = new LinkedHashMap<>();
    /** The light level rows of section pages (the stock image page shows it on top): the card slider's twins. */
    private final List<SliderRow> pageLamps = new ArrayList<>();
    private QpdMirror mirror;
    private QpdPanel panel;
    private OutputPages outputs;
    private MenuState state;
    private String lastAllCat;
    private int refreshGen;
    private boolean eyeOffShown;
    /** The theme the theme button shows. */
    private ThemeMode shownTheme;
    /** The 3D mode the tile shows (its motion plays again when a read-back changes it). */
    private ThreeDMode shown3d;
    private Boolean lastBoost;
    private final PressAgain boostAsk = new PressAgain(BOOST_PRESS_WINDOW_MS);
    /** LEFT at the lowest brightness asks while its hint is on screen (the note's slide in and hold). */
    private final SliderOff sliderOff = new SliderOff(Theme.NOTIFY_SLIDE_MS + Theme.NOTIFY_HOLD_MS);
    /** apply() is re-rendering the open page: its shown hook must not read again (that read would apply again). */
    private boolean applying;
    /** A popup opened on its checked row and no key since: a read-back moves the focus to the checked row. */
    private boolean popupUntouched;
    /** The section last told to a ProjectorFeatures.Live adapter, and the generation of its live callback. */
    private String liveSection;
    private int liveGen;
    private final SignalWatch signal;
    // the user's quick menu (pencil editor, 2026-11-18): catalogue, layout D as the default, the stored one
    private final QuickStore quickStore;
    private QuickCatalog quickCatalog;
    private QuickLayout quickDefaults, quickStored, quickShown;
    private QuickEditMode edit;
    /** Tiles by id and look ("id|kind"), so a look change in the editor keeps each look's view. */
    private final Map<String, QpdTile> tileViews = new HashMap<>();
    /** Number sliders the user put on the card (the volume, a picture slider), by feature. */
    private final Map<FeatureId, QpdSliderRow> numberSliders = new LinkedHashMap<>();
    /** The panel of an item the user put on the card in a tile look (its row in a page of its own), by feature. */
    private final Map<FeatureId, Page> quickPages = new HashMap<>();
    private QpdTile themeTile, eyeTile;
    private String quickJson;

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
        this.signal = new SignalWatch(dev, this::ours, () -> state, this::refresh);
        this.quickStore = new SecureQuickStore(app);
        this.devicePages = devicePages;
        this.host = host != null ? host : OverlayPanelHost.get(app);
        this.profile = profile(app);
        this.spec = MenuSpec.of(profile);
        for (FeatureId missing : missingInAdapter()) Log.e(TAG, "adapter lacks " + missing + ": its rows are not shown");
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
        for (String sec : spec.popups.keySet()) tilePopup(sec);
    }

    private static Set<FeatureId> set(FeatureId... s) { return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(s))); }

    private static List<Extension> extensions(String section) {
        List<Extension> l = EXTENSIONS.get(section);
        return l == null ? Collections.emptyList() : l;
    }

    /** Features of the profile the adapter does not implement (shown nowhere). */
    private List<FeatureId> missingInAdapter() {
        List<FeatureId> out = new ArrayList<>();
        for (FeatureId f : profile.features()) {
            if (!MenuSpec.module(f) && !dev.implemented().contains(f)) out.add(f);
        }
        return out;
    }

    private boolean has(FeatureId f) {
        if (MenuSpec.EVERY_MODEL.contains(f)) return ModuleScreens.available(app, f);
        if (MenuSpec.module(f)) return profile.has(f) && ModuleScreens.available(app, f);
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
        List<FeatureId> above = new ArrayList<>();
        for (MenuSpec.Group g : spec.sections.get(sec)) {
            List<FeatureId> present = new ArrayList<>();
            for (FeatureId f : g.rows) if (has(f)) present.add(f);
            if (present.isEmpty()) continue;
            if (g.header != null) {
                HeaderRow hr = p.add(new HeaderRow(app, headerText(g.header)));
                headers.put(hr, present);
                if (MenuSpec.LINE.equals(g.header)) lineAbove.put(hr, new ArrayList<>(above));
            }
            for (FeatureId f : present) {
                if (FeatureId.LAMP == f && !MenuSpec.LAMP.equals(sec)) addLampRow(p);
                else addRow(p, f);
            }
            above.addAll(present);
        }
        String foot = dev.footer(sec);
        if (foot != null) p.add(new TextRow(app, named(foot)));
        p.setOnShown(() -> onPageShown(sec));
        return p;
    }

    private void onPageShown(String sec) {
        refresh();
        live(sec);
        watchSignal();
        for (Extension x : extensions(sec)) {
            try {
                x.onPageShown();
            } catch (Throwable t) {
                Log.e(TAG, "extension onPageShown " + sec, t);
            }
        }
    }

    /**
     * A page of the menu's own rows (MenuSpec.pages, {@link FeatureKind#PAGE}): built like a section page from the
     * profile (a row of it may be a page again), its values read again each time it shows; the device adapter sets
     * and refuses its rows as any other row.
     */
    private Page buildPage(FeatureId page) {
        Page p = new Page(s(MenuLabels.title(page)));
        for (MenuSpec.Group g : spec.pages.get(page)) {
            List<FeatureId> present = new ArrayList<>();
            for (FeatureId f : g.rows) if (has(f)) present.add(f);
            if (present.isEmpty()) continue;
            if (g.header != null) headers.put(p.add(new HeaderRow(app, headerText(g.header))), present);
            for (FeatureId f : present) addRow(p, f);
        }
        p.setOnShown(() -> { refresh(); watchSignal(); });
        return p;
    }

    /** A group header's text: its label, nothing for a line (the row frame draws the line), else the id. */
    private String headerText(String id) {
        if (MenuSpec.LINE.equals(id)) return "";
        int h = MenuLabels.header(id);
        return h != 0 ? s(h) : id.toUpperCase();
    }

    /** OK on a page row: the stock refusal of the page (a note, nothing opens), else the page above this one. */
    private void openPage(FeatureId f, Page page) {
        Note n = dev.blocked(f, null, state);
        if (n != null) { note(n, f); return; }
        page.setInitialFocus(-1);
        push(page);
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
        tilePopup(sec);
        Page shown = panel != null && panel.isShowing() ? panel.currentPage() : null;
        if (shown != null && (shown == p || shown == popups.get(sec))) panel.refreshPage();
    }

    /** A CHOICE whose options are exactly off, on (Z9X eye protection) is one switch, as stock. */
    private boolean switchChoice(FeatureId f) {
        return f.kind == FeatureKind.CHOICE && profile.options(f).equals(Arrays.asList("off", "on"));
    }

    private void addRow(Page p, FeatureId f) {
        if (MenuSpec.onOutputPage(f, profile)) return;     // drawn at the bottom of the sound output page
        if (FeatureId.BOOST == f) {                        // Boost is the slider's top step; here only its hint
            p.add(new TextRow(app, s(MenuLabels.boostSummary(profile.boostSummary()))));
            return;
        }
        CharSequence title = s(MenuLabels.title(f));
        View row = null;
        switch (f.kind) {
            case TOGGLE:
                row = p.add(new ToggleRow(app, title, (r, wanted) -> safe("toggle " + f, () -> onToggle(f, r, wanted))));
                break;
            case CHOICE: {
                if (FeatureId.SOUND_OUTPUT == f) {                 // its own page (owner variant C, OutputPages)
                    if (outputs == null) outputs = outputPages();
                    row = p.add(new NavRow(app, title, () -> safe("output page", () -> outputs.open(state))));
                    break;
                }
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
                cr.setCommitDelay(FeatureId.PICTURE_MODE == f ? PICTURE_MODE_COMMIT_MS : CHOICE_COMMIT_MS);
                if (FeatureId.PICTURE_MODE == f) cr.setOnOpen(() -> safe("picture mode list", this::openModeList));
                row = p.add(cr);
                TextRow note = p.add(new TextRow(app, ""));
                note.setVisibility(View.GONE);
                notes.put(f.id, note);
                featureOf.put(note, f);
                break;
            }
            case SLIDER:
                if (FeatureId.PICTURE_PQ == f) {
                    int mn = profile.number(f, "min", 0), mx = profile.number(f, "max", 100);
                    for (String item : profile.items(f)) {
                        FeatureId id = FeatureId.pq(item);
                        SliderRow sr = p.add(new SliderRow(app, s(MenuLabels.pq(item)), mn, mx, slide(id)));
                        rows.put(id, sr);
                        featureOf.put(sr, id);
                    }
                    return;
                }
                if (FeatureId.LAMP == f) {                 // the bottom slider of the card; here only its hints
                    p.add(new TextRow(app, IconText.keys(app, s(MenuLabels.summary(f)))));
                    return;
                }
                int lo = profile.number(f, "min", 0);
                SliderRow sr = new SliderRow(app, title, lo, profile.number(f, "max", 100), slide(f));
                if (lo < 0) sr.setFormatter(v -> v > 0 ? "+" + v : Integer.toString(v));   // a two-way slider, 0 in the middle
                row = p.add(sr);
                break;
            case ACTION:
                row = p.add(new NavRow(app, title, () -> safe("action " + f, () -> action(f))).setChevron(false));
                break;
            case SCREEN:
                row = p.add(new NavRow(app, title, () -> safe("screen " + f, () -> screen(f))));
                break;
            case PAGE: {
                Page sub = buildPage(f);
                if (sub.rows().isEmpty()) return;                // no row of it the adapter implements: no row
                row = p.add(new NavRow(app, title, () -> safe("page " + f, () -> openPage(f, sub))));
                break;
            }
            case INFO:
                row = p.add(new NavRow(app, title).setChevron(false));
                break;
            default:
                return;
        }
        rows.put(f, row);
        featureOf.put(row, f);
        int sum = MenuLabels.summary(f);
        if (sum != 0) featureOf.put(p.add(new TextRow(app, s(sum))), f);
        View tip = tip(f);
        if (tip != null) {
            tips.put(row, tip);
            pageTips.put(p, tip);
        }
    }

    /** The sound output page and its format page: the menu's choose path, refresh, pages and labels. */
    private OutputPages outputPages() {
        return new OutputPages(app, profile, s(MenuLabels.section(MenuSpec.SOUND)), new OutputPages.Host() {
            @Override public void choose(FeatureId f, String token) { safe("output " + token, () -> MenuController.this.choose(f, token)); }
            @Override public void refresh() { MenuController.this.refresh(); }
            @Override public void push(Page p) { MenuController.this.push(p); }
            @Override public void back(Page from) { if (panel != null && panel.currentPage() == from) panel.pop(); }
            @Override public boolean onTop(Page p) { return panel != null && panel.isShowing() && panel.currentPage() == p; }
            @Override public boolean formatShown(MenuState st) {
                return has(FeatureId.SOUND_FORMAT) && st != null && st.get(FeatureId.SOUND_FORMAT) != null
                        && !st.hidden(FeatureId.SOUND_FORMAT);
            }
            @Override public CharSequence label(FeatureId f, String token) { return optionLabel(f, token); }
            @Override public void failed() { host.toast(R.string.lumen_m_toast_failed); }
        });
    }

    private SliderRow.OnSlide slide(FeatureId f) {
        return new SliderRow.OnSlide() {
            @Override public void onChanging(SliderRow row, int value) { touch(row); }
            @Override public void onCommit(SliderRow row, int value) {
                safe("slider " + f, () -> {
                    Note n = dev.blocked(f, Integer.toString(value), state);
                    if (n != null) { row.setTag(null); note(n, f); refresh(); return; }   // the device value back at once
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
    private View tip(FeatureId f) {
        String h = profile.hint(f);
        if (h == null) return null;
        HintPicture kind = HintPicture.of(h);
        if (kind == null) {
            Log.w(TAG, "hint picture " + h + " of " + f + " is not drawable yet: no picture");
            return null;
        }
        if (kind != HintPicture.PROJECTION) return new QpdTip(app, kind, s(MenuLabels.tipText(h)));
        QpdTip t = new QpdTip(app, kind, null);
        showSetup(t, state);
        setupTips.add(t);
        return t;
    }

    /** The projection picture of the setup read back now: how the projector is placed, then the viewer's side. */
    private void showSetup(QpdTip t, MenuState st) {
        ProjectionSetup s = ProjectionSetup.of(st);
        t.show(s, s == null ? null : Texts.sentences(s(MenuLabels.tipMount(s.mount)), s(MenuLabels.tipSide(s.side()))));
    }

    private CharSequence optionLabel(FeatureId f, String token) {
        String rt = state == null ? null : state.label(f, token);
        if (rt != null) return rt;
        String as = state == null ? null : state.shownAs(f, token);
        int r = as == null ? 0 : MenuLabels.option(f, as);
        if (r == 0) r = MenuLabels.option(f, token);
        int fmt = r == 0 && token.matches("[0-9]{1,6}") ? MenuLabels.optionFormat(f.id) : 0;
        if (fmt != 0) return app.getString(fmt, Integer.parseInt(token));
        if (r == 0) Log.w(TAG, "no label for " + f + " option " + token);
        return r == 0 ? token : s(r);
    }

    private CharSequence[] labels(FeatureId f, List<String> opts) {
        CharSequence[] out = new CharSequence[opts.size()];
        for (int i = 0; i < out.length; i++) out[i] = optionLabel(f, opts.get(i));
        return out;
    }

    /** The wide tiles of picture, sound and game open a radio list (layout D); mirroring has its receiver popup. */
    private void buildPopups() {
        radioPopup(MenuSpec.PICTURE, FeatureId.PICTURE_MODE, R.string.lumen_m_panel_picture_mode, R.string.lumen_qpd_picture_more);
        radioPopup(MenuSpec.SOUND, FeatureId.SOUND_MODE, R.string.lumen_m_panel_sound_mode, R.string.lumen_qpd_sound_more);
        if (pages.containsKey(MenuSpec.GAME)) {
            Page g = new Page(s(MenuLabels.section(MenuSpec.GAME)));
            g.setOnShown(() -> onPageShown(MenuSpec.GAME));
            popups.put(MenuSpec.GAME, g);
            if (has(FeatureId.GAME_MODE) && !switchChoice(FeatureId.GAME_MODE)) {
                for (String o : profile.options(FeatureId.GAME_MODE)) {
                    gameRadios.add(new CheckRow(app, optionLabel(FeatureId.GAME_MODE, o), false,
                            () -> safe("game mode item", () -> choose(FeatureId.GAME_MODE, o))));
                }
            }
            refreshGamePopup();
        }
        if (has(FeatureId.PICTURE_3D)) threeDPopup();
        if (has(FeatureId.MIRRORING) && QpdMirror.installed(app)) {
            mirror = new QpdMirror(app, s(R.string.lumen_qpd_mirroring), new QpdMirror.Host() {
                @Override public void closeThen(Runnable r) { MenuController.this.closeThen(r); }
                @Override public boolean setupDone(String what) { return MenuController.this.setupDone(what); }
            });
        }
    }

    /**
     * The 3D tile's popup: the model's stock 3D modes as radios, the one read back marked. A press marks it at once
     * (the vendor blacks the picture for a moment before the read-back), the read-back then decides.
     */
    private void threeDPopup() {
        FeatureId f = FeatureId.PICTURE_3D;
        Page p = new Page(s(MenuLabels.title(f)));
        p.setOnShown(this::refresh);
        List<CheckRow> list = new ArrayList<>();
        for (String o : profile.options(f)) {
            CheckRow r = p.add(new CheckRow(app, optionLabel(f, o), false, null));
            r.setOnClick(() -> safe("3d " + o, () -> {
                if (state != null && o.equals(state.get(f))) return;
                for (CheckRow x : list) x.setChecked(x == r);
                choose(f, o);
            }));
            list.add(r);
        }
        radios.put(f, list);
        popups.put(f.id, p);
    }

    private void radioPopup(String sec, FeatureId f, int titleRes, int moreRes) {
        if (!has(f) || !pages.containsKey(sec)) return;
        Page p = new Page(s(titleRes));
        p.setOnShown(() -> { refresh(); live(sec); watchSignal(); });
        popups.put(sec, p);
        rebuildRadios(sec, f, moreRes, profile.options(f));
    }

    private void rebuildRadios(String sec, FeatureId f, int moreRes, List<String> opts) {
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
        View mode = rows.get(FeatureId.GAME_MODE);
        for (View v : full.rows()) {
            if (v == mode && !gameRadios.isEmpty()) {
                for (CheckRow r : gameRadios) g.add(r);
            } else if (v != notes.get(FeatureId.GAME_MODE.id) || gameRadios.isEmpty()) {
                g.add(v);
            }
        }
        g.setInitialFocus(f);
    }

    /**
     * The popup of a section tile whose section has rows with a card tile of their own (MenuSpec.popups: keystone
     * without Auto keystone now and zoom): the full page's rows minus those, the same views. All settings keeps them.
     */
    private void tilePopup(String sec) {
        Page full = pages.get(sec), p = popups.get(sec);
        List<FeatureId> keep = spec.popupRowsOf(sec);
        if (full == null || keep.isEmpty() || keep.equals(spec.rowsOf(sec)) || (p != null && !tilePopups.contains(sec))) return;
        if (p == null) {
            p = new Page(full.getTitle());
            p.setOnShown(() -> onPageShown(sec));
            popups.put(sec, p);
            tilePopups.add(sec);
        }
        p.clear();
        boolean rowAbove = false;
        for (View v : full.rows()) {
            FeatureId f = featureOf.get(v);
            if (f != null && !keep.contains(f.parent != null ? f.parent : f)) continue;
            if (v instanceof HeaderRow && Collections.disjoint(headers.get(v), keep)) continue;
            if (lineAbove.containsKey(v) && !rowAbove) continue;                // a line never opens the popup
            if (f != null) rowAbove = true;
            p.add(v);
        }
    }

    private Page buildRoot() {
        Page p = new Page(s(R.string.lumen_m_panel_title));
        card = new QpdCard(app);
        for (MenuSpec.Tile t : spec.sliders) {                 // the brightness slider, the iris under it
            if (!has(t.feature)) continue;
            if (FeatureId.LAMP == t.feature) {
                lamp = new QpdSliderRow(app, s(MenuLabels.title(FeatureId.LAMP)), lampMin(), lampMax(), lampSlide(),
                        R.drawable.lumen_qpd_ic_sun);
                lamp.setBoostLevel(spec.boostLevel());
                wireLamp(lamp);
            } else {
                choiceSlider(t.feature);
            }
        }
        loadQuick();
        p.setOnShown(() -> { refresh(); live(null); });
        return p;
    }

    // ================================================================== the user's quick menu (pencil)

    /**
     * The quick menu to show: the stored one (Settings.Secure lumen_quick_menu of the current user) repaired for this
     * projector now, or layout D when nothing is stored (QuickDefaults: the card MenuSpec gives, the pencil beside
     * the gear). Read again at every open, so another user's or a reset menu shows at once.
     */
    private void loadQuick() {
        quickCatalog = QuickCatalog.of(spec, this::tilePresent);
        quickDefaults = QuickDefaults.of(spec, this::tilePresent);
        quickJson = quickStore.load();
        quickStored = QuickLayoutJson.parse(quickJson);
        quickShown = QuickLayoutJson.reconcile(quickStored, quickCatalog, quickDefaults);
        showQuick(quickShown);
    }

    /** Draws {@code l} on the card: one view per item in its look (a tile per look, each slider once). */
    private void showQuick(QuickLayout l) {
        Map<String, View> views = new HashMap<>();
        boolean list = l.density == Density.LIST, named = list;
        for (Placement pl : l.items) {                        // a slider the user added: every slider says its name
            if (pl.as == Presentation.SLIDER && spec.sliders.stream().noneMatch(t -> t.id.equals(pl.id))) named = true;
        }
        for (Placement pl : l.items) {
            View v = cellView(pl, list, named);
            if (v != null) views.put(pl.id, v);
        }
        card.setContent(QuickPacker.pack(l), views);
        if (state != null) {
            applyTiles(state);
            applyChoiceSliders(state);
            applyNumberSliders(state);
            applyLamp(state);
        }
        if (list) for (QpdTile t : card.tiles()) t.setChevron(t.kind == QpdTile.LIST && opensPanel(t.key));
        applyQuickValues(state);
        showTheme();
    }

    /** A list row that opens a panel (a chevron at its end): a section, a mode, All settings, not an action. */
    private boolean opensPanel(String key) {
        if (MenuSpec.GENERAL.equals(key)) return true;
        FeatureId f = FeatureId.of(key);
        if (f != null && f.kind == FeatureKind.ACTION) return false;
        return qpdHost.hasPopup(key);
    }

    private View cellView(Placement pl, boolean list, boolean named) {
        if (pl.as == Presentation.SLIDER) {
            QpdSliderRow r = sliderOf(pl.id);
            if (r != null) r.setNamed(named || !spec.hasTile(pl.id));     // layout D's own sliders show no name
            return r;
        }
        boolean pencil = QuickCatalog.EDIT.equals(pl.id);
        int kind = pl.as == Presentation.ROUND ? QpdTile.ROUND : pl.as == Presentation.FOOTER ? (pencil ? QpdTile.ROUND : QpdTile.LIST)
                : list ? QpdTile.LIST : pl.as == Presentation.WIDE ? QpdTile.WIDE : QpdTile.SMALL;
        String k = pl.id + "|" + kind;
        QpdTile t = tileViews.get(k);
        if (t == null) {
            t = tile(pl.id, kind);
            tileViews.put(k, t);
        }
        return t;
    }

    /** The slider view of {@code id}: the light level, a choice slider (the iris), else a number slider made once. */
    private QpdSliderRow sliderOf(String id) {
        FeatureId f = FeatureId.of(id);
        if (f == null) return null;
        if (f == FeatureId.LAMP) return lamp;
        QpdSliderRow r = choiceSliders.get(f);
        if (r != null) return r;
        if (MenuSpec.isSliderTile(id)) return has(f) ? choiceSlider(f) : null;
        r = numberSliders.get(f);
        if (r == null && f.kind == FeatureKind.SLIDER) {
            int[] range = quickRange(f);
            r = new QpdSliderRow(app, quickTitle(id), range[0], range[1], slide(f), quickIcon(id));
            numberSliders.put(f, r);
            featureOf.put(r, f);
        }
        return r;
    }

    /** The range of a number slider the user added: the volume's stream steps, a picture slider's profile range. */
    private int[] quickRange(FeatureId f) {
        if (MenuSpec.EVERY_MODEL.contains(f)) return new int[]{0, ModuleScreens.max(app, f)};
        FeatureId p = f.parent != null ? f.parent : f;
        return new int[]{profile.number(p, "min", 0), profile.number(p, "max", 100)};
    }

    /** The name of a quick menu item (the pencil, a picture slider, a tile, a row, a section). */
    private CharSequence quickTitle(String id) {
        if (QuickCatalog.EDIT.equals(id)) return s(R.string.lumen_qe_edit);
        FeatureId f = FeatureId.of(id);
        if (f != null && f.parent == FeatureId.PICTURE_PQ) {             // mock: "Яркость картинки", not the light's "Яркость"
            return s("brightness".equals(f.item) ? R.string.lumen_qe_pq_brightness : MenuLabels.pq(f.item));
        }
        int r = MenuLabels.tile(id);
        if (r == 0) r = MenuLabels.title(id);
        if (r == 0) r = MenuLabels.section(id);
        return s(r);
    }

    private int quickIcon(String id) { return MenuLabels.icon(id); }

    /** The panel of an item the user put on the card as a tile (a slider, a mode, the output): its row on a page. */
    private Page quickPage(String key) {
        FeatureId f = FeatureId.of(key);
        if (f == null) return null;
        if (f == FeatureId.LAMP) return pages.get(MenuSpec.LAMP);
        Page p = quickPages.get(f);
        if (p != null) return p;
        View row = f == FeatureId.SOUND_VOLUME ? volumeRow() : rows.get(f);
        if (row == null) return null;
        p = new Page(quickTitle(key));
        p.add(row);
        p.setOnShown(this::refresh);
        quickPages.put(f, p);
        return p;
    }

    /** The volume row of its panel: the music stream's steps, the module writes it (ModuleScreens). */
    private View volumeRow() {
        FeatureId f = FeatureId.SOUND_VOLUME;
        View v = rows.get(f);
        if (v != null) return v;
        SliderRow r = new SliderRow(app, quickTitle(f.id), 0, ModuleScreens.max(app, f), slide(f));
        rows.put(f, r);
        featureOf.put(r, f);
        return r;
    }

    private String quickKeyOf(Page p) {
        for (Map.Entry<FeatureId, Page> e : quickPages.entrySet()) if (e.getValue() == p) return e.getKey().id;
        return null;
    }

    /** True for an item from the editor's catalogue that is no tile of layout D (or a slider tile in a tile look). */
    private boolean quickItem(String id) {
        FeatureId f = FeatureId.of(id);
        return f != null && (!spec.hasTile(id) || card.tile(id) != null && spec.sliders.stream().anyMatch(t -> t.id.equals(id)));
    }

    /** OK on such an item: an action runs, anything else opens its panel right of the card. */
    private boolean openQuick(String id) {
        if (!quickItem(id)) return false;
        FeatureId f = FeatureId.of(id);
        if (f.kind == FeatureKind.ACTION) { action(f); return true; }
        Page p = quickPage(id);
        if (p == null) return false;
        card.remember(id);
        while (panel.depth() > 1 && panel.pop()) { /* one panel at a time */ }
        card.settle();
        p.setInitialFocus(0);
        popupUntouched = false;
        panel.push(p);
        return true;
    }

    /** The value line of items the user put on the card in a tile or row look (a slider, a mode, eye protection). */
    private void applyQuickValues(MenuState st) {
        for (QpdTile t : card.tiles()) {
            FeatureId f = FeatureId.of(t.key);
            if (f == null || TILE_VALUES.contains(t.key)) continue;
            String v = st == null ? null : st.get(f);
            CharSequence shown = null;
            if (f == FeatureId.LAMP) shown = boostOn() ? s(R.string.lumen_m_panel_boost) : v;
            else if (v != null && f.kind == FeatureKind.SLIDER) shown = v;
            else if (v != null && f.kind == FeatureKind.CHOICE) shown = optionLabel(f, v);
            t.setValue(shown);
        }
    }

    /** Tiles whose value line applyTiles writes (or that have none). */
    private static final Set<String> TILE_VALUES = new HashSet<>(Arrays.asList(MenuSpec.PICTURE, MenuSpec.SOUND, MenuSpec.GAME,
            MenuSpec.PROJECTION, FeatureId.INPUT.id, FeatureId.KEYSTONE_AK_NOW.id, FeatureId.PICTURE_3D.id));

    /** The number sliders the user added: the value read (the volume from the module), greyed while unread. */
    private void applyNumberSliders(MenuState st) {
        for (Map.Entry<FeatureId, QpdSliderRow> e : numberSliders.entrySet()) {
            Integer n = st == null ? null : st.number(e.getKey());
            if (n == null && st != null) n = parseInt(st.get(e.getKey()));      // a module value (the volume) is text
            QpdSliderRow r = e.getValue();
            if (!sliderBusy(r)) r.setValue(n);
            r.setRowEnabled(n != null);
            r.invalidate();
        }
    }

    private static Integer parseInt(String v) {
        if (v == null) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The pencil: the editor beside the card (the card is its live preview). */
    private void openEditor() {
        if (panel == null || !panel.isShowing() || panel.isBar()) return;
        while (panel.depth() > 1 && panel.pop()) { /* the editor takes the panel's place */ }
        loadQuick();
        edit = new QuickEditMode(app, editHost, quickCatalog, quickDefaults, quickShown, quickStored);
        edit.start();
    }

    private final QuickEditMode.Host editHost = new QuickEditMode.Host() {
        @Override public void preview(QuickLayout l) { showQuick(l); }
        @Override public QpdCard card() { return card; }
        @Override public QpdPanel panel() { return panel; }
        @Override public CharSequence title(String id) { return quickTitle(id); }
        @Override public int icon(String id) { return quickIcon(id); }
        @Override public CharSequence groupTitle(String g) {
            int r = MenuLabels.category(g);
            return s(r != 0 ? r : MenuLabels.section(g));
        }
        @Override public void toast(int res) { host.toast(res); }
        @Override public void done(boolean store, QuickLayout value) {
            edit = null;
            if (store && !(value == null ? quickStore.clear() : quickStore.save(QuickLayoutJson.write(value)))) {
                host.toast(R.string.lumen_m_toast_failed);
            }
            loadQuick();
            card.remember(QuickCatalog.EDIT);
            if (panel != null && panel.isShowing()) card.focusRemembered();
        }
    };

    /** A new menu window: the theme's colours, and the stored quick menu again when it changed (another user, a reset). */
    private void freshOpen() {
        followTheme();
        String now = quickStore.load();
        if (edit == null && !java.util.Objects.equals(now, quickJson)) loadQuick();
    }

    /** The menu's colours follow the theme button (the shared lumen-ui theme): light or dark. */
    private void followTheme() {
        if (QpdStyle.theme(!LumenTheme.dark(app))) {
            card.retheme();
            if (panel != null) panel.retheme();
        }
    }

    /**
     * A feature tile exists when the feature is usable, screen mirroring only with its receiver installed, a section
     * tile when its section has rows; the theme button and the gear always.
     */
    private boolean tilePresent(String id) {
        if (MenuSpec.GENERAL.equals(id) || MenuSpec.THEME.equals(id) || QuickCatalog.EDIT.equals(id)) return true;
        if (FeatureId.MIRRORING.id.equals(id)) return mirror != null;
        FeatureId f = FeatureId.of(id);
        if (f != null && f.parent != null) return has(f.parent);         // a picture slider: the picture.pq row
        return f != null ? has(f) : spec.hasSection(id);
    }

    private QpdTile tile(String id, int kind) {
        QpdTile t = new QpdTile(app, id, kind, quickIcon(id), quickTitle(id), () -> safe("tile " + id, () -> onTile(id)));
        if (FeatureId.KEYSTONE_AK_NOW.id.equals(id)) t.setValue(s(R.string.lumen_qpd_autocorrect_value));
        if (FeatureId.EYE.id.equals(id) && eyeHasPopup()) t.setOnLongOk(() -> safe("eye popup", () -> openPopup(id)));
        return t;
    }

    /** The theme button: the icon of the mode in force (sun, moon, half disc) and its name for the screen reader. */
    private void showTheme() {
        QpdTile t = card.tile(MenuSpec.THEME);
        if (t == null) return;
        ThemeMode m = LumenTheme.mode(app);
        if (m != shownTheme || t != themeTile) {
            shownTheme = m;
            themeTile = t;
            t.setIcon(MenuLabels.themeIcon(m));
        }
        t.setTitle(s(R.string.lumen_qpd_theme) + ". " + s(MenuLabels.themeLabel(m)));
    }

    /** One press: Светлая, Тёмная, Авто, Светлая through the shared switch; Home and Setup follow at once. */
    private void cycleTheme() {
        ThemeMode next = LumenTheme.mode(app).next();
        if (!LumenTheme.setMode(app, next)) {
            host.toast(R.string.lumen_m_toast_failed);
            return;
        }
        showTheme();
        followTheme();
        QpdTile t = card.tile(MenuSpec.THEME);
        if (t != null) t.playIcon();
    }

    /**
     * A choice as a slider tile of the card (the iris): one step per option of the profile, in its stock order, the
     * option's name on the right. A step commits as a pick in the page row does (choose: the stock refusal before
     * anything is sent, the question, the read-back), and both show the one value read, so they never disagree.
     */
    private QpdSliderRow choiceSlider(FeatureId f) {
        int[] range = spec.sliderRange(f);
        List<String> opts = profile.options(f);
        QpdSliderRow r = new QpdSliderRow(app, s(MenuLabels.title(f)), range[0], range[1], new SliderRow.OnSlide() {
            @Override public void onChanging(SliderRow row, int value) { touch(row); }
            @Override public void onCommit(SliderRow row, int value) {
                safe(f.id, () -> {
                    String token = MenuSpec.sliderToken(opts, value);
                    if (token != null) choose(f, token);
                });
            }
        }, MenuLabels.icon(f.id));
        r.setLabels(v -> {
            String t = MenuSpec.sliderToken(opts, v);
            return t == null ? "" : optionLabel(f, t);
        });
        choiceSliders.put(f, r);
        featureOf.put(r, f);
        return r;
    }

    private int lampMin() { return profile.number(FeatureId.LAMP, "min", 1); }

    private int lampMax() { return profile.number(FeatureId.LAMP, "max", 10); }

    /** The light level row of a section page: the card slider's steps, Boost edge and light-off edge, one behaviour. */
    private void addLampRow(Page p) {
        SliderRow r = p.add(new SliderRow(app, s(MenuLabels.title(FeatureId.LAMP)), lampMin(), lampMax(), lampSlide()));
        wireLamp(r);
        pageLamps.add(r);
        featureOf.put(r, FeatureId.LAMP);
    }

    /** A light level slider sends 1..max, never under Boost (the Boost step is its own two-press step). */
    private SliderRow.OnSlide lampSlide() {
        return new SliderRow.OnSlide() {
            @Override public void onChanging(SliderRow row, int value) { touch(row); }
            @Override public void onCommit(SliderRow row, int value) {
                safe("lamp", () -> {
                    if (boostOn() || value > lampMax() || value < lampMin()) return;
                    set(FeatureId.LAMP, Integer.toString(value));
                });
            }
        };
    }

    /** The Boost word past the top level, the light-off edge below the lowest, the Boost edge above the top. */
    private void wireLamp(SliderRow r) {
        final int bl = spec.boostLevel();
        r.setFormatter(v -> bl > 0 && v >= bl ? s(R.string.lumen_m_panel_boost) : Integer.toString(v));
        r.setEdgeListener((row, dir) -> {
            // LEFT at the lowest level asks like Boost, then goes the "Turn off screen" row's way (menu closes first)
            switch (sliderOff.edge(has(FeatureId.LAMP_OFF), row.isKnown() ? row.getValue() : null, row.getMin(), dir, 0,
                    SystemClock.uptimeMillis())) {
                case HINT: host.note(IconText.keys(app, s(R.string.lumen_m_lamp_off_press_again))); return true;
                case OFF: safe("lamp off", () -> action(FeatureId.LAMP_OFF)); return true;
                default: break;
            }
            // Boost only where this projector reads it: never a dead Boost step
            if (dir > 0 && bl > 0 && has(FeatureId.BOOST) && !boostOn() && state != null && state.on(FeatureId.BOOST) != null) {
                safe("boost", () -> boostPress(1));
            }
            return true;
        });
    }

    /** True for the card's light slider and its twins on section pages. */
    private boolean isLamp(View v) { return v != null && (v == lamp || pageLamps.contains(v)); }

    private boolean boostOn() { return state != null && Boolean.TRUE.equals(state.on(FeatureId.BOOST)) && spec.boostLevel() > 0; }

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

    /**
     * The full page of a section, above its popup (BACK goes to the popup): what a remote key for a section's expert
     * settings opens (the RS30 PRO_SETTING key, stock image mode_setting). A section without its own full page opens
     * as {@link #show(String)}.
     */
    public void showPage(String section) {
        if (section == null || !spec.hasSection(section) || refusedPage(section)) return;
        Page full = pages.get(section);
        Page popup = popupPage(section);
        if (full == null || popup == null || popup == full || full.rows().isEmpty()) { show(section, null); return; }
        show(section, full.rows().get(0));
    }

    /** {@code section}'s popup with the focus on {@code focusRow} (a row of that page), or on its first row. */
    private void show(String section, View focusRow) {
        if (MenuSpec.GENERAL.equals(section)) { openAll(section, focusRow); return; }
        if (MenuSpec.LAMP.equals(section)) { showBar(); return; }
        if (FeatureId.MIRRORING.id.equals(section) && mirror == null) section = null;
        openSection(section, focusRow);
    }

    private Page popupPage(String key) {
        if (key == null) return null;
        if (FeatureId.INPUT.id.equals(key)) return devicePages == null ? null : devicePages.page(FeatureId.INPUT, this);
        if (FeatureId.MIRRORING.id.equals(key)) return mirror == null ? null : mirror.page;
        if (MenuSpec.LAMP.equals(key) || MenuSpec.GENERAL.equals(key)) return null;
        Page p = popups.get(key);
        return p != null ? p : pages.get(key);
    }

    private void openSection(String key, View focusRow) {
        List<Page> open = new ArrayList<>();
        Page target = popupPage(key);
        popupUntouched = target != null && focusRow == null;
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
        freshOpen();
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
        freshOpen();
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
        else if ("keystone_reset".equals(what) && has(FeatureId.KEYSTONE_RESTORE)) run(FeatureId.KEYSTONE_RESTORE);
        else if (fallback != null) fallback.run();
    }

    private void openAll(String category, View focusRow) {
        popupUntouched = false;
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
        freshOpen();
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
        if (edit != null) return;                             // the editor takes OK itself
        card.remember(id);
        if (MenuSpec.GENERAL.equals(id)) { openAll(null, null); return; }
        if (MenuSpec.THEME.equals(id)) { cycleTheme(); return; }
        if (QuickCatalog.EDIT.equals(id)) { openEditor(); return; }
        if (openQuick(id)) return;
        FeatureId f = FeatureId.known(id);
        if (f != null) {
            switch (f) {
                case KEYSTONE_AK_NOW: closeThen(() -> run(f)); return;
                case EYE: toggleEye(); return;
                case ZOOM:
                case ZOOM_DIGITAL:
                    if (!dev.open(f, () -> show(null, null))) Log.w(TAG, id + " cannot open now");   // BACK comes back here
                    return;
                case BLUETOOTH:
                    if (setupDone(id)) closeThen(() -> openSystemPage(SettingsPage.BLUETOOTH));
                    return;
                case NETWORK:
                    if (setupDone(id)) closeThen(() -> openSystemPage(SettingsPage.NETWORK));
                    return;
                case SIGNAL_PANEL:
                    screen(f);
                    return;
                case INPUT:
                    if (!setupDone(id)) return;
                    if (popupPage(id) == null) {                  // no input popup of the device: the Source key's step
                        closeThen(() -> { if (!dev.open(f, () -> show(null, null))) Log.w(TAG, "input cannot open now"); });
                        return;
                    }
                    break;
                default:
                    break;
            }
        }
        openPopup(id);
    }

    /** The tile's popup beside it (RIGHT on the last tile of a row, a tile's OK, eye protection's held OK). */
    private void openPopup(String id) {
        if (panel == null || !panel.isShowing()) return;
        if (spec.hasSection(id) && refusedPage(id)) return;
        Page p = popupPage(id);
        if (p == null) return;
        card.remember(id);
        if (p == popups.get(MenuSpec.GAME)) refreshGamePopup();
        while (panel.depth() > 1 && panel.pop()) { /* one popup at a time */ }
        card.settle();
        p.setInitialFocus(checkedIndex(p));                   // radio lists open on the current choice, others on row 1
        popupUntouched = true;
        panel.push(p);
    }

    /** Eye protection tile: OK switches it, the mode used last comes back on a model with modes. */
    private void toggleEye() {
        String to = QpdLayout.eyeToggle(state == null ? null : state.get(FeatureId.EYE), profile.options(FeatureId.EYE),
                prefs.getString(KEY_EYE_ON, null));
        if (to == null) {                                     // not read yet: never a guessed value
            if (readyOrNote()) refresh();
            return;
        }
        choose(FeatureId.EYE, to);
    }

    private boolean eyeHasPopup() { return QpdLayout.eyeHasPopup(profile.options(FeatureId.EYE)); }

    /** The stock refusal of a whole section page (the game page off HDMI, with Dolby Vision, in 3D). */
    private boolean refusedPage(String section) {
        Note n = dev.blockedPage(section, state);
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
        FeatureId f = FeatureId.PICTURE_MODE;
        String cur = state == null ? null : state.get(f);
        if (cur == null) { readyOrNote(); return; }
        Page p = new Page(s(MenuLabels.title(f)));
        int focus = -1;
        List<String> opts = listed(f, state);
        Set<String> greyed = SignalRule.greyed(profile, f, state);
        for (int i = 0; i < opts.size(); i++) {
            final String o = opts.get(i);
            if (o.equals(cur)) focus = i;
            p.add(new CheckRow(app, optionLabel(f, o), o.equals(cur), () -> safe("mode list item", () -> {
                if (panel != null) panel.pop();
                choose(f, o);
            }))).setRowEnabled(!greyed.contains(o));
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
        closeThen(panel, r);
    }

    /** Closes {@code p} (the menu, or the power menu in the same host) and runs {@code r} when its exit animation ended. */
    void closeThen(Panel p, Runnable r) {
        if (p != null) host.dismiss(p);
        Ui.main().postDelayed(() -> safe("after close", r), AFTER_CLOSE_MS);
    }

    /** True while this menu is the panel on screen. */
    public boolean isShowing() { return ours(); }

    /** Closes the menu if it is shown. */
    public void dismiss() { if (panel != null && panel.isShowing()) host.dismiss(panel); }

    /** The host the menu's windows live in. */
    public PanelHost host() { return host; }

    // the power menu's ways into this menu (PowerMenu): what it offers, a row's own action or screen, a device page
    boolean offers(FeatureId f) { return has(f); }

    void act(FeatureId f) {
        if (f.kind == FeatureKind.ACTION) action(f);
        else screen(f);
    }

    Page devicePage(FeatureId f) { return devicePages == null ? null : devicePages.page(f, this); }

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
        if (FeatureId.INPUT.id.equals(sec) || FeatureId.MIRRORING.id.equals(sec)) return () -> openSection(sec, row);
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
        if (mirror != null && p == mirror.page) return FeatureId.MIRRORING.id;
        if (devicePages != null && p == devicePages.page(FeatureId.INPUT, this)) return FeatureId.INPUT.id;
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

    /** The tile's Android settings screen through the shared lumen-system module (its chain of ways), else a note. */
    private void openSystemPage(SettingsPage page) {
        Ui.wakeFromDream(app);
        if (!SystemPages.open(app, page)) host.toast(R.string.lumen_m_toast_failed);
    }

    // ================================================================== keys

    /** Every key of the menu window, before the rows (QpdPanel.onKeyEvent). */
    private boolean onKey(KeyEvent ev) {
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN, first = down && ev.getRepeatCount() == 0;
        if (down) popupUntouched = false;                    // the user's own focus from now on
        View f = focusedView();
        boolean onLamp = isLamp(f);
        int towardsStart = onLamp && ((SliderRow) f).isRtl() ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        if (down) sliderOff.key(onLamp && k == towardsStart);    // any other key drops the light-off question
        int towardsEnd = towardsStart == KeyEvent.KEYCODE_DPAD_LEFT ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int sliderDir = !onLamp ? 0 : k == towardsEnd ? 1 : k == towardsStart ? -1 : 0;
        if (down) boostAsk.key(sliderDir);    // any other key drops the Boost question too
        if (edit != null && panel != null && panel.hasSide()) return edit.onKey(ev);   // the pencil editor's keys
        // BACK while an equalizer band is being adjusted only ends adjusting
        if (f instanceof EqBandsRow && ((EqBandsRow) f).isEditing() && (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE)) {
            if (ev.getAction() == KeyEvent.ACTION_UP && !ev.isCanceled()) ((EqBandsRow) f).stopEditing();
            return true;
        }
        boolean step = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (step && (f instanceof SliderRow || f instanceof ChoiceRow)) {
            FeatureId feat = onLamp ? FeatureId.LAMP : featureOf.get(f);
            Note n = feat == null ? null : IrisRule.blocked(profile, feat, state);
            if (n == null && feat != null) n = dev.blocked(feat, null, state);
            if (n != null) {                                  // stock: a locked row does not move, one note per press
                if (first) note(n, feat);
                return true;
            }
        }
        if (step && f instanceof ChoiceRow && down) {          // the option list the user steps through stays until commit
            FeatureId feat = featureOf.get(f);
            if (feat != null) steppedAt.put(feat, SystemClock.uptimeMillis());
        }
        // leaving Boost: at "Boost" a press towards the start arms, a second one within 3 s turns it off
        if (boostOn() && onLamp && k == towardsStart) {
            if (first) boostPress(-1);
            return true;
        }
        return false;
    }

    private View focusedView() {
        if (card != null) for (QpdSliderRow s : card.sliders()) if (s.isFocused()) return s;
        Page top = panel == null ? null : panel.currentPage();
        View v = focused(top);
        if (v != null) return v;
        for (View r : rows.values()) if (r.isFocused()) return r;
        return null;
    }

    /** Boost: two presses beyond the top level to enter, two below Boost to leave. dir +1 enter, -1 leave. */
    private void boostPress(int dir) {
        if (boostAsk.press(dir, SystemClock.uptimeMillis())) {
            String v = dir > 0 ? "on" : "off";
            Note n = dev.blocked(FeatureId.BOOST, v, state);
            if (n != null) { note(n, FeatureId.BOOST); return; }
            set(FeatureId.BOOST, v);
            return;
        }
        host.note(IconText.keys(app, s(dir > 0 ? R.string.lumen_m_panel_boost_press_again
                : R.string.lumen_m_panel_boost_leave_press_again)));
    }

    // ================================================================== user actions

    private void onToggle(FeatureId f, ToggleRow row, boolean wanted) {
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

    private void choose(FeatureId f, String token) {
        if (SKIP_SAME.contains(f) && state != null && token.equals(state.get(f))) return;
        if (SignalRule.greyed(profile, f, state).contains(token)) { refresh(); return; }
        if (SETUP_GATED.contains(f) && !setupDone(f.id)) { refresh(); return; }
        Note n = dev.blocked(f, token, state);
        if (n != null) {
            steppedAt.remove(f);                              // a refused step shows the device value at once
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

    private void action(FeatureId f) {
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

    private void screen(FeatureId f) {
        if (MenuSpec.MODULE_SCREENS.contains(f)) {
            closeThen(() -> { if (!ModuleScreens.open(app, f)) host.toast(R.string.lumen_m_toast_failed); });
            return;
        }
        if (SETUP_GATED.contains(f) && !setupDone(f.id)) return;
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

    private String lastSectionOf(FeatureId f) {
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

    private void set(FeatureId f, String value) {
        if (MenuSpec.MODULE_SETTINGS.contains(f) || MenuSpec.EVERY_MODEL.contains(f)) { setModule(f, value); return; }
        if (!readyOrNote()) return;
        dev.set(f, value, r -> after(f, value, r));
    }

    /** A module setting: the module stores it, the row shows it at once (no device call). */
    private void setModule(FeatureId f, String value) {
        if (!ModuleScreens.set(app, f, value)) host.toast(R.string.lumen_m_toast_failed);
        refresh();
    }

    private void setConfirmed(FeatureId f, String value) {
        if (MenuSpec.MODULE_SETTINGS.contains(f)) { setModule(f, value); return; }
        if (!readyOrNote()) return;
        dev.setConfirmed(f, value, r -> after(f, value, r));
    }

    private void run(FeatureId f) {
        if (!readyOrNote()) return;
        dev.run(f, r -> after(f, r));
    }

    private void after(FeatureId f, ProjectorFeatures.Result r) { after(f, null, r); }

    private void after(FeatureId f, String value, ProjectorFeatures.Result r) {
        if (r == null) { refresh(); return; }
        if (ChoiceStep.forget(r.kind)) steppedAt.remove(f);           // a step the device did not take: its value at once
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
    private void note(ProjectorFeatures.Note n, FeatureId f) { note(n, f, null); }

    private void note(ProjectorFeatures.Note n, FeatureId f, CharSequence fallbackTitle) {
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

    private CharSequence rowTitle(FeatureId f, CharSequence fallback) {
        View v = f == null ? null : rows.get(f);
        if (v instanceof Row) return ((Row) v).getTitle();
        if (f != null && f.parent == FeatureId.PICTURE_PQ) {
            return s(MenuLabels.pq(f.item));
        }
        if (f != null) return s(MenuLabels.title(f));
        return fallback;
    }

    /** Re-reads every value and updates the rows (main thread); an older read arriving late is dropped. */
    public void refresh() {
        if (applying) return;               // the shown hook of apply()'s own re-render: the values are fresh
        if (!dev.ready()) { apply(null); return; }
        final int gen = ++refreshGen;
        dev.read(st -> {
            if (gen == refreshGen) apply(ModuleScreens.merge(app, st));
        });
    }

    private void apply(MenuState st) {
        state = st;
        boolean ok = st != null;
        for (Map.Entry<FeatureId, View> e : rows.entrySet()) {
            FeatureId f = e.getKey();
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
                boolean opens = f.kind == FeatureKind.SCREEN || f.kind == FeatureKind.PAGE;
                if (f.kind == FeatureKind.INFO || opens) ((NavRow) v).setValue(val);
                if (opens) ((NavRow) v).setRowEnabled(st == null || !st.disabled(f));
            }
            // a row stock keeps closed now (the picture settings page in AI): dimmed like a stock disabled cell, its
            // value kept, still focusable so a press says why. A disabled row keeps its own dimming
            if (v instanceof Row && ((Row) v).isRowEnabled()) v.setAlpha(LockedRow.alpha(LockedRow.locked(dev, f, st)));
        }
        for (Map.Entry<FeatureId, List<CheckRow>> e : inline.entrySet()) {
            String val = ok ? st.get(e.getKey()) : null;
            List<String> opts = profile.options(e.getKey());
            Set<String> greyed = SignalRule.greyed(profile, e.getKey(), st);
            for (int i = 0; i < e.getValue().size(); i++) {
                CheckRow r = e.getValue().get(i);
                r.setChecked(val != null && opts.get(i).equals(val));
                r.setRowEnabled(val != null && !greyed.contains(opts.get(i)));
                if (ok) r.setVisibility(st.hidden(e.getKey()) ? View.GONE : View.VISIBLE);
            }
        }
        for (Map.Entry<String, TextRow> e : notes.entrySet()) {
            String t = ok ? st.note(e.getKey()) : null;
            e.getValue().setText(t);
            e.getValue().setVisibility(t == null ? View.GONE : View.VISIBLE);
        }
        for (Map.Entry<HeaderRow, List<FeatureId>> e : headers.entrySet()) {
            List<FeatureId> above = lineAbove.get(e.getKey());
            boolean shown = above == null ? spec.headerShown(e.getValue(), st) : spec.lineShown(above, e.getValue(), st);
            if (ok) e.getKey().setVisibility(shown ? View.VISIBLE : View.GONE);
        }
        View out = rows.get(FeatureId.SOUND_OUTPUT);
        if (outputs != null && out instanceof NavRow) {
            ((NavRow) out).setValue(ok ? outputs.summary(st) : null);
            outputs.apply(st);
        }
        applyRadios(st);
        applyLamp(st);
        applyChoiceSliders(st);
        applyNumberSliders(st);
        applyTiles(st);
        applyQuickValues(st);
        for (QpdTip t : setupTips) showSetup(t, st);
        if (panel != null && panel.isShowing()) {
            applying = true;
            try {
                // a radio list opened before its value was read lands on the checked row when it arrives
                panel.refreshPage(QpdLayout.popupFocus(popupUntouched && !panel.isAllMode(), checkedIndex(panel.currentPage()), -1));
            } finally {
                applying = false;
            }
        }
    }

    /** The options a choice lists now: the device's runtime subset, else the profile's. */
    private List<String> listed(FeatureId f, MenuState st) {
        return st == null || st.options(f) == null ? profile.options(f) : st.options(f);
    }

    /** Options with a stock support list follow the content kind while the menu is open (SignalWatch). */
    private void watchSignal() {
        for (FeatureId f : profile.features()) {
            if (profile.hasSupport(f)) { signal.start(); return; }
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
            FeatureId f = FeatureId.of(e.getKey());
            View v = f == null ? null : rows.get(f);
            if (v instanceof NavRow && f.kind == FeatureKind.INFO) ((NavRow) v).setValue(e.getValue());
        }
    }

    private void applyChoice(FeatureId f, ChoiceRow r, MenuState st, boolean usable) {
        List<String> want = new ArrayList<>(listed(f, st));
        want.removeAll(SignalRule.greyed(profile, f, st));
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

    private static boolean hasRuntimeLabels(FeatureId f, List<String> opts, MenuState st) {
        for (String o : opts) if (st.label(f, o) != null) return true;
        return false;
    }

    private void applyRadios(MenuState st) {
        boolean ok = st != null;
        String pm = ok ? st.get(FeatureId.PICTURE_MODE) : null;
        List<CheckRow> pr = radios.get(FeatureId.PICTURE_MODE);
        if (pr != null) {
            List<String> opts = listed(FeatureId.PICTURE_MODE, st);
            if (opts.size() != pr.size() || !opts.equals(currentRadioTokens(FeatureId.PICTURE_MODE))) {
                rebuildRadios(MenuSpec.PICTURE, FeatureId.PICTURE_MODE, R.string.lumen_qpd_picture_more, opts);
                radioTokens.put(FeatureId.PICTURE_MODE, opts);
                pr = radios.get(FeatureId.PICTURE_MODE);
                if (panel != null && panel.isShowing() && panel.currentPage() == popups.get(MenuSpec.PICTURE)) panel.refreshPage();
            }
            Set<String> greyed = SignalRule.greyed(profile, FeatureId.PICTURE_MODE, st);
            for (int i = 0; i < pr.size(); i++) {
                pr.get(i).setChecked(pm != null && opts.get(i).equals(pm));
                pr.get(i).setRowEnabled(!greyed.contains(opts.get(i)));
            }
        }
        List<CheckRow> sr = radios.get(FeatureId.SOUND_MODE);
        if (sr != null) {
            String sm = ok ? st.get(FeatureId.SOUND_MODE) : null;
            List<String> opts = profile.options(FeatureId.SOUND_MODE);
            for (int i = 0; i < sr.size() && i < opts.size(); i++) {
                sr.get(i).setTitle(optionLabel(FeatureId.SOUND_MODE, opts.get(i)));   // "AI · <sub-mode>" follows the app
                sr.get(i).setChecked(opts.get(i).equals(sm));
            }
        }
        List<CheckRow> td = radios.get(FeatureId.PICTURE_3D);
        if (td != null) {
            String now = ok ? st.get(FeatureId.PICTURE_3D) : null;
            List<String> opts = profile.options(FeatureId.PICTURE_3D);
            for (int i = 0; i < td.size() && i < opts.size(); i++) td.get(i).setChecked(opts.get(i).equals(now));
        }
        if (!gameRadios.isEmpty()) {
            String gm = ok ? st.get(FeatureId.GAME_MODE) : null;
            View mode = rows.get(FeatureId.GAME_MODE);
            List<String> opts = profile.options(FeatureId.GAME_MODE);
            for (int i = 0; i < gameRadios.size(); i++) {
                gameRadios.get(i).setChecked(opts.get(i).equals(gm));
                if (mode != null) gameRadios.get(i).setVisibility(mode.getVisibility());
            }
        }
    }

    /** A light level slider: at the Boost step while Boost is on, else the level (1..max), greyed while unknown. */
    private void applyLampRow(SliderRow r, MenuState st) {
        boolean ok = st != null, boost = boostOn();
        if (!sliderBusy(r)) {
            if (boost) r.setRange(lampMin(), spec.boostLevel()).setValue(spec.boostLevel());
            else r.setRange(lampMin(), lampMax()).setValue(ok ? st.number(FeatureId.LAMP) : null);
        }
        r.setRowEnabled(ok && (st.number(FeatureId.LAMP) != null || boost));
    }

    private final Map<FeatureId, List<String>> radioTokens = new HashMap<>();

    private List<String> currentRadioTokens(FeatureId f) {
        List<String> l = radioTokens.get(f);
        return l != null ? l : profile.options(f);
    }

    private void applyLamp(MenuState st) {
        for (SliderRow r : pageLamps) applyLampRow(r, st);
        if (lamp == null) return;
        boolean ok = st != null;
        applyLampRow(lamp, st);
        Boolean b = ok && has(FeatureId.BOOST) ? st.on(FeatureId.BOOST) : null;
        if (QpdLayout.boostNoticeDue(lastBoost, b)) host.note(s(R.string.lumen_m_panel_boost), s(R.string.lumen_qpd_boost_note));
        if (b != null) lastBoost = b;
        lamp.invalidate();
    }

    /**
     * The card's choice sliders (the iris): the step of the value read (none while unread or outside the options),
     * dimmed like the page row while stock keeps the row closed (the iris in the office and performance modes).
     */
    private void applyChoiceSliders(MenuState st) {
        for (Map.Entry<FeatureId, QpdSliderRow> e : choiceSliders.entrySet()) {
            FeatureId f = e.getKey();
            QpdSliderRow r = e.getValue();
            Integer step = st == null ? null : MenuSpec.sliderStep(profile.options(f), st.get(f));
            if (!sliderBusy(r)) r.setValue(step);
            r.setRowEnabled(step != null && !st.disabled(f));
            if (r.isRowEnabled()) r.setAlpha(LockedRow.alpha(LockedRow.locked(dev, f, st)));
            r.invalidate();
        }
    }

    private void applyTiles(MenuState st) {
        boolean ok = st != null;
        QpdTile pic = card.tile(MenuSpec.PICTURE);
        if (pic != null) {
            String m = ok ? st.get(FeatureId.PICTURE_MODE) : null;
            String over = ok ? st.tile(MenuSpec.PICTURE) : null;
            pic.setValue(over != null ? over : m == null ? null : tileLabel(FeatureId.PICTURE_MODE, m));
        }
        for (String id : new String[]{FeatureId.INPUT.id, MenuSpec.GAME, MenuSpec.SOUND, MenuSpec.PROJECTION}) {
            QpdTile t = card.tile(id);
            if (t != null && (ok && st.tile(id) != null || FeatureId.INPUT.id.equals(id) || MenuSpec.GAME.equals(id))) {
                t.setValue(ok ? st.tile(id) : null);
            }
        }
        QpdTile td = card.tile(FeatureId.PICTURE_3D.id);
        if (td != null) {
            ThreeDMode m = ok ? ThreeDMode.of(st.get(FeatureId.PICTURE_3D)) : null;
            td.setState(m == null || !m.on() ? null : optionLabel(FeatureId.PICTURE_3D, m.token));
            td.setActive(m != null && m.on());
            if (m != null && shown3d != null && m != shown3d) td.playIcon();
            if (m != null) shown3d = m;
        }
        QpdTile eye = card.tile(MenuSpec.EYE);
        String eyeNow = ok ? st.get(FeatureId.EYE) : null;
        boolean eyeOff = QpdLayout.OFF.equals(eyeNow);                   // crossed out only when it reads off
        if (eyeNow != null && !eyeOff && !eyeNow.equals(prefs.getString(KEY_EYE_ON, null))) {
            prefs.edit().putString(KEY_EYE_ON, eyeNow).apply();
        }
        if (eye != null && (eyeOff != eyeOffShown || eye != eyeTile)) {
            eyeOffShown = eyeOff;
            eyeTile = eye;
            eye.setIcon(eyeOff ? R.drawable.lumen_qpd_ic_eyeoff : R.drawable.lumen_qpd_ic_eye);
        }
        showTheme();
    }

    /** Tile value line: the short form where the full name does not fit a square tile in some languages. */
    private CharSequence tileLabel(FeatureId f, String token) {
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
            if (edit != null && panel == p) edit.close();      // closed while editing: as Готово (stored when changed)
            if (p.allCategory() != null) lastAllCat = p.allCategory();
            boostAsk.cancel();
            sliderOff.cancel();
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
        @Override public String anchorFor(Page sectionPage) {
            String q = quickKeyOf(sectionPage);
            return q != null ? q : sectionOf(sectionPage);
        }
        @Override public int iconFor(Page p) {
            String k = anchorFor(p);
            return k == null ? 0 : quickIcon(k);
        }
        @Override public CharSequence valueFor(Page p) {
            String k = anchorFor(p);
            QpdTile t = k == null ? null : card.tile(k);
            return t == null || t.kind == QpdTile.SMALL || t.kind == QpdTile.ROUND ? null : t.value();
        }
        @Override public boolean onSideBack() { return edit != null && edit.onBack(); }
        @Override public boolean hasPopup(String key) {
            if (FeatureId.EYE.id.equals(key) && !eyeHasPopup()) return false;   // a switch only: OK is all it does
            if (quickItem(key)) return FeatureId.of(key).kind != FeatureKind.ACTION && quickPage(key) != null;
            return !MenuSpec.GENERAL.equals(key) && popupPage(key) != null;
        }
        @Override public void openPopup(String key) {
            if (FeatureId.EYE.id.equals(key)) MenuController.this.openPopup(key);
            else onTile(key);
        }
    };

    /** The profile this menu shows (tests, device glue). */
    public MenuProfile profile() { return profile; }

    /** The last state read (null before the first read). */
    public MenuState state() { return state; }
}
