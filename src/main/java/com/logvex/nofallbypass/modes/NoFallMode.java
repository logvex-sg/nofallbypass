package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

/**
 * A single, independently implemented fall-damage mitigation strategy.
 *
 * <p>Modes receive all Minecraft state through {@link Context} and {@link FallTracker},
 * which keeps the decision logic testable and keeps every Minecraft API call in one place
 * (the module). A mode must never send packets on its own.
 */
public abstract class NoFallMode {
    /** The strategies a mode can implement. Used by {@code ServerCompatibility}. */
    public enum Strategy {
        Vanilla,
        GroundSpoof,
        Packet,
        MlgAssist,
        Elytra,
        Vehicle,
        SafeLanding,
        /** Meta strategy: chooses a concrete strategy per tick. */
        Adaptive,
        /** Meta strategy: user chosen primary plus fallback. */
        Custom;

        /** Whether this strategy is a meta strategy rather than a concrete implementation. */
        public boolean isMeta() {
            return this == Adaptive || this == Custom;
        }
    }

    /**
     * Bridge from a mode back to the module. Implemented by the module; stubbed in tests.
     */
    public interface Context {
        /** Emit a debug line (only when the debug setting is on). */
        void debug(String message);

        /** Emit a user-facing notification (only when notifications are enabled). */
        void notify(String message);

        /** Override the on-ground flag of the movement packet currently being sent. */
        void setPacketGround(boolean ground);

        /**
         * Send a single {@code PlayerMoveC2SPacket.OnGroundOnly} with the given flag, without
         * re-entering the packet event. Must be edge triggered by the caller; the module does not
         * rate limit it.
         */
        void sendGroundPacket(boolean ground);

        /** Ask the module to perform a water-bucket / powder-snow MLG clutch. */
        void requestMlg();

        /** Whether an MLG clutch is currently possible (item present and a landing spot in reach). */
        boolean canMlg();

        /** Distance from the player's feet to the first solid surface below, in blocks. */
        double heightAboveGround();
    }

    private final Strategy strategy;
    private final String name;
    private final String description;

    protected NoFallMode(Strategy strategy, String name, String description) {
        this.strategy = strategy;
        this.name = name;
        this.description = description;
    }

    public final Strategy strategy() { return strategy; }
    public final String name() { return name; }
    public final String description() { return description; }

    /** Whether this mode can modify outgoing movement packets. */
    public boolean modifiesPackets() { return false; }

    /** Called once per client tick while this mode is the resolved strategy. */
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {}

    /**
     * Called for every outgoing {@code PlayerMoveC2SPacket} while this mode is resolved.
     * Implementations may call {@link Context#setPacketGround(boolean)} at most once per packet.
     */
    public void onPacket(Context ctx, FallTracker tracker, NoFallSettings settings) {}

    /** Called when the module is disabled or the strategy is switched away from this mode. */
    public void onDeactivate(Context ctx) {}

    @Override
    public String toString() {
        return name;
    }
}
