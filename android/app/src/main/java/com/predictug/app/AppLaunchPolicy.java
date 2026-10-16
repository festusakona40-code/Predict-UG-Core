package com.predictug.app;

/** Bundle-first consumer delivery policy; never a permission or trust boundary. */
final class AppLaunchPolicy {
    private AppLaunchPolicy() {}

    static boolean useBundledPracticeBeta(String flavor, int versionCode) {
        // Keep future consumer APKs on their audited packaged interface while
        // remote web hosting is blocked. This is independent of release number.
        // Merchant builds must never load this consumer-only experience.
        return "consumer".equals(flavor) && versionCode >= 86;
    }
}
