// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The one way into the queue and out to the server, for manual and automatic reports alike. Synchronized: the
 * manual report's thread and the job service never touch the queue at the same time. Worker threads only.
 */
final class ReportSender {
    private static final String TAG = ReportConfig.TAG;

    private ReportSender() {}

    /** Takes a finished zip into the queue (manual: after a failed direct upload; automatic: always). */
    static synchronized void enqueue(Context c, File zip, ReportKind kind) throws IOException {
        ReportPrefs.queue(c).add(zip, kind, System.currentTimeMillis());
    }

    static synchronized int queued(Context c) {
        return ReportPrefs.queue(c).size();
    }

    /**
     * Sends every due report, oldest first. A failure that may pass (network, server, too many reports) keeps the
     * report with a longer wait, a refusal drops it. Automatic reports of a switch turned off meanwhile are dropped
     * unsent. Returns the wait until the next report is due, or -1 when the queue is empty.
     */
    static synchronized long sendDue(Context c) {
        ReportQueue q = ReportPrefs.queue(c);
        List<String> urls = ReportConfig.urls();
        long now = System.currentTimeMillis();
        for (ReportQueue.Entry e : q.due(now)) {
            if (urls.isEmpty() || (e.kind.automatic() && !ReportPrefs.autoOn(c))) {
                q.remove(e.id);
                continue;
            }
            try {
                String id = ReportPrefs.installId(c), version = ReportConfig.prop("ro.lumen.version");
                String code = ReportUploader.big(e.zip)
                        ? new ReportUploader().parts(urls, e.zip, id, version, model(), e.kind, (s, t) -> true)
                        : ReportUploader.uploadAny(urls, u -> new ReportUploader().upload(u, e.zip, id, version, model(),
                                e.kind, (s, t) -> true));
                ReportPrefs.setLast(c, e.kind, code, System.currentTimeMillis());
                q.remove(e.id);
                Log.i(TAG, e.kind.wire + " report sent: " + code);
            } catch (ReportUploader.UploadException x) {
                Log.w(TAG, e.kind.wire + " report not sent (" + x.failure + "): " + x.getMessage());
                try {
                    if (x.failure.retry()) q.failed(e, now);
                    else q.remove(e.id);
                } catch (IOException io) {
                    q.remove(e.id);
                }
                if (x.failure == ReportFailure.NETWORK || x.failure == ReportFailure.NO_NETWORK) break;
            } catch (IOException x) {
                Log.w(TAG, e.kind.wire + " report not sent: " + x);
                try {
                    q.failed(e, now);
                } catch (IOException io) {
                    q.remove(e.id);
                }
                break;
            }
        }
        return q.nextDueIn(System.currentTimeMillis());
    }

    /** The model as the server keys it: the XGIMI model code in lower case (g0082, g0111), else the device. */
    static String model() {
        String m = ReportConfig.prop("ro.boot.xgimi.modelname").trim().toLowerCase(java.util.Locale.ROOT);
        if (m.isEmpty()) m = ReportConfig.prop("ro.product.device").trim().toLowerCase(java.util.Locale.ROOT);
        return ReportUploader.headerModel(m);
    }
}
