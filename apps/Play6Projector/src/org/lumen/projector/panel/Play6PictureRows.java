package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Values;
import org.lumen.projector.hal.PqItem;

import java.util.function.Consumer;

/** Light level, picture mode, the Custom picture items, colour temperature, MEMC, dynamic contrast, picture reset. */
final class Play6PictureRows extends Play6Rows {
    /** Custom picture item range (stock 0..100). */
    private static final int PQ_MIN = 0, PQ_MAX = 100;

    Play6PictureRows(Play6Features menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        switch (feature) {
            case LAMP: {
                int v = Integer.parseInt(value.trim());
                if (!Play6MenuGates.lampValue(v)) throw new IllegalArgumentException("lamp " + v);
                g.setLampLevel(v);                                                   // 176
                return null;
            }
            case PICTURE_COLOR_TEMP: {
                Integer w = Play6MenuGates.COLOR_TEMP.wire(value);
                if (w == null) throw new IllegalArgumentException("colour temperature " + value);
                if (!Play6MenuGates.custom(g.getPictureModeRaw())) return "";
                g.setColorTemperature(w);                                            // 685
                return null;
            }
            case PICTURE_MEMC: {
                Integer w = Play6MenuGates.MEMC.wire(value);
                if (w == null) throw new IllegalArgumentException("memc " + value);
                if (!Play6MenuGates.memcMode(g.getPictureModeRaw())) return "";
                if (!g.setMemcLevel(Play6Values.memc(w))) throw new IllegalStateException("669 returned false");
                return null;
            }
            case PICTURE_DYNAMIC_CONTRAST:
                if (!g2.isIdentityOpen() || !Play6MenuGates.custom(g.getPictureModeRaw())) return "";
                g2.setDynamicContrast(on);                                           // IGmpf2 231
                return null;
            default:
                return OTHER;
        }
    }

    @Override
    boolean run(FeatureId action, Consumer<Result> cb) {
        switch (action) {
            case PICTURE_RESET: {                                            // asked by the menu
                Play6MenuGates.Read r = menu.last();
                if (r == null || !Play6MenuGates.custom(r.pictureMode)) { refuse(cb, null); return true; }
                write("658 reset custom picture", cb, (g, g2) -> {
                    if (!Play6MenuGates.custom(g.getPictureModeRaw())) return "";
                    g.resetCustomPicture();                                          // 658
                    return null;
                });
                return true;
            }
            default:
                return false;
        }
    }

    /** p6-hal: a Custom picture item (0..100), Custom only (stock image_mode_custom). */
    String setPq(String item, String value, Play6Gmpf g) throws Exception {
        int v = Integer.parseInt(value.trim());
        if (v < PQ_MIN || v > PQ_MAX) throw new IllegalArgumentException("pq " + item + " " + v);
        if (!Play6MenuGates.custom(g.getPictureModeRaw())) return "";
        boolean r;
        switch (item) {
            case "brightness": r = g.setPq(PqItem.BRIGHTNESS, v); break;            // 693
            case "contrast": r = g.setPq(PqItem.CONTRAST, v); break;                // 695
            case "saturation": r = g.setPq(PqItem.SATURATION, v); break;            // 691
            case "sharpness": r = g.setPq(PqItem.SHARPNESS, v); break;              // 689
            default: throw new IllegalArgumentException("pq item " + item);
        }
        if (!r) throw new IllegalStateException("pq " + item + " returned false");
        return null;
    }

    /** Picture mode: locked in Game (refusal; Performance was asked by the menu, Play6MenuGates.confirm), else 667(0, m). */
    void setPictureMode(String value, Play6MenuGates.Read r, Consumer<Result> cb) {
        Integer m = Play6MenuGates.PICTURE_MODE.wire(value);
        if (m == null) { refuse(cb, null); return; }
        if (m.equals(Play6MenuGates.shownPictureMode(r.pictureMode))) { refuse(cb, null); return; }
        write("667 picture mode " + m, cb, (g, g2) -> {
            if (Play6MenuGates.gamePicture(g.getPictureModeRaw())) return "p6_pic_game_hint";
            if (!g.setPictureMode(Play6Values.pictureMode(m))) throw new IllegalStateException("667 returned false");
            return null;
        });
    }
}
