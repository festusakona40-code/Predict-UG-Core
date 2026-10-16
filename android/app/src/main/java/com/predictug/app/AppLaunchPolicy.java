package com.predictug.app;

/** Bundle-first consumer delivery policy, never an authentication/trust boundary. */
final class AppLaunchPolicy {
    private AppLaunchPolicy() {}

    static boolean useBundledPracticeBeta(String flavor, int versionCode) {
        // Future consumer versions keep the audited packaged interface even
        // while remote web builds are unavailable. Merchant builds are excluded.
        return "consumer".equals(flavor) && versionCode >= 86;
    }
}
