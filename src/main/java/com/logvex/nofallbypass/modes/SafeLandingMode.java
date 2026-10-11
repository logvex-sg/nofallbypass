package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;
import com.logvex.nofallbypass.utils.LandingHelper;

/**
 * The conservative fallback. It makes no changes to movement packets and does not fabricate
 * anything. It only observes the fall and reports when a damaging landing is about to happen, so
 * the player is never surprised by a lethal drop.
 *
 * <p>Limitations: by design it cannot reduce fall damage. It exists so that when every other
 * strategy is unsupported the module still does something honest and safe.
 */
public final class SafeLandingMode extends NoFallMode {
    private boolean warned;

    public SafeLandingMode() {
        super(Strategy.SafeLanding, "Safe Landing", "Monitors the fall and warns before a damaging landing. Never spoofs packets.");
    }

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (!tracker.isFalling()) {
            warned = false;
            return;
        }

        if (warned) return;

        float damage = LandingHelper.vanillaFallDamage(tracker.fallDistance());
        if (damage <= 0f) return;

        double height = ctx.heightAboveGround();
        if (height > settings.landingThreshold()) return;

        warned = true;
        ctx.notify("Safe landing: about %.0f damage from %.1f blocks".formatted(damage, tracker.fallDistance()));
    }

    @Override
    public void onDeactivate(Context ctx) {
        warned = false;
    }
}
