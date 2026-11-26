// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.steps;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.app.LocalePicker;

import org.lumen.setup.InstallOffer;
import org.lumen.setup.L;
import org.lumen.setup.Langs;
import org.lumen.setup.R;
import org.lumen.setup.SetupActivity;
import org.lumen.setup.Sys;
import org.lumen.setup.ui.Icon;
import org.lumen.setup.ui.Row;
import org.lumen.setup.ui.ScrollBox;
import org.lumen.setup.ui.Sheet;
import org.lumen.setup.ui.Ui;
import org.lumen.setup.ui.Wordmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Welcome + language: the 17 Lumen languages with native names, then "More languages…"
 * (all system asset locales; our apps fall back to English there). Selecting a language applies it
 * at once without an activity relaunch and moves on; the current one only moves on.
 */
public class WelcomeStep extends Step {
    public static final String ID = "welcome";
    private View mFocus;

    public WelcomeStep(SetupActivity h) {
        super(h);
    }

    @Override
    public String id() { return ID; }

    @Override
    public CharSequence title() { return s(R.string.welcome_title); }

    @Override
    public CharSequence subtitle() { return s(R.string.welcome_subtitle); }

    /** the design notes: the big logo (mark 120, word mark 64) above the title, none in the top bar. */
    @Override
    public View leftHead() {
        LinearLayout v = Ui.vbox(ctx());
        v.addView(new Wordmark(ctx(), 120, 64, 26));
        v.setPadding(0, 0, 0, Ui.px(72));
        return v;
    }

    @Override
    public int leftTop() { return 268; }

    @Override
    public boolean showsLogo() { return false; }

    @Override
    public CharSequence footer() {
        return s(R.string.footer_version, Sys.osVersion()) + "      " + s(R.string.tagline);
    }

    /** the design notes: the list from y 232, the current language first and focused. */
    @Override
    public int rightTop() { return 232; }

    /** Design px of the list's height: about eight rows, the next one fades out at the bottom edge. */
    private static final int LIST_H = 1080 - 232 - 150;

    @Override
    public View createContent() {
        ScrollBox box = new ScrollBox(ctx(), 72);
        LinearLayout list = box.list;
        Locale cur = host.getResources().getConfiguration().getLocales().get(0);
        int sel = Langs.indexOf(cur);
        mFocus = null;
        if (InstallOffer.available(host)) list.addView(installRow(), rowLp());
        for (int i : Langs.order(sel)) {
            final String tag = Langs.TAGS[i];
            Row r = new Row(ctx(), Langs.NAMES[i]);
            r.title.setTextLocale(Locale.forLanguageTag(tag));
            r.end(new Icon(ctx(), i == sel ? Icon.RADIO_ON : Icon.RADIO_OFF), Ui.px(32), Ui.px(32));
            if (i == sel) mFocus = r;
            final boolean isCurrent = i == sel;
            r.setOnClickListener(v -> pick(Locale.forLanguageTag(tag), isCurrent));
            list.addView(r, rowLp());
        }
        Row more = new Row(ctx(), s(R.string.lang_more));
        more.end(new Icon(ctx(), Icon.CHEVRON), Ui.px(32), Ui.px(32));
        if (sel < 0) {
            more.subtitle(Langs.nativeName(cur));
            mFocus = more;
        }
        more.setOnClickListener(v -> showMore());
        list.addView(more, rowLp());
        LinearLayout v = Ui.vbox(ctx());
        v.addView(box, ScrollBox.lp(Ui.px(LIST_H), 0f));
        return v;
    }

    /**
     * "Install Lumen OS" (a trial run only): a list row like the others with the install icon and one short line
     * under the title. The texts are the installer's own (one translation); an older installer without the short
     * line gives its long one on two lines.
     */
    private Row installRow() {
        Row offer = new Row(ctx(), InstallOffer.text(host, "offer_title", "Install Lumen OS"));
        offer.startIcon(new Icon(ctx(), Icon.INSTALL), 32);
        String sub = InstallOffer.text(host, "offer_short", null);
        if (sub == null) {
            sub = InstallOffer.text(host, "offer_sub", "");
        } else {
            offer.subtitleLines(1);
        }
        offer.subtitle(sub);
        offer.end(new Icon(ctx(), Icon.CHEVRON), Ui.px(32), Ui.px(32));
        offer.setOnClickListener(v -> host.startActivity(InstallOffer.intent()));
        return offer;
    }

    private static LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(6);
        return lp;
    }

    @Override
    public View initialFocus() { return mFocus; }

    private void pick(Locale l, boolean isCurrent) {
        if (isCurrent) {
            host.next("next");
            return;
        }
        host.setLocale(l, () -> {
            if (host.current() == this) host.next("next");
        });
    }

    /**
     * Locales the system has resources for: LocalePicker.getAllAssetLocales (Android 14, hidden),
     * else the public AssetManager list. Pseudo-locales (en-XA, ar-XB…) are dropped.
     */
    private List<Locale> assetLocales() {
        List<Locale> out = new ArrayList<>();
        try {
            for (LocalePicker.LocaleInfo li : LocalePicker.getAllAssetLocales(host, false)) {
                if (li.getLocale() != null) out.add(li.getLocale());
            }
        } catch (Throwable t) {
            L.w("getAllAssetLocales unavailable (" + t.getClass().getSimpleName() + "), using AssetManager");
            out.clear();
            for (String tag : android.content.res.Resources.getSystem().getAssets().getLocales()) {
                if (tag == null || tag.isEmpty()) continue;
                Locale l = Locale.forLanguageTag(tag);
                String c = l.getCountry();
                if (l.getLanguage().isEmpty() || "XA".equals(c) || "XB".equals(c) || "XC".equals(c)) continue;
                out.add(l);
            }
            // "af" next to "af-ZA": keep only the regional entries of a language
            Set<String> regional = new HashSet<>();
            for (Locale l : out) if (!l.getCountry().isEmpty()) regional.add(l.getLanguage());
            out.removeIf(l -> l.getCountry().isEmpty() && regional.contains(l.getLanguage()));
        }
        return out;
    }

    /** All asset locales of the system, native names, sorted; ours already listed are left out. */
    private void showMore() {
        host.bg.execute(() -> {
            Set<String> ours = new HashSet<>();
            for (String t : Langs.TAGS) ours.add(t);
            Set<String> seen = new HashSet<>();
            List<Locale> locs = new ArrayList<>();
            for (Locale l : assetLocales()) {
                String tag = l.toLanguageTag();
                if (ours.contains(tag) || !seen.add(tag)) continue;
                locs.add(l);
            }
            List<String> names = new ArrayList<>();
            for (Locale l : locs) names.add(Langs.nativeName(l));
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < locs.size(); i++) order.add(i);
            java.text.Collator col = java.text.Collator.getInstance(Locale.ROOT);
            Collections.sort(order, (a, b) -> col.compare(names.get(a), names.get(b)));
            host.main.post(() -> {
                if (host.current() != this) return;
                // Lumen OS without Google: no Google apps to name in the hint
                Sheet sh = new Sheet(ctx(), s(R.string.lang_more), s(Sys.installed(host, Sys.PKG_GMS)
                        ? R.string.lang_more_hint : R.string.lang_more_hint_nogms));
                LinearLayout list = sh.addList();
                View first = null;
                Locale cur = host.getResources().getConfiguration().getLocales().get(0);
                for (int idx : order) {
                    Locale l = locs.get(idx);
                    Row r = new Row(ctx(), names.get(idx));
                    r.title.setTextLocale(l);
                    r.end(new Icon(ctx(), l.equals(cur) ? Icon.RADIO_ON : Icon.RADIO_OFF), Ui.px(32), Ui.px(32));
                    if (l.equals(cur)) first = r;
                    r.setOnClickListener(v -> {
                        sh.dismiss();
                        pick(l, l.equals(cur));
                    });
                    list.addView(r, rowLp());
                    if (first == null && list.getChildCount() == 1) first = r;
                }
                sh.addButton(s(R.string.action_cancel), false, v -> sh.dismiss());
                host.showSheet(sh, first);
            });
        });
    }
}
