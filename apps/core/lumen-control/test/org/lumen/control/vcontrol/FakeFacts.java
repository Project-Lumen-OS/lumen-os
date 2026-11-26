// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.vcontrol;

/** A projector with fixed facts. */
final class FakeFacts implements Facts {
    String name = "XGIMI X50 Ultra";

    @Override public String ip() { return "<lan-ip>"; }
    @Override public String name() { return name; }
    @Override public String productName() { return "Product_dianapro"; }
    @Override public String pid() { return "0123456789ABCDEF"; }
    @Override public String bluetoothMac() { return null; }
    @Override public String mac() { return ""; }
}
