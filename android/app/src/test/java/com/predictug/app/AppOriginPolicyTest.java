package com.predictug.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppOriginPolicyTest {
    @Test
    public void trustedOriginsRequireExactHttpsHostMatch() {
        assertTrue(AppOriginPolicy.isInternal("https", "appassets.androidplatform.net"));
        assertTrue(AppOriginPolicy.isInternal("HTTPS", "APPASSETS.ANDROIDPLATFORM.NET"));
        assertTrue(AppOriginPolicy.isInternal("https", "predict-ug-app.onrender.com"));
        assertTrue(AppOriginPolicy.isInternal("HTTPS", "PREDICT-UG-APP.ONRENDER.COM"));

        assertTrue(AppOriginPolicy.isLive("https", "predict-ug-app.onrender.com"));
        assertTrue(AppOriginPolicy.isBundled("https", "appassets.androidplatform.net"));

        assertFalse(AppOriginPolicy.isInternal("http", "appassets.androidplatform.net"));
        assertFalse(AppOriginPolicy.isInternal("http", "predict-ug-app.onrender.com"));
        assertFalse(AppOriginPolicy.isInternal("https", "predict-ug-app.onrender.com.evil.example"));
        assertFalse(AppOriginPolicy.isInternal("https", "evilpredict-ug-app.onrender.com"));
        assertFalse(AppOriginPolicy.isInternal(null, null));
    }

    @Test
    public void untrustedSubframesAreBlockedFromBridgeWebView() {
        assertTrue(AppOriginPolicy.shouldBlockUntrustedSubframe(
                "https", "example.com", false
        ));
        assertTrue(AppOriginPolicy.shouldBlockUntrustedSubframe(
                "predictug", "merchant", false
        ));
        assertTrue(AppOriginPolicy.shouldBlockUntrustedSubframe(
                "http", "appassets.androidplatform.net", false
        ));
        assertFalse(AppOriginPolicy.shouldBlockUntrustedSubframe(
                "https", "appassets.androidplatform.net", false
        ));
        assertFalse(AppOriginPolicy.shouldBlockUntrustedSubframe(
                "https", "predict-ug-app.onrender.com", false
        ));
        assertFalse(AppOriginPolicy.shouldBlockUntrustedSubframe(
                "https", "example.com", true
        ));
    }

    @Test
    public void merchantDeepLinkSchemeIsExplicit() {
        assertTrue(AppOriginPolicy.isMerchantScheme("predictug"));
        assertTrue(AppOriginPolicy.isMerchantScheme("PREDICTUG"));
        assertFalse(AppOriginPolicy.isMerchantScheme("https"));
        assertFalse(AppOriginPolicy.isMerchantScheme(null));
    }
}
