package com.logvex.nofallbypass;

import com.logvex.nofallbypass.modules.NoFall;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/**
 * Addon entry point. Registered through the {@code meteor} entrypoint in {@code fabric.mod.json}.
 * The module is a plain Meteor {@link meteordevelopment.meteorclient.systems.modules.Module}, so
 * once it is added to {@link Modules} Meteor handles enabling, disabling, settings persistence and
 * event subscription.
 */
public final class NoFallBypassAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initialising NoFall Bypass addon");
        Modules.get().add(new NoFall());
    }

    @Override
    public String getPackage() {
        return "com.logvex.nofallbypass";
    }
}
