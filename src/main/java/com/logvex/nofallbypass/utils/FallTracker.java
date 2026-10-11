package com.logvex.nofallbypass.utils;

/**
 * Minecraft-independent fall state tracker.
 *
 * <p>Feed it one {@link Sample} per client tick. The tracker derives the current fall
 * distance, whether the player is falling, and single-tick transition flags
 * (landed, started falling, world changed, gliding toggled, vehicle toggled, teleport).
 * Keeping this class free of Minecraft types makes it unit testable without a game client.
 */
public final class FallTracker {
    /** Immutable snapshot of the inputs the tracker needs for one tick. */
    public record Sample(
        double y,
        double verticalVelocity,
        boolean onGround,
        boolean gliding,
        boolean inVehicle,
        boolean inFluid,
        boolean flying,
        int worldId
    ) {}

    /** A downward or upward Y delta larger than this is treated as a teleport / desync. */
    public static final double TELEPORT_THRESHOLD = 8.0;

    private boolean initialized;

    private double lastY;
    private double fallStartY;
    private double fallDistance;
    private double lastLandedFallDistance;
    private int ticksFalling;

    private boolean onGround;
    private boolean gliding;
    private boolean inVehicle;
    private boolean inFluid;
    private boolean flying;
    private int worldId;

    private boolean justLanded;
    private boolean justStartedFalling;
    private boolean worldChanged;
    private boolean startedGliding;
    private boolean stoppedGliding;
    private boolean enteredVehicle;
    private boolean leftVehicle;
    private boolean teleported;

    public void reset() {
        initialized = false;
        lastY = 0;
        fallStartY = 0;
        fallDistance = 0;
        lastLandedFallDistance = 0;
        ticksFalling = 0;
        onGround = false;
        gliding = false;
        inVehicle = false;
        inFluid = false;
        flying = false;
        worldId = 0;
        clearTransitions();
    }

    private void clearTransitions() {
        justLanded = false;
        justStartedFalling = false;
        worldChanged = false;
        startedGliding = false;
        stoppedGliding = false;
        enteredVehicle = false;
        leftVehicle = false;
        teleported = false;
    }

    public void update(Sample s) {
        clearTransitions();

        if (!initialized) {
            initialized = true;
            fallStartY = s.y();
            adopt(s);
            return;
        }

        worldChanged = s.worldId() != worldId;

        double deltaY = s.y() - lastY;
        teleported = Math.abs(deltaY) > TELEPORT_THRESHOLD;

        startedGliding = s.gliding() && !gliding;
        stoppedGliding = !s.gliding() && gliding;
        enteredVehicle = s.inVehicle() && !inVehicle;
        leftVehicle = !s.inVehicle() && inVehicle;

        boolean wasOnGround = onGround;
        double previousFallDistance = fallDistance;

        // A teleport or world change invalidates all accumulated fall state.
        if (teleported || worldChanged) {
            fallDistance = 0;
            ticksFalling = 0;
            fallStartY = s.y();
            adopt(s);
            return;
        }

        boolean fallCounts = !s.onGround() && !s.gliding() && !s.inVehicle() && !s.inFluid() && !s.flying();

        if (s.onGround()) {
            if (!wasOnGround) {
                justLanded = true;
                lastLandedFallDistance = previousFallDistance;
            }
            fallDistance = 0;
            ticksFalling = 0;
            fallStartY = s.y();
        } else if (fallCounts) {
            fallStartY = Math.max(fallStartY, s.y());
            fallDistance = Math.max(0, fallStartY - s.y());
            ticksFalling++;
        } else {
            // Gliding, riding, swimming or creative flight: no vanilla fall damage accrues.
            fallDistance = 0;
            ticksFalling = 0;
            fallStartY = s.y();
        }

        justStartedFalling = previousFallDistance <= 0 && fallDistance > 0;

        adopt(s);
    }

    private void adopt(Sample s) {
        lastY = s.y();
        onGround = s.onGround();
        gliding = s.gliding();
        inVehicle = s.inVehicle();
        inFluid = s.inFluid();
        flying = s.flying();
        worldId = s.worldId();
    }

    public boolean isInitialized() { return initialized; }
    public boolean isFalling() { return !onGround && fallDistance > 0 && ticksFalling > 0; }
    public double fallDistance() { return fallDistance; }
    public double lastLandedFallDistance() { return lastLandedFallDistance; }
    public int ticksFalling() { return ticksFalling; }
    public boolean isOnGround() { return onGround; }
    public boolean isGliding() { return gliding; }
    public boolean isInVehicle() { return inVehicle; }
    public boolean isInFluid() { return inFluid; }
    public boolean isFlying() { return flying; }

    public boolean justLanded() { return justLanded; }
    public boolean justStartedFalling() { return justStartedFalling; }
    public boolean worldChanged() { return worldChanged; }
    public boolean startedGliding() { return startedGliding; }
    public boolean stoppedGliding() { return stoppedGliding; }
    public boolean enteredVehicle() { return enteredVehicle; }
    public boolean leftVehicle() { return leftVehicle; }
    public boolean teleported() { return teleported; }
}
