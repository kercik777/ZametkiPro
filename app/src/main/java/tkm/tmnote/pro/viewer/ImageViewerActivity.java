package tkm.tmnote.pro.viewer;

import tkm.tmnote.pro.R;
import tkm.tmnote.pro.utils.ThemeHelper;
import tkm.tmnote.pro.utils.HapticUtils;
import tkm.tmnote.pro.utils.AttachmentUtils;
import tkm.tmnote.pro.models.Attachment;
import tkm.tmnote.pro.db.NotesRepository;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Продвинутый и невероятно красивый просмотрщик галереи изображений заметки (как в Google Photos и Telegram!).
 * Финальная, ультра-оптимизированная версия:
 * 1. Полностью убрано масштабирование двумя пальцами (Pinch-to-zoom) для исключения любых физических дрожаний экрана!
 * 2. Оставлено идеальное, 100% стабильное масштабирование ДВОЙНЫМ ТАПОМ (Double-Tap) от 1.0x до 3.5x.
 * 3. Реализовано СВЕРХБЫСТРОЕ ПАНОРАМИРОВАНИЕ (с повышенным скоростным коэффициентом) на абсолютных координатах.
 * 4. Автоскрытие панелей через 3 сек, показ/скрытие при тапе, и лента миниатюр внизу.
 * 5. Полная поддержка светлых и темных тем оформления ЗаметкиPro.
 */
public class ImageViewerActivity extends AppCompatActivity {

    public interface OnItemClickListener {
        void onClick(int position);
    }

    public interface PlaylistSelectionListener {
        void onSelectionChanged();
    }

    private ViewPager2 viewPager;
    private RecyclerView rvThumbnails;
    private TextView tvName;
    private ImageView btnPlaylist;
    
    private final List<Attachment> imageAttachments = new ArrayList<>();
    private PageAdapter pageAdapter;
    private ThumbAdapter thumbAdapter;
    private int currentPosition = 0;
    private long noteId = 0L;
    private NotesRepository repo;

    // Управление видимостью интерфейса (Google Photos Style)
    private boolean isUiVisible = true;
    private final Runnable autoHideRunnable = this::hideUi;
    private static final long AUTO_HIDE_DELAY = 3000; // 3 секунды

    // Ссылка на активный обработчик зума текущей страницы для сброса состояния
    private ZoomTouchListener activeZoomListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Подключаем тему
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        tkm.tmnote.pro.utils.SystemBarsHelper.apply(this);

        // Получаем список вложений и стартовый индекс
        List<Attachment> list = (List<Attachment>) getIntent().getSerializableExtra("images");
        currentPosition = getIntent().getIntExtra("index", 0);
        noteId = getIntent().getLongExtra("note_id", 0L);
        repo = new NotesRepository(this);

        if (list != null) {
            imageAttachments.addAll(list);
        }

        // Если список пуст, создаем фейковый элемент из старых параметров для совместимости
        if (imageAttachments.isEmpty()) {
            String path = getIntent().getStringExtra("path");
            String name = getIntent().getStringExtra("name");
            if (path != null) {
                File file = new File(path);
                Attachment fallback = new Attachment(Attachment.TYPE_IMAGE, file.getName(), name, file.length());
                imageAttachments.add(fallback);
            }
        }

        initUi();
        setupViewPager();
        setupThumbnails();

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            HapticUtils.light(v);
            closeActivity();
        });

        if (btnPlaylist != null) {
            btnPlaylist.setOnClickListener(v -> {
                HapticUtils.light(v);
                showPlaylistDialog();
            });
        }

        // Запускаем таймер автоскрытия панелей при открытии
        resetAutoHideTimer();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeActivity();
            }
        });
    }

    private void initUi() {
        viewPager = findViewById(R.id.view_pager);
        rvThumbnails = findViewById(R.id.rv_thumbnails);
        tvName = findViewById(R.id.tv_name);
        btnPlaylist = findViewById(R.id.btn_playlist);
    }

    private void setupViewPager() {
        pageAdapter = new PageAdapter(this, imageAttachments);
        viewPager.setAdapter(pageAdapter);
        viewPager.setCurrentItem(currentPosition, false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                
                // Сбрасываем зум предыдущей картинки
                if (activeZoomListener != null) {
                    activeZoomListener.reset();
                }

                // Устанавливаем заголовок активного изображения
                Attachment a = imageAttachments.get(position);
                tvName.setText(a.displayName == null ? a.fileName : a.displayName);

                // Синхронизируем ленту миниатюр снизу
                if (thumbAdapter != null) {
                    thumbAdapter.setActivePosition(position);
                    rvThumbnails.smoothScrollToPosition(position);
                }

                // Продлеваем отображение панелей при активном свайпе
                if (isUiVisible) {
                    resetAutoHideTimer();
                }
            }
        });
    }

    private void setupThumbnails() {
        rvThumbnails.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        thumbAdapter = new ThumbAdapter(this, imageAttachments, position -> {
            viewPager.setCurrentItem(position, true);
        });
        rvThumbnails.setAdapter(thumbAdapter);
        thumbAdapter.setActivePosition(currentPosition);
        rvThumbnails.scrollToPosition(currentPosition);

        // Если в заметке всего одна картинка — скрываем нижнюю ленту миниатюр за ненадобностью!
        if (imageAttachments.size() <= 1) {
            findViewById(R.id.layout_bottom_strip).setVisibility(View.GONE);
            if (btnPlaylist != null) btnPlaylist.setVisibility(View.GONE);
        }
    }

    private void toggleUiVisibility() {
        if (isUiVisible) {
            hideUi();
        } else {
            showUi();
        }
    }

    private void showUi() {
        isUiVisible = true;
        findViewById(R.id.layout_top_bar).animate().alpha(1.0f).setDuration(250).withStartAction(() -> {
            findViewById(R.id.layout_top_bar).setVisibility(View.VISIBLE);
        }).start();

        if (imageAttachments.size() > 1) {
            findViewById(R.id.layout_bottom_strip).animate().alpha(1.0f).setDuration(250).withStartAction(() -> {
                findViewById(R.id.layout_bottom_strip).setVisibility(View.VISIBLE);
            }).start();
        }

        resetAutoHideTimer();
    }

    private void hideUi() {
        isUiVisible = false;
        findViewById(R.id.layout_top_bar).animate().alpha(0.0f).setDuration(250).withEndAction(() -> {
            findViewById(R.id.layout_top_bar).setVisibility(View.GONE);
        }).start();

        findViewById(R.id.layout_bottom_strip).animate().alpha(0.0f).setDuration(250).withEndAction(() -> {
            findViewById(R.id.layout_bottom_strip).setVisibility(View.GONE);
        }).start();

        cancelAutoHideTimer();
    }

    private void resetAutoHideTimer() {
        // Автоматическое скрытие отключено по запросу пользователя. Панели скрываются только по тапу.
    }

    private void cancelAutoHideTimer() {
        viewPager.removeCallbacks(autoHideRunnable);
    }

    private void closeActivity() {
        cancelAutoHideTimer();
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        cancelAutoHideTimer();
        super.onDestroy();
    }

    // ===== Адаптер слайдера картинок (ViewPager2) =====
    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        private final Context ctx;
        private final List<Attachment> items;

        PageAdapter(Context ctx, List<Attachment> items) {
            this.ctx = ctx;
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_image_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Attachment a = items.get(pos);
            File file = AttachmentUtils.getFile(ctx, a.fileName);

            Glide.with(h.imageView)
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(h.imageView);

            // Создаем и привязываем умный обработчик зума и панорамирования для этой страницы
            ZoomTouchListener zoomListener = new ZoomTouchListener(viewPager, h.imageView);
            h.imageView.setOnTouchListener(zoomListener);

            // Если это текущая активная страница — запоминаем её обработчик
            if (pos == currentPosition) {
                activeZoomListener = zoomListener;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView imageView;
            VH(@NonNull View v) {
                super(v);
                imageView = v.findViewById(R.id.iv_page_image);
            }
        }
    }

    // ===== Адаптер ленты миниатюр (RecyclerView) =====
    private class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.VH> {

        private final Context ctx;
        private final List<Attachment> items;
        private final OnItemClickListener listener;
        private int activePos = 0;

        ThumbAdapter(Context ctx, List<Attachment> items, OnItemClickListener listener) {
            this.ctx = ctx;
            this.items = items;
            this.listener = listener;
        }

        void setActivePosition(int pos) {
            int old = activePos;
            activePos = pos;
            notifyItemChanged(old);
            notifyItemChanged(activePos);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_image_thumbnail, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Attachment a = items.get(pos);
            File file = AttachmentUtils.getFile(ctx, a.fileName);

            Glide.with(h.thumbnail)
                    .load(file)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(h.thumbnail);

            // Показываем золотую рамку для активной миниатюры
            h.borderSelected.setVisibility(pos == activePos ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(v -> {
                HapticUtils.light(v);
                if (listener != null) {
                    listener.onClick(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            View borderSelected;
            VH(@NonNull View v) {
                super(v);
                thumbnail = v.findViewById(R.id.iv_thumb_image);
                borderSelected = v.findViewById(R.id.v_border_selected);
            }
        }
    }

    /**
     * Сверхбыстрый и надежный обработчик Touch-событий, ЗУМА ДВОЙНЫМ КАСАНИЕМ и скоростного панорамирования.
     * Полностью исключает дрожание экрана, так как двухпальцевый Pinch-зум удален по выбору пользователя.
     */
    private class ZoomTouchListener implements View.OnTouchListener {
        private final ViewPager2 pager;
        private final ImageView iv;
        private final GestureDetector gd;
        
        private float scale = 1.0f;
        private float posX = 0f, posY = 0f;
        private float lastX, lastY;
        private int pointerId = MotionEvent.INVALID_POINTER_ID;

        ZoomTouchListener(ViewPager2 pager, ImageView iv) {
            this.pager = pager;
            this.iv = iv;

            // Детектор одиночного и двойного тапа
            this.gd = new GestureDetector(iv.getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    // Двойной тап переключает зум между 1.0x и 3.5x для глубокого детального обзора!
                    if (scale > 1.0f) {
                        reset();
                    } else {
                        scale = 3.5f;
                        posX = 0f;
                        posY = 0f;
                        apply();
                        // Замораживаем свайпы ViewPager2 при приближении
                        pager.setUserInputEnabled(false);
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    // Тап по экрану включает или скрывает панели управления!
                    toggleUiVisibility();
                    return true;
                }
            });
        }

        private void apply() {
            iv.setScaleX(scale);
            iv.setScaleY(scale);
            iv.setTranslationX(posX);
            iv.setTranslationY(posY);
        }

        public void reset() {
            scale = 1.0f; posX = 0f; posY = 0f;
            apply();
            pager.setUserInputEnabled(true);
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            gd.onTouchEvent(e);
            
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Запоминаем абсолютные координаты экрана (getRawX/getRawY), которые независимы 
                    // от матриц сдвигов самого ImageView. Это полностью убирает дрожание экрана!
                    lastX = e.getRawX();
                    lastY = e.getRawY();
                    pointerId = e.getPointerId(0);
                    break;
                    
                case MotionEvent.ACTION_MOVE:
                    // Панорамируем только если картинка приближена
                    if (scale > 1.0f) {
                        int idx = e.findPointerIndex(pointerId);
                        if (idx >= 0) {
                            float rawX = e.getRawX();
                            float rawY = e.getRawY();
                            
                            // Вычисляем дельту перемещения в абсолютном пространстве экрана
                            float deltaX = rawX - lastX;
                            float deltaY = rawY - lastY;
                            
                            // Применяем скоростной коэффициент 1.5x для моментального, отзывчивого перетаскивания
                            posX += deltaX * 1.5f;
                            posY += deltaY * 1.5f;
                            
                            // Ограничение границ: картинка не улетает за пределы видимости!
                            float maxMoveX = (v.getWidth() * (scale - 1.0f)) / 2.0f;
                            float maxMoveY = (v.getHeight() * (scale - 1.0f)) / 2.0f;
                            
                            posX = Math.max(-maxMoveX, Math.min(posX, maxMoveX));
                            posY = Math.max(-maxMoveY, Math.min(posY, maxMoveY));
                            
                            apply();
                            
                            lastX = rawX;
                            lastY = rawY;
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
    }

    private void showPlaylistDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_image_playlist, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = 
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                // Автоматическое раскрытие: если картинок больше 6 (то есть больше 2 полных рядов),
                // полностью разворачиваем шторку! Иначе она сама сожмется под размер картинок.
                if (imageAttachments.size() > 6) {
                    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                }
            }
        });

        View layoutSelectionHeader = view.findViewById(R.id.layout_selection_header);
        TextView tvPlaylistTitle = view.findViewById(R.id.tv_playlist_title);
        TextView tvSelectionCount = view.findViewById(R.id.tv_selection_count);
        View btnSelectAll = view.findViewById(R.id.btn_select_all);
        View btnDeleteSelected = view.findViewById(R.id.btn_delete_selected);

        RecyclerView rv = view.findViewById(R.id.rv_playlist);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        
        PlaylistGridAdapter adapter = new PlaylistGridAdapter(this, imageAttachments, position -> {
            dialog.dismiss();
            viewPager.setCurrentItem(position, true);
        });
        rv.setAdapter(adapter);
        rv.scrollToPosition(currentPosition);

        adapter.setSelectionListener(new PlaylistSelectionListener() {
            @Override
            public void onSelectionChanged() {
                if (adapter.isSelectionMode()) {
                    layoutSelectionHeader.setVisibility(View.VISIBLE);
                    tvPlaylistTitle.setVisibility(View.GONE);
                    tvSelectionCount.setText("Выбрано: " + adapter.getSelectedPositions().size());
                } else {
                    layoutSelectionHeader.setVisibility(View.GONE);
                    tvPlaylistTitle.setVisibility(View.VISIBLE);
                }
            }
        });

        btnSelectAll.setOnClickListener(v -> {
            HapticUtils.light(v);
            if (adapter.getSelectedPositions().size() == adapter.getItemCount()) {
                adapter.clearSelection();
            } else {
                adapter.selectAll();
            }
        });

        btnDeleteSelected.setOnClickListener(v -> {
            HapticUtils.medium(v);
            tkm.tmnote.pro.utils.ConfirmSheet.show(this, "Удалить файлы?", 
                "Выбранные изображения будут полностью удалены из заметки и памяти приложения.", 
                "Удалить", true, () -> {
                    List<Integer> selectedIndices = new java.util.ArrayList<>(adapter.getSelectedPositions());
                    java.util.Collections.sort(selectedIndices, java.util.Collections.reverseOrder());
                    
                    List<String> filesToDelete = new java.util.ArrayList<>();
                    List<Attachment> attachmentsToRemove = new java.util.ArrayList<>();
                    for (int index : selectedIndices) {
                        Attachment a = imageAttachments.get(index);
                        filesToDelete.add(a.fileName);
                        attachmentsToRemove.add(a);
                    }
                    
                    // 1. Удаляем физически
                    for (String fileName : filesToDelete) {
                        File f = AttachmentUtils.getFile(ImageViewerActivity.this, fileName);
                        if (f.exists()) f.delete();
                    }
                    
                    // 2. Удаляем из списка
                    for (Attachment a : attachmentsToRemove) {
                        imageAttachments.remove(a);
                    }
                    
                    // 3. Обновляем БД
                    if (noteId > 0) {
                        tkm.tmnote.pro.models.Note note = repo.getNoteById(noteId);
                        if (note != null) {
                            List<Attachment> currentAtts = note.getAttachments();
                            List<Attachment> updatedAtts = new java.util.ArrayList<>();
                            for (Attachment att : currentAtts) {
                                if (!filesToDelete.contains(att.fileName)) {
                                    updatedAtts.add(att);
                                }
                            }
                            note.setAttachments(updatedAtts);
                            repo.updateNote(note);
                        }
                    }
                    
                    // 4. Обновляем UI
                    pageAdapter.notifyDataSetChanged();
                    thumbAdapter.notifyDataSetChanged();
                    
                    if (imageAttachments.isEmpty()) {
                        dialog.dismiss();
                        closeActivity();
                    } else {
                        if (currentPosition >= imageAttachments.size()) {
                            currentPosition = imageAttachments.size() - 1;
                        }
                        viewPager.setCurrentItem(currentPosition, false);
                        Attachment active = imageAttachments.get(currentPosition);
                        tvName.setText(active.displayName == null ? active.fileName : active.displayName);
                        thumbAdapter.setActivePosition(currentPosition);
                        dialog.dismiss();
                    }
                });
        });

        view.findViewById(R.id.btn_close_playlist).setOnClickListener(v -> {
            HapticUtils.light(v);
            dialog.dismiss();
        });

        dialog.show();
    }

    private class PlaylistGridAdapter extends RecyclerView.Adapter<PlaylistGridAdapter.VH> {

        private final Context ctx;
        private final List<Attachment> items;
        private final OnItemClickListener clickListener;
        private final java.util.Set<Integer> selectedPositions = new java.util.HashSet<>();
        private boolean isSelectionMode = false;
        private PlaylistSelectionListener selectionListener;

        PlaylistGridAdapter(Context ctx, List<Attachment> items, OnItemClickListener clickListener) {
            this.ctx = ctx;
            this.items = items;
            this.clickListener = clickListener;
        }

        void setSelectionListener(PlaylistSelectionListener listener) {
            this.selectionListener = listener;
        }

        boolean isSelectionMode() { return isSelectionMode; }
        java.util.Set<Integer> getSelectedPositions() { return selectedPositions; }

        void toggleSelection(int pos) {
            if (selectedPositions.contains(pos)) {
                selectedPositions.remove(pos);
                if (selectedPositions.isEmpty()) isSelectionMode = false;
            } else {
                selectedPositions.add(pos);
            }
            notifyItemChanged(pos);
            if (selectionListener != null) selectionListener.onSelectionChanged();
        }

        void selectAll() {
            selectedPositions.clear();
            for (int i = 0; i < getItemCount(); i++) selectedPositions.add(i);
            isSelectionMode = true;
            notifyDataSetChanged();
            if (selectionListener != null) selectionListener.onSelectionChanged();
        }

        void clearSelection() {
            selectedPositions.clear();
            isSelectionMode = false;
            notifyDataSetChanged();
            if (selectionListener != null) selectionListener.onSelectionChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_playlist_grid_cell, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Attachment a = items.get(pos);
            File file = AttachmentUtils.getFile(ctx, a.fileName);

            Glide.with(h.ivThumb)
                    .load(file)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(h.ivThumb);

            h.ivPlayBadge.setVisibility(View.GONE);
            
            boolean isSelected = selectedPositions.contains(pos);
            h.ivThumb.setAlpha(isSelected ? 0.45f : 1.0f);
            h.borderSelected.setVisibility((isSelected || pos == currentPosition) ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(v -> {
                HapticUtils.light(v);
                if (isSelectionMode) {
                    toggleSelection(pos);
                } else {
                    if (clickListener != null) clickListener.onClick(pos);
                }
            });

            h.itemView.setOnLongClickListener(v -> {
                HapticUtils.medium(v);
                if (!isSelectionMode) {
                    isSelectionMode = true;
                    selectedPositions.add(pos);
                    notifyDataSetChanged();
                    if (selectionListener != null) selectionListener.onSelectionChanged();
                } else {
                    toggleSelection(pos);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView ivThumb;
            ImageView ivPlayBadge;
            View borderSelected;

            VH(@NonNull View v) {
                super(v);
                ivThumb = v.findViewById(R.id.iv_thumb);
                ivPlayBadge = v.findViewById(R.id.iv_play_badge);
                borderSelected = v.findViewById(R.id.v_border_selected);
            }
        }
    }
}
