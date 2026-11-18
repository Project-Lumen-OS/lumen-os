// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

import org.lumen.audio.BtA2dp;
import org.lumen.audio.OutputScan;
import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuState;
import org.lumen.menu.sound.SoundOutputs;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.Page;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The sound output page and its audio format page (owner variant C, 2026-11-18) for every projector: the rows come
 * from SoundOutputs over the device's read (sound.output value and options, sound.format) and lumen-audio OutputScan
 * (real names, paired Bluetooth devices, where media plays now); a choice goes through the menu's usual path
 * (MenuController.choose: refusals, questions, the device adapter's set), Connect is a normal Bluetooth connect of the
 * paired device (BtA2dp.connect). Main thread.
 */
final class OutputPages {
    private static final String TAG = MenuController.TAG;
    /** Re-reads after Connect: the stack needs a few seconds to bring the device up. */
    private static final long[] CONNECT_REREAD_MS = {2_000, 5_000, 9_000};
    private static final long CONNECT_GIVE_UP_MS = 12_000;
    private static final String FORMAT_KEY = "format", OFFLINE_KEY = "bt@";

    interface Host {
        void choose(FeatureId f, String token);
        void refresh();
        void push(Page p);
        void back(Page from);
        boolean onTop(Page p);
        boolean formatShown(MenuState st);
        CharSequence label(FeatureId f, String token);
        void failed();
    }

    private final Context app;
    private final MenuProfile profile;
    private final CharSequence crumb;
    private final Host host;
    private final Set<String> connecting = new HashSet<>();
    private SheetPage outPage, formatPage;

    OutputPages(Context app, MenuProfile profile, CharSequence crumb, Host host) {
        this.app = app;
        this.profile = profile;
        this.crumb = crumb;
        this.host = host;
    }

    /** The row's OK on the Sound page: the output page, built in the theme on screen now. */
    void open(MenuState st) {
        OutputSheet v = sheet(crumb, app.getText(R.string.lumen_m_sound_output));
        outPage = new SheetPage(app.getText(R.string.lumen_m_sound_output), v);
        showOutputs(st);
        host.push(outPage);
        host.refresh();
    }

    /** A new read: the page on screen shows it. */
    void apply(MenuState st) {
        if (outPage != null && host.onTop(outPage)) showOutputs(st);
        if (formatPage != null && host.onTop(formatPage)) showFormats(st);
    }

    /** The value the Sound page's row shows: the choice (a runtime name when the device gives one). */
    CharSequence summary(MenuState st) {
        String v = st == null ? null : st.get(FeatureId.SOUND_OUTPUT);
        return v == null || v.isEmpty() ? host.label(FeatureId.SOUND_OUTPUT, SoundOutputs.AUTO) : host.label(FeatureId.SOUND_OUTPUT, v);
    }

    // ------------------------------------------------------------------ the output page
    private void showOutputs(MenuState st) {
        SoundOutputs o = SoundOutputs.of(profile.options(FeatureId.SOUND_OUTPUT),
                st == null ? null : st.options(FeatureId.SOUND_OUTPUT), st == null ? null : st.get(FeatureId.SOUND_OUTPUT),
                OutputScan.read(app), profile.jackSensed(), st == null ? null : st.get(FeatureId.SOUND_FORMAT));
        List<OutputSheet.Item> items = new ArrayList<>();
        String focus = null;
        for (SoundOutputs.Row r : o.rows) {
            OutputSheet.Item it = item(r);
            items.add(it);
            if (r.selected) focus = it.key;
        }
        int sep = -1;
        if (host.formatShown(st)) {
            sep = items.size();
            String f = st.get(FeatureId.SOUND_FORMAT);
            items.add(new OutputSheet.Item(FORMAT_KEY, R.drawable.lumen_ic_sound_format, app.getText(R.string.lumen_m_sound_format),
                    null, host.label(FeatureId.SOUND_FORMAT, f), false, false, false, false, true, this::openFormats));
        }
        outPage.sheet.show(now(o), items, sep, focus);
    }

    private OutputSheet.Item item(SoundOutputs.Row r) {
        boolean wait = r.offline() && connecting.contains(r.addr);
        String state = wait ? null : r.state;
        CharSequence title = title(r);
        CharSequence via = via(r.via);
        CharSequence line = join(via != null && via.toString().contentEquals(title) ? null : via,
                wait ? app.getText(R.string.lumen_m_out_connecting) : stateText(state));
        if (r.offline()) {
            return new OutputSheet.Item(OFFLINE_KEY + r.addr, R.drawable.lumen_ic_bluetooth_off, title, line, null, false, false,
                    false, true, false, () -> connect(r.addr));
        }
        return new OutputSheet.Item(r.token, icon(r.kind), title, line, null, true, r.selected, r.playing, false, false,
                () -> host.choose(FeatureId.SOUND_OUTPUT, r.token));
    }

    private OutputSheet.Now now(SoundOutputs o) {
        if (o.now == null) return null;
        CharSequence fmt = o.nowFormat == null ? null : host.label(FeatureId.SOUND_FORMAT, o.nowFormat);
        CharSequence picked = app.getText(o.nowAuto ? R.string.lumen_m_out_auto_picked : R.string.lumen_m_out_hand_picked);
        CharSequence title = title(o.now);
        CharSequence via = via(o.now.via);
        return new OutputSheet.Now(icon(o.now.kind), title,
                join(join(via != null && via.toString().contentEquals(title) ? null : via, fmt), picked));
    }

    private CharSequence title(SoundOutputs.Row r) {
        return r.name != null ? r.name : host.label(FeatureId.SOUND_OUTPUT, r.kind);
    }

    private CharSequence via(String v) {
        if (v == null) return null;
        switch (v) {
            case SoundOutputs.VIA_ARC: return app.getText(R.string.lumen_m_out_arc);
            case SoundOutputs.VIA_EARC: return app.getText(R.string.lumen_m_out_earc);
            case SoundOutputs.VIA_BT: return app.getText(R.string.lumen_m_out_bt);
            case SoundOutputs.VIA_JACK: return app.getText(R.string.lumen_m_out_jack);
            case SoundOutputs.VIA_USB: return app.getText(R.string.lumen_m_out_usb);
            default: return null;
        }
    }

    private CharSequence stateText(String s) {
        if (s == null) return null;
        switch (s) {
            case SoundOutputs.CONNECTED: return app.getText(R.string.lumen_m_out_connected);
            case SoundOutputs.OFFLINE: return app.getText(R.string.lumen_m_out_offline);
            case SoundOutputs.BY_HAND: return app.getText(R.string.lumen_m_out_by_hand);
            default: return null;
        }
    }

    private static int icon(String kind) {
        switch (kind) {
            case SoundOutputs.SPEAKER: return R.drawable.lumen_ic_speaker;
            case SoundOutputs.ARC: return R.drawable.lumen_ic_soundbar;
            case SoundOutputs.BT: return R.drawable.lumen_ic_bluetooth;
            case SoundOutputs.WIRED: return R.drawable.lumen_ic_headphones;
            case SoundOutputs.USB: return R.drawable.lumen_ic_usb;
            default: return R.drawable.lumen_ic_auto;
        }
    }

    /** a · b, the second part starting lower case (HDMI ARC · подключено); a missing part is left out. */
    private CharSequence join(CharSequence a, CharSequence b) {
        if (a == null || a.length() == 0) return b;
        if (b == null || b.length() == 0) return a;
        String t = b.toString();
        boolean word = t.length() > 1 && Character.isUpperCase(t.charAt(0)) && Character.isLowerCase(t.charAt(1));
        Locale l = app.getResources().getConfiguration().getLocales().get(0);
        return a + " \u00B7 " + (word ? t.substring(0, 1).toLowerCase(l) + t.substring(1) : t);
    }

    private void connect(String addr) {
        if (!connecting.add(addr)) return;
        host.refresh();
        new Thread(() -> {
            boolean ok = BtA2dp.connect(addr, "output menu");
            Ui.main().post(() -> {
                if (!ok) {
                    connecting.remove(addr);
                    host.failed();
                }
                host.refresh();
            });
        }, "lumen-out-connect").start();
        for (long ms : CONNECT_REREAD_MS) Ui.main().postDelayed(host::refresh, ms);
        Ui.main().postDelayed(() -> {
            if (connecting.remove(addr)) Log.i(TAG, "output menu: " + addr + " not up after " + CONNECT_GIVE_UP_MS + " ms");
            host.refresh();
        }, CONNECT_GIVE_UP_MS);
    }

    // ------------------------------------------------------------------ the audio format page
    private void openFormats() {
        OutputSheet v = sheet(app.getText(R.string.lumen_m_sound_output), app.getText(R.string.lumen_m_sound_format));
        formatPage = new SheetPage(app.getText(R.string.lumen_m_sound_format), v);
        showFormats(null);
        host.push(formatPage);
        host.refresh();
    }

    private void showFormats(MenuState st) {
        String cur = st == null ? null : st.get(FeatureId.SOUND_FORMAT);
        List<OutputSheet.Item> items = new ArrayList<>();
        for (String o : profile.options(FeatureId.SOUND_FORMAT)) {
            items.add(new OutputSheet.Item(o, R.drawable.lumen_ic_sound_format, host.label(FeatureId.SOUND_FORMAT, o), null, null,
                    true, o.equals(cur), false, false, false, () -> {
                        host.choose(FeatureId.SOUND_FORMAT, o);
                        host.back(formatPage);
                    }));
        }
        formatPage.sheet.show(null, items, -1, cur);
    }

    private OutputSheet sheet(CharSequence crumbText, CharSequence title) {
        Palette p = LumenTheme.palette(app);
        OutputSheet v = new OutputSheet(app, p, crumbText, title);
        v.setConnectText(app.getText(R.string.lumen_m_out_connect));
        return v;
    }
}
