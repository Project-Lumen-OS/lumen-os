// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.vcontrol;

/** The ports a {@link VcontrolServer} uses; {@link #STOCK} on a projector, free ports in tests. */
public final class Endpoints {
    public static final Endpoints STOCK = new Endpoints(Ports.DISCOVERY, Ports.COMMANDS, Ports.PHONE, Ports.TCP);

    public final int discovery;
    public final int commands;
    public final int phone;
    public final int tcp;

    public Endpoints(int discovery, int commands, int phone, int tcp) {
        this.discovery = discovery;
        this.commands = commands;
        this.phone = phone;
        this.tcp = tcp;
    }
}
