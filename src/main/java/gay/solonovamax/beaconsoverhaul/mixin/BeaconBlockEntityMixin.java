package gay.solonovamax.beaconsoverhaul.mixin;

import ca.solostudios.guava.kotlin.collect.MultisetsKt;
import ca.solostudios.guava.kotlin.collect.MutableMultiset;
import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gay.solonovamax.beaconsoverhaul.block.beacon.OverhauledBeacon;
import gay.solonovamax.beaconsoverhaul.block.beacon.OverhauledBeaconPropertyDelegate;
import gay.solonovamax.beaconsoverhaul.block.beacon.blockentity.BeaconBeamSegment;
import gay.solonovamax.beaconsoverhaul.block.beacon.blockentity.OverhauledBeaconBlockEntityKt;
import gay.solonovamax.beaconsoverhaul.config.ConfigManager;
import kotlinx.datetime.Instant;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SuppressWarnings({"PackageVisibleField", "CastToIncompatibleInterface"})
@Mixin(BeaconBlockEntity.class)
@Debug(export = true)
abstract class BeaconBlockEntityMixin extends BlockEntity implements ExtendedScreenHandlerFactory, OverhauledBeacon {
    @Unique
    @NotNull
    private final List<ServerPlayerEntity> listeningPlayers = Lists.newArrayList();

    @Shadow
    public int level;

    @Shadow
    @Nullable
    public StatusEffect primary;

    @Shadow
    @Nullable
    public StatusEffect secondary;

    @Shadow
    @NotNull
    public List<BeaconBlockEntity.BeamSegment> beamSegments;

    @Shadow
    public int minY;

    @Shadow
    private List<BeaconBlockEntity.BeamSegment> field_19178;

    @Unique
    private boolean brokenBeam = false;

    @Unique
    @NotNull
    private Instant lastUpdate = Instant.Companion.getDISTANT_PAST();

    @Unique
    @NotNull
    private MutableMultiset<Block> baseBlocks = MultisetsKt.mutableMultisetOf();

    @Final
    @Shadow
    @Mutable
    @NotNull
    private PropertyDelegate propertyDelegate;

    @Unique
    private double beaconPoints = 0.0;

    @Unique
    private boolean didRedirection = false;

    BeaconBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    target = "Lnet/minecraft/block/entity/BeaconBlockEntity;updateLevel(Lnet/minecraft/world/World;III)I",
                    value = "INVOKE"
            )
    )
    private static int updateLevel(World world, int x, int y, int z) {
        OverhauledBeacon beacon = (OverhauledBeacon) world.getBlockEntity(new BlockPos(x, y, z));
        assert beacon != null;

        return beacon.getLevel();
    }

    // This captures the for loop inside tick that computes the beacon segments
    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "intValue=0", ordinal = 0))
    private static int constructBeamSegments(int original, World level, BlockPos pos, BlockState state, BeaconBlockEntity beacon) {
        OverhauledBeaconBlockEntityKt.constructBeamSegments((OverhauledBeacon) beacon);

        return Integer.MAX_VALUE;
    }

    @Inject(
            method = "tick",
            at = @At(
                    target = "Lnet/minecraft/block/entity/BeaconBlockEntity;level:I",
                    shift = At.Shift.BY,
                    by = 2,
                    value = "FIELD",
                    opcode = Opcodes.INVOKESTATIC,
                    ordinal = 0
            )
    )
    private static void updateTier(World world, BlockPos pos, BlockState beaconState, BeaconBlockEntity beacon, CallbackInfo ci) {
        OverhauledBeaconBlockEntityKt.updateTier((OverhauledBeacon) beacon, world, pos);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
            )
    )
    private static <T> List<T> disableAdvancementTrigger(World instance, Class<T> aClass, Box box) {
        return List.of();
    }

    @ModifyVariable(
            method = "applyPlayerEffects",
            at = @At(value = "STORE", opcode = Opcodes.DSTORE, ordinal = 0),
            index = 5,
            require = 1,
            allow = 1
    )
    private static double modifyRange(double radius, World world, BlockPos pos) {
        OverhauledBeacon beacon = (OverhauledBeacon) world.getBlockEntity(pos);
        assert beacon != null;

        return beacon.getRange();
    }

    @ModifyVariable(
            method = "applyPlayerEffects",
            at = @At(value = "STORE", opcode = Opcodes.ISTORE, ordinal = 0),
            index = 7,
            require = 1,
            allow = 1
    )
    private static int modifyPrimaryAmplifier(int primaryAmplifier, World world, BlockPos pos, int levels,
                                              @Nullable StatusEffect primaryEffect) {
        OverhauledBeacon beacon = (OverhauledBeacon) world.getBlockEntity(pos);
        assert beacon != null;

        if (!ConfigManager.getBeaconConfig().getLevelOneStatusEffects().contains(primaryEffect))
            return beacon.getPrimaryAmplifier() - 1;
        else
            return 0; // 0 = level 1
    }

    @ModifyVariable(
            method = "applyPlayerEffects",
            at = @At(value = "STORE", opcode = Opcodes.ISTORE, ordinal = 1),
            index = 7,
            require = 1,
            allow = 1
    )
    private static int modifyPotentPrimaryAmplifier(int primaryAmplifier, World world, BlockPos pos, int levels,
                                                    @Nullable StatusEffect primaryEffect, @Nullable StatusEffect secondaryEffect) {
        OverhauledBeacon beacon = (OverhauledBeacon) world.getBlockEntity(pos);

        assert beacon != null;
        if (!ConfigManager.getBeaconConfig().getLevelOneStatusEffects().contains(primaryEffect))
            return beacon.getPrimaryAmplifierPotent() - 1;
        else
            return 0;

    }

    // Cannot use ModifyArg here as we need to capture the target method parameters
    @ModifyConstant(method = "applyPlayerEffects", constant = @Constant(/*intValue = 0,*/ ordinal = 1), require = 1, allow = 1)
    private static int modifySecondaryAmplifier(int secondaryAmplifier, World world, BlockPos pos, int levels,
                                                @Nullable StatusEffect primaryEffect, @Nullable StatusEffect secondaryEffect) {
        OverhauledBeacon beacon = (OverhauledBeacon) world.getBlockEntity(pos);
        assert beacon != null;

        if (!ConfigManager.getBeaconConfig().getLevelOneStatusEffects().contains(secondaryEffect))
            return beacon.getSecondaryAmplifier() - 1;
        else
            return 0;
    }

    @ModifyVariable(
            method = "applyPlayerEffects",
            at = @At(value = "STORE", opcode = Opcodes.ISTORE, ordinal = 0),
            index = 8,
            require = 1,
            allow = 1
    )
    private static int modifyDuration(int duration, World world, BlockPos pos, int levels) {
        OverhauledBeacon beacon = (OverhauledBeacon) world.getBlockEntity(pos);
        assert beacon != null;

        return beacon.getDuration();
    }

    @Redirect(
            method = "applyPlayerEffects",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/entity/effect/StatusEffect;IIZZ)Lnet/minecraft/entity/effect/StatusEffectInstance;"
            ),
            expect = 2
    )
    private static StatusEffectInstance disableEffectParticles(StatusEffect type, int duration, int amplifier, boolean ambient,
                                                               boolean visible) {
        return new StatusEffectInstance(type, duration, amplifier, ambient, ConfigManager.getBeaconConfig()
                .getEffectParticles());
    }

    @Inject(method = "<init>(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", at = @At("TAIL"))
    private void init(CallbackInfo info) {
        this.propertyDelegate = new OverhauledBeaconPropertyDelegate(this);
    }

    @Inject(
            method = "createMenu",
            at = @At(
                    value = "NEW",
                    target = "(ILnet/minecraft/inventory/Inventory;Lnet/minecraft/screen/PropertyDelegate;Lnet/minecraft/screen/ScreenHandlerContext;)Lnet/minecraft/screen/BeaconScreenHandler;"
            ),
            cancellable = true
    )
    private void createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity,
                            CallbackInfoReturnable<ScreenHandler> cir) {
        cir.setReturnValue(OverhauledBeaconBlockEntityKt.createMenu(this, syncId, playerEntity));
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void readOverhauledNbt(NbtCompound nbt, CallbackInfo ci) {
        this.level = nbt.getInt("Levels");
        this.beaconPoints = nbt.getDouble("BeaconPoints");
        this.didRedirection = nbt.getBoolean("DidRedirection");

        String primaryIdentifier = nbt.getString("Primary");
        if (!primaryIdentifier.isBlank())
            this.primary = Registries.STATUS_EFFECT.get(new Identifier(primaryIdentifier));

        String secondaryIdentifier = nbt.getString("Secondary");
        if (!secondaryIdentifier.isBlank())
            this.secondary = Registries.STATUS_EFFECT.get(new Identifier(secondaryIdentifier));
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void writeOverhauledNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putDouble("BeaconPoints", this.beaconPoints);
        nbt.putBoolean("DidRedirection", this.didRedirection);

        Identifier primaryId = Registries.STATUS_EFFECT.getId(this.primary);
        if (this.primary != null && primaryId != null)
            nbt.putString("Primary", primaryId.toString());

        Identifier secondaryId = Registries.STATUS_EFFECT.getId(this.secondary);
        if (this.secondary != null && secondaryId != null)
            nbt.putString("Secondary", secondaryId.toString());
    }

    @Unique
    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        OverhauledBeaconBlockEntityKt.writeScreenOpeningData(this, player, buf);
    }

    @Override
    public int getMinY() {
        return this.minY;
    }

    @Override
    public void setMinY(int minY) {
        this.minY = minY;
    }

    @Unique
    @NotNull
    @Override
    public Instant getLastUpdate() {
        return this.lastUpdate;
    }

    @Unique
    @Override
    public void setLastUpdate(@NotNull Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Unique
    @NotNull
    @Override
    public MutableMultiset<Block> getBaseBlocks() {
        return this.baseBlocks;
    }

    @Unique
    @Override
    public void setBaseBlocks(@NotNull MutableMultiset<Block> baseBlocks) {
        this.baseBlocks = baseBlocks;
    }

    @Unique
    @Override
    public int getLevel() {
        return this.level;
    }

    @Unique
    @Override
    public void setLevel(int level) {
        this.level = level;
    }

    @Unique
    @Override
    public double getBeaconPoints() {
        return this.beaconPoints;
    }

    @Unique
    @Override
    public void setBeaconPoints(double beaconPoints) {
        this.beaconPoints = beaconPoints;
    }

    @Unique
    @NotNull
    @Override
    public OverhauledBeaconPropertyDelegate getPropertyDelegate() {
        return (OverhauledBeaconPropertyDelegate) this.propertyDelegate;
    }

    @Unique
    @Override
    public int getRange() {
        return ConfigManager.getBeaconConfig().calculateRange(this.beaconPoints);
    }

    @Unique
    @Override
    public int getDuration() {
        return ConfigManager.getBeaconConfig().calculateDuration(this.beaconPoints);
    }

    @Unique
    @Nullable
    @Override
    @SuppressWarnings("SuspiciousGetterSetter")
    public StatusEffect getPrimaryEffect() {
        return this.primary;
    }

    @Unique
    @Override
    @SuppressWarnings("SuspiciousGetterSetter")
    public void setPrimaryEffect(@Nullable StatusEffect primaryEffect) {
        this.primary = primaryEffect;
    }

    @Unique
    @Nullable
    @Override
    @SuppressWarnings("SuspiciousGetterSetter")
    public StatusEffect getSecondaryEffect() {
        return this.secondary;
    }

    @Unique
    @Override
    @SuppressWarnings("SuspiciousGetterSetter")
    public void setSecondaryEffect(@Nullable StatusEffect secondaryEffect) {
        this.secondary = secondaryEffect;
    }

    @Unique
    @NotNull
    @Override
    public World getWorld() {
        return Objects.requireNonNull(this.world);
    }

    @Unique
    @NotNull
    @Override
    public BlockPos getPos() {
        return this.pos;
    }

    @Unique
    @NotNull
    @Override
    @SuppressWarnings({"AssignmentOrReturnOfFieldWithMutableType", "unchecked"})
    public List<BeaconBeamSegment> getBeamSegments() {
        return (List<BeaconBeamSegment>) (List<?>) this.beamSegments;
    }

    @Unique
    @NotNull
    @Override
    @SuppressWarnings({"AssignmentOrReturnOfFieldWithMutableType", "unchecked"})
    public List<BeaconBeamSegment> getBeamSegmentsToCheck() {
        return (List<BeaconBeamSegment>) (List<?>) this.field_19178;
    }

    @Unique
    @Override
    @SuppressWarnings({"AssignmentOrReturnOfFieldWithMutableType", "unchecked"})
    public void setBeamSegmentsToCheck(@NotNull List<BeaconBeamSegment> beamSegmentsToCheck) {
        this.field_19178 = (List<BeaconBlockEntity.BeamSegment>) (List<?>) beamSegmentsToCheck;
    }

    @Override
    public boolean getBrokenBeam() {
        return this.brokenBeam;
    }

    @Override
    public void setBrokenBeam(boolean brokenBeam) {
        this.brokenBeam = brokenBeam;
    }

    @Override
    public boolean getDidRedirection() {
        return this.didRedirection;
    }

    @Override
    public void setDidRedirection(boolean didRedirection) {
        this.didRedirection = didRedirection;
    }

    @Unique
    @Override
    public int getPrimaryAmplifier() {
        return ConfigManager.getBeaconConfig().calculatePrimaryAmplifier(this.beaconPoints, false);
    }

    @Unique
    @Override
    public int getPrimaryAmplifierPotent() {
        return ConfigManager.getBeaconConfig().calculatePrimaryAmplifier(this.beaconPoints, true);
    }

    @Unique
    @Override
    public int getSecondaryAmplifier() {
        return ConfigManager.getBeaconConfig().calculateSecondaryAmplifier(this.beaconPoints);
    }

    @Unique
    @NotNull
    @Override
    public List<ServerPlayerEntity> getListeningPlayers() {
        return Collections.unmodifiableList(this.listeningPlayers);
    }

    @Unique
    @Override
    public void addUpdateListener(@NotNull ServerPlayerEntity player) {
        this.listeningPlayers.add(player);
    }

    @Unique
    @Override
    public void removeUpdateListener(@NotNull PlayerEntity player) {
        this.listeningPlayers.remove(player);
    }

    @Unique
    @Override
    public boolean canApplyEffect(@NotNull StatusEffect effect) {
        return OverhauledBeaconBlockEntityKt.testCanApplyEffect(this, effect);
    }
}
