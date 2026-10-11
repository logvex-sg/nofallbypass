package com.logvex.nofallbypass;

import com.logvex.nofallbypass.compat.ServerCompatibility;
import com.logvex.nofallbypass.modes.NoFallMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerCompatibilityTest {
    @Test
    void normalisesHosts() {
        assertEquals("example.com", ServerCompatibility.normaliseHost("example.com"));
        assertEquals("example.com", ServerCompatibility.normaliseHost("Example.COM"));
        assertEquals("example.com", ServerCompatibility.normaliseHost("example.com:25565"));
        assertEquals("example.com", ServerCompatibility.normaliseHost("https://example.com/path"));
        assertEquals("6b6t.org", ServerCompatibility.normaliseHost("6b6t.org:25565"));
        assertEquals("[::1]", ServerCompatibility.normaliseHost("[::1]:25565"));
        assertEquals("", ServerCompatibility.normaliseHost(null));
    }

    @Test
    void matchesSixB6tWithoutHardcodingElsewhere() {
        assertEquals(ServerCompatibility.Profiles.ANARCHY_6B6T, ServerCompatibility.forAddress("6b6t.org"));
        assertEquals(ServerCompatibility.Profiles.ANARCHY_6B6T, ServerCompatibility.forAddress("play.6b6t.org:25565"));
    }

    @Test
    void unknownAddressFallsBackToVanillaLike() {
        assertEquals(ServerCompatibility.Profiles.VANILLA_LIKE, ServerCompatibility.forAddress("some.random.host"));
    }

    @Test
    void emptyAddressIsLocalOrUnknown() {
        assertEquals(ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN, ServerCompatibility.forAddress(""));
    }

    @Test
    void resolveReturnsRequestedWhenAllowed() {
        ServerCompatibility c = ServerCompatibility.Profiles.ANARCHY_GENERIC;
        assertEquals(NoFallMode.Strategy.Packet, c.resolve(NoFallMode.Strategy.Packet));
    }

    @Test
    void resolveFallsBackWhenNotAllowed() {
        ServerCompatibility strict = ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN;
        assertEquals(NoFallMode.Strategy.SafeLanding, strict.resolve(NoFallMode.Strategy.Packet));
        assertEquals(NoFallMode.Strategy.SafeLanding, strict.resolve(NoFallMode.Strategy.GroundSpoof));
    }

    @Test
    void localProfileDoesNotAllowPacketModification() {
        assertFalse(ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN.allowsPacketModification());
    }
}
