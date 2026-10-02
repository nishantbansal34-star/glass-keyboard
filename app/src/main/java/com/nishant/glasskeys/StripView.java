package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** The bar above the keys: toolbar icons, word suggestions, chips (paste / math result), panel titles. */
public class StripView extends View {

    public static class Cell {
        public int icon;          // GlassPainter icon id or 0
        public String text;       // label or null
        public boolean primary;   // bold centre suggestion
        public boolean chip;      // glass pill (paste / result chips)
        public boolean active;    // highlighted (e.g. mic listening)
        public boolean title;     // non-clickable title text
        public float weight;      // 0 = fixed icon width
        public Runnable action;
        public Runnable longAction;
        public float hud = -1;     // >= 0: level badge with an XP bar (value = progress 0..1)
        public float fixedW = 0;
        final RectF r = new RectF();

        public static Cell icon(int icon, Runnable a) { Cell c = new Cell(); c.icon = icon; c.action = a; return c; }
        public static Cell word(String t, boolean primary, Runnable a) {
            Cell c = new Cell(); c.text = t; c.primary = primary; c.weight = 1; c.action = a; return c;
        }
        public static Cell chip(int icon, String t, Runnable a) {
            Cell c = new Cell(); c.icon = icon; c.text = t; c.chip = true; c.weight = 1; c.action = a; return c;
        }
        public static Cell title(String t) { Cell c = new Cell(); c.text = t; c.title = true; c.weight = 1; return c; }
        public static Cell spacer() { Cell c = new Cell(); c.weight = 1; c.title = true; return c; }
    }

    private final GlassPainter gp;
    private final float dp;
    private Theme theme = Theme.get(0);
    private final List<Cell> cells = new ArrayList<>();
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint div = new Paint();
    private Cell pressed;
    private float downX, downY;
    public Runnable feedback;
    private final Runnable longPressCheck = () -> {
        if (pressed != null && pressed.longAction != null) {
            Cell c = pressed; pressed = null; invalidate();
            if (this.feedback != null) this.feedback.run();
            c.longAction.run();
        }
    };

    public StripView(Context c, GlassPainter gp) {
        super(c);
        this.gp = gp;
        dp = c.getResources().getDisplayMetrics().density;
        tp.setTextAlign(Paint.Align.CENTER);
    }

    public void setTheme(Theme t) { theme = t; invalidate(); }

    private String lastSig = "";

    public void setCells(List<Cell> list) {
        StringBuilder sig = new StringBuilder();
        for (Cell c : list) sig.append(c.chip ? 'c' : c.title ? 't' : c.text != null ? 'w' : 'i');
        if (!sig.toString().equals(lastSig) && isShown()) {
            // cross-fade between toolbar / suggestions / chips
            animate().cancel();
            setAlpha(0.2f);
            setTranslationY(3 * dp);
            animate().alpha(1f).translationY(0f).setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
        boolean sameShape = sig.toString().equals(lastSig);
        lastSig = sig.toString();
        oldTexts.clear();
        if (sameShape) {
            boolean changed = false;
            for (int i = 0; i < cells.size() && i < list.size(); i++) {
                String o = cells.get(i).text, n = list.get(i).text;
                oldTexts.add(o);
                if (o != null && !o.equals(n)) changed = true;
            }
            if (changed) transStart = android.os.SystemClock.uptimeMillis(); else oldTexts.clear();
        }
        cells.clear();
        cells.addAll(list);
        pressed = null;
        layoutCells();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) { layoutCells(); }

    private void layoutCells() {
        float W = getWidth(), H = getHeight();
        if (W == 0) return;
        float iconW = (premium() ? 38 : 42) * dp, fixed = 0, weights = 0;
        for (Cell c : cells) { if (c.fixedW > 0) fixed += c.fixedW; else if (c.weight == 0) fixed += iconW; else weights += c.weight; }
        float pad = 10 * dp;
        if (!cells.isEmpty() && (cells.get(0).icon == GlassPainter.IC_SPARKLE || cells.get(0).icon == GlassPainter.IC_KEYBOARD)) {
            // leading round button: square cell of full height
            fixed += (H - 10 * dp) + 12 * dp - iconW;
        }
        float unit = weights > 0 ? (W - 2 * pad - fixed) / weights : 0;
        float x = pad;
        if (weights == 0) x = (W - fixed) / 2f; // icons only: centre the toolbar
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            float w = c.fixedW > 0 ? c.fixedW : c.weight == 0 ? iconW : unit * c.weight;
            if (i == 0 && (c.icon == GlassPainter.IC_SPARKLE || c.icon == GlassPainter.IC_KEYBOARD)) {
                w = (H - 10 * dp) + 12 * dp;
                x = 0;
            }
            c.r.set(x, 0, x + w, H);
            x += w;
        }
    }

    private final RectF capsule = new RectF(), circle = new RectF();
    private final List<String> oldTexts = new ArrayList<>();
    private long transStart = -1, orbAwakeUntil, orbTapAt = -1;
    private final long born = android.os.SystemClock.uptimeMillis();
    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Typeface TF_REG = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface TF_MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface TF_LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private boolean premium() { return gp.amoled && gp.pack == 0; }

    /**
     * The AI orb: a small glass sphere with a softly drifting luminous core and a four-point sparkle.
     * Tap: the glass compresses, the light expands and a ripple leaves the orb.
     */
    private void drawOrb(Canvas c, RectF o, boolean down) {
        long now = android.os.SystemClock.uptimeMillis();
        float t = (now - born) / 1000f;
        float tap = orbTapAt < 0 ? 1f : Math.min(1f, (now - orbTapAt) / 420f);
        float squeeze = down ? 0.92f : 1f - 0.08f * (float) Math.sin(Math.PI * Math.min(1f, tap * 1.4f)) * (tap < 1f ? 1f : 0f);
        c.save();
        c.scale(squeeze, squeeze, o.centerX(), o.centerY());
        gp.variant = -1;
        gp.drawGlass(c, o, o.height() / 2, GlassPainter.STYLE_KEY, false, theme);
        // luminous core: drifts very slightly and breathes
        float cx = o.centerX() + (float) Math.sin(t * 0.8) * 1.6f * dp, cy = o.centerY() + (float) Math.cos(t * 0.6) * 1.2f * dp;
        float breath = 0.85f + 0.15f * (float) Math.sin(t * 1.3);
        float expand = (down ? 1.5f : 1f) + (tap < 1f ? 0.6f * (1f - tap) : 0f);
        int core = theme.dark ? 0xDCE6FF : 0xFFFFFF;
        orbPaint.setShader(new android.graphics.RadialGradient(cx, cy, o.width() * 0.42f * expand,
                new int[]{((int) (110 * breath) << 24) | core, ((int) (34 * breath) << 24) | (theme.accent & 0xFFFFFF), 0x00000000},
                new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(o.centerX(), o.centerY(), o.width() / 2 - 1, orbPaint);
        orbPaint.setShader(null);
        orbPaint.setShader(new android.graphics.RadialGradient(o.centerX(), o.centerY(), 9 * dp * expand,
                ((int) (70 * breath) << 24) | 0xFFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(o.centerX(), o.centerY(), 9 * dp * expand, orbPaint);
        orbPaint.setShader(null);
        gp.drawIcon(c, GlassPainter.IC_SPARKLE, o.centerX() + 1 * dp, o.centerY() + 1 * dp, 16 * dp,
                theme.dark ? 0xF2FFFFFF : 0xE615181E);
        c.restore();
        if (tap < 1f) {
            orbPaint.setStyle(Paint.Style.STROKE);
            orbPaint.setStrokeWidth(1.2f * dp);
            orbPaint.setColor(((int) (90 * (1f - tap)) << 24) | 0xFFFFFF);
            c.drawCircle(o.centerX(), o.centerY(), o.width() / 2 + 10 * dp * tap, orbPaint);
            orbPaint.setStyle(Paint.Style.FILL);
        }
        if (now < orbAwakeUntil || tap < 1f || down) postInvalidateDelayed(tap < 1f || down ? 16 : 40);
    }

    @Override
    protected void onDraw(Canvas c) {
        float H = getHeight(), W = getWidth();
        gp.setOriginFromView(this);
        div.setColor(gp.pack == 2 ? 0x1AFFFFFF : premium() ? (theme.dark ? 0x1FFFFFFF : 0x261B1F2A) : theme.dark ? 0x40FFFFFF : 0x401B1F2A);
        if (cells.isEmpty()) return;

        // Layout of the glass: a round button for the leading ✦ cell, a capsule for the rest.
        Cell first = cells.get(0);
        boolean lead = first.icon == GlassPainter.IC_SPARKLE || first.icon == GlassPainter.IC_KEYBOARD;
        float m = 6 * dp, top = 5 * dp, bot = H - 5 * dp, d = bot - top;
        float capLeft = m;
        if (premium()) {
            capsule.set(m, top, W - m, bot);
            gp.variant = -1;
            gp.drawGlass(c, capsule, d / 2, GlassPainter.STYLE_KEY, false, theme);
            gp.variant = 0;
            if (lead) {
                float in = 4 * dp;
                circle.set(m + in, top + in, m + in + (d - 2 * in), bot - in);
                if (first.icon == GlassPainter.IC_SPARKLE) drawOrb(c, circle, first == pressed);
                else {
                    gp.drawGlass(c, circle, circle.height() / 2, GlassPainter.STYLE_KEY, first == pressed, theme);
                    gp.drawIcon(c, first.icon, circle.centerX(), circle.centerY(), 18 * dp, theme.text);
                }
            }
        } else if (lead) {
            circle.set(m, top, m + d, bot);
            gp.variant = -1;
            gp.drawGlass(c, circle, d / 2, GlassPainter.STYLE_KEY, first == pressed, theme,
                    first == pressed ? 0.8f : 0f);
            gp.drawIcon(c, first.icon, circle.centerX(), circle.centerY(), 20 * dp, theme.text);
            capLeft = circle.right + 6 * dp;
        }
        if (!premium()) {
            capsule.set(capLeft, top, W - m, bot);
            gp.variant = -1;
            gp.drawGlass(c, capsule, d / 2, GlassPainter.STYLE_KEY, false, theme);
            gp.variant = 0;
        }
        long now = android.os.SystemClock.uptimeMillis();
        float tt = transStart < 0 ? 1f : Math.min(1f, (now - transStart) / 170f);
        if (tt < 1f) postInvalidateOnAnimation(); else { transStart = -1; oldTexts.clear(); }
        tt = 1f - (1f - tt) * (1f - tt);   // ease-out

        Cell prevWord = null;
        for (int i = lead ? 1 : 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            RectF r = cell.r;
            float cl = Math.max(r.left, capsule.left + 4 * dp), cr = Math.min(r.right, capsule.right - 4 * dp);
            if (cell.chip || cell.active) {
                RectF g = new RectF(cl + 2 * dp, top + 5 * dp, cr - 2 * dp, bot - 5 * dp);
                gp.drawGlass(c, g, g.height() / 2, cell.active ? GlassPainter.STYLE_ACTION : GlassPainter.STYLE_ACTIVE,
                        cell == pressed, theme);
            } else if (cell == pressed) {
                fill.setColor(theme.dark ? 0x26FFFFFF : 0x331B1F2A);
                RectF g = new RectF(cl + 2 * dp, top + 5 * dp, cr - 2 * dp, bot - 5 * dp);
                c.drawRoundRect(g, g.height() / 2, g.height() / 2, fill);
            }
            int col = (cell.active || cell.chip) ? 0xFFFFFFFF : theme.text;
            float cy = (top + bot) / 2;
            if (cell.hud >= 0) { drawHud(c, cell, cl, cr, top, bot); continue; }
            if (gp.pack == 1 && cell.primary && cell.text != null) {
                // glowing outlined pill around the best suggestion
                RectF g = new RectF(cl + 3 * dp, top + 4 * dp, cr - 3 * dp, bot - 4 * dp);
                fill.setColor(0x338B5CFF);
                c.drawRoundRect(g, 12 * dp, 12 * dp, fill);
                hudPaint.setStyle(Paint.Style.STROKE);
                hudPaint.setStrokeWidth(1.5f * dp);
                hudPaint.setColor(0xFFA88BFF);
                hudPaint.setShadowLayer(6 * dp, 0, 0, 0xCC8B5CFF);
                c.drawRoundRect(g, 12 * dp, 12 * dp, hudPaint);
                hudPaint.clearShadowLayer();
                hudPaint.setStyle(Paint.Style.FILL);
            }
            if (cell.text != null) {
                boolean prem = premium();
                int tc = cell.title ? theme.subText : col;
                if (prem && !cell.title && !cell.chip && !cell.active && !cell.primary) tc = (tc & 0x00FFFFFF) | 0xB3000000;
                tp.setColor(tc);
                tp.setTextSize((cell.title ? 14 : prem ? 16.5f : 17) * dp);
                tp.setTypeface(cell.primary ? TF_MED : cell.title ? Typeface.DEFAULT : TF_REG);
                if (theme.dark && !prem && gp.pack != 2) tp.setShadowLayer(3 * dp, 0, 1 * dp, 0x59000000); else tp.clearShadowLayer();
                if (gp.pack == 2) tp.setTypeface(cell.primary ? TF_REG : TF_LIGHT);
                float avail = (cr - cl) - (cell.icon != 0 ? 36 * dp : 14 * dp);
                String s = TextUtils.ellipsize(cell.text, tp, Math.max(avail, 10), TextUtils.TruncateAt.END).toString();
                float tx = (cl + cr) / 2 + (cell.icon != 0 ? 11 * dp : 0);
                Paint.FontMetrics fm = tp.getFontMetrics();
                float base = cy - (fm.ascent + fm.descent) / 2;
                String old = i < oldTexts.size() ? oldTexts.get(i) : null;
                if (tt < 1f && old != null && !old.equals(cell.text)) {
                    // gentle cross-fade: old word drifts up and out, new word settles in from below
                    int a0 = tp.getAlpha();
                    tp.setAlpha((int) (a0 * (1f - tt)));
                    c.drawText(TextUtils.ellipsize(old, tp, Math.max(avail, 10), TextUtils.TruncateAt.END).toString(), tx, base - 5 * dp * tt, tp);
                    tp.setAlpha((int) (a0 * tt));
                    c.drawText(s, tx, base + 5 * dp * (1f - tt), tp);
                    tp.setAlpha(a0);
                } else c.drawText(s, tx, base, tp);
                if (cell.icon != 0) {
                    float tw = tp.measureText(s);
                    gp.drawIcon(c, cell.icon, tx - tw / 2 - 12 * dp, cy, 17 * dp, col);
                }
                if (!cell.chip && !cell.title && prevWord != null) c.drawLine(r.left, top + d * 0.28f, r.left, bot - d * 0.28f, div);
                prevWord = cell.chip || cell.title ? null : cell;
            } else if (cell.icon != 0) {
                gp.drawIcon(c, cell.icon, (cl + cr) / 2, cy, 21 * dp, col);
                prevWord = null;
            }
        }
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Level badge: "LV 12" with a slim glowing XP bar underneath. */
    private void drawHud(Canvas c, Cell cell, float cl, float cr, float top, float bot) {
        float h = bot - top;
        RectF box = new RectF(cl + 4 * dp, top + 5 * dp, cr - 4 * dp, bot - 5 * dp);
        if (cell == pressed) {
            fill.setColor(0x338B5CFF);
            c.drawRoundRect(box, 10 * dp, 10 * dp, fill);
        }
        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setTextAlign(Paint.Align.CENTER);
        hudPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        hudPaint.setTextSize(13 * dp);
        hudPaint.setLetterSpacing(0.12f);
        hudPaint.setColor(0xFFEFE8FF);
        hudPaint.setShadowLayer(5 * dp, 0, 0, 0xCC8B5CFF);
        c.drawText(cell.text, box.centerX(), box.top + h * 0.42f, hudPaint);
        hudPaint.clearShadowLayer();
        hudPaint.setLetterSpacing(0f);
        float bl = box.left + 6 * dp, br = box.right - 6 * dp, by = box.bottom - 7 * dp;
        hudPaint.setColor(0x33FFFFFF);
        c.drawRoundRect(new RectF(bl, by - 1.5f * dp, br, by + 1.5f * dp), 2 * dp, 2 * dp, hudPaint);
        hudPaint.setColor(0xFFA070FF);
        hudPaint.setShadowLayer(4 * dp, 0, 0, 0xFF8B5CFF);
        c.drawRoundRect(new RectF(bl, by - 1.5f * dp, bl + (br - bl) * Math.max(0.03f, Math.min(1f, cell.hud)), by + 1.5f * dp), 2 * dp, 2 * dp, hudPaint);
        hudPaint.clearShadowLayer();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = null;
                for (Cell c : cells) if (c.r.contains(e.getX(), e.getY()) && !c.title && c.action != null) pressed = c;
                downX = e.getX(); downY = e.getY();
                if (pressed != null) {
                    if (pressed.icon == GlassPainter.IC_SPARKLE) { orbTapAt = android.os.SystemClock.uptimeMillis(); orbAwakeUntil = orbTapAt + 9000; }
                    if (feedback != null) feedback.run();
                    if (pressed.longAction != null) postDelayed(longPressCheck, 450);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed != null && !pressed.r.contains(e.getX(), e.getY())) {
                    pressed = null; removeCallbacks(longPressCheck); invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressCheck);
                Cell p = pressed;
                pressed = null;
                invalidate();
                if (p != null && p.action != null) p.action.run();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressCheck);
                pressed = null;
                invalidate();
                return true;
        }
        return true;
    }
}
