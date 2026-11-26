// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.steps;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.setup.L;
import org.lumen.setup.R;
import org.lumen.setup.SetupActivity;
import org.lumen.setup.Sys;
import org.lumen.setup.net.Gservices;
import org.lumen.setup.ui.Icon;
import org.lumen.setup.ui.Pill;
import org.lumen.setup.ui.Row;
import org.lumen.setup.ui.Ui;
import org.lumen.qr.QrView;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Google account, optional (F5/F6, R2/R3). The standard AccountManager.addAccount
 * ("com.google") -> GMS -> SetupWraith AddAccountActivity (PRE_ADD_ACCOUNT) -> Google's TV sign-in.
 * No is_setup_wizard option. device_provisioned is set to 1 first (user_setup_complete stays 0, so
 * HOME and our key gates stay locked). After a failure: [Try again] / [Skip] / "Problems signing in?"
 * (GSF device ID + QR to Google's uncertified-device page). Skipping loses nothing: Settings and
 * the Play Store use the same flow later.
 */
public class GoogleStep extends Step {
    public static final String ID = "google";
    private static final String TYPE = "com.google";
    private static final String UNCERTIFIED_URL = "https://www.google.com/android/uncertified";

    private static final int P_MAIN = 0, P_WAIT = 1, P_FAILED = 2, P_HELP = 3, P_DONE = 4;
    private int mPage;
    private boolean mFailedOnce;
    private boolean mHelp;
    private boolean mWaiting;
    private String mAccount;

    public GoogleStep(SetupActivity h) {
        super(h);
    }

    @Override
    public String id() { return ID; }

    /** Only with validated internet and GMS + SetupWraith installed and enabled. */
    @Override
    public boolean available() {
        return host.net != null && host.net.online()
                && Sys.enabled(host, Sys.PKG_GMS) && Sys.enabled(host, Sys.PKG_SETUPWRAITH);
    }

    @Override
    public CharSequence title() { return s(R.string.google_title); }

    @Override
    public CharSequence subtitle() { return s(R.string.google_subtitle); }

    @Override
    public View leftExtra() {
        return Ui.note(ctx(), s(R.string.google_skip_hint));
    }

    private String existingAccount() {
        try {
            Account[] a = AccountManager.get(host).getAccountsByType(TYPE);
            return a.length > 0 ? a[0].name : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** the design notes: the rows at y 340, the other pages level with the title (300). */
    @Override
    public int rightTop() { return 0; }

    @Override
    public View createContent() {
        FrameLayout p = pages();
        p.post(() -> {
            mAccount = existingAccount();
            if (mWaiting) showWait();
            else if (mAccount != null) showDone();
            else if (mFailedOnce) showFailed();
            else showMain();
        });
        return p;
    }

    @Override
    public boolean onBack() {
        if (mPage == P_HELP) {
            leaveHelp();
            return true;
        }
        return mWaiting; // ignore BACK while Google's screens are starting
    }

    private LinearLayout btnRow(Pill a, Pill b) {
        LinearLayout btns = Ui.hbox(ctx());
        btns.addView(a);
        if (b != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Ui.px(16));
            btns.addView(b, lp);
        }
        return btns;
    }

    private void showMain() {
        mPage = P_MAIN;
        // the design notes: Sign in (focused), Cannot sign in?, a line, Skip
        LinearLayout v = Ui.vbox(ctx());
        Row sign = new Row(ctx(), s(R.string.google_sign_in));
        sign.startIcon(new Icon(ctx(), Icon.PERSON), 32);
        sign.subtitle(s(R.string.google_sign_in_sub));
        sign.end(new Icon(ctx(), Icon.CHEVRON), Ui.px(32), Ui.px(32));
        sign.setOnClickListener(x -> signIn());
        v.addView(sign, rowLp());
        Row help = new Row(ctx(), s(R.string.google_help));
        help.startIcon(new Icon(ctx(), Icon.INFO), 32);
        help.subtitle(s(R.string.google_help_sub));
        help.end(new Icon(ctx(), Icon.CHEVRON), Ui.px(32), Ui.px(32));
        help.setOnClickListener(x -> showHelp());
        v.addView(help, rowLp());
        v.addView(Ui.separator(ctx()));
        Row skip = new Row(ctx(), s(R.string.action_skip));
        skip.startIcon(new Icon(ctx(), Icon.FORWARD), 32);
        skip.setOnClickListener(x -> host.next("skip"));
        v.addView(skip, rowLp());
        v.setLayoutParams(pageLp(340));
        showPage(v, sign);
    }

    private void showWait() {
        mPage = P_WAIT;
        LinearLayout v = Ui.hbox(ctx());
        Icon sp = new Icon(ctx(), Icon.SPINNER).color(Ui.TEXT);
        v.addView(sp, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        TextView t = Ui.text(ctx(), 26, Ui.TEXT_DIM, Ui.W_STATUS);
        t.setText(s(R.string.working));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.setMarginStart(Ui.px(20));
        v.addView(t, tl);
        v.setFocusable(true);
        showPage(v, v);
    }

    private void showDone() {
        mPage = P_DONE;
        LinearLayout v = Ui.vbox(ctx());
        LinearLayout card = Ui.hbox(ctx());
        card.setBackground(Ui.card(org.lumen.ui.Tokens.R_TILE_SETUP));
        card.setPadding(Ui.px(28), Ui.px(24), Ui.px(28), Ui.px(24));
        TextView t = Ui.text(ctx(), 26, Ui.TEXT, Ui.W_STATUS);
        t.setText(s(R.string.google_signed_in, mAccount));
        card.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(new Icon(ctx(), Icon.DONE).color(Ui.OK), new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        v.addView(card);
        Pill cont = new Pill(ctx(), s(R.string.action_continue), true);
        cont.setOnClickListener(x -> host.next("next"));
        v.addView(btnRow(cont, null), Ui.lpTop(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 36));
        showPage(v, cont);
    }

    private void showFailed() {
        mPage = P_FAILED;
        LinearLayout v = Ui.vbox(ctx());
        LinearLayout head = Ui.hbox(ctx());
        head.addView(new Icon(ctx(), Icon.ALERT).color(Ui.ERROR), new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        TextView t = Ui.text(ctx(), 30, Ui.ERROR, Ui.W_STATUS);
        t.setText(s(R.string.google_failed));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.px(20));
        head.addView(t, tl);
        v.addView(head);
        Pill again = new Pill(ctx(), s(R.string.net_try_again), true);
        again.setOnClickListener(x -> signIn());
        Pill skip = new Pill(ctx(), s(R.string.action_skip), false);
        skip.setOnClickListener(x -> host.next("skip"));
        v.addView(btnRow(again, skip), Ui.lpTop(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 32));
        if (mHelp) {
            Row help = new Row(ctx(), s(R.string.google_help));
            help.startIcon(new Icon(ctx(), Icon.INFO), 32);
            help.end(new Icon(ctx(), Icon.CHEVRON), Ui.px(32), Ui.px(32));
            help.setOnClickListener(x -> showHelp());
            v.addView(help, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 28));
        }
        showPage(v, again);
    }

    /** Back from the help page to where it was opened: the failed page after a failed sign in, else the main one. */
    private void leaveHelp() {
        if (mFailedOnce) showFailed();
        else showMain();
    }

    /** Uncertified-device helper: every wipe creates a new GSF ID that may need registering. */
    private void showHelp() {
        mPage = P_HELP;
        LinearLayout v = Ui.hbox(ctx());
        v.setGravity(Gravity.TOP);
        QrView qr = new QrView(ctx(), UNCERTIFIED_URL);
        FrameLayout qrCard = new FrameLayout(ctx());
        qrCard.setBackground(Ui.roundRect(org.lumen.ui.Tokens.QR_CARD, 24));
        qrCard.setPadding(Ui.px(20), Ui.px(20), Ui.px(20), Ui.px(20));
        qrCard.addView(qr, new FrameLayout.LayoutParams(Ui.px(260), Ui.px(260)));
        v.addView(qrCard, new LinearLayout.LayoutParams(Ui.px(300), Ui.px(300)));
        LinearLayout col = Ui.vbox(ctx());
        TextView txt = Ui.body(ctx(), s(R.string.google_help_text));
        col.addView(txt);
        TextView url = Ui.text(ctx(), 24, Ui.ACCENT, Ui.regular());
        url.setText("google.com/android/uncertified");
        col.addView(url, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 14));
        TextView dec = Ui.text(ctx(), 30, Ui.TEXT, Ui.medium());
        TextView hex = Ui.text(ctx(), 24, Ui.TEXT_DIM, Ui.regular());
        dec.setTextIsSelectable(false);
        col.addView(dec, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 24));
        col.addView(hex, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        Pill back = new Pill(ctx(), s(R.string.action_back), true);
        back.setOnClickListener(x -> leaveHelp());
        col.addView(btnRow(back, null), Ui.lpTop(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 28));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.setMarginStart(Ui.px(36));
        v.addView(col, cl);
        dec.setText(s(R.string.google_device_id_none));
        host.bg.execute(() -> {
            String id = Gservices.androidId(host);
            host.main.post(() -> {
                if (mPage != P_HELP) return;
                if (id == null) return;
                dec.setText(s(R.string.google_device_id, id));
                try {
                    hex.setText(s(R.string.google_device_id_hex, new BigInteger(id).toString(16)));
                } catch (NumberFormatException e) {
                    hex.setText("");
                }
            });
        });
        showPage(v, back);
    }

    private void signIn() {
        if (mWaiting) return;
        mWaiting = true;
        showWait();
        host.bg.execute(() -> {
            // F6 / T8: GMS add-account outside a setup wizard may expect a provisioned device
            try {
                if (Settings.Global.getInt(host.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) == 0) {
                    Settings.Global.putInt(host.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
                    L.i("google: device_provisioned=1 before add-account");
                }
            } catch (RuntimeException e) {
                L.w("device_provisioned", e);
            }
            host.main.post(this::startAddAccount);
        });
    }

    private void startAddAccount() {
        L.i("google add-account start");
        try {
            AccountManager.get(host).addAccount(TYPE, null, null, new Bundle(), host, this::onResult, host.main);
        } catch (RuntimeException e) {
            L.w("addAccount", e);
            finishAttempt("error:" + e.getClass().getSimpleName(), null);
        }
    }

    private void onResult(AccountManagerFuture<Bundle> f) {
        try {
            Bundle b = f.getResult();
            String name = b == null ? null : b.getString(AccountManager.KEY_ACCOUNT_NAME);
            if (name == null) name = existingAccount();
            finishAttempt(name != null ? "ok" : "error:no_account", name);
        } catch (OperationCanceledException e) {
            String name = existingAccount();
            finishAttempt(name != null ? "ok" : "cancel", name);
        } catch (AuthenticatorException e) {
            finishAttempt("error:authenticator", existingAccount());
        } catch (IOException e) {
            finishAttempt("error:io", existingAccount());
        } catch (RuntimeException e) {
            finishAttempt("error:" + e.getClass().getSimpleName(), existingAccount());
        }
    }

    private void finishAttempt(String result, String name) {
        mWaiting = false;
        L.i("google result=" + result);
        if (host.current() != this) return;
        mAccount = name;
        if (name != null) {
            showDone();
            host.main.postDelayed(() -> {
                if (host.current() == this && mPage == P_DONE) host.next("next");
            }, 1200);
        } else {
            mFailedOnce = true;
            // a cancel offers Try again / Skip; a real error also the uncertified-device helper
            if (!"cancel".equals(result)) mHelp = true;
            showFailed();
        }
    }

    private static LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(6);
        return lp;
    }
}
