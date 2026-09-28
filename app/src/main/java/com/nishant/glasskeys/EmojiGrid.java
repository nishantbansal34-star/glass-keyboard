package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** A scrollable (inside a ScrollView) grid of emoji drawn on one canvas — fast even with hundreds. */
public class EmojiGrid extends View {
    public interface OnPick { void pick(String emoji); }

    private final List<String> items = new ArrayList<>();
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassPainter gp;
    private final float dp;
    private Theme theme = Theme.get(0);
    private OnPick onPick;
    private int cols = 8;
    private float cell;
    private int pressed = -1;
    private float downX, downY;
    private String emptyText = "";
    public Runnable feedback;

    public EmojiGrid(Context c, GlassPainter gp) {
        super(c);
        this.gp = gp;
        dp = c.getResources().getDisplayMetrics().density;
        tp.setTextAlign(Paint.Align.CENTER);
    }

    public void setTheme(Theme t) { theme = t; invalidate(); }
    public void setOnPick(OnPick p) { onPick = p; }

    public void setItems(List<String> list, String empty) {
        items.clear();
        items.addAll(list);
        emptyText = empty;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        cols = Math.max(6, (int) (w / (46 * dp)));
        cell = w / (float) cols;
        int rows = (items.size() + cols - 1) / cols;
        int minH = MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED ? 0 : MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, Math.max((int) (rows * cell + 8 * dp), (int) (120 * dp)));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (items.isEmpty()) {
            tp.setTextSize(14 * dp);
            tp.setColor(theme.subText);
            c.drawText(emptyText, getWidth() / 2f, 60 * dp, tp);
            return;
        }
        gp.setOriginFromView(this);
        tp.setTextSize(cell * 0.56f);
        tp.setColor(0xFF000000);
        Paint.FontMetrics fm = tp.getFontMetrics();
        for (int i = 0; i < items.size(); i++) {
            float x = (i % cols) * cell, y = (i / cols) * cell + 4 * dp;
            if (i == pressed) {
                RectF r = new RectF(x + 3 * dp, y + 3 * dp, x + cell - 3 * dp, y + cell - 3 * dp);
                gp.drawGlass(c, r, 10 * dp, GlassPainter.STYLE_KEY, true, theme);
            }
            c.drawText(items.get(i), x + cell / 2, y + cell / 2 - (fm.ascent + fm.descent) / 2, tp);
        }
    }

    private int indexAt(float x, float y) {
        int col = (int) (x / cell), row = (int) ((y - 4 * dp) / cell);
        int i = row * cols + col;
        return (col >= 0 && col < cols && row >= 0 && i < items.size()) ? i : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                pressed = indexAt(downX, downY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > 12 * dp && pressed != -1) { pressed = -1; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                int p = pressed;
                pressed = -1;
                invalidate();
                if (p >= 0 && onPick != null) {
                    if (feedback != null) feedback.run();
                    onPick.pick(items.get(p));
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
        }
        return true;
    }
}
