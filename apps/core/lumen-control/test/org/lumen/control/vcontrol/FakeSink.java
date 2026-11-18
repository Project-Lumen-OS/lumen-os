// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import java.util.ArrayList;
import java.util.List;
import org.lumen.control.Command;
import org.lumen.control.ControlSink;

/** Records the commands; answers ok unless told otherwise. */
final class FakeSink implements ControlSink {
    final List<String> ran = new ArrayList<>();
    boolean ok = true;

    @Override public synchronized boolean run(Command c) {
        ran.add(c.toString());
        return ok;
    }

    @Override public int[] volume() {
        return new int[] {15, 7};
    }

    synchronized String all() {
        return String.join(";", ran);
    }
}
