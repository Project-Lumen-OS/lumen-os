package org.lumen.projector.audio;

/** Stock wire values of digital_output_format (MtkAudio.setAudioDataFormatAndNoSave). UI order. */
public enum DigitalOutputFormat {
    AUTO("auto"), PCM("pcm"), PASSTHROUGH("bypass");
    public final String wire;
    DigitalOutputFormat(String w) { wire = w; }
}
