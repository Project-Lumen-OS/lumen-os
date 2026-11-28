// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * Play6MusicPlayerCallback: our Java xgimi.hardware.gmpf@1.0::IMusicPlayerCallback for IGmpf 215 / 227 on the
 * XGIMI Play 6 (stock parity: the "device closed / opened" event of the ambient speaker).
 *
 * musicplayerEvent = transaction 1: token, i32 type, i32 keycode, i32 status; reply Status(0) + bool(true), sent
 * at once, also on a parse error (BpHwMusicPlayerCallback::_hidl_musicplayerEvent of the stock v1.7.116 library
 * reads Status then a bool). Stock com.xgimi.xrmservice MachineStateService acts on type 4 only: status 1 = the
 * projector was closed with the slide function "ambient speaker" (IGmpf2 69 = 1), anything else = opened. The
 * vendor also reports its MCU body keys here (stock NewSounderMode HwDevice: 4 play / pause, 8 next); this app
 * only logs those. The callback never calls the HAL and never blocks; it hands the event to the Sink. The
 * IBase-reserved codes are answered like the hidl-gen Java IBase.Stub; getHashChain returns the
 * IMusicPlayerCallback hash recorded offline from both stock vendor libraries (stock analysis:
 * 56684d1e...d6fc, the same on v1.7.116 and v1.1.67) and the IBase hash as element 1.
 */
package org.lumen.projector.hal;

import android.os.HwParcel;
import android.util.Log;

public final class Play6MusicPlayerCallback extends HidlCallback {
    private static final String TAG = "P6Music";
    public static final String IMUSIC_PLAYER_CALLBACK = "xgimi.hardware.gmpf@1.0::IMusicPlayerCallback";

    static final int MUSIC_PLAYER_EVENT = 1;

    /** Recorded offline (stock analysis): IMusicPlayerCallback, then IBase. */
    private static final byte[] HASH_SELF = hex("56684d1e7f030fdde318d46504290bf35be646a5e3c71bbcf0144aea2e88d6fc");

    /** Receives music-player events. Called on the hwbinder thread AFTER the reply was sent: only post. */
    public interface Sink {
        void onMusicPlayerEvent(int type, int keycode, int status);
    }

    private final Sink sink;

    /** The caller keeps a strong reference (the HAL holds only a binder reference to this object). */
    public Play6MusicPlayerCallback(Sink sink) {
        super(TAG, IMUSIC_PLAYER_CALLBACK, HASH_SELF);
        this.sink = sink;
    }

    @Override
    public void onTransact(int code, HwParcel req, HwParcel reply, int flags) {
        if (code == MUSIC_PLAYER_EVENT) {
            int type = -1, keycode = -1, status = -1;
            boolean parsed = false;
            try {
                req.enforceInterface(IMUSIC_PLAYER_CALLBACK);
                type = req.readInt32();
                keycode = req.readInt32();
                status = req.readInt32();
                parsed = true;
            } catch (Throwable t) {
                Log.w(TAG, "musicplayerEvent parse: " + t);
            }
            // Reply at once, also on a parse error: the vendor waits for Status + bool.
            try {
                reply.writeStatus(HwParcel.STATUS_SUCCESS);
                reply.writeBool(true);
                reply.send();
            } catch (Throwable t) {
                Log.w(TAG, "musicplayerEvent reply: " + t);
            }
            if (parsed && sink != null) {
                try { sink.onMusicPlayerEvent(type, keycode, status); } catch (Throwable t) { Log.w(TAG, "sink: " + t); }
            }
            return;
        }
        answerIBase(code, req, reply);
    }
}
