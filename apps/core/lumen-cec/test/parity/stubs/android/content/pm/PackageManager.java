package android.content.pm;

import parity.World;

public class PackageManager {
    public ProviderInfo resolveContentProvider(String authority, int flags) {
        return World.providers.contains(authority) ? new ProviderInfo() : null;
    }
}
