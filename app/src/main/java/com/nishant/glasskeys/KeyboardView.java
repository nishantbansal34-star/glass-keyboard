package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The letter/symbol key area. Draws glass keys and handles all touch gestures. */
public class KeyboardView extends View {

    public interface Listener {
        void onText(String text);
        void onKey(int code);
        void onCursorMove(int steps);
        void onDeleteWords(int count);
        void onSpaceLongPress();
        void onOneHandedSwap();
        void onOneHandedExit();
        void feedback(boolean strong);
        void onTouchPoint(float x, float y);
        void onGlide(List<String> words);
        void onGlidePreview(List<String> words);
    }

    public static final int SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_LOCK = 2;

    private Listener listener;
    private final GlassPainter gp;
    private Theme theme = Theme.get(0);
    private final float dp;
    private List<List<Key>> rows;
    private int page = Layouts.ALPHA;
    private boolean numberRow = true;
    private int shift = SHIFT_OFF;
    private long lastShiftTap;
    private boolean shiftHeld, shiftUsedWhileHeld;
    private int oneHanded = 0;
    private final RectF sideBtnA = new RectF(), sideBtnB = new RectF();
    private boolean keyPopupEnabled = true, rippleEnabled = true;

    private int enterIcon = GlassPainter.IC_ENTER;
    private String enterText = null;
    private String spaceLabel = "space";
    public boolean capsLabels = true;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());

    // Touch tracking (multi-touch, like fast two-thumb typing)
    private static class Ptr {
        Key key;
        float downX, downY, lastX;
        boolean consumed, popup, spaceSwipe, delSwipe;
        int popupIndex, cursorSteps, delWords;
    }
    private final SparseArray<Ptr> ptrs = new SparseArray<>();
    private Ptr longPressTarget;
    private Ptr popupPtr;
    private final RectF popupRect = new RectF();
    private int popupCols, popupRows;
    private float popupCellW, popupCellH;

    // Ripple of light across the glass on each press
    private float rippleX, rippleY;
    private long rippleStart = -1;

    private int repeatCount;

    public View overlay; // full-size view that draws previews/popups above everything

    // ---- fluid motion state
    private final Map<Key, Spring> pressSpring = new IdentityHashMap<>();
    private final Map<Key, Spring> appear = new IdentityHashMap<>();
    private final Map<Key, Long> appearAt = new IdentityHashMap<>();
    private static class Drop { Key key; String label; final Spring s = new Spring(520f, 0.58f); }
    private final List<Drop> drops = new ArrayList<>();
    private long lastFrame;
    public LiquidBackdrop backdrop;
    public float rootW = 1, rootH = 1;
    private final RectF tmpR = new RectF();
    private final android.graphics.Path dropPath = new android.graphics.Path();
    private final android.graphics.Path partPath = new android.graphics.Path();

    // ---- swipe typing
    public GlideDecoder decoder;
    public boolean glideEnabled = true, trailEnabled = true;
    private boolean gliding;
    private int glidePointer = -1;
    private float keyUnit = 1;
    private float[] gx = new float[512], gy = new float[512];
    private long[] gt = new long[512];
    private int gn;
    private long glideEnd = -1, lastPreview;
    private final Paint trailGlow = new Paint(Paint.ANTI_ALIAS_FLAG), trailCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    { trailGlow.setStrokeCap(Paint.Cap.ROUND); trailCore.setStrokeCap(Paint.Cap.ROUND); }

    public boolean isGliding() { return gliding; }

    private void addGlidePoint(float x, float y, long t) {
        if (gn > 0 && Math.abs(gx[gn - 1] - x) < 0.5f && Math.abs(gy[gn - 1] - y) < 0.5f) return;
        if (gn == gx.length) {
            gx = java.util.Arrays.copyOf(gx, gn * 2);
            gy = java.util.Arrays.copyOf(gy, gn * 2);
            gt = java.util.Arrays.copyOf(gt, gn * 2);
        }
        gx[gn] = x; gy[gn] = y; gt[gn] = t; gn++;
    }

    private List<String> decodeGlide(boolean partial) {
        if (decoder == null || gn < 2) return new ArrayList<>();
        float[] ux = new float[gn], uy = new float[gn];
        for (int i = 0; i < gn; i++) { ux[i] = gx[i] / keyUnit; uy[i] = gy[i] / keyUnit; }
        return decoder.decode(ux, uy, gn, null, partial).words;
    }

    private void updateDecoderLayout() {
        if (decoder == null || page != Layouts.ALPHA) return;
        for (List<Key> row : rows) for (Key k : row) {
            if (k.label.length() == 1) {
                char ch = k.label.charAt(0);
                if (ch >= 'a' && ch <= 'z') decoder.setKey(ch, k.rect.centerX() / keyUnit, k.rect.centerY() / keyUnit);
            }
        }
    }

    private final android.graphics.Path ribbon = new android.graphics.Path();
    private final android.graphics.Path headDot = new android.graphics.Path();
    private final Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.BlurMaskFilter glowBlur;
    private float[] sx = new float[256], sy = new float[256], sw = new float[256];

    /**
     * Glowing liquid trail drawn as one continuous ribbon: points are smoothed, the width tapers
     * from a rounded head under the finger to a fine point at the tail, and it fades as a whole.
     */
    private void drawTrail(Canvas c) {
        if (!trailEnabled || gn < 2) return;
        long now = SystemClock.uptimeMillis();
        float global = 1f;
        if (!gliding) {
            if (glideEnd < 0) return;
            global = 1f - (now - glideEnd) / 280f;
            if (global <= 0) { gn = 0; glideEnd = -1; return; }
            global = global * global * (3 - 2 * global); // smoothstep
        }
        final float life = 560f;
        // while the finger rests, keep the trail alive instead of fading it out
        if (gliding) now = Math.min(now, gt[gn - 1] + 140);
        // 1. collect the visible part of the stroke, resampled every ~3dp and smoothed
        int first = gn - 1;
        while (first > 0 && now - gt[first - 1] < life) first--;
        float step = 3f * dp;
        int m = 0;
        float lx = gx[first], ly = gy[first];
        long lt = gt[first];
        if (sx.length < 400) { sx = new float[400]; sy = new float[400]; sw = new float[400]; }
        sx[m] = lx; sy[m] = ly; sw[m] = lt; m++;
        for (int i = first + 1; i < gn && m < 399; i++) {
            float dx = gx[i] - lx, dy = gy[i] - ly;
            float d = (float) Math.hypot(dx, dy);
            if (d < step) continue;
            int n = Math.min(12, (int) (d / step));
            for (int k = 1; k <= n && m < 399; k++) {
                float t = k / (float) n;
                sx[m] = lx + dx * t; sy[m] = ly + dy * t; sw[m] = lt + (gt[i] - lt) * t; m++;
            }
            lx = gx[i]; ly = gy[i]; lt = gt[i];
        }
        if (m < 2) return;
        // light smoothing (keeps the ends fixed)
        for (int pass = 0; pass < 2; pass++)
            for (int i = 1; i < m - 1; i++) {
                sx[i] = (sx[i - 1] + 2 * sx[i] + sx[i + 1]) / 4f;
                sy[i] = (sy[i - 1] + 2 * sy[i] + sy[i + 1]) / 4f;
            }
        // 2. width per point: full at the head, tapering to nothing at the tail
        boolean prem = gp.amoled && gp.pack == 0;
        float maxW = (prem ? 2.8f : 5.5f) * dp;
        for (int i = 0; i < m; i++) {
            float age = Math.max(0f, (now - sw[i]) / life);
            float byAge = (float) Math.pow(Math.max(0f, 1f - age), 0.7);
            float byPos = Math.min(1f, (i + 1) / (float) Math.max(6, m * 0.35f));
            sw[i] = maxW * byAge * byPos;
        }

        int accent = theme.accent & 0x00FFFFFF;
        if (glowBlur == null) glowBlur = new android.graphics.BlurMaskFilter(7 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL);
        ribbonPaint.setStyle(Paint.Style.FILL);

        int glowCol = prem ? (theme.dark ? 0xC8D6F0 : 0x5A6B85) : accent;
        int coreCol = prem ? (theme.dark ? 0xF4F8FF : 0x2A3342) : 0xFFFFFF;
        // outer glow
        buildRibbon(m, prem ? 3.6f : 3.4f);
        ribbonPaint.setMaskFilter(glowBlur);
        ribbonPaint.setColor(((int) ((prem ? 46 : 120) * global) << 24) | glowCol);
        c.drawPath(ribbon, ribbonPaint);
        ribbonPaint.setMaskFilter(null);
        // inner glow
        buildRibbon(m, prem ? 1.8f : 1.9f);
        ribbonPaint.setColor(((int) ((prem ? 70 : 150) * global) << 24) | glowCol);
        c.drawPath(ribbon, ribbonPaint);
        // luminous core
        buildRibbon(m, 1f);
        ribbonPaint.setColor(((int) ((prem ? 200 : 245) * global) << 24) | coreCol);
        c.drawPath(ribbon, ribbonPaint);
    }

    /** Builds a closed, smoothly curved ribbon outline around the sampled stroke. */
    private void buildRibbon(int m, float scale) {
        ribbon.reset();
        float[] lxs = new float[m], lys = new float[m], rxs = new float[m], rys = new float[m];
        for (int i = 0; i < m; i++) {
            int a = Math.max(0, i - 1), b = Math.min(m - 1, i + 1);
            float tx = sx[b] - sx[a], ty = sy[b] - sy[a];
            float len = (float) Math.hypot(tx, ty);
            if (len < 1e-3f) { tx = 1; ty = 0; len = 1; }
            float nx = -ty / len, ny = tx / len;
            float hw = sw[i] * scale / 2f;
            lxs[i] = sx[i] + nx * hw; lys[i] = sy[i] + ny * hw;
            rxs[i] = sx[i] - nx * hw; rys[i] = sy[i] - ny * hw;
        }
        ribbon.moveTo(lxs[0], lys[0]);
        for (int i = 1; i < m - 1; i++)
            ribbon.quadTo(lxs[i], lys[i], (lxs[i] + lxs[i + 1]) / 2f, (lys[i] + lys[i + 1]) / 2f);
        ribbon.lineTo(lxs[m - 1], lys[m - 1]);
        ribbon.lineTo(rxs[m - 1], rys[m - 1]);
        for (int i = m - 2; i > 0; i--)
            ribbon.quadTo(rxs[i], rys[i], (rxs[i] + rxs[i - 1]) / 2f, (rys[i] + rys[i - 1]) / 2f);
        ribbon.lineTo(rxs[0], rys[0]);
        ribbon.close();
        // rounded head under the finger
        float hr = sw[m - 1] * scale / 2f;
        if (hr > 0.5f) {
            headDot.reset();
            headDot.addCircle(sx[m - 1], sy[m - 1], hr, android.graphics.Path.Direction.CW);
            ribbon.op(headDot, android.graphics.Path.Op.UNION);
        }
    }

    private final Map<Key, float[]> touchInfo = new IdentityHashMap<>();   // {x, y, startMillis}

    private void markTouch(Key k, float x, float y) {
        touchInfo.put(k, new float[]{x - k.rect.left, y - k.rect.top, SystemClock.uptimeMillis() % 1000000});
    }

    private Spring pressOf(Key k) {
        Spring sp = pressSpring.get(k);
        if (sp == null) { sp = gp.amoled && gp.pack == 0 ? new Spring(1500f, 1.0f) : new Spring(1100f, 0.42f); pressSpring.put(k, sp); }
        return sp;
    }

    /** Keys rise and settle row by row, like liquid filling a mould. */
    public void playEntrance() {
        long now = SystemClock.uptimeMillis();
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                Key k = row.get(i);
                Spring sp = (gp.amoled && gp.pack == 0 ? new Spring(360f, 0.9f) : new Spring(420f, 0.6f)).set(0f);
                sp.target = 1f;
                appear.put(k, sp);
                appearAt.put(k, now + r * 32L + Math.abs(i - row.size() / 2) * 14L);
            }
        }
        lastFrame = 0;
        invalidate();
    }

    private void startDrop(Key k) {
        if (!keyPopupEnabled || k == null || !k.isChar()) return;
        for (Drop d : drops) if (d.key == k) { d.s.target = 1f; d.label = labelFor(k); return; }
        Drop d = new Drop();
        d.key = k;
        d.label = labelFor(k);
        d.s.set(0f);
        d.s.target = 1f;
        drops.add(d);
    }

    private void endDrop(Key k) {
        for (Drop d : drops) if (d.key == k) d.s.target = 0f;
    }

    private String labelFor(Key k) {
        return shift != SHIFT_OFF ? k.label.toUpperCase() : k.label;
    }

    public KeyboardView(Context c, GlassPainter gp) {
        super(c);
        this.gp = gp;
        dp = c.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setLetterSpacing(0.01f);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        rows = Layouts.build(page, numberRow);
    }

    public void setListener(Listener l) { listener = l; }

    public void configure(Theme t, boolean numberRow, int oneHanded, boolean popup, boolean ripple) {
        theme = t;
        this.keyPopupEnabled = popup;
        this.rippleEnabled = ripple;
        boolean relayout = this.numberRow != numberRow || this.oneHanded != oneHanded;
        this.numberRow = numberRow;
        this.oneHanded = oneHanded;
        if (relayout) { rows = Layouts.build(page, numberRow); requestLayout(); layoutKeys(); }
        invalidate();
    }

    public int rowCount() { return rows.size(); }
    public int page() { return page; }

    public void setPage(int p) {
        page = p;
        rows = Layouts.build(p, numberRow);
        layoutKeys();
        updateDecoderLayout();
        invalidate();
    }

    public int shiftState() { return shift; }

    public void setShift(int s) {
        if (shift != s) { shift = s; invalidate(); }
    }

    public void setEnter(int icon, String label) {
        enterIcon = icon;
        enterText = label;
        invalidate();
    }

    public void setSpaceLabel(String s) { spaceLabel = s; invalidate(); }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int hgt, int ow, int oh) {
        layoutKeys();
    }

    private float keysLeft() {
        if (oneHanded == 1) return 0;
        if (oneHanded == 2) return getWidth() * 0.16f;
        return 0;
    }

    private float keysRight() {
        if (oneHanded == 1) return getWidth() * 0.84f;
        return getWidth();
    }

    private void layoutKeys() {
        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0) return;
        float padX = 3 * dp, padTop = 4 * dp, padBottom = 4 * dp;
        float gapX = 5 * dp, gapY = 8 * dp;
        float left = keysLeft() + padX, right = keysRight() - padX;
        float rowH = (H - padTop - padBottom) / rows.size();
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            float total = 0;
            for (Key k : row) total += k.weight;
            // Rows that are shorter than 10 units (e.g. 9 letters) keep standard key width and centre.
            float unit = (right - left) / Math.max(total, 10f);
            float x = left + ((right - left) - unit * total) / 2f;
            float top = padTop + r * rowH;
            for (Key k : row) {
                float w = unit * k.weight;
                k.rect.set(x + gapX / 2, top + gapY / 2, x + w - gapX / 2, top + rowH - gapY / 2);
                x += w;
            }
        }
        keyUnit = Math.max(1f, (right - left) / 10f);
        updateDecoderLayout();
        if (oneHanded != 0) {
            float colL = oneHanded == 1 ? keysRight() : 0;
            float colR = oneHanded == 1 ? W : keysLeft();
            float cx = (colL + colR) / 2, bw = Math.min(colR - colL - 12 * dp, 44 * dp);
            sideBtnA.set(cx - bw / 2, H * 0.22f - bw / 2, cx + bw / 2, H * 0.22f + bw / 2);
            sideBtnB.set(cx - bw / 2, H * 0.55f - bw / 2, cx + bw / 2, H * 0.55f + bw / 2);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean animating = false;

        float lx = 0, ly = 0, lp = 0;
        if (backdrop != null && rippleEnabled) {
            lx = backdrop.lightX * rootW - getLeft();
            ly = backdrop.lightY * rootH - overflowAbove;
            lp = backdrop.lightPower;
            if (lp > 0.01f) animating = true;
        }
        float radius = 10 * dp;
        float reach = 130 * dp;
        gp.setOriginFromView(this);
        gp.rootWidth = (int) rootW;
        gp.lightX = rootW * 0.38f;

        for (List<Key> row : rows) for (Key k : row) {
            if (k.code == 0) continue;
            boolean pressed = isPressed(k);
            Spring ps = pressSpring.get(k);
            float pv = 0;
            if (ps != null) { if (ps.step(dt)) animating = true; pv = ps.value; }
            float a = 1f;
            Spring ap = appear.get(k);
            if (ap != null) {
                Long at = appearAt.get(k);
                if (at != null && now < at) { a = 0f; animating = true; }
                else { if (ap.step(dt)) animating = true; a = ap.value; }
                if (!animating && a >= 1f) appear.remove(k);
            }
            if (a <= 0.01f) continue;

            int style = GlassPainter.STYLE_KEY;
            if (k.code == Key.ENTER) style = gp.pack == 1 ? GlassPainter.STYLE_ACTION : GlassPainter.STYLE_FUNC;
            else if (k.code == Key.SHIFT && shift == SHIFT_LOCK) style = GlassPainter.STYLE_ACTIVE;
            else if (k.isFunction()) style = GlassPainter.STYLE_FUNC;

            RectF r = k.rect;
            float glow = 0;
            if (lp > 0.01f) {
                float d = (float) Math.hypot(r.centerX() - lx, r.centerY() - ly);
                glow = lp * Math.max(0f, 1f - d / reach);
            }
            glow = Math.min(1f, glow + pv * 0.7f);
            boolean premium = gp.amoled && gp.pack == 0;
            // premium glass compresses slightly; other styles swell like liquid
            float sc = (premium ? 1f - 0.035f * pv : 1f + 0.07f * pv) * (0.72f + 0.28f * a);
            float ty = (1f - a) * 14 * dp;
            c.save();
            c.translate(r.centerX(), r.centerY() + ty);
            c.scale(sc, sc);
            c.translate(-r.centerX(), -r.centerY());
            gp.variant = System.identityHashCode(k);
            float kr = k.code == Key.SPACE && premium ? r.height() / 2f : radius;   // space bar = glass capsule
            if (premium) {
                gp.pressAmt = pv;
                float[] ti = touchInfo.get(k);
                if (ti != null) {
                    float age = ((SystemClock.uptimeMillis() % 1000000) - ti[2]) / 380f;
                    if (age < 0) age = 1f;
                    gp.touchX = r.left + ti[0];
                    gp.touchY = r.top + ti[1];
                    gp.ripple = age < 1f ? age : -1f;
                    if (age < 1f) animating = true;
                    else if (pv < 0.01f) touchInfo.remove(k);
                }
                gp.drawGlass(c, r, kr, style, pressed, theme, glow * 0.6f);
                gp.pressAmt = 0; gp.touchX = -1; gp.touchY = -1; gp.ripple = -1;
            } else {
                gp.drawGlass(c, r, kr, style, pressed, theme, glow);
            }
            gp.variant = 0;
            drawKeyContent(c, k);
            c.restore();
        }
        if (oneHanded != 0) {
            gp.drawGlass(c, sideBtnA, sideBtnA.width() / 2, GlassPainter.STYLE_FUNC, false, theme);
            gp.drawIcon(c, GlassPainter.IC_SWAP, sideBtnA.centerX(), sideBtnA.centerY(), 20 * dp, theme.text);
            gp.drawGlass(c, sideBtnB, sideBtnB.width() / 2, GlassPainter.STYLE_FUNC, false, theme);
            gp.drawIcon(c, GlassPainter.IC_EXPAND, sideBtnB.centerX(), sideBtnB.centerY(), 20 * dp, theme.text);
        }

        // droplet previews live in the overlay; keep it in step
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            if (d.s.step(dt)) animating = true;
            else if (d.s.target == 0f) drops.remove(i);
        }
        boolean trailAlive = gn > 0 && (gliding || glideEnd >= 0);
        if (overlay != null && (!drops.isEmpty() || popupPtr != null || trailAlive)) overlay.invalidate();
        if (animating || !drops.isEmpty() || (trailAlive && !gliding)) postInvalidateOnAnimation(); else lastFrame = 0;
    }

    private boolean isPressed(Key k) {
        for (int i = 0; i < ptrs.size(); i++) if (ptrs.valueAt(i).key == k) return true;
        return false;
    }

    private void drawKeyContent(Canvas c, Key k) {
        RectF r = k.rect;
        float cx = r.centerX(), cy = r.centerY();
        int col = theme.text;
        float icon = Math.min(r.height() * 0.5f, 24 * dp);
        switch (k.code) {
            case Key.SHIFT:
                int id = shift == SHIFT_OFF ? GlassPainter.IC_SHIFT : shift == SHIFT_ON ? GlassPainter.IC_SHIFT_ON : GlassPainter.IC_CAPS;
                gp.drawIcon(c, id, cx, cy, icon, shift == SHIFT_LOCK ? 0xFFFFFFFF : col);
                return;
            case Key.DELETE:
                Ptr p = ptrFor(k);
                if (p != null && p.delSwipe) {
                    label(c, "− " + p.delWords + (p.delWords == 1 ? " word" : " words"), cx, cy, r.height() * 0.24f, col, true);
                } else gp.drawIcon(c, GlassPainter.IC_DEL, cx, cy, icon, col);
                return;
            case Key.ENTER:
                if (enterText != null) label(c, enterText, cx, cy, r.height() * 0.32f, col, true);
                else gp.drawIcon(c, enterIcon, cx, cy, icon, col);
                return;
            case Key.EMOJI:
                gp.drawIcon(c, GlassPainter.IC_EMOJI, cx, cy, icon * 0.95f, col);
                return;
            case Key.SPACE:
                Ptr sp = ptrFor(k);
                if (sp != null && sp.spaceSwipe) {
                    gp.drawIcon(c, GlassPainter.IC_LEFT, cx - 22 * dp, cy, icon * 0.8f, theme.subText);
                    gp.drawIcon(c, GlassPainter.IC_RIGHT, cx + 22 * dp, cy, icon * 0.8f, theme.subText);
                } else label(c, spaceLabel, cx, cy, r.height() * 0.25f,
                        gp.amoled && gp.pack == 0 ? (theme.text & 0x00FFFFFF) | 0x66000000 : theme.subText, false);
                return;
            case Key.SYMBOLS: case Key.ALPHA: case Key.SYMBOLS2:
                label(c, k.label, cx, cy, r.height() * 0.3f, col, true);
                return;
        }
        String s = k.label;
        boolean letter = s.length() == 1 && Character.isLetter(s.charAt(0));
        if (letter && (shift != SHIFT_OFF || capsLabels)) s = s.toUpperCase();
        label(c, s, cx, cy, r.height() * (letter ? 0.4f : 0.4f), col, false);
        if (k.hint != null) {
            hintPaint.setColor(theme.subText);
            hintPaint.setTextSize(r.height() * 0.22f);
            c.drawText(k.hint, r.right - r.width() * 0.2f, r.top + r.height() * 0.3f, hintPaint);
        }
    }

    private Ptr ptrFor(Key k) {
        for (int i = 0; i < ptrs.size(); i++) if (ptrs.valueAt(i).key == k) return ptrs.valueAt(i);
        return null;
    }

    private static final Typeface TF_REG = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface TF_MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private void label(Canvas c, String s, float cx, float cy, float size, int color, boolean bold) {
        boolean premium = gp.pack == 0 && gp.amoled;
        text.setTypeface(premium ? (bold ? TF_MED : TF_REG) : TF_MED);
        if (gp.pack == 1) text.setShadowLayer(6 * dp, 0, 0, ((color >>> 24) * 0xB3 / 255) << 24 | 0x8B5CFF);
        else if (theme.dark && !gp.amoled) text.setShadowLayer(3 * dp, 0, 1 * dp, ((color >>> 24) * 0x66 / 255) << 24);
        else text.clearShadowLayer();
        text.setColor(color);
        text.setTextSize(size);
        text.setFakeBoldText(bold && !premium);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, text);
        text.setFakeBoldText(false);
    }

    private void drawRipple(Canvas c) {
        if (rippleStart < 0) return;
        float t = (SystemClock.uptimeMillis() - rippleStart) / 420f;
        if (t >= 1f) { rippleStart = -1; return; }
        float rad = 20 * dp + t * getWidth() * 0.35f;
        int a = (int) ((1f - t) * (theme.dark ? 70 : 110));
        ripplePaint.setShader(new RadialGradient(rippleX, rippleY, rad,
                new int[]{0x00FFFFFF, (a << 24) | 0xFFFFFF, 0x00FFFFFF}, new float[]{0.55f, 0.85f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(rippleX, rippleY, rad, ripplePaint);
        postInvalidateOnAnimation();
    }

    /** Called by the overlay view so bubbles can float above the suggestion strip. */
    public void drawOverlay(Canvas c, float offsetY) {
        c.save();
        c.translate(getLeft(), offsetY);
        gp.setOrigin(getLeft(), offsetY);
        float minTop = -offsetY + 3 * dp;
        drawTrail(c);
        if (popupPtr != null && popupPtr.popup) {
            drawPopup(c);
        } else if (!gliding) {
            for (Drop d : drops) drawDrop(c, d, minTop);
        }
        c.restore();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** The pressed key stretches up into a liquid droplet that carries a big copy of the letter. */
    private void drawDrop(Canvas c, Drop d, float minTop) {
        float v = d.s.value;
        if (v <= 0.02f) return;
        RectF k = d.key.rect;
        float bw = Math.max(k.width() * 1.3f, 40 * dp), bh = k.height() * 1.22f;
        float top = Math.max(minTop, k.top - bh - 10 * dp);
        float bl = k.centerX() - bw / 2;
        if (bl < 3 * dp) bl = 3 * dp;
        if (bl + bw > getWidth() - 3 * dp) bl = getWidth() - 3 * dp - bw;
        // bubble grows out of the key
        tmpR.set(lerp(k.left, bl, v), lerp(k.top, top, v), lerp(k.right, bl + bw, v), lerp(k.bottom, top + bh, v));
        RectF b = new RectF(tmpR);
        float rb = Math.min(b.height(), b.width()) * 0.34f, rk = 11 * dp;
        dropPath.reset();
        dropPath.addRoundRect(b, rb, rb, android.graphics.Path.Direction.CW);
        if (b.bottom < k.top + rk) {
            partPath.reset();
            partPath.addRoundRect(k, rk, rk, android.graphics.Path.Direction.CW);
            dropPath.op(partPath, android.graphics.Path.Op.UNION);
            // concave "neck" joining bubble and key, thinner as the drop stretches
            float nt = b.bottom - rb * 0.9f, nb = k.top + rk * 0.9f, mid = (nt + nb) / 2;
            float pinch = lerp(k.width() * 0.46f, k.width() * 0.2f, Math.min(1f, v));
            float kl = Math.max(k.left, b.left), kr = Math.min(k.right, b.right);
            partPath.reset();
            partPath.moveTo(b.left + rb * 0.5f, nt);
            partPath.quadTo(k.centerX() - pinch, mid, kl, nb);
            partPath.lineTo(kr, nb);
            partPath.quadTo(k.centerX() + pinch, mid, b.right - rb * 0.5f, nt);
            partPath.close();
            dropPath.op(partPath, android.graphics.Path.Op.UNION);
        }
        RectF bounds = new RectF(b);
        bounds.union(k);
        gp.drawBubblePath(c, dropPath, bounds, theme);
        int alpha = (int) (255 * Math.max(0f, Math.min(1f, (v - 0.25f) / 0.6f)));
        text.setAlpha(255);
        int col = (theme.text & 0x00FFFFFF) | (alpha << 24);
        label(c, d.label, b.centerX(), b.centerY(), bh * 0.52f * Math.min(1.1f, v), col, false);
    }

    private void drawPopup(Canvas c) {
        Key k = popupPtr.key;
        String[] items = k.popups;
        gp.drawBubble(c, popupRect, 12 * dp, theme);
        for (int i = 0; i < items.length; i++) {
            int col = i % popupCols, row = i / popupCols;
            RectF cell = new RectF(popupRect.left + col * popupCellW, popupRect.top + row * popupCellH,
                    popupRect.left + (col + 1) * popupCellW, popupRect.top + (row + 1) * popupCellH);
            cell.inset(3 * dp, 3 * dp);
            boolean sel = i == popupPtr.popupIndex;
            if (sel) gp.drawGlass(c, cell, 8 * dp, GlassPainter.STYLE_ACTION, true, theme);
            String s = shift != SHIFT_OFF ? items[i].toUpperCase() : items[i];
            label(c, s, cell.centerX(), cell.centerY(), popupCellH * 0.42f, sel ? 0xFFFFFFFF : theme.text, false);
        }
    }

    private void openPopup(Ptr p, float offsetYHint) {
        Key k = p.key;
        String[] items = k.popups;
        popupCellW = Math.min(k.rect.width() * 1.05f, 44 * dp);
        popupCellH = k.rect.height() * 1.05f;
        int maxCols = Math.max(1, (int) ((getWidth() - 8 * dp) / popupCellW));
        popupCols = Math.min(items.length, Math.min(maxCols, 6));
        popupRows = (items.length + popupCols - 1) / popupCols;
        float w = popupCols * popupCellW, hh = popupRows * popupCellH;
        float left = k.rect.centerX() - popupCellW / 2;
        if (left + w > getWidth() - 4 * dp) left = getWidth() - 4 * dp - w;
        if (left < 4 * dp) left = 4 * dp;
        float top = Math.max(-offsetYHint + 2 * dp, k.rect.top - hh - 6 * dp);
        popupRect.set(left, top, left + w, top + hh);
        p.popup = true;
        p.popupIndex = 0;
        popupPtr = p;
        // start on the item under the finger if possible
        updatePopupIndex(p, p.lastX, k.rect.top - popupCellH / 2);
    }

    private void updatePopupIndex(Ptr p, float x, float y) {
        int col = (int) ((x - popupRect.left) / popupCellW);
        col = Math.max(0, Math.min(popupCols - 1, col));
        int row = popupRows == 1 ? 0 : (int) ((y - popupRect.top) / popupCellH);
        row = Math.max(0, Math.min(popupRows - 1, row));
        int idx = Math.min(p.key.popups.length - 1, row * popupCols + col);
        if (idx != p.popupIndex) { p.popupIndex = idx; listener.feedback(false); }
    }

    public float overflowAbove = 0; // height of the strip above us (set by the IME)

    private void invalidateAll() {
        invalidate();
        if (overlay != null) overlay.invalidate();
    }

    // ------------------------------------------------------------------ touch

    private Key keyAt(float x, float y) {
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Key> row : rows) for (Key k : row) {
            if (k.code == 0) continue;
            RectF r = k.rect;
            // generous hit area: distance to key rect (0 inside)
            float dx = Math.max(Math.max(r.left - x, 0), x - r.right);
            float dy = Math.max(Math.max(r.top - y, 0), y - r.bottom);
            float d = dx * dx + dy * dy * 1.5f;
            if (d < bestD) { bestD = d; best = k; }
        }
        return bestD < (30 * dp) * (30 * dp) ? best : null;
    }

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            Ptr p = longPressTarget;
            if (p == null || p.consumed || p.key == null) return;
            if (p.key.code == Key.SPACE) {
                if (!p.spaceSwipe) { p.consumed = true; listener.onSpaceLongPress(); }
                return;
            }
            String[] items = p.key.popups;
            if (items == null && p.key.hint != null) items = new String[]{p.key.hint};
            if (items == null) return;
            if (p.key.popups == null) {
                // single hint (e.g. number on top row) -> type it straight away
                p.consumed = true;
                listener.feedback(true);
                listener.onText(p.key.hint);
                invalidateAll();
                return;
            }
            listener.feedback(true);
            endDrop(p.key);
            openPopup(p, overflowAbove);
            invalidateAll();
        }
    };

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            for (int i = 0; i < ptrs.size(); i++) {
                Ptr p = ptrs.valueAt(i);
                if (p.key != null && p.key.code == Key.DELETE && !p.delSwipe) {
                    repeatCount++;
                    if (repeatCount > 25) listener.onDeleteWords(1); else listener.onKey(Key.DELETE);
                    listener.feedback(false);
                    h.postDelayed(this, repeatCount > 25 ? 140 : 55);
                    return;
                }
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = e.getX(idx), y = e.getY(idx);
                if (gliding) return true; // one swipe at a time
                if (oneHanded != 0) {
                    if (sideBtnA.contains(x, y)) { listener.feedback(true); listener.onOneHandedSwap(); return true; }
                    if (sideBtnB.contains(x, y)) { listener.feedback(true); listener.onOneHandedExit(); return true; }
                    if (x < keysLeft() || x > keysRight()) return true;
                }
                // Fast typing: a new finger commits the previous finger's key immediately.
                for (int i = 0; i < ptrs.size(); i++) {
                    Ptr o = ptrs.valueAt(i);
                    if (!o.consumed && o.key != null && !o.popup && (o.key.isChar() || (o.key.code == Key.SPACE && !o.spaceSwipe))) {
                        o.consumed = true;
                        endDrop(o.key);
                        fire(o.key);
                    }
                }
                Key k = keyAt(x, y);
                if (k == null) return true;
                Ptr p = new Ptr();
                p.key = k; p.downX = x; p.downY = y; p.lastX = x;
                ptrs.put(e.getPointerId(idx), p);
                if (ptrs.size() == 1) { gn = 0; glideEnd = -1; addGlidePoint(x, y, SystemClock.uptimeMillis()); }
                listener.feedback(false);
                pressOf(k).target = 1f;
                markTouch(k, x, y);
                startDrop(k);
                if (rippleEnabled) listener.onTouchPoint(x, y);
                h.removeCallbacks(longPress);
                if (k.code == Key.DELETE) {
                    listener.onKey(Key.DELETE);
                    p.consumed = true;
                    repeatCount = 0;
                    h.removeCallbacks(repeat);
                    h.postDelayed(repeat, 400);
                } else if (k.code == Key.SHIFT) {
                    p.consumed = true;
                    handleShiftDown();
                } else if (k.popups != null || k.hint != null || k.code == Key.SPACE) {
                    longPressTarget = p;
                    h.postDelayed(longPress, k.code == Key.SPACE ? 500 : 330);
                }
                invalidateAll();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    Ptr p = ptrs.get(e.getPointerId(i));
                    if (p == null || p.key == null) continue;
                    float x = e.getX(i), y = e.getY(i);
                    if (p.popup) { updatePopupIndex(p, x, y); invalidateAll(); continue; }
                    int pid = e.getPointerId(i);
                    if (glideEnabled && !gliding && page == Layouts.ALPHA && ptrs.size() == 1 && p.key.isChar()
                            && Character.isLetter(p.key.label.charAt(0)) && !p.consumed
                            && Math.hypot(x - p.downX, y - p.downY) > keyUnit * 0.7f) {
                        // it's a swipe, not a tap
                        gliding = true;
                        glidePointer = pid;
                        p.consumed = true;
                        h.removeCallbacks(longPress);
                        pressOf(p.key).target = 0f;
                        endDrop(p.key);
                    }
                    if (gliding && pid == glidePointer) {
                        long now = SystemClock.uptimeMillis();
                        for (int hh = 0; hh < e.getHistorySize(); hh++)
                            addGlidePoint(e.getHistoricalX(i, hh), e.getHistoricalY(i, hh), e.getHistoricalEventTime(hh));
                        addGlidePoint(x, y, now);
                        if (rippleEnabled) listener.onTouchPoint(x, y);
                        if (now - lastPreview > 90) {
                            lastPreview = now;
                            listener.onGlidePreview(decodeGlide(true));
                        }
                        invalidateAll();
                        continue;
                    }
                    float dx = x - p.downX;
                    if (p.key.code == Key.SPACE && !p.consumed) {
                        if (!p.spaceSwipe && Math.abs(dx) > 14 * dp) { p.spaceSwipe = true; h.removeCallbacks(longPress); }
                        if (rippleEnabled) listener.onTouchPoint(x, y);
                        if (p.spaceSwipe) {
                            int steps = (int) ((x - p.downX) / (11 * dp));
                            if (steps != p.cursorSteps) {
                                listener.onCursorMove(steps - p.cursorSteps);
                                listener.feedback(false);
                                p.cursorSteps = steps;
                            }
                            invalidate();
                        }
                    } else if (p.key.code == Key.DELETE) {
                        if (dx < -26 * dp) {
                            if (!p.delSwipe) { p.delSwipe = true; h.removeCallbacks(repeat); }
                            int words = 1 + (int) ((-dx - 26 * dp) / (32 * dp));
                            if (words != p.delWords) { p.delWords = words; listener.feedback(false); invalidate(); }
                        } else if (p.delSwipe) { p.delWords = 0; invalidate(); }
                    } else if (!p.consumed && p.key.isChar() && !glideEnabled) {
                        // let the finger slide onto a neighbouring key before release
                        Key k = keyAt(x, y);
                        if (k != null && k != p.key && k.isChar() && Math.hypot(x - p.downX, y - p.downY) > 10 * dp) {
                            pressOf(p.key).target = 0f;
                            endDrop(p.key);
                            p.key = k;
                            pressOf(k).target = 1f;
                            startDrop(k);
                            h.removeCallbacks(longPress);
                            if (k.popups != null || k.hint != null) { longPressTarget = p; h.postDelayed(longPress, 330); }
                            invalidateAll();
                        }
                    }
                    p.lastX = x;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Ptr p = ptrs.get(e.getPointerId(idx));
                ptrs.remove(e.getPointerId(idx));
                h.removeCallbacks(longPress);
                if (gliding && e.getPointerId(idx) == glidePointer) {
                    addGlidePoint(e.getX(idx), e.getY(idx), SystemClock.uptimeMillis());
                    gliding = false;
                    glidePointer = -1;
                    glideEnd = SystemClock.uptimeMillis();
                    List<String> words = decodeGlide(false);
                    listener.onGlide(words);
                    if (!words.isEmpty() && shift == SHIFT_ON && !shiftHeld) setShift(SHIFT_OFF);
                    if (p != null && p.key != null) pressOf(p.key).target = 0f;
                    invalidateAll();
                    return true;
                }
                if (p != null && p.key != null) { pressOf(p.key).target = 0f; endDrop(p.key); }
                if (p != null && p.key != null) release(p);
                if (ptrs.size() == 0) h.removeCallbacks(repeat);
                invalidateAll();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                gliding = false;
                glidePointer = -1;
                gn = 0;
                for (Spring sp : pressSpring.values()) sp.target = 0f;
                for (Drop d : drops) d.s.target = 0f;
                ptrs.clear();
                popupPtr = null;
                h.removeCallbacks(longPress);
                h.removeCallbacks(repeat);
                invalidateAll();
                return true;
        }
        return true;
    }

    private void release(Ptr p) {
        if (p.popup) {
            String s = p.key.popups[p.popupIndex];
            if (shift != SHIFT_OFF) s = s.toUpperCase();
            listener.onText(s);
            afterCharTyped();
            popupPtr = null;
            return;
        }
        if (p.key.code == Key.SHIFT) {
            if (shiftHeld && shiftUsedWhileHeld && shift == SHIFT_ON) setShift(SHIFT_OFF);
            shiftHeld = false;
            return;
        }
        if (p.key.code == Key.DELETE) {
            if (p.delSwipe && p.delWords > 0) listener.onDeleteWords(p.delWords);
            return;
        }
        if (p.key.code == Key.SPACE && p.spaceSwipe) return;
        if (!p.consumed) fire(p.key);
    }

    private void fire(Key k) {
        if (k.isChar()) {
            String s = k.label;
            if (shift != SHIFT_OFF) s = s.toUpperCase();
            listener.onText(s);
            afterCharTyped();
        } else {
            listener.onKey(k.code);
        }
    }

    private void afterCharTyped() {
        if (shiftHeld) { shiftUsedWhileHeld = true; return; }
        if (shift == SHIFT_ON) setShift(SHIFT_OFF);
    }

    private void handleShiftDown() {
        long now = SystemClock.uptimeMillis();
        shiftHeld = true;
        shiftUsedWhileHeld = false;
        if (page != Layouts.ALPHA) return;
        if (shift == SHIFT_ON && now - lastShiftTap < 350) setShift(SHIFT_LOCK);
        else if (shift == SHIFT_OFF) setShift(SHIFT_ON);
        else setShift(SHIFT_OFF);
        lastShiftTap = now;
    }
}
