package com.predictug.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class MainActivity extends ComponentActivity {
    private static final String BUNDLED_APP_ORIGIN = "https://appassets.androidplatform.net";
    private static final String BUNDLED_APP_URL = BUNDLED_APP_ORIGIN + "/assets/index.html";
    private static final String LIVE_APP_ORIGIN = "https://predict-ug-app.onrender.com";
    private static final String LIVE_APP_URL = LIVE_APP_ORIGIN + "/?android=1";

    private WebView webView;
    private boolean pageFinishedForShare = false;
    private String pendingSharedText = null;
    private String pendingSharedTitle = null;
    private Uri pendingSharedImageUri = null;
    private boolean sharedImageProcessing = false;
    private boolean bundledFallbackStarted = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NativeCrashReporter.install(this, "android", BuildConfig.VERSION_NAME);
        captureSharedContent(getIntent());

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        final TextView loading = new TextView(this);
        loading.setText("Opening Predict UG…");
        loading.setTextColor(Color.DKGRAY);
        loading.setTextSize(18f);
        loading.setGravity(Gravity.CENTER);
        root.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);

        // Draw a native frame before Android initializes WebView. Some devices can
        // spend long enough constructing WebView that the OS splash screen appears
        // frozen. Posting initialization guarantees the launch screen is dismissed
        // first and gives us a visible recovery surface instead of an apparent crash.
        root.post(() -> initializeWebView(root, loading, savedInstanceState));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initializeWebView(
            FrameLayout root,
            TextView loading,
            Bundle savedInstanceState
    ) {
        if (isFinishing()) return;

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        try {
            webView = new WebView(this);
        } catch (Throwable error) {
            NativeCrashReporter.reportNonFatal(
                    this,
                    "android",
                    BuildConfig.VERSION_NAME,
                    "WebView initialization failed",
                    String.valueOf(error)
            );
            loading.setText("Predict UG could not start Android WebView. Close and reopen the app.");
            Toast.makeText(
                    this,
                    "Predict UG startup failed. Android System WebView may need an update.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        webView.setBackgroundColor(Color.WHITE);
        root.removeAllViews();
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        processPendingSharedImage();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                if (!isFinishing()) {
                    setEnabled(true);
                }
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                    settings,
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            );
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        settings.setUserAgentString(settings.getUserAgentString() + " PredictUG/" + BuildConfig.VERSION_NAME);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null
                        && consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    NativeCrashReporter.reportNonFatal(
                            MainActivity.this,
                            "android-webview",
                            BuildConfig.VERSION_NAME,
                            "WebView console error",
                            consoleMessage.message()
                                    + " @ "
                                    + consoleMessage.sourceId()
                                    + ":"
                                    + consoleMessage.lineNumber()
                    );
                }
                return super.onConsoleMessage(consoleMessage);
            }
        });
        final PlayIntegrityBridge integrityBridge =
                new PlayIntegrityBridge(this, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                    webView,
                    "PredictUGIntegrity",
                    new HashSet<>(Arrays.asList(BUNDLED_APP_ORIGIN, LIVE_APP_ORIGIN)),
                    (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                        if (!isMainFrame
                                || !AppOriginPolicy.isInternal(
                                        sourceOrigin.getScheme(),
                                        sourceOrigin.getHost()
                                )
                                || message.getType() != WebMessageCompat.TYPE_STRING) {
                            return;
                        }
                        integrityBridge.handleMessage(message.getData(), replyProxy);
                    }
            );
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse local = assetLoader.shouldInterceptRequest(request.getUrl());
                return local != null ? local : super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageFinishedForShare = true;
                deliverPendingSharedText();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (webView == null || isFinishing()) return;
                    webView.evaluateJavascript(
                            "(function(){return window.__predictUgBootReady===true?'ready':String(window.__predictUgBootPhase||'not-ready')})()",
                            value -> {
                                if (value == null || !value.contains("ready")) {
                                    NativeCrashReporter.reportNonFatal(
                                            MainActivity.this,
                                            "android-webview",
                                            BuildConfig.VERSION_NAME,
                                            "Predict UG startup watchdog",
                                            "WebView boot state after 15s: " + String.valueOf(value)
                                                    + ", url=" + String.valueOf(url)
                                    );
                                    Uri finished = Uri.parse(String.valueOf(url));
                                    if (AppOriginPolicy.isLive(
                                            finished.getScheme(),
                                            finished.getHost()
                                    ) && !bundledFallbackStarted) {
                                        loadBundledFallback(
                                                "Live Predict UG did not finish starting. Opening the bundled fallback."
                                        );
                                        return;
                                    }
                                    Toast.makeText(
                                            MainActivity.this,
                                            "Predict UG startup is taking too long. Tap Retry in the app.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                            }
                    );
                }, 15000);
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    String description = error == null
                            ? "Unknown WebView load error"
                            : String.valueOf(error.getDescription());
                    Uri failedUrl = request.getUrl();
                    NativeCrashReporter.reportNonFatal(
                            MainActivity.this,
                            "android-webview",
                            BuildConfig.VERSION_NAME,
                            "WebView main-frame load error",
                            description + ", url=" + String.valueOf(failedUrl)
                    );
                    if (failedUrl != null
                            && AppOriginPolicy.isLive(
                                    failedUrl.getScheme(),
                                    failedUrl.getHost()
                            )
                            && !bundledFallbackStarted) {
                        loadBundledFallback(
                                "Live Predict UG is unavailable. Opening the bundled offline fallback."
                        );
                        return;
                    }
                    Toast.makeText(
                            MainActivity.this,
                            "Predict UG could not load its app shell. Reopen the app.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (AppOriginPolicy.isInternal(uri.getScheme(), uri.getHost())) {
                    return false;
                }
                if (AppOriginPolicy.shouldBlockUntrustedSubframe(
                        uri.getScheme(),
                        uri.getHost(),
                        request.isForMainFrame()
                )) {
                    return true;
                }
                // Merchant pairing is handled by the separate private app.
                // The consumer package must target the merchant applicationId,
                // not itself, otherwise the tap silently resolves nowhere.
                if (AppOriginPolicy.isMerchantScheme(uri.getScheme())) {
                    Intent merchant = new Intent(Intent.ACTION_VIEW, uri);
                    merchant.setPackage("com.predictug.app.merchant");
                    try {
                        startActivity(merchant);
                    } catch (ActivityNotFoundException missingMerchant) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        } catch (ActivityNotFoundException noHandler) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Install the Predict UG Merchant app, then tap Connect again.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                    return true;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                String rendererDetails = "renderer process exited";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    rendererDetails =
                            "didCrash=" + detail.didCrash()
                                    + ", priorityAtExit=" + detail.rendererPriorityAtExit();
                }
                NativeCrashReporter.reportNonFatal(
                        MainActivity.this,
                        "android",
                        BuildConfig.VERSION_NAME,
                        "WebView renderer process exited",
                        rendererDetails
                );
                try {
                    if (view.getParent() instanceof ViewGroup) {
                        ((ViewGroup) view.getParent()).removeView(view);
                    }
                    view.destroy();
                } catch (Exception ignored) {
                }
                webView = null;
                Toast.makeText(
                        MainActivity.this,
                        "Predict UG recovered from an Android WebView problem.",
                        Toast.LENGTH_SHORT
                ).show();
                recreate();
                return true;
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(LIVE_APP_URL);
        }
    }

    private void loadBundledFallback(String message) {
        if (bundledFallbackStarted || webView == null || isFinishing()) return;
        bundledFallbackStarted = true;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        webView.loadUrl(BUNDLED_APP_URL);
    }

    private static String trimShared(CharSequence value, int max) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }

    private Uri sharedImageUri(Intent intent) {
        if (intent == null) return null;

        Uri uri = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                //noinspection deprecation
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        } catch (Exception ignored) {
        }

        if (uri != null) return uri;

        ClipData clipData = intent.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).getUri();
        }
        return null;
    }

    private void captureSharedContent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;

        String type = intent.getType();
        if (type == null) return;
        String normalizedType = type.toLowerCase();
        String title = trimShared(intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT), 240);

        if (normalizedType.startsWith("text/")) {
            String text = trimShared(intent.getCharSequenceExtra(Intent.EXTRA_TEXT), 6000);
            if (text == null && title == null) return;

            pendingSharedImageUri = null;
            pendingSharedText = text != null ? text : title;
            pendingSharedTitle = title;
            return;
        }

        if (normalizedType.startsWith("image/")) {
            Uri imageUri = sharedImageUri(intent);
            if (imageUri == null) return;

            pendingSharedText = null;
            pendingSharedTitle = title;
            pendingSharedImageUri = imageUri;
        }
    }

    private void processPendingSharedImage() {
        if (sharedImageProcessing || pendingSharedImageUri == null) return;

        final Uri imageUri = pendingSharedImageUri;
        final InputImage image;
        try {
            image = InputImage.fromFilePath(this, imageUri);
        } catch (IOException | SecurityException error) {
            pendingSharedImageUri = null;
            NativeCrashReporter.reportNonFatal(
                    this,
                    "android-share-ocr",
                    BuildConfig.VERSION_NAME,
                    "Could not open shared screenshot",
                    String.valueOf(error)
            );
            Toast.makeText(
                    this,
                    "Predict UG could not read that screenshot. Try sharing it again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        sharedImageProcessing = true;
        final TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    String extracted = trimShared(result.getText(), 6000);
                    pendingSharedImageUri = null;

                    if (extracted == null) {
                        Toast.makeText(
                                MainActivity.this,
                                "No readable text was found in that screenshot.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    pendingSharedText = extracted;
                    deliverPendingSharedText();
                })
                .addOnFailureListener(error -> {
                    pendingSharedImageUri = null;
                    NativeCrashReporter.reportNonFatal(
                            MainActivity.this,
                            "android-share-ocr",
                            BuildConfig.VERSION_NAME,
                            "Screenshot text extraction failed",
                            String.valueOf(error)
                    );
                    Toast.makeText(
                            MainActivity.this,
                            "Predict UG could not extract text from that screenshot.",
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnCompleteListener(task -> {
                    sharedImageProcessing = false;
                    recognizer.close();
                });
    }

    private void deliverPendingSharedText() {
        if (!pageFinishedForShare || webView == null || pendingSharedText == null) return;

        try {
            JSONObject payload = new JSONObject();
            payload.put("text", pendingSharedText);
            if (pendingSharedTitle != null) payload.put("title", pendingSharedTitle);
            payload.put("source", "android_share");
            payload.put("received_at_ms", System.currentTimeMillis());

            String script =
                    "(function(){try{var p=" + payload.toString()
                            + ";localStorage.setItem('predictug_pending_shared_claim_v1',JSON.stringify(p));"
                            + "if(typeof window.__predictUgReceiveSharedClaim==='function'){"
                            + "window.__predictUgReceiveSharedClaim(p);}"
                            + "return 'stored';}catch(e){return 'error';}})()";

            webView.evaluateJavascript(script, value -> {
                if (value != null && !value.contains("error")) {
                    pendingSharedText = null;
                    pendingSharedTitle = null;
                }
            });
        } catch (Exception error) {
            NativeCrashReporter.reportNonFatal(
                    this,
                    "android-share",
                    BuildConfig.VERSION_NAME,
                    "Could not hand shared text to Claim Checker",
                    String.valueOf(error)
            );
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureSharedContent(intent);
        processPendingSharedImage();
        deliverPendingSharedText();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        pageFinishedForShare = false;
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
