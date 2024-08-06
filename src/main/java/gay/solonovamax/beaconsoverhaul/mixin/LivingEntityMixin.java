package gay.solonovamax.beaconsoverhaul.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin extends Entity {
    @Unique
    private float previousStepHeight = 0.0f;

    @Unique
    private boolean stepIncreased = false;

    LivingEntityMixin(EntityType<?> type, World level) {
        super(type, level);
    }

    @Shadow
    public abstract AttributeContainer getAttributes();

    @Shadow
    protected abstract void onStatusEffectUpgraded(StatusEffectInstance effect, boolean reapplyEffect, @Nullable Entity source);

    @Inject(method = "tickStatusEffects", at = @At("HEAD"), require = 1)
    private void updateJumpBoostStepAssist(CallbackInfo ci) {
        if (this.hasStatusEffect(StatusEffects.JUMP_BOOST) && !this.isSneaking()) {
            if (!this.stepIncreased) {
                this.previousStepHeight = this.getStepHeight();
                this.setStepHeight(1.0F);
                this.stepIncreased = true;
            }
        } else if (this.stepIncreased) {
            this.setStepHeight(this.previousStepHeight);
            this.stepIncreased = false;
        }
    }

    /**
     * Don't damage tick when the health boost effect is being upgraded/re-applied.
     * TODO: HealthBoost class does not exist anymore, no idea what this does and if it's still needed
     */
    /*@Redirect(
            method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;onStatusEffectUpgraded(Lnet/minecraft/entity/effect/StatusEffectInstance;ZLnet/minecraft/entity/Entity;)V"
            )
    )
    private void fixHealthBoostDamageTick(LivingEntity instance, StatusEffectInstance effect, boolean reapplyEffect, Entity source) {
        if (!this.getWorld().isClient) {
            StatusEffect statusEffect = effect.getEffectType();
            if (statusEffect != StatusEffects.HEALTH_BOOST)
                statusEffect.onRemoved((LivingEntity) (Object) this, this.getAttributes(), effect.getAmplifier());
            statusEffect.onApplied((LivingEntity) (Object) this, this.getAttributes(), effect.getAmplifier());
        }

        this.onStatusEffectUpgraded(effect, false, source);
    }*/

    @ModifyExpressionValue(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z",
                    ordinal = 0
            ),
            require = 1,
            allow = 1
    )
    private boolean dropIfCrouching(boolean original) {
        return original && this.isSneaking();
    }

    @Shadow
    public abstract boolean hasStatusEffect(StatusEffect effect);
}
