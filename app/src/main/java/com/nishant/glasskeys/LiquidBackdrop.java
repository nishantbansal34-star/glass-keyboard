package com.nishant.glasskeys;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import java.util.Random;

/**
 * The frosted "liquid" background. Colour pools drift slowly, and a soft pool of light flows
 * towards wherever you touch. It is rendered into a tiny bitmap every frame and stretched with
 * bilinear filtering, which is both cheap and naturally blurry (like frosted glass).
 */
public class LiquidBackdrop {
    private static final int GW = 64, GH = 40;
    private final Bitmap grid = Bitmap.createBitmap(GW, GH, Bitmap.Config.ARGB_8888);
    private final int[] px = new int[GW * GH];
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private Bitmap noise;
    private Bitmap photo;
    private float clock = 3f;       // animation time; only advances while awake so nothing jumps
    private long lastDraw;

    // light that follows the finger (normalised 0..1 coordinates)
    public float lightX = 0.5f, lightY = 0.6f;
    private float targetX = 0.5f, targetY = 0.6f;
    public float lightPower = 0f;
    private long lastTouch;

    public LiquidBackdrop(float dp) { this.dp = dp; }

    public void setPhoto(Bitmap b) { photo = b; }

    public void touch(float nx, float ny) {
        targetX = nx;
        targetY = ny;
        lastTouch = SystemClock.uptimeMillis();
    }

    /** True while there is motion worth animating (recent touches). */
    public boolean awake() { return SystemClock.uptimeMillis() - lastTouch < 6000; }

    private static int ch(int c, int s) { return (c >> s) & 255; }

    public void draw(Canvas c, int w, int h, Theme t, boolean liveBlur, boolean animate) {
        long now = SystemClock.uptimeMillis();
        float dt = lastDraw == 0 ? 0f : Math.min(0.05f, (now - lastDraw) / 1000f);
        lastDraw = now;
        if (animate && (awake() || lightPower > 0.01f)) clock += dt * (0.6f + 1.6f * lightPower);
        float time = clock;

        // light easing (liquid lag)
        lightX += (targetX - lightX) * 0.12f;
        lightY += (targetY - lightY) * 0.12f;
        float wantPower = awake() ? Math.max(0f, 1f - (SystemClock.uptimeMillis() - lastTouch) / 2500f) : 0f;
        lightPower += (wantPower - lightPower) * 0.08f;

        if (liveBlur) {
            c.drawColor((t.base & 0x00FFFFFF) | (t.dark ? 0x80000000 : 0x8C000000));
        } else if (photo != null) {
            float s = Math.max(w / (float) photo.getWidth(), h / (float) photo.getHeight());
            float dw = photo.getWidth() * s, dh = photo.getHeight() * s;
            c.drawBitmap(photo, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), bmpPaint);
            c.drawColor(t.dark ? 0x4D000000 : 0x33FFFFFF);
        } else {
            renderGrid(t, time);
            c.drawBitmap(grid, null, new RectF(0, 0, w, h), bmpPaint);
        }

        // Pool of light under the finger (also on photo / live blur)
        if (lightPower > 0.01f && (photo != null || liveBlur)) {
            float r = Math.max(w, h) * 0.45f;
            p.setShader(new android.graphics.RadialGradient(lightX * w, lightY * h, r,
                    new int[]{Color.argb((int) (60 * lightPower), 255, 255, 255), 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        // Frost: milky veil + grain
        c.drawColor(t.dark ? 0x0DFFFFFF : 0x33FFFFFF);
        p.setShader(new BitmapShader(noiseTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Glass sheet edge: hairline + soft glow falling from the top
        p.setShader(new LinearGradient(0, 0, 0, 18 * dp, t.dark ? 0x26FFFFFF : 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, 18 * dp, p);
        p.setShader(null);
        p.setColor(t.dark ? 0x4DFFFFFF : 0x99FFFFFF);
        c.drawRect(0, 0, w, Math.max(1, 0.8f * dp), p);
    }

    /** Colour of the bottom edge, used to tint the navigation bar so it blends in. */
    public int bottomColor(Theme t) {
        if (photo != null) return t.base;
        int idx = (GH - 1) * GW + GW / 2;
        return px[idx] | 0xFF000000;
    }

    private void renderGrid(Theme t, float time) {
        int base = t.base;
        float br = ch(base, 16), bg = ch(base, 8), bb = ch(base, 0);
        // four drifting colour pools (positions in normalised coords; aspect-corrected)
        float[][] blobs = new float[5][];
        int[] cols = {t.blobs[0], t.blobs[1], t.blobs[2], t.accent, 0xFFFFFFFF};
        float s = time * 0.18f;
        blobs[0] = new float[]{0.18f + 0.10f * (float) Math.sin(s * 0.9), 0.30f + 0.18f * (float) Math.cos(s * 0.7), 0.42f, t.dark ? 0.82f : 0.8f};
        blobs[1] = new float[]{0.82f + 0.08f * (float) Math.cos(s * 0.8 + 1), 0.25f + 0.15f * (float) Math.sin(s * 1.1), 0.40f, t.dark ? 0.78f : 0.75f};
        blobs[2] = new float[]{0.50f + 0.20f * (float) Math.sin(s * 0.6 + 2), 1.00f + 0.10f * (float) Math.cos(s * 0.9), 0.48f, t.dark ? 0.72f : 0.7f};
        blobs[3] = new float[]{0.60f + 0.25f * (float) Math.cos(s * 0.5 + 3), 0.55f + 0.20f * (float) Math.sin(s * 0.8 + 1), 0.30f, t.dark ? 0.38f : 0.35f};
        blobs[4] = new float[]{lightX, lightY, 0.30f, (t.dark ? 0.55f : 0.6f) * lightPower};
        float aspect = 2.2f; // keyboard is roughly 2.2x wider than tall
        for (int y = 0; y < GH; y++) {
            float ny = (y + 0.5f) / GH;
            for (int x = 0; x < GW; x++) {
                float nx = (x + 0.5f) / GW;
                float r = br, g = bg, b = bb;
                for (int i = 0; i < 5; i++) {
                    float[] bl = blobs[i];
                    if (bl[3] <= 0.001f) continue;
                    float dx = (nx - bl[0]) * aspect, dy = ny - bl[1];
                    float d2 = (dx * dx + dy * dy) / (bl[2] * bl[2] * aspect);
                    float wgt = bl[3] / (1f + d2 * d2 * 1.6f);
                    int col = cols[i];
                    r += (ch(col, 16) - r) * wgt;
                    g += (ch(col, 8) - g) * wgt;
                    b += (ch(col, 0) - b) * wgt;
                }
                px[y * GW + x] = 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }
        grid.setPixels(px, 0, GW, 0, 0, GW, GH);
    }

    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }

    private Bitmap noiseTile() {
        if (noise != null) return noise;
        int n = 128;
        int[] a = new int[n * n];
        Random r = new Random(11);
        for (int i = 0; i < a.length; i++) {
            int v = r.nextInt(256);
            a[i] = Color.argb(5 + r.nextInt(9), v, v, v);
        }
        noise = Bitmap.createBitmap(a, n, n, Bitmap.Config.ARGB_8888);
        return noise;
    }
}
