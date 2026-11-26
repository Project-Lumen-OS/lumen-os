// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.media;

import org.lumen.hdmi.sim.Sim;

/** Host stub: the audio HAL keys go to {@link Sim#KEYS} with their virtual time. */
public class AudioManager {
    public void setParameters(String keyValuePairs) { Sim.key(keyValuePairs); }
}
