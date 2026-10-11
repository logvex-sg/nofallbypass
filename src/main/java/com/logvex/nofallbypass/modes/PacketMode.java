package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

/**
 * A packet based strategy. Two independent packet level techniques are used together:
 *
 * <ol>
 *   <li>the on-ground flag of the movement packet is forced to {@code true} while descending, and</li>
 *   <li>on the single tick where the player becomes airborne, one
 *       {@code PlayerMoveC2SPacket.OnGroundOnly(true)} is emitted so the server sees an
 *       "on ground" transition.</li>
 * </ol>
 *
 * <p>Limitations: sending an extra movement packet does not reset the server's own fall
 * distance. On a server that tracks fall distance from its own simulation, or that validates the
 * player's position and velocity, this will not prevent damage and may be rejected. It is the
 * classic client-side NoFall technique and is provided for the servers that still honour it. No
 * bypass is claimed. The extra packet is edge triggered (once per airborne transition) so the
 * module never sends a continuous stream of fabricated movement packets.
 */
public final class PacketMode extends NoFallMode {
    private boolean sentThisFall;

    public PacketMode() {
        super(Strategy.Packet, "Packet", "Ground flag spoof plus a single edge-triggered on-ground packet.");
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

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (!tracker.isFalling()) {
            sentThisFall = false;
            return;
        }

        if (sentThisFall) return;
        if (tracker.fallDistance() < settings.minFallDistance()) return;
        if (tracker.isGliding() || tracker.isInVehicle() || tracker.isInFluid() || tracker.isFlying()) return;

        sentThisFall = true;
        ctx.sendGroundPacket(true);
        ctx.debug("Packet: sent one on-ground packet at fall distance %.2f".formatted(tracker.fallDistance()));
    }

    @Override
    public void onDeactivate(Context ctx) {
        sentThisFall = false;
    }
}
