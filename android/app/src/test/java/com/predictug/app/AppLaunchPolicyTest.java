package com.predictug.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AppLaunchPolicyTest {
    @Test public void bundleFirstSurvivesNewConsumerVersions() {
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 86));
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 90));
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 91));
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 92));
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 120));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 85));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", -1));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("merchant", 91));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("merchant", 120));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta(null, 92));
    }
}
