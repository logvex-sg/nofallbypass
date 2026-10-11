package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.LandingHelper;

/**
 * Validated, Minecraft-independent view of the module's numeric settings and toggles.
 *
 * <p>Every mode reads its configuration from here instead of from Meteor {@code Setting}
 * objects, so modes stay unit testable and all numeric validation happens in one place.
 */
public record NoFallSettings(
    double minFallDistance,
    double landingThreshold,
    boolean waterBucketAssist,
    boolean elytraHandling,
    boolean vehicleHandling,
    boolean debug,
    boolean notifications,
    double grimFallThreshold,
    boolean grimModifiedPacket,
    boolean grimFastFall,
    boolean grimVelocityCheck
) {
    public static final double MIN_FALL_DISTANCE_LOWER = 0.5;
    public static final double MIN_FALL_DISTANCE_UPPER = 100.0;
    public static final double LANDING_THRESHOLD_LOWER = 0.5;
    public static final double LANDING_THRESHOLD_UPPER = 20.0;
    public static final double GRIM_THRESHOLD_LOWER = 3.0;
    public static final double GRIM_THRESHOLD_UPPER = 15.0;

    public NoFallSettings {
        minFallDistance = LandingHelper.clamp(minFallDistance, MIN_FALL_DISTANCE_LOWER, MIN_FALL_DISTANCE_UPPER);
        landingThreshold = LandingHelper.clamp(landingThreshold, LANDING_THRESHOLD_LOWER, LANDING_THRESHOLD_UPPER);
        grimFallThreshold = LandingHelper.clamp(grimFallThreshold, GRIM_THRESHOLD_LOWER, GRIM_THRESHOLD_UPPER);
    }

    public static NoFallSettings defaults() {
        return new NoFallSettings(3.0, 5.0, true, true, true, false, true, 3.0, false, false, true);
    }
}
