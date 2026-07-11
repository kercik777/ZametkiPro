package com.zametki.pro.utils;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.zametki.pro.services.AttachmentDeleteService;

import java.util.ArrayList;
import java.util.List;

public class DeleteFilesHelper {

    public static void deleteFiles(Context ctx, List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) return;
        ArrayList<String> list = new ArrayList<>(fileNames);

        if (list.size() <= 5) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                for (String n : list) {
                    AttachmentUtils.deleteAttachmentFile(ctx, n);
                }
            });
            return;
        }

        Intent svc = new Intent(ctx, AttachmentDeleteService.class);
        svc.putStringArrayListExtra(AttachmentDeleteService.EXTRA_FILE_NAMES, list);
        try {
            ContextCompat.startForegroundService(ctx, svc);
        } catch (Exception e) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                for (String n : list) {
                    AttachmentUtils.deleteAttachmentFile(ctx, n);
                }
            });
        }
    }
}
