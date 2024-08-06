package gay.solonovamax.beaconsoverhaul.block.beacon

import net.minecraft.block.entity.BeaconBlockEntity
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.screen.BeaconScreenHandler
import net.minecraft.screen.PropertyDelegate
import net.minecraft.sound.SoundEvents

class OverhauledBeaconPropertyDelegate(
    private val beacon: OverhauledBeacon,
) : PropertyDelegate {
    override fun get(index: Int): Int {
        return when (index) {
            0 -> beacon.level
            1 -> BeaconScreenHandler.getRawIdForStatusEffect(beacon.primaryEffect)
            2 -> BeaconScreenHandler.getRawIdForStatusEffect(beacon.secondaryEffect)
            else -> 0
        }
    }

    override fun set(index: Int, value: Int) {
        var updatedEffects = false
        when (index) {
            0 -> beacon.level = value
            1 -> {
                beacon.updateEffect(value, beacon.primaryEffect) { newEffect ->
                    updatedEffects = newEffect != null
                    beacon.primaryEffect = newEffect
                }
            }

            2 -> {
                beacon.updateEffect(value, beacon.secondaryEffect) { newEffect ->
                    updatedEffects = newEffect != null
                    beacon.secondaryEffect = newEffect
                }
            }
        }

        if (updatedEffects && !beacon.world.isClient && beacon.beamSegments.isNotEmpty())
            BeaconBlockEntity.playSound(beacon.world, beacon.pos, SoundEvents.BLOCK_BEACON_POWER_SELECT)
    }

    private fun OverhauledBeacon.updateEffect(effectId: Int, currentEffect: StatusEffect?, applyNewEffect: (StatusEffect?) -> Unit) {
        val effectFromId = BeaconScreenHandler.getStatusEffectForRawId(effectId)
        val newEffect = BeaconBlockEntity.getEffectOrNull(effectFromId)
        when {
            newEffect == null -> applyNewEffect(null)
            canApplyEffect(newEffect) && newEffect != currentEffect -> {
                applyNewEffect(newEffect)
            }
        }
    }

    override fun size() = 3
}
