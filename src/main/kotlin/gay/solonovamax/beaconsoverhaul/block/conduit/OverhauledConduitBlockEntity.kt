package gay.solonovamax.beaconsoverhaul.block.conduit

import gay.solonovamax.beaconsoverhaul.config.ConfigManager
import gay.solonovamax.beaconsoverhaul.integration.lavender.LavenderStructureTemplate
import gay.solonovamax.beaconsoverhaul.util.entitiesByClass
import gay.solonovamax.beaconsoverhaul.util.identifierOf
import gay.solonovamax.beaconsoverhaul.util.kotlinRandom
import gay.solonovamax.beaconsoverhaul.util.nonSpectatingEntities
import io.wispforest.lavender.structure.BlockStatePredicate
import io.wispforest.lavender.structure.LavenderStructures
import net.minecraft.SharedConstants
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.Monster
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import net.silkmc.silk.core.kotlin.asKotlinRandom
import net.silkmc.silk.core.math.vector.minus
import net.silkmc.silk.core.math.vector.plus
import net.silkmc.silk.nbt.set
import org.slf4j.kotlin.getLogger
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.EasingType
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

class OverhauledConduitBlockEntity(pos: BlockPos?, state: BlockState?) : BlockEntity(BlockEntityType.CONDUIT, pos, state), GeoBlockEntity {
    private val logger by getLogger()

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    var ticks: Int = 0
        private set
    var ticksActive: Int = 0
        private set

    val isActive: Boolean
        get() = tier > 0
    val isEyeOpen: Boolean
        get() = tier > 1
    val isWindActive: Boolean
        get() = tier > 2

    val range: Double
        get() = tier * 8.0

    var targetEntity: LivingEntity? = null
        private set

    private var nextAmbientSoundTime: Long = 0

    private val attackZone: Box
        // why would they remove the overload, why
        get() = Box(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), (pos + 1).x.toDouble(), (pos + 1).y.toDouble(), (pos + 1).z.toDouble()).expand(KILL_RANGE)

    private val effectZone: Box
        get() = Box(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), (pos + 1).x.toDouble(), (pos + 1).y.toDouble(), (pos + 1).z.toDouble()).expand(range).stretch(0.0, world?.height?.toDouble() ?: 0.0, 0.0)

    var tier: Int = 0
        private set

    fun updateTier() {
        var validatedTier = 0
        for (testTier in 0..MAX_TIER) {
            val structure = structureForTier(testTier)
            if (structure != null) {
                if (structure.validate(world, pos - structure.anchor))
                    validatedTier = testTier
                else
                    break
            }
        }
        tier = validatedTier
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        tier = nbt.getInt("Tier")
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        nbt["Tier"] = tier
    }

    override fun toUpdatePacket(): BlockEntityUpdateS2CPacket = BlockEntityUpdateS2CPacket.create(this)
    override fun toInitialChunkDataNbt(): NbtCompound = createNbt()

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, SharedConstants.TICKS_PER_SECOND / 2) { event ->
            if (isActive)
                event.setAndContinue(RawAnimation.begin().thenLoop("animation.powered"))
            else
                event.setAndContinue(RawAnimation.begin().thenLoop("animation.idle"))
        }.setOverrideEasingType(EasingType.EASE_OUT_SINE))
    }

    override fun getAnimatableInstanceCache() = cache

    companion object {
        private const val EFFECT_DURATION = 13 * SharedConstants.TICKS_PER_SECOND
        private const val KILL_RANGE = 8.0
        private const val STRUCTURE_ID_FORMAT = "conduit/tier_%s"
        private const val MAX_TIER = 4
        private val particleSources = listOf(Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN).map {
            it.defaultState
        }.toTypedArray()

        @JvmStatic
        fun structureIdForTier(tier: Int) = identifierOf(STRUCTURE_ID_FORMAT.format(tier))

        @JvmStatic
        fun structureForTier(tier: Int) = LavenderStructures.get(structureIdForTier(tier))

        @JvmStatic
        fun clientTick(world: World, pos: BlockPos, state: BlockState, conduit: OverhauledConduitBlockEntity) {
            conduit.ticks++
            if (world.time % 40L == 0L) {
                conduit.updateTier()
            }

            spawnNautilusParticles(world, pos, conduit)

            if (conduit.isActive)
                conduit.ticksActive++
        }

        @JvmStatic
        fun serverTick(world: World, pos: BlockPos, state: BlockState, conduit: OverhauledConduitBlockEntity) {
            conduit.ticks++
            val worldTime = world.time
            if (worldTime % 40L == 0L) {
                val tier = conduit.tier
                conduit.updateTier()
                if (conduit.tier != tier) {
                    val soundEvent = if (conduit.isActive) SoundEvents.BLOCK_CONDUIT_ACTIVATE else SoundEvents.BLOCK_CONDUIT_DEACTIVATE
                    val pitch = if (conduit.isActive) 1.0f + (conduit.tier - 1) * 0.125f else 1.0f
                    world.playSound(null, pos, soundEvent, SoundCategory.BLOCKS, 1.0f, pitch)
                }

                if (conduit.isActive) {
                    givePlayersEffects(world, pos, conduit)
                    attackHostileEntity(world, pos, state, conduit)
                }
            }

            if (conduit.isActive) {
                if (worldTime % 80L == 0L) {
                    world.playSound(null, pos, SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.BLOCKS, 1.0f, 1.0f)
                }

                if (worldTime > conduit.nextAmbientSoundTime) {
                    conduit.nextAmbientSoundTime = worldTime + 60L + world.getRandom().nextInt(40).toLong()
                    world.playSound(null, pos, SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, SoundCategory.BLOCKS, 1.0f, 1.0f)
                }
            }
        }

        private fun givePlayersEffects(world: World, pos: BlockPos, conduit: OverhauledConduitBlockEntity) {
            for (player in world.nonSpectatingEntities<PlayerEntity>(conduit.effectZone)) {
                if (pos.isWithinDistance(player.blockPos, conduit.range) && player.isWet) {
                    player.addStatusEffect(
                        StatusEffectInstance(
                            StatusEffects.CONDUIT_POWER,
                            EFFECT_DURATION,
                            0,
                            true,
                            ConfigManager.conduitConfig.effectParticles
                        )
                    )
                }
            }
        }

        private fun attackHostileEntity(
            world: World,
            pos: BlockPos,
            state: BlockState,
            conduit: OverhauledConduitBlockEntity,
        ) {
            val originalTarget = conduit.targetEntity

            conduit.targetEntity = when {
                conduit.tier < 2 -> null

                conduit.targetEntity == null -> {
                    world.entitiesByClass<LivingEntity>(conduit.attackZone) { entity ->
                        entity is Monster && entity.isTouchingWaterOrRain
                    }.randomOrNull(world.random.asKotlinRandom())
                }

                !conduit.targetEntity!!.isAlive || !pos.isWithinDistance(conduit.targetEntity!!.blockPos, KILL_RANGE) -> null

                else -> conduit.targetEntity
            }

            val target = conduit.targetEntity

            if (target != null) {
                world.playSound(
                    null,
                    target.x,
                    target.y,
                    target.z,
                    SoundEvents.BLOCK_CONDUIT_ATTACK_TARGET,
                    SoundCategory.BLOCKS,
                    1.0f,
                    1.0f
                )

                target.damage(world.damageSources.magic(), 4.0f)
            }

            if (originalTarget !== conduit.targetEntity) {
                world.updateListeners(pos, state, state, 2)
            }
        }

        private fun spawnNautilusParticles(world: World, pos: BlockPos, conduit: OverhauledConduitBlockEntity) {
            if (!conduit.isActive)
                return

            val structure = structureForTier(conduit.tier) as LavenderStructureTemplate
            val anchor = structure.anchor
            for ((predicate, blockPos) in structure) {
                if (!predicate.isOf(BlockStatePredicate.MatchCategory.NON_AIR))
                    continue

                if (world.kotlinRandom.nextInt(50 - 5 * conduit.tier) == 0 && particleSources.any { predicate.matches(it) }) {
                    val particlePos = blockPos - anchor
                    val vx = (world.kotlinRandom.nextDouble(-0.5, 0.5) + particlePos.x)
                    val vy = (world.kotlinRandom.nextDouble(-0.5, 0.5) + particlePos.y)
                    val vz = (world.kotlinRandom.nextDouble(-0.5, 0.5) + particlePos.z)
                    world.addParticle(ParticleTypes.NAUTILUS, pos.x + 0.5, pos.y + 0.5 + 1.0, pos.z + 0.5, vx, vy - 1.0, vz)
                }
            }

            val entity = conduit.targetEntity
            if (entity != null) {
                world.random.asKotlinRandom()
                val vx = (-0.5 + world.kotlinRandom.nextDouble()) * (3.0 + entity.width)
                val vy = -1.0 + world.kotlinRandom.nextDouble() * entity.height
                val vz = (-0.5 + world.kotlinRandom.nextDouble()) * (3.0 + entity.width)
                world.addParticle(ParticleTypes.NAUTILUS, entity.x, entity.eyeY, entity.z, vx, vy, vz)
            }
        }
    }
}
