package com.swift.radiohome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Stroke icons drawn on a 24x24 grid, the same shapes as the design mockup. API 17 has no VectorDrawable, and
 * drawing them here costs no bitmaps and no extra library.
 */
final class IconView extends View {
    static final int NAV = 0, MUSIC = 1, RADIO = 2, PHONE = 3, BLUETOOTH = 4, USB = 5, EQ = 6, GRID = 7, BACK = 8,
            GPS = 9, PLAY = 10, WIFI = 11, STACK = 12, CLOSE = 13, GEAR = 14, MOON = 15;

    private final int icon;
    private final float strokeWidth;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    IconView(Context context, int icon, int color, float strokeWidth) {
        super(context);
        this.icon = icon;
        this.strokeWidth = strokeWidth;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        // Skia's software anti-aliasing is much smoother than the API 17 GPU path renderer; these views are small and
        // drawn once, so the extra layer costs a few KB.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    void setColor(int color) {
        if (paint.getColor() == color) return;
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        // The path is built on the 24x24 grid and then transformed to view pixels, instead of scaling the canvas:
        // the API 17 hardware renderer rasterises a path at its own size and stretches that texture under
        // canvas.scale(), which pixelates the big tiles. The stroke width is in grid units, like an SVG stroke.
        paint.setStrokeWidth(strokeWidth * s);
        path.reset();
        switch (icon) {
            case NAV:
                poly(3, 11, 21, 3, 13, 21, 11, 13);
                path.close();
                break;
            case MUSIC:
                poly(9, 18, 9, 5, 21, 3, 21, 16);
                circle(6, 18, 3);
                circle(18, 16, 3);
                break;
            case RADIO:
                rect.set(3, 8, 21, 20);
                path.addRoundRect(rect, 2, 2, Path.Direction.CW);
                line(7, 8, 17, 3);
                circle(16, 14, 2.5f);
                line(6, 12, 10, 12);
                line(6, 15, 10, 15);
                break;
            case PHONE:
                path.moveTo(5, 3);
                path.lineTo(9, 3);
                path.lineTo(10.5f, 7.5f);
                path.lineTo(8.5f, 9);
                path.cubicTo(9.5f, 11.5f, 12.5f, 14.5f, 15, 15.5f);
                path.lineTo(16.5f, 13.5f);
                path.lineTo(21, 15);
                path.lineTo(21, 19);
                path.cubicTo(21, 20, 20, 21, 19, 21);
                path.cubicTo(10, 21, 3, 14, 3, 5);
                path.cubicTo(3, 4, 4, 3, 5, 3);
                path.close();
                break;
            case BLUETOOTH:
                poly(7, 7, 17, 17, 12, 22, 12, 2, 17, 7, 7, 17);
                break;
            case USB:
                circle(12, 20, 2);
                line(12, 18, 12, 3);
                poly(9, 6, 12, 3, 15, 6);
                poly(6, 9, 6, 13, 12, 16);
                poly(18, 7, 18, 12, 12, 15);
                break;
            case EQ:
                line(4, 6, 14, 6);
                line(18, 6, 20, 6);
                line(4, 12, 8, 12);
                line(12, 12, 20, 12);
                line(4, 18, 16, 18);
                circle(16, 6, 2);
                circle(10, 12, 2);
                circle(18, 18, 2);
                break;
            case GRID:
                square(3, 3);
                square(14, 3);
                square(3, 14);
                square(14, 14);
                break;
            case BACK:
                poly(15, 18, 9, 12, 15, 6);
                break;
            case PLAY:
                rect.set(2, 5, 22, 19);
                path.addRoundRect(rect, 4, 4, Path.Direction.CW);
                poly(10, 9, 15, 12, 10, 15);
                path.close();
                break;
            case WIFI:
                arc(14);
                arc(9);
                arc(4);
                circle(12, 19.5f, 0.5f);
                break;
            case STACK:
                rect.set(8, 3, 21, 16);
                path.addRoundRect(rect, 2, 2, Path.Direction.CW);
                path.moveTo(4, 8);
                path.lineTo(4, 19);
                path.quadTo(4, 21, 6, 21);
                path.lineTo(17, 21);
                break;
            case CLOSE:
                line(6, 6, 18, 18);
                line(18, 6, 6, 18);
                break;
            case MOON:
                // A crescent: the 9-unit disc with a bite taken out of its top-right (Lucide's moon).
                path.moveTo(12, 3);
                rect.set(10.14f, 1.14f, 22.86f, 13.86f);
                path.arcTo(rect, -135, -180);
                rect.set(3, 3, 21, 21);
                path.arcTo(rect, 0, 270);
                path.close();
                break;
            case GEAR:
                circle(12, 12, 3);
                line(12, 2, 12, 5);
                line(12, 19, 12, 22);
                line(2, 12, 5, 12);
                line(19, 12, 22, 12);
                line(4.9f, 4.9f, 7, 7);
                line(17, 17, 19.1f, 19.1f);
                line(4.9f, 19.1f, 7, 17);
                line(17, 7, 19.1f, 4.9f);
                break;
            case GPS:
                circle(12, 12, 3);
                circle(12, 12, 7);
                line(12, 2, 12, 5);
                line(12, 19, 12, 22);
                line(2, 12, 5, 12);
                line(19, 12, 22, 12);
                break;
        }
        matrix.setScale(s, s);
        matrix.postTranslate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        path.transform(matrix);
        canvas.drawPath(path, paint);
    }

    private void poly(float... xy) {
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
    }

    private void line(float x1, float y1, float x2, float y2) {
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
    }

    private void circle(float cx, float cy, float r) {
        path.addCircle(cx, cy, r, Path.Direction.CW);
    }

    /** A quarter circle opening upwards around (12, 20), for the Wi-Fi waves. */
    private void arc(float r) {
        rect.set(12 - r, 20 - r, 12 + r, 20 + r);
        path.addArc(rect, -135, 90);
    }

    private void square(float x, float y) {
        rect.set(x, y, x + 7, y + 7);
        path.addRoundRect(rect, 1.5f, 1.5f, Path.Direction.CW);
    }
}
