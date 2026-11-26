// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.fakewn;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/** The player a Continue watching card opens: in front means the card started playback. */
public class FakeWnPlayer extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int film = getIntent().getIntExtra("film", -1);
        Log.i("fakewn", "play film=" + film);
        TextView t = new TextView(this);
        t.setText("fake player, film " + film);
        t.setTextSize(40);
        setContentView(t);
    }
}
