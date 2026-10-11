package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;
import com.logvex.nofallbypass.utils.LandingHelper;

/**
 * MLG assist. Watches for an imminent landing and asks the module to place a water bucket (or
 * powder snow) beneath the player when one is available.
 *
 * <p>Limitations: this is a legitimate clutch, not a bypass. It only works when the player has a
 * bucket in the hotbar, when a surface is within reach, and when the server accepts the block
 * placement. If any of those fail the mode reports that it cannot help and the module falls back
 * to a safe strategy. The actual placement is performed by the module using Meteor's block
 * placement utilities.
 */
public final class MlgAssistMode extends NoFallMode {
    private boolean attempted;

    public MlgAssistMode() {
        super(Strategy.MlgAssist, "MLG Assist", "Places a water bucket to break an imminent fall.");
    }

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (!tracker.isFalling()) {
            attempted = false;
            return;
        }

        if (attempted) return;
        if (!settings.waterBucketAssist()) return;
        if (tracker.fallDistance() < settings.minFallDistance()) return;
        if (tracker.isGliding() || tracker.isInVehicle() || tracker.isInFluid() || tracker.isFlying()) return;

        double height = ctx.heightAboveGround();
        if (!LandingHelper.isLandingImminent(height, tracker.fallDistance(), settings.landingThreshold())) return;

        if (!ctx.canMlg()) {
            ctx.debug("MLG: landing imminent but no bucket / no reachable surface");
            return;
        }

        attempted = true;
        ctx.notify("MLG assist: attempting water clutch at %.1f blocks".formatted(tracker.fallDistance()));
        ctx.requestMlg();
    }

    @Override
    public void onDeactivate(Context ctx) {
        attempted = false;
    }
}
