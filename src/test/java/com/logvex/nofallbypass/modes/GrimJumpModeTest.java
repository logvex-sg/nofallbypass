package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural tests for {@link GrimJumpMode}. These exercise the pure state machine; the packet
 * and key side effects are recorded by {@link TestContext}, and the real Minecraft glue lives in
 * the module.
 */
class GrimJumpModeTest {
    private static final NoFallSettings SETTINGS = NoFallSettings.defaults();

    /** Tracker with a long fall that has just landed; lastLandedFallDistance is the fall. */
    private static FallTracker landed(double fallDistance, int steps) {
        FallTracker t = TestContext.trackerLongFall(fallDistance, steps);
        t.update(new FallTracker.Sample(-fallDistance, -0.5, true, false, false, false, false, 1));
        return t;
    }

    @Test
    void armsOnlyPastThreshold() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        FallTracker shortFall = TestContext.trackerLongFall(2.0, 4);
        for (int i = 0; i < 5; i++) mode.onTick(ctx, shortFall, SETTINGS);
        assertTrue(ctx.movePackets.isEmpty(), "must not arm below the threshold");
    }

    @Test
    void doesNotSendCorrectionWhileAirborne() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        FallTracker falling = TestContext.trackerLongFall(12.0, 12);
        for (int i = 0; i < 15; i++) mode.onTick(ctx, falling, SETTINGS);
        assertTrue(ctx.movePackets.isEmpty(), "correction must wait for a real landing");
    }

    @Test
    void sendsCorrectionOnLandingAndRequestsJump() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        FallTracker falling = TestContext.trackerLongFall(12.0, 12);

        mode.onTick(ctx, falling, SETTINGS);
        FallTracker landed = landed(12.0, 12);
        mode.onTick(ctx, landed, SETTINGS);

        assertEquals(1, ctx.movePackets.size(), "exactly one ground correction packet");
        assertEquals("true@0.0", ctx.movePackets.get(0));
        assertEquals(1, ctx.jumpRequests, "jump must be requested once on landing");
    }

    @Test
    void modifiedPacketAddsCorrectedPosition() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        NoFallSettings modified = new NoFallSettings(3, 5, true, true, true, false, true, 3, true, false, true);

        mode.onTick(ctx, TestContext.trackerLongFall(12.0, 12), modified);
        mode.onTick(ctx, landed(12.0, 12), modified);

        assertEquals(2, ctx.movePackets.size());
        assertEquals("true@0.01", ctx.movePackets.get(0));
        assertEquals("true@0.0", ctx.movePackets.get(1));
    }

    @Test
    void correctionIsNotRepeatedWhileAwaitingVelocity() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();

        mode.onTick(ctx, TestContext.trackerLongFall(12.0, 12), SETTINGS);
        FallTracker landed = landed(12.0, 12);
        mode.onTick(ctx, landed, SETTINGS);
        mode.onTick(ctx, landed, SETTINGS);
        mode.onTick(ctx, landed, SETTINGS);

        assertEquals(1, ctx.movePackets.size(), "must not spam corrections");
        assertEquals(1, ctx.jumpRequests);
    }

    @Test
    void velocityAckAllowsANewCycle() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();

        mode.onTick(ctx, TestContext.trackerLongFall(12.0, 12), SETTINGS);
        mode.onTick(ctx, landed(12.0, 12), SETTINGS);
        assertEquals(1, ctx.movePackets.size());

        mode.onVelocity();
        mode.onTick(ctx, TestContext.trackerLongFall(12.0, 12), SETTINGS);
        mode.onTick(ctx, landed(12.0, 12), SETTINGS);
        assertEquals(2, ctx.movePackets.size(), "a second fall must be corrected again");
    }

    @Test
    void releasesJumpWhileFalling() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        FallTracker falling = TestContext.trackerLongFall(12.0, 12);

        for (int i = 0; i < 12; i++) mode.onTick(ctx, falling, SETTINGS);
        assertTrue(ctx.jumpReleases > 0, "jump must be released while descending");
    }

    @Test
    void doesNotRunWhileGliding() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        FallTracker gliding = TestContext.trackerGliding();
        for (int i = 0; i < 5; i++) mode.onTick(ctx, gliding, SETTINGS);
        assertTrue(ctx.movePackets.isEmpty());
        assertTrue(ctx.jumpRequests == 0);
    }

    @Test
    void fastFallAppliesVelocityOnce() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        NoFallSettings fastFall = new NoFallSettings(3, 5, true, true, true, false, true, 3, false, true, true);
        FallTracker falling = TestContext.trackerLongFall(60.0, 30);
        falling.setHeightToGround(40.0);

        for (int i = 0; i < 30; i++) mode.onTick(ctx, falling, fastFall);
        assertFalse(Double.isNaN(ctx.lastAppliedVelocity), "fast fall velocity must be applied");
        assertTrue(ctx.lastAppliedVelocity < 0, "fast fall must push downward");
        assertTrue(falling.fastFallApplied());
    }

    @Test
    void fastFallOffByDefault() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        FallTracker falling = TestContext.trackerLongFall(60.0, 30);
        falling.setHeightToGround(40.0);
        for (int i = 0; i < 30; i++) mode.onTick(ctx, falling, SETTINGS);
        assertTrue(Double.isNaN(ctx.lastAppliedVelocity), "fast fall is opt-in");
    }

    @Test
    void deactivateReleasesJump() {
        GrimJumpMode mode = new GrimJumpMode();
        TestContext ctx = TestContext.of();
        mode.onTick(ctx, TestContext.trackerLongFall(12.0, 12), SETTINGS);
        int releasesBefore = ctx.jumpReleases;
        mode.onDeactivate(ctx);
        assertTrue(ctx.jumpReleases > releasesBefore);
    }
}
