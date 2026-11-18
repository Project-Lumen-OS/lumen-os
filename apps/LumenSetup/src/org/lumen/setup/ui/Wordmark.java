package org.lumen.setup.ui;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.PathParser;
import android.view.View;

import org.lumen.setup.L;
import org.lumen.setup.R;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The real Lumen logo (8.1): the mark "2A Aperture, facets" and the "lumen" word mark, drawn from the
 * paths of res/drawable/lumen_mark.xml and lumen_wordmark.xml (copies of apps/common/brand, apps/common/brand/sync.sh)
 * in the colours of the theme: facet A, facet B, the warm core, the word. The word's x-height centre sits on the
 * mark's centre. Sizes: 52 / 30 in the corner, 120 / 64 on the language step.
 */
public class Wordmark extends View {
    private static final String NS = "http://schemas.android.com/apk/res/android";
    private static List<Shape> sMark, sWord;
    private static float sMarkVp = 200f, sWordVw = 516f, sWordVh = 149.5f;

    private final float mMark, mWordH, mGap;
    private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mM = new Matrix();
    private final Path mTmp = new Path();

    private static final class Shape {
        final String name;
        final Path path;

        Shape(String name, Path path) {
            this.name = name;
            this.path = path;
        }
    }

    public Wordmark(Context c) {
        this(c, 52, 30, 14);
    }

    public Wordmark(Context c, float markDesign, float wordDesign, float gapDesign) {
        super(c);
        mMark = Ui.pxf(markDesign);
        mWordH = Ui.pxf(wordDesign);
        mGap = Ui.pxf(gapDesign);
        load(c);
    }

    private static synchronized void load(Context c) {
        if (sMark != null) return;
        sMark = new ArrayList<>();
        sWord = new ArrayList<>();
        float[] vp = new float[2];
        read(c, R.drawable.lumen_mark, sMark, vp);
        if (vp[0] > 0) sMarkVp = vp[0];
        read(c, R.drawable.lumen_wordmark, sWord, vp);
        if (vp[0] > 0) {
            sWordVw = vp[0];
            sWordVh = vp[1];
        }
    }

    private static void read(Context c, int res, List<Shape> out, float[] vp) {
        vp[0] = vp[1] = 0;
        try (XmlResourceParser x = c.getResources().getXml(res)) {
            for (int e = x.next(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
                if (e != XmlPullParser.START_TAG) continue;
                if ("vector".equals(x.getName())) {
                    vp[0] = x.getAttributeFloatValue(NS, "viewportWidth", 0f);
                    vp[1] = x.getAttributeFloatValue(NS, "viewportHeight", 0f);
                } else if ("path".equals(x.getName())) {
                    String d = x.getAttributeValue(NS, "pathData");
                    String n = x.getAttributeValue(NS, "name");
                    if (d != null) out.add(new Shape(n == null ? "" : n, PathParser.createPathFromPathData(d)));
                }
            }
        } catch (Exception ex) {
            L.w("logo paths", ex);
        }
    }

    private float wordW() { return mWordH * sWordVw / sWordVh; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension((int) Math.ceil(mMark + mGap + wordW()), (int) Math.ceil(mMark));
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = mMark / sMarkVp;
        for (Shape sh : sMark) {
            int idx = sh.name.startsWith("facet") && sh.name.length() > 5 ? sh.name.charAt(5) - '0' : -1;
            mP.setColor(idx < 0 ? Ui.P.markCore : (idx % 2 == 0 ? Ui.P.markA : Ui.P.markB));
            draw(c, sh.path, s, s, 0f, 0f);
        }
        float ws = mWordH / sWordVh;
        float dy = mMark / 2f - mWordH * (94f / 149.5f);
        mP.setColor(Ui.P.word);
        for (Shape sh : sWord) draw(c, sh.path, ws, ws, mMark + mGap, dy);
    }

    private void draw(Canvas c, Path p, float sx, float sy, float dx, float dy) {
        mM.setScale(sx, sy);
        mM.postTranslate(dx, dy);
        p.transform(mM, mTmp);
        c.drawPath(mTmp, mP);
    }
}
