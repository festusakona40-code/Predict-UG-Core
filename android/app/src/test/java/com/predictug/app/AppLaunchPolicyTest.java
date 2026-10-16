package com.predictug.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppLaunchPolicyTest {
    @Test public void bundleFirstIsExplicitlyScopedToConsumerBeta85() {
        assertTrue(AppLaunchPolicy.useBundledPracticeBeta("consumer", 85));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 84));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("consumer", 86));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta("merchant", 85));
        assertFalse(AppLaunchPolicy.useBundledPracticeBeta(null, 85));
    }
}
