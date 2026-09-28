package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** A vertical list of glass cards with optional small action icons (clipboard, snippets). Lives in a ScrollView. */
public class CardList extends View {

    public static class Card {
        public String title;    // optional bold first line
        public String body;
        public String badge;    // e.g. shortcut ";upi"
        public int[] icons = new int[0];
        public Object tag;
        final RectF r = new RectF();
        final RectF[] iconRects = new RectF[4];
        StaticLayout layout;
    }

    public interface OnCard {
        void tap(Card c);
        void icon(Card c, int iconIndex);
    }

    private final GlassPainter gp;
    private final float dp;
    private Theme theme = Theme.get(0);
    private final List<Card> cards = new ArrayList<>();
    private OnCard onCard;
    private final TextPaint body = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint bold = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private Card pressed;
    private int pressedIcon = -1;
    private float downX, downY;
    private String emptyText = "";
    public Runnable feedback;

    public CardList(Context c, GlassPainter gp) {
        super(c);
        this.gp = gp;
        dp = c.getResources().getDisplayMetrics().density;
        body.setTextSize(14 * dp);
        bold.setTextSize(14 * dp);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setTheme(Theme t) { theme = t; invalidate(); }
    public void setOnCard(OnCard o) { onCard = o; }

    public void setCards(List<Card> list, String empty) {
        cards.clear();
        cards.addAll(list);
        emptyText = empty;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float y = 6 * dp;
        float pad = 8 * dp;
        for (Card c : cards) {
            float iconsW = c.icons.length * 36 * dp;
            int textW = (int) Math.max(40 * dp, w - 2 * pad - 24 * dp - iconsW);
            String txt = c.body.length() > 400 ? c.body.substring(0, 400) + "…" : c.body;
            c.layout = StaticLayout.Builder.obtain(txt, 0, txt.length(), body, textW)
                    .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
            float hh = c.layout.getHeight() + 22 * dp + (c.title != null ? 20 * dp : 0);
            hh = Math.max(hh, 52 * dp);
            c.r.set(pad, y, w - pad, y + hh);
            float ix = c.r.right - 8 * dp;
            for (int i = c.icons.length - 1; i >= 0; i--) {
                c.iconRects[i] = new RectF(ix - 34 * dp, c.r.centerY() - 17 * dp, ix, c.r.centerY() + 17 * dp);
                ix -= 36 * dp;
            }
            y += hh + 8 * dp;
        }
        setMeasuredDimension(w, (int) Math.max(y + 4 * dp, 140 * dp));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        body.setColor(theme.text);
        bold.setColor(theme.text);
        if (cards.isEmpty()) {
            body.setColor(theme.subText);
            body.setTextAlign(android.graphics.Paint.Align.CENTER);
            canvas.drawText(emptyText, getWidth() / 2f, 64 * dp, body);
            body.setTextAlign(android.graphics.Paint.Align.LEFT);
            return;
        }
        for (Card c : cards) {
            gp.drawGlass(canvas, c.r, 14 * dp, GlassPainter.STYLE_KEY, c == pressed && pressedIcon < 0, theme);
            float x = c.r.left + 12 * dp, y = c.r.top + 11 * dp;
            if (c.title != null) {
                String t = c.title;
                if (c.badge != null && !c.badge.isEmpty()) t = t + "   ";
                canvas.drawText(t, x, y + 14 * dp, bold);
                if (c.badge != null && !c.badge.isEmpty()) {
                    float tw = bold.measureText(t);
                    body.setColor(theme.accent);
                    canvas.drawText(c.badge, x + tw, y + 14 * dp, body);
                    body.setColor(theme.text);
                }
                y += 20 * dp;
            }
            canvas.save();
            canvas.translate(x, y);
            c.layout.draw(canvas);
            canvas.restore();
            for (int i = 0; i < c.icons.length; i++) {
                RectF ir = c.iconRects[i];
                if (c == pressed && pressedIcon == i) gp.drawGlass(canvas, ir, ir.height() / 2, GlassPainter.STYLE_KEY, true, theme);
                gp.drawIcon(canvas, c.icons[i], ir.centerX(), ir.centerY(), 19 * dp,
                        c.icons[i] == GlassPainter.IC_PIN_ON ? theme.accent : theme.subText);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                pressed = null; pressedIcon = -1;
                for (Card c : cards) if (c.r.contains(downX, downY)) {
                    pressed = c;
                    for (int i = 0; i < c.icons.length; i++) if (c.iconRects[i].contains(downX, downY)) pressedIcon = i;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed != null && Math.hypot(e.getX() - downX, e.getY() - downY) > 12 * dp) { pressed = null; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                Card p = pressed; int pi = pressedIcon;
                pressed = null; pressedIcon = -1;
                invalidate();
                if (p != null && onCard != null) {
                    if (feedback != null) feedback.run();
                    if (pi >= 0) onCard.icon(p, pi); else onCard.tap(p);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = null;
                invalidate();
                return true;
        }
        return true;
    }
}
