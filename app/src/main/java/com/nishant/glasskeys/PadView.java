package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** A grid of liquid-glass buttons (calculator, text editing pad, emoji tab bar). */
public class PadView extends View {

    public static class Btn {
        public String id;
        public String label;      // main text (or caption under an icon)
        public int icon;
        public float weight = 1;
        public int style = GlassPainter.STYLE_KEY;
        public boolean repeat;
        public boolean selected;
        public boolean bigText;   // calculator digits
        final RectF r = new RectF();
        final Spring press = new Spring(900f, 0.45f);

        public Btn(String id, String label, int icon) { this.id = id; this.label = label; this.icon = icon; }
        public Btn style(int s) { style = s; return this; }
        public Btn weight(float w) { weight = w; return this; }
        public Btn repeating() { repeat = true; return this; }
        public Btn big() { bigText = true; return this; }
    }

    public interface OnPress { void press(Btn b); }

    private final GlassPainter gp;
    private final float dp;
    private Theme theme = Theme.get(0);
    private final List<List<Btn>> rows = new ArrayList<>();
    private OnPress onPress;
    private Btn pressed;
    private boolean emojiLabels;
    private float gapX = 6, gapY = 7;
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastFrame;
    private final RectF tmp = new RectF();
    private final Runnable repeater = new Runnable() {
        @Override public void run() {
            if (pressed != null && pressed.repeat) { onPress.press(pressed); h.postDelayed(this, 60); }
        }
    };
    public Runnable feedback;

    public PadView(Context c, GlassPainter gp) {
        super(c);
        this.gp = gp;
        dp = c.getResources().getDisplayMetrics().density;
        tp.setTextAlign(Paint.Align.CENTER);
    }

    public void setTheme(Theme t) { theme = t; invalidate(); }
    public void setOnPress(OnPress p) { onPress = p; }
    public void setTextScale(float s) { }
    public void setEmojiLabels(boolean e) { emojiLabels = e; }
    public void setGaps(float x, float y) { gapX = x; gapY = y; layoutBtns(); }

    public void setRows(List<List<Btn>> r) {
        rows.clear();
        rows.addAll(r);
        layoutBtns();
        invalidate();
    }

    public List<List<Btn>> rows() { return rows; }

    @Override
    protected void onSizeChanged(int w, int hh, int ow, int oh) { layoutBtns(); }

    private void layoutBtns() {
        float W = getWidth(), H = getHeight();
        if (W == 0 || rows.isEmpty()) return;
        float pad = 4 * dp, gx = gapX * dp, gy = gapY * dp;
        float rowH = (H - 2 * pad) / rows.size();
        for (int i = 0; i < rows.size(); i++) {
            List<Btn> row = rows.get(i);
            float tot = 0;
            for (Btn b : row) tot += b.weight;
            float unit = (W - 2 * pad) / tot, x = pad, top = pad + i * rowH;
            for (Btn b : row) {
                float w = unit * b.weight;
                b.r.set(x + gx / 2, top + gy / 2, x + w - gx / 2, top + rowH - gy / 2);
                x += w;
            }
        }
    }

    /** Largest text size (up to max) at which the label fits inside maxWidth. */
    private float fit(String s, float max, float maxWidth) {
        tp.setTextSize(max);
        float w = tp.measureText(s);
        if (w <= maxWidth) return max;
        return Math.max(8 * dp, max * maxWidth / w);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean animating = false;
        for (List<Btn> row : rows) for (Btn b : row) {
            if (b.press.step(dt)) animating = true;
            float s = 1f - 0.06f * b.press.value;
            float cx = b.r.centerX(), cy = b.r.centerY();
            tmp.set(cx - b.r.width() * s / 2, cy - b.r.height() * s / 2, cx + b.r.width() * s / 2, cy + b.r.height() * s / 2);
            int style = b.selected ? GlassPainter.STYLE_ACTIVE : b.style;
            float radius = Math.min(14 * dp, tmp.height() / 2);
            if (!emojiLabels || b == pressed || b.selected)
                gp.drawGlass(c, tmp, radius, style, b == pressed, theme, b.press.value * 0.8f);
            int col = (style == GlassPainter.STYLE_ACTION || style == GlassPainter.STYLE_ACTIVE) ? 0xFFFFFFFF : theme.text;
            float maxW = tmp.width() * 0.84f;
            if (b.icon != 0 && b.label != null) {
                float isz = Math.min(tmp.height() * 0.34f, 20 * dp);
                float cap = fit(b.label, Math.min(tmp.height() * 0.2f, 12.5f * dp), maxW);
                float gap = 4 * dp;
                float total = isz + gap + cap;
                float top = cy - total / 2;
                gp.drawIcon(c, b.icon, cx, top + isz / 2, isz, col);
                tp.setColor((col & 0x00FFFFFF) | 0xCC000000);
                tp.setTypeface(Typeface.DEFAULT);
                tp.setTextSize(cap);
                c.drawText(b.label, cx, top + isz + gap + cap * 0.8f, tp);
            } else if (b.icon != 0) {
                gp.drawIcon(c, b.icon, cx, cy, Math.min(tmp.height() * 0.42f, 22 * dp), col);
            } else if (b.label != null) {
                tp.setColor(col);
                tp.setTypeface(b.bigText ? Typeface.create("sans-serif-light", Typeface.NORMAL)
                        : Typeface.create("sans-serif-medium", Typeface.NORMAL));
                float max = emojiLabels ? tmp.height() * 0.5f
                        : b.bigText ? Math.min(tmp.height() * 0.5f, 26 * dp) : Math.min(tmp.height() * 0.34f, 16 * dp);
                tp.setTextSize(fit(b.label, max, maxW));
                Paint.FontMetrics fm = tp.getFontMetrics();
                c.drawText(b.label, cx, cy - (fm.ascent + fm.descent) / 2, tp);
            }
        }
        if (animating) postInvalidateOnAnimation(); else lastFrame = 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = null;
                for (List<Btn> row : rows) for (Btn b : row) if (b.r.contains(e.getX(), e.getY())) pressed = b;
                if (pressed != null) {
                    pressed.press.target = 1f;
                    if (feedback != null) feedback.run();
                    if (pressed.repeat) { onPress.press(pressed); h.postDelayed(repeater, 400); }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed != null && !pressed.r.contains(e.getX(), e.getY())) {
                    pressed.press.target = 0f;
                    pressed = null; h.removeCallbacks(repeater); invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(repeater);
                Btn p = pressed;
                pressed = null;
                if (p != null) p.press.target = 0f;
                invalidate();
                if (p != null && !p.repeat && onPress != null) onPress.press(p);
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(repeater);
                if (pressed != null) pressed.press.target = 0f;
                pressed = null;
                invalidate();
                return true;
        }
        return true;
    }
}
