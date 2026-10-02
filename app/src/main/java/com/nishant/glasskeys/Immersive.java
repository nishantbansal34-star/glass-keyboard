package com.nishant.glasskeys;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Immersive" glass: thick chrome-rimmed glass on black. Each key is shaded per pixel once
 * (a rounded bead of polished glass reflecting a studio environment) and cached; the letter
 * glows through the frosted face as if a bright glyph sat behind the glass.
 */
final class Immersive {
    private final float dp;
    private final Map<Long, Bitmap> bodies = lru(48);
    private final Map<String, Bitmap> glows = lru(96);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    Immersive(float dp) { this.dp = dp; }

    private static <K> Map<K, Bitmap> lru(final int max) {
        return new LinkedHashMap<K, Bitmap>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, Bitmap> e) { return size() > max; }
        };
    }

    void clear() { bodies.clear(); glows.clear(); }

    float rimThickness(int w, int h) { return Math.max(4.5f * dp, Math.min(w, h) * 0.14f); }

    /** Draws the key body (rim + face). tint != 0 colours the face (action / active keys). */
    void drawKey(Canvas c, RectF r, int w, int h, int rad, boolean func, float press, int tint) {
        long k = ((long) w << 40) ^ ((long) h << 24) ^ ((long) rad << 8) ^ (func ? 1 : 0);
        Bitmap b = bodies.get(k);
        if (b == null) { b = build(w, h, rad, func); bodies.put(k, b); }
        c.drawBitmap(b, null, r, bmp);
        float t = rimThickness(w, h);
        if (tint != 0 || press > 0.01f) {
            float fr = Math.max(0f, rad - t);
            RectF f = new RectF(r.left + t, r.top + t, r.right - t, r.bottom - t);
            if (tint != 0) { fill.setColor(tint); c.drawRoundRect(f, fr, fr, fill); }
            if (press > 0.01f) {
                fill.setColor(((int) (34 * press) << 24) | 0xFFFFFF);
                c.drawRoundRect(f, fr, fr, fill);
            }
        }
    }

    /** The bright, blurred copy of the letter seen through the glass. */
    void drawGlyphGlow(Canvas c, RectF r, int rad, String s, float textSize, Typeface tf) {
        int w = Math.max(2, Math.round(r.width())), h = Math.max(2, Math.round(r.height()));
        String key = s + "|" + w + "x" + h + "|" + rad + "|" + Math.round(textSize);
        Bitmap g = glows.get(key);
        if (g == null) { g = buildGlow(w, h, rad, s, textSize, tf); glows.put(key, g); }
        c.drawBitmap(g, null, r, bmp);
    }

    /** For icon keys: a soft light in the middle of the face. */
    void drawFaceGlow(Canvas c, RectF r, int rad) { drawGlyphGlow(c, r, rad, "●", r.height() * 0.32f, Typeface.DEFAULT); }

    private Bitmap buildGlow(int w, int h, int rad, String s, float size, Typeface tf) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size * 1.6f);
        p.setColor(0xFFF2F6FF);
        p.setMaskFilter(new BlurMaskFilter(Math.max(1f, h * 0.11f), BlurMaskFilter.Blur.NORMAL));
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = h / 2f - (fm.ascent + fm.descent) / 2f + h * 0.03f;
        for (int i = 0; i < 3; i++) cv.drawText(s, w / 2f, y, p);   // stack for a brighter halo
        // keep the light inside the glass face (the rim stays clean chrome)
        float t = rimThickness(w, h);
        Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float fr = Math.max(0f, rad - t);
        cv.drawRoundRect(new RectF(t, t, w - t, h - t), fr, fr, m);
        // overall strength of the glow
        Paint a = new Paint();
        a.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        a.setColor(0x78000000);
        cv.drawRect(0, 0, w, h, a);
        return b;
    }

    // studio lights for the chrome reflections
    private static final float[] L1 = n(-0.55f, -0.75f, 0.45f), L2 = n(0.7f, 0.55f, 0.35f), L3 = n(0.1f, -0.3f, 0.95f);

    private static float[] n(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    private static float lobe(float[] L, float rx, float ry, float rz, double pw) {
        float d = rx * L[0] + ry * L[1] + rz * L[2];
        return d <= 0 ? 0 : (float) Math.pow(d, pw);
    }

    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    private Bitmap build(int w, int h, int rad, boolean func) {
        int[] px = new int[w * h];
        float t = rimThickness(w, h);
        float r = Math.min(rad, Math.min(w, h) / 2f);
        float hw = w / 2f, hh = h / 2f;
        float baseR = 10, baseG = 11, baseB = 14;
        if (func) { baseR *= 1.6f; baseG *= 1.6f; baseB *= 1.6f; }
        for (int y = 0; y < h; y++) {
            float fy = y + 0.5f;
            for (int x = 0; x < w; x++) {
                float fx = x + 0.5f;
                float ax = Math.abs(fx - hw) - (hw - r), ay = Math.abs(fy - hh) - (hh - r);
                float qx = Math.max(ax, 0), qy = Math.max(ay, 0);
                float len = (float) Math.sqrt(qx * qx + qy * qy);
                float d = len + Math.min(Math.max(ax, ay), 0) - r;
                float cov = clamp01(0.5f - d);
                if (cov <= 0) { px[y * w + x] = 0; continue; }
                float sx = Math.signum(fx - hw), sy = Math.signum(fy - hh);
                float nx, ny;
                if (ax > 0 && ay > 0) { nx = qx / (len + 1e-6f) * sx; ny = qy / (len + 1e-6f) * sy; }
                else if (ax > ay) { nx = sx; ny = 0; } else { nx = 0; ny = sy; }
                float u = clamp01(-d / t);
                boolean rim = u < 1f;
                float n3x, n3y, nz;
                if (rim) {
                    float s = 1f - 2f * u;
                    float s2 = Math.signum(s) * (float) Math.pow(Math.abs(s), 0.8);
                    nz = (float) Math.sqrt(Math.max(0f, 1f - s2 * s2));
                    n3x = nx * s2; n3y = ny * s2;
                } else {
                    n3x = (fx - hw) / hw * 0.12f; n3y = (fy - hh) / hh * 0.12f;
                    nz = (float) Math.sqrt(Math.max(0f, 1f - n3x * n3x - n3y * n3y));
                }
                float rx = 2 * nz * n3x, ry = 2 * nz * n3y, rz = 2 * nz * nz - 1;
                float sky = (float) Math.pow(clamp01(-ry * 1.3f + 0.35f), 1.3) * 0.9f;
                float hz = (ry - 0.18f) / 0.07f;
                float horizon = (float) Math.exp(-hz * hz) * 0.9f;
                float e = 0.03f + sky + horizon + lobe(L1, rx, ry, rz, 8) * 1.8f + lobe(L2, rx, ry, rz, 6) * 1.3f
                        + lobe(L3, rx, ry, rz, 4) * 0.18f;
                float om = 1f - nz;
                float fres = 0.04f + 0.96f * om * om * om;
                fres = rim ? Math.max(fres, 0.85f) : fres * 0.9f + 0.03f;
                float haze = rim ? 0f : (float) Math.exp(-Math.max(-d - t, 0f) / (t * 0.9f));
                float tr = baseR + haze * 40, tg = baseG + haze * 44, tb = baseB + haze * 52;
                if (rim) { tr *= 0.35f; tg *= 0.35f; tb *= 0.35f; }
                float cr = tr * (1 - fres) + e * 235 * fres;
                float cg = tg * (1 - fres) + e * 240 * fres;
                float cb = tb * (1 - fres) + e * 250 * fres;
                if (!rim) {
                    float bd = ((fx - w * 0.15f) * 0.6f + (fy - h * 0.1f)) / (h * 0.35f);
                    float band = (float) Math.exp(-bd * bd) * 0.18f;
                    cr += band * 235; cg += band * 240; cb += band * 250;
                }
                float sd = (u - 1f) / 0.08f;
                float seam = (float) Math.exp(-sd * sd) * 0.6f;
                cr *= 1 - seam; cg *= 1 - seam; cb *= 1 - seam;
                int ia = Math.round(cov * 255);
                px[y * w + x] = (ia << 24) | (c8(cr) << 16) | (c8(cg) << 8) | c8(cb);
            }
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    private static int c8(float v) { return v <= 0 ? 0 : v >= 255 ? 255 : Math.round(v); }
}
