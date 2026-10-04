package com.predictug.app;

final class AppOriginPolicy {
    private static final String INTERNAL_HOST = "appassets.androidplatform.net";

    private AppOriginPolicy() {}

    static boolean isInternal(String scheme, String host) {
        return "https".equalsIgnoreCase(String.valueOf(scheme))
                && INTERNAL_HOST.equalsIgnoreCase(String.valueOf(host));
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
