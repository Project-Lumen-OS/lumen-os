package android.media;

/** Host stub. */
public final class AudioDeviceAttributes {
    public static final int ROLE_OUTPUT = 2;
    public final int role, type;
    public final String address;

    public AudioDeviceAttributes(int role, int type, String address) {
        this.role = role;
        this.type = type;
        this.address = address;
    }

    /** AudioDeviceInfo.convertDeviceTypeToInternalDevice for the types the chip tables list. */
    public int getInternalType() {
        switch (type) {
            case 2: return 0x2;
            case 3: return 0x4;
            case 4: return 0x8;
            case 8: return 0x80;
            case 6: return 0x80000;
            case 11: return 0x4000;
            case 22: return 0x4000000;
            default: return 0;
        }
    }
}
