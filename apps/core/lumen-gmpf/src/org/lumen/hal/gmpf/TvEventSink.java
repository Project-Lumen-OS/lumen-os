// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hal.gmpf;

/**
 * Receives the GmTvManager events of the vendor (IGmTvCallback.gmTvEvent: type, message) on a binder thread. Must only
 * post: the vendor waits for the reply of the HIDL callback while it holds its listener lock.
 */
public interface TvEventSink {
    void onTvEvent(int type, String msg);
}
