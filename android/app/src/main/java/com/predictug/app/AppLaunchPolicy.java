package com.predictug.app;

/** Explicit delivery exception for the versioned practice beta, not a trust policy. */
final class AppLaunchPolicy {
    private AppLaunchPolicy() {}

    static boolean useBundledPracticeBeta(String flavor, int versionCode) {
        // Render currently cannot publish the audited shell. Beta 88 carries that
        // exact shell and local SDK; API data and money gates stay authoritative.
        // Future releases must deliberately choose their delivery policy again.
        return "consumer".equals(flavor) && versionCode == 88;
    }
}
