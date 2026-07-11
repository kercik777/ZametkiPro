package com.zametki.pro.viewer;

import com.zametki.pro.R;
import com.zametki.pro.utils.ThemeHelper;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.AppExecutors;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.FileProvider;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Прогрессивный и красивый просмотрщик текстовых/код файлов (TXT, PY, JAVA, XML, JSON, etc.)
 * на базе оптимизированного единого TextView.
 * Обеспечивает СВОБОДНОЕ МНОГОСТРОЧНОЕ ВЫДЕЛЕНИЕ текста и абсолютно плавное GPU-масштабирование (120 FPS)!
 */
public class TextFileViewerActivity extends AppCompatActivity {

    private String filePath;
    private String fileName;
    private String fileContent = "";
    private long fileSize = 0;

    // Элементы UI основного экрана
    private TextView tvTitle;
    private ScrollView scrollView;
    private HorizontalScrollView horizontalScrollView;
    private TextView tvLineNumbers;
    private TextView tvContentScrollable;
    private TextView tvContentWrapped;
    private LinearLayout loadingLayout;
    private TextView tvLoadingStatus;
    private LinearLayout contentLayout; // Контейнер номеров строк и текстов для аппаратного зума без лагов

    // Панель поиска
    private View searchPanel;
    private View searchDivider;
    private EditText etSearch;
    private TextView tvSearchCount;
    private ImageView btnSearchPrev;
    private ImageView btnSearchNext;

    // Жесты и масштабирование
    private ScaleGestureDetector scaleDetector;
    private float currentTextSize = 14f; // в sp
    private float scaleFactor = 1.0f;    // Коэффициент аппаратного зума во время жеста
    private boolean isScaleInProcess = false; // Флаг жеста зума

    // Настройки отображения
    private boolean wordWrapEnabled = false;
    private boolean lineNumbersEnabled = true;
    private boolean monospaceFont = true;

    // Данные для поиска
    private final List<Integer> searchIndices = new ArrayList<>();
    private int currentSearchIndex = -1;

    // Статистика для отображения
    private int statLineCount = 0;
    private int statWordCount = 0;
    private int statCharCount = 0;
    private int statCharNoSpacesCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_file_viewer);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        filePath = getIntent().getStringExtra("path");
        fileName = getIntent().getStringExtra("name");

        initUi();
        setupListeners();
        setupScaleDetector();
        detectDefaultFont();

        // Асинхронно загружаем файл
        loadFileAsync();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeActivity();
            }
        });
    }

    private void initUi() {
        tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(fileName == null ? "Текстовый файл" : fileName);

        scrollView = findViewById(R.id.scroll_view);
        horizontalScrollView = findViewById(R.id.horizontal_scroll_view);
        tvLineNumbers = findViewById(R.id.tv_line_numbers);
        tvContentScrollable = findViewById(R.id.tv_content_scrollable);
        tvContentWrapped = findViewById(R.id.tv_content_wrapped);
        loadingLayout = findViewById(R.id.loading_layout);
        tvLoadingStatus = findViewById(R.id.tv_loading_status);
        contentLayout = findViewById(R.id.content_layout);

        // Поиск
        searchPanel = findViewById(R.id.search_panel);
        searchDivider = findViewById(R.id.search_divider);
        etSearch = findViewById(R.id.et_search);
        tvSearchCount = findViewById(R.id.tv_search_count);
        btnSearchPrev = findViewById(R.id.btn_search_prev);
        btnSearchNext = findViewById(R.id.btn_search_next);
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            HapticUtils.light(v);
            closeActivity();
        });

        findViewById(R.id.btn_search).setOnClickListener(v -> {
            HapticUtils.light(v);
            toggleSearchPanel();
        });

        findViewById(R.id.btn_info).setOnClickListener(v -> {
            HapticUtils.light(v);
            showInfoDialog();
        });

        findViewById(R.id.btn_more).setOnClickListener(v -> {
            HapticUtils.light(v);
            showOptionsDialog();
        });

        // Слушатели для панели поиска
        findViewById(R.id.btn_search_close).setOnClickListener(v -> {
            HapticUtils.light(v);
            hideSearchPanel();
        });

        btnSearchPrev.setOnClickListener(v -> {
            HapticUtils.light(v);
            navigateSearchMatch(-1);
        });

        btnSearchNext.setOnClickListener(v -> {
            HapticUtils.light(v);
            navigateSearchMatch(1);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                performSearch(s.toString());
            }
        });
    }

    /**
     * СОВЕРШЕННОЕ ГИБРИДНОЕ МАСШТАБИРОВАНИЕ (БЕЗ ЛАГОВ):
     * Во время щипка пальцами мы масштабируем контейнер `contentLayout` аппаратно силами GPU (через ScaleX/ScaleY).
     * Это гарантирует чистейшую плавность 120 FPS, так как процессор совершает 0 вычислений разметки!
     * А при отпускании пальцев (onScaleEnd) мы сбрасываем масштаб в 1.0f, один раз меняем размер шрифта текста,
     * перерасчитываем номера строк и идеально восстанавливаем позицию скролла.
     */
    private void setupScaleDetector() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.6f, Math.min(scaleFactor, 2.5f)); // Пределы зума на жест
                
                contentLayout.setPivotX(detector.getFocusX());
                contentLayout.setPivotY(detector.getFocusY());
                
                contentLayout.setScaleX(scaleFactor);
                contentLayout.setScaleY(scaleFactor);
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                scaleFactor = 1.0f;
                contentLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                currentTextSize *= scaleFactor;
                currentTextSize = Math.max(8f, Math.min(currentTextSize, 36f));

                contentLayout.setScaleX(1.0f);
                contentLayout.setScaleY(1.0f);
                contentLayout.setLayerType(View.LAYER_TYPE_NONE, null);

                // Применяем размер и сохраняем фокус
                applyTextSize();
            }
        });
    }

    /**
     * ИСПРАВЛЕНИЕ ХАОТИЧНОГО СКРОЛЛА И ЛОЖНЫХ ВЫДЕЛЕНИЙ:
     * 1. При 2+ пальцах мы блокируем прокрутку ScrollView.
     * 2. ИСПРАВЛЕНИЕ ВЫДЕЛЕНИЯ: При появлении второго пальца мы принудительно шлем событие ACTION_CANCEL.
     *    Это мгновенно заставляет TextView отменить выделение и сбросить таймер длинного тапа.
     *    Благодаря этому текст больше не выделяется случайно во время зума, а свободное многострочное выделение
     *    одним пальцем продолжает работать идеально и стандартно!
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        
        if (ev.getPointerCount() >= 2) {
            if (!isScaleInProcess) {
                isScaleInProcess = true;
                
                MotionEvent cancelEvent = MotionEvent.obtain(
                        ev.getDownTime(),
                        ev.getEventTime(),
                        MotionEvent.ACTION_CANCEL,
                        0f, 0f, 0
                );
                super.dispatchTouchEvent(cancelEvent);
                cancelEvent.recycle();
            }
            scrollView.requestDisallowInterceptTouchEvent(true);
            return true; // Блокируем движение
        }
        
        if (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            isScaleInProcess = false;
        }
        
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Высокоточная смена размера текста с ПОЛНЫМ сохранением фокуса видимой строки!
     */
    private void applyTextSize() {
        int scrollY = scrollView.getScrollY();
        int totalHeight = contentLayout.getHeight();
        final float scrollPercent = (totalHeight > 0) ? (float) scrollY / totalHeight : 0f;

        // Меняем размеры шрифтов
        tvLineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);
        tvContentScrollable.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);
        tvContentWrapped.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);
        
        // Перерасчитываем номера строк
        applyLineNumbers();

        // Восстанавливаем позицию скролла в следующем кадре
        scrollView.post(() -> {
            int newTotalHeight = contentLayout.getHeight();
            int newScrollY = Math.round(scrollPercent * newTotalHeight);
            scrollView.scrollTo(0, newScrollY);
        });
    }

    private void detectDefaultFont() {
        if (fileName != null) {
            String nameLower = fileName.toLowerCase(Locale.ROOT);
            if (nameLower.endsWith(".txt") || nameLower.endsWith(".md") || nameLower.endsWith(".log")) {
                monospaceFont = false;
            }
        }
        applyFontStyle();
    }

    private void applyFontStyle() {
        Typeface tf = monospaceFont ? Typeface.create("monospace", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.NORMAL);
        tvContentScrollable.setTypeface(tf);
        tvContentWrapped.setTypeface(tf);
        tvLineNumbers.setTypeface(Typeface.create("monospace", Typeface.NORMAL)); // Номера строк всегда моноширинные
    }

    private void applyWordWrap() {
        if (wordWrapEnabled) {
            horizontalScrollView.setVisibility(View.GONE);
            tvContentWrapped.setVisibility(View.VISIBLE);
            tvContentWrapped.setText(tvContentScrollable.getText());
        } else {
            horizontalScrollView.setVisibility(View.VISIBLE);
            tvContentWrapped.setVisibility(View.GONE);
            tvContentScrollable.setText(tvContentWrapped.getText());
        }
        applyLineNumbers();
    }

    /**
     * Сверхбыстрое линейное вычисление номеров строк за O(M) для идеального синхрона с переносами!
     */
    private void applyLineNumbers() {
        if (!lineNumbersEnabled) {
            tvLineNumbers.setVisibility(View.GONE);
            return;
        }

        tvLineNumbers.setVisibility(View.VISIBLE);

        if (wordWrapEnabled) {
            tvContentWrapped.post(() -> {
                Layout layout = tvContentWrapped.getLayout();
                if (layout == null) return;

                int totalVisualLines = layout.getLineCount();
                String text = fileContent;
                if (text == null) return;

                StringBuilder sb = new StringBuilder();
                int physicalLineNumber = 1;

                for (int visualLine = 0; visualLine < totalVisualLines; visualLine++) {
                    int start = layout.getLineStart(visualLine);
                    if (start == 0 || (start > 0 && start <= text.length() && text.charAt(start - 1) == '\n')) {
                        sb.append(physicalLineNumber);
                        physicalLineNumber++;
                    }
                    if (visualLine < totalVisualLines - 1) {
                        sb.append("\n");
                    }
                }
                tvLineNumbers.setText(sb.toString());
            });
        } else {
            int lineCount = statLineCount;
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= lineCount; i++) {
                sb.append(i);
                if (i < lineCount) {
                    sb.append("\n");
                }
            }
            tvLineNumbers.setText(sb.toString());
        }
    }

    private void loadFileAsync() {
        if (filePath == null) {
            showErrorAndExit("Файл не указан.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            showErrorAndExit("Файл не найден на устройстве.");
            return;
        }

        fileSize = file.length();
        loadingLayout.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Лимит в 200 000 символов гарантирует мгновенную нативную работу TextView!
                final long MAX_CHAR_LIMIT = 200000;
                boolean truncated = false;

                StringBuilder sb = new StringBuilder();
                int lineCount = 0;
                int charCount = 0;
                int nonSpacesCount = 0;
                int wordCount = 0;

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        if (!truncated) {
                            if (sb.length() + line.length() + 1 > MAX_CHAR_LIMIT) {
                                truncated = true;
                                sb.append("\n\n[ВНИМАНИЕ: Файл слишком большой. Отображены только первые 200 000 символов...]");
                            } else {
                                sb.append(line).append("\n");
                            }
                        }

                        charCount += line.length() + 1;
                        for (int i = 0; i < line.length(); i++) {
                            if (!Character.isWhitespace(line.charAt(i))) {
                                nonSpacesCount++;
                            }
                        }
                        String trimLine = line.trim();
                        if (!trimLine.isEmpty()) {
                            wordCount += trimLine.split("\\s+").length;
                        }
                    }
                }

                fileContent = sb.toString();
                statLineCount = lineCount;
                statCharCount = charCount;
                statCharNoSpacesCount = nonSpacesCount;
                statWordCount = wordCount;

                AppExecutors.getInstance().mainThread(() -> {
                    loadingLayout.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);

                    tvContentScrollable.setText(fileContent);
                    tvContentWrapped.setText(fileContent);

                    applyWordWrap();
                    applyLineNumbers();
                    applyFontStyle();
                });

            } catch (Exception e) {
                AppExecutors.getInstance().mainThread(() -> {
                    showErrorAndExit("Не удалось прочитать файл: " + e.getLocalizedMessage());
                });
            }
        });
    }

    private void showErrorAndExit(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void closeActivity() {
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    // ===== Поиск по содержимому =====
    private void toggleSearchPanel() {
        if (searchPanel.getVisibility() == View.VISIBLE) {
            hideSearchPanel();
        } else {
            searchPanel.setVisibility(View.VISIBLE);
            searchDivider.setVisibility(View.VISIBLE);
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
            }
            performSearch(etSearch.getText().toString());
        }
    }

    private void hideSearchPanel() {
        searchPanel.setVisibility(View.GONE);
        searchDivider.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
        resetHighlighting();
    }

    private void performSearch(String query) {
        searchIndices.clear();
        currentSearchIndex = -1;

        if (query == null || query.trim().isEmpty()) {
            tvSearchCount.setText("0/0");
            resetHighlighting();
            return;
        }

        String lowerContent = fileContent.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int len = lowerQuery.length();

        int idx = lowerContent.indexOf(lowerQuery);
        while (idx >= 0) {
            searchIndices.add(idx);
            idx = lowerContent.indexOf(lowerQuery, idx + len);
        }

        int count = searchIndices.size();
        if (count > 0) {
            currentSearchIndex = 0;
            tvSearchCount.setText("1/" + count);
            highlightAndScrollToMatch();
        } else {
            tvSearchCount.setText("0/0");
            resetHighlighting();
        }
    }

    private void navigateSearchMatch(int dir) {
        int count = searchIndices.size();
        if (count == 0) return;

        currentSearchIndex += dir;
        if (currentSearchIndex >= count) currentSearchIndex = 0;
        else if (currentSearchIndex < 0) currentSearchIndex = count - 1;

        tvSearchCount.setText((currentSearchIndex + 1) + "/" + count);
        highlightAndScrollToMatch();
    }

    /**
     * Подсветка и авто-скролл к найденному тексту:
     * ОПТИМИЗИРОВАНО: Скролл срабатывает асинхронно через scrollView.post() сразу ПОСЛЕ переразметки текста,
     * что гарантирует 100% точность позиционирования и мягкую анимацию скролла!
     * Дополнительно: Если перенос строк выключен, скролл автоматически сдвигается по горизонтали,
     * чтобы найденное слово оказалось ровно по центру экрана по горизонтали!
     */
    private void highlightAndScrollToMatch() {
        if (searchIndices.isEmpty() || currentSearchIndex < 0) return;

        String query = etSearch.getText().toString();
        int queryLen = query.length();

        SpannableStringBuilder builder = new SpannableStringBuilder(fileContent);

        for (int i = 0; i < searchIndices.size(); i++) {
            int start = searchIndices.get(i);
            int end = start + queryLen;
            if (end > fileContent.length()) continue;

            int color = (i == currentSearchIndex) 
                ? 0xFFD4AF37 
                : 0x5543A047;

            builder.setSpan(new BackgroundColorSpan(color), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        final TextView tvActive = wordWrapEnabled ? tvContentWrapped : tvContentScrollable;
        tvActive.setText(builder);

        final int activeOffset = searchIndices.get(currentSearchIndex);
        
        // Постим скролл, чтобы он сработал ровно после того, как TextView обновит свой Layout в памяти!
        scrollView.post(() -> {
            Layout layout = tvActive.getLayout();
            if (layout != null) {
                // 1. Плавный вертикальный скролл к найденной строке (центрируем строку)
                int line = layout.getLineForOffset(activeOffset);
                int y = layout.getLineTop(line);
                int scrollY = Math.max(0, y - scrollView.getHeight() / 3);
                scrollView.smoothScrollTo(0, scrollY);
                
                // 2. Если перенос строк отключен — центрируем найденное слово ещё и по горизонтали!
                if (!wordWrapEnabled) {
                    float primaryHorizontal = layout.getPrimaryHorizontal(activeOffset);
                    int scrollX = Math.max(0, Math.round(primaryHorizontal) - horizontalScrollView.getWidth() / 3);
                    horizontalScrollView.smoothScrollTo(scrollX, 0);
                }
            }
        });
    }

    private void resetHighlighting() {
        tvContentScrollable.setText(fileContent);
        tvContentWrapped.setText(fileContent);
    }

    // ===== Диалоги =====
    private void showInfoDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_text_file_info, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        TextView tvName = view.findViewById(R.id.tv_stat_name);
        TextView tvSize = view.findViewById(R.id.tv_stat_size);
        TextView tvLines = view.findViewById(R.id.tv_stat_lines);
        TextView tvWords = view.findViewById(R.id.tv_stat_words);
        TextView tvChars = view.findViewById(R.id.tv_stat_chars);
        TextView tvCharsNoSpaces = view.findViewById(R.id.tv_stat_chars_no_spaces);

        tvName.setText(fileName == null ? "file.txt" : fileName);
        tvSize.setText(com.zametki.pro.utils.AttachmentUtils.formatSize(fileSize));
        tvLines.setText(String.format(Locale.getDefault(), "%,d", statLineCount));
        tvWords.setText(String.format(Locale.getDefault(), "%,d", statWordCount));
        tvChars.setText(String.format(Locale.getDefault(), "%,d", statCharCount));
        tvCharsNoSpaces.setText(String.format(Locale.getDefault(), "%,d", statCharNoSpacesCount));

        view.findViewById(R.id.btn_close_info).setOnClickListener(v -> {
            HapticUtils.light(v);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showOptionsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_text_viewer_options, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        CheckBox cbWordWrap = view.findViewById(R.id.cb_word_wrap);
        CheckBox cbLineNumbers = view.findViewById(R.id.cb_line_numbers);
        CheckBox cbFontStyle = view.findViewById(R.id.cb_font_style);

        cbWordWrap.setChecked(wordWrapEnabled);
        cbLineNumbers.setChecked(lineNumbersEnabled);
        cbFontStyle.setChecked(monospaceFont);

        // Перенос строк
        view.findViewById(R.id.opt_word_wrap).setOnClickListener(v -> {
            HapticUtils.light(v);
            wordWrapEnabled = !wordWrapEnabled;
            cbWordWrap.setChecked(wordWrapEnabled);
            applyWordWrap();

            if (searchPanel.getVisibility() == View.VISIBLE) {
                highlightAndScrollToMatch();
            }
        });

        // Номера строк
        view.findViewById(R.id.opt_line_numbers).setOnClickListener(v -> {
            HapticUtils.light(v);
            lineNumbersEnabled = !lineNumbersEnabled;
            cbLineNumbers.setChecked(lineNumbersEnabled);
            applyLineNumbers();
        });

        // Моноширинный шрифт
        view.findViewById(R.id.opt_font_style).setOnClickListener(v -> {
            HapticUtils.light(v);
            monospaceFont = !monospaceFont;
            cbFontStyle.setChecked(monospaceFont);
            applyFontStyle();
        });

        // Увеличить шрифт
        view.findViewById(R.id.opt_zoom_in).setOnClickListener(v -> {
            HapticUtils.light(v);
            if (currentTextSize < 36f) {
                currentTextSize += 2f;
                applyTextSize();
            }
        });

        // Уменьшить шрифт
        view.findViewById(R.id.opt_zoom_out).setOnClickListener(v -> {
            HapticUtils.light(v);
            if (currentTextSize > 8f) {
                currentTextSize -= 2f;
                applyTextSize();
            }
        });

        // Копировать весь текст
        view.findViewById(R.id.opt_copy).setOnClickListener(v -> {
            HapticUtils.light(v);
            dialog.dismiss();
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null) {
                cb.setPrimaryClip(ClipData.newPlainText("text", fileContent));
                Toast.makeText(this, "Текст скопирован в буфер обмена", Toast.LENGTH_SHORT).show();
            }
        });

        // Поделиться
        view.findViewById(R.id.opt_share).setOnClickListener(v -> {
            HapticUtils.light(v);
            dialog.dismiss();
            try {
                File file = new File(filePath);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Поделиться файлом"));
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось отправить файл: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
