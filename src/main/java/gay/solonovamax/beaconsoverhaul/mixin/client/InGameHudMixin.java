package gay.solonovamax.beaconsoverhaul.mixin.client;

import gay.solonovamax.beaconsoverhaul.register.StatusEffectRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
abstract class InGameHudMixin {
    @Shadow
    private int scaledHeight;

    @ModifyVariable(
            method = "renderStatusBars",
            at = @At(
                    target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I",
                    ordinal = 0,
                    shift = At.Shift.BY,
                    by = 5,
                    value = "INVOKE",
                    opcode = Opcodes.INVOKEINTERFACE
            ),
            index = 24,
            require = 1,
            allow = 1
    )
    private int noNutritionHungerShake(int randY) {
        PlayerEntity player = this.getCameraPlayer();

        if ((player != null) && !player.getHungerManager().isNotFull()) {
            if (player.hasStatusEffect(StatusEffectRegistry.NUTRITION)) {
                return this.scaledHeight - 39; // reset it to the default value
            }
        }

        return randY;
    }

    @Shadow
    protected abstract PlayerEntity getCameraPlayer();
}
