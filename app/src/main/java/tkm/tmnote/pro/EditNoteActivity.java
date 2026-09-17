package tkm.tmnote.pro;

import tkm.tmnote.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.KeyListener;
import android.text.style.BackgroundColorSpan;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import tkm.tmnote.pro.adapters.AttachmentsEditorAdapter;
import tkm.tmnote.pro.adapters.ChecklistEditorAdapter;
import tkm.tmnote.pro.db.NotesRepository;
import tkm.tmnote.pro.dialogs.RecordAudioDialog;
import tkm.tmnote.pro.models.Attachment;
import tkm.tmnote.pro.models.Category;
import tkm.tmnote.pro.models.ChecklistItem;
import tkm.tmnote.pro.models.Note;
import tkm.tmnote.pro.services.AttachmentCopyService;
import tkm.tmnote.pro.utils.AttachmentUtils;
import tkm.tmnote.pro.utils.ConfirmSheet;
import tkm.tmnote.pro.utils.DeleteFilesHelper;
import tkm.tmnote.pro.utils.ColorUtils;
import tkm.tmnote.pro.utils.HapticUtils;
import tkm.tmnote.pro.utils.InfoSheet;
import tkm.tmnote.pro.utils.PrefsManager;
import tkm.tmnote.pro.utils.ReminderUtils;
import tkm.tmnote.pro.utils.TimeUtils;
import tkm.tmnote.pro.utils.AppExecutors;
import tkm.tmnote.pro.viewer.AudioPlayerActivity;
import tkm.tmnote.pro.viewer.ImageViewerActivity;
import tkm.tmnote.pro.viewer.VideoPlayerActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EditNoteActivity extends AppCompatActivity {

    /** Имена extra, возвращаемые через setResult — обрабатываются в MainActivity. */
    public static final String RESULT_ACTION   = "result_action";
    public static final String RESULT_NOTE_ID  = "result_note_id";
    public static final String ACTION_TRASHED  = "trashed";
    public static final String ACTION_ARCHIVED = "archived";
    public static final String ACTION_RESTORED = "restored";
    public static final String ACTION_UNARCHIVED = "unarchived";
    public static final String ACTION_DELETED_FOREVER = "deleted_forever";

    private NotesRepository repo;
    private PrefsManager prefs;
    private Note note;
    private boolean isNew;
    private boolean startedAsNew;
    private boolean unlocked = false;
    private boolean dirty = false;
    private int sourceFilter = -1;
    private String initialTitle;
    private String initialContent;
    private String initialChecklistJson;
    private String initialAttachmentsJson;
    private int initialColor;
    private long initialCategoryId;
    private boolean initialPinned;
    private boolean initialFavorite;
    private boolean initialLocked;
    private long initialReminder;
    private int initialType;
    private int initialTextSize;
    private final Set<String> pendingDeleteAttachmentFiles = new HashSet<>();

    private EditText etTitle;
    private EditText etContent;
    private RecyclerView rvChecklist;
    private RecyclerView rvAttachments;
    private View btnAddItem;
    private TextView tvCategory;
    private TextView tvReminder;
    private View root;
    private View bottomBar;
    private View progressBar;
    private TextView progressLabel;
    private android.widget.ProgressBar progressIndicator;
    private ImageView btnPin;
    private ImageView btnFav;
    private ImageView btnUndo;
    private ImageView btnRedo;

    private ChecklistEditorAdapter checklistAdapter;
    private AttachmentsEditorAdapter attachmentsAdapter;

    public static Note fastOpenCache = null;
    public static long fastOpenCacheId = -1;

    private static class EditAction {
        static final int TARGET_CONTENT = 0;
        static final int TARGET_TITLE = 1;

        static final int ACTION_INSERT = 1;
        static final int ACTION_DELETE = 2;
        static final int ACTION_REPLACE = 3;

        final int target;
        final String beforeText;
        String afterText;
        final int beforeSelStart;
        final int beforeSelEnd;
        int afterSelStart;
        int afterSelEnd;
        final int beforeScrollY;
        int afterScrollY;
        final int actionType;

        EditAction(int target, String beforeText, String afterText,
                   int beforeSelStart, int beforeSelEnd,
                   int afterSelStart, int afterSelEnd,
                   int beforeScrollY, int afterScrollY,
                   int actionType) {
            this.target = target;
            this.beforeText = beforeText != null ? beforeText : "";
            this.afterText = afterText != null ? afterText : "";
            this.beforeSelStart = beforeSelStart;
            this.beforeSelEnd = beforeSelEnd;
            this.afterSelStart = afterSelStart;
            this.afterSelEnd = afterSelEnd;
            this.beforeScrollY = beforeScrollY;
            this.afterScrollY = afterScrollY;
            this.actionType = actionType;
        }
    }

    private final Deque<EditAction> undoStack = new ArrayDeque<>();
    private final Deque<EditAction> redoStack = new ArrayDeque<>();
    private EditAction activeBatchAction = null;
    private Runnable pendingFinalizeBatchRunnable = null;
    private static final long BATCH_FINALIZE_DELAY = 600L;
    private boolean ignoreTextChange = false;
    private View contentContainer;

    // --- Large-text optimization fields ---
    private static final int LARGE_TEXT_THRESHOLD = 20000;
    private static final long AUTOSAVE_DELAY_SMALL = 1500L;
    private static final long AUTOSAVE_DELAY_LARGE = 2500L;

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingAutosaveRunnable;
    private int autosaveGeneration = 0;
    private volatile boolean isAutosaveInProgress = false;
    private String lastSavedTitle = "";
    private String lastSavedContent = "";
    private boolean beforeTextChangeWasLarge = false;
    private int beforeTextChangeStart = 0;
    private int beforeTextChangeCount = 0;

    private Uri pendingCameraUri;
    private File pendingCameraFile;

    private boolean copyInProgress = false;

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<Uri> videoCaptureLauncher;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> videoPickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String> createDocLauncher;
    private ActivityResultLauncher<Uri> openTreeLauncher;
    private ActivityResultLauncher<Intent> unlockLauncher;
    private Attachment pendingSaveAttachment;
    private java.util.List<Attachment> pendingSaveAttachments;
    // Selection mode для вложений
    private View selectionBarAttachments;
    private View topBar;
    private TextView tvSelectionCountAtt;
    private ImageView selAttClose, selAttSelectAll, selAttShare, selAttSave, selAttDelete;
    private boolean uiReady = false;
    private int bottomBarBasePadding = -1;
    private boolean suppressAutoSaveOnStop = false;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isSpeakingAloud = false;
    private String pendingTtsText;
    private final List<String> ttsChunks = new ArrayList<>();
    private int ttsChunkIndex = 0;
    private View bottomActionsRow;
    private View searchBarRow;
    private EditText etFindInline;
    private TextView tvSearchCount;
    private ImageView btnSearchPrev;
    private ImageView btnSearchNext;
    private final List<Integer> searchIndices = new ArrayList<>();
    private int currentSearchIndex = -1;
    private androidx.core.widget.NestedScrollView scrollContent;
    private boolean isFindMode = false;
    private Runnable pendingSearchRunnable;
    private int searchGeneration = 0;
    private final List<BackgroundColorSpan> activeSearchSpans = new ArrayList<>();
    private static final int MAX_SEARCH_MATCHES = 1000;
    private static final long SEARCH_DEBOUNCE = 300L;
    private ImageView btnBack;
    private ImageView btnMore;
    private ImageView btnPalette;
    private ImageView btnCheck;
    private ImageView btnAttach;
    private ImageView btnReminderQuick;
    private ImageView btnFindReplace;
    private ImageView btnReadMode;
    private View readModeRow;
    private TextView btnSwitchToEdit;
    private boolean readMode = false;
    private boolean readModeFeatureEnabled = false;
    private KeyListener titleKeyListener;
    private KeyListener contentKeyListener;
    private int titleInputType = 0;
    private int contentInputType = 0;

    private static final int PERMISSION_ACTION_NONE = 0;
    private static final int PERMISSION_ACTION_CAMERA_PHOTO = 1;
    private static final int PERMISSION_ACTION_CAMERA_VIDEO = 2;
    private static final int PERMISSION_ACTION_RECORD_AUDIO = 3;
    private int pendingPermissionAction = PERMISSION_ACTION_NONE;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (note == null) return;
            long noteId = intent.getLongExtra(AttachmentCopyService.EXTRA_NOTE_ID, 0);
            if (noteId != note.getId()) return;

            int progressVal = intent.getIntExtra(AttachmentCopyService.EXTRA_PROGRESS, -1);
            int total = intent.getIntExtra(AttachmentCopyService.EXTRA_TOTAL, 0);
            String name = intent.getStringExtra(AttachmentCopyService.EXTRA_CURRENT_NAME);
            boolean finished = intent.hasExtra("finished_all");
            int doneCount = intent.getIntExtra(AttachmentCopyService.EXTRA_DONE, 0);
            // Use literal keys to stay compatible with older AttachmentCopyService versions
            String result = intent.getStringExtra("result");
            int successCount = intent.getIntExtra("success_count", doneCount);
            int failedCount = intent.getIntExtra("failed_count", 0);

            if (finished) {
                progressBar.setVisibility(View.GONE);
                copyInProgress = false;
                if (uiReady && attachmentsAdapter != null) syncAttachmentsFromDb();
                if (result != null && root != null) {
                    String msg;
                    if ("SUCCESS".equals(result)) {
                        try { msg = getString(R.string.service_copy_done, successCount); }
                        catch (Exception e) { msg = "Скопировано: " + successCount; }
                    } else if ("PARTIAL".equals(result)) {
                        try { msg = getString(R.string.service_copy_partial, successCount, failedCount); }
                        catch (Exception e) { msg = "Скопировано: " + successCount + ", не удалось: " + failedCount; }
                    } else if ("CANCELLED".equals(result)) {
                        try { msg = getString(R.string.service_copy_cancelled); }
                        catch (Exception e) { msg = "Копирование отменено"; }
                    } else {
                        try { msg = getString(R.string.service_copy_failed); }
                        catch (Exception e) { msg = "Не удалось скопировать файлы"; }
                    }
                    Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
                }
            } else if (progressVal == -2) {
                progressBar.setVisibility(View.VISIBLE);
                copyInProgress = true;
                String txt = (name != null ? name + "  " : "") +
                        "(" + Math.min(doneCount + 1, total) + "/" + total + ")";
                progressLabel.setText(txt);
                progressIndicator.setIndeterminate(true);
            } else if (progressVal >= 0) {
                progressBar.setVisibility(View.VISIBLE);
                copyInProgress = true;
                String txt = (name != null ? name + "  " : "") +
                        "(" + Math.min(doneCount + 1, total) + "/" + total + ") " + progressVal + "%";
                progressLabel.setText(txt);
                if (progressVal > 0) {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setProgress(progressVal);
                } else {
                    progressIndicator.setIndeterminate(true);
                }
            } else if (progressVal == -1) {
                progressBar.setVisibility(View.VISIBLE);
                copyInProgress = true;
                if (name != null && root != null) {
                    Snackbar.make(root, "Не удалось: " + name, Snackbar.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_edit_note);
        tkm.tmnote.pro.utils.SystemBarsHelper.prepare(this);

        repo = new NotesRepository(this);
        prefs = new PrefsManager(this);

        long id = getIntent().getLongExtra("note_id", 0);
        long defaultCatId = getIntent().getLongExtra("default_category_id", 0);
        unlocked = getIntent().getBooleanExtra("unlocked", false);
        sourceFilter = getIntent().getIntExtra("source_filter", -1);
        isNew = id == 0;
        startedAsNew = isNew;

        registerUnlockLauncher();
        bindViews();
        registerLaunchers();
        registerBackCallback();

        if (isNew) {
            note = new Note();
            if (defaultCatId > 0) note.setCategoryId(defaultCatId);
            try { note.setTextSize(new PrefsManager(this).getDefaultTextSize()); } catch (Exception ignored) {}
            if (note.isLocked() && !unlocked) {
                askUnlock();
                return;
            }
            proceedSetup();
        } else {
            if (fastOpenCache != null && fastOpenCacheId == id) {
                note = fastOpenCache;
                fastOpenCache = null;
                fastOpenCacheId = -1;
                if (note.isLocked() && !unlocked) {
                    askUnlock();
                    return;
                }
                proceedSetup();
                final long bgId = id;
                AppExecutors.getInstance().diskIO().execute(() -> {
                    try {
                        Note fresh = repo.getNoteById(bgId);
                        if (fresh != null) {
                            mainHandler.post(() -> {
                                if (note != null && note.getId() == bgId) {
                                    if (!fresh.getContent().equals(note.getContent()) || !fresh.getTitle().equals(note.getTitle())) {
                                        int selStart = 0, selEnd = 0;
                                        try {
                                            if (etContent != null) {
                                                selStart = etContent.getSelectionStart();
                                                selEnd = etContent.getSelectionEnd();
                                            }
                                        } catch (Exception ignored) {}
                                        note = fresh;
                                        initialTitle = fresh.getTitle();
                                        initialContent = fresh.getContent();
                                        initialChecklistJson = fresh.getChecklistJson();
                                        initialAttachmentsJson = fresh.getAttachmentsJson();
                                        initialColor = fresh.getColorIndex();
                                        initialCategoryId = fresh.getCategoryId();
                                        initialPinned = fresh.isPinned();
                                        initialFavorite = fresh.isFavorite();
                                        initialLocked = fresh.isLocked();
                                        initialReminder = fresh.getReminderAt();
                                        initialType = fresh.getType();
                                        initialTextSize = fresh.getTextSize();
                                        lastSavedTitle = initialTitle;
                                        lastSavedContent = initialContent;
                                        ignoreTextChange = true;
                                        try {
                                            etTitle.setText(initialTitle);
                                            if (note.getType() == Note.TYPE_TEXT) {
                                                etContent.setText(initialContent != null ? initialContent : "", TextView.BufferType.EDITABLE);
                                                try {
                                                    int len = etContent.getText() != null ? etContent.getText().length() : 0;
                                                    etContent.setSelection(Math.min(selStart, len));
                                                } catch (Exception ignored) {}
                                            }
                                        } finally { ignoreTextChange = false; }
                                        updatePinFav();
                                        updateCategoryLabelAsync();
                                        updateReminderLabel();
                                        applyColor();
                                        reloadAttachments();
                                    }
                                }
                            });
                        }
                    } catch (Exception ignored) {}
                });
                return;
            }
            AppExecutors.getInstance().diskIO().execute(() -> {
                Note loaded;
                try { loaded = repo.getNoteById(id); } catch (Exception e) { loaded = null; }
                Note finalLoaded = loaded;
                mainHandler.post(() -> {
                    if (finalLoaded == null) { finish(); return; }
                    note = finalLoaded;
                    if (note.isLocked() && !unlocked) {
                        askUnlock();
                        return;
                    }
                    proceedSetup();
                });
            });
        }
    }

    private void registerUnlockLauncher() {
        unlockLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        unlocked = true;
                        proceedSetup();
                    } else {
                        finish();
                    }
                });
    }

    private void registerBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (attachmentsAdapter != null && attachmentsAdapter.isSelectionMode()) {
                    attachmentsAdapter.clearSelection();
                    return;
                }
                if (isFindMode) {
                    exitFindMode();
                    return;
                }
                saveAndFinish();
            }
        });
    }

    private void askUnlock() {
        Intent i = new Intent(this, LockActivity.class);
        i.putExtra(LockActivity.EXTRA_TITLE, getString(R.string.edit_note_locked_note_title));
        i.putExtra(LockActivity.EXTRA_SUBTITLE, getString(R.string.edit_note_enter_password_to_view));
        unlockLauncher.launch(i);
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        bottomBar = findViewById(R.id.bottom_bar);
        progressBar = findViewById(R.id.progress_bar);
        progressLabel = findViewById(R.id.progress_label);
        progressIndicator = findViewById(R.id.progress_indicator);
        etTitle = findViewById(R.id.et_title);
        etContent = findViewById(R.id.et_content);
        rvChecklist = findViewById(R.id.rv_checklist);
        rvAttachments = findViewById(R.id.rv_attachments);
        topBar = findViewById(R.id.top_bar);
        selectionBarAttachments = findViewById(R.id.selection_bar_attachments);
        tvSelectionCountAtt = findViewById(R.id.tv_selection_count_att);
        selAttClose = findViewById(R.id.sel_att_close);
        selAttSelectAll = findViewById(R.id.sel_att_select_all);
        selAttShare = findViewById(R.id.sel_att_share);
        selAttSave = findViewById(R.id.sel_att_save);
        selAttDelete = findViewById(R.id.sel_att_delete);
        btnAddItem = findViewById(R.id.btn_add_item);
        tvCategory = findViewById(R.id.tv_category);
        tvReminder = findViewById(R.id.tv_reminder);
        btnPin = findViewById(R.id.btn_pin);
        btnFav = findViewById(R.id.btn_fav);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        bottomActionsRow = findViewById(R.id.bottom_actions_row);
        searchBarRow = findViewById(R.id.search_bar_row);
        etFindInline = findViewById(R.id.et_find_inline);
        tvSearchCount = findViewById(R.id.tv_search_count);
        btnSearchPrev = findViewById(R.id.btn_search_prev);
        btnSearchNext = findViewById(R.id.btn_search_next);
        scrollContent = findViewById(R.id.scroll_content);
        contentContainer = findViewById(R.id.content_container);
        btnBack = findViewById(R.id.btn_back);
        btnMore = findViewById(R.id.btn_more);
        btnPalette = findViewById(R.id.btn_palette);
        btnCheck = findViewById(R.id.btn_checklist_toggle);
        btnAttach = findViewById(R.id.btn_attach);
        btnReminderQuick = findViewById(R.id.btn_reminder_quick);
        btnFindReplace = findViewById(R.id.btn_find_replace);
        btnReadMode = findViewById(R.id.btn_read_mode);
        readModeRow = findViewById(R.id.read_mode_row);
        btnSwitchToEdit = findViewById(R.id.btn_switch_to_edit);
        installKeyboardInsetsHandler();
    }

    private void installKeyboardInsetsHandler() {
        if (root == null || bottomBar == null) return;
        if (bottomBarBasePadding < 0) {
            bottomBarBasePadding = bottomBar.getPaddingBottom();
        }

        final int rootBaseLeft = root.getPaddingLeft();
        final int rootBaseTop = root.getPaddingTop();
        final int rootBaseRight = root.getPaddingRight();
        final int rootBaseBottom = root.getPaddingBottom();
        final int bottomBarBaseLeft = bottomBar.getPaddingLeft();
        final int bottomBarBaseTop = bottomBar.getPaddingTop();
        final int bottomBarBaseRight = bottomBar.getPaddingRight();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int keyboardExtra = Math.max(0, imeBottom - bars.bottom);

            // Android 15+ с targetSdk 35+ принудительно рисует layout под status/nav bars.
            // Добавляем верхний inset корню, чтобы toolbar редактора не уходил под status bar.
            root.setPadding(
                    rootBaseLeft + bars.left,
                    rootBaseTop + bars.top,
                    rootBaseRight + bars.right,
                    rootBaseBottom
            );

            // Нижняя панель должна быть выше navigation bar, а при клавиатуре — выше IME.
            bottomBar.setPadding(
                    bottomBarBaseLeft,
                    bottomBarBaseTop,
                    bottomBarBaseRight,
                    bottomBarBasePadding + bars.bottom + keyboardExtra
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void proceedSetup() {
        if (uiReady) return;
        readModeFeatureEnabled = prefs.isEditorReadModeEnabled();
        titleKeyListener = etTitle != null ? etTitle.getKeyListener() : null;
        contentKeyListener = etContent != null ? etContent.getKeyListener() : null;
        titleInputType = etTitle != null ? etTitle.getInputType() : 0;
        contentInputType = etContent != null ? etContent.getInputType() : 0;

        btnBack.setOnClickListener(v -> { HapticUtils.light(v); saveAndFinish(); });
        btnMore.setOnClickListener(v -> { HapticUtils.light(v); showMoreSheet(); });
        btnPalette.setOnClickListener(v -> { HapticUtils.light(v); showColorSheet(); });
        btnCheck.setOnClickListener(v -> { HapticUtils.light(v); toggleChecklistMode(); });
        btnAttach.setOnClickListener(v -> { HapticUtils.light(v); showAttachSheet(); });
        btnReminderQuick.setOnClickListener(v -> { HapticUtils.light(v); showReminderSheet(); });
        btnFindReplace.setOnClickListener(v -> { HapticUtils.light(v); toggleFindMode(); });

        btnReadMode.setOnClickListener(v -> {
            if (!readModeFeatureEnabled || sourceFilter == 5) return;
            HapticUtils.light(v);
            setReadMode(!readMode, true);
        });
        if (btnSwitchToEdit != null) {
            btnSwitchToEdit.setOnClickListener(v -> {
                if (sourceFilter == 5) return;
                HapticUtils.light(v);
                setReadMode(false, true);
            });
        }

        btnPin.setOnClickListener(v -> {
            HapticUtils.light(v);
            note.setPinned(!note.isPinned());
            updatePinFav();
            Snackbar.make(root, note.isPinned() ? R.string.msg_pinned : R.string.msg_unpinned,
                    Snackbar.LENGTH_SHORT).show();
            dirty = true;
            scheduleAutosaveDebounced();
        });
        btnFav.setOnClickListener(v -> {
            HapticUtils.light(v);
            toggleLock();
        });

        btnUndo.setOnClickListener(v -> { HapticUtils.light(v); doUndo(); });
        btnRedo.setOnClickListener(v -> { HapticUtils.light(v); doRedo(); });

        tvCategory.setOnClickListener(v -> { HapticUtils.light(v); showCategorySheet(); });
        tvReminder.setOnClickListener(v -> { HapticUtils.light(v); showReminderSheet(); });

        // Save initial snapshots without heavy toString copies where possible
        initialTitle = note.getTitle();
        initialContent = note.getContent();
        initialChecklistJson = note.getChecklistJson();
        initialAttachmentsJson = note.getAttachmentsJson();
        initialColor = note.getColorIndex();
        initialCategoryId = note.getCategoryId();
        initialPinned = note.isPinned();
        initialFavorite = note.isFavorite();
        initialLocked = note.isLocked();
        initialReminder = note.getReminderAt();
        initialType = note.getType();
        initialTextSize = note.getTextSize();
        lastSavedTitle = initialTitle;
        lastSavedContent = initialContent;

        // --- Optimized loading: show UI fast, minimal init ---
        ignoreTextChange = true;
        try {
            etTitle.setText(initialTitle);
        } catch (Exception ignored) {}
        if (note.getType() == Note.TYPE_TEXT) {
            try {
                String content = initialContent != null ? initialContent : "";
                etContent.setText(content, TextView.BufferType.EDITABLE);
            } catch (Exception e) {
                etContent.setText("");
            }
            showTextMode();
        } else {
            showChecklistMode();
        }
        applyTextSize(note.getTextSize());
        try {
            etContent.setIncludeFontPadding(false);
            if (Build.VERSION.SDK_INT >= 28) etContent.setFallbackLineSpacing(false);
            etContent.setElegantTextHeight(false);
            etContent.setHorizontallyScrolling(false);
            etContent.setSaveEnabled(false);
            etContent.setFreezesText(false);
            if (Build.VERSION.SDK_INT >= 23) {
                etContent.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                etContent.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
            }
            etContent.setLineSpacing(0f, isLargeTextMode() ? 1.2f : 1.45f);
            etContent.setVerticalFadingEdgeEnabled(false);
            etContent.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            etContent.setScrollbarFadingEnabled(true);
            // Disable spellcheck / suggestions completely for large text
            if (isLargeTextMode()) {
                etContent.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            }
        } catch (Exception ignored) {}

        // Rocket: always use fixed-height EditText to avoid parent requestLayout on each keystroke.
        // Previously only for >20k, but even 2-5k causes Choreographer skips and BLASTBufferQueue stalls.
        try {
            if (scrollContent != null) {
                scrollContent.setNestedScrollingEnabled(false);
                scrollContent.setOverScrollMode(View.OVER_SCROLL_NEVER);
            }
            if (contentContainer != null && etContent != null) {
                android.view.ViewGroup.LayoutParams cLp = contentContainer.getLayoutParams();
                if (cLp != null) {
                    cLp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                    contentContainer.setLayoutParams(cLp);
                }
                if (contentContainer instanceof LinearLayout) {
                    LinearLayout.LayoutParams eLp = (LinearLayout.LayoutParams) etContent.getLayoutParams();
                    if (eLp != null) {
                        eLp.height = 0;
                        eLp.weight = 1;
                        etContent.setLayoutParams(eLp);
                    }
                }
                etContent.setMinHeight(0);
                etContent.setMinimumHeight(0);
                etContent.setVerticalScrollBarEnabled(true);
                etContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
                etContent.setHorizontallyScrolling(false);
                // Hardware layer for large text can improve scroll performance
                if (isLargeTextMode() && Build.VERSION.SDK_INT >= 23) {
                    etContent.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }
            }
        } catch (Exception ignored) {}

        ignoreTextChange = false;

        updatePinFav();

        // Title TextWatcher
        etTitle.addTextChangedListener(new TextWatcher() {
            private String beforeStr = "";
            private int beforeSelStart = 0;
            private int beforeSelEnd = 0;
            private int changeStart = 0;
            private int changeCount = 0;
            private int changeAfter = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (ignoreTextChange) return;
                beforeStr = s != null ? s.toString() : "";
                try {
                    beforeSelStart = etTitle.getSelectionStart();
                    beforeSelEnd = etTitle.getSelectionEnd();
                } catch (Exception e) {
                    beforeSelStart = start;
                    beforeSelEnd = start;
                }
                changeStart = start;
                changeCount = count;
                changeAfter = after;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreTextChange) return;
                dirty = true;
                scheduleAutosaveDebounced();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) return;
                String afterStr = s != null ? s.toString() : "";
                int afterSelStart = etTitle.getSelectionStart();
                int afterSelEnd = etTitle.getSelectionEnd();

                onEditTextChanged(
                        EditAction.TARGET_TITLE,
                        beforeStr,
                        afterStr,
                        beforeSelStart,
                        beforeSelEnd,
                        afterSelStart,
                        afterSelEnd,
                        0,
                        0,
                        changeStart,
                        changeCount,
                        changeAfter
                );
            }
        });

        // Content TextWatcher
        etContent.addTextChangedListener(new TextWatcher() {
            private String beforeStr = "";
            private int beforeSelStart = 0;
            private int beforeSelEnd = 0;
            private int beforeScrollY = 0;
            private int changeStart = 0;
            private int changeCount = 0;
            private int changeAfter = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (ignoreTextChange) return;
                beforeStr = s != null ? s.toString() : "";
                try {
                    beforeSelStart = etContent.getSelectionStart();
                    beforeSelEnd = etContent.getSelectionEnd();
                } catch (Exception e) {
                    beforeSelStart = start;
                    beforeSelEnd = start;
                }
                beforeScrollY = getCurrentEtScrollY();
                changeStart = start;
                changeCount = count;
                changeAfter = after;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreTextChange) return;
                dirty = true;
                scheduleAutosaveDebounced();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) return;
                String afterStr = s != null ? s.toString() : "";
                int afterSelStart = etContent.getSelectionStart();
                int afterSelEnd = etContent.getSelectionEnd();
                int afterScrollY = getCurrentEtScrollY();

                onEditTextChanged(
                        EditAction.TARGET_CONTENT,
                        beforeStr,
                        afterStr,
                        beforeSelStart,
                        beforeSelEnd,
                        afterSelStart,
                        afterSelEnd,
                        beforeScrollY,
                        afterScrollY,
                        changeStart,
                        changeCount,
                        changeAfter
                );
            }
        });

        btnAddItem.setOnClickListener(v -> { HapticUtils.light(v); if (checklistAdapter != null) checklistAdapter.addNew(); });

        // --- Attachment rocket optimization: instant UI, no blocking ---
        if (rvAttachments != null) {
            try {
                rvAttachments.setHasFixedSize(true);
                rvAttachments.setItemViewCacheSize(20);
                // Drawing cache is deprecated and causes extra bitmap allocations -> lag
                rvAttachments.setDrawingCacheEnabled(false);
            } catch (Exception ignored) {}
        }
        attachmentsAdapter = new AttachmentsEditorAdapter(this, new ArrayList<>(),
                new AttachmentsEditorAdapter.Callback() {
                    @Override public void onClick(Attachment a) {
                        if (readMode) return;
                        openAttachment(a);
                    }
                    @Override public void onLongClick(Attachment a) {
                        HapticUtils.medium(rvAttachments);
                    }
                });
        rvAttachments.setAdapter(attachmentsAdapter);
        setupAttachmentsSelectionBar();
        // Load attachments off UI thread after first frame for instant editor open
        mainHandler.post(() -> {
            AppExecutors.getInstance().diskIO().execute(() -> {
                List<Attachment> atts;
                try { atts = note.getAttachments(); } catch (Exception e) { atts = new ArrayList<>(); }
                List<Attachment> finalAtts = atts;
                mainHandler.post(() -> {
                    try {
                        if (attachmentsAdapter != null) {
                            attachmentsAdapter.setData(finalAtts);
                            rvAttachments.setVisibility(finalAtts.isEmpty() ? View.GONE : View.VISIBLE);
                            applyAttachmentsLayout(finalAtts.size());
                        }
                    } catch (Exception ignored) {}
                });
            });
        });

        applyColor();
        // Defer category label DB read off UI thread
        updateCategoryLabelAsync();
        updateReminderLabel();
        updateUndoButtons();
        setupFindInlineBar();
        setReadMode(!isNew && prefs.isEditorReadModeEnabled(), false);

        if (isNew) {
            etTitle.requestFocus();
            etTitle.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etTitle, 0);
            }, 200);
        }

        undoStack.clear();
        redoStack.clear();
        finalizeActiveBatch();
        updateUndoButtons();

        uiReady = true;
    }

    private boolean isLargeTextMode() {
        try {
            if (etContent != null && etContent.getText() != null) {
                return etContent.getText().length() > LARGE_TEXT_THRESHOLD;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private int getCurrentScrollY() {
        try {
            if (scrollContent != null) return scrollContent.getScrollY();
        } catch (Exception ignored) {}
        return 0;
    }

    private int getCurrentEtScrollY() {
        try {
            if (etContent != null) return etContent.getScrollY();
        } catch (Exception ignored) {}
        return 0;
    }

    private void scheduleBatchFinalizeDebounced() {
        if (pendingFinalizeBatchRunnable != null) {
            mainHandler.removeCallbacks(pendingFinalizeBatchRunnable);
        }
        pendingFinalizeBatchRunnable = this::finalizeActiveBatch;
        mainHandler.postDelayed(pendingFinalizeBatchRunnable, BATCH_FINALIZE_DELAY);
    }

    private void finalizeActiveBatch() {
        if (pendingFinalizeBatchRunnable != null) {
            mainHandler.removeCallbacks(pendingFinalizeBatchRunnable);
            pendingFinalizeBatchRunnable = null;
        }
        if (activeBatchAction != null) {
            if (activeBatchAction.beforeText.equals(activeBatchAction.afterText)) {
                if (!undoStack.isEmpty() && undoStack.peek() == activeBatchAction) {
                    undoStack.pop();
                }
            }
            activeBatchAction = null;
            updateUndoButtons();
        }
    }

    private void scheduleAutosaveDebounced() {
        if (!uiReady) return;
        if (prefs != null && prefs.isConfirmSaveOnExitEnabled()) return;
        if (copyInProgress) return;
        // For large notes, skip debounced autosave to keep typing 100% smooth - save only onStop/onPause
        if (isLargeTextMode()) return;
        if (pendingAutosaveRunnable != null) {
            mainHandler.removeCallbacks(pendingAutosaveRunnable);
        }
        long delay = AUTOSAVE_DELAY_SMALL;
        pendingAutosaveRunnable = this::performAutosaveAsync;
        mainHandler.postDelayed(pendingAutosaveRunnable, delay);
    }

    private void cancelPendingRunnables() {
        if (pendingFinalizeBatchRunnable != null) {
            mainHandler.removeCallbacks(pendingFinalizeBatchRunnable);
            pendingFinalizeBatchRunnable = null;
        }
        if (pendingAutosaveRunnable != null) {
            mainHandler.removeCallbacks(pendingAutosaveRunnable);
            pendingAutosaveRunnable = null;
        }
        if (pendingSearchRunnable != null) {
            mainHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
        searchGeneration++;
        activeBatchAction = null;
    }

    private void performAutosaveAsync() {
        if (note == null) return;
        if (prefs != null && prefs.isConfirmSaveOnExitEnabled()) return;
        if (copyInProgress) return;
        if (isAutosaveInProgress) {
            scheduleAutosaveDebounced();
            return;
        }
        // Capture UI data on UI thread quickly
        String title;
        String content;
        try {
            title = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : note.getTitle();
        } catch (Exception e) {
            title = note.getTitle();
        }
        try {
            content = etContent != null && etContent.getText() != null ? etContent.getText().toString() : note.getContent();
        } catch (Exception e) {
            content = note.getContent();
        }
        if (title.equals(lastSavedTitle) && content.equals(lastSavedContent) && !dirty) {
            return;
        }
        final String finalTitle = title;
        final String finalContent = content;
        final int gen = ++autosaveGeneration;
        isAutosaveInProgress = true;

        // Capture all note fields on UI thread to avoid touching note from background
        final long noteId = note.getId();
        final int noteType = note.getType();
        final int colorIndex = note.getColorIndex();
        final long categoryId = note.getCategoryId();
        final boolean pinned = note.isPinned();
        final boolean favorite = note.isFavorite();
        final boolean locked = note.isLocked();
        final long reminderAt = note.getReminderAt();
        final int textSize = note.getTextSize();
        final String attachmentsJson = note.getAttachmentsJson();
        final long createdAt = note.getCreatedAt();
        final String existingChecklistJson = note.getChecklistJson();
        final String existingContent = note.getContent();

        final String checklistJson;
        if (noteType == Note.TYPE_CHECKLIST && checklistAdapter != null) {
            String tmp;
            try { tmp = tkm.tmnote.pro.models.ChecklistItem.toJson(checklistAdapter.getItems()); }
            catch (Exception e) { tmp = existingChecklistJson; }
            checklistJson = tmp;
        } else {
            checklistJson = null;
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (gen < autosaveGeneration) {
                    return;
                }
                Note toSave = new Note();
                toSave.setId(noteId);
                toSave.setTitle(finalTitle);
                if (noteType == Note.TYPE_TEXT) {
                    toSave.setContent(finalContent);
                } else {
                    if (checklistJson != null) toSave.setChecklistJson(checklistJson);
                    else toSave.setChecklistJson(existingChecklistJson);
                    toSave.setContent(existingContent);
                }
                toSave.setType(noteType);
                toSave.setColorIndex(colorIndex);
                toSave.setCategoryId(categoryId);
                toSave.setPinned(pinned);
                toSave.setFavorite(favorite);
                toSave.setLocked(locked);
                toSave.setReminderAt(reminderAt);
                toSave.setTextSize(textSize);
                toSave.setAttachmentsJson(attachmentsJson);
                toSave.setCreatedAt(createdAt);
                toSave.setUpdatedAt(System.currentTimeMillis());

                if (toSave.getId() == 0) {
                    long newId = repo.addNote(toSave);
                    mainHandler.post(() -> {
                        if (note != null && note.getId() == 0) {
                            note.setId(newId);
                            isNew = false;
                        }
                    });
                } else {
                    repo.updateNote(toSave);
                    mainHandler.post(() -> {
                        if (note != null && note.getId() == toSave.getId()) {
                            note.setTitle(finalTitle);
                            if (noteType == Note.TYPE_TEXT) note.setContent(finalContent);
                            else if (checklistJson != null) note.setChecklistJson(checklistJson);
                        }
                    });
                }

                mainHandler.post(() -> {
                    lastSavedTitle = finalTitle;
                    lastSavedContent = finalContent;
                    if (gen == autosaveGeneration) dirty = false;
                });

                mainHandler.post(() -> {
                    try {
                        if (reminderAt > System.currentTimeMillis() && noteId > 0) {
                            ReminderUtils.schedule(EditNoteActivity.this, noteId, reminderAt);
                        } else if (noteId > 0) {
                            ReminderUtils.cancel(EditNoteActivity.this, noteId);
                        }
                    } catch (Exception ignored) {}
                });

            } catch (Exception e) {
                try { android.util.Log.e("EditNote", "autosave failed", e); } catch (Exception ignored) {}
            } finally {
                mainHandler.post(() -> {
                    isAutosaveInProgress = false;
                    if (autosaveGeneration > gen) scheduleAutosaveDebounced();
                });
            }
        });
    }

    private void registerLaunchers() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;
                    startCopy(new ArrayList<>(uris));
                });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;
                    startCopy(new ArrayList<>(uris));
                });

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;
                    startCopy(new ArrayList<>(uris));
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                ok -> {
                    if (Boolean.TRUE.equals(ok) && pendingCameraUri != null) {
                        ArrayList<Uri> list = new ArrayList<>();
                        list.add(pendingCameraUri);
                        startCopy(list);
                    }
                });

        videoCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.CaptureVideo(),
                ok -> {
                    if (Boolean.TRUE.equals(ok) && pendingCameraUri != null) {
                        ArrayList<Uri> list = new ArrayList<>();
                        list.add(pendingCameraUri);
                        startCopy(list);
                    }
                });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    int action = pendingPermissionAction;
                    pendingPermissionAction = PERMISSION_ACTION_NONE;
                    if (!Boolean.TRUE.equals(granted)) {
                        Snackbar.make(root, getString(R.string.permission_denied), Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    if (action == PERMISSION_ACTION_CAMERA_PHOTO) {
                        ensureCameraAndOpen(false);
                    } else if (action == PERMISSION_ACTION_CAMERA_VIDEO) {
                        ensureCameraAndOpen(true);
                    } else if (action == PERMISSION_ACTION_RECORD_AUDIO) {
                        recordAudio();
                    }
                });

        createDocLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"), uri -> {
                    if (uri == null || pendingSaveAttachment == null) return;
                    final Attachment a = pendingSaveAttachment;
                    pendingSaveAttachment = null;
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        try (InputStream is = new FileInputStream(
                                AttachmentUtils.getFile(this, a.fileName));
                             OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os == null) throw new Exception("openOutputStream null");
                            byte[] buf = new byte[64 * 1024];
                            int n;
                            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                            runOnUiThread(() -> Snackbar.make(root, getString(R.string.file_saved),
                                    Snackbar.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            runOnUiThread(() -> Snackbar.make(root, getString(R.string.edit_note_error_with_reason, e.getMessage()),
                                    Snackbar.LENGTH_LONG).show());
                        }
                    });
                });

        openTreeLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), treeUri -> {
                    java.util.List<Attachment> toSave = pendingSaveAttachments;
                    pendingSaveAttachments = null;
                    if (treeUri == null || toSave == null || toSave.isEmpty()) return;
                    final java.util.List<Attachment> finalList = toSave;
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        int ok = 0, fail = 0;
                        DocumentFile tree;
                        try {
                            getContentResolver().takePersistableUriPermission(treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        } catch (Exception ignored) {}
                        tree = DocumentFile.fromTreeUri(this, treeUri);
                        if (tree == null || !tree.canWrite()) {
                            runOnUiThread(() -> Snackbar.make(root, getString(R.string.copied_to_file_failed),
                                    Snackbar.LENGTH_SHORT).show());
                            return;
                        }
                        for (Attachment a : finalList) {
                            try {
                                File src1 = AttachmentUtils.getFile(this, a.fileName);
                                if (!src1.exists()) { fail++; continue; }
                                String name = a.displayName == null ? a.fileName : a.displayName;
                                // если файл с таким именем уже есть — добавим суффикс
                                String unique = uniqueNameInTree(tree, name);
                                String mime = AttachmentUtils.getMime(a.type, a.fileName);
                                if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
                                DocumentFile out = tree.createFile(mime, unique);
                                if (out == null) { fail++; continue; }
                                try (InputStream is = new FileInputStream(src1);
                                     OutputStream os = getContentResolver().openOutputStream(out.getUri())) {
                                    if (os == null) { fail++; continue; }
                                    byte[] buf = new byte[64 * 1024];
                                    int n;
                                    while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                                    ok++;
                                }
                            } catch (Exception e) {
                                fail++;
                            }
                        }
                        final int okF = ok, failF = fail;
                        runOnUiThread(() -> {
                            String msg = getString(R.string.edit_note_saved_multi, okF);
                            if (failF > 0) msg = msg + " (" + getString(R.string.edit_note_failed_multi, failF) + ")";
                            Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
                            if (attachmentsAdapter != null) attachmentsAdapter.clearSelection();
                        });
                    });
                });
    }

    private String uniqueNameInTree(DocumentFile tree, String name) {
        if (tree.findFile(name) == null) return name;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (tree.findFile(candidate) == null) return candidate;
        }
        return base + "_" + System.currentTimeMillis() + ext;
    }

    private void startCopy(ArrayList<Uri> uris) {
        if (!ensureEditableMode(null)) return;
        if (isNew || note.getId() <= 0) {
            forceSaveNew();
            if (note.getId() <= 0) {
                Snackbar.make(root, getString(R.string.note_save_failed), Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        copyInProgress = true;
        Intent svc = new Intent(this, AttachmentCopyService.class);
        svc.putExtra(AttachmentCopyService.EXTRA_NOTE_ID, note.getId());
        svc.putParcelableArrayListExtra("uris", uris);
        ContextCompat.startForegroundService(this, svc);
        progressBar.setVisibility(View.VISIBLE);
        progressLabel.setText(getString(R.string.edit_note_prepare_copy));
        progressIndicator.setIndeterminate(true);
    }

    private void reloadAttachments() {
        List<Attachment> atts = note.getAttachments();
        attachmentsAdapter.setData(atts);
        rvAttachments.setVisibility(atts.isEmpty() ? View.GONE : View.VISIBLE);
        applyAttachmentsLayout(atts.size());
    }

    private void syncAttachmentsFromDb() {
        if (note == null || note.getId() <= 0) {
            reloadAttachments();
            return;
        }
        final long nid = note.getId();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Note fresh = repo.getNoteById(nid);
                if (fresh != null) {
                    List<Attachment> merged = fresh.getAttachments();
                    if (!pendingDeleteAttachmentFiles.isEmpty()) {
                        merged.removeIf(att -> att != null
                                && att.fileName != null
                                && pendingDeleteAttachmentFiles.contains(att.fileName));
                    }
                    mainHandler.post(() -> {
                        if (note != null && note.getId() == nid) {
                            note.setAttachments(merged);
                            reloadAttachments();
                        }
                    });
                } else {
                    mainHandler.post(this::reloadAttachments);
                }
            } catch (Exception e) {
                mainHandler.post(this::reloadAttachments);
            }
        });
    }

    private void applyAttachmentsLayout(int count) {
        int maxCols = prefs.getAttachmentColumns(); // 1..5
        int cols;
        if (count <= 1) cols = 1;
        else if (count == 2) cols = 2;
        else if (count == 3) cols = 3;
        else if (count == 4) cols = 4;
        else cols = 5;
        // Никогда не превышаем лимит, заданный в настройках.
        if (cols > maxCols) cols = maxCols;
        if (cols < 1) cols = 1;

        GridLayoutManager lm;
        if (!(rvAttachments.getLayoutManager() instanceof GridLayoutManager)
                || ((GridLayoutManager) rvAttachments.getLayoutManager()).getSpanCount() != cols) {
            lm = new GridLayoutManager(this, cols);
            rvAttachments.setLayoutManager(lm);
        }
        attachmentsAdapter.setColumns(cols);
        attachmentsAdapter.notifyDataSetChanged();
    }

    private int getMaxHistoryLimit() {
        try {
            int len = 0;
            if (etContent != null && etContent.getText() != null) len += etContent.getText().length();
            if (etTitle != null && etTitle.getText() != null) len += etTitle.getText().length();
            if (len > 100000) return 15;
            if (len > 30000) return 25;
            return 50;
        } catch (Exception e) {
            return 30;
        }
    }

    private void pushToUndoStack(EditAction action) {
        if (action == null) return;
        int max = getMaxHistoryLimit();
        while (undoStack.size() >= max) {
            undoStack.pollLast();
        }
        undoStack.push(action);
    }

    private void onEditTextChanged(int target, String beforeText, String afterText,
                                  int beforeSelStart, int beforeSelEnd,
                                  int afterSelStart, int afterSelEnd,
                                  int beforeScrollY, int afterScrollY,
                                  int start, int count, int after) {
        if (ignoreTextChange) return;
        if (beforeText == null) beforeText = "";
        if (afterText == null) afterText = "";
        if (beforeText.equals(afterText)) return;

        int netDelta = after - count;
        int actionType;
        if (netDelta > 0 && after <= 2 && count <= 1) {
            actionType = EditAction.ACTION_INSERT;
        } else if (netDelta < 0 && count <= 2 && after == 0) {
            actionType = EditAction.ACTION_DELETE;
        } else {
            actionType = EditAction.ACTION_REPLACE;
        }

        boolean canMerge = false;
        if (activeBatchAction != null && activeBatchAction.target == target && activeBatchAction.actionType == actionType) {
            if (actionType == EditAction.ACTION_INSERT) {
                if (Math.abs(start - activeBatchAction.afterSelStart) <= 2) {
                    canMerge = true;
                }
            } else if (actionType == EditAction.ACTION_DELETE) {
                if (Math.abs(start - activeBatchAction.afterSelStart) <= 2) {
                    canMerge = true;
                }
            }
        }

        if (canMerge && activeBatchAction != null) {
            activeBatchAction.afterText = afterText;
            activeBatchAction.afterSelStart = afterSelStart;
            activeBatchAction.afterSelEnd = afterSelEnd;
            activeBatchAction.afterScrollY = afterScrollY;

            if (actionType == EditAction.ACTION_INSERT && after == 1 && start < afterText.length() && afterText.charAt(start) == '\n') {
                finalizeActiveBatch();
            } else {
                scheduleBatchFinalizeDebounced();
            }
        } else {
            finalizeActiveBatch();

            EditAction newAction = new EditAction(
                    target,
                    beforeText,
                    afterText,
                    beforeSelStart,
                    beforeSelEnd,
                    afterSelStart,
                    afterSelEnd,
                    beforeScrollY,
                    afterScrollY,
                    actionType
            );

            pushToUndoStack(newAction);
            redoStack.clear();

            if (actionType != EditAction.ACTION_REPLACE) {
                activeBatchAction = newAction;
                if (actionType == EditAction.ACTION_INSERT && after == 1 && start < afterText.length() && afterText.charAt(start) == '\n') {
                    finalizeActiveBatch();
                } else {
                    scheduleBatchFinalizeDebounced();
                }
            }
        }

        updateUndoButtons();
    }

    private void doUndo() {
        if (!ensureEditableMode(getString(R.string.edit_note_read_mode_enabled))) return;
        finalizeActiveBatch();

        while (!undoStack.isEmpty()) {
            EditAction action = undoStack.pop();
            if (action == null) continue;

            EditText targetEt = (action.target == EditAction.TARGET_TITLE) ? etTitle : etContent;
            if (targetEt == null) continue;

            String currentText = targetEt.getText() != null ? targetEt.getText().toString() : "";
            if (action.beforeText.equals(currentText) && !undoStack.isEmpty()) {
                continue;
            }

            redoStack.push(action);
            int max = getMaxHistoryLimit();
            while (redoStack.size() > max) {
                redoStack.pollLast();
            }

            applyEditState(targetEt, action.target, action.beforeText, action.beforeSelStart, action.beforeSelEnd, action.beforeScrollY);
            break;
        }

        updateUndoButtons();
    }

    private void doRedo() {
        if (!ensureEditableMode(getString(R.string.edit_note_read_mode_enabled))) return;
        finalizeActiveBatch();

        while (!redoStack.isEmpty()) {
            EditAction action = redoStack.pop();
            if (action == null) continue;

            EditText targetEt = (action.target == EditAction.TARGET_TITLE) ? etTitle : etContent;
            if (targetEt == null) continue;

            String currentText = targetEt.getText() != null ? targetEt.getText().toString() : "";
            if (action.afterText.equals(currentText) && !redoStack.isEmpty()) {
                continue;
            }

            pushToUndoStack(action);

            applyEditState(targetEt, action.target, action.afterText, action.afterSelStart, action.afterSelEnd, action.afterScrollY);
            break;
        }

        updateUndoButtons();
    }

    private void applyEditState(EditText targetEt, int targetType, String text, int selStart, int selEnd, int scrollY) {
        if (targetEt == null) return;
        String safeText = text != null ? text : "";

        ignoreTextChange = true;
        try {
            if (!targetEt.isFocused()) {
                targetEt.requestFocus();
            }

            targetEt.setText(safeText);
            int len = safeText.length();
            int s = Math.max(0, Math.min(selStart, len));
            int e = Math.max(0, Math.min(selEnd, len));
            targetEt.setSelection(s, e);

            if (targetType == EditAction.TARGET_CONTENT && etContent != null) {
                if (scrollY >= 0) {
                    etContent.scrollTo(0, scrollY);
                }
            }

            dirty = true;
            scheduleAutosaveDebounced();
        } catch (Exception ignored) {
        } finally {
            ignoreTextChange = false;
        }
    }

    private void updateUndoButtons() {
        boolean canUndo = !readMode && (!undoStack.isEmpty() || activeBatchAction != null);
        boolean canRedo = !readMode && !redoStack.isEmpty();

        if (btnUndo != null) {
            btnUndo.setAlpha(canUndo ? 1.0f : 0.4f);
            btnUndo.setEnabled(canUndo);
        }
        if (btnRedo != null) {
            btnRedo.setAlpha(canRedo ? 1.0f : 0.4f);
            btnRedo.setEnabled(canRedo);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean isCtrlPressed = (event.getMetaState() & KeyEvent.META_CTRL_ON) != 0 || event.isCtrlPressed();
            boolean isShiftPressed = (event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0 || event.isShiftPressed();
            int keyCode = event.getKeyCode();

            if (isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_Z) {
                    if (isShiftPressed) {
                        if (!readMode && !redoStack.isEmpty()) {
                            HapticUtils.light(btnRedo != null ? btnRedo : etContent);
                            doRedo();
                            return true;
                        }
                    } else {
                        if (!readMode && (!undoStack.isEmpty() || activeBatchAction != null)) {
                            HapticUtils.light(btnUndo != null ? btnUndo : etContent);
                            doUndo();
                            return true;
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_Y) {
                    if (!readMode && !redoStack.isEmpty()) {
                        HapticUtils.light(btnRedo != null ? btnRedo : etContent);
                        doRedo();
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void setReadMode(boolean enabled, boolean fromUser) {
        if (sourceFilter == 5) {
            enabled = true; // В корзине всегда только чтение
        } else if (!readModeFeatureEnabled) {
            enabled = false;
        }

        if (readMode == enabled && uiReady) {
            updateReadModeUi();
            return;
        }
        readMode = enabled;

        // ... existing code for etTitle and etContent listeners ...
        if (etTitle != null) {
            etTitle.setEnabled(true);
            etTitle.setClickable(true);
            etTitle.setLongClickable(true);
            etTitle.setTextIsSelectable(enabled);
            etTitle.setCursorVisible(!enabled);
            etTitle.setShowSoftInputOnFocus(!enabled);
            etTitle.setFocusable(true);
            etTitle.setFocusableInTouchMode(true);
            if (enabled) {
                etTitle.setKeyListener(null);
            } else {
                etTitle.setTextIsSelectable(false);
                etTitle.setKeyListener(titleKeyListener);
                if (titleInputType != 0) etTitle.setRawInputType(titleInputType);
                etTitle.setMovementMethod(ArrowKeyMovementMethod.getInstance());
                etTitle.setCursorVisible(true);
            }
        }
        if (etContent != null) {
            etContent.setEnabled(true);
            etContent.setClickable(true);
            etContent.setLongClickable(true);
            etContent.setTextIsSelectable(enabled);
            etContent.setCursorVisible(!enabled);
            etContent.setShowSoftInputOnFocus(!enabled);
            etContent.setFocusable(true);
            etContent.setFocusableInTouchMode(true);
            if (enabled) {
                etContent.setKeyListener(null);
            } else {
                etContent.setTextIsSelectable(false);
                etContent.setKeyListener(contentKeyListener);
                if (contentInputType != 0) etContent.setRawInputType(contentInputType);
                etContent.setMovementMethod(ArrowKeyMovementMethod.getInstance());
                etContent.setCursorVisible(true);
            }
        }
        if (checklistAdapter != null) checklistAdapter.setReadOnly(enabled);
        if (attachmentsAdapter != null) attachmentsAdapter.setReadOnly(enabled);
        if (btnAddItem != null) btnAddItem.setVisibility(enabled ? View.GONE : (note.getType() == Note.TYPE_CHECKLIST ? View.VISIBLE : View.GONE));

        setEditorActionEnabled(btnPin, !enabled);
        setEditorActionEnabled(btnFav, !enabled);
        setEditorActionEnabled(btnReminderQuick, !enabled);
        setEditorActionEnabled(btnFindReplace, !enabled);
        setEditorActionEnabled(btnPalette, !enabled);
        setEditorActionEnabled(btnCheck, !enabled);
        setEditorActionEnabled(btnAttach, !enabled);
        // Кнопка More всегда активна, даже в режиме чтения, но внутри меню фильтруется
        setEditorActionEnabled(btnMore, true); 

        if (tvCategory != null) {
            tvCategory.setEnabled(!enabled);
            tvCategory.setAlpha(enabled ? 0.65f : 1f);
        }
        if (tvReminder != null) {
            tvReminder.setEnabled(!enabled);
            tvReminder.setAlpha(enabled ? 0.65f : 1f);
        }
        if (rvAttachments != null) rvAttachments.setAlpha(enabled ? 0.88f : 1f);

        if (enabled) {
            View focused = getCurrentFocus();
            exitFindMode();
            if (etTitle != null) etTitle.clearFocus();
            if (etContent != null) etContent.clearFocus();
            hideKeyboard(focused);
        } else if (fromUser) {
            focusPreferredEditorField();
            showKeyboardForCurrentEditor();
        }
        setTextTapToFocusEnabled(!enabled && note.getType() == Note.TYPE_TEXT);
        updateUndoButtons();
        updatePinFav();
        updateReadModeUi();

        if (fromUser && root != null && sourceFilter != 5) {
            Snackbar.make(root, enabled
                    ? getString(R.string.edit_note_read_mode_enabled)
                    : getString(R.string.edit_note_read_mode_disabled),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    private void setEditorActionEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setClickable(enabled);
        view.setAlpha(enabled ? 1f : 0.38f);
    }

    private void updateReadModeUi() {
        if (btnReadMode != null) {
            btnReadMode.setVisibility((readModeFeatureEnabled && sourceFilter != 5) ? View.VISIBLE : View.GONE);
            btnReadMode.setImageResource(readMode ? R.drawable.ic_hidden : R.drawable.ic_eye);
            btnReadMode.setColorFilter(ContextCompat.getColor(this,
                    readMode ? R.color.gold_primary : R.color.text_secondary));
            btnReadMode.setContentDescription(readMode ? getString(R.string.edit_mode_toggle_desc) : getString(R.string.edit_note_read_mode_on));
        }
        
        if (readModeRow != null) {
            // Желтая полоска "Режим чтения" показывается только если это НЕ корзина
            readModeRow.setVisibility(readModeFeatureEnabled && readMode && sourceFilter != 5 ? View.VISIBLE : View.GONE);
        }
        
        if (searchBarRow != null) {
            if (readMode) searchBarRow.setVisibility(View.GONE);
            else if (isFindMode) searchBarRow.setVisibility(View.VISIBLE);
        }
        
        if (bottomActionsRow != null) {
            // В корзине скрываем ВСЕ кнопки действия, кроме More.
            boolean inTrash = (sourceFilter == 5);
            if (inTrash) {
                bottomActionsRow.setVisibility(View.VISIBLE);
                if (btnUndo != null) btnUndo.setVisibility(View.GONE);
                if (btnRedo != null) btnRedo.setVisibility(View.GONE);
                if (btnPalette != null) btnPalette.setVisibility(View.GONE);
                if (btnCheck != null) btnCheck.setVisibility(View.GONE);
                if (btnAttach != null) btnAttach.setVisibility(View.GONE);
                if (btnReminderQuick != null) btnReminderQuick.setVisibility(View.GONE);
                if (btnFindReplace != null) btnFindReplace.setVisibility(View.GONE);
                if (btnPin != null) btnPin.setVisibility(View.GONE);
                if (btnFav != null) btnFav.setVisibility(View.GONE);
                if (btnMore != null) btnMore.setVisibility(View.VISIBLE);
            } else {
                bottomActionsRow.setVisibility(readMode || isFindMode ? View.GONE : View.VISIBLE);
                // Восстанавливаем видимость для обычного режима
                if (btnUndo != null) btnUndo.setVisibility(View.VISIBLE);
                if (btnRedo != null) btnRedo.setVisibility(View.VISIBLE);
                if (btnPalette != null) btnPalette.setVisibility(View.VISIBLE);
                if (btnCheck != null) btnCheck.setVisibility(View.VISIBLE);
                if (btnAttach != null) btnAttach.setVisibility(View.VISIBLE);
                if (btnReminderQuick != null) btnReminderQuick.setVisibility(View.VISIBLE);
                if (btnFindReplace != null) btnFindReplace.setVisibility(View.VISIBLE);
                if (btnPin != null) btnPin.setVisibility(View.VISIBLE);
                if (btnFav != null) btnFav.setVisibility(View.VISIBLE);
                if (btnMore != null) btnMore.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideKeyboard() {
        hideKeyboard(getCurrentFocus());
    }

    private void hideKeyboard(View preferredTarget) {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View target = preferredTarget != null ? preferredTarget
                    : (etContent != null ? etContent : (etTitle != null ? etTitle : root));
            if (imm != null && target != null) {
                imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
            }
            if (android.os.Build.VERSION.SDK_INT >= 30 && root != null && root.getWindowInsetsController() != null) {
                root.getWindowInsetsController().hide(android.view.WindowInsets.Type.ime());
            }
        } catch (Exception ignored) {}
    }

    private void focusPreferredEditorField() {
        if (readMode) return;
        if (note != null && note.getType() == Note.TYPE_TEXT && etContent != null) {
            etContent.setCursorVisible(true);
            etContent.requestFocus();
            etContent.post(() -> {
                try { etContent.setSelection(etContent.getText().length()); } catch (Exception ignored) {}
            });
            return;
        }
        if (etTitle != null) {
            etTitle.setCursorVisible(true);
            etTitle.requestFocus();
            etTitle.post(() -> {
                try { etTitle.setSelection(etTitle.getText().length()); } catch (Exception ignored) {}
            });
        }
    }

    private void showKeyboardForCurrentEditor() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View target = getCurrentFocus();
            if (imm != null && target != null) {
                imm.showSoftInput(target, 0);
            }
        } catch (Exception ignored) {}
    }

    private boolean ensureEditableMode(String message) {
        if (!readMode) return true;
        if (root != null) {
            Snackbar.make(root, message == null ? getString(R.string.edit_note_switch_edit_first) : message, Snackbar.LENGTH_SHORT).show();
        }
        return false;
    }

    private void updatePinFav() {
        btnPin.setImageResource(note.isPinned() ? R.drawable.ic_pin : R.drawable.ic_pin_outline);
        btnPin.setColorFilter(note.isPinned()
                ? ContextCompat.getColor(this, R.color.gold_primary)
                : ContextCompat.getColor(this, R.color.text_secondary));
        btnFav.setImageResource(note.isLocked() ? R.drawable.ic_lock : R.drawable.ic_lock_open);
        btnFav.setColorFilter(note.isLocked()
                ? ContextCompat.getColor(this, R.color.gold_primary)
                : ContextCompat.getColor(this, R.color.text_secondary));
    }

    private void updateCategoryLabel() {
        // Legacy sync version, kept for quick calls but delegates to async if needed
        if (note.getCategoryId() <= 0) {
            tvCategory.setText(R.string.no_category);
        } else {
            // Try quick, but if DB heavy, async version should be used
            try {
                Category c = repo.getCategoryById(note.getCategoryId());
                tvCategory.setText(c != null ? c.getName() : getString(R.string.no_category));
            } catch (Exception e) {
                tvCategory.setText(R.string.no_category);
            }
        }
    }

    private void updateCategoryLabelAsync() {
        if (tvCategory == null || note == null) return;
        if (note.getCategoryId() <= 0) {
            tvCategory.setText(R.string.no_category);
            return;
        }
        final long catId = note.getCategoryId();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Category c = repo.getCategoryById(catId);
                mainHandler.post(() -> {
                    if (tvCategory != null) {
                        tvCategory.setText(c != null ? c.getName() : getString(R.string.no_category));
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    if (tvCategory != null) tvCategory.setText(R.string.no_category);
                });
            }
        });
    }

    private void updateReminderLabel() {
        if (note.getReminderAt() > 0) {
            tvReminder.setVisibility(View.VISIBLE);
            tvReminder.setText(getString(R.string.reminder_prefix, TimeUtils.formatFullDate(note.getReminderAt())));
        } else {
            tvReminder.setVisibility(View.GONE);
        }
    }

    private void applyColor() {
        int color = ColorUtils.getNoteBgColor(this, note.getColorIndex());
        root.setBackgroundColor(color);
        bottomBar.setBackgroundColor(color);
        // Панель выбора вложений заменяет обычный верхний toolbar при long-click.
        // Поэтому ей тоже нужно применять текущий цвет заметки, иначе после смены
        // цвета заметки selection toolbar остаётся со старым/дефолтным фоном.
        if (selectionBarAttachments != null) {
            selectionBarAttachments.setBackgroundColor(color);
        }
    }

    private void applyTextSize(int sizeSp) {
        note.setTextSize(sizeSp);
        if (etContent != null) {
            etContent.setTextSize(sizeSp);
        }
        if (etTitle != null) {
            // Заголовок чуть крупнее основного текста (+6sp).
            etTitle.setTextSize(sizeSp + 6f);
        }
    }

    private void showTextMode() {
        etContent.setVisibility(View.VISIBLE);
        rvChecklist.setVisibility(View.GONE);
        btnAddItem.setVisibility(View.GONE);
        note.setType(Note.TYPE_TEXT);
        setTextTapToFocusEnabled(!readMode);
    }

    private void showChecklistMode() {
        etContent.setVisibility(View.GONE);
        rvChecklist.setVisibility(View.VISIBLE);
        btnAddItem.setVisibility(View.VISIBLE);
        setTextTapToFocusEnabled(false);
        List<ChecklistItem> items = note.getChecklistItems();
        if (items.isEmpty()) items.add(new ChecklistItem("", false));
        checklistAdapter = new ChecklistEditorAdapter(items, () -> { dirty = true; });
        rvChecklist.setLayoutManager(new LinearLayoutManager(this));
        rvChecklist.setItemAnimator(null);
        rvChecklist.setAdapter(checklistAdapter);
        // Drag&Drop: long-press отключён, перетаскивание стартует через handle
        ItemTouchHelper checklistTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder src,
                                  @NonNull RecyclerView.ViewHolder dst) {
                int from = src.getBindingAdapterPosition();
                int to = dst.getBindingAdapterPosition();
                if (checklistAdapter != null) checklistAdapter.onItemMove(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) { /* not used */ }
        });
        checklistTouchHelper.attachToRecyclerView(rvChecklist);
        checklistAdapter.attachItemTouchHelper(checklistTouchHelper);
        rvChecklist.post(() -> {
            rvChecklist.requestLayout();
            View scrollContent = findViewById(R.id.scroll_content);
            if (scrollContent != null) scrollContent.requestLayout();
        });
        note.setType(Note.TYPE_CHECKLIST);
    }

    private void setTextTapToFocusEnabled(boolean enabled) {
        View contentContainer = findViewById(R.id.content_container);
        View scrollContent = findViewById(R.id.scroll_content);
        View.OnClickListener listener = enabled ? v -> focusTextEditor() : null;
        if (contentContainer != null) contentContainer.setOnClickListener(listener);
        if (scrollContent != null) scrollContent.setOnClickListener(listener);
    }

    private void focusTextEditor() {
        if (readMode || etContent == null) return;
        etContent.requestFocus();
        etContent.setSelection(etContent.getText().length());
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etContent, 0);
    }

    private void toggleChecklistMode() {
        if (!ensureEditableMode(null)) return;
        if (note.getType() == Note.TYPE_TEXT) {
            String content = etContent.getText().toString();
            List<ChecklistItem> items = new ArrayList<>();
            if (!content.trim().isEmpty()) {
                for (String line : content.split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty()) items.add(new ChecklistItem(t, false));
                }
            }
            note.setChecklistItems(items);
            showChecklistMode();
        } else {
            StringBuilder sb = new StringBuilder();
            List<ChecklistItem> items = checklistAdapter.getItems();
            for (int i = 0; i < items.size(); i++) {
                sb.append(items.get(i).text);
                if (i < items.size() - 1) sb.append('\n');
            }
            note.setContent(sb.toString());
            etContent.setText(sb.toString());
            showTextMode();
        }
        dirty = true;
    }

    private void populateNoteFromUi() {
        // Capture UI data safely; may be called from background thread, so use try
        try {
            if (etTitle != null && etTitle.getText() != null) note.setTitle(etTitle.getText().toString());
        } catch (Exception ignored) {}
        try {
            if (note.getType() == Note.TYPE_TEXT && etContent != null && etContent.getText() != null) {
                note.setContent(etContent.getText().toString());
            } else if (checklistAdapter != null) {
                note.setChecklistItems(checklistAdapter.getItems());
            }
        } catch (Exception ignored) {}
        note.setTextSize(note.getTextSize());
    }

    private void populateNoteFromUiInto(Note target, String title, String content, String checklistJson) {
        target.setTitle(title != null ? title : "");
        if (target.getType() == Note.TYPE_TEXT) {
            target.setContent(content != null ? content : "");
        } else if (checklistJson != null) {
            target.setChecklistJson(checklistJson);
        }
        target.setTextSize(target.getTextSize());
    }

    private boolean hasPendingChanges() {
        // Lightweight version: avoid heavy toString unless dirty
        // First check simple flags
        if (dirty || !pendingDeleteAttachmentFiles.isEmpty()) return true;
        if (startedAsNew && note.getId() == 0) {
            // For new note, check if title/content non-empty without full populate
            try {
                String t = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : "";
                String c = etContent != null && etContent.getText() != null ? etContent.getText().toString() : "";
                if (!t.trim().isEmpty() || !c.trim().isEmpty()) return true;
                if (note.getType() == Note.TYPE_CHECKLIST && checklistAdapter != null && !checklistAdapter.getItems().isEmpty()) {
                    // Check if any item has text
                    for (ChecklistItem it : checklistAdapter.getItems()) {
                        if (it != null && it.text != null && !it.text.trim().isEmpty()) return true;
                    }
                }
            } catch (Exception ignored) {}
            return false;
        }
        try {
            String curTitle = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : note.getTitle();
            String curContent;
            String curChecklist;
            if (note.getType() == Note.TYPE_TEXT) {
                curContent = etContent != null && etContent.getText() != null ? etContent.getText().toString() : note.getContent();
                curChecklist = initialChecklistJson;
            } else {
                curContent = initialContent;
                if (checklistAdapter != null) {
                    curChecklist = ChecklistItem.toJson(checklistAdapter.getItems());
                } else {
                    curChecklist = note.getChecklistJson();
                }
            }
            if (!initialTitle.equals(curTitle)) return true;
            if (note.getType() == Note.TYPE_TEXT && !initialContent.equals(curContent)) return true;
            if (note.getType() == Note.TYPE_CHECKLIST && !initialChecklistJson.equals(curChecklist)) return true;
            if (!initialAttachmentsJson.equals(note.getAttachmentsJson())) return true;
            if (initialColor != note.getColorIndex()) return true;
            if (initialCategoryId != note.getCategoryId()) return true;
            if (initialPinned != note.isPinned()) return true;
            if (initialFavorite != note.isFavorite()) return true;
            if (initialLocked != note.isLocked()) return true;
            if (initialReminder != note.getReminderAt()) return true;
            if (initialType != note.getType()) return true;
            if (initialTextSize != note.getTextSize()) return true;
        } catch (Exception ignored) {
            return dirty;
        }
        return false;
    }

    // Legacy sync saveNote now delegates to async to keep UI free
    private boolean saveNote(boolean silent) {
        // Capture current UI state
        String title;
        String content;
        String checklistJson = null;
        try {
            title = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : note.getTitle();
        } catch (Exception e) {
            title = note.getTitle();
        }
        try {
            if (note.getType() == Note.TYPE_TEXT) {
                content = etContent != null && etContent.getText() != null ? etContent.getText().toString() : note.getContent();
            } else {
                content = note.getContent();
                if (checklistAdapter != null) {
                    checklistJson = ChecklistItem.toJson(checklistAdapter.getItems());
                }
            }
        } catch (Exception e) {
            content = note.getContent();
        }

        // Empty check
        boolean isEmpty = (title == null || title.trim().isEmpty())
                && (note.getType() == Note.TYPE_TEXT ? (content == null || content.trim().isEmpty()) : true)
                && (note.getType() == Note.TYPE_CHECKLIST ? (checklistJson == null || checklistJson.equals("[]") || checklistJson.trim().isEmpty()) : true)
                && note.getAttachments().isEmpty();

        // For empty new note, just clear pending deletes
        if (isEmpty && isNew) {
            pendingDeleteAttachmentFiles.clear();
            if (!silent && root != null) Snackbar.make(root, R.string.msg_empty_note, Snackbar.LENGTH_SHORT).show();
            return false;
        }

        final String finalTitle = title;
        final String finalContent = content;
        final String finalChecklistJson = checklistJson;
        final boolean finalSilent = silent;

        // Offload DB to diskIO, but update UI immediately for responsiveness
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Update in-memory note
                note.setTitle(finalTitle);
                if (note.getType() == Note.TYPE_TEXT) {
                    note.setContent(finalContent);
                } else if (finalChecklistJson != null) {
                    note.setChecklistJson(finalChecklistJson);
                }

                if (note.isEmpty()) {
                    if (!isNew) {
                        if (!pendingDeleteAttachmentFiles.isEmpty()) {
                            DeleteFilesHelper.deleteFiles(EditNoteActivity.this, new ArrayList<>(pendingDeleteAttachmentFiles));
                            mainHandler.post(() -> pendingDeleteAttachmentFiles.clear());
                        }
                        repo.updateNote(note);
                    }
                } else {
                    if (isNew) {
                        long newId = repo.addNote(note);
                        mainHandler.post(() -> {
                            if (note.getId() == 0) {
                                note.setId(newId);
                                isNew = false;
                            }
                        });
                    } else {
                        repo.updateNote(note);
                    }
                    mainHandler.post(() -> {
                        flushPendingAttachmentDeletes();
                    });
                }

                mainHandler.post(() -> {
                    initialTitle = note.getTitle();
                    initialContent = note.getContent();
                    initialChecklistJson = note.getChecklistJson();
                    initialAttachmentsJson = note.getAttachmentsJson();
                    initialColor = note.getColorIndex();
                    initialCategoryId = note.getCategoryId();
                    initialPinned = note.isPinned();
                    initialFavorite = note.isFavorite();
                    initialLocked = note.isLocked();
                    initialReminder = note.getReminderAt();
                    initialType = note.getType();
                    initialTextSize = note.getTextSize();
                    lastSavedTitle = finalTitle;
                    lastSavedContent = finalContent != null ? finalContent : "";
                    dirty = false;
                    try {
                        if (note.getReminderAt() > System.currentTimeMillis()) {
                            ReminderUtils.schedule(EditNoteActivity.this, note.getId(), note.getReminderAt());
                        } else {
                            ReminderUtils.cancel(EditNoteActivity.this, note.getId());
                        }
                    } catch (Exception ignored) {}
                    if (!finalSilent && root != null) {
                        Snackbar.make(root, R.string.msg_saved, Snackbar.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                try { android.util.Log.e("EditNote", "saveNote failed", e); } catch (Exception ignored) {}
            }
        });
        // Optimistic return
        return true;
    }

    private void commitIfAutoSaveEnabled() {
        dirty = true;
        if (prefs.isConfirmSaveOnExitEnabled()) return;
        // Debounced autosave instead of immediate save
        scheduleAutosaveDebounced();
    }

    private void forceSaveNew() {
        // Capture UI quickly
        String title;
        String content;
        try { title = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : ""; } catch (Exception e) { title = ""; }
        try { content = etContent != null && etContent.getText() != null ? etContent.getText().toString() : ""; } catch (Exception e) { content = ""; }
        final String ft = title;
        final String fc = content;
        // For attachment flow, we need note ID synchronously? We will do blocking diskIO but on background? However startCopy expects ID immediately.
        // So we do sync in diskIO but wait? To avoid UI thread DB, we will run on diskIO and post result? But original code needed ID before starting service.
        // We implement synchronous save on diskIO thread but called from UI thread - we will do it via repo directly only if not large? For simplicity, keep old behavior but offload to allow UI to continue?
        // To keep compatibility, we do immediate save on current thread only if small, otherwise we try to save synchronously in background and busy wait? Better to do sync save but in diskIO with latch.
        // For minimal change, we keep sync save for this path because it's needed for attachment copy service to have ID.
        // However we can still avoid UI lag by using AppExecutors but blocking with future for short time.
        // We'll attempt async with latch.
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final long[] newIdHolder = new long[1];
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                note.setTitle(ft);
                if (note.getType() == Note.TYPE_TEXT) note.setContent(fc);
                if (isNew) {
                    long nid = repo.addNote(note);
                    newIdHolder[0] = nid;
                    mainHandler.post(() -> {
                        if (note.getId() == 0) {
                            note.setId(nid);
                            isNew = false;
                        }
                    });
                } else {
                    repo.updateNote(note);
                    newIdHolder[0] = note.getId();
                }
            } catch (Exception e) {
                try { android.util.Log.e("EditNote", "forceSaveNew failed", e); } catch (Exception ignored) {}
            } finally {
                latch.countDown();
            }
        });
        try {
            // Wait max 2 seconds to get ID, avoid ANR for large text? 2 sec is okay.
            latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (newIdHolder[0] > 0 && note.getId() == 0) {
                note.setId(newIdHolder[0]);
                isNew = false;
            }
        } catch (InterruptedException ignored) {}
    }

    private void saveAndFinish() {
        if (copyInProgress) {
            ConfirmSheet.show(this, getString(R.string.edit_note_copying_files_title),
                    getString(R.string.edit_note_files_copying_msg),
                    getString(R.string.edit_note_exit), getString(R.string.edit_note_wait), true, R.drawable.ic_info, () -> {
                        saveAndExitNow();
                    });
            return;
        }
        if (prefs.isConfirmSaveOnExitEnabled() && hasPendingChanges()) {
            ConfirmSheet.show(this,
                    getString(R.string.confirm_save_title),
                    getString(R.string.edit_note_choose_action_before_exit),
                    getString(R.string.save),
                    getString(R.string.edit_note_not_saving),
                    false,
                    R.drawable.ic_save,
                    this::saveAndExitNow,
                    this::discardAndExitNow);
            return;
        }
        saveAndExitNow();
    }

    private void saveAndExitNow() {
        suppressAutoSaveOnStop = true;
        cancelPendingRunnables();
        // Capture UI data
        String title;
        String content;
        String checklistJson = null;
        try { title = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : note.getTitle(); } catch (Exception e) { title = note.getTitle(); }
        try {
            if (note.getType() == Note.TYPE_TEXT) {
                content = etContent != null && etContent.getText() != null ? etContent.getText().toString() : note.getContent();
            } else {
                content = note.getContent();
                if (checklistAdapter != null) checklistJson = ChecklistItem.toJson(checklistAdapter.getItems());
            }
        } catch (Exception e) { content = note.getContent(); }

        final String ft = title;
        final String fc = content;
        final String fcl = checklistJson;

        // Save off UI thread then finish
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                note.setTitle(ft);
                if (note.getType() == Note.TYPE_TEXT) note.setContent(fc);
                else if (fcl != null) note.setChecklistJson(fcl);

                if (!note.isEmpty()) {
                    if (isNew) {
                        repo.addNote(note);
                    } else {
                        repo.updateNote(note);
                    }
                    if (!pendingDeleteAttachmentFiles.isEmpty()) {
                        DeleteFilesHelper.deleteFiles(EditNoteActivity.this, new ArrayList<>(pendingDeleteAttachmentFiles));
                    }
                    if (note.getReminderAt() > System.currentTimeMillis()) {
                        ReminderUtils.schedule(EditNoteActivity.this, note.getId(), note.getReminderAt());
                    } else {
                        ReminderUtils.cancel(EditNoteActivity.this, note.getId());
                    }
                } else {
                    if (!isNew && !pendingDeleteAttachmentFiles.isEmpty()) {
                        DeleteFilesHelper.deleteFiles(EditNoteActivity.this, new ArrayList<>(pendingDeleteAttachmentFiles));
                        repo.updateNote(note);
                    }
                }
            } catch (Exception e) {
                try { android.util.Log.e("EditNote", "saveAndExitNow failed", e); } catch (Exception ignored) {}
            } finally {
                mainHandler.post(() -> {
                    finish();
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                });
            }
        });
    }

    private void discardAndExitNow() {
        suppressAutoSaveOnStop = true;
        cancelPendingRunnables();
        // Offload restore to diskIO then finish
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                restoreInitialSnapshotAndCleanupInternal();
            } catch (Exception e) {
                try { android.util.Log.e("EditNote", "discard failed", e); } catch (Exception ignored) {}
            } finally {
                mainHandler.post(() -> {
                    finish();
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                });
            }
        });
    }

    private void restoreInitialSnapshotAndCleanupInternal() {
        try {
            if (note == null) return;
            Set<String> initialFiles = toFileSet(initialAttachmentsJson);

            if (startedAsNew) {
                List<String> allToDelete = new ArrayList<>();
                if (note.getId() > 0) {
                    Note dbNote = repo.getNoteById(note.getId());
                    if (dbNote != null) {
                        allToDelete.addAll(toFileSet(dbNote.getAttachmentsJson()));
                    }
                    List<String> dbFiles = repo.deleteForever(note.getId());
                    for (String f : dbFiles) {
                        if (!allToDelete.contains(f)) allToDelete.add(f);
                    }
                }
                for (String f : pendingDeleteAttachmentFiles) {
                    if (!allToDelete.contains(f)) allToDelete.add(f);
                }
                for (Attachment a : note.getAttachments()) {
                    if (a.fileName != null && !initialFiles.contains(a.fileName) && !allToDelete.contains(a.fileName)) {
                        allToDelete.add(a.fileName);
                    }
                }
                DeleteFilesHelper.deleteFiles(this, allToDelete);
                mainHandler.post(() -> pendingDeleteAttachmentFiles.clear());
                if (pendingCameraFile != null && pendingCameraFile.exists()) {
                    try { pendingCameraFile.delete(); } catch (Exception ignored) {}
                }
                return;
            }

            Note dbNote = repo.getNoteById(note.getId());
            Set<String> currentFiles = new HashSet<>();
            if (dbNote != null) {
                currentFiles = toFileSet(dbNote.getAttachmentsJson());
            } else {
                for (Attachment a : note.getAttachments()) {
                    if (a.fileName != null) currentFiles.add(a.fileName);
                }
            }

            List<String> filesToDelete = new ArrayList<>();
            for (String f : currentFiles) {
                if (!initialFiles.contains(f)) filesToDelete.add(f);
            }
            for (String f : pendingDeleteAttachmentFiles) {
                if (!initialFiles.contains(f) && !filesToDelete.contains(f)) {
                    filesToDelete.add(f);
                }
            }
            if (!filesToDelete.isEmpty()) {
                DeleteFilesHelper.deleteFiles(this, filesToDelete);
            }

            note.setTitle(initialTitle);
            note.setContent(initialContent);
            note.setChecklistJson(initialChecklistJson);
            note.setAttachmentsJson(initialAttachmentsJson);
            note.setColorIndex(initialColor);
            note.setCategoryId(initialCategoryId);
            note.setPinned(initialPinned);
            note.setFavorite(initialFavorite);
            note.setLocked(initialLocked);
            note.setReminderAt(initialReminder);
            note.setType(initialType);
            note.setTextSize(initialTextSize);
            repo.updateNote(note);
            mainHandler.post(() -> {
                pendingDeleteAttachmentFiles.clear();
                dirty = false;
            });
        } catch (Exception e) {
            try { android.util.Log.e("EditNote", "restoreInternal failed", e); } catch (Exception ignored) {}
        }
    }

    private void restoreInitialSnapshotAndCleanup() {
        // Keep old method for compatibility but delegate to internal
        restoreInitialSnapshotAndCleanupInternal();
    }

    private Set<String> toFileSet(String attachmentsJson) {
        Set<String> set = new HashSet<>();
        for (Attachment a : Attachment.fromJson(attachmentsJson)) {
            if (a.fileName != null) set.add(a.fileName);
        }
        return set;
    }

    private void flushPendingAttachmentDeletes() {
        if (pendingDeleteAttachmentFiles.isEmpty()) return;
        DeleteFilesHelper.deleteFiles(this, new ArrayList<>(pendingDeleteAttachmentFiles));
        pendingDeleteAttachmentFiles.clear();
    }

    private void showMoreSheet() {
        // В корзине More работает всегда, в обычном режиме чтения - по правилам
        if (sourceFilter != 5) {
            if (!ensureEditableMode(null)) return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_edit_more, null);
        sheet.setContentView(v);

        TextView aShare = v.findViewById(R.id.action_share);
        TextView aDuplicate = v.findViewById(R.id.action_duplicate);
        TextView aArchive = v.findViewById(R.id.action_archive);
        TextView aDelete = v.findViewById(R.id.action_delete);
        TextView aConvert = v.findViewById(R.id.action_convert);
        TextView aFavorite = v.findViewById(R.id.action_favorite);
        TextView aTextSize = v.findViewById(R.id.action_text_size);
        TextView aReadAloud = v.findViewById(R.id.action_read_aloud);
        TextView aCopy = v.findViewById(R.id.action_copy);
        TextView aInfo = v.findViewById(R.id.action_info);
        TextView aRestore = v.findViewById(R.id.action_restore);

        boolean openedFromArchive = sourceFilter == 4;
        boolean openedFromTrash = sourceFilter == 5;

        if (openedFromTrash) {
            aShare.setVisibility(View.GONE);
            aDuplicate.setVisibility(View.GONE);
            aArchive.setVisibility(View.GONE);
            aConvert.setVisibility(View.GONE);
            aFavorite.setVisibility(View.GONE);
            aTextSize.setVisibility(View.GONE);
            aReadAloud.setVisibility(View.GONE);
            aCopy.setVisibility(View.GONE);
        }

        aConvert.setText(note.getType() == Note.TYPE_TEXT
                ? R.string.convert_to_checklist : R.string.convert_to_text);
        aFavorite.setText(note.isFavorite() ? getString(R.string.sheet_remove_favorite) : getString(R.string.sheet_add_favorite));
        aReadAloud.setText(isSpeakingAloud ? getString(R.string.edit_note_stop_reading) : getString(R.string.edit_note_read_aloud));

        aRestore.setVisibility(openedFromTrash ? View.VISIBLE : View.GONE);
        if (openedFromArchive) {
            aArchive.setVisibility(View.VISIBLE);
            aArchive.setText(R.string.unarchive);
            aArchive.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_unarchive_sheet, 0, 0, 0);
        } else if (openedFromTrash) {
            aArchive.setVisibility(View.GONE);
        } else {
            aArchive.setVisibility(View.VISIBLE);
            aArchive.setText(R.string.archive);
            aArchive.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_archive, 0, 0, 0);
        }

        if (openedFromTrash) {
            aDelete.setText(R.string.delete_forever);
            aDelete.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_delete_forever_sheet, 0, 0, 0);
        } else {
            aDelete.setText(R.string.delete);
            aDelete.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_delete, 0, 0, 0);
        }

        aShare.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            String body = etTitle.getText() + "\n\n" +
                    (note.getType() == Note.TYPE_TEXT ? etContent.getText() : note.getPreview());
            send.putExtra(Intent.EXTRA_TEXT, body);
            startActivity(Intent.createChooser(send, getString(R.string.share)));
        });

        aDuplicate.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            // Capture UI data on UI thread
            String title, content, checklistJson;
            try { title = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : note.getTitle(); } catch (Exception e) { title = note.getTitle(); }
            try {
                if (note.getType() == Note.TYPE_TEXT) content = etContent != null && etContent.getText() != null ? etContent.getText().toString() : note.getContent();
                else content = note.getContent();
                checklistJson = checklistAdapter != null ? ChecklistItem.toJson(checklistAdapter.getItems()) : note.getChecklistJson();
            } catch (Exception e) { content = note.getContent(); checklistJson = note.getChecklistJson(); }
            final String ft = title, fc = content, fcl = checklistJson;
            AppExecutors.getInstance().diskIO().execute(() -> {
                try {
                    Note dup = new Note();
                    dup.setTitle(ft);
                    dup.setContent(fc);
                    dup.setChecklistJson(fcl);
                    dup.setAttachmentsJson("[]");
                    dup.setType(note.getType());
                    dup.setColorIndex(note.getColorIndex());
                    dup.setCategoryId(note.getCategoryId());
                    dup.setTextSize(note.getTextSize());
                    repo.addNote(dup);
                    mainHandler.post(() -> {
                        if (root != null) Snackbar.make(root, R.string.msg_duplicated, Snackbar.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    try { android.util.Log.e("EditNote", "duplicate failed", e); } catch (Exception ignored) {}
                }
            });
        });

        aArchive.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            if (openedFromArchive) {
                AppExecutors.getInstance().diskIO().execute(() -> {
                    try { repo.unarchiveNote(note.getId()); } catch (Exception ignored) {}
                    mainHandler.post(() -> {
                        Intent res = new Intent();
                        res.putExtra(RESULT_ACTION, ACTION_UNARCHIVED);
                        res.putExtra(RESULT_NOTE_ID, note.getId());
                        setResult(RESULT_OK, res);
                        finish();
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                    });
                });
                return;
            }
            if (note.isLocked()) {
                Snackbar.make(root, getString(R.string.remove_protection_first), Snackbar.LENGTH_SHORT).show();
                return;
            }
            // Save then archive off UI thread
            String title, content, checklistJson;
            try { title = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString() : note.getTitle(); } catch (Exception e) { title = note.getTitle(); }
            try {
                if (note.getType() == Note.TYPE_TEXT) content = etContent != null && etContent.getText() != null ? etContent.getText().toString() : note.getContent();
                else content = note.getContent();
                checklistJson = checklistAdapter != null ? ChecklistItem.toJson(checklistAdapter.getItems()) : note.getChecklistJson();
            } catch (Exception e) { content = note.getContent(); checklistJson = note.getChecklistJson(); }
            final String ft = title, fc = content, fcl = checklistJson;
            AppExecutors.getInstance().diskIO().execute(() -> {
                try {
                    note.setTitle(ft);
                    if (note.getType() == Note.TYPE_TEXT) note.setContent(fc);
                    else if (fcl != null) note.setChecklistJson(fcl);
                    if (!note.isEmpty()) repo.updateNote(note);
                    repo.archiveNote(note.getId());
                } catch (Exception e) {
                    try { android.util.Log.e("EditNote", "archive failed", e); } catch (Exception ignored) {}
                }
                mainHandler.post(() -> {
                    Intent res = new Intent();
                    res.putExtra(RESULT_ACTION, ACTION_ARCHIVED);
                    res.putExtra(RESULT_NOTE_ID, note.getId());
                    setResult(RESULT_OK, res);
                    finish();
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                });
            });
        });

        aConvert.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            toggleChecklistMode();
        });

        aTextSize.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            showTextSizeSheet();
        });

        aReadAloud.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            readNoteAloud();
        });

        aCopy.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            String title = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();
            String body = note.getType() == Note.TYPE_TEXT
                    ? (etContent.getText() == null ? "" : etContent.getText().toString().trim())
                    : note.getPreview().trim();
            String textToCopy;
            if (!title.isEmpty() && !body.isEmpty()) {
                textToCopy = title + "\n\n" + body;
            } else if (!title.isEmpty()) {
                textToCopy = title;
            } else {
                textToCopy = body;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), textToCopy));
                Snackbar.make(root, getString(R.string.note_copied), Snackbar.LENGTH_SHORT).show();
            }
        });

        aInfo.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            showNoteInfoDialog();
        });

        aRestore.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            AppExecutors.getInstance().diskIO().execute(() -> {
                try { repo.restoreFromTrash(note.getId()); } catch (Exception ignored) {}
                mainHandler.post(() -> {
                    Intent res = new Intent();
                    res.putExtra(RESULT_ACTION, ACTION_RESTORED);
                    res.putExtra(RESULT_NOTE_ID, note.getId());
                    setResult(RESULT_OK, res);
                    finish();
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                });
            });
        });

        aFavorite.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            note.setFavorite(!note.isFavorite());
            commitIfAutoSaveEnabled();
            updatePinFav();
            Snackbar.make(root, note.isFavorite() ? getString(R.string.edit_note_added_to_favorites) : getString(R.string.edit_note_removed_from_favorites),
                    Snackbar.LENGTH_SHORT).show();
        });

        if (openedFromTrash) {
            aShare.setVisibility(View.GONE);
            aDuplicate.setVisibility(View.GONE);
            aArchive.setVisibility(View.GONE);
            aConvert.setVisibility(View.GONE);
            aFavorite.setVisibility(View.GONE);
            aTextSize.setVisibility(View.GONE);
            aReadAloud.setVisibility(View.GONE);
            aCopy.setVisibility(View.GONE);
            aInfo.setVisibility(View.VISIBLE);
            aRestore.setVisibility(View.VISIBLE);
            aDelete.setVisibility(View.VISIBLE);
        }

        aDelete.setOnClickListener(view -> {
            HapticUtils.medium(view); sheet.dismiss();
            if (openedFromTrash) {
                ConfirmSheet.show(this,
                        getString(R.string.main_delete_note_forever_title),
                        getString(R.string.main_delete_note_forever_msg),
                        getString(R.string.delete), true, () -> {
                            AppExecutors.getInstance().diskIO().execute(() -> {
                                List<String> files;
                                try { files = repo.deleteForever(note.getId()); } catch (Exception e) { files = new ArrayList<>(); }
                                List<String> finalFiles = files;
                                mainHandler.post(() -> {
                                    DeleteFilesHelper.deleteFiles(EditNoteActivity.this, finalFiles);
                                    Intent res = new Intent();
                                    res.putExtra(RESULT_ACTION, ACTION_DELETED_FOREVER);
                                    res.putExtra(RESULT_NOTE_ID, note.getId());
                                    setResult(RESULT_OK, res);
                                    finish();
                                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                                });
                            });
                        });
                return;
            }
            if (note.isLocked()) {
                Snackbar.make(root, getString(R.string.remove_protection_first), Snackbar.LENGTH_SHORT).show();
                return;
            }
            ConfirmSheet.show(this, getString(R.string.confirm_delete_title),
                    getString(R.string.confirm_delete_msg),
                    getString(R.string.delete), true, () -> {
                        if (!isNew) {
                            AppExecutors.getInstance().diskIO().execute(() -> {
                                try {
                                    repo.moveToTrash(note.getId());
                                    ReminderUtils.cancel(EditNoteActivity.this, note.getId());
                                } catch (Exception ignored) {}
                                mainHandler.post(() -> {
                                    Intent res = new Intent();
                                    res.putExtra(RESULT_ACTION, ACTION_TRASHED);
                                    res.putExtra(RESULT_NOTE_ID, note.getId());
                                    setResult(RESULT_OK, res);
                                    finish();
                                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                                });
                            });
                        } else {
                            finish();
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                        }
                    });
        });

        sheet.show();
    }

    private void showNoteInfoDialog() {
        populateNoteFromUi();
        List<Attachment> attachments = note.getAttachments();
        int attachmentCount = attachments.size();
        int checklistCount = note.getType() == Note.TYPE_CHECKLIST ? note.getTotalChecklistItems() : 0;
        int checklistDone = note.getType() == Note.TYPE_CHECKLIST ? note.getCheckedCount() : 0;
        int imageCount = 0;
        int videoCount = 0;
        int audioCount = 0;
        int fileCount = 0;
        long totalAttachmentSize = 0L;
        for (Attachment attachment : attachments) {
            if (attachment == null) continue;
            totalAttachmentSize += Math.max(0L, attachment.size);
            switch (attachment.type) {
                case Attachment.TYPE_IMAGE:
                    imageCount++;
                    break;
                case Attachment.TYPE_VIDEO:
                    videoCount++;
                    break;
                case Attachment.TYPE_AUDIO:
                    audioCount++;
                    break;
                default:
                    fileCount++;
                    break;
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append(getString(R.string.note_info_created, TimeUtils.formatFullDate(note.getCreatedAt())));
        msg.append("\n");
        msg.append(getString(R.string.note_info_updated, TimeUtils.formatFullDate(note.getUpdatedAt())));
        msg.append("\n\n");
        msg.append(getString(R.string.note_info_words, note.getWordCount()));
        msg.append("\n");
        msg.append(getString(R.string.note_info_chars, note.getCharCount()));
        msg.append("\n");
        msg.append(getString(R.string.note_info_attachments, attachmentCount));
        if (attachmentCount > 0) {
            msg.append("\n");
            msg.append(getString(R.string.note_info_attachment_size, AttachmentUtils.formatSize(totalAttachmentSize)));
            if (imageCount > 0) {
                msg.append("\n");
                msg.append(getString(R.string.note_info_attachment_images, imageCount));
            }
            if (videoCount > 0) {
                msg.append("\n");
                msg.append(getString(R.string.note_info_attachment_videos, videoCount));
            }
            if (audioCount > 0) {
                msg.append("\n");
                msg.append(getString(R.string.note_info_attachment_audio, audioCount));
            }
            if (fileCount > 0) {
                msg.append("\n");
                msg.append(getString(R.string.note_info_attachment_files, fileCount));
            }
        }
        if (note.getType() == Note.TYPE_CHECKLIST) {
            msg.append("\n");
            msg.append(getString(R.string.note_info_checklist, checklistCount));
            msg.append("\n");
            msg.append(getString(R.string.note_info_done, checklistDone));
        }
        InfoSheet.show(this,
                getString(R.string.note_info_title),
                msg.toString(),
                0,
                getString(R.string.ok),
                null);
    }

    private void toggleLock() {
        if (!ensureEditableMode(null)) return;
        if (note.isLocked()) {
            note.setLocked(false);
            commitIfAutoSaveEnabled();
            updatePinFav();
            Snackbar.make(root, getString(R.string.edit_note_protection_removed), Snackbar.LENGTH_SHORT).show();
        } else {
            if (!prefs.hasPassword()) {
                ConfirmSheet.show(this, getString(R.string.install_password),
                        getString(R.string.edit_note_master_password_required),
                        getString(R.string.edit_note_go_to_settings), false,
                        () -> startActivity(new Intent(this, SettingsActivity.class)));
                return;
            }
            note.setLocked(true);
            commitIfAutoSaveEnabled();
            updatePinFav();
            Snackbar.make(root, getString(R.string.edit_note_note_protected), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showColorSheet() {
        if (!ensureEditableMode(null)) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_color_picker, null);
        sheet.setContentView(v);
        LinearLayout cont = v.findViewById(R.id.colors_container);
        LayoutInflater inf = LayoutInflater.from(this);
        cont.removeAllViews();
        for (int i = 0; i < ColorUtils.getNoteColorCount(); i++) {
            View item = inf.inflate(R.layout.item_color_circle, cont, false);
            View circle = item.findViewById(R.id.v_circle);
            int color = ColorUtils.getNoteBgColor(this, i);
            int accent = ColorUtils.getNoteAccentColor(this, i);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke((int) (1.5 * getResources().getDisplayMetrics().density),
                    note.getColorIndex() == i
                            ? ContextCompat.getColor(this, R.color.gold_primary)
                            : ContextCompat.getColor(this, R.color.divider));
            circle.setBackground(bg);
            View dot = item.findViewById(R.id.v_dot);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(accent);
            dot.setBackground(dotBg);
            dot.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
            int finalI = i;
            item.setOnClickListener(view -> {
                HapticUtils.light(view);
                note.setColorIndex(finalI);
                applyColor();
                sheet.dismiss();
            });
            cont.addView(item);
        }
        sheet.show();
    }

    private void showCategorySheet() {
        if (!ensureEditableMode(null)) return;
        if (note.isLocked()) {
            Snackbar.make(root, getString(R.string.remove_protection_first), Snackbar.LENGTH_SHORT).show();
            return;
        }
        // Load categories off UI thread
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Category> cats;
            try { cats = repo.getAllCategories(); } catch (Exception e) { cats = new ArrayList<>(); }
            List<Category> finalCats = cats;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                BottomSheetDialog sheet = new BottomSheetDialog(EditNoteActivity.this);
                View v = LayoutInflater.from(EditNoteActivity.this).inflate(R.layout.sheet_category_picker, null);
                sheet.setContentView(v);
                LinearLayout list = v.findViewById(R.id.cat_list);
                LayoutInflater inf = LayoutInflater.from(EditNoteActivity.this);
                list.removeAllViews();
                View none = inf.inflate(R.layout.item_category_pick, list, false);
                ((TextView) none.findViewById(R.id.tv_name)).setText(R.string.no_category);
                none.findViewById(R.id.v_dot).setVisibility(View.INVISIBLE);
                none.findViewById(R.id.iv_check).setVisibility(note.getCategoryId() == 0 ? View.VISIBLE : View.INVISIBLE);
                none.setOnClickListener(view -> {
                    HapticUtils.light(view);
                    note.setCategoryId(0);
                    tvCategory.setText(R.string.no_category);
                    sheet.dismiss();
                    dirty = true;
                    scheduleAutosaveDebounced();
                });
                list.addView(none);
                for (Category c : finalCats) {
                    View row = inf.inflate(R.layout.item_category_pick, list, false);
                    ((TextView) row.findViewById(R.id.tv_name)).setText(c.getName());
                    View dot = row.findViewById(R.id.v_dot);
                    GradientDrawable gd = (GradientDrawable) dot.getBackground().mutate();
                    gd.setColor(ColorUtils.getCategoryColor(EditNoteActivity.this, c.getColorIndex()));
                    row.findViewById(R.id.iv_check).setVisibility(
                            note.getCategoryId() == c.getId() ? View.VISIBLE : View.INVISIBLE);
                    row.setOnClickListener(view -> {
                        HapticUtils.light(view);
                        note.setCategoryId(c.getId());
                        tvCategory.setText(c.getName());
                        sheet.dismiss();
                        dirty = true;
                        scheduleAutosaveDebounced();
                    });
                    list.addView(row);
                }
                sheet.show();
            });
        });
    }

    private void showReminderSheet() {
        if (!ensureEditableMode(null)) return;
        Calendar cal = Calendar.getInstance();
        if (note.getReminderAt() > 0) cal.setTimeInMillis(note.getReminderAt());

        DatePickerDialog dp = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar c2 = Calendar.getInstance();
                    if (note.getReminderAt() > 0) c2.setTimeInMillis(note.getReminderAt());
                    TimePickerDialog tp = new TimePickerDialog(this,
                            (tv, h, m) -> {
                                Calendar set = Calendar.getInstance();
                                set.set(year, month, day, h, m, 0);
                                set.set(Calendar.MILLISECOND, 0);
                                if (set.getTimeInMillis() <= System.currentTimeMillis()) {
                                    Snackbar.make(root, getString(R.string.time_already_passed), Snackbar.LENGTH_SHORT).show();
                                    return;
                                }
                                note.setReminderAt(set.getTimeInMillis());
                                updateReminderLabel();
                                commitIfAutoSaveEnabled();
                                Snackbar.make(root, getString(R.string.reminder_set), Snackbar.LENGTH_SHORT).show();
                            },
                            c2.get(Calendar.HOUR_OF_DAY), c2.get(Calendar.MINUTE), true);
                    tp.show();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        if (note.getReminderAt() > 0) {
            dp.setButton(DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.edit_note_remove_reminder), (d, w) -> {
                note.setReminderAt(0);
                ReminderUtils.cancel(this, note.getId());
                updateReminderLabel();
                commitIfAutoSaveEnabled();
            });
        }
        dp.show();
    }

    private void showAttachSheet() {
        if (!ensureEditableMode(null)) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_attach, null);
        sheet.setContentView(v);

        v.findViewById(R.id.attach_image).setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            try {
                imagePickerLauncher.launch("image/*");
            } catch (Exception e) {
                Snackbar.make(root, getString(R.string.gallery_open_failed_with_reason, e.getMessage()),
                        Snackbar.LENGTH_LONG).show();
            }
        });

        v.findViewById(R.id.attach_video_gallery).setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            try {
                videoPickerLauncher.launch("video/*");
            } catch (Exception e) {
                Snackbar.make(root, getString(R.string.open_gallery_video_failed), Snackbar.LENGTH_LONG).show();
            }
        });

        v.findViewById(R.id.attach_camera).setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            ensureCameraAndOpen(false);
        });

        v.findViewById(R.id.attach_video).setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            ensureCameraAndOpen(true);
        });

        v.findViewById(R.id.attach_audio).setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            recordAudio();
        });

        v.findViewById(R.id.attach_file).setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            try {
                filePickerLauncher.launch(new String[]{"*/*"});
            } catch (Exception e) {
                Snackbar.make(root, getString(R.string.file_picker_failed), Snackbar.LENGTH_SHORT).show();
            }
        });

        sheet.show();
    }

    private void ensureCameraAndOpen(boolean isVideo) {
        if (!ensureEditableMode(null)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = isVideo
                    ? PERMISSION_ACTION_CAMERA_VIDEO
                    : PERMISSION_ACTION_CAMERA_PHOTO;
            permissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        try {
            File dir = new File(getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            String name = "cam_" + System.currentTimeMillis() + (isVideo ? ".mp4" : ".jpg");
            pendingCameraFile = new File(dir, name);
            pendingCameraUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", pendingCameraFile);
            if (isVideo) videoCaptureLauncher.launch(pendingCameraUri);
            else cameraLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Snackbar.make(root, getString(R.string.camera_unavailable, e.getMessage()),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void recordAudio() {
        if (!ensureEditableMode(null)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = PERMISSION_ACTION_RECORD_AUDIO;
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        RecordAudioDialog dlg = RecordAudioDialog.newInstance();
        dlg.setOnRecorded(filePath -> {
            File f = new File(filePath);
            if (!f.exists()) return;
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            ArrayList<Uri> list = new ArrayList<>();
            list.add(uri);
            startCopy(list);
        });
        dlg.show(getSupportFragmentManager(), "rec");
    }

    private void openAttachment(Attachment a) {
        File f = AttachmentUtils.getFile(this, a.fileName);
        if (!f.exists()) {
            Snackbar.make(root, getString(R.string.file_not_found), Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (a.type == Attachment.TYPE_IMAGE) {
            ArrayList<Attachment> imgList = new ArrayList<>();
            for (Attachment att : note.getAttachments()) {
                if (att.type == Attachment.TYPE_IMAGE) {
                    imgList.add(att);
                }
            }
            int clickedIdx = 0;
            for (int k = 0; k < imgList.size(); k++) {
                if (imgList.get(k).fileName != null && imgList.get(k).fileName.equals(a.fileName)) {
                    clickedIdx = k;
                    break;
                }
            }
            Intent i = new Intent(this, ImageViewerActivity.class);
            i.putParcelableArrayListExtra("images", imgList);
            i.putExtra("index", clickedIdx);
            i.putExtra("note_id", note.getId());
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else if (a.type == Attachment.TYPE_AUDIO) {
            ArrayList<Attachment> audioList = new ArrayList<>();
            for (Attachment att : note.getAttachments()) {
                if (att.type == Attachment.TYPE_AUDIO) {
                    audioList.add(att);
                }
            }
            int clickedIdx = 0;
            for (int k = 0; k < audioList.size(); k++) {
                if (audioList.get(k).fileName != null && audioList.get(k).fileName.equals(a.fileName)) {
                    clickedIdx = k;
                    break;
                }
            }
            Intent i = new Intent(this, AudioPlayerActivity.class);
            i.putParcelableArrayListExtra("audios", audioList);
            i.putExtra("index", clickedIdx);
            i.putExtra("note_id", note.getId());
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else if (a.type == Attachment.TYPE_VIDEO) {
            ArrayList<Attachment> videoList = new ArrayList<>();
            for (Attachment att : note.getAttachments()) {
                if (att.type == Attachment.TYPE_VIDEO) {
                    videoList.add(att);
                }
            }
            int clickedIdx = 0;
            for (int k = 0; k < videoList.size(); k++) {
                if (videoList.get(k).fileName != null && videoList.get(k).fileName.equals(a.fileName)) {
                    clickedIdx = k;
                    break;
                }
            }
            Intent i = new Intent(this, VideoPlayerActivity.class);
            i.putParcelableArrayListExtra("videos", videoList);
            i.putExtra("index", clickedIdx);
            i.putExtra("note_id", note.getId());
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else if (isTextFile(a.fileName)) {
            Intent i = new Intent(this, tkm.tmnote.pro.viewer.TextFileViewerActivity.class);
            i.putExtra("path", f.getAbsolutePath());
            i.putExtra("name", a.displayName);
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else if (a.fileName != null && a.fileName.toLowerCase(Locale.US).endsWith(".pdf")) {
            Intent i = new Intent(this, tkm.tmnote.pro.viewer.PdfViewerActivity.class);
            i.putExtra("path", f.getAbsolutePath());
            i.putExtra("name", a.displayName);
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else if (a.fileName != null && a.fileName.toLowerCase(Locale.US).endsWith(".apk")) {
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Для соответствия Google Play не используем REQUEST_INSTALL_PACKAGES,
                // открываем через системный chooser
                Intent chooser = Intent.createChooser(i, "Открыть APK с помощью");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e) {
                Snackbar.make(root, "Ошибка при запуске установки: " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
            }
        } else {
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, AttachmentUtils.getMime(a.type, a.fileName));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, getString(R.string.edit_note_open_with)));
            } catch (Exception e) {
                Snackbar.make(root, getString(R.string.no_app_to_open), Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private boolean isTextFile(String fileName) {
        if (fileName == null) return false;
        String nameLower = fileName.toLowerCase(Locale.US);
        return nameLower.endsWith(".txt") || nameLower.endsWith(".log") || nameLower.endsWith(".ini")
                || nameLower.endsWith(".conf") || nameLower.endsWith(".properties")
                || nameLower.endsWith(".py") || nameLower.endsWith(".java") || nameLower.endsWith(".kt")
                || nameLower.endsWith(".cpp") || nameLower.endsWith(".c") || nameLower.endsWith(".h")
                || nameLower.endsWith(".cs") || nameLower.endsWith(".sh") || nameLower.endsWith(".bat")
                || nameLower.endsWith(".js") || nameLower.endsWith(".ts") || nameLower.endsWith(".html")
                || nameLower.endsWith(".css") || nameLower.endsWith(".xml") || nameLower.endsWith(".json")
                || nameLower.endsWith(".yaml") || nameLower.endsWith(".yml") || nameLower.endsWith(".gradle")
                || nameLower.endsWith(".md") || nameLower.endsWith(".sql") || nameLower.endsWith(".php")
                || nameLower.endsWith(".rb") || nameLower.endsWith(".go") || nameLower.endsWith(".rs")
                || nameLower.endsWith(".swift") || nameLower.endsWith(".toml") || nameLower.endsWith(".csv");
    }

    // ===== Selection mode для вложений (как в MainActivity) =====
    private void setupAttachmentsSelectionBar() {
        if (attachmentsAdapter == null) return;
        attachmentsAdapter.setSelectionListener(count -> {
            updateAttachmentsSelectionUi(count);
        });
        if (selAttClose != null) {
            selAttClose.setOnClickListener(v -> {
                HapticUtils.light(v);
                attachmentsAdapter.clearSelection();
            });
        }
        if (selAttSelectAll != null) {
            selAttSelectAll.setOnClickListener(v -> {
                HapticUtils.light(v);
                if (attachmentsAdapter.getSelectedCount() == attachmentsAdapter.getTotalCount()
                        && attachmentsAdapter.getTotalCount() > 0) {
                    attachmentsAdapter.clearSelection();
                } else {
                    attachmentsAdapter.selectAll();
                }
            });
        }
        if (selAttShare != null) {
            selAttShare.setOnClickListener(v -> {
                HapticUtils.light(v);
                shareSelectedAttachments();
            });
        }
        if (selAttSave != null) {
            selAttSave.setOnClickListener(v -> {
                HapticUtils.light(v);
                saveSelectedAttachments();
            });
        }
        if (selAttDelete != null) {
            selAttDelete.setOnClickListener(v -> {
                HapticUtils.medium(v);
                deleteSelectedAttachments();
            });
        }
    }

    private void updateAttachmentsSelectionUi(int count) {
        if (selectionBarAttachments == null) return;
        if (count > 0) {
            selectionBarAttachments.setVisibility(View.VISIBLE);
            if (topBar != null) topBar.setVisibility(View.GONE);
            if (tvSelectionCountAtt != null) {
                tvSelectionCountAtt.setText(getString(R.string.items_selected, count));
            }
        } else {
            selectionBarAttachments.setVisibility(View.GONE);
            if (topBar != null) topBar.setVisibility(View.VISIBLE);
        }
    }

    private void shareSelectedAttachments() {
        if (attachmentsAdapter == null) return;
        java.util.List<Attachment> selected = attachmentsAdapter.getSelectedAttachments();
        if (selected.isEmpty()) return;
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            String commonMime = null;
            boolean mixed = false;
            for (Attachment a : selected) {
                File f = AttachmentUtils.getFile(this, a.fileName);
                if (!f.exists()) continue;
                Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                uris.add(u);
                String mime = AttachmentUtils.getMime(a.type, a.fileName);
                if (commonMime == null) commonMime = mime;
                else if (mime != null && !mime.equals(commonMime)) mixed = true;
            }
            if (uris.isEmpty()) return;
            Intent i;
            if (uris.size() == 1) {
                i = new Intent(Intent.ACTION_SEND);
                i.setType(commonMime == null ? "*/*" : commonMime);
                i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.setType(mixed || commonMime == null ? "*/*" : commonMime);
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.edit_note_share_chooser)));
        } catch (Exception e) {
            Snackbar.make(root, getString(R.string.share_failed), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void saveSelectedAttachments() {
        if (attachmentsAdapter == null) return;
        java.util.List<Attachment> selected = attachmentsAdapter.getSelectedAttachments();
        if (selected.isEmpty()) return;
        if (selected.size() == 1) {
            // одиночный файл — сохраняем через CreateDocument (как раньше)
            saveAttachmentToDownloads(selected.get(0));
            return;
        }
        pendingSaveAttachments = new ArrayList<>(selected);
        try {
            openTreeLauncher.launch(null);
        } catch (Exception e) {
            pendingSaveAttachments = null;
            Snackbar.make(root, getString(R.string.copied_to_file_failed), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void deleteSelectedAttachments() {
        if (!ensureEditableMode(null)) return;
        if (attachmentsAdapter == null) return;
        final java.util.List<Attachment> selected = attachmentsAdapter.getSelectedAttachments();
        if (selected.isEmpty()) return;
        final int n = selected.size();
        String title = getResources().getQuantityString(R.plurals.edit_note_remove_attachments_q, n, n);
        ConfirmSheet.show(this, title, null, getString(R.string.delete), true, () -> {
            List<Attachment> list = new ArrayList<>(note.getAttachments());
            HashSet<String> selectedFiles = new HashSet<>();
            for (Attachment a : selected) {
                if (a.fileName != null) selectedFiles.add(a.fileName);
            }
            int removed = 0;
            for (int i = list.size() - 1; i >= 0; i--) {
                Attachment x = list.get(i);
                boolean match = false;
                if (x.fileName != null && selectedFiles.contains(x.fileName)) {
                    match = true;
                } else {
                    for (Attachment sa : selected) {
                        if (isSameAttachment(x, sa)) { match = true; break; }
                    }
                }
                if (match) {
                    if (x.fileName != null && !x.fileName.isEmpty()) {
                        pendingDeleteAttachmentFiles.add(x.fileName);
                    }
                    list.remove(i);
                    removed++;
                }
            }
            if (removed == 0) return;
            note.setAttachments(list);
            dirty = true;
            attachmentsAdapter.clearSelection();
            reloadAttachments();
            if (!prefs.isConfirmSaveOnExitEnabled()) saveNote(true);
            Snackbar.make(root, getString(R.string.edit_note_attachments_deleted, removed),
                    Snackbar.LENGTH_SHORT).show();
        });
    }

    private void showAttachmentActions(Attachment a) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_attachment_actions, null);
        sheet.setContentView(view);
        ((TextView) view.findViewById(R.id.tv_title)).setText(a.displayName);
        view.findViewById(R.id.action_open).setOnClickListener(vv -> {
            HapticUtils.light(vv); sheet.dismiss(); openAttachment(a);
        });
        view.findViewById(R.id.action_share).setOnClickListener(vv -> {
            HapticUtils.light(vv); sheet.dismiss(); shareAttachment(a);
        });
        view.findViewById(R.id.action_save).setOnClickListener(vv -> {
            HapticUtils.light(vv); sheet.dismiss(); saveAttachmentToDownloads(a);
        });
        view.findViewById(R.id.action_delete).setOnClickListener(vv -> {
            HapticUtils.medium(vv); sheet.dismiss(); deleteAttachment(a);
        });
        sheet.show();
    }

    private void deleteAttachment(Attachment a) {
        if (!ensureEditableMode(null)) return;
        ConfirmSheet.show(this, getString(R.string.edit_note_remove_attachment),
                a.displayName, getString(R.string.delete), true, () -> {
                    List<Attachment> list = new ArrayList<>(note.getAttachments());
                    boolean removed = false;
                    for (int i = list.size() - 1; i >= 0; i--) {
                        Attachment x = list.get(i);
                        if (isSameAttachment(x, a)) {
                            if (x.fileName != null && !x.fileName.isEmpty()) {
                                pendingDeleteAttachmentFiles.add(x.fileName);
                            }
                            list.remove(i);
                            removed = true;
                        }
                    }
                    if (!removed) return;
                    note.setAttachments(list);
                    dirty = true;
                    reloadAttachments();
                    if (!prefs.isConfirmSaveOnExitEnabled()) saveNote(true);
                    Snackbar.make(root, getString(R.string.edit_note_attachment_deleted), Snackbar.LENGTH_SHORT).show();
                });
    }

    private boolean isSameAttachment(Attachment left, Attachment right) {
        if (left == null || right == null) return false;
        if (!TextUtils.isEmpty(left.fileName) && !TextUtils.isEmpty(right.fileName)) {
            return left.fileName.equals(right.fileName);
        }
        return left.type == right.type
                && left.createdAt == right.createdAt
                && TextUtils.equals(left.displayName, right.displayName)
                && left.size == right.size;
    }

    private void shareAttachment(Attachment a) {
        File f = AttachmentUtils.getFile(this, a.fileName);
        if (!f.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(AttachmentUtils.getMime(a.type, a.fileName));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.edit_note_share_chooser)));
        } catch (Exception e) {
            Snackbar.make(root, getString(R.string.share_failed), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void saveAttachmentToDownloads(Attachment a) {
        pendingSaveAttachment = a;
        try {
            createDocLauncher.launch(a.displayName == null ? a.fileName : a.displayName);
        } catch (Exception e) {
            Snackbar.make(root, getString(R.string.copied_to_file_failed), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showTextSizeSheet() {
        if (!ensureEditableMode(null)) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_text_size, null);
        sheet.setContentView(v);
        TextView tvTitle = v.findViewById(R.id.tv_title);
        TextView tvSize = v.findViewById(R.id.tv_size_value);
        SeekBar seek = v.findViewById(R.id.seek_text_size);
        TextView btnReset = v.findViewById(R.id.btn_reset);
        TextView btnCancel = v.findViewById(R.id.btn_cancel);
        TextView btnApply = v.findViewById(R.id.btn_apply);

        final int oldSize = note.getTextSize();
        final int defaultSize = PrefsManager.DEFAULT_TEXT_SIZE;

        if (tvTitle != null) tvTitle.setText(R.string.sheet_text_size_title);
        if (btnReset != null) btnReset.setVisibility(View.VISIBLE);
        seek.setMax(25 - 10);
        seek.setProgress(oldSize - 10);
        tvSize.setText(String.valueOf(oldSize));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int s = 10 + progress;
                tvSize.setText(String.valueOf(s));
                applyTextSize(s);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        if (btnReset != null) {
            btnReset.setOnClickListener(view -> {
                HapticUtils.light(view);
                applyTextSize(defaultSize);
                dirty = true;
                if (!prefs.isConfirmSaveOnExitEnabled()) saveNote(true);
                sheet.dismiss();
                Snackbar.make(root, getString(R.string.edit_note_text_size_format, defaultSize), Snackbar.LENGTH_SHORT).show();
            });
        }
        btnCancel.setOnClickListener(view -> {
            HapticUtils.light(view);
            applyTextSize(oldSize);
            sheet.dismiss();
        });
        btnApply.setOnClickListener(view -> {
            HapticUtils.light(view);
            int selected = 10 + seek.getProgress();
            applyTextSize(selected);
            dirty = true;
            if (!prefs.isConfirmSaveOnExitEnabled()) saveNote(true);
            sheet.dismiss();
            Snackbar.make(root, getString(R.string.edit_note_text_size_format, selected), Snackbar.LENGTH_SHORT).show();
        });
        sheet.show();
    }

    private void setupFindInlineBar() {
        ImageView btnSearchMore = findViewById(R.id.btn_search_more);
        ImageView btnSearchClose = findViewById(R.id.btn_search_close);
        if (etFindInline != null) {
            etFindInline.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (!isFindMode) return;
                    scheduleSearchDebounced(s == null ? "" : s.toString(), false);
                }
            });
        }
        if (btnSearchClose != null) {
            btnSearchClose.setOnClickListener(v -> {
                HapticUtils.light(v);
                exitFindMode();
            });
        }
        if (btnSearchMore != null) {
            btnSearchMore.setOnClickListener(v -> {
                HapticUtils.light(v);
                showReplaceSheet();
            });
        }
        if (btnSearchPrev != null) {
            btnSearchPrev.setOnClickListener(v -> {
                HapticUtils.light(v);
                navigateSearchMatch(-1);
            });
        }
        if (btnSearchNext != null) {
            btnSearchNext.setOnClickListener(v -> {
                HapticUtils.light(v);
                navigateSearchMatch(1);
            });
        }
    }

    private void scheduleSearchDebounced(String query, boolean showMessage) {
        if (pendingSearchRunnable != null) {
            mainHandler.removeCallbacks(pendingSearchRunnable);
        }
        final int gen = ++searchGeneration;
        pendingSearchRunnable = () -> {
            if (gen != searchGeneration) return;
            if (!isFindMode) return;
            if (isLargeTextMode() || (etContent != null && etContent.getText() != null && etContent.getText().length() > 50000)) {
                final String q = query;
                final boolean sm = showMessage;
                AppExecutors.getInstance().diskIO().execute(() -> {
                    if (gen != searchGeneration) return;
                    List<Integer> indices = new ArrayList<>();
                    try {
                        if (etContent != null && etContent.getText() != null) {
                            String text = etContent.getText().toString();
                            if (!q.trim().isEmpty() && !text.isEmpty()) {
                                String lowerQ = q.toLowerCase(Locale.getDefault());
                                String lowerSrc = text.toLowerCase(Locale.getDefault());
                                int len = lowerQ.length();
                                int idx = lowerSrc.indexOf(lowerQ);
                                while (idx >= 0 && indices.size() < MAX_SEARCH_MATCHES) {
                                    indices.add(idx);
                                    idx = lowerSrc.indexOf(lowerQ, idx + len);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    List<Integer> finalIndices = indices;
                    mainHandler.post(() -> {
                        if (gen != searchGeneration) return;
                        if (!isFindMode) return;
                        searchIndices.clear();
                        searchIndices.addAll(finalIndices);
                        currentSearchIndex = searchIndices.isEmpty() ? -1 : 0;
                        if (tvSearchCount != null) {
                            tvSearchCount.setText(searchIndices.isEmpty() ? "0/0" : "1/" + searchIndices.size());
                        }
                        if (!searchIndices.isEmpty()) {
                            highlightMatchesAndScroll();
                        } else {
                            clearFindHighlight();
                            if (sm && root != null) Snackbar.make(root, getString(R.string.search_no_matches), Snackbar.LENGTH_SHORT).show();
                        }
                    });
                });
            } else {
                performFind(query, showMessage);
            }
        };
        mainHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE);
    }

    private void toggleFindMode() {
        if (!ensureEditableMode(null)) return;
        if (note.getType() != Note.TYPE_TEXT) {
            Snackbar.make(root, getString(R.string.edit_note_text_mode_search_only), Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (isFindMode) {
            exitFindMode();
            return;
        }
        isFindMode = true;
        searchIndices.clear();
        currentSearchIndex = -1;
        activeSearchSpans.clear();
        if (bottomActionsRow != null) bottomActionsRow.setVisibility(View.GONE);
        if (searchBarRow != null) searchBarRow.setVisibility(View.VISIBLE);
        if (tvSearchCount != null) tvSearchCount.setText("0/0");
        if (etFindInline != null) {
            etFindInline.setText("");
            etFindInline.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etFindInline, 0);
        }
    }

    private void exitFindMode() {
        isFindMode = false;
        searchGeneration++;
        if (pendingSearchRunnable != null) {
            mainHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
        clearFindHighlight();
        searchIndices.clear();
        currentSearchIndex = -1;
        if (searchBarRow != null) searchBarRow.setVisibility(View.GONE);
        if (etFindInline != null) etFindInline.setText("");
        updateReadModeUi();
    }

    private void showReplaceSheet() {
        if (!isFindMode || etFindInline == null) return;
        String query = etFindInline.getText() == null ? "" : etFindInline.getText().toString().trim();
        if (query.isEmpty()) {
            Snackbar.make(root, getString(R.string.search_text_prompt), Snackbar.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_replace_text, null);
        sheet.setContentView(v);
        EditText etFind = v.findViewById(R.id.et_find);
        EditText etReplace = v.findViewById(R.id.et_replace);
        TextView btnCancel = v.findViewById(R.id.btn_cancel);
        TextView btnApply = v.findViewById(R.id.btn_apply);
        etFind.setText(query);
        etFind.setEnabled(false);
        btnCancel.setOnClickListener(vv -> { HapticUtils.light(vv); sheet.dismiss(); });
        btnApply.setOnClickListener(vv -> {
            HapticUtils.light(vv);
            String repl = etReplace.getText() == null ? "" : etReplace.getText().toString();
            int count = replaceAllInContent(query, repl);
            Snackbar.make(root,
                    count > 0 ? getString(R.string.edit_note_replace_done, count) : getString(R.string.search_no_matches),
                    Snackbar.LENGTH_SHORT).show();
            if (count > 0 && !prefs.isConfirmSaveOnExitEnabled()) {
                saveNote(true);
            }
            sheet.dismiss();
        });
        sheet.show();
    }

    private void performFind(String query, boolean showMessage) {
        searchIndices.clear();
        currentSearchIndex = -1;
        clearFindHighlight();

        if (query == null || query.trim().isEmpty()) {
            if (tvSearchCount != null) tvSearchCount.setText("0/0");
            return;
        }

        Editable editable = etContent.getText();
        if (editable == null) return;
        String text = editable.toString();
        if (text.isEmpty()) return;

        String q = query.toLowerCase(Locale.getDefault());
        String src = text.toLowerCase(Locale.getDefault());
        int len = q.length();

        int idx = src.indexOf(q);
        while (idx >= 0 && searchIndices.size() < MAX_SEARCH_MATCHES) {
            searchIndices.add(idx);
            idx = src.indexOf(q, idx + len);
        }

        int count = searchIndices.size();
        if (count > 0) {
            currentSearchIndex = 0;
            if (tvSearchCount != null) tvSearchCount.setText("1/" + count);
            highlightMatchesAndScroll();
        } else {
            if (tvSearchCount != null) tvSearchCount.setText("0/0");
            if (showMessage) {
                Snackbar.make(root, getString(R.string.search_no_matches), Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateSearchMatch(int dir) {
        int count = searchIndices.size();
        if (count == 0) return;

        currentSearchIndex += dir;
        if (currentSearchIndex >= count) currentSearchIndex = 0;
        else if (currentSearchIndex < 0) currentSearchIndex = count - 1;

        if (tvSearchCount != null) {
            tvSearchCount.setText((currentSearchIndex + 1) + "/" + count);
        }
        highlightMatchesAndScroll();
    }

    private void highlightMatchesAndScroll() {
        if (searchIndices.isEmpty() || currentSearchIndex < 0) return;

        Editable editable = etContent.getText();
        if (editable == null) return;

        clearFindHighlight();

        String query = etFindInline != null && etFindInline.getText() != null ? etFindInline.getText().toString() : "";
        int queryLen = query.length();
        if (queryLen == 0) return;

        boolean large = isLargeTextMode();
        int startIdx = large ? currentSearchIndex : 0;
        int endIdx = large ? currentSearchIndex + 1 : searchIndices.size();

        for (int i = startIdx; i < endIdx && i < searchIndices.size(); i++) {
            int start = searchIndices.get(i);
            int end = start + queryLen;
            if (end > editable.length() || start < 0) continue;
            int color;
            if (i == currentSearchIndex) {
                try { color = ContextCompat.getColor(EditNoteActivity.this, R.color.gold_primary); }
                catch (Exception e) { color = 0xFFD4AF37; }
            } else {
                color = 0x5543A047;
            }
            BackgroundColorSpan span = new BackgroundColorSpan(color);
            editable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            activeSearchSpans.add(span);
        }

        final int activeOffset = searchIndices.get(currentSearchIndex);
        if (scrollContent != null) {
            scrollContent.post(() -> {
                try {
                    Layout layout = etContent.getLayout();
                    if (layout != null) {
                        int line = layout.getLineForOffset(activeOffset);
                        int y = layout.getLineTop(line);
                        int etY = etContent.getTop();
                        int targetScrollY = Math.max(0, etY + y - scrollContent.getHeight() / 3);
                        scrollContent.smoothScrollTo(0, targetScrollY);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private void highlightMatches(String query) {
        performFind(query, true);
    }

    private void highlightMatchesInternal(String query, boolean showMessage) {
        performFind(query, showMessage);
    }

    private int replaceAllInContent(String query, String replacement) {
        clearFindHighlight();
        if (query == null || query.isEmpty()) return 0;
        String text = etContent.getText() == null ? "" : etContent.getText().toString();
        if (text.isEmpty()) return 0;
        String repl = replacement == null ? "" : replacement;
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = query.toLowerCase(Locale.getDefault());
        int count = 0;
        int idx = lowerText.indexOf(lowerQuery);
        while (idx >= 0 && count < MAX_SEARCH_MATCHES) {
            count++;
            idx = lowerText.indexOf(lowerQuery, idx + query.length());
        }
        if (count == 0) return 0;

        finalizeActiveBatch();

        int curS = etContent.getSelectionStart();
        int curE = etContent.getSelectionEnd();
        int curScroll = getCurrentEtScrollY();

        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        int searchIdx = lowerText.indexOf(lowerQuery);
        while (searchIdx >= 0) {
            sb.append(text, last, searchIdx);
            sb.append(repl);
            last = searchIdx + query.length();
            searchIdx = lowerText.indexOf(lowerQuery, last);
        }
        sb.append(text.substring(last));
        String replaced = sb.toString();

        int first = lowerText.indexOf(lowerQuery);
        int newSel = first >= 0 ? Math.min(first + repl.length(), replaced.length()) : 0;

        EditAction replaceAction = new EditAction(
                EditAction.TARGET_CONTENT,
                text,
                replaced,
                curS,
                curE,
                newSel,
                newSel,
                curScroll,
                curScroll,
                EditAction.ACTION_REPLACE
        );
        pushToUndoStack(replaceAction);
        redoStack.clear();

        applyEditState(etContent, EditAction.TARGET_CONTENT, replaced, newSel, newSel, curScroll);
        updateUndoButtons();
        return count;
    }

    private void clearFindHighlight() {
        if (etContent == null || etContent.getText() == null) return;
        try {
            Editable editable = etContent.getText();
            if (!activeSearchSpans.isEmpty()) {
                for (BackgroundColorSpan span : activeSearchSpans) {
                    try { editable.removeSpan(span); } catch (Exception ignored) {}
                }
                activeSearchSpans.clear();
            } else {
                if (editable.length() < 50000) {
                    BackgroundColorSpan[] spans = editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
                    for (BackgroundColorSpan span : spans) {
                        try { editable.removeSpan(span); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void readNoteAloud() {
        if (isSpeakingAloud) {
            stopReadAloud();
            Snackbar.make(root, getString(R.string.edit_note_reading_stopped), Snackbar.LENGTH_SHORT).show();
            return;
        }
        StringBuilder text = new StringBuilder();
        if (!etTitle.getText().toString().trim().isEmpty()) {
            text.append(etTitle.getText().toString().trim()).append(". ");
        }
        if (note.getType() == Note.TYPE_TEXT) {
            text.append(etContent.getText().toString());
        } else {
            List<ChecklistItem> items = checklistAdapter != null
                    ? checklistAdapter.getItems()
                    : note.getChecklistItems();
            for (ChecklistItem item : items) {
                if (item.text == null || item.text.trim().isEmpty()) continue;
                text.append(item.checked
                        ? getString(R.string.edit_note_done_prefix, item.text)
                        : getString(R.string.edit_note_point_prefix, item.text)).append(". ");
            }
        }
        String toSpeak = text.toString().trim();
        if (toSpeak.isEmpty()) {
            Snackbar.make(root, getString(R.string.no_text_to_read), Snackbar.LENGTH_SHORT).show();
            return;
        }
        startReadAloud(toSpeak);
    }

    private void ensureTtsReady() {
        if (tts != null) return;
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) {
                Snackbar.make(root, getString(R.string.tts_unavailable), Snackbar.LENGTH_SHORT).show();
                return;
            }
            tts.setLanguage(Locale.getDefault());
            if (!TextUtils.isEmpty(pendingTtsText)) {
                String pending = pendingTtsText;
                pendingTtsText = null;
                startReadAloud(pending);
            }
        });
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { isSpeakingAloud = true; }
            @Override public void onDone(String utteranceId) {
                runOnUiThread(() -> speakNextChunk());
            }
            @Override public void onError(String utteranceId) {
                isSpeakingAloud = false;
                ttsChunks.clear();
                ttsChunkIndex = 0;
            }
        });
    }

    private void startReadAloud(String text) {
        ensureTtsReady();
        if (tts == null) return;
        if (!ttsReady) {
            pendingTtsText = text;
            Snackbar.make(root, getString(R.string.edit_note_preparing_read), Snackbar.LENGTH_SHORT).show();
            return;
        }
        ttsChunks.clear();
        ttsChunkIndex = 0;
        int maxChunk = 3000;
        int p = 0;
        while (p < text.length()) {
            int end = Math.min(text.length(), p + maxChunk);
            if (end < text.length()) {
                int dot = text.lastIndexOf('.', end);
                int comma = text.lastIndexOf(',', end);
                int br = text.lastIndexOf('\n', end);
                int split = Math.max(br, Math.max(dot, comma));
                if (split > p + 300) end = split + 1;
            }
            ttsChunks.add(text.substring(p, end).trim());
            p = end;
        }
        speakNextChunk();
    }

    private void speakNextChunk() {
        if (tts == null || !ttsReady) return;
        while (ttsChunkIndex < ttsChunks.size() && ttsChunks.get(ttsChunkIndex).isEmpty()) {
            ttsChunkIndex++;
        }
        if (ttsChunkIndex >= ttsChunks.size()) {
            isSpeakingAloud = false;
            ttsChunks.clear();
            ttsChunkIndex = 0;
            return;
        }
        isSpeakingAloud = true;
        String chunk = ttsChunks.get(ttsChunkIndex++);
        tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "note_read_" + ttsChunkIndex);
    }

    private void stopReadAloud() {
        isSpeakingAloud = false;
        pendingTtsText = null;
        ttsChunks.clear();
        ttsChunkIndex = 0;
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            registerReceiver(progressReceiver, new IntentFilter(AttachmentCopyService.ACTION_PROGRESS),
                    Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);
        } catch (Exception ignored) {}
        if (uiReady && attachmentsAdapter != null && note != null && !isNew) {
            // Async sync to avoid DB on UI thread
            AppExecutors.getInstance().diskIO().execute(() -> {
                try {
                    Note fresh = repo.getNoteById(note.getId());
                    if (fresh != null) {
                        List<Attachment> merged = fresh.getAttachments();
                        if (!pendingDeleteAttachmentFiles.isEmpty()) {
                            merged.removeIf(att -> att != null
                                    && att.fileName != null
                                    && pendingDeleteAttachmentFiles.contains(att.fileName));
                        }
                        mainHandler.post(() -> {
                            if (note != null) {
                                note.setAttachments(merged);
                                reloadAttachments();
                            }
                        });
                    }
                } catch (Exception ignored) {}
            });
        }
        if (!AttachmentCopyService.PENDING.containsKey(note != null ? note.getId() : 0L)
                && progressBar != null) {
            progressBar.setVisibility(View.GONE);
            copyInProgress = false;
        }
        if (uiReady) {
            readModeFeatureEnabled = prefs.isEditorReadModeEnabled();
            if (!readModeFeatureEnabled && readMode) setReadMode(false, false);
            else updateReadModeUi();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(progressReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (suppressAutoSaveOnStop) {
            cancelPendingRunnables();
            return;
        }
        if (!uiReady || note == null) return;
        if (prefs.isConfirmSaveOnExitEnabled()) return;
        if (copyInProgress) return;
        try {
            // Use async autosave, not sync saveNote
            if (dirty) performAutosaveAsync();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        cancelPendingRunnables();
        stopReadAloud();
        // Avoid heavy clearFindHighlight which iterates spans on huge doc; do lightweight
        try {
            if (etContent != null && etContent.getText() != null) {
                // Only clear our search spans, but avoid full scan if large
                // clearFindHighlight already does getSpans, but we call it only if in find mode
                if (isFindMode) clearFindHighlight();
            }
        } catch (Exception ignored) {}
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
        }
        super.onDestroy();
    }

}
