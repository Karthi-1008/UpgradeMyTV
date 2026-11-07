package com.gathin.tvpowerhub;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import io.nayuki.qrcodegen.QrCode;

/**
 * Draws the branded "liquid" QR code. Geometry MUST match reference/styled-qr.js.
 * - Error correction HIGH (centre logo hides <4% of the area, ECC H tolerates ~30%).
 * - Dark rounded modules on warm paper, quiet zone 4 modules, deep-orange finder centres.
 * Call from a background thread; returns an ARGB_8888 bitmap (square, sizePx).
 */
public final class StyledQrRenderer {
    public static final int INK   = 0xFF14110F;
    public static final int PAPER = 0xFFFAF8F4;
    public static final int EYE   = 0xFFE0501A;
    private static final int QUIET = 4;
    private static final int LOGO_MODULES = 7;

    private StyledQrRenderer() {}

    /** @param logo optional square logo (any size); pass null to skip the centre logo. */
    public static Bitmap render(String text, int sizePx, Bitmap logo) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.HIGH);
        final int n = qr.size;
        final float total = n + QUIET * 2;
        final float u = sizePx / total;               // pixels per module (float -> exact fit)

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(PAPER);

        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(INK);
        ink.setStyle(Paint.Style.FILL);

        float mid = n / 2f;
        float half = LOGO_MODULES / 2f;

        Path path = new Path();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!qr.getModule(x, y) || inFinder(x, y, n)) continue;
                if (Math.abs(x + .5f - mid) < half + .5f && Math.abs(y + .5f - mid) < half + .5f) continue;
                boolean up = on(qr, x, y - 1), dn = on(qr, x, y + 1);
                boolean lf = on(qr, x - 1, y), rt = on(qr, x + 1, y);
                float l = (x + QUIET) * u, t = (y + QUIET) * u;
                addModule(path, l, t, u, !up && !lf, !up && !rt, !dn && !rt, !dn && !lf);
            }
        }
        c.drawPath(path, ink);

        // Finder patterns: ink ring (rounded) + deep-orange rounded centre
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(INK);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(u);
        Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
        eye.setColor(EYE);
        int[][] origins = {{0, 0}, {n - 7, 0}, {0, n - 7}};
        for (int[] o : origins) {
            float x = (o[0] + QUIET) * u, y = (o[1] + QUIET) * u;
            c.drawRoundRect(new RectF(x + .5f * u, y + .5f * u, x + 6.5f * u, y + 6.5f * u), 1.9f * u, 1.9f * u, ring);
            c.drawRoundRect(new RectF(x + 2f * u, y + 2f * u, x + 5f * u, y + 5f * u), 1f * u, 1f * u, eye);
        }

        // Centre logo plate
        if (logo != null) {
            float lp = LOGO_MODULES * u;
            float lx = (QUIET + mid) * u - lp / 2f;
            Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
            plate.setColor(PAPER);
            c.drawRoundRect(new RectF(lx, lx, lx + lp, lx + lp), 1.8f * u, 1.8f * u, plate);
            float pad = .45f * u;
            RectF dst = new RectF(lx + pad, lx + pad, lx + lp - pad, lx + lp - pad);
            Path clip = new Path();
            clip.addRoundRect(dst, 1.45f * u, 1.45f * u, Path.Direction.CW);
            int save = c.save();
            c.clipPath(clip);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(logo, null, dst, p);
            c.restoreToCount(save);
        }
        return bmp;
    }

    private static boolean on(QrCode qr, int x, int y) {
        return x >= 0 && y >= 0 && x < qr.size && y < qr.size && qr.getModule(x, y);
    }

    private static boolean inFinder(int x, int y, int n) {
        return (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8);
    }

    /** One module as a rounded rect where each corner is rounded only if flagged. */
    private static void addModule(Path p, float l, float t, float u, boolean tl, boolean tr, boolean br, boolean bl) {
        float r = u * 0.5f;
        float[] radii = {
            tl ? r : 0, tl ? r : 0,
            tr ? r : 0, tr ? r : 0,
            br ? r : 0, br ? r : 0,
            bl ? r : 0, bl ? r : 0
        };
        // 0.35px overlap hides hairline seams between neighbouring modules
        p.addRoundRect(new RectF(l - 0.18f, t - 0.18f, l + u + 0.18f, t + u + 0.18f), radii, Path.Direction.CW);
    }
}
