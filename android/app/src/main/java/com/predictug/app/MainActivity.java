package com.predictug.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends ComponentActivity {
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net";
    private static final String APP_URL = APP_ORIGIN + "/assets/index.html";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NativeCrashReporter.install(this, "android", BuildConfig.VERSION_NAME);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 20, 38));
        setContentView(webView);

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        settings.setUserAgentString(settings.getUserAgentString() + " PredictUG/" + BuildConfig.VERSION_NAME);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(
                new PlayIntegrityBridge(this, webView, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER),
                "PredictUGIntegrity"
        );
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse local = assetLoader.shouldInterceptRequest(request.getUrl());
                return local != null ? local : super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (AppOriginPolicy.isInternal(uri.getScheme(), uri.getHost())) {
                    return false;
                }
                if (!request.isForMainFrame()) {
                    return false;
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
            webView.loadUrl(APP_URL);
        }
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
