package com.daddypixels.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** The startup intro: a bouncing pixel heart (Pixels skin) or a booting terminal (Fallout skin). Tap to skip. */
class IntroView extends View {
    private static final long LENGTH = 1900;   // ms
    private static final String[] HEART = {
            ".XX...XX.",
            "XPPX.XPPX",
            "XPWPXPPPX",
            "XPPPPPPPX",
            ".XPPPPPX.",
            "..XPPPX..",
            "...XPX...",
            "....X....",
    };
    private static final String[] BOOT = {
            "> DADDY-TEC TERMINAL v1",
            "> LOADING ANDROID WEBVIEW ..... OK",
            "> TUNING PHOSPHOR ............. OK",
            "> WELCOME BACK, OVERSEER.",
    };

    private final boolean terminal;
    private final Typeface font;
    private final Runnable done;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();
    private boolean finishing, gone;
    private long finishAt;

    IntroView(Context c, boolean terminal, Typeface font, Runnable done) {
        super(c);
        this.terminal = terminal;
        this.font = font;
        this.done = done;
        setClickable(true);
    }

    void finish() {
        if (finishing) return;
        finishing = true;
        finishAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        long t = now - start;
        if (!finishing && t > LENGTH) finish();
        float fade = finishing ? Math.max(0f, 1f - (now - finishAt) / 250f) : 1f;
        if (finishing && fade <= 0f) { if (!gone) { gone = true; post(done); } return; }
        setAlpha(fade);

        int w = getWidth(), h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        if (terminal) drawTerminal(c, w, h, t, density); else drawPixels(c, w, h, t, density);
        postInvalidateOnAnimation();
    }

    private void drawPixels(Canvas c, int w, int h, long t, float d) {
        c.drawColor(Color.parseColor("#e3f5ff"));
        // pixel grid
        p.setColor(Color.parseColor("#ffffff"));
        float cell = 24 * d;
        for (float x = 0; x < w; x += cell) c.drawRect(x, 0, x + 2 * d, h, p);
        for (float y = 0; y < h; y += cell) c.drawRect(0, y, w, y + 2 * d, p);

        float px = Math.min(w, h) / 16f;
        float bounce = (float) Math.abs(Math.sin(t / 180.0)) * px * 1.2f;
        float hx = w / 2f - 4.5f * px, hy = h / 2f - 7f * px - bounce;
        for (int r = 0; r < HEART.length; r++) {
            for (int col = 0; col < HEART[r].length(); col++) {
                char ch = HEART[r].charAt(col);
                if (ch == '.') continue;
                p.setColor(ch == 'X' ? Color.parseColor("#2f5fe0") : ch == 'W' ? Color.WHITE : Color.parseColor("#ff6fb5"));
                c.drawRect(hx + col * px, hy + r * px, hx + (col + 1) * px, hy + (r + 1) * px, p);
            }
        }
        p.setTypeface(font);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(px * 1.3f);
        float ty = h / 2f + 3.2f * px;
        p.setColor(Color.parseColor("#ff6fb5"));
        c.drawText("DADDY PIXELS", w / 2f + px * 0.18f, ty + px * 0.18f, p);
        p.setColor(Color.parseColor("#2f5fe0"));
        c.drawText("DADDY PIXELS", w / 2f, ty, p);

        // loading bar
        float bw = Math.min(w * 0.7f, 320 * d), bh = px * 0.9f;
        float bx = (w - bw) / 2f, by = ty + px * 1.5f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        c.drawRect(bx, by, bx + bw, by + bh, p);
        p.setStyle(Paint.Style.FILL);
        float prog = Math.min(1f, t / (float) LENGTH);
        int blocks = (int) (prog * 14);
        float blk = (bw - 6 * d) / 14f;
        p.setColor(Color.parseColor("#ff6fb5"));
        for (int i = 0; i < blocks; i++) c.drawRect(bx + 3 * d + i * blk, by + 3 * d, bx + 3 * d + (i + 1) * blk - 3 * d, by + bh - 3 * d, p);
    }

    private void drawTerminal(Canvas c, int w, int h, long t, float d) {
        c.drawColor(Color.parseColor("#07140b"));
        p.setTypeface(font);
        p.setTextAlign(Paint.Align.LEFT);
        float size = Math.min(w / 22f, 26 * d);
        p.setTextSize(size);
        p.setShadowLayer(6 * d, 0, 0, Color.parseColor("#993cff7a"));
        p.setColor(Color.parseColor("#3cff7a"));
        int chars = (int) (t / 1000f * 70);       // typing speed
        float x = 16 * d, y = h / 3f;
        for (String line : BOOT) {
            if (chars <= 0) break;
            String part = line.substring(0, Math.min(line.length(), chars));
            c.drawText(part, x, y, p);
            chars -= line.length() + 6;
            y += size * 1.4f;
        }
        if ((t / 400) % 2 == 0) c.drawText("█", x, y, p);
        p.setShadowLayer(0, 0, 0, 0);
        // scanlines
        p.setColor(Color.argb(70, 0, 0, 0));
        for (float sy = 0; sy < h; sy += 4 * d) c.drawRect(0, sy, w, sy + 2 * d, p);
    }
}
