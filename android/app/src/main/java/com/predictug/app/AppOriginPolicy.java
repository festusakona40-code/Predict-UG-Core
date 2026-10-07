package com.predictug.app;

final class AppOriginPolicy {
    private static final String BUNDLED_HOST = "appassets.androidplatform.net";
    private static final String LIVE_HOST = "predict-ug-app.onrender.com";

    private AppOriginPolicy() {}

    static boolean isInternal(String scheme, String host) {
        if (!"https".equalsIgnoreCase(String.valueOf(scheme))) return false;
        String normalizedHost = String.valueOf(host);
        return BUNDLED_HOST.equalsIgnoreCase(normalizedHost)
                || LIVE_HOST.equalsIgnoreCase(normalizedHost);
    }

    static boolean isLive(String scheme, String host) {
        return "https".equalsIgnoreCase(String.valueOf(scheme))
                && LIVE_HOST.equalsIgnoreCase(String.valueOf(host));
    }

    static boolean isBundled(String scheme, String host) {
        return "https".equalsIgnoreCase(String.valueOf(scheme))
                && BUNDLED_HOST.equalsIgnoreCase(String.valueOf(host));
    }

    static boolean shouldBlockUntrustedSubframe(
            String scheme,
            String host,
            boolean isForMainFrame
    ) {
        return !isForMainFrame && !isInternal(scheme, host);
    }

    static boolean isMerchantScheme(String scheme) {
        return "predictug".equalsIgnoreCase(String.valueOf(scheme));
    }
}
