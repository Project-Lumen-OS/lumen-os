// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

/** Work for a model's vendor HAL thread (VendorAudio.post). */
public interface VendorCall {
    void run(VendorOutput out) throws Exception;
}
