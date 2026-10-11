package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModeBehaviourTest {
    private static final NoFallSettings SETTINGS = NoFallSettings.defaults();

    // region GroundSpoof

    @Test
    void groundSpoofSpoofsOnlyWhileFallingPastThreshold() {
        GroundSpoofMode mode = new GroundSpoofMode();
        TestContext ctx = TestContext.of();

        mode.onPacket(ctx, TestContext.trackerFalling(8), SETTINGS);
        assertTrue(ctx.packetGround);

        ctx.packetGround = false;
        mode.onPacket(ctx, TestContext.trackerFalling(1), SETTINGS);
        assertFalse(ctx.packetGround, "below min distance must not spoof");

        ctx.packetGround = false;
        mode.onPacket(ctx, TestContext.trackerGliding(), SETTINGS);
        assertFalse(ctx.packetGround, "gliding must not spoof");
    }

    @Test
    void groundSpoofSendsNoPackets() {
        GroundSpoofMode mode = new GroundSpoofMode();
        TestContext ctx = TestContext.of();
        mode.onPacket(ctx, TestContext.trackerFalling(8), SETTINGS);
        assertTrue(ctx.groundPackets.isEmpty());
    }

    // endregion

    // region Packet

    @Test
    void packetModeSendsExactlyOneGroundPacketPerFall() {
        PacketMode mode = new PacketMode();
        TestContext ctx = TestContext.of();
        FallTracker tracker = TestContext.trackerFalling(8);

        mode.onTick(ctx, tracker, SETTINGS);
        mode.onTick(ctx, tracker, SETTINGS);
        mode.onTick(ctx, tracker, SETTINGS);

        assertEquals(1, ctx.groundPackets.size(), "must be edge triggered");
        assertEquals(Boolean.TRUE, ctx.groundPackets.get(0));
    }

    @Test
    void packetModeAlsoSpoofsPacketsWhileFalling() {
        PacketMode mode = new PacketMode();
        TestContext ctx = TestContext.of();
        mode.onPacket(ctx, TestContext.trackerFalling(8), SETTINGS);
        assertTrue(ctx.packetGround);
    }

    // endregion

    // region MLG

    @Test
    void mlgRequestsWhenImminentAndAvailable() {
        MlgAssistMode mode = new MlgAssistMode();
        TestContext ctx = TestContext.of().canMlg(true).height(2.0);
        mode.onTick(ctx, TestContext.trackerFalling(8), SETTINGS);
        assertTrue(ctx.mlgRequested);
    }

    @Test
    void mlgDoesNotRequestWhenNoBucket() {
        MlgAssistMode mode = new MlgAssistMode();
        TestContext ctx = TestContext.of().canMlg(false).height(2.0);
        mode.onTick(ctx, TestContext.trackerFalling(8), SETTINGS);
        assertFalse(ctx.mlgRequested);
    }

    @Test
    void mlgDoesNotRequestWhenStillHigh() {
        MlgAssistMode mode = new MlgAssistMode();
        TestContext ctx = TestContext.of().canMlg(true).height(50.0);
        mode.onTick(ctx, TestContext.trackerFalling(8), SETTINGS);
        assertFalse(ctx.mlgRequested);
    }

    // endregion

    // region SafeLanding

    @Test
    void safeLandingWarnsButNeverSpoofs() {
        SafeLandingMode mode = new SafeLandingMode();
        TestContext ctx = TestContext.of().height(1.0);

        FallTracker tracker = TestContext.trackerFalling(8);
        mode.onTick(ctx, tracker, SETTINGS);
        mode.onPacket(ctx, tracker, SETTINGS);

        assertFalse(ctx.notifications.isEmpty());
        assertFalse(ctx.packetGround, "safe landing must not modify packets");
        assertTrue(ctx.groundPackets.isEmpty());
    }

    @Test
    void safeLandingQuietWhenNoDamageExpected() {
        SafeLandingMode mode = new SafeLandingMode();
        TestContext ctx = TestContext.of().height(1.0);
        mode.onTick(ctx, TestContext.trackerFalling(2), SETTINGS);
        assertTrue(ctx.notifications.isEmpty());
    }

    // endregion

    // region Elytra

    @Test
    void elytraDoesNotActWhileGliding() {
        ElytraMode mode = new ElytraMode();
        TestContext ctx = TestContext.of();
        mode.onTick(ctx, TestContext.trackerGliding(), SETTINGS);
        assertFalse(ctx.mlgRequested);
    }

    // endregion

    // region Vehicle

    @Test
    void vehicleWarnsOnLowHeight() {
        VehicleMode mode = new VehicleMode();
        TestContext ctx = TestContext.of().height(2.0);
        mode.onTick(ctx, TestContext.trackerVehicle(), SETTINGS);
        assertFalse(ctx.notifications.isEmpty());
    }

    @Test
    void vehicleIsSilentWhenDisabled() {
        VehicleMode mode = new VehicleMode();
        NoFallSettings off = new NoFallSettings(3, 5, true, true, false, false, true, 3, false, false, true);
        TestContext ctx = TestContext.of().height(2.0);
        mode.onTick(ctx, TestContext.trackerVehicle(), off);
        assertTrue(ctx.notifications.isEmpty());
    }

    // endregion

    // region Vanilla

    @Test
    void vanillaDoesNothing() {
        VanillaMode mode = new VanillaMode();
        TestContext ctx = TestContext.of();
        FallTracker tracker = TestContext.trackerFalling(8);
        mode.onTick(ctx, tracker, SETTINGS);
        mode.onPacket(ctx, tracker, SETTINGS);
        assertFalse(ctx.packetGround);
        assertTrue(ctx.groundPackets.isEmpty());
        assertTrue(ctx.notifications.isEmpty());
    }

    // endregion
}
