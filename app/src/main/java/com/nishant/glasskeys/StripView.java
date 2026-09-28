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

    public void setCells(List<Cell> list) {
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
        float iconW = 44 * dp, fixed = 0, weights = 0;
        for (Cell c : cells) { if (c.weight == 0) fixed += iconW; else weights += c.weight; }
        float pad = 4 * dp;
        float unit = weights > 0 ? (W - 2 * pad - fixed) / weights : 0;
        float x = pad;
        if (weights == 0) x = (W - fixed) / 2f; // icons only: centre the toolbar
        for (Cell c : cells) {
            float w = c.weight == 0 ? iconW : unit * c.weight;
            c.r.set(x, 0, x + w, H);
            x += w;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float H = getHeight();
        div.setColor(theme.dark ? 0x33FFFFFF : 0x331B1F2A);
        Cell prevWord = null;
        for (Cell cell : cells) {
            RectF r = cell.r;
            if (cell.chip || cell.active || cell == pressed) {
                RectF g = new RectF(r.left + 3 * dp, 6 * dp, r.right - 3 * dp, H - 6 * dp);
                int style = cell.active ? GlassPainter.STYLE_ACTION : GlassPainter.STYLE_KEY;
                if (cell.chip || cell.active || (cell.icon != 0 && cell.text == null))
                    gp.drawGlass(c, g, g.height() / 2, style, cell == pressed, theme);
                else gp.drawGlass(c, g, 10 * dp, GlassPainter.STYLE_FUNC, true, theme);
            }
            int col = cell.active ? 0xFFFFFFFF : theme.text;
            if (cell.text != null) {
                tp.setColor(cell.title ? theme.subText : col);
                tp.setTextSize((cell.title ? 14 : 16) * dp);
                tp.setTypeface(cell.primary || cell.title ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                float avail = r.width() - (cell.icon != 0 ? 36 * dp : 12 * dp);
                String s = TextUtils.ellipsize(cell.text, tp, Math.max(avail, 10), TextUtils.TruncateAt.END).toString();
                float tx = r.centerX() + (cell.icon != 0 ? 11 * dp : 0);
                Paint.FontMetrics fm = tp.getFontMetrics();
                c.drawText(s, tx, H / 2 - (fm.ascent + fm.descent) / 2, tp);
                if (cell.icon != 0) {
                    float tw = tp.measureText(s);
                    gp.drawIcon(c, cell.icon, tx - tw / 2 - 12 * dp, H / 2, 17 * dp, col);
                }
                if (!cell.chip && !cell.title && prevWord != null) c.drawLine(r.left, H * 0.3f, r.left, H * 0.7f, div);
                prevWord = cell.chip || cell.title ? null : cell;
            } else if (cell.icon != 0) {
                gp.drawIcon(c, cell.icon, r.centerX(), H / 2, 22 * dp, col);
                prevWord = null;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = null;
                for (Cell c : cells) if (c.r.contains(e.getX(), e.getY()) && !c.title && c.action != null) pressed = c;
                downX = e.getX(); downY = e.getY();
                if (pressed != null) {
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
