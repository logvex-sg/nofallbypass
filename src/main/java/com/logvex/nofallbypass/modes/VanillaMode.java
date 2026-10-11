package com.logvex.nofallbypass.modes;

/**
 * Makes no changes at all. Selecting this mode is equivalent to leaving the module off while
 * still keeping the module's settings loaded; it exists so the module can be parked without
 * being disabled.
 */
public final class VanillaMode extends NoFallMode {
    public VanillaMode() {
        super(Strategy.Vanilla, "Vanilla", "No changes. Preserves normal Minecraft fall behaviour.");
    }
}
