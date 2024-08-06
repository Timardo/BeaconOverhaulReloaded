package gay.solonovamax.beaconsoverhaul.integration.lavender

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.wispforest.owo.ui.base.BaseComponent
import io.wispforest.owo.ui.core.Component
import io.wispforest.owo.ui.core.Easing
import io.wispforest.owo.ui.core.OwoUIDrawContext
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.parsing.UIModel
import io.wispforest.owo.ui.parsing.UIModelParsingException
import io.wispforest.owo.ui.parsing.UIParsing
import io.wispforest.owo.util.pond.OwoEntityRenderDispatcherExtension
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderDispatcher
import net.minecraft.client.render.entity.PlayerModelPart
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.StringNbtReader
import net.minecraft.network.ClientConnection
import net.minecraft.network.NetworkSide
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import org.w3c.dom.Element
import java.time.Duration
import kotlin.math.atan
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class EntityModelComponent<E : Entity> private constructor(
    sizing: Sizing,
    val entity: E,
) : BaseComponent() {
    var rotationX = 0.0f
    var rotationY = 0.0f
    var scale = 1.0f
    var lookAtCursor = false
    var rotate = false
    var allowMouseRotation = false
    var scaleToFit = false
        set(value) {
            scale = min(.5 / entity.width, .5 / entity.height).toFloat()

            field = value
        }
    var showNametag = false
    var transform: (MatrixStack) -> Unit = { }

    private var lastInteraction: Instant = Instant.DISTANT_PAST

    private val dispatcher: EntityRenderDispatcher = client.entityRenderDispatcher

    private val entityBuffers: VertexConsumerProvider.Immediate = client.bufferBuilders.entityVertexConsumers

    init {
        sizing(sizing)
    }

    private constructor(sizing: Sizing, type: EntityType<E>, nbt: NbtCompound?) : this(sizing, type.create(client.world)!!) {
        if (nbt != null)
            entity.readNbt(nbt)
        val player = client.player!!
        entity.updatePosition(player.x, player.y, player.z)
    }

    override fun update(delta: Float, mouseX: Int, mouseY: Int) {
        super.update(delta, mouseX, mouseY)
        val diff = Clock.System.now() - lastInteraction
        if (diff < 5.seconds)
            return

        if (rotate) {
            rotationX += delta * Easing.SINE.apply(min(1.0f, ((diff - 5.seconds).toLong(DurationUnit.SECONDS) / 1.5f)))
            rotationY = 0.0f
        }
    }

    override fun draw(context: OwoUIDrawContext, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        with(context) {
            with(Unit) {
                draw(mouseX, mouseY, partialTicks, delta)
            }
        }
    }

    context(OwoUIDrawContext, Unit)
    @Suppress("UnstableApiUsage")
    private fun draw(mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        matrices.push()

        matrices.translate(x + width / 2.0f, y + height / 2.0f, 100.0f)
        matrices.scale(75 * scale * width / 64.0f, -75 * scale * height / 64.0f, 75 * scale)

        matrices.translate(0.0f, entity.height / -2.0f, 0.0f)

        transform(matrices)

        if (lookAtCursor && Clock.System.now() - lastInteraction > 5.seconds) {
            var xRotation: Float = Math.toDegrees(atan((mouseY - y - (height / 2.0)) / 40.0)).toFloat()
            val yRotation: Float = Math.toDegrees(atan((mouseX - x - (width / 2.0)) / 40.0)).toFloat()

            if (entity is LivingEntity)
                entity.prevHeadYaw = -yRotation

            entity.prevYaw = -yRotation
            entity.prevPitch = xRotation * .65f

            // We make sure the xRotation never becomes 0, as the lighting otherwise becomes very unhappy
            if (xRotation == 0.0f)
                xRotation = .1f
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(xRotation * .15f))
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yRotation * .15f))
        } else {
            // why are these flipped??
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(35.0f + rotationY))
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45 + rotationX))
        }

        Blocks.WITHER_SKELETON_SKULL

        dispatcher as OwoEntityRenderDispatcherExtension
        dispatcher.`owo$setCounterRotate`(true)
        dispatcher.`owo$setShowNametag`(showNametag)

        RenderSystem.setShaderLights(Vector3f(.15f, 1.0f, 0.0f), Vector3f(.15f, -1.0f, 0.0f))
        dispatcher.setRenderShadows(false)
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 0.0f, matrices, entityBuffers, LightmapTextureManager.MAX_LIGHT_COORDINATE)
        dispatcher.setRenderShadows(true)
        entityBuffers.draw()
        DiffuseLighting.enableGuiDepthLighting()

        matrices.pop()

        dispatcher.`owo$setCounterRotate`(false)
        dispatcher.`owo$setShowNametag`(true)
    }

    override fun onMouseDrag(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double, button: Int): Boolean {
        val result = super.onMouseDrag(mouseX, mouseY, deltaX, deltaY, button)
        return when {
            button != GLFW.GLFW_MOUSE_BUTTON_LEFT -> result

            allowMouseRotation -> {
                rotationX += deltaX.toFloat()
                rotationY += deltaY.toFloat()

                lastInteraction = Clock.System.now()

                true
            }

            else -> result
        }
    }

    override fun canFocus(source: Component.FocusSource): Boolean {
        return source == Component.FocusSource.MOUSE_CLICK
    }

    override fun parseProperties(model: UIModel, element: Element, children: Map<String, Element>) {
        super.parseProperties(model, element, children)

        UIParsing.apply(children, "scale", UIParsing::parseFloat) { scale ->
            this.scale = scale
        }
        UIParsing.apply(children, "look-at-cursor", UIParsing::parseBool) { lookAtCursor ->
            this.lookAtCursor = lookAtCursor
        }
        UIParsing.apply(children, "mouse-rotation", UIParsing::parseBool) { allowMouseRotation ->
            this.allowMouseRotation = allowMouseRotation
        }
        UIParsing.apply(children, "scale-to-fit", UIParsing::parseBool) { scaleToFit ->
            this.scaleToFit = scaleToFit
        }
    }

    // no idea what this does but it looks like it is not used anywhere, I cannot update it so yeet it is
    /*class RenderablePlayerEntity(profile: GameProfile?) : ClientPlayerEntity(
        MinecraftClient.getInstance(),
        MinecraftClient.getInstance().world,
        ClientPlayNetworkHandler(
            MinecraftClient.getInstance(),
            null,
            ClientConnection(NetworkSide.CLIENTBOUND),
            null,
            profile,
            MinecraftClient.getInstance().telemetryManager.createWorldSession(false, Duration.ZERO, "tetris")
        ),
        null, null, false, false
    ) {
        private var skinTextureId: Identifier? = null
        private var model: String? = null

        init {
            client.skinProvider.loadSkin(
                gameProfile,
                { type: MinecraftProfileTexture.Type, identifier: Identifier, texture: MinecraftProfileTexture ->
                    if (type != MinecraftProfileTexture.Type.SKIN)
                        return@loadSkin
                    skinTextureId = identifier
                    model = texture.getMetadata("model") ?: "default"
                }, true
            )
        }

        override fun hasSkinTexture(): Boolean = skinTextureId != null

        override fun getSkinTexture(): Identifier = if (skinTextureId != null) skinTextureId!! else super.getSkinTexture()

        override fun isPartVisible(modelPart: PlayerModelPart): Boolean = true

        override fun getModel(): String = if (model != null) model!! else super.getModel()

        override fun getPlayerListEntry(): PlayerListEntry? = null
    }*/

    companion object {
        private val client: MinecraftClient
            get() = MinecraftClient.getInstance()

        //fun createRenderablePlayer(profile: GameProfile?): RenderablePlayerEntity = RenderablePlayerEntity(profile)

        fun parse(element: Element): EntityModelComponent<*> {
            UIParsing.expectAttributes(element, "type")
            val entityId: Identifier = UIParsing.parseIdentifier(element.getAttributeNode("type"))
            val entityType: EntityType<*> = Registries.ENTITY_TYPE.getOrEmpty(entityId).orElseThrow {
                UIModelParsingException("Unknown entity type $entityId")
            }

            val nbt = if (element.hasAttribute("nbt")) {
                try {
                    StringNbtReader.parse(element.getAttribute("nbt"))
                } catch (cse: CommandSyntaxException) {
                    throw UIModelParsingException("Invalid NBT compound", cse)
                }
            } else {
                null
            }

            return EntityModelComponent(Sizing.content(), entityType, nbt)
        }
    }
}
