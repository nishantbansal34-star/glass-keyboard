package com.nishant.glasskeys;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
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

    public void drawGlass(Canvas c, RectF r, float radius, int style, boolean pressed, Theme t) {
        drawGlass(c, r, radius, style, pressed, t, 0f);
    }

    /** Rounded-rect liquid glass. {@code glow} (0..1) brightens the rim when the moving light is near. */
    public void drawGlass(Canvas c, RectF r, float radius, int style, boolean pressed, Theme t, float glow) {
        shape.reset();
        shape.addRoundRect(r, radius, radius, Path.Direction.CW);
        drawGlassPath(c, shape, r, style, pressed, t, glow);
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
        c.save();
        c.translate(0, 3 * dp);
        shadow.setColor(t.dark ? 0x40000000 : 0x261B2A4A);
        c.drawPath(sh, shadow);
        c.restore();
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                t.dark ? 0xE6363B5C : 0xF7FFFFFF, t.dark ? 0xD92A2E48 : 0xEEF2F5FA, Shader.TileMode.CLAMP));
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
            IC_GLOBE = 34, IC_HIDE = 35, IC_GRID = 36, IC_ARROW_RIGHT = 37, IC_TEXT_CASE = 38, IC_PIN_ON = 39;

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
