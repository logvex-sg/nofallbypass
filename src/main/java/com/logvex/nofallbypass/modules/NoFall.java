package com.logvex.nofallbypass.modules;

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
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * Multi-mode NoFall module.
 *
 * <p>Every strategy is implemented in {@code com.logvex.nofallbypass.modes}. This class is the
 * Minecraft-aware glue: it samples the world once per tick, drives the selected mode, and performs
 * the only two side effects the modes are allowed to request — a packet ground-flag override and a
 * water-bucket clutch.
 *
 * <p>No bypass is claimed. Client-side fall mitigation cannot defeat server-side validation that
 * simulates the player's movement; the packet strategies are attempts that some servers honour and
 * others do not. The conservative strategies (Safe Landing and MLG) never fabricate movement.
 */
public class NoFall extends Module {
    /** Tag marking packets sent by this module so its own listener ignores them. */
    private static final int TAG_SELF = 1337;
    private static final double MLG_REACH = 4.5;
    /** Most bottom Y coordinate, used as the far end of the downward ray. */
    private static final double MIN_Y = -128.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgHandling = settings.createGroup("Handling");

    private final Setting<NoFallMode.Strategy> mode = sgGeneral.add(new EnumSetting.Builder<NoFallMode.Strategy>()
        .name("mode")
        .description("The fall-damage strategy to use.")
        .defaultValue(NoFallMode.Strategy.Adaptive)
        .onChanged(strategy -> applyStrategy(strategy))
        .build()
    );

    private final Setting<NoFallMode.Strategy> customPrimary = sgGeneral.add(new EnumSetting.Builder<NoFallMode.Strategy>()
        .name("custom-primary")
        .description("Primary strategy used by Custom mode. Falls back to a safe strategy when unsupported.")
        .defaultValue(NoFallMode.Strategy.Packet)
        .visible(() -> mode.get() == NoFallMode.Strategy.Custom)
        .onChanged(this::applyCustomPrimary)
        .build()
    );

    private final Setting<Boolean> adaptive = sgGeneral.add(new BoolSetting.Builder()
        .name("adaptive")
        .description("Allow Adaptive mode to re-select a strategy every tick. When off, the conservative Safe Landing strategy is used instead.")
        .defaultValue(true)
        .visible(() -> mode.get() == NoFallMode.Strategy.Adaptive)
        .build()
    );

    private final Setting<Double> minFallDistance = sgSafety.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance in blocks before any strategy intervenes.")
        .defaultValue(3.0)
        .range(0.5, 100.0)
        .sliderRange(1.0, 20.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Double> landingThreshold = sgSafety.add(new DoubleSetting.Builder()
        .name("landing-threshold")
        .description("Distance above the ground, in blocks, at which a landing counts as imminent.")
        .defaultValue(5.0)
        .range(0.5, 20.0)
        .sliderRange(1.0, 10.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Boolean> waterBucketAssist = sgSafety.add(new BoolSetting.Builder()
        .name("water-bucket-assist")
        .description("Allow MLG and Adaptive modes to place a water or powder-snow bucket.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> elytraHandling = sgHandling.add(new BoolSetting.Builder()
        .name("elytra-handling")
        .description("Handle the transition out of an elytra glide.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vehicleHandling = sgHandling.add(new BoolSetting.Builder()
        .name("vehicle-handling")
        .description("Warn about hard landings while riding a vehicle.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show a chat message when a strategy is selected, warns, or falls back.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Log strategy decisions and internal state to chat.")
        .defaultValue(false)
        .build()
    );

    private final Map<NoFallMode.Strategy, NoFallMode> modes = new EnumMap<>(NoFallMode.Strategy.class);
    private final FallTracker tracker = new FallTracker();
    private final NoFallMode.Context context = new ModuleContext();

    private AdaptiveMode adaptiveMode;
    private CustomMode customMode;

    private NoFallMode activeMode;
    private ServerCompatibility compatibility = ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN;

    private boolean overrideGround;
    private boolean pendingMlg;
    private double cachedHeightAboveGround = Double.MAX_VALUE;

    public NoFall() {
        super(Categories.Movement, "nofall-bypass", "Multi-mode fall-damage mitigation: packet, MLG, elytra, vehicle, safe landing and adaptive strategies.");
        buildModes();
    }

    private void buildModes() {
        modes.clear();
        modes.put(NoFallMode.Strategy.Vanilla, new VanillaMode());
        modes.put(NoFallMode.Strategy.GroundSpoof, new GroundSpoofMode());
        modes.put(NoFallMode.Strategy.Packet, new PacketMode());
        modes.put(NoFallMode.Strategy.MlgAssist, new MlgAssistMode());
        modes.put(NoFallMode.Strategy.Elytra, new ElytraMode());
        modes.put(NoFallMode.Strategy.Vehicle, new VehicleMode());
        modes.put(NoFallMode.Strategy.SafeLanding, new SafeLandingMode());

        adaptiveMode = new AdaptiveMode(modes, compatibility);
        customMode = new CustomMode(modes, compatibility);
        modes.put(NoFallMode.Strategy.Adaptive, adaptiveMode);
        modes.put(NoFallMode.Strategy.Custom, customMode);
    }

    // region settings bridge

    private NoFallSettings settings() {
        return new NoFallSettings(
            minFallDistance.get(),
            landingThreshold.get(),
            waterBucketAssist.get(),
            elytraHandling.get(),
            vehicleHandling.get(),
            debug.get(),
            notifications.get()
        );
    }

    // endregion

    // region lifecycle

    @Override
    public void onActivate() {
        compatibility = detectCompatibility();
        buildModes();
        customMode.setPrimary(customPrimary.get());
        tracker.reset();
        overrideGround = false;
        pendingMlg = false;
        cachedHeightAboveGround = Double.MAX_VALUE;

        switchTo(modes.get(mode.get()));
        info("Server profile: %s".formatted(compatibility.name()));
    }

    @Override
    public void onDeactivate() {
        if (activeMode != null) {
            activeMode.onDeactivate(context);
            activeMode = null;
        }
        tracker.reset();
        overrideGround = false;
        pendingMlg = false;
        cachedHeightAboveGround = Double.MAX_VALUE;
    }

    private void switchTo(NoFallMode target) {
        NoFallMode.Strategy requested = target == null ? NoFallMode.Strategy.Vanilla : target.strategy();

        // The adaptive toggle disables per-tick re-selection; fall back to Safe Landing instead.
        if (requested == NoFallMode.Strategy.Adaptive && !adaptive.get()) {
            requested = NoFallMode.Strategy.SafeLanding;
        }

        NoFallMode.Strategy resolved = compatibility.resolve(requested);

        NoFallMode next = modes.get(resolved);
        if (next == null) next = modes.get(NoFallMode.Strategy.Vanilla);
        if (next == activeMode) return;

        if (activeMode != null) activeMode.onDeactivate(context);
        activeMode = next;
    }

    private void applyStrategy(NoFallMode.Strategy strategy) {
        if (!isActive()) return;
        NoFallMode target = modes.get(strategy);
        if (target != null) switchTo(target);
    }

    private void applyCustomPrimary(NoFallMode.Strategy strategy) {
        if (customMode != null) customMode.setPrimary(strategy);
    }

    private ServerCompatibility detectCompatibility() {
        if (mc == null || mc.isInSingleplayer()) return ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN;
        if (mc.getCurrentServerEntry() == null) return ServerCompatibility.Profiles.LOCAL_OR_UNKNOWN;
        return ServerCompatibility.forAddress(mc.getCurrentServerEntry().address);
    }

    // endregion

    // region events

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetState();
        compatibility = detectCompatibility();
        buildModes();
        customMode.setPrimary(customPrimary.get());
        switchTo(modes.get(mode.get()));
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
    }

    private void resetState() {
        tracker.reset();
        overrideGround = false;
        pendingMlg = false;
        cachedHeightAboveGround = Double.MAX_VALUE;
        if (activeMode != null) {
            activeMode.onDeactivate(context);
            activeMode = null;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive()) return;
        if (overrideGround) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket)) return;
        if (((IPlayerMoveC2SPacket) event.packet).meteor$getTag() == TAG_SELF) return;

        NoFallMode current = activeMode;
        if (current == null || !current.modifiesPackets()) return;

        current.onPacket(context, tracker, settings());

        if (overrideGround) {
            ((PlayerMoveC2SPacketAccessor) event.packet).meteor$setOnGround(true);
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!isActive() || !Utils.canUpdate()) return;

        // Sample the world exactly once per tick.
        tracker.update(new FallTracker.Sample(
            mc.player.getY(),
            mc.player.getVelocity().y,
            mc.player.isOnGround(),
            mc.player.isGliding(),
            mc.player.getVehicle() != null,
            mc.player.isSubmergedInWater() || mc.player.isInLava(),
            mc.player.getAbilities().flying,
            worldId()
        ));

        cachedHeightAboveGround = computeHeightAboveGround();

        overrideGround = false;
        pendingMlg = false;

        NoFallMode current = activeMode;
        if (current == null) return;

        current.onTick(context, tracker, settings());

        if (pendingMlg) {
            performMlg();
        }
    }

    private int worldId() {
        return mc.world == null ? -1 : System.identityHashCode(mc.world);
    }

    // endregion

    // region helpers

    private double computeHeightAboveGround() {
        Vec3d from = mc.player.getEntityPos();
        Vec3d to = from.subtract(0, from.y - MIN_Y, 0);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return Double.MAX_VALUE;
        return from.y - hit.getBlockPos().getY();
    }

    private FindItemResult findBucket() {
        return InvUtils.findInHotbar(Items.WATER_BUCKET, Items.POWDER_SNOW_BUCKET);
    }

    private void performMlg() {
        if (!Utils.canUpdate()) return;
        if (cachedHeightAboveGround <= 0 || cachedHeightAboveGround > MLG_REACH) {
            debugLog("MLG aborted: target out of reach (%.2f)".formatted(cachedHeightAboveGround));
            return;
        }

        FindItemResult bucket = findBucket();
        if (!bucket.found()) return;

        Vec3d from = mc.player.getEntityPos();
        Vec3d to = from.subtract(0, from.y - MIN_Y, 0);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos target = hit.getBlockPos();
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 50, true, () -> {
            if (bucket.isOffhand()) {
                mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hit);
            } else {
                InvUtils.swap(bucket.slot(), true);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                InvUtils.swapBack();
            }
        });
    }

    private void debugLog(String message) {
        if (debug.get()) info(message);
    }

    // endregion

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }

    private final class ModuleContext implements NoFallMode.Context {
        @Override
        public void debug(String message) {
            if (debug.get()) NoFall.this.info(message);
        }

        @Override
        public void notify(String message) {
            if (notifications.get()) NoFall.this.info(message);
        }

        @Override
        public void setPacketGround(boolean ground) {
            if (ground) overrideGround = true;
        }

        @Override
        public void sendGroundPacket(boolean ground) {
            if (!Utils.canUpdate()) return;
            if (mc.getNetworkHandler() == null) return;

            PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.OnGroundOnly(ground, false);
            ((IPlayerMoveC2SPacket) packet).meteor$setTag(TAG_SELF);
            mc.getNetworkHandler().getConnection().send(packet);
        }

        @Override
        public void requestMlg() {
            pendingMlg = true;
        }

        @Override
        public boolean canMlg() {
            if (!Utils.canUpdate()) return false;
            if (cachedHeightAboveGround <= 0 || cachedHeightAboveGround > MLG_REACH) return false;
            return findBucket().found();
        }

        @Override
        public double heightAboveGround() {
            return cachedHeightAboveGround;
        }
    }
}
