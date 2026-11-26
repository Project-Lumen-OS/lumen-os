// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.android.tv.settings.accessories.BluetoothA2dpConnector;
import com.android.tv.settings.accessories.BluetoothDevicePairer;

import lumen.a2dp.World;

/**
 * Add accessory, A2DP part: what BluetoothA2dpConnector reports to BluetoothDevicePairer ("succeeded" = the screen
 * says Paired, "failed" = Couldn't pair) for each order of events after the bond. Time 0 = the bond is made and
 * the pairer opens the connection. One line per scenario: the policy calls and the report, with their times.
 */
public final class A2dpRaceTest {
    private static final BluetoothDevice SPEAKER = new BluetoothDevice("<mac>");
    private static final BluetoothDevice OTHER = new BluetoothDevice("<mac>");

    public static void main(String[] args) {
        // HB 2026-11-14 22:31 (UGREEN Studio Max5s): the speaker's A2DP link is up 0.9 s after the bond, before the
        // connector's proxy arrives and starts listening
        run("S1 speaker_first", () -> {
            World.proxyDelay = 1200;
            World.at(300, World.a2dp(SPEAKER, STATE_DISCONNECTED, STATE_CONNECTING));
            World.at(900, World.a2dp(SPEAKER, STATE_CONNECTING, STATE_CONNECTED));
        });
        // an incoming connection accepted from Disconnected: one broadcast Disconnected -> Connected
        run("S2 incoming_direct", () -> World.at(800, World.a2dp(SPEAKER, STATE_DISCONNECTED, STATE_CONNECTED)));
        // the connector's own policy call connects the speaker
        run("S3 normal", () -> World.onPolicy = d -> {
            World.at(World.now() + 100, World.a2dp(d, STATE_DISCONNECTED, STATE_CONNECTING));
            World.at(World.now() + 1100, World.a2dp(d, STATE_CONNECTING, STATE_CONNECTED));
        });
        // the speaker refuses the connection: a real failure
        run("S4 refused", () -> World.onPolicy = d -> {
            World.at(World.now() + 100, World.a2dp(d, STATE_DISCONNECTED, STATE_CONNECTING));
            World.at(World.now() + 1600, World.a2dp(d, STATE_CONNECTING, STATE_DISCONNECTED));
        });
        // the speaker never answers: a real failure
        run("S5 silent", () -> { });
        run("S6 no_proxy", () -> World.proxyOk = false);
        // another speaker connects meanwhile, the target never does: a real failure
        run("S7 other_device", () -> World.at(800, World.a2dp(OTHER, STATE_DISCONNECTED, STATE_CONNECTED)));
        // the proxy arrives while the link is still coming up: both versions see Connecting -> Connected
        run("S8 proxy_between", () -> {
            World.proxyDelay = 500;
            World.at(300, World.a2dp(SPEAKER, STATE_DISCONNECTED, STATE_CONNECTING));
            World.at(900, World.a2dp(SPEAKER, STATE_CONNECTING, STATE_CONNECTED));
        });
    }

    private static void run(String name, Runnable setup) {
        World.reset();
        setup.run();
        BluetoothA2dpConnector c = new BluetoothA2dpConnector(new Context(), SPEAKER,
                new BluetoothDevicePairer.OpenConnectionCallback() {
                    @Override public void succeeded() {
                        World.event("succeeded");
                    }

                    @Override public void failed() {
                        World.event("failed");
                    }
                });
        World.at(0, () -> c.openConnection(BluetoothAdapter.getDefaultAdapter()));
        World.run(30_000);
        System.out.println(name + ": " + World.trace());
    }
}
