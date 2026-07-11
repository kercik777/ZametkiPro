package com.zametki.pro.utils;

import com.zametki.pro.R;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class BiometricHelper {

    public interface Callback {
        void onSuccess();
        void onError(String msg);
        void onCancel();
    }

    public static boolean canUseBiometric(androidx.fragment.app.FragmentActivity activity) {
        BiometricManager bm = BiometricManager.from(activity);
        int res = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return res == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void prompt(FragmentActivity activity, String title, String subtitle, Callback cb) {
        Executor exec = androidx.core.content.ContextCompat.getMainExecutor(activity);
        BiometricPrompt prompt = new BiometricPrompt(activity, exec,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        if (cb != null) cb.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        if (cb != null) {
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_CANCELED) {
                                cb.onCancel();
                            } else {
                                cb.onError(String.valueOf(errString));
                            }
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Просто пропускаем — пользователь может попробовать ещё
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle == null ? "" : subtitle)
                .setNegativeButtonText(activity.getString(R.string.biometric_use_password))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();

        prompt.authenticate(info);
    }
}
