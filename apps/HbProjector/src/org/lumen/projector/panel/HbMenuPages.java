package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.cec.CecPage;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.Theme;

/**
 * Z9X HB (goat_elegant): the device pages shown INSIDE the shared menu (MenuController.Pages):
 * picture.pro = the HB professional picture pages (ProPicture: white balance, colour, gamma, contrast, noise reduction,
 * each row behind its HbFeatures key), settings.cec = the CEC page of the cec module. Built once per menu (the menu is
 * rebuilt on a locale change, HbMenu.reset). Everything else is a screen of HbMenuFeatures.open or nothing.
 */
final class HbMenuPages implements MenuController.Pages {
    private static final String TAG = "HbMenu";
    private final Context app;
    private final SharedPreferences prefs;
    private ProPicture pro;
    private Page cec;

    HbMenuPages(Context c) {
        app = c.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
    }

    private MenuController menu;

    @Override
    public Page page(FeatureId id, MenuController menu) {
        this.menu = menu;
        switch (id) {
            case PICTURE_PRO: return pro().page;
            case SETTINGS_CEC: return cec();
            default: return null;
        }
    }

    private ProPicture pro() {
        if (pro == null) {
            pro = new ProPicture(app, prefs, new ProPicture.Host() {
                @Override public void push(Page pg) { if (menu != null) menu.push(pg); }

                @Override public void confirm(int titleRes, CharSequence msg, int okRes, Runnable onOk) {
                    if (menu != null) menu.confirm(titleRes, msg, okRes, onOk);
                }
            });
        }
        return pro;
    }

    private Page cec() {
        if (cec == null) {
            cec = CecPage.build(app, new CecPage.Host() {
                @Override public void refreshPage(Page page) { if (menu != null) menu.refreshPage(page); }

                @Override public void closeThen(Runnable r) {
                    if (menu != null) menu.closeThen(r);
                    else Ui.main().postDelayed(r, Theme.PANEL_EXIT_MS + 60);
                }

                @Override public void openHdmiOptions() {
                    try {
                        Ui.wakeFromDream(app);
                        app.startActivity(new Intent().setClassName("org.lumen.tvinput", "org.lumen.tvinput.SettingsActivity")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable t) {
                        Log.w(TAG, "HDMI settings: " + t);
                        Ui.toast(app, R.string.toast_failed);
                    }
                }

                @Override public boolean setupDone(String what) {
                    if (KeyReceiver.isSetupComplete(app)) return true;
                    Log.i(TAG, "setup not complete: " + what + " ignored");
                    Ui.toast(app, R.string.toast_setup_running);
                    return false;
                }
            });
        }
        return cec;
    }
}
