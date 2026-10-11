package com.logvex.nofallbypass;

import com.logvex.nofallbypass.utils.LandingHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LandingHelperTest {
    @Test
    void noDamageUnderFourBlocks() {
        assertEquals(0f, LandingHelper.vanillaFallDamage(0), 1e-6);
        assertEquals(0f, LandingHelper.vanillaFallDamage(3.0), 1e-6);
        assertEquals(0f, LandingHelper.vanillaFallDamage(3.9), 1e-6);
    }

    @Test
    void damageScalesPastFourBlocks() {
        assertEquals(1f, LandingHelper.vanillaFallDamage(4.0), 1e-6);
        assertEquals(2f, LandingHelper.vanillaFallDamage(5.9), 1e-6);
        assertEquals(7f, LandingHelper.vanillaFallDamage(10.0), 1e-6);
    }

    @Test
    void wouldTakeDamage() {
        assertFalse(LandingHelper.wouldTakeDamage(3.0));
        assertTrue(LandingHelper.wouldTakeDamage(4.0));
    }

    @Test
    void ticksUntilHeightIsInfiniteWhenNotDescending() {
        assertTrue(Double.isInfinite(LandingHelper.ticksUntilHeight(0.5, 5)));
        assertTrue(Double.isInfinite(LandingHelper.ticksUntilHeight(0, 5)));
    }

    @Test
    void ticksUntilHeightDecreasesAsClosingSpeedGrows() {
        double slow = LandingHelper.ticksUntilHeight(-0.2, 5);
        double fast = LandingHelper.ticksUntilHeight(-2.0, 5);
        assertTrue(slow > fast);
        assertTrue(fast > 0);
    }

    @Test
    void secondsUntilLandingConvertsTicks() {
        double ticks = LandingHelper.ticksUntilHeight(-1.0, 10);
        assertEquals(ticks / 20.0, LandingHelper.secondsUntilLanding(-1.0, 10), 1e-9);
    }

    @Test
    void isLandingImminentRespectsThreshold() {
        assertTrue(LandingHelper.isLandingImminent(3, 10, 5));
        assertFalse(LandingHelper.isLandingImminent(6, 10, 5));
        assertFalse(LandingHelper.isLandingImminent(3, 0, 5));
        assertFalse(LandingHelper.isLandingImminent(3, 10, 0));
    }

    @Test
    void clampBounds() {
        assertEquals(5, LandingHelper.clamp(3, 5, 10), 1e-9);
        assertEquals(10, LandingHelper.clamp(20, 5, 10), 1e-9);
        assertEquals(7, LandingHelper.clamp(7, 5, 10), 1e-9);
    }
}
