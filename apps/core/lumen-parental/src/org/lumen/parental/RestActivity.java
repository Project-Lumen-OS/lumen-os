// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The stock time limit screen (TimeLimitActivity: 休息一会再回来吧, 解锁继续观看): a session over its limit asks for a
 * 10 minute break, the day's time ends viewing; the parent's password lets the child go on.
 */
public final class RestActivity extends Activity {
    private static final int ASK_PIN = 1;

    static void show(Context c, LimitState st) {
        c.startActivity(new Intent(c, RestActivity.class).putExtra("daily", st == LimitState.DAILY_DONE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean daily = getIntent().getBooleanExtra("daily", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Look.BG);
        root.addView(Look.text(this, getString(R.string.lumen_lp_rest_title), 30, Look.TEXT, true),
                new LinearLayout.LayoutParams(-2, -2));
        TextView line = Look.text(this, getString(daily ? R.string.lumen_lp_rest_daily : R.string.lumen_lp_rest_session), 16, Look.SUB, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Look.dp(this, 12);
        lp.bottomMargin = Look.dp(this, 30);
        root.addView(line, lp);
        TextView unlock = Look.button(this, getString(R.string.lumen_lp_rest_unlock));
        unlock.setOnClickListener(v -> startActivityForResult(PinActivity.forResult(this, PinActivity.VERIFY), ASK_PIN));
        root.addView(unlock, new LinearLayout.LayoutParams(-2, -2));
        setContentView(root);
        unlock.requestFocus();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != ASK_PIN || res != RESULT_OK) return;
        ParentalState s = Parental.load(this);
        ParentalRules.unlockLimit(s, System.currentTimeMillis());
        Parental.save(this, s);
        finish();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) return true;          // the break holds until it ends or the parent unlocks
        return super.onKeyDown(code, e);
    }
}
