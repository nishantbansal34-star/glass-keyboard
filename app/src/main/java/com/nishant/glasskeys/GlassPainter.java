package com.nishant.glasskeys;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Draws the "liquid glass" look: a frosted, blurred backdrop and translucent glass keys
 * with a bright refracting rim and a specular highlight. Also draws all the line icons.
 */
public class GlassPainter {
    public static final int STYLE_KEY = 0, STYLE_FUNC = 1, STYLE_ACTION = 2, STYLE_ACTIVE = 3;

    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final Path path = new Path();
    private final RectF tmp = new RectF();

    private LinearGradient bodyDark, bodyLight, rimGrad, glossGrad;
    private RadialGradient specGrad;
    private Bitmap noise;

    public GlassPainter(float density) {
        dp = density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1f * dp);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeJoin(Paint.Join.ROUND);
        iconFill.setStyle(Paint.Style.FILL);
        // Unit gradients (0..1 on Y) re-used for every key via a local matrix: no per-frame allocation.
        bodyDark = new LinearGradient(0, 0, 0, 1,
                new int[]{0x21FFFFFF, 0x10FFFFFF, 0x0DFFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        bodyLight = new LinearGradient(0, 0, 0, 1,
                new int[]{0x80FFFFFF, 0x59FFFFFF, 0x4DFFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        rimGrad = new LinearGradient(0, 0, 1, 1,
                new int[]{0xB3FFFFFF, 0x2EFFFFFF, 0x0AFFFFFF, 0x1AFFFFFF, 0x66FFFFFF},
                new float[]{0f, 0.22f, 0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP);
        glossGrad = new LinearGradient(0, 0, 0, 1,
                new int[]{0x40FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        specGrad = new RadialGradient(0.3f, 0.0f, 0.9f,
                new int[]{0x24FFFFFF, 0x0AFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    // ------------------------------------------------------------------ backdrop

    /** Renders the frosted backdrop into a bitmap (drawn once per size/theme change). */
    public Bitmap renderBackdrop(int w, int h, Theme t, Bitmap custom, boolean liveBlur) {
        if (w <= 0 || h <= 0) return null;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        if (liveBlur) {
            // The system blurs the app behind us; we just add a tinted frost layer.
            c.drawColor((t.base & 0x00FFFFFF) | (t.dark ? 0x8C000000 : 0x99000000));
        } else if (custom != null) {
            // Centre-crop the user's (already blurred) picture.
            float s = Math.max(w / (float) custom.getWidth(), h / (float) custom.getHeight());
            float dw = custom.getWidth() * s, dh = custom.getHeight() * s;
            c.drawBitmap(custom, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p);
            c.drawColor(t.dark ? 0x59000000 : 0x40FFFFFF);
        } else {
            c.drawColor(t.base);
            float[][] pos = {{0.08f, 0.25f, 0.7f}, {0.9f, 0.2f, 0.65f}, {0.5f, 1.0f, 0.75f}};
            for (int i = 0; i < 3; i++) {
                float cx = pos[i][0] * w, cy = pos[i][1] * h, r = pos[i][2] * Math.max(w, h) * 0.6f;
                int col = t.blobs[i];
                p.setShader(new RadialGradient(cx, cy, r,
                        new int[]{(col & 0x00FFFFFF) | (t.dark ? 0xE6000000 : 0xCC000000),
                                  (col & 0x00FFFFFF) | 0x59000000,
                                  col & 0x00FFFFFF},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
            }
            p.setShader(null);
            // Milky frost wash
            c.drawColor(t.dark ? 0x0FFFFFFF : 0x40FFFFFF);
        }

        // Fine grain so it reads as frosted glass rather than a flat gradient.
        p.setShader(new BitmapShader(noiseTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Bright top edge like the lip of a glass sheet.
        p.setShader(new LinearGradient(0, 0, 0, 3 * dp, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, 3 * dp, p);
        return bmp;
    }

    private Bitmap noiseTile() {
        if (noise != null) return noise;
        int n = 96;
        int[] px = new int[n * n];
        Random r = new Random(7);
        for (int i = 0; i < px.length; i++) {
            int v = r.nextInt(256);
            int a = 6 + r.nextInt(10);
            px[i] = Color.argb(a, v, v, v);
        }
        noise = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        return noise;
    }

    /** Cheap, good-looking blur for a picked photo: shrink hard, box-blur, then let the GPU scale up. */
    public static Bitmap frost(Bitmap src) {
        int tw = 60;
        int th = Math.max(1, (int) (src.getHeight() * (tw / (float) src.getWidth())));
        Bitmap small = Bitmap.createScaledBitmap(src, tw, th, true);
        int[] px = new int[tw * th];
        small.getPixels(px, 0, tw, 0, 0, tw, th);
        for (int pass = 0; pass < 3; pass++) px = boxBlur(px, tw, th, 2);
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, tw, 0, 0, tw, th);
        return out;
    }

    private static int[] boxBlur(int[] in, int w, int h, int r) {
        int[] tmp = new int[in.length], out = new int[in.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int rs = 0, gs = 0, bs = 0, n = 0;
            for (int k = -r; k <= r; k++) {
                int xx = Math.min(w - 1, Math.max(0, x + k));
                int c = in[y * w + xx];
                rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
            }
            tmp[y * w + x] = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int rs = 0, gs = 0, bs = 0, n = 0;
            for (int k = -r; k <= r; k++) {
                int yy = Math.min(h - 1, Math.max(0, y + k));
                int c = tmp[yy * w + x];
                rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
            }
            out[y * w + x] = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
        }
        return out;
    }

    // ------------------------------------------------------------------ glass keys

    private void unit(Shader s, RectF r) {
        m.reset();
        m.setScale(1, r.height());
        m.postTranslate(0, r.top);
        s.setLocalMatrix(m);
    }

    private void unitBox(Shader sh, RectF r) {
        m.reset();
        m.setScale(r.width(), r.height());
        m.postTranslate(r.left, r.top);
        sh.setLocalMatrix(m);
    }

    private final Path shape = new Path();

    // ---------------------------------------------------------------- refraction source
    // The backdrop hands us the picture that sits behind the keyboard, so each glass key can
    // show that picture slightly magnified through itself, like a real lens.
    private Bitmap refrSrc;
    private float srcScaleX = 1, srcScaleY = 1;
    private float originX, originY;
    private android.view.View rootView;
    private final int[] loc = new int[2], rootLoc = new int[2];
    private final android.graphics.Rect srcRect = new android.graphics.Rect();
    private final Paint refr = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final java.util.HashMap<Long, Bitmap> overlayCache = new java.util.HashMap<>();
    private final java.util.HashMap<Long, Bitmap> shadowCache = new java.util.HashMap<>();
    private final Paint spritePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    {
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setSaturation(1.18f);
        android.graphics.ColorMatrix bright = new android.graphics.ColorMatrix(new float[]{
                1.1f, 0, 0, 0, 10, 0, 1.1f, 0, 0, 10, 0, 0, 1.1f, 0, 12, 0, 0, 0, 1, 0});
        cm.postConcat(bright);
        refr.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
    }

    /** Dark "AMOLED" glass: smoky keys with hairline edges on black. */
    public boolean amoled = true;
    /** Theme pack: 0 = Liquid Glass, 1 = Shadow Realm */
    public int pack = 0;
    /** Per-key variation (so flames differ from key to key). */
    public int variant = 0;

    public void setPack(int p) {
        if (p != pack) { pack = p; clearSprites(); }
    }

    public void setAmoled(boolean a) {
        if (a != amoled) { amoled = a; clearSprites(); }
    }

    /**
     * Still: no glass, no glow. Keys are just their letters on black; a faint fill appears only
     * while pressed, the space bar and list cards get a hairline outline so they can be found.
     */
    private final Paint stillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void drawStill(Canvas c, RectF r, int w, int h, int rad, int style, boolean pressed) {
        boolean wide = w > h * 2.6f;
        stillPaint.setStyle(Paint.Style.FILL);
        if (style == STYLE_ACTIVE) { stillPaint.setColor(0x26FFFFFF); c.drawRoundRect(r, rad, rad, stillPaint); }
        else if (style == STYLE_ACTION) { stillPaint.setColor(0x1AFFFFFF); c.drawRoundRect(r, rad, rad, stillPaint); }
        if (pressed) { stillPaint.setColor(0x1FFFFFFF); c.drawRoundRect(r, rad, rad, stillPaint); }
        if (wide && variant != -1) {
            stillPaint.setStyle(Paint.Style.STROKE);
            stillPaint.setStrokeWidth(Math.max(1f, 0.8f * dp));
            stillPaint.setColor(0x2EFFFFFF);
            float i = stillPaint.getStrokeWidth() / 2;
            c.drawRoundRect(r.left + i, r.top + i, r.right - i, r.bottom - i, rad, rad, stillPaint);
            stillPaint.setStyle(Paint.Style.FILL);
        }
    }

    /** Immersive chrome-glass look (Liquid Glass pack only). */
    public boolean immersive = false;
    private Immersive imm;
    private int lastRad;

    public void setImmersive(boolean on) {
        if (on != immersive) { immersive = on; clearSprites(); }
    }

    public boolean immersiveActive() { return immersive && pack == 0; }

    /** Letter light behind the glass of the key that was just drawn. */
    public void drawGlyphGlow(Canvas c, RectF r, String s, float size, Typeface tf) {
        if (!immersiveActive() || imm == null) return;
        imm.drawGlyphGlow(c, r, lastRad, s, size, tf);
    }

    public void drawFaceGlow(Canvas c, RectF r) {
        if (!immersiveActive() || imm == null) return;
        imm.drawFaceGlow(c, r, lastRad);
    }

    public void setRoot(android.view.View root) { rootView = root; }

    /** Kept for compatibility with the backdrop; this look refracts the main source only. */
    public void setSoftSource(Bitmap soft, float scaleX, float scaleY) { }

    public void setSource(Bitmap src, float scaleX, float scaleY) {
        refrSrc = src; srcScaleX = scaleX; srcScaleY = scaleY;
    }

    /** Call at the start of a view's onDraw so refraction lines up with the backdrop. */
    public void setOriginFromView(android.view.View v) {
        if (rootView == null) { originX = originY = 0; return; }
        v.getLocationInWindow(loc);
        rootView.getLocationInWindow(rootLoc);
        originX = loc[0] - rootLoc[0];
        originY = loc[1] - rootLoc[1];
    }

    public void setOrigin(float x, float y) { originX = x; originY = y; }

    public void clearSprites() { overlayCache.clear(); shadowCache.clear(); if (imm != null) imm.clear(); }

    private void drawRefraction(Canvas c, RectF r, float mag) {
        if (refrSrc == null || refrSrc.isRecycled()) return;
        float cx = (r.centerX() + originX) * srcScaleX, cy = (r.centerY() + originY) * srcScaleY;
        float hw = r.width() * srcScaleX / (2 * mag), hh = r.height() * srcScaleY / (2 * mag);
        // sample a little lower than the key centre: light bends upward through thick glass
        cy += hh * 0.12f;
        srcRect.set(Math.round(cx - hw), Math.round(cy - hh), Math.round(cx + hw), Math.round(cy + hh));
        c.drawBitmap(refrSrc, srcRect, r, refr);
    }

    public void drawGlass(Canvas c, RectF r, float radius, int style, boolean pressed, Theme t) {
        drawGlass(c, r, radius, style, pressed, t, 0f);
    }

    /** Thick liquid-glass key: soft shadow, magnified backdrop, frosted body, caustic rims. */
    public void drawGlass(Canvas c, RectF r, float radius, int style, boolean pressed, Theme t, float glow) {
        int w = Math.max(2, Math.round(r.width())), h = Math.max(2, Math.round(r.height()));
        int rad = Math.round(Math.min(radius, Math.min(w, h) / 2f));

        if (pack == 1) { drawShadowKey(c, r, w, h, rad, style, pressed, t, glow); return; }
        if (pack == 2) { drawStill(c, r, w, h, rad, style, pressed); return; }
        if (immersive) {
            if (imm == null) imm = new Immersive(dp);
            lastRad = rad;
            int tint = style == STYLE_ACTIVE ? (0x59000000 | (t.accent & 0xFFFFFF))
                    : style == STYLE_ACTION ? (0x40000000 | (t.accent & 0xFFFFFF)) : 0;
            float press = Math.max(pressAmt, pressed ? 1f : 0f);
            imm.drawKey(c, r, w, h, rad, style == STYLE_FUNC, press, tint);
            return;
        }
        if (!amoled) {
            Bitmap sh = shadow(w, h, rad, t.dark);
            float pad = 12 * dp;
            c.drawBitmap(sh, r.left - pad, r.top - pad, spritePaint);
        }

        if (amoled) { drawPremium(c, r, w, h, rad, style, pressed, t, glow); return; }
        shape.reset();
        shape.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        drawRefraction(c, r, pressed ? 1.16f : 1.07f);
        if (style == STYLE_ACTION) {
            fill.setColor((t.accent & 0x00FFFFFF) | 0x8C000000);
            c.drawRect(r, fill);
        } else if (style == STYLE_ACTIVE) {
            fill.setColor((t.accent & 0x00FFFFFF) | 0x73000000);
            c.drawRect(r, fill);
        } else if (style == STYLE_FUNC) {
            fill.setColor(t.dark ? 0x14FFFFFF : 0x26FFFFFF);
            c.drawRect(r, fill);
        }
        if (pressed) {
            fill.setColor(t.dark ? 0x21FFFFFF : 0x40FFFFFF);
            c.drawRect(r, fill);
        }
        c.restore();

        c.drawBitmap(overlay(w, h, rad, t.dark), null, r, spritePaint);

        if (glow > 0.02f) {
            rim.setShader(null);
            rim.setStrokeWidth(1.4f * dp);
            rim.setColor(Color.argb((int) (150 * Math.min(1f, glow)), 255, 255, 255));
            tmp.set(r.left + 0.7f * dp, r.top + 0.7f * dp, r.right - 0.7f * dp, r.bottom - 0.7f * dp);
            c.drawRoundRect(tmp, rad - 0.7f * dp, rad - 0.7f * dp, rim);
        }
    }

    // ---------------------------------------------------------------- Premium liquid glass
    //
    // One material for everything: charcoal glass that refracts (slightly magnifies and blurs)
    // whatever is behind it, picks up that colour, deepens over bright areas so text stays
    // readable, and is lit by ONE light above the keyboard (so each key's specular edge sits on
    // the side facing the light). All static lighting lives in cached sprites.

    /** Press state for the key being drawn (set by the keyboard view): 0..1, touch point, ripple 0..1. */
    public float pressAmt, touchX = -1, touchY = -1, ripple = -1;
    /** Horizontal position of the environment light, in root coordinates. */
    public float lightX = -1;
    public int rootWidth = 1;
    private final Paint envPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int envSample(RectF r) {
        if (refrSrc == null || refrSrc.isRecycled()) return 0xFF000000;
        int x = (int) ((r.centerX() + originX) * srcScaleX), y = (int) ((r.centerY() + originY) * srcScaleY);
        x = Math.max(0, Math.min(refrSrc.getWidth() - 1, x));
        y = Math.max(0, Math.min(refrSrc.getHeight() - 1, y));
        return refrSrc.getPixel(x, y);
    }

    private static float luma(int c) {
        return (0.2126f * ((c >> 16) & 255) + 0.7152f * ((c >> 8) & 255) + 0.0722f * (c & 255)) / 255f;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private void drawPremium(Canvas c, RectF r, int w, int h, int rad, int style, boolean pressed, Theme t, float glow) {
        boolean dark = t.dark;
        boolean wide = w > h * 2.6f;
        int env = envSample(r);
        float L = luma(env);

        // 1. float: a soft shadow under every key lifts it off the surface
        Bitmap sh = premiumShadow(w, h, rad, dark);
        float pad = 10 * dp;
        c.drawBitmap(sh, r.left - pad, r.top - pad, spritePaint);

        shape.reset();
        shape.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        // 2. refraction: the wallpaper seen through the glass, slightly magnified and softened
        drawRefraction(c, r, wide ? 1.04f : 1.08f);

        // 3. glass body: charcoal tinted by the environment; deeper over bright areas for legibility
        int base;
        float alpha;
        if (dark) {
            // clear glass: cool grey with a very faint blue, tinted by what is behind the key
            base = mix(0xFF2B3139, env, 0.38f);
            alpha = style == STYLE_FUNC ? 0.34f : wide ? 0.14f : 0.24f;
            alpha += Math.max(0f, L - 0.45f) * 0.5f;            // only over bright spots: a little deeper
        } else {
            base = mix(0xFFF6F8FB, env, 0.25f);
            alpha = style == STYLE_FUNC ? 0.55f : wide ? 0.34f : 0.44f;
            alpha += Math.max(0f, 0.5f - L) * 0.3f;
        }
        if (wide && dark) {
            // the space bar is the clearest, brightest piece of glass
            envPaint.setColor(0x0DFFFFFF);
            c.drawRect(r, envPaint);
        }
        envPaint.setColor(((int) (255 * Math.min(0.92f, alpha)) << 24) | (base & 0xFFFFFF));
        c.drawRect(r, envPaint);
        if (style == STYLE_ACTION || style == STYLE_ACTIVE) {
            envPaint.setColor(((style == STYLE_ACTIVE ? 0x4D : 0x2E) << 24) | (t.accent & 0xFFFFFF));
            c.drawRect(r, envPaint);
        }

        // 4. press: brighten, highlight gathers under the finger, tiny internal ripple
        if (pressAmt > 0.01f) {
            envPaint.setColor(((int) ((dark ? 30 : 40) * pressAmt) << 24) | 0xFFFFFF);
            c.drawRect(r, envPaint);
            if (touchX >= 0) {
                envPaint.setShader(new RadialGradient(touchX, touchY, Math.max(w, h) * 0.8f,
                        ((int) ((dark ? 46 : 70) * pressAmt) << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawRect(r, envPaint);
                envPaint.setShader(null);
            }
        }
        if (ripple >= 0f && ripple < 1f && touchX >= 0) {
            float rr = (float) Math.hypot(w, h) * (0.15f + 0.85f * ripple);
            envPaint.setStyle(Paint.Style.STROKE);
            envPaint.setStrokeWidth(Math.max(1f, 5 * dp * (1f - ripple)));
            envPaint.setColor(((int) ((dark ? 40 : 55) * (1f - ripple)) << 24) | 0xFFFFFF);
            c.drawCircle(touchX, touchY, rr, envPaint);
            envPaint.setStyle(Paint.Style.FILL);
        }
        c.restore();

        // 5. static light: internal highlight, edge luminance, specular edge facing the light
        int bucket = 2;
        if (lightX >= 0) {
            float d = (lightX - (r.centerX() + originX)) / Math.max(1, rootWidth); // -1..1
            bucket = Math.max(0, Math.min(4, Math.round(2 + d * 4.5f)));
        }
        c.drawBitmap(premiumOverlay(w, h, rad, dark, bucket, style == STYLE_FUNC, wide), null, r, spritePaint);

        if (glow > 0.05f) {
            rim.setShader(null);
            rim.setStrokeWidth(0.9f * dp);
            rim.setColor(Color.argb((int) (70 * Math.min(1f, glow)), 255, 255, 255));
            tmp.set(r.left + 0.5f * dp, r.top + 0.5f * dp, r.right - 0.5f * dp, r.bottom - 0.5f * dp);
            c.drawRoundRect(tmp, rad - 0.5f * dp, rad - 0.5f * dp, rim);
        }
    }

    private Bitmap premiumShadow(int w, int h, int rad, boolean dark) {
        long k = w | ((long) h << 16) | ((long) rad << 32) | (9L << 50) | ((dark ? 1L : 0L) << 55);
        Bitmap b = shadowCache.get(k);
        if (b != null) return b;
        if (shadowCache.size() > 90) shadowCache.clear();
        int pad = Math.round(10 * dp);
        b = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(dark ? 0x4D000000 : 0x331B2330);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(5f * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
        cv.drawRoundRect(new RectF(pad + 2 * dp, pad + 3.5f * dp, pad + w - 2 * dp, pad + h + 3 * dp), rad, rad, p);
        shadowCache.put(k, b);
        return b;
    }

    /** Cached lighting for one key size / light bucket. */
    private Bitmap premiumOverlay(int w, int h, int rad, boolean dark, int bucket, boolean func, boolean wide) {
        long k = w | ((long) h << 16) | ((long) rad << 32) | ((long) bucket << 48) | ((dark ? 1L : 0L) << 52)
                | ((func ? 1L : 0L) << 53) | (6L << 58);
        Bitmap b = overlayCache.get(k);
        if (b != null) return b;
        if (overlayCache.size() > 120) overlayCache.clear();
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF all = new RectF(0, 0, w, h);
        Path pth = new Path();
        pth.addRoundRect(all, rad, rad, Path.Direction.CW);
        float peak = 0.18f + bucket * 0.16f;          // where the light strikes along the top edge
        android.graphics.BlurMaskFilter soft = new android.graphics.BlurMaskFilter(Math.max(0.8f, 1.1f * dp), android.graphics.BlurMaskFilter.Blur.NORMAL);
        cv.save();
        cv.clipPath(pth);
        // light entering the top of the glass (lit side brighter)
        p.setShader(new RadialGradient(w * peak, -h * 0.2f, Math.max(w, h) * (wide ? 0.6f : 1.0f),
                new int[]{dark ? 0x2EFFFFFF : 0x40FFFFFF, 0x0AFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        p.setShader(null);
        // soft darker refractive band just inside the edge (light bending away at the thick rim)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.4f * dp);
        p.setShader(new LinearGradient(0, 0, 0, h, dark ? 0x08000000 : 0x061B2330, dark ? 0x21000000 : 0x141B2330, Shader.TileMode.CLAMP));
        p.setMaskFilter(new android.graphics.BlurMaskFilter(2.2f * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
        float band = 2.6f * dp;
        cv.drawRoundRect(new RectF(band, band, w - band, h - band), Math.max(1, rad - band), Math.max(1, rad - band), p);
        p.setMaskFilter(null);
        p.setShader(null);
        // return glow: light caught by the thickness of the glass along the bottom inside edge
        p.setStrokeWidth(2f * dp);
        p.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00FFFFFF, dark ? 0x2EFFFFFF : 0x66FFFFFF, Shader.TileMode.CLAMP));
        p.setMaskFilter(soft);
        float in2 = 1.4f * dp;
        cv.drawRoundRect(new RectF(in2, in2, w - in2, h - in2), rad - in2, rad - in2, p);
        p.setMaskFilter(null);
        p.setShader(null);
        cv.restore();
        // faint rim so the silhouette reads, strongest top and bottom, almost gone on the sides
        float in = 0.5f * dp;
        p.setStrokeWidth(0.7f * dp);
        p.setShader(new LinearGradient(0, 0, 0, h,
                dark ? new int[]{0x40FFFFFF, 0x0DFFFFFF, 0x0AFFFFFF, 0x26FFFFFF} : new int[]{0xB3FFFFFF, 0x40FFFFFF, 0x33FFFFFF, 0x59FFFFFF},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(in, in, w - in, h - in), rad - in, rad - in, p);
        // soft specular edge where the light hits (blurred, never a hard outline)
        cv.save();
        cv.clipRect(0, 0, w, Math.min(h * 0.55f, rad + 3 * dp));
        p.setStrokeWidth(1.6f * dp);
        p.setMaskFilter(soft);
        float spread = wide ? 0.24f : 0.45f;
        p.setShader(new LinearGradient(w * (peak - spread), 0, w * (peak + spread), 0,
                new int[]{0x00FFFFFF, dark ? 0xB3FFFFFF : 0xF2FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(in + 0.4f * dp, in + 0.4f * dp, w - in - 0.4f * dp, h - in), rad - in, rad - in, p);
        p.setMaskFilter(null);
        cv.restore();
        p.setShader(null);
        if (func) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? 0x0D000000 : 0x0A1B2330);
            cv.drawPath(pth, p);
        }
        overlayCache.put(k, b);
        return b;
    }

    // ---------------------------------------------------------------- Shadow Realm keys

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void drawShadowKey(Canvas c, RectF r, int w, int h, int rad, int style, boolean pressed, Theme t, float glow) {
        boolean hero = style == STYLE_ACTION;           // return key: blazing outer glow
        if (hero || pressed || glow > 0.3f) {
            Bitmap g = violetGlow(w, h, rad);
            float pad = 14 * dp;
            spritePaint.setAlpha(hero ? 255 : (int) (255 * Math.max(pressed ? 0.8f : 0f, glow * 0.6f)));
            c.drawBitmap(g, r.left - pad, r.top - pad, spritePaint);
            spritePaint.setAlpha(255);
        }
        shape.reset();
        shape.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        drawRefraction(c, r, 1.0f);
        // obsidian body: the art shows through faintly (more on the wide space bar)
        boolean wide = w > h * 3;
        fill.setColor(wide ? 0x730A0618 : (style == STYLE_FUNC ? 0xD9090614 : 0xCC0A0716));
        c.drawRect(r, fill);
        if (pressed) {
            fill.setColor(0x598B5CFF);
            c.drawRect(r, fill);
        }
        c.restore();
        c.drawBitmap(shadowOverlay(w, h, rad, variant, hero, wide), null, r, spritePaint);
        if (glow > 0.02f) {
            rim.setShader(null);
            rim.setStrokeWidth(1.4f * dp);
            rim.setColor(Color.argb((int) (170 * Math.min(1f, glow)), 0xB0, 0x90, 0xFF));
            tmp.set(r.left + 0.7f * dp, r.top + 0.7f * dp, r.right - 0.7f * dp, r.bottom - 0.7f * dp);
            c.drawRoundRect(tmp, rad - 0.7f * dp, rad - 0.7f * dp, rim);
        }
    }

    private Bitmap violetGlow(int w, int h, int rad) {
        long k = w | ((long) h << 16) | ((long) rad << 32) | (7L << 50);
        Bitmap b = shadowCache.get(k);
        if (b != null) return b;
        int pad = Math.round(14 * dp);
        b = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        glowPaint.setColor(0xB38B5CFF);
        glowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(9 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
        cv.drawRoundRect(new RectF(pad, pad, pad + w, pad + h), rad, rad, glowPaint);
        glowPaint.setMaskFilter(null);
        shadowCache.put(k, b);
        return b;
    }

    /** Violet flames licking up from the bottom edge, a flame-lit rim and a faint top sheen. */
    private Bitmap shadowOverlay(int w, int h, int rad, int seed, boolean hero, boolean wide) {
        long k = w | ((long) h << 16) | ((long) rad << 32) | ((long) (seed == -1 ? 64 : seed & 63) << 49) | ((hero ? 1L : 0L) << 57) | (1L << 60);
        Bitmap b = overlayCache.get(k);
        if (b != null) return b;
        if (overlayCache.size() > 120) overlayCache.clear();
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF all = new RectF(0, 0, w, h);
        Path pth = new Path();
        pth.addRoundRect(all, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(pth);
        // ember glow pooled at the bottom
        p.setShader(new RadialGradient(w * 0.5f, h * 1.15f, Math.max(w, h) * 0.8f,
                new int[]{hero ? 0xB38B5CFF : 0x6B7C4DFF, 0x1A5B2DD0, 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        p.setShader(null);
        // flame tongues
        Random rnd = new Random((seed & 63) * 7919L + w * 31L + h);
        int tongues = seed == -1 ? 0 : wide ? 9 : (hero ? 5 : 2 + rnd.nextInt(3));
        p.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1f, 1.6f * dp), android.graphics.BlurMaskFilter.Blur.NORMAL));
        for (int i = 0; i < tongues; i++) {
            float cx = w * (0.08f + 0.84f * rnd.nextFloat());
            float fh = h * ((hero ? 0.35f : 0.18f) + rnd.nextFloat() * (hero ? 0.35f : 0.28f));
            float fw = Math.min(w * 0.35f, (7 + rnd.nextFloat() * 9) * dp);
            float lean = (rnd.nextFloat() - 0.5f) * fw * 1.4f;
            Path f = new Path();
            f.moveTo(cx - fw / 2, h + 2);
            f.quadTo(cx - fw * 0.55f + lean * 0.3f, h - fh * 0.45f, cx + lean, h - fh);
            f.quadTo(cx + fw * 0.35f + lean * 0.3f, h - fh * 0.5f, cx + fw / 2, h + 2);
            f.close();
            p.setShader(new LinearGradient(0, h, 0, h - fh, 0xD97C4DFF, 0x006B3DF0, Shader.TileMode.CLAMP));
            cv.drawPath(f, p);
            // hot inner core
            Path core = new Path();
            core.moveTo(cx - fw * 0.18f, h + 2);
            core.quadTo(cx - fw * 0.2f + lean * 0.2f, h - fh * 0.35f, cx + lean * 0.55f, h - fh * 0.6f);
            core.quadTo(cx + fw * 0.12f, h - fh * 0.3f, cx + fw * 0.18f, h + 2);
            core.close();
            p.setShader(new LinearGradient(0, h, 0, h - fh * 0.6f, 0xB3D4C2FF, 0x00B79CFF, Shader.TileMode.CLAMP));
            cv.drawPath(core, p);
        }
        p.setShader(null);
        p.setMaskFilter(null);
        // faint glassy sheen at the top
        p.setShader(new LinearGradient(0, 0, 0, h * 0.4f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        p.setShader(null);
        cv.restore();
        // flame-lit rim: cool violet at the top, hot at the bottom
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth((hero ? 1.8f : 1.2f) * dp);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{hero ? 0xFFC9B5FF : 0x99A58BFF, hero ? 0xCC8B5CFF : 0x4D6B4BFF, hero ? 0xFFB08CFF : 0xCC9B6BFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        float in = 0.7f * dp;
        cv.drawRoundRect(new RectF(in, in, w - in, h - in), rad - in, rad - in, p);
        overlayCache.put(k, b);
        return b;
    }

    /** Smoky glass lighting: faint frost, hairline rim brighter at the top, a whisper of light at the bottom. */
    private Bitmap overlayDark(int w, int h, int rad) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF all = new RectF(0, 0, w, h);
        Path pth = new Path();
        pth.addRoundRect(all, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(pth);
        cv.drawColor(0x1CFFFFFF);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x17FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        p.setShader(new RadialGradient(w * 0.5f, h * 1.1f, Math.max(w, h) * 0.7f,
                new int[]{0x1FFFFFFF, 0x08FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        p.setShader(null);
        cv.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f * dp);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x8CFFFFFF, 0x2EFFFFFF, 0x17FFFFFF, 0x21FFFFFF, 0x4DFFFFFF},
                new float[]{0f, 0.2f, 0.55f, 0.85f, 1f}, Shader.TileMode.CLAMP));
        float in = 0.6f * dp;
        cv.drawRoundRect(new RectF(in, in, w - in, h - in), rad - in, rad - in, p);
        return b;
    }

    private Bitmap shadow(int w, int h, int rad, boolean dark) {
        long k = w | ((long) h << 16) | ((long) rad << 32) | ((dark ? 1L : 0L) << 48);
        Bitmap b = shadowCache.get(k);
        if (b != null) return b;
        if (shadowCache.size() > 90) shadowCache.clear();
        int pad = Math.round(12 * dp);
        b = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // broad ambient shadow
        p.setColor(dark ? 0x59000000 : 0x331B2A4A);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(7 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
        cv.drawRoundRect(new RectF(pad + 1 * dp, pad + 4 * dp, pad + w - 1 * dp, pad + h + 3 * dp), rad, rad, p);
        // tight contact shadow
        p.setColor(dark ? 0x4D000000 : 0x261B2A4A);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(2 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
        cv.drawRoundRect(new RectF(pad, pad + 1.5f * dp, pad + w, pad + h + 1.5f * dp), rad, rad, p);
        shadowCache.put(k, b);
        return b;
    }

    /** All the static glass lighting for one key size, drawn once in software and cached. */
    private Bitmap overlay(int w, int h, int rad, boolean dark) {
        long k = w | ((long) h << 16) | ((long) rad << 32) | ((dark ? 1L : 0L) << 48) | ((amoled ? 1L : 0L) << 49);
        Bitmap b = overlayCache.get(k);
        if (b != null) return b;
        if (overlayCache.size() > 90) overlayCache.clear();
        if (amoled) { b = overlayDark(w, h, rad); overlayCache.put(k, b); return b; }
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF all = new RectF(0, 0, w, h);
        Path pth = new Path();
        pth.addRoundRect(all, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(pth);
        // frosted body
        cv.drawColor(dark ? 0x2EFFFFFF : 0x4DFFFFFF);
        // top sheen
        p.setShader(new LinearGradient(0, 0, 0, h * 0.55f, dark ? 0x42FFFFFF : 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        // glass thickness: darker toward the bottom
        p.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, dark ? 0x21000000 : 0x141B2A4A, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        // caustic: light gathering along the bottom inside edge
        p.setShader(new RadialGradient(w * 0.5f, h * 1.08f, Math.max(w, h) * 0.75f,
                new int[]{dark ? 0x70FFFFFF : 0x8CFFFFFF, 0x1AFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(all, p);
        p.setShader(null);
        // specular glint, top-left
        // soft highlight hugging the top edge (not a separate blob)
        p.setColor(dark ? 0x4DFFFFFF : 0x80FFFFFF);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1f, 3f * dp), android.graphics.BlurMaskFilter.Blur.NORMAL));
        cv.drawRoundRect(new RectF(w * 0.12f, 1.5f * dp, w * 0.88f, 1.5f * dp + Math.min(h * 0.12f, 5 * dp)), 4 * dp, 4 * dp, p);
        p.setMaskFilter(null);
        cv.restore();
        // inner bright rim: strong at top and bottom, softer at the sides
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * dp);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xD9FFFFFF, 0x40FFFFFF, 0x26FFFFFF, 0x4DFFFFFF, 0xB3FFFFFF},
                new float[]{0f, 0.2f, 0.55f, 0.85f, 1f}, Shader.TileMode.CLAMP));
        float in = 0.8f * dp;
        cv.drawRoundRect(new RectF(in, in, w - in, h - in), rad - in, rad - in, p);
        // second, softer rim a little further in (gives the "thick" look)
        p.setStrokeWidth(2.5f * dp);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x2EFFFFFF}, new float[]{0f, 0.25f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        float in2 = 2.4f * dp;
        cv.drawRoundRect(new RectF(in2, in2, w - in2, h - in2), Math.max(1, rad - in2), Math.max(1, rad - in2), p);
        // crisp outer edge for definition against bright backgrounds
        p.setShader(null);
        p.setStrokeWidth(0.8f * dp);
        p.setColor(dark ? 0x33000000 : 0x1F1B2A4A);
        cv.drawRoundRect(new RectF(0.4f * dp, 0.4f * dp, w - 0.4f * dp, h - 0.4f * dp), rad, rad, p);
        overlayCache.put(k, b);
        return b;
    }

    /**
     * Liquid glass for any shape: an almost-clear body, a light pool inside the lens,
     * and a thin rim that is brighter on the lit (top-left) and bottom-right edges.
     */
    public void drawGlassPath(Canvas c, Path sh, RectF r, int style, boolean pressed, Theme t, float glow) {
        boolean dark = t.dark;

        // whisper-soft contact shadow
        c.save();
        c.translate(0, 1.2f * dp);
        shadow.setColor(dark ? 0x14000000 : 0x0F1B2A4A);
        c.drawPath(sh, shadow);
        c.restore();

        c.save();
        c.clipPath(sh);
        drawRefraction(c, r, 1.12f);
        c.restore();

        // body
        if (style == STYLE_ACTION || style == STYLE_ACTIVE) {
            int a = t.accent & 0x00FFFFFF;
            int top = a | (style == STYLE_ACTIVE ? 0x73000000 : (pressed ? 0xFF000000 : 0xD9000000));
            int bot = a | (style == STYLE_ACTIVE ? 0x40000000 : (pressed ? 0xD9000000 : 0xA6000000));
            fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, top, bot, Shader.TileMode.CLAMP));
            c.drawPath(sh, fill);
            fill.setShader(null);
        } else {
            Shader body = dark ? bodyDark : bodyLight;
            unit(body, r);
            fill.setShader(body);
            fill.setAlpha(style == STYLE_FUNC ? (dark ? 175 : 150) : 255);
            c.drawPath(sh, fill);
            fill.setShader(null);
            fill.setAlpha(255);
            if (style == STYLE_FUNC) {
                fill.setColor(dark ? 0x12000000 : 0x0A1B2A4A);
                c.drawPath(sh, fill);
            }
        }
        if (pressed && style != STYLE_ACTION) {
            fill.setColor(dark ? 0x2EFFFFFF : 0x4DFFFFFF);
            c.drawPath(sh, fill);
        }

        // light pooled inside the lens
        c.save();
        c.clipPath(sh);
        unitBox(specGrad, r);
        gloss.setShader(specGrad);
        gloss.setAlpha((int) Math.min(255, 255 * (0.8f + glow * 0.8f)));
        c.drawRect(r, gloss);
        gloss.setShader(null);
        gloss.setAlpha(255);
        c.restore();

        // refracting rim
        unitBox(rimGrad, r);
        rim.setShader(rimGrad);
        rim.setStrokeWidth(1f * dp);
        rim.setAlpha((int) Math.min(255, (dark ? 150 : 190) + glow * 105));
        c.drawPath(sh, rim);
        rim.setShader(null);
        rim.setAlpha(255);
    }

    /** A large glass bubble used for long-press menus. */
    public void drawBubble(Canvas c, RectF r, float radius, Theme t) {
        shape.reset();
        shape.addRoundRect(r, radius, radius, Path.Direction.CW);
        drawBubblePath(c, shape, r, t);
    }

    /** Glass that is a little more opaque (so what is inside stays readable), e.g. the droplet preview. */
    public void drawBubblePath(Canvas c, Path sh, RectF r, Theme t) {
        if (pack == 2) {
            stillPaint.setStyle(Paint.Style.FILL);
            stillPaint.setColor(0xF5121212);
            c.drawPath(sh, stillPaint);
            stillPaint.setStyle(Paint.Style.STROKE);
            stillPaint.setStrokeWidth(Math.max(1f, 0.8f * dp));
            stillPaint.setColor(0x40FFFFFF);
            c.drawPath(sh, stillPaint);
            stillPaint.setStyle(Paint.Style.FILL);
            return;
        }
        c.save();
        c.translate(0, 3 * dp);
        shadow.setColor(t.dark ? 0x40000000 : 0x261B2A4A);
        c.drawPath(sh, shadow);
        c.restore();
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                amoled ? (t.dark ? 0xEB1E2229 : 0xEBF4F6F9) : (t.dark ? 0x8C2A3048 : 0xB3FFFFFF),
                amoled ? (t.dark ? 0xEB15181E : 0xEBE9EDF2) : (t.dark ? 0x731E2236 : 0x99F2F5FA), Shader.TileMode.CLAMP));
        c.drawPath(sh, fill);
        fill.setShader(null);
        drawGlassPath(c, sh, r, STYLE_KEY, false, t, 0.6f);
    }

    // ------------------------------------------------------------------ icons

    public static final int IC_SHIFT = 1, IC_SHIFT_ON = 2, IC_CAPS = 3, IC_DEL = 4, IC_ENTER = 5, IC_SEARCH = 6,
            IC_SEND = 7, IC_EMOJI = 8, IC_CLIP = 9, IC_SNIPPET = 10, IC_CALC = 11, IC_EDIT = 12, IC_MIC = 13,
            IC_GEAR = 14, IC_KEYBOARD = 15, IC_LEFT = 16, IC_RIGHT = 17, IC_UP = 18, IC_DOWN = 19, IC_SWAP = 20,
            IC_EXPAND = 21, IC_PIN = 22, IC_CLOSE = 23, IC_CHECK = 24, IC_UNDO = 25, IC_REDO = 26, IC_COPY = 27,
            IC_CUT = 28, IC_PASTE = 29, IC_SELECT = 30, IC_TRASH = 31, IC_PLUS = 32, IC_ONEHAND = 33,
            IC_GLOBE = 34, IC_HIDE = 35, IC_GRID = 36, IC_ARROW_RIGHT = 37, IC_TEXT_CASE = 38, IC_PIN_ON = 39, IC_SPARKLE = 40,
            IC_PHONE = 41, IC_MAP = 42, IC_RUPEE = 43, IC_SHOP = 44, IC_QR = 45, IC_CLOCK = 46, IC_CONTACT = 47;

    /** Draws an outline icon designed on a 24x24 grid, centred on (cx, cy). */
    public void drawIcon(Canvas c, int id, float cx, float cy, float size, int color) {
        float s = size / 24f;
        c.save();
        c.translate(cx - size / 2, cy - size / 2);
        c.scale(s, s);
        icon.setColor(color);
        icon.setStrokeWidth(1.9f);
        iconFill.setColor(color);
        Path p = path;
        p.reset();
        switch (id) {
            case IC_SHIFT: case IC_SHIFT_ON: case IC_CAPS:
                p.moveTo(12, 3.5f); p.lineTo(20.5f, 12.5f); p.lineTo(15.5f, 12.5f); p.lineTo(15.5f, 18.5f);
                p.lineTo(8.5f, 18.5f); p.lineTo(8.5f, 12.5f); p.lineTo(3.5f, 12.5f); p.close();
                if (id != IC_SHIFT) c.drawPath(p, iconFill);
                c.drawPath(p, icon);
                if (id == IC_CAPS) c.drawLine(8.5f, 21.5f, 15.5f, 21.5f, icon);
                break;
            case IC_DEL:
                p.moveTo(8, 5); p.lineTo(21, 5); p.lineTo(21, 19); p.lineTo(8, 19); p.lineTo(2.5f, 12); p.close();
                c.drawPath(p, icon);
                c.drawLine(11.5f, 9, 16.5f, 15, icon);
                c.drawLine(16.5f, 9, 11.5f, 15, icon);
                break;
            case IC_ENTER:
                p.moveTo(19.5f, 5); p.lineTo(19.5f, 13); p.lineTo(5, 13);
                c.drawPath(p, icon);
                p.reset(); p.moveTo(9, 9); p.lineTo(5, 13); p.lineTo(9, 17);
                c.drawPath(p, icon);
                break;
            case IC_SEARCH:
                c.drawCircle(10.5f, 10.5f, 6, icon);
                c.drawLine(15, 15, 20.5f, 20.5f, icon);
                break;
            case IC_SEND:
                p.moveTo(3.5f, 4.5f); p.lineTo(21, 12); p.lineTo(3.5f, 19.5f); p.lineTo(6.5f, 12); p.close();
                c.drawPath(p, icon);
                c.drawLine(6.5f, 12, 12.5f, 12, icon);
                break;
            case IC_ARROW_RIGHT:
                c.drawLine(4, 12, 20, 12, icon);
                p.moveTo(14, 6); p.lineTo(20, 12); p.lineTo(14, 18);
                c.drawPath(p, icon);
                break;
            case IC_EMOJI:
                c.drawCircle(12, 12, 9, icon);
                iconFill.setColor(color);
                c.drawCircle(9, 10, 1.2f, iconFill);
                c.drawCircle(15, 10, 1.2f, iconFill);
                tmp.set(7.5f, 9.5f, 16.5f, 17);
                c.drawArc(tmp, 20, 140, false, icon);
                break;
            case IC_CLIP:
                tmp.set(5, 4.5f, 19, 21); c.drawRoundRect(tmp, 2.5f, 2.5f, icon);
                tmp.set(9, 2.5f, 15, 6.5f); c.drawRoundRect(tmp, 1.5f, 1.5f, icon);
                c.drawLine(8.5f, 11, 15.5f, 11, icon);
                c.drawLine(8.5f, 15, 13.5f, 15, icon);
                break;
            case IC_SNIPPET:
                tmp.set(3, 4, 21, 17); c.drawRoundRect(tmp, 3, 3, icon);
                p.moveTo(8, 17); p.lineTo(7, 21); p.lineTo(12, 17);
                c.drawPath(p, icon);
                p.reset(); p.moveTo(12.8f, 6.5f); p.lineTo(9.5f, 11); p.lineTo(12.5f, 11); p.lineTo(11.2f, 14.5f);
                p.lineTo(14.5f, 10); p.lineTo(11.5f, 10); p.close();
                c.drawPath(p, iconFill);
                break;
            case IC_CALC:
                tmp.set(5, 2.5f, 19, 21.5f); c.drawRoundRect(tmp, 2.5f, 2.5f, icon);
                tmp.set(8, 5.5f, 16, 9); c.drawRoundRect(tmp, 1, 1, icon);
                for (int yy = 0; yy < 3; yy++) for (int xx = 0; xx < 3; xx++)
                    c.drawCircle(8.8f + xx * 3.2f, 12.5f + yy * 3, 0.9f, iconFill);
                break;
            case IC_EDIT:
                c.drawLine(12, 4, 12, 20, icon);
                c.drawLine(9, 4, 15, 4, icon);
                c.drawLine(9, 20, 15, 20, icon);
                p.moveTo(6, 9); p.lineTo(3, 12); p.lineTo(6, 15);
                p.moveTo(18, 9); p.lineTo(21, 12); p.lineTo(18, 15);
                c.drawPath(p, icon);
                break;
            case IC_MIC:
                tmp.set(9, 3, 15, 14); c.drawRoundRect(tmp, 3, 3, icon);
                tmp.set(5.5f, 5, 18.5f, 17); c.drawArc(tmp, 20, 140, false, icon);
                c.drawLine(12, 17, 12, 21, icon);
                c.drawLine(9, 21, 15, 21, icon);
                break;
            case IC_GEAR:
                c.drawCircle(12, 12, 3, icon);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine((float) (12 + Math.cos(a) * 6.5), (float) (12 + Math.sin(a) * 6.5),
                            (float) (12 + Math.cos(a) * 9), (float) (12 + Math.sin(a) * 9), icon);
                }
                c.drawCircle(12, 12, 6.5f, icon);
                break;
            case IC_KEYBOARD:
                tmp.set(2.5f, 5.5f, 21.5f, 18.5f); c.drawRoundRect(tmp, 2.5f, 2.5f, icon);
                for (int i = 0; i < 5; i++) c.drawCircle(6 + i * 3, 9.5f, 0.9f, iconFill);
                for (int i = 0; i < 4; i++) c.drawCircle(7.5f + i * 3, 12.5f, 0.9f, iconFill);
                c.drawLine(8, 15.5f, 16, 15.5f, icon);
                break;
            case IC_HIDE:
                tmp.set(2.5f, 3, 21.5f, 14.5f); c.drawRoundRect(tmp, 2.5f, 2.5f, icon);
                c.drawLine(7, 11, 17, 11, icon);
                p.moveTo(9, 18); p.lineTo(12, 21); p.lineTo(15, 18);
                c.drawPath(p, icon);
                break;
            case IC_LEFT: p.moveTo(15, 5); p.lineTo(8, 12); p.lineTo(15, 19); c.drawPath(p, icon); break;
            case IC_RIGHT: p.moveTo(9, 5); p.lineTo(16, 12); p.lineTo(9, 19); c.drawPath(p, icon); break;
            case IC_UP: p.moveTo(5, 15); p.lineTo(12, 8); p.lineTo(19, 15); c.drawPath(p, icon); break;
            case IC_DOWN: p.moveTo(5, 9); p.lineTo(12, 16); p.lineTo(19, 9); c.drawPath(p, icon); break;
            case IC_SWAP:
                c.drawLine(4, 8, 20, 8, icon); p.moveTo(16, 4); p.lineTo(20, 8); p.lineTo(16, 12);
                c.drawLine(4, 16, 20, 16, icon); p.moveTo(8, 12); p.lineTo(4, 16); p.lineTo(8, 20);
                c.drawPath(p, icon);
                break;
            case IC_EXPAND:
                p.moveTo(4, 9); p.lineTo(4, 4); p.lineTo(9, 4);
                p.moveTo(15, 4); p.lineTo(20, 4); p.lineTo(20, 9);
                p.moveTo(20, 15); p.lineTo(20, 20); p.lineTo(15, 20);
                p.moveTo(9, 20); p.lineTo(4, 20); p.lineTo(4, 15);
                c.drawPath(p, icon);
                break;
            case IC_PIN: case IC_PIN_ON:
                p.moveTo(9, 3.5f); p.lineTo(15, 3.5f); p.lineTo(14, 9.5f); p.lineTo(17.5f, 13); p.lineTo(6.5f, 13);
                p.lineTo(10, 9.5f); p.close();
                if (id == IC_PIN_ON) c.drawPath(p, iconFill);
                c.drawPath(p, icon);
                c.drawLine(12, 13, 12, 21, icon);
                break;
            case IC_CLOSE: c.drawLine(6, 6, 18, 18, icon); c.drawLine(18, 6, 6, 18, icon); break;
            case IC_CHECK: p.moveTo(4.5f, 12.5f); p.lineTo(9.5f, 17.5f); p.lineTo(19.5f, 6.5f); c.drawPath(p, icon); break;
            case IC_UNDO: case IC_REDO:
                if (id == IC_REDO) { c.scale(-1, 1, 12, 12); }
                p.moveTo(8, 5); p.lineTo(4, 9); p.lineTo(8, 13);
                p.moveTo(4, 9); p.lineTo(14, 9);
                p.cubicTo(18, 9, 20.5f, 11.5f, 20.5f, 14.5f); p.cubicTo(20.5f, 17.5f, 18, 20, 14, 20); p.lineTo(9, 20);
                c.drawPath(p, icon);
                break;
            case IC_COPY:
                tmp.set(8, 8, 20, 20.5f); c.drawRoundRect(tmp, 2, 2, icon);
                p.moveTo(16, 8); p.lineTo(16, 5.5f); p.cubicTo(16, 4.4f, 15.1f, 3.5f, 14, 3.5f);
                p.lineTo(6, 3.5f); p.cubicTo(4.9f, 3.5f, 4, 4.4f, 4, 5.5f); p.lineTo(4, 14);
                p.cubicTo(4, 15.1f, 4.9f, 16, 6, 16); p.lineTo(8, 16);
                c.drawPath(p, icon);
                break;
            case IC_CUT:
                c.drawCircle(6.5f, 17.5f, 3, icon); c.drawCircle(17.5f, 17.5f, 3, icon);
                c.drawLine(8.5f, 15.3f, 18, 3.5f, icon); c.drawLine(15.5f, 15.3f, 6, 3.5f, icon);
                break;
            case IC_PASTE:
                tmp.set(5, 4.5f, 19, 21); c.drawRoundRect(tmp, 2.5f, 2.5f, icon);
                tmp.set(9, 2.5f, 15, 6.5f); c.drawRoundRect(tmp, 1.5f, 1.5f, iconFill);
                break;
            case IC_SELECT:
                icon.setPathEffect(new android.graphics.DashPathEffect(new float[]{2.5f, 2.2f}, 0));
                tmp.set(3.5f, 5, 20.5f, 19); c.drawRoundRect(tmp, 2, 2, icon);
                icon.setPathEffect(null);
                c.drawLine(8, 10, 16, 10, icon); c.drawLine(8, 14, 13.5f, 14, icon);
                break;
            case IC_TRASH:
                c.drawLine(4, 6.5f, 20, 6.5f, icon);
                p.moveTo(6, 6.5f); p.lineTo(7, 20.5f); p.lineTo(17, 20.5f); p.lineTo(18, 6.5f);
                p.moveTo(9.5f, 6.5f); p.lineTo(9.5f, 3.5f); p.lineTo(14.5f, 3.5f); p.lineTo(14.5f, 6.5f);
                c.drawPath(p, icon);
                break;
            case IC_PLUS: c.drawLine(12, 5, 12, 19, icon); c.drawLine(5, 12, 19, 12, icon); break;
            case IC_ONEHAND:
                tmp.set(9, 3, 20, 21); c.drawRoundRect(tmp, 2.5f, 2.5f, icon);
                p.moveTo(6, 9); p.lineTo(3, 12); p.lineTo(6, 15);
                c.drawPath(p, icon);
                break;
            case IC_GLOBE:
                c.drawCircle(12, 12, 9, icon);
                c.drawLine(3, 12, 21, 12, icon);
                tmp.set(8, 3, 16, 21); c.drawOval(tmp, icon);
                break;
            case IC_GRID:
                for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) {
                    tmp.set(4 + xx * 9, 4 + yy * 9, 11 + xx * 9, 11 + yy * 9);
                    c.drawRoundRect(tmp, 2, 2, icon);
                }
                break;
            case IC_PHONE:
                p.moveTo(6.5f, 3.5f); p.lineTo(9.5f, 3.5f); p.lineTo(11, 8); p.lineTo(8.8f, 9.6f);
                p.cubicTo(9.9f, 12.1f, 11.9f, 14.1f, 14.4f, 15.2f); p.lineTo(16, 13); p.lineTo(20.5f, 14.5f);
                p.lineTo(20.5f, 17.5f); p.cubicTo(20.5f, 19.2f, 19.2f, 20.5f, 17.5f, 20.5f);
                p.cubicTo(10.3f, 20.1f, 3.9f, 13.7f, 3.5f, 6.5f); p.cubicTo(3.5f, 4.8f, 4.8f, 3.5f, 6.5f, 3.5f); p.close();
                c.drawPath(p, icon);
                break;
            case IC_MAP:
                p.moveTo(12, 21); p.cubicTo(7, 15.5f, 5, 12.5f, 5, 9.5f);
                p.cubicTo(5, 5.6f, 8.1f, 2.5f, 12, 2.5f); p.cubicTo(15.9f, 2.5f, 19, 5.6f, 19, 9.5f);
                p.cubicTo(19, 12.5f, 17, 15.5f, 12, 21); p.close();
                c.drawPath(p, icon);
                c.drawCircle(12, 9.5f, 2.6f, icon);
                break;
            case IC_RUPEE:
                c.drawLine(6.5f, 4, 17.5f, 4, icon);
                c.drawLine(6.5f, 8.5f, 17.5f, 8.5f, icon);
                p.moveTo(6.5f, 4); p.lineTo(10, 4); p.cubicTo(13.5f, 4, 14.5f, 6.2f, 14.5f, 7.5f);
                p.cubicTo(14.5f, 10.5f, 12, 13, 8.5f, 13); p.lineTo(6.5f, 13); p.lineTo(15, 21);
                c.drawPath(p, icon);
                break;
            case IC_SHOP:
                p.moveTo(3.5f, 9); p.lineTo(5, 4); p.lineTo(19, 4); p.lineTo(20.5f, 9); p.close();
                c.drawPath(p, icon);
                p.reset(); p.moveTo(5, 9); p.lineTo(5, 20); p.lineTo(19, 20); p.lineTo(19, 9);
                c.drawPath(p, icon);
                tmp.set(9.5f, 13.5f, 14.5f, 20); c.drawRect(tmp, icon);
                break;
            case IC_QR:
                for (int qy = 0; qy < 2; qy++) for (int qx = 0; qx < 2; qx++) {
                    if (qx == 1 && qy == 1) continue;
                    tmp.set(3.5f + qx * 10, 3.5f + qy * 10, 10.5f + qx * 10, 10.5f + qy * 10);
                    c.drawRoundRect(tmp, 1.5f, 1.5f, icon);
                    c.drawRect(5.8f + qx * 10, 5.8f + qy * 10, 8.2f + qx * 10, 8.2f + qy * 10, iconFill);
                }
                c.drawRect(14, 14, 16.2f, 16.2f, iconFill); c.drawRect(18, 18, 20.5f, 20.5f, iconFill);
                c.drawRect(18, 14, 20.5f, 16.2f, iconFill); c.drawRect(14, 18, 16.2f, 20.5f, iconFill);
                break;
            case IC_CLOCK:
                c.drawCircle(12, 12, 8.5f, icon);
                c.drawLine(12, 12, 12, 7, icon);
                c.drawLine(12, 12, 15.5f, 14, icon);
                break;
            case IC_CONTACT:
                c.drawCircle(10, 8.5f, 3.5f, icon);
                p.moveTo(3.5f, 19.5f); p.cubicTo(3.5f, 15.5f, 6.5f, 14, 10, 14); p.cubicTo(12, 14, 13.5f, 14.4f, 14.5f, 15.2f);
                c.drawPath(p, icon);
                c.drawLine(18, 13.5f, 18, 20.5f, icon); c.drawLine(14.5f, 17, 21.5f, 17, icon);
                break;
            case IC_SPARKLE:
                p.moveTo(10, 2.5f); p.quadTo(10.8f, 9.2f, 17.5f, 10); p.quadTo(10.8f, 10.8f, 10, 17.5f);
                p.quadTo(9.2f, 10.8f, 2.5f, 10); p.quadTo(9.2f, 9.2f, 10, 2.5f); p.close();
                c.drawPath(p, iconFill);
                p.reset();
                p.moveTo(18, 14); p.quadTo(18.4f, 17.6f, 22, 18); p.quadTo(18.4f, 18.4f, 18, 22);
                p.quadTo(17.6f, 18.4f, 14, 18); p.quadTo(17.6f, 17.6f, 18, 14); p.close();
                c.drawPath(p, iconFill);
                break;
            case IC_TEXT_CASE:
                icon.setStyle(Paint.Style.FILL);
                icon.setTextSize(13);
                icon.setFakeBoldText(true);
                icon.setTextAlign(Paint.Align.CENTER);
                c.drawText("Aa", 12, 16.5f, icon);
                icon.setStyle(Paint.Style.STROKE);
                icon.setFakeBoldText(false);
                break;
        }
        c.restore();
    }

    public float dp() { return dp; }
    public Rect scratchRect = new Rect();
}
