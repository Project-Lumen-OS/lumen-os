package org.lumen.projector.audio;

/** What HdmiVolume does after a change of the routed or the confirmed vendor path, HDMI picture or not. */
public enum HdmiVolumeStep {
    /** The speaker is both routed and confirmed: spk_volume follows STREAM_MUSIC. */
    FOLLOW,
    /** The output is not (or not yet) the confirmed speaker: send nothing (never on ARC). */
    HOLD
}
