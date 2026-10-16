package com.predictug.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppLaunchPolicyTest {
    @Test public void bundleFirstIsExplicitlyScopedToConsumerBeta90() {
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 90));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 85));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 87));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 88));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 89));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("merchant", 90));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta(null, 90));
    }
}
