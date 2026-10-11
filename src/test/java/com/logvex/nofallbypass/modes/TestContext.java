package com.logvex.nofallbypass.modes;

import com.logvex.nofallbypass.utils.FallTracker;

import java.util.ArrayList;
import java.util.List;

/** Recording stub of {@link NoFallMode.Context} used by the mode unit tests. */
public final class TestContext implements NoFallMode.Context {
    public List<String> debug = new ArrayList<>();
    public List<String> notifications = new ArrayList<>();
    public List<Boolean> groundPackets = new ArrayList<>();
    
    public boolean packetGround;
    public boolean mlgRequested;
    public boolean canMlg = true;
    public double heightAboveGround = 1.0;

    public static TestContext of() {
        return new TestContext();
    }

    public TestContext canMlg(boolean value) { this.canMlg = value; return this; }
    public TestContext height(double value) { this.heightAboveGround = value; return this; }

    @Override public void debug(String message) { debug.add(message); }
    @Override public void notify(String message) { notifications.add(message); }
    @Override public void setPacketGround(boolean ground) { if (ground) packetGround = true; }
    @Override public void sendGroundPacket(boolean ground) { groundPackets.add(ground); }
    @Override public void requestMlg() { mlgRequested = true; }
    @Override public boolean canMlg() { return canMlg; }
    @Override public double heightAboveGround() { return heightAboveGround; }

    public static FallTracker trackerFalling(double fallDistance) {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(0, 0, true, false, false, false, false, 1));
        // One big downward step keeps the sequence inside the teleport threshold for
        // fallDistance <= 8; a fall of exactly 8 is the practical test ceiling.
        t.update(new FallTracker.Sample(-fallDistance, -1.0, false, false, false, false, false, 1));
        return t;
    }

    public static FallTracker trackerGliding() {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(100, 0, true, false, false, false, false, 1));
        t.update(new FallTracker.Sample(100, 0, false, true, false, false, false, 1));
        return t;
    }

    public static FallTracker trackerVehicle() {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(100, 0, true, false, false, false, false, 1));
        t.update(new FallTracker.Sample(100, 0, false, false, true, false, false, 1));
        return t;
    }

    public static FallTracker trackerOnGround() {
        FallTracker t = new FallTracker();
        t.update(new FallTracker.Sample(100, 0, true, false, false, false, false, 1));
        return t;
    }
}
