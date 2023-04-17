package org.jfedor.facefollower;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class FaceOverlay extends View {
    
    Paint paint;
    private RectF faceRect;
    Matrix matrix;
    public int orientation;

    public FaceOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(0xff00ff00);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
        faceRect = new RectF();
        matrix = new Matrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        matrix.reset();
        matrix.setScale(-1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(orientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(w / 2000f, h / 2000f);
        matrix.postTranslate(w / 2f, h / 2f);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (!faceRect.isEmpty()) {
            canvas.drawRect(faceRect, paint);
        }
    }
    
    public void setRect(Rect rect) {
        if (rect == null) {
            faceRect.setEmpty();
        } else {
            faceRect.set(rect);
            matrix.mapRect(faceRect);
        }
    }
}
