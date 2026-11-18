package android.content;

import android.net.Uri;
import android.os.Bundle;
import parity.World;

public class ContentResolver {
    public Bundle call(Uri uri, String method, String arg, Bundle extras) { return World.providerCall(uri, method, arg, extras); }
}
