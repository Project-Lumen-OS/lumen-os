package android.media.tv;

import android.net.Uri;

public final class TvContract {
    private TvContract() { }
    public static Uri buildChannelUriForPassthroughInput(String inputId) {
        return Uri.parse("content://android.media.tv/passthrough/" + inputId);
    }
}
