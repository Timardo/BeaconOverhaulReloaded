package gay.solonovamax.beaconsoverhaul.screen

import gay.solonovamax.beaconsoverhaul.block.beacon.data.OverhauledBeaconData
import gay.solonovamax.beaconsoverhaul.register.ScreenHandlerRegistry
import gay.solonovamax.beaconsoverhaul.util.PropertyDelegate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import net.minecraft.block.Blocks
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.tag.ItemTags
import net.minecraft.screen.BeaconScreenHandler
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.slot.Slot
import net.minecraft.world.World
import org.slf4j.kotlin.getLogger
import java.util.Optional

class OverhauledBeaconScreenHandler private constructor(
    syncId: Int,
    player: PlayerEntity,
    data: OverhauledBeaconData,
    delegate: PropertyDelegate,
    context: ScreenHandlerContext,
    onClose: (PlayerEntity) -> Unit,
) : OverhauledScreenHandler<OverhauledBeaconData>(
    syncId,
    player,
    data,
    delegate,
    context,
    onClose,
    ScreenHandlerRegistry.OVERHAULED_BEACON_SCREEN_HANDLER
) {
    private val logger by getLogger()

    /**
     * Client constructor
     */
    @OptIn(ExperimentalSerializationApi::class)
    constructor(
        syncId: Int,
        inv: PlayerInventory,
        buf: PacketByteBuf,
    ) : this(
        syncId,
        inv.player,
        Cbor.decodeFromByteArray(buf.readByteArray()),
        PropertyDelegate(PROPERTY_COUNT),
        ScreenHandlerContext.EMPTY,
        {},
    )

    /**
     * Server constructor
     */
    constructor(
        syncId: Int,
        player: PlayerEntity,
        propertyDelegate: PropertyDelegate,
        context: ScreenHandlerContext,
        onClose: (PlayerEntity) -> Unit,
    ) : this(syncId, player, OverhauledBeaconData.UNIT, propertyDelegate, context, onClose)

    private val paymentInventory = PaymentInventory()

    val level: Int
        get() = delegate[0]

    val primaryEffect: StatusEffect?
        get() = BeaconScreenHandler.getStatusEffectForRawId(delegate[1])

    val secondaryEffect: StatusEffect?
        get() = BeaconScreenHandler.getStatusEffectForRawId(delegate[2])

    val hasPayment: Boolean
        get() = !paymentInventory.getStack(PAYMENT_SLOT_ID).isEmpty

    private var paymentSlot: PaymentSlot = PaymentSlot(paymentInventory, PAYMENT_SLOT_ID, PAYMENT_SLOT_X, PAYMENT_SLOT_Y)

    init {
        checkDataCount(delegate, PROPERTY_COUNT)
        addSlot(paymentSlot)
        addProperties(delegate)

        // Magic numbers!!
        for (y in 0 until 3)
            for (x in 0 until 9)
                addSlot(
                    Slot(player.inventory, x + y * 9 + 9, INVENTORY_X + x * INVENTORY_SLOT_OFFSET, INVENTORY_Y + y * INVENTORY_SLOT_OFFSET)
                )

        for (x in 0 until 9)
            addSlot(Slot(player.inventory, x, INVENTORY_X + x * INVENTORY_SLOT_OFFSET, INVENTORY_Y + 58))
    }

    override fun canUse(player: PlayerEntity?): Boolean = canUse(this.context, player, Blocks.BEACON)

    override fun setProperty(id: Int, value: Int) {
        super.setProperty(id, value)
        this.sendContentUpdates()
    }

    override fun quickMove(player: PlayerEntity?, slotId: Int): ItemStack {
        val slot = slots[slotId]

        return if (slot.hasStack()) {
            val sourceStack = slot.stack
            val inputStack = sourceStack.copy()

            if (slotId == 0) {
                if (!this.insertItem(sourceStack, INVENTORY_START, HOTBAR_END, false))
                    return ItemStack.EMPTY

                slot.onQuickTransfer(sourceStack, inputStack)
            } else if (!paymentSlot.hasStack() && paymentSlot.canInsert(sourceStack)) {
                if (!this.insertItem(sourceStack, PAYMENT_SLOT_ID, BEACON_INVENTORY_SIZE, false))
                    return ItemStack.EMPTY
            }

            if (sourceStack.isEmpty)
                slot.stack = ItemStack.EMPTY
            else
                slot.markDirty()

            if (sourceStack.count == inputStack.count)
                return ItemStack.EMPTY

            slot.onTakeItem(player, sourceStack)

            inputStack
        } else {
            ItemStack.EMPTY
        }
    }

    fun setEffects(primary: Optional<StatusEffect>, secondary: Optional<StatusEffect>) {
        if (paymentSlot.hasStack()) {
            delegate[1] = primary.map(BeaconScreenHandler::getRawIdForStatusEffect).orElse(-1)
            delegate[2] = secondary.map(BeaconScreenHandler::getRawIdForStatusEffect).orElse(-1)
            paymentSlot.takeStack(1)
            context.run(World::markDirty)
        }
    }

    internal class PaymentSlot(
        inventory: PaymentInventory,
        index: Int = PAYMENT_SLOT_ID,
        x: Int,
        y: Int,
    ) : Slot(inventory, index, x, y) {
        override fun canInsert(stack: ItemStack): Boolean = stack.isIn(ItemTags.BEACON_PAYMENT_ITEMS)
        override fun getMaxItemCount(): Int = 1
    }

    internal class PaymentInventory : SimpleInventory(BEACON_INVENTORY_SIZE) {
        override fun isValid(slot: Int, stack: ItemStack): Boolean = stack.isIn(ItemTags.BEACON_PAYMENT_ITEMS)
        override fun getMaxCountPerStack(): Int = 1
    }

    companion object {
        private const val PAYMENT_SLOT_ID = 0
        private const val BEACON_INVENTORY_SIZE = 1
        private const val PROPERTY_COUNT = 3
        private const val INVENTORY_START = 1
        private const val HOTBAR_END = 37

        private const val PAYMENT_SLOT_X = 149 + OverhauledBeaconScreen.BEACON_BASE_GUI_X_OFFSET
        private const val PAYMENT_SLOT_Y = 109
        private const val INVENTORY_X = 36 + OverhauledBeaconScreen.BEACON_BASE_GUI_X_OFFSET
        private const val INVENTORY_Y = 137
        private const val INVENTORY_SLOT_OFFSET = 18
    }
}
