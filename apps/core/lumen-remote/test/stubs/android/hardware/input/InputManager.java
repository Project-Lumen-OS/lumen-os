package android.hardware.input;
public class InputManager {
    public int[] getInputDeviceIds() { return lumen.fake.World.inputIds(); }
    public android.view.InputDevice getInputDevice(int id) { return lumen.fake.World.input(id); }
}
