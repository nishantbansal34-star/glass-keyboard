package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** A grid of glass buttons (used by the calculator, the text-editing pad and the emoji category bar). */
public class PadView extends View {

    public static class Btn {
        public String id;
        public String label;
        public int icon;
        public float weight = 1;
        public int style = GlassPainter.STYLE_KEY;
        public boolean repeat;     // auto-repeat while held (arrows, delete)
        public boolean selected;
        final RectF r = new RectF();

        public Btn(String id, String label, int icon) { this.id = id; this.label = label; this.icon = icon; }
        public Btn style(int s) { style = s; return this; }
        public Btn weight(float w) { weight = w; return this; }
        public Btn repeating() { repeat = true; return this; }
    }

    public interface OnPress { void press(Btn b); }

    private final GlassPainter gp;
    private final float dp;
    private Theme theme = Theme.get(0);
    private final List<List<Btn>> rows = new ArrayList<>();
    private OnPress onPress;
    private Btn pressed;
    private float textScale = 0.36f;
    private boolean emojiLabels;
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
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
    public void setTextScale(float s) { textScale = s; }
    public void setEmojiLabels(boolean e) { emojiLabels = e; }

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
        float pad = 3 * dp, gx = 5 * dp, gy = 6 * dp;
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

    @Override
    protected void onDraw(Canvas c) {
        for (List<Btn> row : rows) for (Btn b : row) {
            int style = b.selected ? GlassPainter.STYLE_ACTIVE : b.style;
            if (!emojiLabels || b == pressed || b.selected)
                gp.drawGlass(c, b.r, Math.min(10 * dp, b.r.height() / 2), style, b == pressed, theme);
            int col = (style == GlassPainter.STYLE_ACTION || style == GlassPainter.STYLE_ACTIVE) ? 0xFFFFFFFF : theme.text;
            float cx = b.r.centerX(), cy = b.r.centerY();
            if (b.icon != 0 && b.label != null) {
                float isz = Math.min(b.r.height() * 0.42f, 20 * dp);
                gp.drawIcon(c, b.icon, cx, cy - b.r.height() * 0.14f, isz, col);
                tp.setColor(col);
                tp.setTypeface(Typeface.DEFAULT);
                tp.setTextSize(Math.min(b.r.height() * 0.2f, 12 * dp));
                c.drawText(b.label, cx, cy + b.r.height() * 0.3f, tp);
            } else if (b.icon != 0) {
                gp.drawIcon(c, b.icon, cx, cy, Math.min(b.r.height() * 0.5f, 24 * dp), col);
            } else if (b.label != null) {
                tp.setColor(col);
                tp.setTypeface(b.style == GlassPainter.STYLE_FUNC ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                tp.setTextSize(Math.min(b.r.height() * textScale, b.r.width() * 0.34f));
                Paint.FontMetrics fm = tp.getFontMetrics();
                c.drawText(b.label, cx, cy - (fm.ascent + fm.descent) / 2, tp);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = null;
                for (List<Btn> row : rows) for (Btn b : row) if (b.r.contains(e.getX(), e.getY())) pressed = b;
                if (pressed != null) {
                    if (feedback != null) feedback.run();
                    if (pressed.repeat) { onPress.press(pressed); h.postDelayed(repeater, 400); }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed != null && !pressed.r.contains(e.getX(), e.getY())) {
                    pressed = null; h.removeCallbacks(repeater); invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(repeater);
                Btn p = pressed;
                pressed = null;
                invalidate();
                if (p != null && !p.repeat && onPress != null) onPress.press(p);
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(repeater);
                pressed = null;
                invalidate();
                return true;
        }
        return true;
    }
}
