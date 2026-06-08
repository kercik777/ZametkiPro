package com.zametki.pro.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutors {

    private static final int THREADS = Runtime.getRuntime().availableProcessors() + 1;
    private static AppExecutors instance;

    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final Handler mainThread;

    private AppExecutors() {
        this.diskIO = Executors.newSingleThreadExecutor();
        this.networkIO = Executors.newFixedThreadPool(THREADS);
        this.mainThread = new Handler(Looper.getMainLooper());
    }

    public static synchronized AppExecutors getInstance() {
        if (instance == null) {
            instance = new AppExecutors();
        }
        return instance;
    }

    public ExecutorService diskIO() {
        return diskIO;
    }

    public ExecutorService networkIO() {
        return networkIO;
    }

    public void mainThread(Runnable runnable) {
        mainThread.post(runnable);
    }

    public void shutdown() {
        diskIO.shutdown();
        networkIO.shutdown();
    }
}
