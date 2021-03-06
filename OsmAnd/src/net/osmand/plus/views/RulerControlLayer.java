package net.osmand.plus.views;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;

import net.osmand.Location;
import net.osmand.data.QuadPoint;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings.RulerMode;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;

import java.util.ArrayList;

public class RulerControlLayer extends OsmandMapLayer {

    private static final int TEXT_SIZE = 14;
    private final MapActivity mapActivity;
    private OsmandApplication app;
    private OsmandMapTileView view;

    private TextSide textSide;
    private int maxRadiusInDp;
    private float maxRadius;
    private int radius;
    private double roundedDist;

    private QuadPoint cacheCenter;
    private int cacheZoom;
    private double cacheTileX;
    private double cacheTileY;
    private ArrayList<String> cacheDistances;

    private Bitmap centerIconDay;
    private Bitmap centerIconNight;
    private Paint bitmapPaint;
    private RenderingLineAttributes lineAttrs;
    private RenderingLineAttributes circleAttrs;

    public RulerControlLayer(MapActivity mapActivity) {
        this.mapActivity = mapActivity;
    }

    @Override
    public void initLayer(OsmandMapTileView view) {
        app = mapActivity.getMyApplication();
        this.view = view;
        cacheDistances = new ArrayList<>();
        cacheCenter = new QuadPoint();
        maxRadiusInDp = mapActivity.getResources().getDimensionPixelSize(R.dimen.map_ruler_radius);

        centerIconDay = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_ruler_center_day);
        centerIconNight = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_ruler_center_night);

        bitmapPaint = new Paint();
        bitmapPaint.setAntiAlias(true);
        bitmapPaint.setDither(true);
        bitmapPaint.setFilterBitmap(true);

        lineAttrs = new RenderingLineAttributes("rulerLine");

        circleAttrs = new RenderingLineAttributes("rulerCircle");
        circleAttrs.paint.setStrokeWidth(2);
        circleAttrs.paint2.setTextSize(TEXT_SIZE * mapActivity.getResources().getDisplayMetrics().density);
        circleAttrs.paint3.setColor(Color.WHITE);
        circleAttrs.paint3.setStrokeWidth(6);
        circleAttrs.paint3.setTextSize(TEXT_SIZE * mapActivity.getResources().getDisplayMetrics().density);
        circleAttrs.shadowPaint.setStrokeWidth(6);
        circleAttrs.shadowPaint.setColor(Color.WHITE);
    }

    @Override
    public void onDraw(Canvas canvas, RotatedTileBox tb, DrawSettings settings) {
        if (mapActivity.getMapLayers().getMapWidgetRegistry().isVisible("ruler")) {
            lineAttrs.updatePaints(view, settings, tb);
            circleAttrs.updatePaints(view, settings, tb);
            circleAttrs.paint2.setStyle(Style.FILL);
            final QuadPoint center = tb.getCenterPixelPoint();
            final RulerMode mode = app.getSettings().RULER_MODE.get();

            if (mode == RulerMode.FIRST) {
                if (view.isMultiTouch()) {
                    float x1 = view.getFirstTouchPointX();
                    float y1 = view.getFirstTouchPointY();
                    float x2 = view.getSecondTouchPointX();
                    float y2 = view.getSecondTouchPointY();
                    drawFingerDistance(canvas, tb, center, x1, y1, x2, y2);
                } else {
                    drawCenterIcon(canvas, tb, center, settings.isNightMode());
                    Location currentLoc = app.getLocationProvider().getLastKnownLocation();
                    if (currentLoc != null) {
                        drawDistance(canvas, tb, center, currentLoc);
                    }
                }
            } else if (mode == RulerMode.SECOND) {
                drawCenterIcon(canvas, tb, center, settings.isNightMode());
                updateData(tb, center);
                for (int i = 1; i <= cacheDistances.size(); i++) {
                    drawCircle(canvas, tb, i, center);
                }
            }
        }
    }

    private void drawFingerDistance(Canvas canvas, RotatedTileBox tb, QuadPoint center, float x1, float y1, float x2, float y2) {
        canvas.rotate(-tb.getRotate(), center.x, center.y);
        canvas.drawLine(x1, y1, x2, y2, lineAttrs.paint);
        canvas.rotate(tb.getRotate(), center.x, center.y);
    }

    private void drawCenterIcon(Canvas canvas, RotatedTileBox tb, QuadPoint center, boolean nightMode) {
        canvas.rotate(-tb.getRotate(), center.x, center.y);
        if (nightMode) {
            canvas.drawBitmap(centerIconNight, center.x - centerIconNight.getWidth() / 2,
                    center.y - centerIconNight.getHeight() / 2, bitmapPaint);
        } else {
            canvas.drawBitmap(centerIconDay, center.x - centerIconDay.getWidth() / 2,
                    center.y - centerIconDay.getHeight() / 2, bitmapPaint);
        }
        canvas.rotate(tb.getRotate(), center.x, center.y);
    }

    private void drawDistance(Canvas canvas, RotatedTileBox tb, QuadPoint center, Location currentLoc) {
        int currentLocX = tb.getPixXFromLonNoRot(currentLoc.getLongitude());
        int currentLocY = tb.getPixYFromLatNoRot(currentLoc.getLatitude());
        canvas.drawLine(currentLocX, currentLocY, center.x, center.y, lineAttrs.paint);
    }

    private void updateData(RotatedTileBox tb, QuadPoint center) {
        if (tb.getPixHeight() > 0 && tb.getPixWidth() > 0 && maxRadiusInDp > 0) {
            if (cacheCenter.y != center.y || cacheCenter.x != center.x) {
                cacheCenter = center;
                updateCenter(tb, center);
            }

            boolean move = tb.getZoom() != cacheZoom || Math.abs(tb.getCenterTileX() - cacheTileX) > 1 ||
                    Math.abs(tb.getCenterTileY() - cacheTileY) > 1;

            if (!mapActivity.getMapView().isZooming() && move) {
                cacheZoom = tb.getZoom();
                cacheTileX = tb.getCenterTileX();
                cacheTileY = tb.getCenterTileY();
                cacheDistances.clear();
                updateDistance(tb);
            }
        }
    }

    private void updateCenter(RotatedTileBox tb, QuadPoint center) {
        float topDist = center.y;
        float bottomDist = tb.getPixHeight() - center.y;
        float leftDist = center.x;
        float rightDist = tb.getPixWidth() - center.x;
        float maxVertical = topDist >= bottomDist ? topDist : bottomDist;
        float maxHorizontal = rightDist >= leftDist ? rightDist : leftDist;

        if (maxVertical >= maxHorizontal) {
            maxRadius = maxVertical;
            textSide = TextSide.VERTICAL;
        } else {
            maxRadius = maxHorizontal;
            textSide = TextSide.HORIZONTAL;
        }
        if (radius != 0) {
            updateText();
        }
    }

    private void updateDistance(RotatedTileBox tb) {
        final double dist = tb.getDistance(0, tb.getPixHeight() / 2, tb.getPixWidth(), tb.getPixHeight() / 2);
        double pixDensity = tb.getPixWidth() / dist;
        roundedDist = OsmAndFormatter.calculateRoundedDist(maxRadiusInDp / pixDensity, app);
        radius = (int) (pixDensity * roundedDist);
        updateText();
    }

    private void updateText() {
        double maxCircleRadius = maxRadius;
        int i = 1;
        while ((maxCircleRadius -= radius) > 0) {
            cacheDistances.add(OsmAndFormatter
                    .getFormattedDistance((float) roundedDist * i++, app, false).replaceAll(" ", ""));
        }
    }

    private void drawCircle(Canvas canvas, RotatedTileBox tb, int circleNumber, QuadPoint center) {
        Rect bounds = new Rect();
        String text = cacheDistances.get(circleNumber - 1);
        circleAttrs.paint2.getTextBounds(text, 0, text.length(), bounds);

        // coords of left or top text
        float x1 = 0;
        float y1 = 0;
        // coords of right or bottom text
        float x2 = 0;
        float y2 = 0;

        if (textSide == TextSide.VERTICAL) {
            x1 = center.x - bounds.width() / 2;
            y1 = center.y - radius * circleNumber + bounds.height() / 2;
            x2 = center.x - bounds.width() / 2;
            y2 = center.y + radius * circleNumber + bounds.height() / 2;
        } else if (textSide == TextSide.HORIZONTAL) {
            x1 = center.x - radius * circleNumber - bounds.width() / 2;
            y1 = center.y + bounds.height() / 2;
            x2 = center.x + radius * circleNumber - bounds.width() / 2;
            y2 = center.y + bounds.height() / 2;
        }

        if (!mapActivity.getMapView().isZooming()) {
            canvas.rotate(-tb.getRotate(), center.x, center.y);
            canvas.drawCircle(center.x, center.y, radius * circleNumber, circleAttrs.shadowPaint);
            canvas.drawCircle(center.x, center.y, radius * circleNumber, circleAttrs.paint);
            canvas.drawText(text, x1, y1, circleAttrs.paint3);
            canvas.drawText(text, x1, y1, circleAttrs.paint2);
            canvas.drawText(text, x2, y2, circleAttrs.paint3);
            canvas.drawText(text, x2, y2, circleAttrs.paint2);
            canvas.rotate(tb.getRotate(), center.x, center.y);
        }
    }

    private enum TextSide {
        VERTICAL,
        HORIZONTAL
    }

    @Override
    public void destroyLayer() {

    }

    @Override
    public boolean drawInScreenPixels() {
        return false;
    }
}
