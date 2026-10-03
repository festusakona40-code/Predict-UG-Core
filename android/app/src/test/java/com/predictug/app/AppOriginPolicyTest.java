package com.predictug.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppOriginPolicyTest {
    @Test
    public void internalAssetOriginIsStrictHttpsHostMatch() {
        assertTrue(AppOriginPolicy.isInternal("https", "appassets.androidplatform.net"));
        assertTrue(AppOriginPolicy.isInternal("HTTPS", "APPASSETS.ANDROIDPLATFORM.NET"));
        assertFalse(AppOriginPolicy.isInternal("http", "appassets.androidplatform.net"));
        assertFalse(AppOriginPolicy.isInternal("https", "predict-ug-app.onrender.com"));
        assertFalse(AppOriginPolicy.isInternal(null, null));
    }

    @Test
    public void merchantDeepLinkSchemeIsExplicit() {
        assertTrue(AppOriginPolicy.isMerchantScheme("predictug"));
        assertTrue(AppOriginPolicy.isMerchantScheme("PREDICTUG"));
        assertFalse(AppOriginPolicy.isMerchantScheme("https"));
        assertFalse(AppOriginPolicy.isMerchantScheme(null));
    }
}
