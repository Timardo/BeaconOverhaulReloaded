package gay.solonovamax.beaconsoverhaul.block.beacon.blockentity

import ca.solostudios.guava.kotlin.collect.Multiset
import ca.solostudios.guava.kotlin.collect.MutableMultiset
import ca.solostudios.guava.kotlin.collect.mutableMultisetOf
import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.colormath.model.SRGB
import com.github.ajalt.colormath.transform.interpolate
import gay.solonovamax.beaconsoverhaul.BeaconOverhaulReloaded
//import gay.solonovamax.beaconsoverhaul.advancement.RedirectBeaconCriterion
import gay.solonovamax.beaconsoverhaul.block.beacon.OverhauledBeacon
import gay.solonovamax.beaconsoverhaul.block.beacon.data.OverhauledBeaconData
import gay.solonovamax.beaconsoverhaul.config.ConfigManager
import gay.solonovamax.beaconsoverhaul.register.TagRegistry
import gay.solonovamax.beaconsoverhaul.screen.OverhauledBeaconScreenHandler
import gay.solonovamax.beaconsoverhaul.util.contains
import gay.solonovamax.beaconsoverhaul.util.nonSpectatingEntities
import kotlinx.datetime.Clock
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import net.minecraft.advancement.criterion.Criteria
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.Stainable
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.tag.BlockTags
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import net.silkmc.silk.core.math.vector.minus

// class OverhauledBeaconBlockEntity(
//     pos: BlockPos,
//     state: BlockState,
// ) : AbstractBeaconBlockEntity(BlockEntityType.BEACON, pos, state) {
//     override val range: Int
//         get() = TODO("Not yet implemented")
//     override val duration: Int
//         get() = TODO("Not yet implemented")
//     override val primaryAmplifier: Int
//         get() = TODO("Not yet implemented")
//     override val primaryAmplifierPotent: Int
//         get() = TODO("Not yet implemented")
//     override val secondaryAmplifier: Int
//         get() = TODO("Not yet implemented")
//
//     override fun applyPrimaryStatusEffect(entities: List<Entity>) {
//         TODO("Not yet implemented")
//     }
//
//     override fun applySecondaryStatusEffect(entities: List<Entity>) {
//         TODO("Not yet implemented")
//     }
//
// }

fun OverhauledBeacon.createMenu(
    syncId: Int,
    player: PlayerEntity,
): OverhauledBeaconScreenHandler {
    val context = ScreenHandlerContext.create(this.world, this.pos)

    if (player is ServerPlayerEntity)
        addUpdateListener(player)

    val beaconScreenHandler = OverhauledBeaconScreenHandler(
        syncId,
        player,
        this.propertyDelegate,
        context,
    ) { player: PlayerEntity ->
        this.removeUpdateListener(player)
    }

    return beaconScreenHandler
}

fun OverhauledBeacon.updateTier(world: World, pos: BlockPos) {
    if (!shouldUpdateBeacon(world, pos))
        return

    val oldBaseBlocks = this.baseBlocks
    val oldBeaconPoints = this.beaconPoints

    val (level, baseBlocks) = buildBlockMultiset(pos.y, world, pos.x, pos.z)

    if (level > this.level) {
        // if (/*!broke &&*/beamSegmentsToCheck.isNotEmpty()) {
        for (player in world.nonSpectatingEntities<ServerPlayerEntity>(Box(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).expand(10.0)))
            Criteria.CONSTRUCT_BEACON.trigger(player, level)
        // }
    }

    this.baseBlocks = baseBlocks
    this.level = level
    this.beaconPoints = computePoints(baseBlocks)

    if (this.beaconPoints != oldBeaconPoints || this.baseBlocks != oldBaseBlocks) {
        for (player in this.listeningPlayers)
            BeaconOverhaulReloaded.updateBeaconPacket.send(OverhauledBeaconData.from(this), player)
    }
}

private fun buildBlockMultiset(
    y: Int,
    world: World,
    x: Int,
    z: Int,
): Pair<Int, MutableMultiset<Block>> {
    var level = 0

    val baseBlocks = mutableMultisetOf<Block>()

    for (layerOffset in 1..ConfigManager.beaconConfig.maxBeaconLayers) {
        val yOffset = y - layerOffset

        if (yOffset < world.bottomY)
            break

        val layerContents = mutableMultisetOf<Block>()
        for (xOffset in x - layerOffset..x + layerOffset) {
            for (zOffset in z - layerOffset..z + layerOffset) {
                val state = world.getBlockState(BlockPos(xOffset, yOffset, zOffset))

                if (state !in BlockTags.BEACON_BASE_BLOCKS)
                    return level to baseBlocks

                layerContents.add(state.block)
            }
        }

        baseBlocks.addAll(layerContents)
        level = layerOffset
    }

    return level to baseBlocks
}

private fun OverhauledBeacon.shouldUpdateBeacon(world: World, pos: BlockPos): Boolean {
    val now = Clock.System.now()

    val updateDelay = ConfigManager.beaconConfig.beaconUpdateDelay
    val initialUpdateDelay = ConfigManager.beaconConfig.initialBeaconUpdateDelay

    when {
        now - this.lastUpdate > updateDelay -> {
            this.lastUpdate = Clock.System.now()
            return true
        }

        level > 0 -> {
            return false
        }

        now - this.lastUpdate <= initialUpdateDelay -> {
            return false
        }

        else -> {
            this.lastUpdate = Clock.System.now()

            for (xOffset in -1..1)
                for (zOffset in -1..1)
                    if (world.getBlockState(pos.add(xOffset, -1, zOffset)) !in BlockTags.BEACON_BASE_BLOCKS)
                        return false

            return true
        }
    }
}


private fun computePoints(baseBlocks: Multiset<Block>): Double {
    var result = 0.0
    // addition modifiers (ie. most blocks)
    for ((block, expression) in ConfigManager.beaconConfig.additionModifierBlocks) {
        if (block in baseBlocks) {
            val expressionResult = expression.evaluate(baseBlocks[block].toDouble())
            result += expressionResult
        }
    }
    // multiplication modifiers (ie. netherite)
    for ((block, expression) in ConfigManager.beaconConfig.multiplicationModifierBlocks) {
        if (block in baseBlocks) {
            val expressionResult = expression.evaluate(baseBlocks[block].toDouble())
            result *= expressionResult
        }
    }

    return result
}

fun OverhauledBeacon.writeScreenOpeningData(player: ServerPlayerEntity, buf: PacketByteBuf) {
    val data = OverhauledBeaconData.from(this)
    val bytes = Cbor.encodeToByteArray(data)
    buf.writeByteArray(bytes)
}

fun OverhauledBeacon.testCanApplyEffect(effect: StatusEffect): Boolean {
    return when {
        level == 0 -> false
        effect in ConfigManager.beaconConfig.beaconEffectsByTier.tierOne -> level >= 1
        effect in ConfigManager.beaconConfig.beaconEffectsByTier.tierTwo -> level >= 2
        effect in ConfigManager.beaconConfig.beaconEffectsByTier.tierThree -> level >= 3
        effect in ConfigManager.beaconConfig.beaconEffectsByTier.secondaryEffects -> level >= 4
        else -> false
    }
}

fun OverhauledBeacon.constructBeamSegments() {
    if (world.time % ConfigManager.beaconConfig.beamUpdateFrequency != 0L)
        return

    if (level == 0)
        return

    var currentPos = pos

    var remainingHorizontalMoves = ConfigManager.beaconConfig.redirectionHorizontalMoveLimit
    val targetHeight = world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.x, pos.z)

    var broke = false
    var didRedirection = false

    val beamSegmentsToCheck = mutableListOf<BeaconBeamSegment>()

    var currentColor = SRGB(1.0f, 1.0f, 1.0f)

    var lastDirection: Direction? = null
    var currentSegment = BeaconBeamSegment(Direction.UP, Vec3i.ZERO, currentColor, currentColor).also { beamSegmentsToCheck.add(it) }

    val seenPositions = hashSetOf<BlockPos>()
    var check: Boolean
    var firstColorChange = false


    while (world.isInBuildLimit(currentPos) && remainingHorizontalMoves > 0) {
        if (currentSegment.direction === Direction.UP && currentSegment.direction !== lastDirection) {
            val heightmapVal = world.getTopY(Heightmap.Type.WORLD_SURFACE, currentPos.x, currentPos.z)
            if (heightmapVal == (currentPos.y + 1)) {
                currentSegment.height = heightmapVal + 1000
                break
            }

            lastDirection = currentSegment.direction
        }

        currentPos = currentPos.offset(currentSegment.direction)
        remainingHorizontalMoves = if (currentSegment.direction.axis.isHorizontal)
            remainingHorizontalMoves - 1
        else
            ConfigManager.beaconConfig.redirectionHorizontalMoveLimit

        val state = world.getBlockState(currentPos)
        val block = state.block

        val nextColor = block.beaconTint ?: currentColor

        when {
            ConfigManager.beaconConfig.allowTintedGlassTransparency && block === Blocks.TINTED_GLASS -> {
                check = true

                val mixedColor = currentColor.interpolate(nextColor.copy(alpha = 0.0f), 0.25, premultiplyAlpha = false)

                currentSegment = BeaconBeamSegment(
                    direction = currentSegment.direction,
                    offset = currentPos - (pos),
                    color = mixedColor,
                    previousColor = currentColor,
                ).also { beamSegmentsToCheck.add(it) }
                lastDirection = currentSegment.direction
                currentColor = mixedColor
            }

            isRedirectingBlock(block) && state[Properties.FACING] != currentSegment.direction -> {
                check = true

                didRedirection = true
                lastDirection = currentSegment.direction
                currentSegment.isTurn = true
                currentSegment = BeaconBeamSegment(
                    direction = state[Properties.FACING],
                    offset = currentPos.subtract(pos),
                    color = currentColor,
                    previousColor = currentColor,
                    previousSegmentIsTurn = true,
                ).also { beamSegmentsToCheck.add(it) }
            }

            nextColor != currentColor -> {
                check = true

                val mixedColor = if (!firstColorChange) {
                    firstColorChange = true
                    nextColor
                } else {
                    currentColor.interpolate(nextColor, 0.5, premultiplyAlpha = false)
                }

                currentSegment = BeaconBeamSegment(
                    currentSegment.direction,
                    currentPos - (pos),
                    mixedColor,
                    currentColor,
                ).also { beamSegmentsToCheck.add(it) }
                lastDirection = currentSegment.direction
                currentColor = mixedColor
            }

            else -> {
                check = false
                // skip transparent blocks & blocks in the beacon transparent tag (bedrock)
                if (state !in TagRegistry.BEACON_TRANSPARENT && state.getOpacity(world, currentPos) >= 15) {
                    if (currentSegment.direction == Direction.UP)
                        broke = true
                    break
                }

                currentSegment.increaseHeight()
            }
        }

        /*if (BeaconOverhaulConfigManager.config.allowTintedGlassTransparency) {
            if (block === Blocks.TINTED_GLASS) {
                targetAlpha = if (alpha < 0.3f) 0f else (alpha * 0.75f)
            }
        }

        if (isRedirectingBlock(block)) {
            val dir = state[Properties.FACING]
            if (dir == currentSegment.direction) {
                currentSegment.increaseHeight()
            } else {
                check = true
                beamSegmentsToCheck.add(currentSegment)

                targetColor = floatArrayOf(1.0f, 1.0f, 1.0f)
                if (targetColor[0] == 1.0f && targetColor[1] == 1.0f && targetColor[2] == 1.0f)
                    targetColor = currColor

                val mixedColor = floatArrayOf(
                    (currColor[0] + targetColor[0] * 3) / 4.0f,
                    (currColor[1] + targetColor[1] * 3) / 4.0f,
                    (currColor[2] + targetColor[2] * 3) / 4.0f
                )
                currColor = mixedColor

                didRedirection = true
                lastDirection = currentSegment.direction
                currentSegment.isTurn = true
                currentSegment = BeaconBeamSegment(
                    dir,
                    currentPos.subtract(pos),
                    currColor,
                    alpha,
                    currColor,
                    alpha,
                    previousSegmentIsTurn = true,
                )
            }
        } else if (targetColor != null || targetAlpha != -1.0f) {
            if (targetColor.contentEquals(currColor) && targetAlpha == alpha) {
                currentSegment.increaseHeight()
            } else {
                check = true
                beamSegmentsToCheck.add(currentSegment)

                val previousColor = currColor
                val previousAlpha = alpha

                var mixedColor = currColor
                if (targetColor != null) {
                    mixedColor = mixColors(currColor, targetColor)

                    if (!firstColorChange) {
                        mixedColor = targetColor
                        firstColorChange = true
                    }

                    currColor = mixedColor
                }

                if (targetAlpha != -1.0f)
                    alpha = targetAlpha

                lastDirection = currentSegment.direction
                currentSegment = BeaconBeamSegment(
                    currentSegment.direction,
                    currentPos.subtract(pos),
                    mixedColor,
                    alpha,
                    previousColor,
                    previousAlpha
                )
            }
        } else {
            // skip transparent blocks & blocks in the beacon transparent tag (bedrock)
            if (state !in TagRegistry.BEACON_TRANSPARENT && state.getOpacity(world, currentPos) >= 15) {
                if (currentSegment.direction == Direction.UP)
                    broke = true
                break
            }

            currentSegment.increaseHeight()
        }*/

        if (check) {
            val added = seenPositions.add(currentPos)
            if (!added) {
                broke = true
                break
            }
        }
    }

    if (remainingHorizontalMoves == 0 || currentPos.y <= world.bottomY)
        broke = true

    minY = if (!broke) {
        targetHeight + 1
    } else {
        if (remainingHorizontalMoves == 0)
            currentSegment.height = 1000

        targetHeight
    }

    if (!this.didRedirection && didRedirection) {
        if (!broke && beamSegmentsToCheck.isNotEmpty()) {
            /*for (player in world.nonSpectatingEntities<ServerPlayerEntity>(Box(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).expand(10.0)))
                RedirectBeaconCriterion.trigger(player)*/
        }
    }

    this.brokenBeam = broke
    this.beamSegmentsToCheck = beamSegmentsToCheck
    this.didRedirection = didRedirection
}

private fun isRedirectingBlock(block: Block): Boolean {
    return block === Blocks.AMETHYST_CLUSTER
}

val Block.beaconTint: RGB?
    get() {
        if (this is Stainable)
            return SRGB.create(color.colorComponents)

        return null
    }
