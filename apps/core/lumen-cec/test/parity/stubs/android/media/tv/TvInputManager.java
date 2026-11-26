// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.media.tv;

import java.util.List;
import parity.World;

public class TvInputManager {
    public List<TvInputInfo> getTvInputList() { World.event("tim.getTvInputList()"); return World.inputs; }
}
