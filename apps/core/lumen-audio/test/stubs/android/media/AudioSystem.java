package android.media;

import java.util.ArrayList;
import java.util.List;

/** Host stub: records the policy indexes set. */
public final class AudioSystem {
    public static final List<String> INDEX = new ArrayList<>();

    private AudioSystem() {}

    public static int setStreamVolumeIndexAS(int stream, int index, int device) {
        INDEX.add("stream " + stream + " index " + index + " device 0x" + Integer.toHexString(device));
        return 0;
    }
}
