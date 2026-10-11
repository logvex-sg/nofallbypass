package com.logvex.nofallbypass.utils;

/**
 * Pure maths helpers for reasoning about an imminent landing.
 *
 * <p>All values are in Minecraft blocks and ticks. A tick is 1/20 s and gravity is
 * 0.08 blocks/tick² in vanilla, so the helpers use a simple constant-acceleration
 * model. The model is intentionally approximate: it exists to decide *whether* to act,
 * not to predict a landing to the exact tick.
 */
public final class LandingHelper {
    public static final double GRAVITY = 0.08;
    public static final double TICKS_PER_SECOND = 20.0;

    private LandingHelper() {}

    /** Approximate fall damage in half-hearts for a fall distance, vanilla rules. */
    public static float vanillaFallDamage(double fallDistance) {
        if (fallDistance <= 3.0) return 0f;
        return (float) Math.floor(fallDistance - 3.0);
    }

    /** {@code true} when a fall from this distance would hurt. */
    public static boolean wouldTakeDamage(double fallDistance) {
        return vanillaFallDamage(fallDistance) > 0f;
    }

    /**
     * Ticks until the player, falling with the given downward velocity (negative = down),
     * would reach {@code heightAboveTarget} blocks above the ground, ignoring drag.
     *
     * <p>Returns {@link Double#POSITIVE_INFINITY} when the player is not descending.
     */
    public static double ticksUntilHeight(double downwardVelocity, double heightAboveTarget) {
        if (downwardVelocity >= 0 || heightAboveTarget <= 0) return Double.POSITIVE_INFINITY;
        double v = -downwardVelocity;
        // Solve 0.5*g*t^2 + v*t - h = 0 for the positive root.
        double a = 0.5 * GRAVITY;
        double disc = v * v + 4 * a * heightAboveTarget;
        double t = (-v + Math.sqrt(disc)) / (2 * a);
        return Math.max(0, t);
    }

    /** Estimated seconds until landing given the current height above the ground. */
    public static double secondsUntilLanding(double downwardVelocity, double heightAboveTarget) {
        double ticks = ticksUntilHeight(downwardVelocity, heightAboveTarget);
        if (Double.isInfinite(ticks)) return Double.POSITIVE_INFINITY;
        return ticks / TICKS_PER_SECOND;
    }

    /**
     * {@code true} when the player is close enough to the ground that a landing is imminent
     * and worth reacting to.
     *
     * @param heightAboveGround distance from the player's feet to the surface
     * @param fallDistance      distance already fallen
     * @param threshold         activation threshold in blocks (must be &gt; 0)
     */
    public static boolean isLandingImminent(double heightAboveGround, double fallDistance, double threshold) {
        if (threshold <= 0) return false;
        return fallDistance > 0 && heightAboveGround >= 0 && heightAboveGround <= threshold;
    }

    /** Clamp helper used by the module when reading numeric settings. */
    public static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
