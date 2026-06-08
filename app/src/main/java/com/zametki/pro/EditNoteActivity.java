package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

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
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.KeyListener;
import android.text.util.Linkify;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.speech.tts.TextToSpeech;
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
import com.zametki.pro.adapters.AttachmentsEditorAdapter;
import com.zametki.pro.adapters.ChecklistEditorAdapter;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.dialogs.RecordAudioDialog;
import com.zametki.pro.models.Attachment;
import com.zametki.pro.models.Category;
import com.zametki.pro.models.ChecklistItem;
import com.zametki.pro.models.Note;
import com.zametki.pro.services.AttachmentCopyService;
import com.zametki.pro.utils.AttachmentUtils;
import com.zametki.pro.utils.ConfirmSheet;
import com.zametki.pro.utils.DeleteFilesHelper;
import com.zametki.pro.utils.ColorUtils;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.InfoSheet;
import com.zametki.pro.utils.PrefsManager;
import com.zametki.pro.utils.ReminderUtils;
import com.zametki.pro.utils.TimeUtils;
import com.zametki.pro.utils.AppExecutors;
import com.zametki.pro.viewer.AudioPlayerActivity;
import com.zametki.pro.viewer.ImageViewerActivity;
import com.zametki.pro.viewer.VideoPlayerActivity;

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

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private boolean ignoreTextChange = false;
    private String lastContent = "";

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
    private boolean isFindMode = false;
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
    private boolean activeLinksEnabled = true;
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

            if (finished || (progressVal >= 100 && total > 0 && doneCount >= total)) {
                progressBar.setVisibility(View.GONE);
                copyInProgress = false;
                if (uiReady && attachmentsAdapter != null) syncAttachmentsFromDb();
            } else if (progressVal >= 0) {
                progressBar.setVisibility(View.VISIBLE);
                copyInProgress = true;
                String txt = (name != null ? name + "  " : "") +
                        "(" + Math.min(doneCount + 1, total) + "/" + total + ") " + progressVal + "%";
                progressLabel.setText(txt);
                if (progressVal > 0) {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setProgress(progressVal);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_edit_note);

        repo = new NotesRepository(this);
        prefs = new PrefsManager(this);

        long id = getIntent().getLongExtra("note_id", 0);
        long defaultCatId = getIntent().getLongExtra("default_category_id", 0);
        unlocked = getIntent().getBooleanExtra("unlocked", false);
        sourceFilter = getIntent().getIntExtra("source_filter", -1);
        isNew = id == 0;
        startedAsNew = isNew;
        if (isNew) {
            note = new Note();
            if (defaultCatId > 0) note.setCategoryId(defaultCatId);
            // Применяем размер текста по умолчанию из настроек (только для новой).
            try { note.setTextSize(new PrefsManager(this).getDefaultTextSize()); } catch (Exception ignored) {}
        } else {
            note = repo.getNoteById(id);
            if (note == null) { finish(); return; }
        }

        registerUnlockLauncher();
        bindViews();
        registerLaunchers();
        registerBackCallback();

        if (note.isLocked() && !unlocked) {
            askUnlock();
            return;
        }
        proceedSetup();
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
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int extra = Math.max(0, imeBottom - sysBottom);
            bottomBar.setPadding(
                    bottomBar.getPaddingLeft(),
                    bottomBar.getPaddingTop(),
                    bottomBar.getPaddingRight(),
                    bottomBarBasePadding + extra
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
            if (!readModeFeatureEnabled) return;
            HapticUtils.light(v);
            setReadMode(!readMode, true);
        });
        if (btnSwitchToEdit != null) {
            btnSwitchToEdit.setOnClickListener(v -> {
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
        });
        btnFav.setOnClickListener(v -> {
            HapticUtils.light(v);
            toggleLock();
        });

        btnUndo.setOnClickListener(v -> { HapticUtils.light(v); doUndo(); });
        btnRedo.setOnClickListener(v -> { HapticUtils.light(v); doRedo(); });

        tvCategory.setOnClickListener(v -> { HapticUtils.light(v); showCategorySheet(); });
        tvReminder.setOnClickListener(v -> { HapticUtils.light(v); showReminderSheet(); });

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

        ignoreTextChange = true;
        etTitle.setText(note.getTitle());
        if (note.getType() == Note.TYPE_TEXT) {
            etContent.setText(note.getContent());
            lastContent = note.getContent();
            showTextMode();
        } else {
            showChecklistMode();
        }
        applyTextSize(note.getTextSize());
        ignoreTextChange = false;


        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreTextChange) return;
                pushUndo(lastContent);
                lastContent = s.toString();
                redoStack.clear();
                updateUndoButtons();
            }
            @Override public void afterTextChanged(Editable s) {
                if (!ignoreTextChange) applyContentLinks();
            }
        });

        btnAddItem.setOnClickListener(v -> { HapticUtils.light(v); checklistAdapter.addNew(); });

        attachmentsAdapter = new AttachmentsEditorAdapter(this, note.getAttachments(),
                new AttachmentsEditorAdapter.Callback() {
                    @Override public void onClick(Attachment a) {
                        if (readMode) return;
                        openAttachment(a);
                    }
                    @Override public void onLongClick(Attachment a) {
                        // long-click сам переключает selection в адаптере;
                        // здесь можем добавить вибрацию
                        HapticUtils.medium(rvAttachments);
                    }
                });
        rvAttachments.setAdapter(attachmentsAdapter);
        setupAttachmentsSelectionBar();
        reloadAttachments();

        applyColor();
        updatePinFav();
        updateCategoryLabel();
        updateReminderLabel();
        updateUndoButtons();
        setupFindInlineBar();
        activeLinksEnabled = prefs.isActiveLinksEnabled();
        applyContentLinks();
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
        updateUndoButtons();

        uiReady = true;
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
        if (note != null && note.getId() > 0) {
            Note fresh = repo.getNoteById(note.getId());
            if (fresh != null) {
                List<Attachment> merged = fresh.getAttachments();
                if (!pendingDeleteAttachmentFiles.isEmpty()) {
                    merged.removeIf(att -> att != null
                            && att.fileName != null
                            && pendingDeleteAttachmentFiles.contains(att.fileName));
                }
                note.setAttachments(merged);
            }
        }
        reloadAttachments();
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

    private void pushUndo(String text) {
        if (undoStack.size() > 100) undoStack.pollLast();
        undoStack.push(text);
    }

    private void doUndo() {
        if (!ensureEditableMode(getString(R.string.edit_note_read_mode_enabled))) return;
        if (undoStack.isEmpty()) return;
        String prev = undoStack.pop();
        redoStack.push(lastContent);
        ignoreTextChange = true;
        etContent.setText(prev);
        etContent.setSelection(prev.length());
        lastContent = prev;
        ignoreTextChange = false;
        updateUndoButtons();
    }

    private void doRedo() {
        if (!ensureEditableMode(getString(R.string.edit_note_read_mode_enabled))) return;
        if (redoStack.isEmpty()) return;
        String next = redoStack.pop();
        undoStack.push(lastContent);
        ignoreTextChange = true;
        etContent.setText(next);
        etContent.setSelection(next.length());
        lastContent = next;
        ignoreTextChange = false;
        updateUndoButtons();
    }

    private void updateUndoButtons() {
        btnUndo.setAlpha((readMode || undoStack.isEmpty()) ? 0.4f : 1f);
        btnRedo.setAlpha((readMode || redoStack.isEmpty()) ? 0.4f : 1f);
        btnUndo.setEnabled(!readMode && !undoStack.isEmpty());
        btnRedo.setEnabled(!readMode && !redoStack.isEmpty());
    }

    private void setReadMode(boolean enabled, boolean fromUser) {
        if (!readModeFeatureEnabled) enabled = false;
        if (readMode == enabled && uiReady) {
            updateReadModeUi();
            return;
        }
        readMode = enabled;

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
        if (btnAddItem != null) btnAddItem.setVisibility(enabled ? View.GONE : (note.getType() == Note.TYPE_CHECKLIST ? View.VISIBLE : View.GONE));

        setEditorActionEnabled(btnPin, !enabled);
        setEditorActionEnabled(btnFav, !enabled);
        setEditorActionEnabled(btnReminderQuick, !enabled);
        setEditorActionEnabled(btnFindReplace, !enabled);
        setEditorActionEnabled(btnPalette, !enabled);
        setEditorActionEnabled(btnCheck, !enabled);
        setEditorActionEnabled(btnAttach, !enabled);
        setEditorActionEnabled(btnMore, !enabled);
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
        updateReadModeUi();

        if (fromUser && root != null) {
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
            btnReadMode.setVisibility(readModeFeatureEnabled ? View.VISIBLE : View.GONE);
            btnReadMode.setImageResource(readMode ? R.drawable.ic_hidden : R.drawable.ic_eye);
            btnReadMode.setColorFilter(ContextCompat.getColor(this,
                    readMode ? R.color.gold_primary : R.color.text_secondary));
            btnReadMode.setContentDescription(readMode ? getString(R.string.edit_mode_toggle_desc) : getString(R.string.edit_note_read_mode_on));
        }
        if (readModeRow != null) {
            readModeRow.setVisibility(readModeFeatureEnabled && readMode ? View.VISIBLE : View.GONE);
        }
        if (searchBarRow != null) {
            if (readMode) searchBarRow.setVisibility(View.GONE);
            else if (isFindMode) searchBarRow.setVisibility(View.VISIBLE);
        }
        if (bottomActionsRow != null) {
            bottomActionsRow.setVisibility(readMode || isFindMode ? View.GONE : View.VISIBLE);
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
        if (note.getCategoryId() <= 0) {
            tvCategory.setText(R.string.no_category);
        } else {
            Category c = repo.getCategoryById(note.getCategoryId());
            tvCategory.setText(c != null ? c.getName() : getString(R.string.no_category));
        }
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

    private void setupContentLinkTouchHandler() {
        if (etContent == null) return;
        etContent.setOnTouchListener(null);
    }

    private void applyContentLinks() {
        if (etContent == null || etContent.getText() == null) return;
        Editable editable = etContent.getText();
        LinkActionSpan[] custom = editable.getSpans(0, editable.length(), LinkActionSpan.class);
        for (LinkActionSpan span : custom) editable.removeSpan(span);
        URLSpan[] urlSpans = editable.getSpans(0, editable.length(), URLSpan.class);
        for (URLSpan span : urlSpans) editable.removeSpan(span);
        if (!activeLinksEnabled) {
            if (!readMode) etContent.setMovementMethod(ArrowKeyMovementMethod.getInstance());
            return;
        }
        try {
            Linkify.addLinks(editable, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
            URLSpan[] spans = editable.getSpans(0, editable.length(), URLSpan.class);
            for (URLSpan span : spans) {
                int start = editable.getSpanStart(span);
                int end = editable.getSpanEnd(span);
                int flags = editable.getSpanFlags(span);
                String url = span.getURL();
                editable.removeSpan(span);
                editable.setSpan(new LinkActionSpan(url, detectLinkType(url)), start, end, flags);
            }
        } catch (Exception ignored) {}
    }

    private int detectLinkType(String url) {
        if (url == null) return LinkActionSpan.TYPE_WEB;
        String lower = url.toLowerCase(Locale.getDefault());
        if (lower.startsWith("mailto:")) return LinkActionSpan.TYPE_EMAIL;
        if (lower.startsWith("tel:")) return LinkActionSpan.TYPE_PHONE;
        return LinkActionSpan.TYPE_WEB;
    }

    private void openLinkAction(int type, String url) {
        if (url == null || url.trim().isEmpty()) return;
        String title;
        Intent intent;
        if (type == LinkActionSpan.TYPE_EMAIL) {
            title = getString(R.string.link_type_email);
            intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
        } else if (type == LinkActionSpan.TYPE_PHONE) {
            title = getString(R.string.link_type_phone);
            intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
        } else {
            title = getString(R.string.link_type_web);
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        }
        String message = url.replace("mailto:", "").replace("tel:", "");
        ConfirmSheet.show(this, title, message, getString(R.string.open), getString(R.string.cancel), false, R.drawable.ic_info, () -> {
            try {
                startActivity(intent);
            } catch (Exception e) {
                Snackbar.make(root, getString(R.string.link_open_failed), Snackbar.LENGTH_SHORT).show();
            }
        }, null);
    }

    private final class LinkActionSpan extends ClickableSpan {
        static final int TYPE_WEB = 0;
        static final int TYPE_EMAIL = 1;
        static final int TYPE_PHONE = 2;

        final String url;
        final int type;

        LinkActionSpan(String url, int type) {
            this.url = url;
            this.type = type;
        }

        @Override
        public void onClick(@NonNull View widget) {
            // Активные ссылки только визуально подчёркиваются, без действий по нажатию.
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setColor(ContextCompat.getColor(EditNoteActivity.this, R.color.text_primary));
            ds.setUnderlineText(true);
        }
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
        note.setTitle(etTitle.getText().toString());
        if (note.getType() == Note.TYPE_TEXT) {
            note.setContent(etContent.getText().toString());
        } else if (checklistAdapter != null) {
            note.setChecklistItems(checklistAdapter.getItems());
        }
        note.setTextSize(note.getTextSize());
    }

    private boolean hasPendingChanges() {
        populateNoteFromUi();
        if (startedAsNew && note.getId() == 0) {
            return !note.isEmpty() || dirty;
        }
        return isNew
                || !initialTitle.equals(note.getTitle())
                || !initialContent.equals(note.getContent())
                || !initialChecklistJson.equals(note.getChecklistJson())
                || !initialAttachmentsJson.equals(note.getAttachmentsJson())
                || initialColor != note.getColorIndex()
                || initialCategoryId != note.getCategoryId()
                || initialPinned != note.isPinned()
                || initialFavorite != note.isFavorite()
                || initialLocked != note.isLocked()
                || initialReminder != note.getReminderAt()
                || initialType != note.getType()
                || initialTextSize != note.getTextSize()
                || !pendingDeleteAttachmentFiles.isEmpty()
                || dirty;
    }

    private boolean saveNote(boolean silent) {
        populateNoteFromUi();
        if (note.isEmpty()) {
            // Новую пустую заметку — не создаём.
            if (isNew) {
                pendingDeleteAttachmentFiles.clear();
                if (!silent) Snackbar.make(root, R.string.msg_empty_note, Snackbar.LENGTH_SHORT).show();
                return false;
            }
            // Существующую заметку, ставшую пустой (после редактирования) — НЕ удаляем,
            // а сохраняем как пустую. Удалением пусть управляет пользователь явно.
            // При этом важно «дочистить» файлы вложений, которые юзер удалил.
            if (!pendingDeleteAttachmentFiles.isEmpty()) {
                DeleteFilesHelper.deleteFiles(this, new ArrayList<>(pendingDeleteAttachmentFiles));
                pendingDeleteAttachmentFiles.clear();
            }
            repo.updateNote(note);
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
            dirty = false;
            if (note.getReminderAt() > System.currentTimeMillis()) {
                ReminderUtils.schedule(this, note.getId(), note.getReminderAt());
            } else {
                ReminderUtils.cancel(this, note.getId());
            }
            if (!silent) Snackbar.make(root, R.string.msg_saved, Snackbar.LENGTH_SHORT).show();
            return true;
        }
        boolean realChanged = isNew
                || !initialTitle.equals(note.getTitle())
                || !initialContent.equals(note.getContent())
                || !initialChecklistJson.equals(note.getChecklistJson())
                || !initialAttachmentsJson.equals(note.getAttachmentsJson())
                || initialColor != note.getColorIndex()
                || initialCategoryId != note.getCategoryId()
                || initialPinned != note.isPinned()
                || initialFavorite != note.isFavorite()
                || initialLocked != note.isLocked()
                || initialReminder != note.getReminderAt()
                || initialType != note.getType()
                || initialTextSize != note.getTextSize()
                || dirty;
        if (!realChanged) {
            return true;
        }
        if (isNew) {
            repo.addNote(note);
            isNew = false;
            initialTitle = note.getTitle();
            initialContent = note.getContent();
            initialChecklistJson = note.getChecklistJson();
            initialAttachmentsJson = note.getAttachmentsJson();
        } else {
            repo.updateNote(note);
        }
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
        dirty = false;
        if (note.getReminderAt() > System.currentTimeMillis()) {
            ReminderUtils.schedule(this, note.getId(), note.getReminderAt());
        } else {
            ReminderUtils.cancel(this, note.getId());
        }
        if (!silent) Snackbar.make(root, R.string.msg_saved, Snackbar.LENGTH_SHORT).show();
        flushPendingAttachmentDeletes();
        return true;
    }

    private void commitIfAutoSaveEnabled() {
        dirty = true;
        if (!prefs.isConfirmSaveOnExitEnabled()) {
            saveNote(true);
        }
    }

    private void forceSaveNew() {
        populateNoteFromUi();
        if (isNew) {
            repo.addNote(note);
            isNew = false;
        } else {
            repo.updateNote(note);
        }
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
        saveNote(true);
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void discardAndExitNow() {
        suppressAutoSaveOnStop = true;
        restoreInitialSnapshotAndCleanup();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void restoreInitialSnapshotAndCleanup() {
        try {
            if (note == null) return;
            if (startedAsNew) {
                if (note.getId() > 0) {
                    List<String> files = repo.deleteForever(note.getId());
                    DeleteFilesHelper.deleteFiles(this, files);
                }
                if (pendingCameraFile != null && pendingCameraFile.exists()) {
                    pendingCameraFile.delete();
                }
                return;
            }
            Note dbNote = repo.getNoteById(note.getId());
            if (dbNote != null) {
                Set<String> initialFiles = toFileSet(initialAttachmentsJson);
                Set<String> currentFiles = toFileSet(dbNote.getAttachmentsJson());
                List<String> filesToDelete = new ArrayList<>();
                for (String file : currentFiles) {
                    if (!initialFiles.contains(file)) filesToDelete.add(file);
                }
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
            pendingDeleteAttachmentFiles.clear();
            dirty = false;
        } catch (Exception ignored) {}
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
        if (!ensureEditableMode(null)) return;
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
            populateNoteFromUi();
            Note dup = new Note();
            dup.setTitle(note.getTitle());
            dup.setContent(note.getContent());
            dup.setChecklistJson(note.getChecklistJson());
            dup.setAttachmentsJson(note.getAttachmentsJson());
            dup.setType(note.getType());
            dup.setColorIndex(note.getColorIndex());
            dup.setCategoryId(note.getCategoryId());
            dup.setTextSize(note.getTextSize());
            repo.addNote(dup);
            Snackbar.make(root, R.string.msg_duplicated, Snackbar.LENGTH_SHORT).show();
        });

        aArchive.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            if (openedFromArchive) {
                repo.unarchiveNote(note.getId());
                Intent res = new Intent();
                res.putExtra(RESULT_ACTION, ACTION_UNARCHIVED);
                res.putExtra(RESULT_NOTE_ID, note.getId());
                setResult(RESULT_OK, res);
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return;
            }
            if (note.isLocked()) {
                Snackbar.make(root, getString(R.string.remove_protection_first), Snackbar.LENGTH_SHORT).show();
                return;
            }
            if (saveNote(true)) {
                repo.archiveNote(note.getId());
                Intent res = new Intent();
                res.putExtra(RESULT_ACTION, ACTION_ARCHIVED);
                res.putExtra(RESULT_NOTE_ID, note.getId());
                setResult(RESULT_OK, res);
            }
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
            repo.restoreFromTrash(note.getId());
            Intent res = new Intent();
            res.putExtra(RESULT_ACTION, ACTION_RESTORED);
            res.putExtra(RESULT_NOTE_ID, note.getId());
            setResult(RESULT_OK, res);
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        aFavorite.setOnClickListener(view -> {
            HapticUtils.light(view); sheet.dismiss();
            note.setFavorite(!note.isFavorite());
            commitIfAutoSaveEnabled();
            updatePinFav();
            Snackbar.make(root, note.isFavorite() ? getString(R.string.edit_note_added_to_favorites) : getString(R.string.edit_note_removed_from_favorites),
                    Snackbar.LENGTH_SHORT).show();
        });

        aDelete.setOnClickListener(view -> {
            HapticUtils.medium(view); sheet.dismiss();
            if (openedFromTrash) {
                ConfirmSheet.show(this,
                        getString(R.string.main_delete_note_forever_title),
                        getString(R.string.main_delete_note_forever_msg),
                        getString(R.string.delete), true, () -> {
                            List<String> files = repo.deleteForever(note.getId());
                            DeleteFilesHelper.deleteFiles(this, files);
                            Intent res = new Intent();
                            res.putExtra(RESULT_ACTION, ACTION_DELETED_FOREVER);
                            res.putExtra(RESULT_NOTE_ID, note.getId());
                            setResult(RESULT_OK, res);
                            finish();
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
                            repo.moveToTrash(note.getId());
                            ReminderUtils.cancel(this, note.getId());
                            Intent res = new Intent();
                            res.putExtra(RESULT_ACTION, ACTION_TRASHED);
                            res.putExtra(RESULT_NOTE_ID, note.getId());
                            setResult(RESULT_OK, res);
                        }
                        finish();
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_category_picker, null);
        sheet.setContentView(v);
        LinearLayout list = v.findViewById(R.id.cat_list);
        LayoutInflater inf = LayoutInflater.from(this);
        list.removeAllViews();
        View none = inf.inflate(R.layout.item_category_pick, list, false);
        ((TextView) none.findViewById(R.id.tv_name)).setText(R.string.no_category);
        none.findViewById(R.id.v_dot).setVisibility(View.INVISIBLE);
        none.findViewById(R.id.iv_check).setVisibility(note.getCategoryId() == 0 ? View.VISIBLE : View.INVISIBLE);
        none.setOnClickListener(view -> {
            HapticUtils.light(view);
            note.setCategoryId(0);
            updateCategoryLabel();
            sheet.dismiss();
        });
        list.addView(none);
        for (Category c : repo.getAllCategories()) {
            View row = inf.inflate(R.layout.item_category_pick, list, false);
            ((TextView) row.findViewById(R.id.tv_name)).setText(c.getName());
            View dot = row.findViewById(R.id.v_dot);
            GradientDrawable gd = (GradientDrawable) dot.getBackground().mutate();
            gd.setColor(ColorUtils.getCategoryColor(this, c.getColorIndex()));
            row.findViewById(R.id.iv_check).setVisibility(
                    note.getCategoryId() == c.getId() ? View.VISIBLE : View.INVISIBLE);
            row.setOnClickListener(view -> {
                HapticUtils.light(view);
                note.setCategoryId(c.getId());
                updateCategoryLabel();
                sheet.dismiss();
            });
            list.addView(row);
        }
        sheet.show();
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
            Intent i = new Intent(this, ImageViewerActivity.class);
            i.putExtra("path", f.getAbsolutePath());
            i.putExtra("name", a.displayName);
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else if (a.type == Attachment.TYPE_AUDIO) {
            Intent i = new Intent(this, AudioPlayerActivity.class);
            i.putExtra("path", f.getAbsolutePath());
            i.putExtra("name", a.displayName);
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else if (a.type == Attachment.TYPE_VIDEO) {
            Intent i = new Intent(this, VideoPlayerActivity.class);
            i.putExtra("path", f.getAbsolutePath());
            i.putExtra("name", a.displayName);
            startActivity(i);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
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
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!isFindMode) return;
                    highlightMatchesInternal(s == null ? "" : s.toString(), false);
                }
                @Override public void afterTextChanged(Editable s) {}
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
        if (bottomActionsRow != null) bottomActionsRow.setVisibility(View.GONE);
        if (searchBarRow != null) searchBarRow.setVisibility(View.VISIBLE);
        if (etFindInline != null) {
            etFindInline.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etFindInline, 0);
        }
    }

    private void exitFindMode() {
        isFindMode = false;
        clearFindHighlight();
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

    private void highlightMatches(String query) {
        highlightMatchesInternal(query, true);
    }

    private void highlightMatchesInternal(String query, boolean showMessage) {
        clearFindHighlight();
        if (query == null || query.trim().isEmpty()) {
            if (showMessage) {
                Snackbar.make(root, getString(R.string.search_text_prompt), Snackbar.LENGTH_SHORT).show();
            }
            return;
        }
        String text = etContent.getText() == null ? "" : etContent.getText().toString();
        if (text.isEmpty()) return;
        String q = query.toLowerCase(Locale.getDefault());
        String src = text.toLowerCase(Locale.getDefault());
        Editable editable = etContent.getText();
        int from = 0;
        int hits = 0;
        while (from < src.length()) {
            int idx = src.indexOf(q, from);
            if (idx < 0) break;
            int highlightColor = (ContextCompat.getColor(this, R.color.success) & 0x00FFFFFF) | 0x66000000;
            editable.setSpan(new BackgroundColorSpan(highlightColor), idx, idx + q.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            hits++;
            from = idx + q.length();
        }
        if (!showMessage) return;
        if (hits == 0) {
            Snackbar.make(root, getString(R.string.search_no_matches), Snackbar.LENGTH_SHORT).show();
        } else {
            Snackbar.make(root, getString(R.string.search_found_count, hits), Snackbar.LENGTH_SHORT).show();
        }
    }

    private int replaceAllInContent(String query, String replacement) {
        clearFindHighlight();
        if (query == null || query.isEmpty()) return 0;
        String text = etContent.getText() == null ? "" : etContent.getText().toString();
        if (text.isEmpty()) return 0;
        int count = 0;
        int idx = text.indexOf(query);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(query, idx + query.length());
        }
        if (count == 0) return 0;
        String replaced = text.replace(query, replacement == null ? "" : replacement);
        ignoreTextChange = true;
        etContent.setText(replaced);
        etContent.setSelection(Math.min(replaced.length(), etContent.getText().length()));
        lastContent = replaced;
        ignoreTextChange = false;
        dirty = true;
        return count;
    }

    private void clearFindHighlight() {
        if (etContent == null || etContent.getText() == null) return;
        Editable editable = etContent.getText();
        BackgroundColorSpan[] spans = editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
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
        if (uiReady && attachmentsAdapter != null && note != null && !isNew) syncAttachmentsFromDb();
        if (!AttachmentCopyService.PENDING.containsKey(note != null ? note.getId() : 0L)
                && progressBar != null) {
            progressBar.setVisibility(View.GONE);
            copyInProgress = false;
        }
        if (uiReady) {
            readModeFeatureEnabled = prefs.isEditorReadModeEnabled();
            activeLinksEnabled = prefs.isActiveLinksEnabled();
            applyContentLinks();
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
        if (suppressAutoSaveOnStop) return;
        if (!uiReady || note == null) return;
        if (prefs.isConfirmSaveOnExitEnabled()) return;
        if (copyInProgress) return;
        try {
            saveNote(true);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        stopReadAloud();
        clearFindHighlight();
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
        }
        super.onDestroy();
    }
}
