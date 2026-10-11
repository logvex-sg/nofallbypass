package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.compat.ServerCompatibility;
import com.logvex.nofallbypass.utils.FallTracker;

import java.util.Map;

/**
 * Runs a user chosen primary strategy and automatically falls back to a conservative strategy when
 * the primary is not allowed by the active server profile or cannot act (for example MLG with no
 * bucket in the inventory).
 *
 * <p>This is the only meta strategy that exposes a primary selection, and it never presents an
 * unimplemented strategy as working: the primary must be one of the concrete strategies installed
 * in the mode map.
 */
public final class CustomMode extends CompositeMode {
    private final ServerCompatibility compatibility;
    private Strategy primaryStrategy = Strategy.Packet;
    private Strategy lastResolved;

    public CustomMode(Map<Strategy, NoFallMode> modes, ServerCompatibility compatibility) {
        super(Strategy.Custom, "Custom", "Runs a chosen strategy with an automatic safe fallback.", modes);
        this.compatibility = compatibility;
    }

    @Override
    public void onTick(Context ctx, FallTracker tracker, NoFallSettings settings) {
        Strategy resolved = resolvePrimary(ctx, tracker, settings, primaryStrategy);

        if (resolved != lastResolved) {
            ctx.debug("Custom: using %s".formatted(resolved));
            lastResolved = resolved;
        }

        delegate(ctx, concrete(resolved), tracker, settings);
    }

    /** Set the user's chosen primary strategy. Meta strategies are rejected. */
    public void setPrimary(Strategy strategy) {
        if (strategy == null || strategy.isMeta()) return;
        this.primaryStrategy = strategy;
    }

    public Strategy primary() {
        return primaryStrategy;
    }

    private Strategy resolvePrimary(Context ctx, FallTracker tracker, NoFallSettings settings, Strategy primary) {
        if (!compatibility.allows(primary)) return compatibility.fallbackStrategy();

        if (primary == Strategy.MlgAssist) {
            if (!ctx.canMlg() || !settings.waterBucketAssist()) return compatibility.fallbackStrategy();
        }

        if (primary == Strategy.Elytra && tracker.isInVehicle()) return compatibility.fallbackStrategy();
        if (primary == Strategy.Vehicle && !tracker.isInVehicle()) return compatibility.fallbackStrategy();

        return primary;
    }

    @Override
    public void onDeactivate(Context ctx) {
        super.onDeactivate(ctx);
        lastResolved = null;
    }
}
