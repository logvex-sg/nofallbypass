package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.compat.ServerCompatibility;
import com.logvex.nofallbypass.utils.FallTracker;

import java.util.Map;

/**
 * Chooses a concrete strategy every tick from the current movement state and the active server
 * profile. The decision order is:
 *
 * <ol>
 *   <li>riding a vehicle → {@link Strategy#Vehicle}</li>
 *   <li>gliding → {@link Strategy#Elytra}</li>
 *   <li>not falling → {@link Strategy#Vanilla}</li>
 *   <li>just left a glide → {@link Strategy#Elytra}</li>
 *   <li>falling from at least the minimum distance with bucket assist on → {@link Strategy#MlgAssist}</li>
 *   <li>a packet strategy is allowed → {@link Strategy#Packet}</li>
 *   <li>otherwise → {@link Strategy#SafeLanding}</li>
 * </ol>
 *
 * <p>Vehicle and glide states are checked before "not falling" because the tracker deliberately
 * suspends fall accumulation in those states, so they would never register as falling.
 *
 * <p>Every candidate is filtered through {@link ServerCompatibility#resolve} so an unsupported
 * strategy can never be selected. A switch is only reported when the selected strategy changes.
 */
public final class AdaptiveMode extends CompositeMode {
    private final ServerCompatibility compatibility;
    private Strategy lastSelected;

    public AdaptiveMode(Map<Strategy, NoFallMode> modes, ServerCompatibility compatibility) {
        super(Strategy.Adaptive, "Adaptive", "Selects a strategy from the movement state and server profile.", modes);
        this.compatibility = compatibility;
    }

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        Strategy chosen = select(tracker, settings);
        Strategy resolved = compatibility.resolve(chosen);

        if (resolved != lastSelected) {
            ctx.notify("Adaptive: %s -> %s".formatted(lastSelected == null ? "none" : lastSelected, resolved));
            lastSelected = resolved;
        }

        delegate(ctx, concrete(resolved), tracker, settings);
    }

    /** Exposed for testing: decide the raw (unfiltered) strategy for the current state. */
    public Strategy select(FallTracker tracker, NoFallSettings settings) {
        if (tracker.isInVehicle()) return Strategy.Vehicle;
        if (tracker.isGliding()) return Strategy.Elytra;
        if (!tracker.isFalling()) return Strategy.Vanilla;
        if (tracker.stoppedGliding()) return Strategy.Elytra;

        if (settings.waterBucketAssist() && tracker.fallDistance() >= settings.minFallDistance()) {
            return Strategy.MlgAssist;
        }

        if (compatibility.allows(Strategy.Packet)) return Strategy.Packet;
        return Strategy.SafeLanding;
    }

    @Override
    public void onDeactivate(Context ctx) {
        super.onDeactivate(ctx);
        lastSelected = null;
    }
}
