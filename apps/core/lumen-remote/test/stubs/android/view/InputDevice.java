package android.view;
public class InputDevice {
    public final String name, address; public final int vendor, product; public final boolean external;
    public InputDevice(String name, String address, int vendor, int product, boolean external) {
        this.name = name; this.address = address; this.vendor = vendor; this.product = product; this.external = external; }
    public String getName() { return name; }
    public String getBluetoothAddress() { return address; }
    public int getVendorId() { return vendor; }
    public int getProductId() { return product; }
    public boolean isExternal() { return external; }
}
