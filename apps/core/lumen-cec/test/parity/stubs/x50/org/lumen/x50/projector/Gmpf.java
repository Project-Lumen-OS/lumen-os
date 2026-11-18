package org.lumen.x50.projector;

import parity.World;

/** The X50 vendor HAL as the CEC page reaches it (CecHal only): every call logged, state in World.hal. */
final class Gmpf implements CecHal {
    @Override public boolean cecWakeUp() throws Exception {
        Boolean v = World.halCall("getCecWakeUpState", null);
        if (v == null) throw new Exception("getCecWakeUpState failed");
        return v;
    }

    @Override public boolean cecWakeUpSet(boolean on) throws Exception {
        if (World.halCall("setCecWakeUp", on) == null) throw new Exception("setCecWakeUp failed");
        World.hal.put("getCecWakeUpState", on);
        return true;
    }
}
