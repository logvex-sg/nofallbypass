package com.logvex.nofallbypass;

import com.logvex.nofallbypass.compat.ServerCompatibility;
import com.logvex.nofallbypass.modes.AdaptiveMode;
import com.logvex.nofallbypass.modes.CustomMode;
import com.logvex.nofallbypass.modes.ElytraMode;
import com.logvex.nofallbypass.modes.GroundSpoofMode;
import com.logvex.nofallbypass.modes.MlgAssistMode;
import com.logvex.nofallbypass.modes.NoFallMode;
import com.logvex.nofallbypass.modes.NoFallSettings;
import com.logvex.nofallbypass.modes.PacketMode;
import com.logvex.nofallbypass.modes.SafeLandingMode;
import com.logvex.nofallbypass.modes.VanillaMode;
import com.logvex.nofallbypass.modes.VehicleMode;
import com.logvex.nofallbypass.utils.FallTracker;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveSelectionTest {
    private static Map<NoFallMode.Strategy, NoFallMode> modes(ServerCompatibility compat) {
        Map<NoFallMode.Strategy, NoFallMode> m = new EnumMap<>(NoFallMode.Strategy.class);
        m.put(NoFallMode.Strategy.Vanilla, new VanillaMode());
        m.put(NoFallMode.Strategy.GroundSpoof, new GroundSpoofMode());
        m.put(NoFallMode.Strategy.Packet, new PacketMode());
        m.put(NoFallMode.Strategy.MlgAssist, new MlgAssistMode());
        m.put(NoFallMode.Strategy.Elytra, new ElytraMode());
        m.put(NoFallMode.Strategy.Vehicle, new VehicleMode());
        m.put(NoFallMode.Strategy.SafeLanding, new SafeLandingMode());
        AdaptiveMode a = new AdaptiveMode(m, compat);
        CustomMode c = new CustomMode(m, compat);
        m.put(NoFallMode.Strategy.Adaptive, a);
        m.put(NoFallMode.Strategy.Custom, c);
        return m;
    }

    private static FallTracker onGround() {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(100, 0, true, false, false, false, false, 1));
        return t;
    }

    private static FallTracker falling(double d) {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(100, 0, true, false, false, false, false, 1));
        t.update(new FallTracker.Sample(100 - d, -1, false, false, false, false, false, 1));
        return t;
    }

    private static FallTracker gliding() {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(100, 0, true, false, false, false, false, 1));
        t.update(new FallTracker.Sample(100, 0, false, true, false, false, false, 1));
        return t;
    }

    private static FallTracker riding() {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(100, 0, true, false, false, false, false, 1));
        t.update(new FallTracker.Sample(100, 0, false, false, true, false, false, 1));
        return t;
    }

    @Test
    void vanillaWhenNotFalling() {
        AdaptiveMode a = new AdaptiveMode(modes(ServerCompatibility.Profiles.ANARCHY_6B6T), ServerCompatibility.Profiles.ANARCHY_6B6T);
        assertEquals(NoFallMode.Strategy.Vanilla, a.select(onGround(), NoFallSettings.defaults()));
    }

    @Test
    void vehicleTakesPriority() {
        AdaptiveMode a = new AdaptiveMode(modes(ServerCompatibility.Profiles.ANARCHY_6B6T), ServerCompatibility.Profiles.ANARCHY_6B6T);
        assertEquals(NoFallMode.Strategy.Vehicle, a.select(riding(), NoFallSettings.defaults()));
    }

    @Test
    void elytraTakesPriorityOverMlg() {
        AdaptiveMode a = new AdaptiveMode(modes(ServerCompatibility.Profiles.ANARCHY_6B6T), ServerCompatibility.Profiles.ANARCHY_6B6T);
        assertEquals(NoFallMode.Strategy.Elytra, a.select(gliding(), NoFallSettings.defaults()));
    }

    @Test
    void mlgWhenFallingPastThreshold() {
        AdaptiveMode a = new AdaptiveMode(modes(ServerCompatibility.Profiles.ANARCHY_6B6T), ServerCompatibility.Profiles.ANARCHY_6B6T);
        assertEquals(NoFallMode.Strategy.MlgAssist, a.select(falling(4), NoFallSettings.defaults()));
    }

    @Test
    void mlgSkippedWhenAssistDisabled() {
        AdaptiveMode a = new AdaptiveMode(modes(ServerCompatibility.Profiles.ANARCHY_6B6T), ServerCompatibility.Profiles.ANARCHY_6B6T);
        NoFallSettings noBucket = new NoFallSettings(3, 5, false, true, true, false, true, 3, false, false, true);
        assertEquals(NoFallMode.Strategy.Packet, a.select(falling(4), noBucket));
    }

    @Test
    void strictProfileResolvesUnsupportedStrategyToFallback() {
        ServerCompatibility strict = ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN;
        // Packet and GroundSpoof are not allowed locally, so both resolve to the safe fallback.
        assertEquals(NoFallMode.Strategy.SafeLanding, strict.resolve(NoFallMode.Strategy.Packet));
        assertEquals(NoFallMode.Strategy.SafeLanding, strict.resolve(NoFallMode.Strategy.GroundSpoof));
        // SafeLanding itself is allowed and stays unchanged.
        assertEquals(NoFallMode.Strategy.SafeLanding, strict.resolve(NoFallMode.Strategy.SafeLanding));
    }

    @Test
    void anarchyAllowsGroundSpoofAndPacket() {
        ServerCompatibility anarch = ServerCompatibility.Profiles.ANARCHY_6B6T;
        assertTrue(anarch.allows(NoFallMode.Strategy.GroundSpoof));
        assertTrue(anarch.allows(NoFallMode.Strategy.Packet));
        assertTrue(anarch.allowsPacketModification());
    }

    @Test
    void customFallsBackWhenPrimaryUnsupported() {
        ServerCompatibility strict = ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN;
        Map<NoFallMode.Strategy, NoFallMode> m = modes(strict);
        CustomMode c = (CustomMode) m.get(NoFallMode.Strategy.Custom);
        c.setPrimary(NoFallMode.Strategy.Packet);

        com.logvex.nofallbypass.modes.TestContext ctx = com.logvex.nofallbypass.modes.TestContext.of();
        // onTick resolves Packet -> SafeLanding under the strict profile; the debug line names it.
        c.onTick(ctx, falling(4), NoFallSettings.defaults());
        assertTrue(ctx.debug.stream().anyMatch(s -> s.contains("SafeLanding")),
            "expected a fallback debug line, got " + ctx.debug);
    }

    @Test
    void metaStrategiesCannotBeSetAsCustomPrimary() {
        Map<NoFallMode.Strategy, NoFallMode> m = modes(ServerCompatibility.Profiles.ANARCHY_6B6T);
        CustomMode c = (CustomMode) m.get(NoFallMode.Strategy.Custom);
        c.setPrimary(NoFallMode.Strategy.Adaptive);
        assertNotEquals(NoFallMode.Strategy.Adaptive, c.primary());
    }
}
