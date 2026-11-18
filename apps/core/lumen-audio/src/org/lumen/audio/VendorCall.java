// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

/** Work for a model's vendor HAL thread (VendorAudio.post). */
public interface VendorCall {
    void run(VendorOutput out) throws Exception;
}
