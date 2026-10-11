package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

/**
 * GrimJump: a landing-time correction technique for servers that use a prediction based movement
 * check (for example GrimAC).
 *
 * <p>The idea is not to hide the fall while it happens. Instead, the instant the server is about to
 * register the landing, the client resends the last "on ground" position and re-issues the jump
 * input. On servers whose check accepts the resulting jump velocity and defers to the client's
 * landing transition, this cancels the accumulated fall damage. The implementation follows the
 * publicly published client logic (Krs {@code NoFall} "Grim 1.9+" mode and the Epsilon client
 * {@code GrimJump} mode); both do the same three things:
 *
 * <ol>
 *   <li>while airborne and descending, release the jump key;</li>
 *   <li>when {@code onGround} becomes true and the accumulated fall exceeds the threshold, send a
 *       silent {@code PlayerMoveC2SPacket} (a {@code StatusOnly(true)} and, optionally, a
 *       {@code Pos(y + 0.01, true)}) so the server sees a fresh landing, then request a jump;</li>
 *   <li>wait for the server's {@code EntityVelocityUpdateS2CPacket} (the jump impulse) before
 *       restoring normal input.</li>
 * </ol>
 *
 * <p>Limitations, stated plainly: this is a heuristic that only works against checks that do not
 * fully simulate the landing themselves. A server that computes fall damage from its own replica of
 * the player, or that has patched the landing-time jump handling, will not be fooled; the
 * correction is at best ignored and can be flagged as an invalid movement. No bypass is claimed.
 * The mode is also deliberately narrow: it does not run while gliding, riding, swimming or flying,
 * it waits for a real landing rather than spamming packets in mid-air, and it never sends more than
 * two packets per landing.
 */
public final class GrimJumpMode extends NoFallMode {
    /** Ticks airborne with a downward motion before the jump key is released. Matches the reference. */
    private static final int AIRBORNE_TICKS_BEFORE_RELEASE = 9;
    /** Minimum height above the ground before the optional fast fall is applied. */
    private static final double FAST_FALL_MIN_HEIGHT = 5.0;

    private int airborneTicks;
    private boolean active;
    private boolean awaitingVelocity;
    private boolean jumpRequested;

    public GrimJumpMode() {
        super(Strategy.GrimJump, "Grim Jump", "Resends a landing packet and re-presses jump when the server registers a landing.");
    }

    @Override
    public boolean modifiesPackets() {
        // The correction is sent silently, bypassing the send event, so the module must not
        // additionally rewrite the ordinary movement packets.
        return false;
    }

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (tracker.isGliding() || tracker.isInVehicle() || tracker.isInFluid() || tracker.isFlying()) {
            resetInput(ctx);
            return;
        }

        if (tracker.isOnGround()) {
            airborneTicks = 0;
        } else {
            airborneTicks++;
        }

        boolean armed = tracker.fallDistance() >= settings.grimFallThreshold()
            || tracker.lastLandedFallDistance() >= settings.grimFallThreshold();

        if (tracker.verticalVelocity() > 0.1) {
            // Moving up again: either the jump was accepted, or the player jumped normally.
            if (jumpRequested) {
                ctx.releaseJump();
                jumpRequested = false;
            }
            if (!awaitingVelocity) resetCycle();
            return;
        }

        if (!active && armed && tracker.isFalling()) {
            active = true;
            ctx.debug("GrimJump: armed at %.2f blocks".formatted(tracker.fallDistance()));
        }

        if (!active) return;

        if (!tracker.isOnGround()) {
            // Release jump while falling so the jump packet cannot be confused with the landing.
            if (airborneTicks >= AIRBORNE_TICKS_BEFORE_RELEASE || jumpRequested) {
                ctx.releaseJump();
                jumpRequested = false;
            }
            applyFastFall(ctx, tracker, settings);
            return;
        }

        // Landing transition: send the correction once and ask for the jump.
        if (awaitingVelocity) return;

        if (settings.grimModifiedPacket()) {
            ctx.sendMovePacket(true, 0.01);
            ctx.debug("GrimJump: sent corrected position packet");
        }
        ctx.sendMovePacket(true, 0.0);

        ctx.releaseJump();
        ctx.requestJump();
        jumpRequested = true;
        awaitingVelocity = true;
        ctx.debug("GrimJump: landing correction at %.2f blocks".formatted(tracker.lastLandedFallDistance()));
    }

    private void applyFastFall(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (!settings.grimFastFall()) return;
        if (!tracker.shouldFastFall(AIRBORNE_TICKS_BEFORE_RELEASE, FAST_FALL_MIN_HEIGHT)) return;

        tracker.markFastFallApplied();
        ctx.releaseJump();
        // Predict vanilla gravity a few ticks ahead and snap the velocity down, as the reference
        // client does. This is a genuine movement change, not a hidden packet.
        double predicted = tracker.verticalVelocity();
        for (int i = 0; i < 10; i++) predicted = (predicted - 0.08) * 0.98;
        ctx.applyVerticalVelocity(predicted);
        ctx.debug("GrimJump: fast fall applied (vy %.3f)".formatted(predicted));
    }

    /** Notify the mode that the server acknowledged with a velocity update (the jump impulse). */
    public void onVelocity() {
        awaitingVelocity = false;
        jumpRequested = false;
    }

    private void resetCycle() {
        active = false;
        awaitingVelocity = false;
    }

    private void resetInput(Context ctx) {
        ctx.releaseJump();
        jumpRequested = false;
        active = false;
        awaitingVelocity = false;
        airborneTicks = 0;
    }

    @Override
    public void onDeactivate(Context ctx) {
        resetInput(ctx);
    }
}
