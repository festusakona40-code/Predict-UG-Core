package com.predictug.app;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.StandardIntegrityManager;

import org.json.JSONObject;

public final class PlayIntegrityBridge {
    private final Activity activity;
    private final WebView webView;
    private final long cloudProjectNumber;
    private final StandardIntegrityManager manager;
    private volatile StandardIntegrityManager.StandardIntegrityTokenProvider provider;
    private volatile boolean preparing;

    PlayIntegrityBridge(Activity activity, WebView webView, long cloudProjectNumber) {
        this.activity = activity;
        this.webView = webView;
        this.cloudProjectNumber = cloudProjectNumber;
        this.manager = IntegrityManagerFactory.createStandard(activity.getApplicationContext());
        if (cloudProjectNumber > 0) {
            activity.runOnUiThread(this::prepareInternal);
        }
    }

    @JavascriptInterface
    public boolean isConfigured() {
        return cloudProjectNumber > 0;
    }

    @JavascriptInterface
    public boolean isReady() {
        return provider != null;
    }

    @JavascriptInterface
    public void prepare() {
        if (cloudProjectNumber <= 0) {
            return;
        }
        activity.runOnUiThread(this::prepareInternal);
    }

    @JavascriptInterface
    public void requestToken(String requestHash, String callbackId) {
        final String hash = requestHash == null ? "" : requestHash.trim();
        final String callback = callbackId == null ? "" : callbackId.trim();

        if (cloudProjectNumber <= 0) {
            deliver(callback, null, "not_configured");
            return;
        }
        if (hash.isEmpty() || hash.length() > 500) {
            deliver(callback, null, "invalid_request_hash");
            return;
        }

        activity.runOnUiThread(() -> {
            StandardIntegrityManager.StandardIntegrityTokenProvider current = provider;
            if (current == null) {
                prepareInternal();
                deliver(callback, null, "provider_not_ready");
                return;
            }

            current.request(
                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                            .setRequestHash(hash)
                            .build()
            ).addOnSuccessListener(response ->
                    deliver(callback, response.token(), null)
            ).addOnFailureListener(error -> {
                provider = null;
                prepareInternal();
                deliver(callback, null, "token_request_failed");
            });
        });
    }

    private void prepareInternal() {
        if (cloudProjectNumber <= 0 || preparing) {
            return;
        }
        preparing = true;
        manager.prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(cloudProjectNumber)
                        .build()
        ).addOnSuccessListener(tokenProvider -> {
            provider = tokenProvider;
            preparing = false;
        }).addOnFailureListener(error -> {
            provider = null;
            preparing = false;
        });
    }

    private void deliver(String callbackId, String token, String error) {
        final String script =
                "window.__predictUgIntegrityResult&&window.__predictUgIntegrityResult(" +
                JSONObject.quote(callbackId) + "," +
                (token == null ? "null" : JSONObject.quote(token)) + "," +
                (error == null ? "null" : JSONObject.quote(error)) + ");";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
}
