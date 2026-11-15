package com.github.eka2l1.belle;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * The control hints drawn over every Symbian app: a slim Belle strip along the bottom with a button icon and its meaning
 * for each key (A select, X options, B back). It is never touchable, so it cannot get in the way of the app, and it
 * hides itself a few seconds after the app opens (and shows again when a controller button is pressed).
 */
public final class BelleHints extends View {
    private static final String[] ICON = {"A", "X", "B"};
    private static final String[] LABEL = {"Select", "Options", "Back"};
    private static final int[] COLOR = {0xFF3CB44B, 0xFF1080DD, 0xFFE0443A};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Runnable fade = new Runnable() {
        @Override
        public void run() {
            animate().alpha(0f).setDuration(400).start();
        }
    };

    private BelleHints(Context c) {
        super(c);
        setClickable(false);
        setFocusable(false);
        setAlpha(0f);
    }

    /** Adds the hint strip over [activity]'s content. */
    public static BelleHints attach(Activity activity) {
        BelleHints h = new BelleHints(activity);
        float d = activity.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (36 * d), Gravity.BOTTOM);
        activity.addContentView(h, lp);
        h.poke();
        return h;
    }

    /** Shows the strip for a few seconds. */
    public void poke() {
        removeCallbacks(fade);
        animate().cancel();
        setAlpha(1f);
        postDelayed(fade, 4000);
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xE0343434, 0xE0141414, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x33FFFFFF);
        c.drawRect(0, 0, w, 1, p);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        float cell = w / ICON.length;
        for (int i = 0; i < ICON.length; i++) {
            float cx = cell * i + cell / 2f;
            float cy = h / 2f;
            float rad = 11 * d;
            // the button, as on the controller
            p.setStyle(Paint.Style.FILL);
            p.setColor(COLOR[i]);
            c.drawCircle(cx - 38 * d, cy, rad, p);
            p.setColor(Color.WHITE);
            p.setTextSize(14 * d);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(ICON[i], cx - 38 * d, cy + 5 * d, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(15 * d);
            p.setColor(0xFFE8E8E8);
            c.drawText(LABEL[i], cx - 22 * d, cy + 5.5f * d, p);
        }
    }
}
