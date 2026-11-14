package org.lumen.projector.hal;

import android.os.IHwBinder;
import android.os.RemoteException;
import android.os.Wire;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * The members of the Play 6 gmpf clients by the key they had when the golden was recorded ("g." Play6Gmpf, "g2."
 * Play6Gmpf2, "name(Type,...)"), each called on the current clients: the migration table of the old Play 6 API onto
 * the shared one. A menu integer goes through Play6Values as in the app. A call the typed API cannot express (a null
 * callback: the client holds its own) is Unrepresentable; compare.py accepts it only where the golden sent nothing
 * and failed.
 */
final class P6Api {
    private interface Member { Object call(Object[] a) throws Exception; }
    private interface VoidCall { void run() throws Exception; }

    /** A call shape the typed API cannot express. */
    static final class Unrepresentable extends Exception {
        Unrepresentable(String m) { super(m); }
    }

    private static final Object FOCUS_CALLBACK = new Object();
    private static final TreeMap<String, Member> MEMBERS = new TreeMap<>();
    private static Play6Gmpf g;
    private static Play6Gmpf2 g2;

    private P6Api() {}

    private static void put(String key, Member m) { MEMBERS.put(key, m); }

    static void init() {
        g = new Play6Gmpf(proxy(FocusEventSink.class));
        g2 = new Play6Gmpf2();
        put("g.applyGameModeOption()", a -> g.applyGameModeOption());
        put("g.applyKeystonePoints(Play6KstPoint)", a -> g.applyKeystonePoints((Play6KstPoint) a[0]));
        put("g.autoKst()", a -> g.autoKst());
        put("g.checkKeystonePoints(Play6KstPoint)", a -> g.checkKeystonePoints((Play6KstPoint) a[0]));
        put("g.connect(DeathRecipient,long)", a -> g.connect((IHwBinder.DeathRecipient) a[0], (Long) a[1]));
        put("g.connectFocusManager(Play6FocusCallback)", a -> v(() -> { focus(a[0]); g.connectFocusManager(); }));
        put("g.connectMusicPlayer(Play6MusicPlayerCallback)", a -> v(() -> g.connectMusicPlayer((Play6MusicPlayerCallback) a[0])));
        put("g.connectPowerManager(Play6PowerCallback)", a -> v(() -> g.connectPowerManager((Play6PowerCallback) a[0])));
        put("g.getAllFeatureList()", a -> g.getAllFeatureList());
        put("g.getAspectRatio()", a -> g.getAspectRatioRaw());
        put("g.getAudioOutput()", a -> g.getAudioOutputRaw());
        put("g.getAutoProjection()", a -> g.getAutoReverse());
        put("g.getBatteryLevel()", a -> g.getBatteryLevel());
        put("g.getBlueGain()", a -> g.getWb(WbItem.BLUE_GAIN));
        put("g.getBlueOffset()", a -> g.getWb(WbItem.BLUE_OFFSET));
        put("g.getBootAnimState()", a -> g.getBootAnimState());
        put("g.getBootMusic()", a -> g.getPowerOnMusic());
        put("g.getBrightness()", a -> g.getPq(PqItem.BRIGHTNESS));
        put("g.getColorTemperature()", a -> g.getColorTemperature());
        put("g.getContrast()", a -> g.getPq(PqItem.CONTRAST));
        put("g.getCurrentInputSource()", a -> g.getCurrentInputSource());
        put("g.getDtsEffects()", a -> g.getDtsEffects());
        put("g.getEarc()", a -> g.getEarc());
        put("g.getEcoState()", a -> g.getEcoState());
        put("g.getEdidVersion()", a -> g.getEdidVersion());
        put("g.getEyeProtection()", a -> g.getEyeProtection());
        put("g.getGameModeOption()", a -> g.getGameModeOptionRaw());
        put("g.getGreenGain()", a -> g.getWb(WbItem.GREEN_GAIN));
        put("g.getGreenOffset()", a -> g.getWb(WbItem.GREEN_OFFSET));
        put("g.getHdmiAutoSwitch()", a -> g.getHdmiAutoSwitch());
        put("g.getHdmiInfo()", a -> g.getHdmiInfo());
        put("g.getHdmiRange()", a -> g.getHdmiRange());
        put("g.getHdrAuto()", a -> g.getHdrAuto());
        put("g.getHdrType()", a -> g.getHdrType());
        put("g.getKeystoneMode()", a -> g.getKeystoneMode());
        put("g.getKeystonePoints()", a -> g.getKeystonePoints());
        put("g.getLampLevel()", a -> g.getLampLevel());
        put("g.getLocalContrast()", a -> g.getLocalContrast());
        put("g.getMemcLevel()", a -> g.getMemcLevel());
        put("g.getMoveAf()", a -> g.getMoveAf());
        put("g.getMoveAk()", a -> g.getMoveAk());
        put("g.getNoiseReduction()", a -> g.getNoiseReduction());
        put("g.getPictureMode()", a -> g.getPictureModeRaw());
        put("g.getPictureStyle()", a -> g.getLumensModeRaw());
        put("g.getPowerOnAk()", a -> g.getPowerOnAk());
        put("g.getPutMode()", a -> g.getPutModeRaw());
        put("g.getRedGain()", a -> g.getWb(WbItem.RED_GAIN));
        put("g.getRedOffset()", a -> g.getWb(WbItem.RED_OFFSET));
        put("g.getRgbLevels()", a -> g.getRgbLevels());
        put("g.getSaturation()", a -> g.getPq(PqItem.SATURATION));
        put("g.getScreenOnOff()", a -> g.getScreenOn());
        put("g.getSharpness()", a -> g.getPq(PqItem.SHARPNESS));
        put("g.getSignalStatus()", a -> g.getSignalStatus());
        put("g.getSoundEffect()", a -> g.getSoundEffectRaw());
        put("g.getSoundProcess()", a -> g.getSoundProcessRaw());
        put("g.getSystemTemperature()", a -> g.getSystemTemperature());
        put("g.getSystemUiState()", a -> g.getSystemUiState());
        put("g.getZoomStep()", a -> g.getZoomStep());
        put("g.getZoomStepRange()", a -> g.getZoomStepRange());
        put("g.goToBootCompleted(int)", a -> g.goToBootCompleted(Play6Values.bootStage(i(a, 0))));
        put("g.isAdapterPowered()", a -> g.isAdapterPowered());
        put("g.isBatteryFull()", a -> g.isBatteryFull());
        put("g.isConnected()", a -> g.isConnected());
        put("g.manualFocus(int)", a -> v(() -> g.manualFocus(Play6Values.manualFocus(i(a, 0)))));
        put("g.nuiReaderReady()", a -> Play6Gmpf.nuiReaderReady());
        put("g.requestAutofocus()", a -> v(() -> g.sendRemoteAutofocus()));
        put("g.resetCustomPicture()", a -> g.resetCustomPicture());
        put("g.resetKeystone()", a -> g.resetKeystone());
        put("g.restoreEyeProtectionScreen()", a -> g.restoreEyeProtectionScreen());
        put("g.sendPowerOnUiReady()", a -> v(() -> g.sendPowerOnUiReady()));
        put("g.setAspectRatio(int)", a -> g.setAspectRatio(Play6Values.aspectRatio(i(a, 0))));
        put("g.setAudioOutput(int)", a -> v(() -> g.setAudioOutput(Play6Values.audioPath(i(a, 0)))));
        put("g.setAutoProjection(boolean)", a -> g.setAutoReverse(b(a, 0)));
        put("g.setBlueGain(int)", a -> g.setWb(WbItem.BLUE_GAIN, i(a, 0)));
        put("g.setBlueOffset(int)", a -> g.setWb(WbItem.BLUE_OFFSET, i(a, 0)));
        put("g.setBootAnimFinished()", a -> v(() -> g.setBootAnimFinished()));
        put("g.setBootMusic(boolean)", a -> v(() -> g.setPowerOnMusic(b(a, 0))));
        put("g.setBrightness(int)", a -> g.setPq(PqItem.BRIGHTNESS, i(a, 0)));
        put("g.setColorTemperature(int)", a -> g.setColorTemperature(i(a, 0)));
        put("g.setContrast(int)", a -> g.setPq(PqItem.CONTRAST, i(a, 0)));
        put("g.setDigitalZoomStep(int)", a -> g.setDigitalZoomStep(i(a, 0)));
        put("g.setEarc(boolean)", a -> g.setEarc(b(a, 0)));
        put("g.setEcoState(boolean)", a -> v(() -> g.setEcoState(b(a, 0))));
        put("g.setEdidVersion(int)", a -> g.setEdidVersion(i(a, 0)));
        put("g.setEyeProtection(boolean)", a -> g.setEyeProtection(b(a, 0)));
        put("g.setFocusListener(Play6FocusCallback)", a -> v(() -> { focus(a[0]); g.setFocusListener(); }));
        put("g.setFullDisplayArea()", a -> g.setFullDisplayArea());
        put("g.setGreenGain(int)", a -> g.setWb(WbItem.GREEN_GAIN, i(a, 0)));
        put("g.setGreenOffset(int)", a -> g.setWb(WbItem.GREEN_OFFSET, i(a, 0)));
        put("g.setHdmiRange(int)", a -> g.setHdmiRange(Play6Values.hdmiRange(i(a, 0))));
        put("g.setHdrAuto(boolean)", a -> g.setHdrAuto(b(a, 0)));
        put("g.setLampLevel(int)", a -> v(() -> g.setLampLevel(i(a, 0))));
        put("g.setLocalContrast(int)", a -> g.setLocalContrast(i(a, 0)));
        put("g.setMemcLevel(int)", a -> g.setMemcLevel(Play6Values.memc(i(a, 0))));
        put("g.setMoveAf(boolean)", a -> g.setMoveAf(b(a, 0)));
        put("g.setMoveAk(boolean)", a -> g.setMoveAk(b(a, 0)));
        put("g.setMusicPlayerListener(Play6MusicPlayerCallback)", a -> v(() -> g.setMusicPlayerListener((Play6MusicPlayerCallback) a[0])));
        put("g.setNoiseReduction(int)", a -> g.setNoiseReduction(Play6Values.noiseReduction(i(a, 0))));
        put("g.setPeqBand(int,int,int,int)", a -> g.setPeqBand(i(a, 0), i(a, 1), i(a, 2), i(a, 3)));
        put("g.setPeqEnabled(boolean)", a -> g.setPeqEnabled(b(a, 0)));
        put("g.setPictureMode(int)", a -> g.setPictureMode(Play6Values.pictureMode(i(a, 0))));
        put("g.setPictureStyle(int)", a -> v(() -> g.setColorTemp(Play6Values.pictureStyle(i(a, 0)))));
        put("g.setPowerListener(Play6PowerCallback)", a -> v(() -> g.setPowerListener((Play6PowerCallback) a[0])));
        put("g.setPowerOnAk(boolean)", a -> v(() -> g.setPowerOnAk(b(a, 0))));
        put("g.setPutMode(int)", a -> v(() -> g.setPutMode(Play6Values.putMode(i(a, 0)))));
        put("g.setRealtimeAkRunning(boolean)", a -> g.setRealtimeAkTemporarily(b(a, 0)));
        put("g.setRedGain(int)", a -> g.setWb(WbItem.RED_GAIN, i(a, 0)));
        put("g.setRedOffset(int)", a -> g.setWb(WbItem.RED_OFFSET, i(a, 0)));
        put("g.setRgbLevels(int,int,int)", a -> v(() -> g.setRgbLevels(i(a, 0), i(a, 1), i(a, 2))));
        put("g.setSaturation(int)", a -> g.setPq(PqItem.SATURATION, i(a, 0)));
        put("g.setScreenOnOff(boolean)", a -> v(() -> g.setScreenOn(b(a, 0))));
        put("g.setSharpness(int)", a -> g.setPq(PqItem.SHARPNESS, i(a, 0)));
        put("g.setSoundEffect(int)", a -> v(() -> g.setSoundEffect(Play6Values.soundEffect(i(a, 0)))));
        put("g.setSystemUiReady()", a -> v(() -> g.setSystemUiReady()));
        put("g.shiftPicture(int,boolean)", a -> g.shiftPicture(Play6Values.shift(i(a, 0)), b(a, 1)));
        put("g.switchInputSource(int)", a -> g.switchInputSource(i(a, 0)));
        put("g.zoomSessionFinish()", a -> g.zoomSessionFinish());
        put("g.zoomSessionStart()", a -> g.zoomSessionStart());
        put("g2.checkRotationStep(String,float)", a -> Play6Gmpf2.checkRotationStep((String) a[0], (Float) a[1]));
        put("g2.dropConnection()", a -> v(() -> g2.dropConnection()));
        put("g2.getAllmAutoSwitch()", a -> g2.getAllmAutoSwitch());
        put("g2.getAllmStatus()", a -> g2.getAllmStatus());
        put("g2.getAmbientLightSupported()", a -> g2.getAmbientLightSupported());
        put("g2.getAutoEcoMode()", a -> g2.getAutoEcoMode());
        put("g2.getConvexLensType()", a -> g2.getConvexLensType());
        put("g2.getDynamicContrast()", a -> g2.getDynamicContrast());
        put("g2.getGameModeProp()", a -> g2.getGameModeProp());
        put("g2.getGamma()", a -> g2.getGamma());
        put("g2.getPowerSourceType()", a -> g2.getPowerSourceType());
        put("g2.getPowerSupplyVoltage()", a -> g2.getPowerSupplyVoltage());
        put("g2.getScreenRotation()", a -> g2.getScreenRotation());
        put("g2.getSlideFunction()", a -> g2.getSlideFunction());
        put("g2.getSmartAutofocus()", a -> g2.getSmartAutofocus());
        put("g2.getStandBatteryLevel()", a -> g2.getStandBatteryLevel());
        put("g2.getStandVerticalAngle()", a -> g2.getStandVerticalAngle());
        put("g2.isConnected()", a -> g2.isConnected());
        put("g2.isConvexLensIn()", a -> g2.isConvexLensIn());
        put("g2.isIdentityOpen()", a -> g2.isIdentityOpen());
        put("g2.liveHashChain()", a -> g2.liveHashChain());
        put("g2.recordedVendor(String)", a -> Play6Gmpf2.recordedVendor((String) a[0]));
        put("g2.rotateScreen(boolean,float)", a -> g2.rotateScreen(b(a, 0), (Float) a[1]));
        put("g2.setDynamicContrast(boolean)", a -> g2.setDynamicContrast(b(a, 0)));
        put("g2.setGameModeState(boolean)", a -> g2.setGameModeState(b(a, 0)));
        put("g2.setGameModeType(int)", a -> g2.setGameModeType(Play6Values.gameModeType(i(a, 0))));
        put("g2.setGamma(int)", a -> g2.setGamma(i(a, 0)));
        put("g2.setPushPullScreen(boolean)", a -> g2.setAdjustScreenOpen(b(a, 0)));
        put("g2.setSlideFunction(int)", a -> g2.setSlideFunction(i(a, 0)));
        put("g2.setSmartAutofocus(boolean)", a -> g2.setSmartAutofocus(b(a, 0)));
        put("g2.verifyIdentity()", a -> g2.verifyIdentity());
    }

    private static int i(Object[] a, int n) { return (Integer) a[n]; }

    private static boolean b(Object[] a, int n) { return (Boolean) a[n]; }

    private static Object v(VoidCall c) throws Exception {
        c.run();
        return null;
    }

    /** The focus callback is the client's own object: the old null argument cannot be passed. */
    private static void focus(Object cb) throws Unrepresentable {
        if (cb == null) throw new Unrepresentable("no callback argument: the client registers its own");
    }

    static List<String> keys() { return new ArrayList<>(MEMBERS.keySet()); }

    static Object call(String key, Object... args) throws Exception {
        Member m = MEMBERS.get(key);
        if (m == null) throw new NoSuchMethodException(key);
        return m.call(args);
    }

    static Object deathRecipient() { return proxy(IHwBinder.DeathRecipient.class); }

    /** The argument values of the client types. */
    static Object[] values(String type) {
        switch (type) {
            case "Play6KstPoint": {
                Play6KstPoint full = new Play6KstPoint().set(Play6KstPoint.TL, 0, 0).set(Play6KstPoint.TR, 1920, 0)
                        .set(Play6KstPoint.BR, 1920, 1080).set(Play6KstPoint.BL, 0, 1080);
                return new Object[] {full, full.copy().set(Play6KstPoint.TL, 100, 120), null};
            }
            case "Play6FocusCallback": return new Object[] {FOCUS_CALLBACK, null};
            case "Play6PowerCallback": return new Object[] {new Play6PowerCallback(proxy(Play6PowerCallback.Sink.class)), null};
            case "Play6MusicPlayerCallback":
                return new Object[] {new Play6MusicPlayerCallback(proxy(Play6MusicPlayerCallback.Sink.class)), null};
            default: throw new IllegalStateException("no values for " + type);
        }
    }

    static String show(Object v) {
        if (v instanceof IHwBinder || v == FOCUS_CALLBACK) return "cb";
        if (v instanceof IHwBinder.DeathRecipient) return "death";
        return String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface) {
        return (T) Proxy.newProxyInstance(P6Api.class.getClassLoader(), new Class<?>[] {iface}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return m.getName().equals("hashCode") ? 7 : m.getName().equals("toString") ? "proxy" : null;
            Wire.log("EVENT " + m.getName() + (a == null ? "" : Arrays.toString(a)));
            return null;
        });
    }
}
