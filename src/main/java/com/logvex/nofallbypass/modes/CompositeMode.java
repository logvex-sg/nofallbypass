package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

import java.util.Map;

/**
 * Base class for strategies that delegate to another concrete strategy.
 *
 * <p>Handles the switch lifecycle so that a delegated mode's {@link NoFallMode#onDeactivate} is
 * always called before another is activated, and forwards tick and packet events.
 */
public abstract class CompositeMode extends NoFallMode {
    protected final Map<Strategy, NoFallMode> modes;
    private NoFallMode active;

    protected CompositeMode(Strategy strategy, String name, String description, Map<Strategy, NoFallMode> modes) {
        super(strategy, name, description);
        this.modes = modes;
    }

    /** Resolve a strategy to an installed concrete mode, never returning a meta mode. */
    protected final NoFallMode concrete(Strategy strategy) {
        NoFallMode mode = modes.get(strategy);
        if (mode == null || mode.strategy().isMeta()) return modes.get(Strategy.Vanilla);
        return mode;
    }

    protected final NoFallMode activeMode() {
        return active;
    }

    protected final void delegate(Context ctx, NoFallMode target, FallTracker tracker, NoFallSettings settings) {
        if (target == null) target = concrete(Strategy.Vanilla);

        if (target != active) {
            if (active != null) active.onDeactivate(ctx);
            active = target;
            onSwitch(ctx, target);
        }

        active.onTick(ctx, tracker, settings);
    }

    /** Called when the delegated mode changes. Default is a debug line. */
    protected void onSwitch(Context ctx, NoFallMode target) {
        ctx.debug("%s: selected %s".formatted(name(), target.name()));
    }

    @Override
    public boolean modifiesPackets() {
        return active != null && active.modifiesPackets();
    }

    @Override
    public void onPacket(Context ctx, FallTracker tracker, NoFallSettings settings) {
        if (active != null) active.onPacket(ctx, tracker, settings);
    }

    @Override
    public void onDeactivate(Context ctx) {
        if (active != null) {
            active.onDeactivate(ctx);
            active = null;
        }
    }
}
