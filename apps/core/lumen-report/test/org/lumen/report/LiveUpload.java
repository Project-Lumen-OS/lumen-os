// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.io.File;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Live check of the real upload path from a host on the network in question: sends one zip of the given size the way
 * the projector does and prints how it ended and how long it took. Not part of run.sh.
 *     sh apps/core/lumen-report/test/live_upload.sh KB URL [URL...]
 * The zip says it is a test; kind "manual" is stored by the server, so delete the code it prints (reports.sh rm).
 */
public final class LiveUpload {
    public static void main(String[] a) throws Exception {
        int kb = Integer.parseInt(a[0]);
        File zip = File.createTempFile("live", ".zip");
        zip.deleteOnExit();
        byte[] noise = new byte[kb * 1024];
        new SecureRandom().nextBytes(noise);
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("summary.txt"));
            z.write("LIVE UPLOAD TEST of apps/core/lumen-report (not a projector). Delete me.\n".getBytes("UTF-8"));
            z.putNextEntry(new ZipEntry("noise.bin"));
            z.write(noise);
        }
        String id = String.format("%024x", new java.math.BigInteger(1, Arrays.copyOf(noise, 12)));
        long t0 = System.nanoTime();
        try {
            String code = ReportUploader.uploadAny(Arrays.asList(Arrays.copyOfRange(a, 1, a.length)), url ->
                    new ReportUploader().upload(url, zip, id, "TEST-live", "test_probe", ReportKind.MANUAL,
                            (s, t) -> true));
            System.out.printf("SENT %s (%d bytes) in %.1f s%n", code, zip.length(), (System.nanoTime() - t0) / 1e9);
        } catch (ReportUploader.UploadException e) {
            System.out.printf("FAILED %s: %s after %.1f s%n", e.failure, e.getMessage(), (System.nanoTime() - t0) / 1e9);
        }
    }
}
