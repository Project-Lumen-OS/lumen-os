// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.View;

import org.lumen.projector.R;
import org.lumen.ui.Inter;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;
import org.lumen.ui.ThemeFade;
import org.lumen.ui.page.PaneProgress;
import org.lumen.ui.page.PaneRow;
import org.lumen.ui.page.TwoPane;

import java.util.EnumMap;
import java.util.Map;

/**
 * "Send a problem report", the same screen on every projector (All settings > General > Send a problem report), in
 * the shared two-pane page (lumen-ui TwoPane, owner pick A 2026-11-26): on the left where the user is, what a report
 * does and the last report's code, on the right the actions. Which screen, which actions and where the focus starts
 * is {@link ReportView} (focus starts on Send report). It follows the shared light / dark theme live (ThemeFade). The
 * job lives in the process, so HOME during a report does not lose the code.
 */
public final class ReportActivity extends Activity implements ReportJob.Listener {
    private static final String TAG = ReportConfig.TAG;
    private static final String SEP = "  \u00b7  ";

    private ReportJob job;
    private ThemeFade theme;
    private Palette pal;
    private TwoPane page;
    private ReportView shown;
    private boolean details;
    private ReportView.Action refocus;
    private PaneProgress progress;
    private final EnumMap<ReportView.Action, PaneRow> rows = new EnumMap<>(ReportView.Action.class);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!ReportConfig.enabled()) {
            finish();
            return;
        }
        job = ReportJob.get();
        pal = LumenTheme.palette(this);
        theme = ThemeFade.activity(this, p -> {
            pal = p;
            if (shown != null) build(focused());
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (job == null) return;
        job.addListener(this);
        theme.start();
        shown = null;
        render();
    }

    @Override
    protected void onStop() {
        if (job != null) {
            job.removeListener(this);
            theme.stop();
        }
        super.onStop();
    }

    @Override
    public void onReportChanged() {
        if (!isFinishing() && !isDestroyed()) render();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (job == null || shown == null) {
            super.onBackPressed();
            return;
        }
        switch (shown.back()) {
            case CANCEL:
                job.cancel();
                return;
            case START:
                refocus = ReportView.returnFocus(shown.screen);
                if (shown.screen == ReportView.Screen.DETAILS) details = false;
                else job.reset();
                render();
                return;
            default:
                super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ screens
    private void render() {
        if (job.state() != ReportState.IDLE) details = false;
        ReportView v = ReportView.of(job.state(), details);
        if (shown != null && v.screen == shown.screen) {
            updateProgress();
            return;
        }
        shown = v;
        ReportView.Action f = refocus != null && v.actions.contains(refocus) ? refocus : v.focus;
        refocus = null;
        build(f);
    }

    private ReportView.Action focused() {
        for (Map.Entry<ReportView.Action, PaneRow> e : rows.entrySet()) if (e.getValue().isFocused()) return e.getKey();
        return shown.focus;
    }

    private Drawable icon(int res) {
        return getDrawable(res);
    }

    private void build(ReportView.Action focus) {
        page = new TwoPane(this, pal);
        rows.clear();
        progress = null;
        CharSequence crumb = getString(R.string.lumen_report_crumb_settings) + SEP + getString(R.string.lumen_report_crumb_general);
        page.footer(icon(R.drawable.lumen_report_ic_lock), getString(R.string.lumen_report_footer));
        switch (shown.screen) {
            case DETAILS:
                guideStart(crumb);
                page.clear(true);
                page.section(getString(R.string.lumen_report_included));
                item(R.drawable.lumen_report_ic_logs, R.string.lumen_report_inc_logs, false);
                item(R.drawable.lumen_report_ic_alert, R.string.lumen_report_inc_crashes, false);
                item(R.drawable.lumen_report_ic_kernel, R.string.lumen_report_inc_kernel, false);
                item(R.drawable.lumen_report_ic_devices, R.string.lumen_report_inc_devices, false);
                item(R.drawable.lumen_report_ic_screen, R.string.lumen_report_inc_system, false);
                page.section(getString(R.string.lumen_report_excluded));
                item(R.drawable.lumen_report_ic_lock, R.string.lumen_report_exc_accounts, true);
                item(R.drawable.lumen_report_ic_wifi_off, R.string.lumen_report_exc_network, true);
                item(R.drawable.lumen_report_ic_shield, R.string.lumen_report_exc_serial, true);
                item(R.drawable.lumen_report_ic_package, R.string.lumen_report_exc_apps, true);
                item(R.drawable.lumen_report_ic_image, R.string.lumen_report_exc_files, true);
                page.space(18);
                row(ReportView.Action.BACK, R.drawable.lumen_report_ic_back, R.string.lumen_report_back, 0);
                break;
            case PROGRESS:
                guideStart(crumb);
                progress = page.progress();
                updateProgress();
                row(ReportView.Action.CANCEL, R.drawable.lumen_report_ic_close, R.string.lumen_report_cancel, 0);
                break;
            case DONE:
                page.guide(crumb, getString(R.string.lumen_report_done_title), getString(R.string.lumen_report_done_text));
                page.code(getString(R.string.lumen_report_code), job.code() == null ? "" : job.code());
                row(ReportView.Action.DONE, R.drawable.lumen_report_ic_check, R.string.lumen_report_done, 0);
                break;
            case QUEUED:
                page.guide(crumb, getString(R.string.lumen_report_queued_title), getString(R.string.lumen_report_queued_text));
                row(ReportView.Action.CLOSE, R.drawable.lumen_report_ic_close, R.string.lumen_report_close, 0);
                break;
            case FAILED:
                page.guide(crumb, getString(R.string.lumen_report_failed_title), getString(failureText(job.failure())));
                row(ReportView.Action.RETRY, R.drawable.lumen_report_ic_refresh, R.string.lumen_report_retry, 0);
                row(ReportView.Action.CLOSE, R.drawable.lumen_report_ic_close, R.string.lumen_report_close, 0);
                break;
            case START:
            default:
                guideStart(crumb);
                row(ReportView.Action.SEND, R.drawable.lumen_report_ic_send, R.string.lumen_report_send,
                        R.string.lumen_report_send_sub);
                row(ReportView.Action.AUTO, R.drawable.lumen_report_ic_refresh, R.string.lumen_report_auto,
                        R.string.lumen_report_auto_sub).withSwitch(ReportPrefs.autoOn(this));
                row(ReportView.Action.DETAILS, R.drawable.lumen_report_ic_shield_check, R.string.lumen_report_what,
                        R.string.lumen_report_what_sub).withChevron(icon(R.drawable.lumen_report_ic_chevron));
                row(ReportView.Action.CLOSE, R.drawable.lumen_report_ic_close, R.string.lumen_report_close, 0);
                break;
        }
        setContentView(page);
        PaneRow f = rows.get(focus);
        if (f == null) f = rows.get(shown.focus);
        if (f != null) {
            final View v = f;
            v.post(v::requestFocus);
        }
    }

    /** The left side of START, DETAILS and PROGRESS: what the page does and the last report. */
    private void guideStart(CharSequence crumb) {
        page.guide(crumb, getString(R.string.lumen_report_heading), getString(R.string.lumen_report_intro));
        String mc = ReportPrefs.lastCode(this, false), ac = ReportPrefs.lastCode(this, true);
        long mt = ReportPrefs.lastTime(this, false), at = ReportPrefs.lastTime(this, true);
        boolean auto = ReportView.newerIsAuto(mc, mt, ac, at);
        String code = auto ? ac : mc;
        long when = auto ? at : mt;
        if (code == null || when <= 0) return;
        SpannableStringBuilder v = new SpannableStringBuilder(code);
        v.setSpan(new TypefaceSpan(Inter.of(this, 600, 30)), 0, code.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.append(", ").append(DateUtils.getRelativeDateTimeString(this, when, DateUtils.DAY_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS, 0));
        page.meta(getString(auto ? R.string.lumen_report_last_auto : R.string.lumen_report_last), v);
    }

    private void item(int icon, int text, boolean quiet) {
        page.item(icon(icon), getString(text), quiet);
    }

    private PaneRow row(ReportView.Action a, int icon, int label, int sub) {
        PaneRow r = page.row(icon(icon), getString(label), sub == 0 ? null : getString(sub));
        r.setOnClickListener(v -> safe(() -> act(a, r)));
        rows.put(a, r);
        return r;
    }

    private void act(ReportView.Action a, PaneRow r) {
        switch (a) {
            case SEND:
            case RETRY:
                job.start(this);
                break;
            case AUTO:
                boolean on = !ReportPrefs.autoOn(this);
                ReportPrefs.setAutoOn(this, on);
                r.setOn(on);
                break;
            case DETAILS:
                details = true;
                render();
                break;
            case BACK:
                details = false;
                refocus = ReportView.Action.DETAILS;
                render();
                break;
            case CANCEL:
                job.cancel();
                break;
            case DONE:
                job.reset();
                break;
            case CLOSE:
            default:
                job.reset();
                finish();
                break;
        }
    }

    private void updateProgress() {
        if (progress == null) return;
        ReportState st = job.state();
        float done = ReportView.progress(st, job.step(), job.steps(), job.sent(), job.total());
        if (st == ReportState.COLLECTING) {
            progress.set(getString(R.string.lumen_report_collecting), getString(R.string.lumen_report_step,
                    ReportView.step(job.step(), job.steps()), Math.max(1, job.steps())), done);
        } else {
            progress.set(getString(R.string.lumen_report_sending), getString(R.string.lumen_report_bytes,
                    Formatter.formatShortFileSize(this, job.sent()), Formatter.formatShortFileSize(this, job.total())), done);
        }
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.w(TAG, "click: " + t);
        }
    }

    private static int failureText(ReportFailure f) {
        if (f == null) return R.string.lumen_report_err_server;
        switch (f) {
            case NO_NETWORK: return R.string.lumen_report_err_no_network;
            case NETWORK: return R.string.lumen_report_err_network;
            case RATE_LIMITED: return R.string.lumen_report_err_rate;
            case REJECTED: return R.string.lumen_report_err_rejected;
            case COLLECT: return R.string.lumen_report_err_collect;
            case SERVER:
            default: return R.string.lumen_report_err_server;
        }
    }
}
