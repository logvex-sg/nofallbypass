package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

/**
 * Elytra handling. Fall damage does not accrue while gliding, but the transition out of a glide
 * (running out of rockets, hitting a wall, or cancelling flight) can drop the player from a lethal
 * height with no warning.
 *
 * <p>This mode does not fabricate packets. It tracks the height at which the glide stopped and, if
 * the player is about to land hard after a glide, asks the module for an MLG clutch when one is
 * possible. If no clutch is possible it reports the situation instead of pretending to help.
 *
 * <p>Limitations: it cannot create fall protection from nothing. A server that has already
 * accepted the player's altitude will still apply fall damage unless a legitimate mitigation (a
 * bucket, water, a hay bale) is available.
 */
public final class ElytraMode extends NoFallMode {
    private boolean gliding;
    private boolean postGlideAssist;

    public ElytraMode() {
        super(Strategy.Elytra, "Elytra", "Handles the transition out of a glide and assists the landing.");
    }

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (!settings.elytraHandling()) return;

        if (tracker.startedGliding()) {
            gliding = true;
            postGlideAssist = false;
            ctx.debug("Elytra: glide started");
        }

        if (tracker.stoppedGliding() && gliding) {
            gliding = false;
            postGlideAssist = true;
            ctx.notify("Elytra: glide ended at %.1f blocks fallen".formatted(tracker.fallDistance()));
        }

        if (gliding) return;

        if (postGlideAssist && tracker.isFalling()) {
            if (tracker.fallDistance() < settings.minFallDistance()) return;
            if (ctx.heightAboveGround() > settings.landingThreshold()) return;

            if (ctx.canMlg()) {
                postGlideAssist = false;
                ctx.requestMlg();
            } else {
                ctx.debug("Elytra: hard landing after glide, no clutch available");
            }
        }

        if (tracker.isOnGround()) postGlideAssist = false;
    }

    @Override
    public void onDeactivate(Context ctx) {
        gliding = false;
        postGlideAssist = false;
    }
}
