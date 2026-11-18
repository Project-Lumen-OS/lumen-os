package android.media;

import java.util.ArrayList;
import java.util.List;

/** Host stub: records the HAL parameters in order (the audio HAL keeps the last switch_end). */
public class AudioManager {
    public static final int STREAM_VOICE_CALL = 0, STREAM_SYSTEM = 1, STREAM_RING = 2, STREAM_MUSIC = 3, STREAM_ALARM = 4,
            STREAM_NOTIFICATION = 5, STREAM_BLUETOOTH_SCO = 6, STREAM_SYSTEM_ENFORCED = 7, STREAM_DTMF = 8, STREAM_TTS = 9,
            STREAM_ACCESSIBILITY = 10;
    public boolean systemMuted;

    public int getStreamMaxVolume(int stream) { return 100; }
    public boolean isStreamMute(int stream) { return stream == STREAM_SYSTEM && systemMuted; }
    public final List<String> sent = new ArrayList<>();

    public void setParameters(String kv) { sent.add(kv); }

    /** The HAL sink after everything sent: the last switch_end, -1 none. */
    public int sink() {
        int s = -1;
        for (String kv : sent) if (kv.startsWith("xgimi_audiopath_switch_end=")) s = Integer.parseInt(kv.substring(27));
        return s;
    }
}
