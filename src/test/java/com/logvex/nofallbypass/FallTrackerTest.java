package com.logvex.nofallbypass;

import com.logvex.nofallbypass.utils.FallTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallTrackerTest {
    private static FallTracker.Sample air(double y) {
        return new FallTracker.Sample(y, -0.5, false, false, false, false, false, 1);
    }

    private static FallTracker.Sample ground(double y) {
        return new FallTracker.Sample(y, 0, true, false, false, false, false, 1);
    }

    @Test
    void firstSampleOnlyInitialises() {
        FallTracker t = new FallTracker();
        t.update(air(80));
        assertTrue(t.isInitialized());
        assertEquals(0, t.fallDistance());
        assertFalse(t.isFalling());
    }

    @Test
    void accumulatesFallDistanceWhileAirborne() {
        FallTracker t = new FallTracker();
        t.update(air(80));
        t.update(air(79));
        t.update(air(77));
        t.update(air(74));
        assertEquals(6.0, t.fallDistance(), 1e-9);
        assertTrue(t.isFalling());
        assertEquals(3, t.ticksFalling());
    }

    @Test
    void jumpingUpDoesNotCountAsFall() {
        FallTracker t = new FallTracker();
        t.update(ground(64));
        t.update(air(65)); // going up
        t.update(air(65.5));
        assertEquals(0.0, t.fallDistance(), 1e-9);
        // Falling back down from the apex counts.
        t.update(air(65));
        t.update(air(64));
        assertEquals(1.5, t.fallDistance(), 1e-9);
    }

    @Test
    void landingResetsAndRecordsLastFall() {
        FallTracker t = new FallTracker();
        t.update(air(80));
        t.update(air(72));
        t.update(air(70));
        assertEquals(10.0, t.fallDistance(), 1e-9);

        t.update(ground(70));
        assertTrue(t.justLanded());
        assertEquals(10.0, t.lastLandedFallDistance(), 1e-9);
        assertEquals(0.0, t.fallDistance(), 1e-9);
        assertFalse(t.isFalling());
    }

    @Test
    void glidingSuppressesFall() {
        FallTracker t = new FallTracker();
        t.update(ground(80));
        t.update(new FallTracker.Sample(78, -0.4, false, true, false, false, false, 1));
        assertEquals(0.0, t.fallDistance(), 1e-9);
        assertFalse(t.isFalling());
    }

    @Test
    void waterSuppressesFall() {
        FallTracker t = new FallTracker();
        t.update(ground(80));
        t.update(new FallTracker.Sample(70, -1.0, false, false, false, true, false, 1));
        assertEquals(0.0, t.fallDistance(), 1e-9);
    }

    @Test
    void vehicleSuppressesFall() {
        FallTracker t = new FallTracker();
        t.update(ground(80));
        t.update(new FallTracker.Sample(70, -1.0, false, false, true, false, false, 1));
        assertEquals(0.0, t.fallDistance(), 1e-9);
        assertTrue(t.enteredVehicle());
    }

    @Test
    void creativeFlightSuppressesFall() {
        FallTracker t = new FallTracker();
        t.update(ground(80));
        t.update(new FallTracker.Sample(70, -1.0, false, false, false, false, true, 1));
        assertEquals(0.0, t.fallDistance(), 1e-9);
    }

    @Test
    void teleportResetsAccumulation() {
        FallTracker t = new FallTracker();
        t.update(air(100));
        t.update(air(99));
        assertEquals(1.0, t.fallDistance(), 1e-9);

        t.update(air(0)); // teleport far down
        assertTrue(t.teleported());
        assertEquals(0.0, t.fallDistance(), 1e-9);
    }

    @Test
    void worldChangeResetsAccumulation() {
        FallTracker t = new FallTracker();
        t.update(air(100));
        t.update(air(99));
        t.update(new FallTracker.Sample(99, -0.5, false, false, false, false, false, 2));
        assertTrue(t.worldChanged());
        assertEquals(0.0, t.fallDistance(), 1e-9);
    }

    @Test
    void glideTransitionsAreDetected() {
        FallTracker t = new FallTracker();
        t.update(ground(80));
        t.update(new FallTracker.Sample(80, 0, false, true, false, false, false, 1));
        assertTrue(t.startedGliding());
        t.update(new FallTracker.Sample(80, 0, false, false, false, false, false, 1));
        assertTrue(t.stoppedGliding());
    }

    @Test
    void justStartedFallingOnlyOnFirstTick() {
        FallTracker t = new FallTracker();
        t.update(ground(80));
        t.update(air(79));
        assertTrue(t.justStartedFalling());
        t.update(air(78));
        assertFalse(t.justStartedFalling());
    }

    @Test
    void resetClearsEverything() {
        FallTracker t = new FallTracker();
        t.update(air(80));
        t.update(air(70));
        t.reset();
        assertFalse(t.isInitialized());
        assertEquals(0, t.fallDistance());
        assertFalse(t.isFalling());
    }
}
