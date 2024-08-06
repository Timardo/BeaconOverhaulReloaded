package gay.solonovamax.beaconsoverhaul.screen

import com.github.ajalt.colormath.model.SRGB
import gay.solonovamax.beaconsoverhaul.block.beacon.data.OverhauledBeaconData
import gay.solonovamax.beaconsoverhaul.config.ConfigManager
import gay.solonovamax.beaconsoverhaul.util.asRomanNumeral
import gay.solonovamax.beaconsoverhaul.util.drawCenteredTextWithShadow
import gay.solonovamax.beaconsoverhaul.util.drawItem
import gay.solonovamax.beaconsoverhaul.util.drawTextWithShadow
import gay.solonovamax.beaconsoverhaul.util.identifierOf
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.entity.BeaconBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.PressableWidget
import net.minecraft.client.texture.Sprite
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.UpdateBeaconC2SPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerListener
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import org.slf4j.kotlin.getLogger
import java.util.Optional
import kotlin.math.roundToInt

@Environment(EnvType.CLIENT)
class OverhauledBeaconScreen(
    handler: OverhauledBeaconScreenHandler,
    inventory: PlayerInventory,
    title: Text,
) : OverhauledScreen<OverhauledBeaconData, OverhauledBeaconScreenHandler>(handler, inventory, title) {
    private val logger by getLogger()

    private var buttons = listOf<BeaconButtonWidget>()
    var primaryEffect: StatusEffect? = null
    var secondaryEffect: StatusEffect? = null

    init {
        backgroundWidth = BEACON_UI_WIDTH
        backgroundHeight = BEACON_UI_HEIGHT

        handler.addListener(object : ScreenHandlerListener {
            override fun onSlotUpdate(handler: ScreenHandler, slotId: Int, stack: ItemStack) {}

            override fun onPropertyUpdate(screenHandler: ScreenHandler, property: Int, value: Int) {
                primaryEffect = handler.primaryEffect
                secondaryEffect = handler.secondaryEffect
            }
        })
    }

    override fun init() {
        super.init()

        buttons = buildList {
            add(DoneButtonWidget(this@OverhauledBeaconScreen, x + DONE_BUTTON_X + BEACON_BASE_GUI_X_OFFSET, y + DONE_BUTTON_Y))
            add(CancelButtonWidget(this@OverhauledBeaconScreen, x + CANCEL_BUTTON_X + BEACON_BASE_GUI_X_OFFSET, y + CANCEL_BUTTON_Y))

            for (effectTier in 0..2) {
                val tierEffectsCount = BeaconBlockEntity.EFFECTS_BY_LEVEL[effectTier].size
                val xCenterOffset = tierEffectsCount * EFFECT_BUTTON_SIZE + (tierEffectsCount - 1) * PADDING

                for (effectIndex in 0 until tierEffectsCount) {
                    val effectButton = EffectButtonWidget(
                        this@OverhauledBeaconScreen,
                        x + PRIMARY_BUTTON_X + (effectIndex * EFFECT_BUTTON_OFFSET) - xCenterOffset / 2 + BEACON_BASE_GUI_X_OFFSET,
                        y + PRIMARY_BUTTON_Y + (effectTier * EFFECT_BUTTON_OFFSET + 1),
                        BeaconBlockEntity.EFFECTS_BY_LEVEL[effectTier][effectIndex],
                        true,
                        effectTier
                    )
                    effectButton.active = false
                    add(effectButton)
                }
            }

            val secondaryEffectsSize = BeaconBlockEntity.EFFECTS_BY_LEVEL[3].size
            val effectColumns = ((secondaryEffectsSize + 1) / 2.0).roundToInt()
            val bottomColumns = secondaryEffectsSize - effectColumns
            val xTopOffset = ((effectColumns * EFFECT_BUTTON_SIZE) + (effectColumns - 1) * PADDING) / 2
            val xBottomOffset = ((bottomColumns + 1) * EFFECT_BUTTON_SIZE + bottomColumns * PADDING) / 2

            for (effectIndex in 0 until secondaryEffectsSize) {
                val buttonX = x + SECONDARY_BUTTON_X + ((effectIndex % effectColumns) * EFFECT_BUTTON_OFFSET) + BEACON_BASE_GUI_X_OFFSET
                val buttonY = y + SECONDARY_BUTTON_Y + ((effectIndex / effectColumns) * EFFECT_BUTTON_OFFSET)
                val effectButtonWidget = EffectButtonWidget(
                    this@OverhauledBeaconScreen,
                    buttonX - if (effectIndex < effectColumns) xTopOffset else xBottomOffset,
                    buttonY,
                    BeaconBlockEntity.EFFECTS_BY_LEVEL[3][effectIndex],
                    false,
                    3
                )
                effectButtonWidget.active = false
                add(effectButtonWidget)
            }
            val primaryEffectLevelTwoButton = LevelTwoEffectButtonWidget(
                this@OverhauledBeaconScreen,
                x + SECONDARY_BUTTON_X + ((secondaryEffectsSize % effectColumns) * EFFECT_BUTTON_OFFSET) - xBottomOffset + BEACON_BASE_GUI_X_OFFSET,
                y + SECONDARY_BUTTON_Y + ((secondaryEffectsSize / effectColumns) * EFFECT_BUTTON_OFFSET),
                BeaconBlockEntity.EFFECTS_BY_LEVEL[0][0]
            )
            primaryEffectLevelTwoButton.visible = true
            primaryEffectLevelTwoButton.active = false
            add(primaryEffectLevelTwoButton)
        }

        buttons.forEach {
            addDrawableChild(it)
        }
    }

    public override fun handledScreenTick() {
        super.handledScreenTick()
        tickButtons()
    }

    fun tickButtons() {
        buttons.forEach { button ->
            button.tick(handler.level)
        }
    }

    context(DrawContext, Unit)
    override fun drawForeground(mouseX: Int, mouseY: Int) {

        // why this formula? It aligns almost (R^2=0.9998) perfectly with mc's GUI
        // for ((index, item) in BEACON_PAYMENT_ITEMS.withIndex())
        //     drawItem(item, initialX + (22.0375 * index + 19.4).roundToInt(), beaconPaymentY)
        for ((index, item) in BEACON_PAYMENT_ITEMS.withIndex()) {
            drawItem(
                item,
                PAYMENT_ITEM_ICON_X + index * (ITEM_ICON_SIZE + PAYMENT_ITEM_ICON_SPACING) + BEACON_BASE_GUI_X_OFFSET,
                PAYMENT_ITEM_ICON_Y
            )
        }

        drawBeaconInformation(BEACON_BASE_GUI_X_OFFSET, 0)

        drawCenteredTextWithShadow(
            textRenderer,
            PRIMARY_POWER_TEXT,
            PRIMARY_TEXT_X + BEACON_BASE_GUI_X_OFFSET,
            LABEL_TEXT_Y,
            POWER_TEXT_COLOR
        )
        drawCenteredTextWithShadow(
            textRenderer,
            SECONDARY_POWER_TEXT,
            SECONDARY_TEXT_X + BEACON_BASE_GUI_X_OFFSET,
            LABEL_TEXT_Y,
            POWER_TEXT_COLOR
        )
    }


    context(DrawContext, Unit)
    override fun drawBackground(delta: Float, mouseX: Int, mouseY: Int) {
        // val initialX = (width - backgroundWidth) / 2
        // val initialY = (height - backgroundHeight) / 2

        drawTexture(BEACON_CONTAINER_TEXTURE, x, y, 0, 0, BEACON_UI_WIDTH, BEACON_UI_HEIGHT)

        val sidebarRightX = x + BEACON_UI_WIDTH + PADDING + BEACON_BASE_GUI_X_OFFSET
        drawTexture(BEACON_SIDEBAR_TEXTURE, sidebarRightX, y, 0, 0, BEACON_BLOCKS_SIDEBAR_WIDTH, BEACON_BLOCKS_SIDEBAR_HEIGHT)

        val sidebarLeftX = x + BEACON_UI_WIDTH + PADDING
        drawTexture(
            BEACON_SIDEBAR_TEXTURE,
            sidebarLeftX,
            y + BEACON_BLOCKS_SIDEBAR_HEIGHT + PADDING,
            BEACON_BLOCKS_SIDEBAR_WIDTH,
            0,
            BEACON_STATS_SIDEBAR_WIDTH,
            BEACON_STATS_SIDEBAR_HEIGHT
        )

        // matrices.push()
        // matrices.translate(0.0f, 0.0f, 100.0f)
        //
        // matrices.pop()
    }

    context(DrawContext)
    private fun drawBeaconInformation(initialX: Int, initialY: Int) {
        val fontOffset = (ITEM_ICON_SIZE - textRenderer.fontHeight) / 2

        // val sidebarLeftX = initialX - BEACON_STATS_SIDEBAR_WIDTH + 3
        val sidebarLeftX = initialX + BEACON_UI_WIDTH + PADDING * 2 + 3
        val sidebarLeftY = initialY + BEACON_STATS_SIDEBAR_Y + PADDING + 3

        val statsList = listOf(
            DURATION_LABEL_TEXT to data.durationText,
            RANGE_LABEL_TEXT to data.rangeText,
            POINTS_LABEL_TEXT to data.pointsText
        )

        val labelOffset = textRenderer.fontHeight + PADDING / 2
        val textInfoOffset = textRenderer.fontHeight + PADDING * 2
        val textRowOffset = labelOffset + textInfoOffset
        statsList.forEachIndexed { row, (label, stat) ->
            drawTextWithShadow(
                textRenderer,
                label,
                sidebarLeftX,
                sidebarLeftY + textRowOffset * row,
                WHITE
            )
            drawTextWithShadow(
                textRenderer,
                stat,
                sidebarLeftX,
                sidebarLeftY + labelOffset + textRowOffset * row,
                WHITE
            )
        }

        // var textRow = 0
        // val labelOffset = textRenderer.fontHeight + PADDING / 2
        // val textInfoOffset = textRenderer.fontHeight + PADDING * 2
        // val textRowOffset = labelOffset + textInfoOffset
        // drawTextWithShadow(
        //     textRenderer,
        //     DURATION_LABEL_TEXT,
        //     sidebarLeftX,
        //     sidebarLeftY,
        //     WHITE
        // )
        // drawTextWithShadow(
        //     textRenderer,
        //     data.durationText,
        //     sidebarLeftX,
        //     sidebarLeftY + labelOffset + textRowOffset * textRow++,
        //     WHITE
        // )
        // drawTextWithShadow(
        //     textRenderer,
        //     RANGE_LABEL_TEXT,
        //     sidebarLeftX,
        //     sidebarLeftY + textRowOffset * textRow,
        //     WHITE
        // )
        // drawTextWithShadow(
        //     textRenderer,
        //     data.rangeText,
        //     sidebarLeftX,
        //     sidebarLeftY + labelOffset + textRowOffset * textRow++,
        //     WHITE
        // )
        // drawTextWithShadow(
        //     textRenderer,
        //     POINTS_LABEL_TEXT,
        //     sidebarLeftX,
        //     sidebarLeftY + textRowOffset * textRow,
        //     WHITE
        // )
        // drawTextWithShadow(
        //     textRenderer,
        //     data.pointsText,
        //     sidebarLeftX,
        //     sidebarLeftY + labelOffset + textRowOffset * textRow++,
        //     WHITE
        // )

        data.blocksInBase.forEachIndexed { index, (block, count) ->
            var x = initialX + BEACON_UI_WIDTH + PADDING + 4
            val y = 4 + (ITEM_ICON_OFFSET) * index + initialY

            drawItem(block.asItem().defaultStack, x, y)
            x += ITEM_ICON_OFFSET

            drawTextWithShadow(textRenderer, count, x, y + fontOffset, WHITE)
        }
    }

    fun x() = x
    fun y() = y
    fun backgroundWidth() = backgroundWidth
    fun backgroundHeight() = backgroundHeight

    @Environment(EnvType.CLIENT)
    interface BeaconButtonWidget : Element, Drawable, Selectable {
        fun tick(level: Int)
    }

    @Environment(EnvType.CLIENT)
    abstract class BaseButtonWidget(
        val screen: OverhauledBeaconScreen,
        x: Int,
        y: Int,
        message: Text = ScreenTexts.EMPTY,
    ) : PressableWidget(x, y, 22, 22, message), BeaconButtonWidget {
        var isDisabled: Boolean = false

        public override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            with(context) {
                renderButton()
            }
        }

        context(DrawContext)
        private fun renderButton() {
            val j = when {
                !active -> width * 2
                isDisabled -> width * 1
                isSelected -> width * 3
                else -> 0
            }

            drawTexture(BEACON_CONTAINER_TEXTURE, x, y, j, 219, width, height)
            renderExtra()
        }

        context(DrawContext)
        protected abstract fun renderExtra()

        public override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            appendDefaultNarrations(builder)
        }
    }

    @Environment(EnvType.CLIENT)
    internal class CancelButtonWidget(
        screen: OverhauledBeaconScreen,
        x: Int,
        y: Int,
    ) : IconButtonWidget(screen, x, y, 112, 220, ScreenTexts.CANCEL) {
        override fun onPress() {
            screen.client?.player?.closeHandledScreen()
        }

        override fun tick(level: Int) {}
    }

    @Environment(EnvType.CLIENT)
    internal class DoneButtonWidget(
        screen: OverhauledBeaconScreen,
        x: Int,
        y: Int,
    ) : IconButtonWidget(screen, x, y, 90, 220, ScreenTexts.DONE) {
        override fun onPress() {
            screen.client?.networkHandler?.sendPacket(
                UpdateBeaconC2SPacket(
                    Optional.ofNullable(screen.primaryEffect), Optional.ofNullable(screen.secondaryEffect)
                )
            )
            screen.client?.player?.closeHandledScreen()
        }

        override fun tick(level: Int) {
            active = screen.handler.hasPayment && screen.primaryEffect != null
        }
    }

    @Environment(EnvType.CLIENT)
    internal open class EffectButtonWidget(
        screen: OverhauledBeaconScreen,
        x: Int,
        y: Int,
        private var effect: StatusEffect,
        private val primary: Boolean,
        private val level: Int,
    ) : BaseButtonWidget(screen, x, y) {
        private var sprite: Sprite = MinecraftClient.getInstance().statusEffectSpriteManager.getSprite(effect)

        init {
            init(effect)
        }

        protected fun init(statusEffect: StatusEffect) {
            effect = statusEffect
            sprite = MinecraftClient.getInstance().statusEffectSpriteManager.getSprite(statusEffect)
            tooltip = Tooltip.of(getEffectName(statusEffect), null)
        }

        protected open fun getEffectName(statusEffect: StatusEffect): MutableText {
            val text = Text.translatable(statusEffect.translationKey)
            val amplifier = if (primary) screen.data.primaryAmplifier else screen.data.secondaryAmplifier
            if (statusEffect !in ConfigManager.beaconConfig.levelOneStatusEffects && amplifier > 1) {
                text.append(" ")
                    .append(amplifier.asRomanNumeral())
            }
            return text
        }

        override fun onPress() {
            if (!isDisabled) {
                if (primary)
                    screen.primaryEffect = effect
                else
                    screen.secondaryEffect = effect

                screen.tickButtons()
            }
        }

        context(DrawContext)
        override fun renderExtra() {
            drawSprite(x + 2, y + 2, 0, 18, 18, sprite)
        }

        override fun tick(level: Int) {
            active = this.level < level
            isDisabled = effect === (if (primary) screen.primaryEffect else screen.secondaryEffect)
        }

        override fun getNarrationMessage(): MutableText {
            return getEffectName(effect)
        }
    }

    @Environment(EnvType.CLIENT)
    internal abstract class IconButtonWidget protected constructor(
        screen: OverhauledBeaconScreen,
        i: Int,
        j: Int,
        private val u: Int,
        private val v: Int,
        text: Text,
    ) : BaseButtonWidget(screen, i, j, text) {
        context(DrawContext)
        override fun renderExtra() {
            drawTexture(BEACON_CONTAINER_TEXTURE, x + 2, y + 2, u, v, 18, 18)
        }
    }

    @Environment(EnvType.CLIENT)
    internal class LevelTwoEffectButtonWidget(
        screen: OverhauledBeaconScreen,
        x: Int,
        y: Int,
        statusEffect: StatusEffect,
    ) : EffectButtonWidget(screen, x, y, statusEffect, false, 3) {
        override fun getEffectName(statusEffect: StatusEffect): MutableText {
            val text = Text.translatable(statusEffect.translationKey)
            val amplifier = screen.data.primaryAmplifierPotent

            if (statusEffect !in ConfigManager.beaconConfig.levelOneStatusEffects && amplifier > 1)
                text.append(" ")
                    .append(amplifier.asRomanNumeral())

            return text
        }

        override fun tick(level: Int) {
            val primaryEffect = screen.primaryEffect
            if (primaryEffect != null) {
                visible = true
                init(primaryEffect)
                super.tick(level)
            } else {
                visible = true
            }
        }
    }

    companion object {
        private val BEACON_PAYMENT_ITEMS = listOf(
            Items.NETHERITE_INGOT,
            Items.EMERALD,
            Items.DIAMOND,
            Items.GOLD_INGOT,
            Items.IRON_INGOT
        )

        private val BEACON_CONTAINER_TEXTURE = identifierOf("textures/gui/container/overhauled_beacon.png")
        private val BEACON_SIDEBAR_TEXTURE = identifierOf("textures/gui/container/overhauled_beacon_sidebar.png")

        private val PRIMARY_POWER_TEXT = Text.translatable("block.minecraft.beacon.primary")
        private val SECONDARY_POWER_TEXT = Text.translatable("block.minecraft.beacon.secondary")
        private val POINTS_LABEL_TEXT = Text.translatable("block.beaconoverhaul.beacon.points")
        private val RANGE_LABEL_TEXT = Text.translatable("block.beaconoverhaul.beacon.range")
        private val DURATION_LABEL_TEXT = Text.translatable("block.beaconoverhaul.beacon.duration")

        const val PADDING = 2

        const val BEACON_UI_WIDTH = 230
        const val BEACON_UI_HEIGHT = 219
        const val BEACON_BLOCKS_SIDEBAR_WIDTH = 64
        const val BEACON_BLOCKS_SIDEBAR_HEIGHT = 96
        const val BEACON_STATS_SIDEBAR_WIDTH = 54
        const val BEACON_STATS_SIDEBAR_HEIGHT = 74
        const val BEACON_STATS_SIDEBAR_Y = BEACON_BLOCKS_SIDEBAR_HEIGHT + PADDING

        const val BEACON_SIDEBARS_HEIGHT = BEACON_STATS_SIDEBAR_HEIGHT + BEACON_BLOCKS_SIDEBAR_HEIGHT + PADDING

        const val BEACON_BASE_GUI_X_OFFSET = 0

        const val ITEM_ICON_SIZE = 16
        const val ITEM_ICON_OFFSET = ITEM_ICON_SIZE + PADDING
        const val PAYMENT_ITEM_ICON_SPACING = 8
        const val PAYMENT_ITEM_ICON_X = 18
        const val PAYMENT_ITEM_ICON_Y = 109

        const val EFFECT_BUTTON_SIZE = 22
        const val EFFECT_BUTTON_OFFSET = EFFECT_BUTTON_SIZE + PADDING
        const val PRIMARY_BUTTON_X = 76
        const val PRIMARY_BUTTON_Y = 22
        const val SECONDARY_BUTTON_X = 167
        const val SECONDARY_BUTTON_Y = 47

        const val DONE_BUTTON_X = 173
        const val DONE_BUTTON_Y = 106
        const val CANCEL_BUTTON_X = 197
        const val CANCEL_BUTTON_Y = 106

        const val PRIMARY_TEXT_X = 62
        const val SECONDARY_TEXT_X = 169
        const val LABEL_TEXT_Y = 10

        private val POWER_TEXT_COLOR = SRGB.from255(224, 224, 224)
        private val WHITE = SRGB(1.0, 1.0, 1.0)
        private val RED = SRGB(1.0, 0.0, 0.0)
    }
}
