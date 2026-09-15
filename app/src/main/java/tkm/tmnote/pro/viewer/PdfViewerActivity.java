package tkm.tmnote.pro.viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import tkm.tmnote.pro.R;
import tkm.tmnote.pro.utils.HapticUtils;
import tkm.tmnote.pro.utils.ThemeHelper;

import java.io.File;

/**
 * Продвинутый нативный просмотрщик PDF-файлов.
 * Особенности:
 * 1. Использует системный PdfRenderer без утяжеляющих сторонних библиотек.
 * 2. RecyclerView для отображения страниц: высокая производительность и плавная вертикальная прокрутка.
 * 3. Динамический номер страницы (например, "Страница 2 из 15") при прокрутке.
 * 4. Поддержка жестов Pinch-to-zoom (увеличение двумя пальцами) с возможностью перемещения страниц.
 */
public class PdfViewerActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private TextView tvName;
    private TextView tvPageIndicator;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private int pageCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        tkm.tmnote.pro.utils.SystemBarsHelper.apply(this);

        String path = getIntent().getStringExtra("path");
        String name = getIntent().getStringExtra("name");

        recycler = findViewById(R.id.rv_pdf);
        tvName = findViewById(R.id.tv_name);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);

        tvName.setText(name == null ? "Документ PDF" : name);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            HapticUtils.light(v);
            closeActivity();
        });

        if (path != null) {
            initPdf(new File(path));
        } else {
            Toast.makeText(this, "Путь к файлу не найден", Toast.LENGTH_SHORT).show();
            finish();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeActivity();
            }
        });
    }

    private void initPdf(File file) {
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            pageCount = renderer.getPageCount();

            tvPageIndicator.setText("1 / " + pageCount);

            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            recycler.setLayoutManager(layoutManager);
            
            PdfPageAdapter adapter = new PdfPageAdapter(this, renderer);
            recycler.setAdapter(adapter);

            // Динамический индикатор номера страницы при скролле
            recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    int firstVisible = layoutManager.findFirstVisibleItemPosition();
                    if (firstVisible != RecyclerView.NO_POSITION) {
                        tvPageIndicator.setText((firstVisible + 1) + " / " + pageCount);
                    }
                }
            });

            // Навешиваем поддержку жестов Зума и перемещения
            recycler.setOnTouchListener(new ZoomTouchListener());

        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть PDF: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            closeActivity();
        }
    }

    private void closeActivity() {
        cleanup();
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void cleanup() {
        if (renderer != null) {
            try { renderer.close(); } catch (Exception ignored) {}
            renderer = null;
        }
        if (pfd != null) {
            try { pfd.close(); } catch (Exception ignored) {}
            pfd = null;
        }
    }

    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    // ===== Адаптер страниц PDF =====
    private class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.VH> {
        private final Context ctx;
        private final PdfRenderer renderer;
        private final int count;

        PdfPageAdapter(Context ctx, PdfRenderer renderer) {
            this.ctx = ctx;
            this.renderer = renderer;
            this.count = renderer.getPageCount();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_pdf_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            PdfRenderer.Page page = null;
            try {
                // Извлекаем и рендерим страницу PDF
                page = renderer.openPage(pos);
                
                int w = page.getWidth();
                int hPage = page.getHeight();
                
                // Чтобы шрифт оставался четким и сглаженным при увеличении,
                // мы увеличиваем разрешение рендеринга страницы в 2.2 раза!
                float scale = 2.2f;
                int reqW = (int) (w * scale);
                int reqH = (int) (hPage * scale);
                
                Bitmap bitmap = Bitmap.createBitmap(reqW, reqH, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(android.graphics.Color.WHITE); // Белый фон страницы
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                h.imageView.setImageBitmap(bitmap);

            } catch (Exception e) {
                h.imageView.setImageResource(R.drawable.ic_file);
            } finally {
                if (page != null) {
                    try { page.close(); } catch (Exception ignored) {}
                }
            }
        }

        @Override
        public int getItemCount() {
            return count;
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView imageView;
            VH(@NonNull View v) {
                super(v);
                imageView = v.findViewById(R.id.iv_pdf_page);
            }
        }
    }

    // ===== Умный обработчик жестов ЗУМА ДВОЙНЫМ ТАПОМ и панорамирования =====
    private class ZoomTouchListener implements View.OnTouchListener {
        private final GestureDetector gestureDetector;
        private float scale = 1.0f;
        private float posX = 0f;
        private float posY = 0f;
        private float lastX, lastY;
        private int pointerId = -1;

        ZoomTouchListener() {
            gestureDetector = new GestureDetector(PdfViewerActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (scale > 1.0f) {
                        reset();
                    } else {
                        scale = 2.5f; // Комфортный зум 2.5х при двойном тапе
                        posX = 0f;
                        posY = 0f;
                        apply();
                    }
                    return true;
                }
            });
        }

        private void apply() {
            recycler.setScaleX(scale);
            recycler.setScaleY(scale);
            recycler.setTranslationX(posX);
            recycler.setTranslationY(posY);
        }

        public void reset() {
            scale = 1.0f;
            posX = 0f;
            posY = 0f;
            apply();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);

            if (scale > 1.0f) {
                // Если экран приближен — мы перемещаем (панорамируем) страницы
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        pointerId = event.getPointerId(0);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        int index = event.findPointerIndex(pointerId);
                        if (index >= 0) {
                            float rawX = event.getRawX();
                            float rawY = event.getRawY();

                            float dx = rawX - lastX;
                            float dy = rawY - lastY;

                            // Повышенный скоростной коэффициент для моментального скролла вблизи
                            posX += dx * 1.5f;
                            posY += dy * 1.5f;

                            apply();

                            lastX = rawX;
                            lastY = rawY;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        pointerId = -1;
                        break;
                }
                return true; // Поглощаем тачи для зума и сдвига
            } else {
                return false; // Отдаем тачи RecyclerView для стандартной прокрутки страниц!
            }
        }
    }
}
