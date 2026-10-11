package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

/**
 * Vehicle handling. While riding an entity the client player's own fall distance does not accrue;
 * fall damage, when it happens, is applied to the vehicle. This mode therefore does not spoof
 * packets. It watches the vehicle's descent and, when a hard landing looks likely and the player
 * is able to dismount safely, it reports the situation and offers the MLG clutch as a last resort.
 *
 * <p>Limitations: the client cannot place blocks or drink while seated, so the only reliable
 * vehicle defence is to dismount before impact. The mode will not dismount automatically because
 * that is a highly situational, server-visible action; it notifies instead.
 */
public final class VehicleMode extends NoFallMode {
    private boolean inVehicle;

    public VehicleMode() {
        super(Strategy.Vehicle, "Vehicle", "Watches vehicle fall state and warns before a hard landing.");
    }

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (!settings.vehicleHandling()) return;

        if (tracker.enteredVehicle() && !inVehicle) {
            inVehicle = true;
            ctx.debug("Vehicle: entered a vehicle");
        }

        if (tracker.leftVehicle() && inVehicle) {
            inVehicle = false;
            ctx.debug("Vehicle: left the vehicle");
        }

        if (!inVehicle) return;
        if (tracker.isOnGround()) return;

        // The tracker suppresses fall accumulation while riding, so use height instead.
        double height = ctx.heightAboveGround();
        if (height <= settings.landingThreshold()) {
            ctx.notify("Vehicle: hard landing likely, dismount or brace");
        }
    }

    @Override
    public void onDeactivate(Context ctx) {
        inVehicle = false;
    }
}
