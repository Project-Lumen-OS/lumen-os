// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.lumen.about.CatalogSource;
import org.lumen.about.RestoreCatalog;
import org.lumen.about.UnitFacts;
import org.lumen.about.WayBack;
import org.lumen.about.XgimiFacts;
import org.lumen.projector.R;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Back to the XGIMI system, a page of the menu on every projector (Settings > About opens it through the old screen's
 * address, lumen-about XgimiSystemActivity): the model code, the XGIMI firmware, the exact file name the USB stick
 * needs, the official XGIMI file when Lumen Installer's catalog knows it for this model and firmware, the erase
 * warning and a QR code of that file (else of the guide's Back to stock section). It only shows facts: nothing on the
 * projector changes. MenuController asks the erase warning first (DialogPanel.confirmPage).
 */
final class XgimiSystemPage {
    private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> new Thread(r, "lumen-xgimi-page"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context app;
    private final Consumer<Page> refresh;
    private final XgimiFacts facts = UnitFacts.read();
    final Page page;
    private final NavRow model, firmware, usbFile, official;
    private final TextRow status, link;
    private final QrRow qr;
    private RestoreCatalog catalog;
    private boolean searching = true, loading;

    XgimiSystemPage(Context c, Consumer<Page> refresh) {
        app = c.getApplicationContext();
        this.refresh = refresh;
        page = new Page(s(R.string.lumen_about_back));
        page.add(new TextRow(app, s(R.string.lumen_about_intro)));
        model = fact(R.string.lumen_about_model_code);
        firmware = fact(R.string.lumen_about_firmware);
        usbFile = fact(R.string.lumen_about_usb_file);
        official = fact(R.string.lumen_about_official);
        status = page.add(new TextRow(app, "").readable());
        page.add(new TextRow(app, s(R.string.lumen_about_erases)).readable());
        qr = page.add(new QrRow(app));
        page.add(new TextRow(app, s(R.string.lumen_about_scan)));
        link = page.add(new TextRow(app, "").readable());
        page.setOnShown(this::load);
        render();
    }

    private String s(int res) {
        return app.getString(res);
    }

    private NavRow fact(int label) {
        return page.add(new NavRow(app, s(label)).setChevron(false));
    }

    /** Shown: the kept catalog at once, then a fresh one from the network, as the old screen did. */
    private void load() {
        if (loading) return;
        loading = true;
        BG.execute(() -> {
            RestoreCatalog kept = CatalogSource.kept(app);
            MAIN.post(() -> show(kept, true));
            RestoreCatalog fresh = CatalogSource.fresh(app, kept);
            MAIN.post(() -> {
                loading = false;
                show(fresh != null ? fresh : kept, false);
            });
        });
    }

    private void show(RestoreCatalog c, boolean stillSearching) {
        catalog = c;
        searching = stillSearching;
        render();
        refresh.accept(page);
    }

    private void render() {
        WayBack w = WayBack.of(facts, catalog, app.getResources().getConfiguration().getLocales().get(0).getLanguage());
        value(model, w.model);
        value(firmware, w.firmware);
        value(usbFile, w.usbFile);
        value(official, w.official == null ? "" : app.getString(R.string.lumen_about_official_value,
                XgimiFacts.shown(w.official.version), String.format(Locale.getDefault(), "%,d", w.official.size)));
        boolean known = !w.usbFile.isEmpty() && w.official != null;
        status.setText(s(searching ? R.string.lumen_about_searching : R.string.lumen_about_guide_files));
        status.setVisibility(known ? View.GONE : View.VISIBLE);
        qr.setText(w.link);
        link.setText(w.link);
    }

    /** A fact row with its value, gone while the projector names none. */
    private static void value(NavRow row, String v) {
        row.setValue(v);
        row.setVisibility(v.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
