package com.zametki.pro.viewer;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.zametki.pro.R;
import com.zametki.pro.utils.HapticUtils;

import java.io.File;

/**
 * Простой просмотрщик изображений с pinch-to-zoom через ScaleGestureDetector.
 */
public class ImageViewerActivity extends AppCompatActivity {

    private ImageView imageView;
    private ScaleGestureDetector scaleDetector;
    private float scale = 1f;
    private float minScale = 1f, maxScale = 5f;
    private float lastTouchX, lastTouchY;
    private float posX, posY;
    private int pointerId = MotionEvent.INVALID_POINTER_ID;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_image_viewer);

        String path = getIntent().getStringExtra("path");
        String name = getIntent().getStringExtra("name");

        imageView = findViewById(R.id.image);
        TextView tvName = findViewById(R.id.tv_name);
        tvName.setText(name == null ? "" : name);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            HapticUtils.light(v); finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            HapticUtils.light(v);
            scale = 1f; posX = 0; posY = 0;
            applyTransform();
        });

        loadImage(path);

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                scale *= d.getScaleFactor();
                scale = Math.max(minScale, Math.min(scale, maxScale));
                applyTransform();
                return true;
            }
        });

        imageView.setOnTouchListener(this::onTouch);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
        });
    }

    private void loadImage(String path) {
        if (path == null) return;
        File f = new File(path);
        if (!f.exists()) return;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int sample = 1;
            int maxDim = Math.max(getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels) * 2;
            while ((opts.outWidth / sample) > maxDim || (opts.outHeight / sample) > maxDim) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp != null) imageView.setImageBitmap(bmp);
        } catch (Exception ignored) {}
    }

    private boolean onTouch(View v, MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = e.getX();
                lastTouchY = e.getY();
                pointerId = e.getPointerId(0);
                break;
            case MotionEvent.ACTION_MOVE:
                if (scale > 1f && !scaleDetector.isInProgress()) {
                    int idx = e.findPointerIndex(pointerId);
                    if (idx >= 0) {
                        float x = e.getX(idx);
                        float y = e.getY(idx);
                        posX += (x - lastTouchX);
                        posY += (y - lastTouchY);
                        applyTransform();
                        lastTouchX = x; lastTouchY = y;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pointerId = MotionEvent.INVALID_POINTER_ID;
                break;
        }
        return true;
    }

    private void applyTransform() {
        imageView.setScaleX(scale);
        imageView.setScaleY(scale);
        imageView.setTranslationX(posX);
        imageView.setTranslationY(posY);
    }

}
