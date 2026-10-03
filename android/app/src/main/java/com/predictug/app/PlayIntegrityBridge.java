package com.predictug.app;

import android.app.Activity;

import androidx.webkit.JavaScriptReplyProxy;

import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.StandardIntegrityManager;

import org.json.JSONObject;

public final class PlayIntegrityBridge {
    private final Activity activity;
    private final long cloudProjectNumber;
    private final StandardIntegrityManager manager;
    private volatile StandardIntegrityManager.StandardIntegrityTokenProvider provider;
    private volatile boolean preparing;

    PlayIntegrityBridge(Activity activity, long cloudProjectNumber) {
        this.activity = activity;
        this.cloudProjectNumber = cloudProjectNumber;
        this.manager = IntegrityManagerFactory.createStandard(activity.getApplicationContext());
        if (cloudProjectNumber > 0) {
            activity.runOnUiThread(this::prepareInternal);
        }
    }

    void handleMessage(String rawMessage, JavaScriptReplyProxy replyProxy) {
        try {
            JSONObject request = new JSONObject(rawMessage == null ? "{}" : rawMessage);
            String action = request.optString("action", "");
            String callbackId = request.optString("callback_id", "");

            switch (action) {
                case "status":
                    deliver(
                            replyProxy,
                            callbackId,
                            null,
                            null,
                            cloudProjectNumber > 0,
                            provider != null,
                            preparing
                    );
                    return;
                case "prepare":
                    if (cloudProjectNumber <= 0) {
                        deliver(replyProxy, callbackId, null, "not_configured", false, false, false);
                        return;
                    }
                    activity.runOnUiThread(() -> {
                        prepareInternal();
                        deliver(
                                replyProxy,
                                callbackId,
                                null,
                                null,
                                true,
                                provider != null,
                                preparing
                        );
                    });
                    return;
                case "token":
                    requestToken(
                            request.optString("request_hash", ""),
                            callbackId,
                            replyProxy
                    );
                    return;
                default:
                    deliver(
                            replyProxy,
                            callbackId,
                            null,
                            "unsupported_action",
                            cloudProjectNumber > 0,
                            provider != null,
                            preparing
                    );
            }
        } catch (Exception error) {
            deliver(
                    replyProxy,
                    "",
                    null,
                    "invalid_message",
                    cloudProjectNumber > 0,
                    provider != null,
                    preparing
            );
        }
    }

    private void requestToken(
            String requestHash,
            String callbackId,
            JavaScriptReplyProxy replyProxy
    ) {
        final String hash = requestHash == null ? "" : requestHash.trim();
        if (cloudProjectNumber <= 0) {
            deliver(replyProxy, callbackId, null, "not_configured", false, false, false);
            return;
        }
        if (hash.isEmpty() || hash.length() > 500) {
            deliver(
                    replyProxy,
                    callbackId,
                    null,
                    "invalid_request_hash",
                    true,
                    provider != null,
                    preparing
            );
            return;
        }

        activity.runOnUiThread(() -> {
            StandardIntegrityManager.StandardIntegrityTokenProvider current = provider;
            if (current == null) {
                prepareInternal();
                deliver(
                        replyProxy,
                        callbackId,
                        null,
                        "provider_not_ready",
                        true,
                        false,
                        preparing
                );
                return;
            }

            current.request(
                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                            .setRequestHash(hash)
                            .build()
            ).addOnSuccessListener(response ->
                    deliver(replyProxy, callbackId, response.token(), null, true, true, false)
            ).addOnFailureListener(error -> {
                provider = null;
                prepareInternal();
                deliver(
                        replyProxy,
                        callbackId,
                        null,
                        "token_request_failed",
                        true,
                        false,
                        preparing
                );
            });
        });
    }

    private void prepareInternal() {
        if (cloudProjectNumber <= 0 || preparing || provider != null) {
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

    private void deliver(
            JavaScriptReplyProxy replyProxy,
            String callbackId,
            String token,
            String error,
            boolean configured,
            boolean ready,
            boolean isPreparing
    ) {
        try {
            JSONObject response = new JSONObject();
            response.put("callback_id", callbackId == null ? "" : callbackId);
            response.put("configured", configured);
            response.put("ready", ready);
            response.put("preparing", isPreparing);
            if (token != null) response.put("token", token);
            if (error != null) response.put("error", error);

            String payload = response.toString();
            activity.runOnUiThread(() -> {
                try {
                    replyProxy.postMessage(payload);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
