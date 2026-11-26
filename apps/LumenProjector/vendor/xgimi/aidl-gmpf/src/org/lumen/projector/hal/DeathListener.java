// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.hal;

/** The HB vendor process died (all 14 services at once). Called once per death, on a binder thread: only post. */
public interface DeathListener {
    void onVendorDied();
}
