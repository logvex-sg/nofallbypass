package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

/**
 * Ground-state manipulation. While the player is descending past the minimum fall distance the
 * on-ground flag of the outgoing movement packet is forced to {@code true}.
 *
 * <p>Limitation: this only changes what the client tells the server. A server that validates
 * movement against its own simulation (as most modern servers and movement checks do) will
 * ignore the spoofed flag and may correct or flag it. It is not a reliable bypass on its own and
 * is deliberately marked unverified.
 */
public final class GroundSpoofMode extends NoFallMode {
    public GroundSpoofMode() {
        super(Strategy.GroundSpoof, "Ground Spoof", "Forces the on-ground flag on movement packets while descending.");
    }

    @Override
    public boolean modifiesPackets() {
        return true;
    }

    @Override
    public void onPacket(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (!tracker.isFalling()) return;
        if (tracker.fallDistance() < settings.minFallDistance()) return;
        if (tracker.isGliding() || tracker.isInVehicle() || tracker.isInFluid() || tracker.isFlying()) return;

        ctx.setPacketGround(true);
    }
}
