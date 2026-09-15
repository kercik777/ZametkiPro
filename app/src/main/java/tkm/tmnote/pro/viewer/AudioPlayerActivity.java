package tkm.tmnote.pro.viewer;

import tkm.tmnote.pro.R;
import tkm.tmnote.pro.utils.ThemeHelper;
import tkm.tmnote.pro.utils.HapticUtils;
import tkm.tmnote.pro.utils.AttachmentUtils;
import tkm.tmnote.pro.models.Attachment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Продвинутый музыкальный медиаплеер премиум-класса для ваших заметок!
 * Особенности:
 * 1. Загружает всю музыку, принадлежащую текущей заметке (как галерея картинок).
 * 2. Вращающаяся виниловая пластинка с автоматическим отображением оригинальной обложки трека (Album Art / Обложки)!
 * 3. Режимы воспроизведения: Повтор (выкл, по кругу, повтор одного трека) и Случайный порядок (Shuffle).
 * 4. Всплывающее меню очереди воспроизведения (Bottom Sheet Playlist) с возможностью быстрого переключения.
 */
public class AudioPlayerActivity extends AppCompatActivity {

    // Интерфейс для клика по треку (объявлен как статический верхний член класса для совместимости с компилятором Sketchware)
    public interface OnItemClickListener {
        void onClick(int position);
    }

    public interface PlaylistSelectionListener {
        void onSelectionChanged();
    }

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private MediaPlayer player;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal, tvName, tvTrackIndex;
    private ImageView btnPlay, btnPrev, btnNext, btnShuffle, btnRepeat, btnPlaylist;
    
    private View layoutVinyl;
    private ImageView ivVinylIcon;

    private final List<Attachment> audioAttachments = new ArrayList<>();
    private final List<Integer> playOrder = new ArrayList<>(); // Хранит индексы в нужном порядке (для Shuffle)
    private int currentPosition = 0;
    private long noteId = 0L;
    private tkm.tmnote.pro.db.NotesRepository repo;
    private boolean playing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // Настройки плеера
    private boolean isShuffle = false;
    private int repeatMode = REPEAT_ALL;

    // Параметры анимаций вращения винила
    private float vinylRotation = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        tkm.tmnote.pro.utils.SystemBarsHelper.apply(this);

        // Получаем список аудиофайлов — через Parcelable
        ArrayList<Attachment> list = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                list = getIntent().getParcelableArrayListExtra("audios", Attachment.class);
            } else {
                list = getIntent().getParcelableArrayListExtra("audios");
            }
        } catch (Exception e) {
            list = null;
        }
        currentPosition = getIntent().getIntExtra("index", 0);
        noteId = getIntent().getLongExtra("note_id", 0L);
        repo = new tkm.tmnote.pro.db.NotesRepository(this);

        if (list != null) {
            audioAttachments.addAll(list);
        }

        // Поддержка обратной совместимости при открытии одной аудиозаписи
        if (audioAttachments.isEmpty()) {
            String path = getIntent().getStringExtra("path");
            String name = getIntent().getStringExtra("name");
            if (path != null) {
                File file = new File(path);
                Attachment fallback = new Attachment(Attachment.TYPE_AUDIO, file.getName(), name, file.length());
                audioAttachments.add(fallback);
            }
        }

        initUi();
        setupListeners();
        buildPlayOrder();

        // Запускаем первый трек
        loadAndPlayTrack(currentPosition, true);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeActivity();
            }
        });
    }

    private void initUi() {
        seekBar = findViewById(R.id.seek_bar);
        tvCurrent = findViewById(R.id.tv_current);
        tvTotal = findViewById(R.id.tv_total);
        tvName = findViewById(R.id.tv_name);
        tvTrackIndex = findViewById(R.id.tv_track_index);
        
        btnPlay = findViewById(R.id.btn_play);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnPlaylist = findViewById(R.id.btn_playlist);

        layoutVinyl = findViewById(R.id.layout_vinyl);
        ivVinylIcon = findViewById(R.id.iv_vinyl_icon);

        // Устанавливаем стартовую кастомную иконку ноты
        ivVinylIcon.setImageResource(R.drawable.ic_player_note);

        // Скрываем ненужные кнопки переключения, если музыка всего одна
        if (audioAttachments.size() <= 1) {
            btnPrev.setVisibility(View.INVISIBLE);
            btnNext.setVisibility(View.INVISIBLE);
            btnShuffle.setVisibility(View.GONE);
            btnRepeat.setVisibility(View.GONE);
            btnPlaylist.setVisibility(View.GONE);
            tvTrackIndex.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            HapticUtils.light(v);
            closeActivity();
        });

        btnPlay.setOnClickListener(v -> {
            HapticUtils.medium(v);
            togglePlay();
        });

        btnPrev.setOnClickListener(v -> {
            HapticUtils.light(v);
            playPrevious();
        });

        btnNext.setOnClickListener(v -> {
            HapticUtils.light(v);
            playNext(false);
        });

        btnShuffle.setOnClickListener(v -> {
            HapticUtils.light(v);
            toggleShuffle();
        });

        btnRepeat.setOnClickListener(v -> {
            HapticUtils.light(v);
            toggleRepeat();
        });

        // ИСПРАВЛЕНИЕ: Возвращаем клик на кнопку плейлиста!
        btnPlaylist.setOnClickListener(v -> {
            HapticUtils.light(v);
            showPlaylistDialog();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    player.seekTo(progress);
                    tvCurrent.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Жесты свайпа для переключения треков с мгновенной (моментальной) реакцией и большой областью свайпа
        float density = getResources().getDisplayMetrics().density;
        float threshold = 55 * density; // Порог в 55dp для моментального отклика

        android.view.View.OnTouchListener swipeListener = new android.view.View.OnTouchListener() {
            private float startX = 0f;
            private float startY = 0f;
            private boolean isSwiped = false;

            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        isSwiped = false;
                        return true; // Возвращаем true, чтобы ловить события перемещения пальца
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (isSwiped) return true;
                        float diffX = event.getX() - startX;
                        float diffY = event.getY() - startY;
                        if (Math.abs(diffX) > Math.abs(diffY)) {
                            if (Math.abs(diffX) > threshold) {
                                if (diffX > 0) {
                                    // Свайп вправо -> Предыдущий трек
                                    playPrevious();
                                } else {
                                    // Свайп влево -> Следующий трек
                                    playNext(false);
                                }
                                isSwiped = true;
                            }
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        isSwiped = false;
                        return true;
                }
                return false;
            }
        };

        // Назначаем этот свайп-слушатель везде: на весь экран, на виниловый диск и на иконку внутри него
        android.view.View rootBg = findViewById(R.id.root);
        if (rootBg != null) {
            rootBg.setOnTouchListener(swipeListener);
        }
        if (layoutVinyl != null) {
            layoutVinyl.setOnTouchListener(swipeListener);
        }
        if (ivVinylIcon != null) {
            ivVinylIcon.setOnTouchListener(swipeListener);
        }
    }

    private void buildPlayOrder() {
        playOrder.clear();
        for (int i = 0; i < audioAttachments.size(); i++) {
            playOrder.add(i);
        }
        if (isShuffle) {
            Collections.shuffle(playOrder);
        }
    }

    /**
     * Извлекает оригинальную обложку (альбом арт) из музыкального файла в формате MP3/M4A.
     */
    private byte[] getEmbeddedAlbumArt(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            return retriever.getEmbeddedPicture();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loadAndPlayTrack(int position, boolean startPlaying) {
        if (audioAttachments.isEmpty()) return;
        currentPosition = position;

        // Останавливаем старый плеер
        if (player != null) {
            player.release();
            player = null;
        }

        Attachment track = audioAttachments.get(position);
        File file = AttachmentUtils.getFile(this, track.fileName);

        tvName.setText(track.displayName == null ? track.fileName : track.displayName);
        tvTrackIndex.setText(String.format(Locale.getDefault(), "Трек %d из %d", (position + 1), audioAttachments.size()));

        // УМНОЕ ОТОБРАЖЕНИЕ ОБЛОЖКИ (ALBUM ART / SURPRISE FEATURE):
        // Пытаемся извлечь обложку трека. Если нашли — Glide делает её круглой и размещает на виниле!
        byte[] albumArt = getEmbeddedAlbumArt(file.getAbsolutePath());
        if (albumArt != null) {
            // Расширяем размеры иконки на весь центр пластинки для обложки
            ViewGroup.LayoutParams lp = ivVinylIcon.getLayoutParams();
            lp.width = dpToPx(130);
            lp.height = dpToPx(130);
            ivVinylIcon.setLayoutParams(lp);

            // ИСПРАВЛЕНИЕ: Обязательно убираем TintList перед загрузкой оригинальной обложки,
            // чтобы убрать зеленый оверлей-фильтр на картинке!
            ivVinylIcon.setImageTintList(null);

            Glide.with(this)
                    .load(albumArt)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop() // Идеальное центрированное скругление
                    .error(R.drawable.ic_player_note)
                    .into(ivVinylIcon);
        } else {
            // Если обложки нет — возвращаем красивую кастомную золотую ноту и восстанавливаем её золотой Tint!
            Glide.with(this).clear(ivVinylIcon);
            ivVinylIcon.setImageResource(R.drawable.ic_player_note);
            ivVinylIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.gold_primary)));

            ViewGroup.LayoutParams lp = ivVinylIcon.getLayoutParams();
            lp.width = dpToPx(90);
            lp.height = dpToPx(90);
            ivVinylIcon.setLayoutParams(lp);
        }

        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            seekBar.setMax(player.getDuration());
            tvTotal.setText(formatTime(player.getDuration()));
            tvCurrent.setText("00:00");
            seekBar.setProgress(0);

            player.setOnCompletionListener(mp -> {
                // Логика завершения трека в зависимости от режима повтора
                if (repeatMode == REPEAT_ONE) {
                    loadAndPlayTrack(currentPosition, true);
                } else {
                    playNext(true); // Автоматический переход
                }
            });

            if (startPlaying) {
                player.start();
                playing = true;
                btnPlay.setImageResource(R.drawable.ic_pause);
                startVisualizations();
            } else {
                playing = false;
                btnPlay.setImageResource(R.drawable.ic_play);
                stopVisualizations();
            }

        } catch (Exception e) {
            tvName.setText("Ошибка файла: " + e.getLocalizedMessage());
        }
    }

    private void togglePlay() {
        if (player == null) return;
        if (playing) {
            player.pause();
            playing = false;
            btnPlay.setImageResource(R.drawable.ic_play);
            stopVisualizations();
        } else {
            player.start();
            playing = true;
            btnPlay.setImageResource(R.drawable.ic_pause);
            startVisualizations();
        }
    }

    private void playNext(boolean isAutoCompletion) {
        if (audioAttachments.size() <= 1) {
            if (repeatMode == REPEAT_ALL) {
                loadAndPlayTrack(currentPosition, true);
            } else {
                reset();
            }
            return;
        }

        int indexInOrder = playOrder.indexOf(currentPosition);
        if (indexInOrder < 0) {
            loadAndPlayTrack(0, true);
            return;
        }

        if (indexInOrder < playOrder.size() - 1) {
            int nextTrack = playOrder.get(indexInOrder + 1);
            loadAndPlayTrack(nextTrack, true);
        } else {
            if (repeatMode == REPEAT_ALL || !isAutoCompletion) {
                int nextTrack = playOrder.get(0);
                loadAndPlayTrack(nextTrack, true);
            } else {
                playing = false;
                btnPlay.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(player.getDuration());
                tvCurrent.setText(formatTime(player.getDuration()));
                stopVisualizations();
            }
        }
    }

    private void playPrevious() {
        if (audioAttachments.size() <= 1) {
            loadAndPlayTrack(currentPosition, true);
            return;
        }

        int indexInOrder = playOrder.indexOf(currentPosition);
        if (indexInOrder <= 0) {
            if (repeatMode == REPEAT_ALL) {
                int lastTrack = playOrder.get(playOrder.size() - 1);
                loadAndPlayTrack(lastTrack, true);
            } else {
                loadAndPlayTrack(currentPosition, true); // Перезапускаем текущий
            }
        } else {
            int prevTrack = playOrder.get(indexInOrder - 1);
            loadAndPlayTrack(prevTrack, true);
        }
    }

    private void toggleShuffle() {
        isShuffle = !isShuffle;
        btnShuffle.setColorFilter(getResources().getColor(isShuffle ? R.color.gold_primary : R.color.text_tertiary));
        buildPlayOrder();
        Toast.makeText(this, isShuffle ? "Случайный порядок включен" : "По порядку", Toast.LENGTH_SHORT).show();
    }

    private void toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        switch (repeatMode) {
            case REPEAT_OFF:
                // ИСПРАВЛЕНИЕ: Используем правильную иконку и цвет
                btnRepeat.setImageResource(R.drawable.ic_player_repeat);
                btnRepeat.setColorFilter(getResources().getColor(R.color.text_tertiary));
                Toast.makeText(this, "Повтор отключен", Toast.LENGTH_SHORT).show();
                break;
            case REPEAT_ALL:
                btnRepeat.setImageResource(R.drawable.ic_player_repeat);
                btnRepeat.setColorFilter(getResources().getColor(R.color.gold_primary));
                Toast.makeText(this, "Повторять все треки заметки", Toast.LENGTH_SHORT).show();
                break;
            case REPEAT_ONE:
                btnRepeat.setImageResource(R.drawable.ic_player_repeat);
                btnRepeat.setColorFilter(getResources().getColor(R.color.cat_yellow)); // Другой оттенок для одного
                Toast.makeText(this, "Повторять текущий трек", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // ===== Анимации и визуализации винила =====
    private void startVisualizations() {
        handler.removeCallbacksAndMessages(null);
        handler.post(tickSeekRunnable);
    }

    private void stopVisualizations() {
        handler.removeCallbacksAndMessages(null);
        if (ivVinylIcon != null) {
            ivVinylIcon.setScaleX(1.0f);
            ivVinylIcon.setScaleY(1.0f);
        }
    }

    private final Runnable tickSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null || !playing) return;

            // 1. Обновляем SeekBar и текстовое время
            int pos = player.getCurrentPosition();
            seekBar.setProgress(pos);
            tvCurrent.setText(formatTime(pos));

            // 2. ВРАЩАЕМ ВИНИЛ: увеличиваем угол вращения диска
            vinylRotation += 1.5f;
            if (vinylRotation >= 360f) {
                vinylRotation -= 360f;
            }
            if (layoutVinyl != null) {
                layoutVinyl.setRotation(vinylRotation);
            }

            // 3. ПУЛЬСИРУЮЩИЙ СИНУС (Эквалайзер / Вау-эффект):
            // Мягко сужаем и расширяем центральный значок в такт воображаемой волны
            float scale = 1.0f + 0.05f * (float) Math.sin(System.currentTimeMillis() / 120.0);
            if (ivVinylIcon != null) {
                ivVinylIcon.setScaleX(scale);
                ivVinylIcon.setScaleY(scale);
            }

            // Запускаем следующий цикл через 30 мс для идеальных 60 FPS анимаций!
            handler.postDelayed(this, 30);
        }
    };

    /**
     * Отображение списка воспроизведения с автоматической регулировкой высоты шторки по количеству треков!
     */
    private void showPlaylistDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_audio_playlist, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = 
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                if (audioAttachments.size() > 5) {
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
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        PlaylistAdapter playlistAdapter = new PlaylistAdapter(this, audioAttachments, position -> {
            HapticUtils.light(view);
            dialog.dismiss();
            loadAndPlayTrack(position, true);
        });
        rv.setAdapter(playlistAdapter);
        rv.scrollToPosition(currentPosition);

        playlistAdapter.setSelectionListener(new PlaylistSelectionListener() {
            @Override
            public void onSelectionChanged() {
                if (playlistAdapter.isSelectionMode()) {
                    layoutSelectionHeader.setVisibility(View.VISIBLE);
                    tvPlaylistTitle.setVisibility(View.GONE);
                    tvSelectionCount.setText("Выбрано: " + playlistAdapter.getSelectedPositions().size());
                } else {
                    layoutSelectionHeader.setVisibility(View.GONE);
                    tvPlaylistTitle.setVisibility(View.VISIBLE);
                }
            }
        });

        btnSelectAll.setOnClickListener(v -> {
            HapticUtils.light(v);
            if (playlistAdapter.getSelectedPositions().size() == playlistAdapter.getItemCount()) {
                playlistAdapter.clearSelection();
            } else {
                playlistAdapter.selectAll();
            }
        });

        btnDeleteSelected.setOnClickListener(v -> {
            HapticUtils.medium(v);
            tkm.tmnote.pro.utils.ConfirmSheet.show(this, "Удалить файлы?", 
                "Выбранные аудиозаписи будут полностью удалены из заметки и памяти приложения.", 
                "Удалить", true, () -> {
                    List<Integer> selectedIndices = new java.util.ArrayList<>(playlistAdapter.getSelectedPositions());
                    java.util.Collections.sort(selectedIndices, java.util.Collections.reverseOrder());
                    
                    List<String> filesToDelete = new java.util.ArrayList<>();
                    List<Attachment> attachmentsToRemove = new java.util.ArrayList<>();
                    for (int index : selectedIndices) {
                        Attachment a = audioAttachments.get(index);
                        filesToDelete.add(a.fileName);
                        attachmentsToRemove.add(a);
                    }
                    
                    // 1. Удаляем физически
                    for (String fileName : filesToDelete) {
                        File f = AttachmentUtils.getFile(AudioPlayerActivity.this, fileName);
                        if (f.exists()) f.delete();
                    }
                    
                    // 2. Удаляем из списка
                    for (Attachment a : attachmentsToRemove) {
                        audioAttachments.remove(a);
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
                    buildPlayOrder();
                    
                    if (audioAttachments.isEmpty()) {
                        dialog.dismiss();
                        closeActivity();
                    } else {
                        if (currentPosition >= audioAttachments.size()) {
                            currentPosition = audioAttachments.size() - 1;
                        }
                        loadAndPlayTrack(currentPosition, true);
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

    private void reset() {
        playing = false;
        btnPlay.setImageResource(R.drawable.ic_play);
        if (player != null) {
            player.seekTo(0);
        }
        seekBar.setProgress(0);
        tvCurrent.setText("00:00");
        stopVisualizations();
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        int sec = s % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, sec);
    }

    private void closeActivity() {
        stopVisualizations();
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        closeActivity();
        super.onDestroy();
    }

    // ===== Адаптер для вывода треков в Плейлисте (Bottom Sheet) =====
    private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {

        private final Context ctx;
        private final List<Attachment> items;
        private final OnItemClickListener clickListener;
        private final java.util.Set<Integer> selectedPositions = new java.util.HashSet<>();
        private boolean isSelectionMode = false;
        private PlaylistSelectionListener selectionListener;

        PlaylistAdapter(Context ctx, List<Attachment> items, OnItemClickListener clickListener) {
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
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_audio_track, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Attachment a = items.get(pos);
            h.tvTitle.setText(a.displayName == null ? a.fileName : a.displayName);
            h.tvSize.setText(AttachmentUtils.formatSize(a.size));

            boolean isPlayingThis = (pos == currentPosition);
            boolean isSelected = selectedPositions.contains(pos);

            h.itemView.setBackgroundResource(isSelected 
                    ? R.drawable.bg_drawer_item_selected 
                    : R.drawable.bg_ripple_rounded);

            if (isSelected) {
                h.ivPlayingIndicator.setVisibility(View.VISIBLE);
                h.ivPlayingIndicator.setImageResource(R.drawable.ic_check);
                h.ivPlayingIndicator.setColorFilter(androidx.core.content.ContextCompat.getColor(ctx, R.color.gold_primary));
                if (h.playingEqualizer != null) {
                    h.playingEqualizer.stop();
                    h.playingEqualizer = null;
                }
            } else {
                h.ivPlayingIndicator.setColorFilter(androidx.core.content.ContextCompat.getColor(ctx, R.color.gold_primary));
                if (isPlayingThis) {
                    h.ivPlayingIndicator.setVisibility(View.VISIBLE);
                    if (h.playingEqualizer == null) {
                        int goldColor = androidx.core.content.ContextCompat.getColor(ctx, R.color.gold_primary);
                        h.playingEqualizer = new EqualizerDrawable(goldColor);
                        h.ivPlayingIndicator.setImageDrawable(h.playingEqualizer);
                        h.playingEqualizer.start();
                    } else {
                        h.playingEqualizer.start();
                    }
                } else {
                    h.ivPlayingIndicator.setVisibility(View.GONE);
                    if (h.playingEqualizer != null) {
                        h.playingEqualizer.stop();
                        h.playingEqualizer = null;
                    }
                }
            }

            File file = AttachmentUtils.getFile(ctx, a.fileName);

            // Пытаемся извлечь обложку трека для плейлиста
            byte[] albumArt = getEmbeddedAlbumArt(file.getAbsolutePath());
            if (albumArt != null) {
                h.ivAudioIcon.setPadding(0, 0, 0, 0);
                h.ivAudioIcon.setImageTintList(null);
                Glide.with(ctx)
                        .asBitmap()
                        .load(albumArt)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(h.ivAudioIcon);
            } else {
                Glide.with(ctx).clear(h.ivAudioIcon);
                h.ivAudioIcon.setImageResource(R.drawable.ic_player_note);
                h.ivAudioIcon.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                h.ivAudioIcon.setImageTintList(ColorStateList.valueOf(ctx.getResources().getColor(
                        isPlayingThis ? R.color.gold_primary : R.color.icon_accent
                )));
            }

            h.itemView.setAlpha(isSelected ? 0.85f : 1.0f);

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

        @Override
        public void onViewRecycled(@NonNull VH h) {
            super.onViewRecycled(h);
            if (h.playingEqualizer != null) {
                h.playingEqualizer.stop();
                h.playingEqualizer = null;
            }
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSize;
            ImageView ivPlayingIndicator, ivAudioIcon;
            EqualizerDrawable playingEqualizer;

            VH(@NonNull View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_track_title);
                tvSize = v.findViewById(R.id.tv_track_size);
                ivPlayingIndicator = v.findViewById(R.id.iv_playing_indicator);
                ivAudioIcon = v.findViewById(R.id.iv_audio_icon);
            }
        }
    }

    public static class EqualizerDrawable extends android.graphics.drawable.Drawable implements Runnable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int barColor;
        private boolean running = false;

        public EqualizerDrawable(int color) {
            this.barColor = color;
            this.paint.setColor(color);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float w = bounds.width();
            float h = bounds.height();
            if (w <= 0 || h <= 0) return;

            float density = w / 24f;
            float gap = 3f * density;
            float barW = 4f * density;
            float startX = (w - (3f * barW + 2f * gap)) / 2f;

            long time = android.os.SystemClock.uptimeMillis();
            
            float h1 = 0.25f + 0.75f * (float) Math.abs(Math.sin(time / 160.0));
            float h2 = 0.25f + 0.75f * (float) Math.abs(Math.sin(time / 100.0 + 1.5));
            float h3 = 0.25f + 0.75f * (float) Math.abs(Math.sin(time / 220.0 + 3.0));

            drawBar(canvas, startX, h1, h, barW);
            drawBar(canvas, startX + barW + gap, h2, h, barW);
            drawBar(canvas, startX + 2f * (barW + gap), h3, h, barW);

            if (running) {
                nextFrame();
            }
        }

        private void drawBar(android.graphics.Canvas canvas, float left, float heightFraction, float totalH, float barW) {
            float barH = totalH * heightFraction;
            float top = totalH - barH;
            float right = left + barW;
            float bottom = totalH;
            float radius = barW / 2f;
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
        }

        public void start() {
            if (running) return;
            running = true;
            nextFrame();
        }

        public void stop() {
            if (!running) return;
            running = false;
            unscheduleSelf(this);
        }

        private void nextFrame() {
            scheduleSelf(this, android.os.SystemClock.uptimeMillis() + 16);
        }

        @Override
        public void run() {
            invalidateSelf();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
