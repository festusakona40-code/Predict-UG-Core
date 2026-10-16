package com.predictug.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppLaunchPolicyTest {
    @Test public void bundleFirstIsExplicitlyScopedToConsumerBeta88() {
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 88));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 85));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 87));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("merchant", 88));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta(null, 88));
    }
}
