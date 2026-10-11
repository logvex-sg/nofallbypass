package com.logvex.nofallbypass.compat;

import com.logvex.nofallbypass.modes.NoFallMode.Strategy;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Describes what a server-side movement implementation is believed to accept.
 *
 * <p>Server-specific knowledge lives here and nowhere else. A profile is an *estimate*,
 * never a guarantee: the module always falls back to a safe strategy when a profile
 * disallows the selected one. Nothing in this interface is allowed to assert that a
 * strategy bypasses an anti-cheat; it only records whether the module is willing to
 * attempt it.
 */
public interface ServerCompatibility {
    /** Human readable profile name. */
    String name();

    /** Strategies this profile is willing to attempt. */
    Set<Strategy> allowedStrategies();

    /** Whether fabricated / modified movement packets should be attempted at all. */
    boolean allowsPacketModification();

    /** Conservative fallback used when the selected strategy is not allowed. */
    default Strategy fallbackStrategy() {
        return Strategy.SafeLanding;
    }

    /** Round-trip latency estimate in milliseconds, used to gate reactive strategies. */
    default int latencyMillis() {
        return 0;
    }

    /**
     * Pick a concrete strategy for a requested one.
     *
     * @return the requested strategy when allowed, otherwise {@link #fallbackStrategy()}.
     */
    default Strategy resolve(Strategy requested) {
        if (allowedStrategies().contains(requested)) return requested;
        return fallbackStrategy();
    }

    /** Whether the requested strategy is allowed by this profile. */
    default boolean allows(Strategy strategy) {
        return allowedStrategies().contains(strategy);
    }

    /**
     * Build the profile for a server address. Matching is on a normalised host only and
     * must never be used to make anti-cheat claims.
     */
    static ServerCompatibility forAddress(String address) {
        String host = normaliseHost(address);

        if (host.isEmpty()) return Profiles.LOCAL_OR_UNKNOWN;
        if (host.equals("6b6t.org") || host.endsWith(".6b6t.org")) return Profiles.ANARCHY_6B6T;
        if (host.equals("2b2t.org") || host.endsWith(".2b2t.org")) return Profiles.ANARCHY_GENERIC;
        if (host.contains("anarchy")) return Profiles.ANARCHY_GENERIC;

        return Profiles.VANILLA_LIKE;
    }

    /** Extract the bare host from {@code host}, {@code host:port} or a full URL. */
    static String normaliseHost(String address) {
        if (address == null) return "";
        String s = address.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "";

        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);

        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);

        // IPv6 literal
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end > 0) return s.substring(0, end + 1);
        }

        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(':') == colon) s = s.substring(0, colon);

        return s;
    }

    /**
     * Named profiles. These encode conservative assumptions, not bypass claims.
     */
    final class Profiles {
        private Profiles() {}

        /** Meta strategies are always selectable; their concrete choice is filtered separately. */
        private static final EnumSet<Strategy> META = EnumSet.of(Strategy.Adaptive, Strategy.Custom);

        private static EnumSet<Strategy> withMeta(EnumSet<Strategy> set) {
            set.addAll(META);
            return set;
        }

        /** Single player or a server we could not classify: assume strict validation. */
        public static final ServerCompatibility LOCAL_OR_UNKNOWN = new SimpleProfile(
            "local-or-unknown",
            withMeta(EnumSet.of(Strategy.Vanilla, Strategy.MlgAssist, Strategy.SafeLanding)),
            false
        );

        /** A vanilla-like server: ground spoofing is not trusted, so we do not rely on it. */
        public static final ServerCompatibility VANILLA_LIKE = new SimpleProfile(
            "vanilla-like",
            withMeta(EnumSet.of(Strategy.Vanilla, Strategy.Packet, Strategy.GrimJump, Strategy.MlgAssist, Strategy.Elytra, Strategy.Vehicle, Strategy.SafeLanding)),
            true
        );

        /**
         * Generic anarchy profile. Anarchy servers usually run a fork with a movement check;
         * the exact implementation is unknown, so the module is allowed to *attempt* packet
         * strategies but must treat them as unverified.
         */
        public static final ServerCompatibility ANARCHY_GENERIC = new SimpleProfile(
            "anarchy-generic",
            withMeta(EnumSet.of(Strategy.Vanilla, Strategy.GroundSpoof, Strategy.Packet, Strategy.GrimJump, Strategy.MlgAssist, Strategy.Elytra, Strategy.Vehicle, Strategy.SafeLanding)),
            true
        );

        /**
         * 6b6t. Treated as an anarchy server with an unknown movement check. The module may
         * attempt the same strategies as the generic anarchy profile; no bypass is claimed.
         */
        public static final ServerCompatibility ANARCHY_6B6T = new SimpleProfile(
            "anarchy-6b6t",
            withMeta(EnumSet.of(Strategy.Vanilla, Strategy.GroundSpoof, Strategy.Packet, Strategy.GrimJump, Strategy.MlgAssist, Strategy.Elytra, Strategy.Vehicle, Strategy.SafeLanding)),
            true
        );
    }

    /** Immutable simple profile. */
    final class SimpleProfile implements ServerCompatibility {
        private final String name;
        private final Set<Strategy> allowed;
        private final boolean allowsPacketModification;

        public SimpleProfile(String name, Set<Strategy> allowed, boolean allowsPacketModification) {
            this.name = name;
            this.allowed = EnumSet.copyOf(allowed);
            this.allowsPacketModification = allowsPacketModification;
        }

        @Override public String name() { return name; }
        @Override public Set<Strategy> allowedStrategies() { return EnumSet.copyOf(allowed); }
        @Override public boolean allowsPacketModification() { return allowsPacketModification; }

        @Override
        public String toString() {
            return "ServerCompatibility[" + name + "]";
        }
    }
}
