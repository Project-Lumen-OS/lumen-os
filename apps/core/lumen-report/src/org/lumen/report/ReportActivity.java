// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.lumen.icons.IconText;
import org.lumen.projector.R;

/**
 * "Send a problem report", the same screen on every projector (General > Send a problem report). Screens, one per
 * ReportJob state:
 *   consent    what is included and what is removed, the switch of automatic reports with its note, the codes of
 *              the last reports; "Send report" / "Cancel" (focus starts on Cancel: nothing leaves the projector
 *              without a deliberate press)
 *   progress   collecting step i of n, then sending x of y; Cancel stops and deletes everything
 *   done       the report code (LR-XXXXXX) to give to the developer
 *   queued     the server cannot be reached: the projector sends the report later, its code then shows here
 *   failed     why, "Try again" (the same zip, or a new collection) / "Close"
 * Framework widgets, D-pad only, every text 18 sp or more, sized for 960x540 dp. BACK: cancel a running report, else
 * close. The job lives in the process, so HOME during a report does not lose the code.
 */
public final class ReportActivity extends Activity implements ReportJob.Listener {
    private static final String TAG = ReportConfig.TAG;
    private static final int BG = 0xFF15171B, TEXT = 0xFFF1EEE8, DIM = 0xFFB5B1AA, ACCENT = 0xFFE9CF9C;

    private ReportJob job;
    private ScrollView scroll;
    private TextView title;
    private LinearLayout body;
    private LinearLayout buttons;
    private ReportState shown;
    private TextView status;
    private TextView hint;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!ReportConfig.enabled()) {
            finish();
            return;
        }
        job = ReportJob.get();
        buildFrame();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (job == null) return;
        job.addListener(this);
        shown = null;
        render();
    }

    @Override
    protected void onStop() {
        if (job != null) job.removeListener(this);
        super.onStop();
    }

    @Override
    public void onReportChanged() {
        if (!isFinishing() && !isDestroyed()) render();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (job != null) {
            if (job.busy()) job.cancel();
            else job.reset();
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ frame
    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void buildFrame() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setDefaultFocusHighlightEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(24), dp(48), dp(16));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        title = text("", 28, TEXT, true);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(16);
        root.addView(buttons, blp);
        setContentView(scroll);
    }

    private TextView text(CharSequence s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView add(LinearLayout parent, CharSequence s, int sp, int color, boolean bold, int topDp) {
        TextView t = text(s, sp, color, bold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        parent.addView(t, lp);
        return t;
    }

    private TextView button(int res, Runnable action) {
        TextView b = text(getString(res), 20, TEXT, false);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(dp(200));
        b.setPadding(dp(28), dp(12), dp(28), dp(12));
        b.setBackgroundResource(R.drawable.lumen_report_row_bg);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(v -> safe(action));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(16));
        buttons.addView(b, lp);
        return b;
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.w(TAG, "click: " + t);
        }
    }

    // ------------------------------------------------------------------ states
    private void render() {
        ReportState st = job.state();
        if (st == shown) {
            updateProgress();
            return;
        }
        shown = st;
        body.removeAllViews();
        buttons.removeAllViews();
        status = hint = null;
        bar = null;
        View focus;
        switch (st) {
            case COLLECTING:
            case UPLOADING:
                focus = buildProgress();
                break;
            case DONE:
                focus = buildDone();
                break;
            case QUEUED:
                focus = buildQueued();
                break;
            case FAILED:
                focus = buildFailed();
                break;
            default:
                focus = buildConsent();
                break;
        }
        scroll.scrollTo(0, 0);
        if (focus != null) focus.post(focus::requestFocus);
    }

    private View buildConsent() {
        title.setText(R.string.lumen_report_title);
        add(body, getString(R.string.lumen_report_intro), 18, DIM, false, 0);

        final LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        body.addView(cols, clp);
        column(cols, R.string.lumen_report_included, R.string.lumen_report_inc_system, R.string.lumen_report_inc_logs,
                R.string.lumen_report_inc_crashes, R.string.lumen_report_inc_kernel, R.string.lumen_report_inc_state);
        View gap = new View(this);
        cols.addView(gap, new LinearLayout.LayoutParams(dp(32), 1));
        column(cols, R.string.lumen_report_excluded, R.string.lumen_report_exc_accounts, R.string.lumen_report_exc_network,
                R.string.lumen_report_exc_serial, R.string.lumen_report_exc_apps, R.string.lumen_report_exc_files);

        add(body, getString(R.string.lumen_report_footer), 18, DIM, false, 12);
        autoRow();
        last(false, R.string.lumen_report_last);
        last(true, R.string.lumen_report_last_auto);

        button(R.string.lumen_report_send, () -> job.start(this));
        TextView cancel = button(R.string.lumen_report_cancel, this::finish);
        // Only when the text is taller than the screen (long languages): let D-pad UP reach it so the
        // ScrollView can show its top again. A block that fits never takes focus.
        cols.post(() -> {
            View content = scroll.getChildAt(0);
            if (content != null && content.getHeight() > scroll.getHeight()) {
                cols.setFocusable(true);
                cols.setBackgroundResource(R.drawable.lumen_report_row_bg);
            }
        });
        return cancel;
    }

    /** "Send problem reports automatically": one focusable row, OK flips the switch. */
    private void autoRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackgroundResource(R.drawable.lumen_report_row_bg);
        row.setFocusable(true);
        row.setClickable(true);
        TextView label = text(getString(R.string.lumen_report_auto), 20, TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(this);
        sw.setFocusable(false);
        sw.setClickable(false);
        sw.setChecked(ReportPrefs.autoOn(this));
        row.addView(sw);
        row.setOnClickListener(v -> safe(() -> {
            boolean on = !ReportPrefs.autoOn(this);
            ReportPrefs.setAutoOn(this, on);
            sw.setChecked(on);
        }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        body.addView(row, lp);
        add(body, getString(R.string.lumen_report_auto_note), 18, DIM, false, 4);
    }

    private void last(boolean automatic, int res) {
        String code = ReportPrefs.lastCode(this, automatic);
        long when = ReportPrefs.lastTime(this, automatic);
        if (code == null || when <= 0) return;
        String date = DateUtils.formatDateTime(this, when,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
        add(body, getString(res, code, date), 18, DIM, false, 6);
    }

    private void column(LinearLayout parent, int headerRes, int... items) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        parent.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        add(col, getString(headerRes), 20, ACCENT, true, 0);
        for (int r : items) add(col, IconText.bullet(getString(r), TEXT, dp(3), dp(12)), 18, TEXT, false, 4);
    }

    private View buildProgress() {
        title.setText(R.string.lumen_report_title);
        status = add(body, "", 22, TEXT, false, 8);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(1000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        lp.topMargin = dp(16);
        body.addView(bar, lp);
        hint = add(body, getString(R.string.lumen_report_log_hint), 18, DIM, false, 16);
        TextView cancel = button(R.string.lumen_report_cancel, () -> {
            job.cancel();
            finish();
        });
        updateProgress();
        return cancel;
    }

    private void updateProgress() {
        if (status == null || bar == null) return;
        if (job.state() == ReportState.COLLECTING) {
            int n = Math.max(1, job.steps());
            int s = Math.max(1, Math.min(job.step(), n));
            status.setText(getString(R.string.lumen_report_collecting, s, n));
            bar.setProgress(1000 * (s - 1) / n);
            hint.setVisibility(View.VISIBLE);
        } else {
            long t = Math.max(1, job.total());
            status.setText(getString(R.string.lumen_report_sending, Formatter.formatShortFileSize(this, job.sent()),
                    Formatter.formatShortFileSize(this, job.total())));
            bar.setProgress((int) (1000 * Math.min(job.sent(), t) / t));
            hint.setVisibility(View.GONE);
        }
    }

    private View buildDone() {
        title.setText(R.string.lumen_report_done_title);
        TextView code = add(body, job.code() == null ? "" : job.code(), 56, ACCENT, true, 12);
        code.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        add(body, getString(R.string.lumen_report_done_text), 20, TEXT, false, 12);
        add(body, getString(R.string.lumen_report_footer), 18, DIM, false, 12);
        return close();
    }

    private View buildQueued() {
        title.setText(R.string.lumen_report_queued_title);
        add(body, getString(R.string.lumen_report_queued_text), 20, TEXT, false, 12);
        return close();
    }

    private View buildFailed() {
        title.setText(R.string.lumen_report_failed_title);
        add(body, getString(failureText(job.failure())), 20, TEXT, false, 12);
        TextView retry = button(R.string.lumen_report_retry, () -> job.start(this));
        close();
        return retry;
    }

    private View close() {
        return button(R.string.lumen_report_close, () -> {
            job.reset();
            finish();
        });
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
