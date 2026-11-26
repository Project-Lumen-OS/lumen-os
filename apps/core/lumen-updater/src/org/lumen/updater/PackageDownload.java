// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import org.lumen.download.HttpError;
import org.lumen.download.ParallelDownload;

import java.io.File;
import java.io.IOException;

/**
 * The network half of UpdateService's download, without Android: wait for the network, run the ParallelDownload,
 * try again after a growing pause (a file the server no longer has, 404 or 410, ends it), then check the whole file
 * against the manifest's sha256. After a mismatch the file is deleted and downloaded once more from the start
 * before the download counts as broken.
 */
final class PackageDownload {
    static final int HASH_RETRIES = 1;

    interface Host {
        boolean online();

        boolean stopRequested();

        void sleep(long ms);

        /** No network: have of total bytes are on disk. */
        void offline(long have, long total);

        /** @return false to stop */
        boolean progress(long bytes, long total, long speed);

        /** The whole file has the manifest's sha256 (the same check a file on the USB stick gets). */
        boolean verified(File f) throws IOException;
    }

    /** The file still differs from the manifest after the retries. */
    static final class HashMismatch extends IOException {
        HashMismatch() { super("sha256 mismatch"); }
    }

    private final ParallelDownload net;
    private final Host host;

    PackageDownload(ParallelDownload net, Host host) {
        this.net = net;
        this.host = host;
    }

    /**
     * @param skipComplete a complete file goes to the check even without network (InstallKind.skipsCompleteFile)
     * @return the ETag of a verified file, null when stopped
     */
    String fetch(String url, File dst, long size, String sha256, boolean skipComplete) throws IOException {
        int attempt = 0;
        for (int pass = 0; ; pass++) {
            String tag = "";
            while (!(skipComplete && dst.isFile() && dst.length() == size)) {
                if (host.stopRequested()) return null;
                if (!host.online()) {
                    host.offline(ParallelDownload.have(dst, size), size);
                    host.sleep(5000);
                    continue;
                }
                try {
                    tag = net.run(url, dst, size, sha256, host::progress);
                    break;
                } catch (ParallelDownload.Stopped e) {
                    return null;
                } catch (HttpError e) {
                    if (e.code == 404 || e.code == 410) throw e;
                    attempt++;
                } catch (IOException e) {
                    attempt++;
                }
                host.sleep(Math.min(60000, 5000L * attempt));
            }
            if (host.stopRequested()) return null;
            if (host.verified(dst)) return tag;
            ParallelDownload.discard(dst);
            if (pass >= HASH_RETRIES) throw new HashMismatch();
        }
    }
}
