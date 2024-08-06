package gay.solonovamax.beaconsoverhaul.advancement

/*import com.google.gson.JsonObject
import gay.solonovamax.beaconsoverhaul.util.identifierOf
import net.minecraft.advancement.criterion.AbstractCriterion
import net.minecraft.advancement.criterion.AbstractCriterionConditions
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer
import net.minecraft.predicate.entity.LootContextPredicate
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

object RedirectBeaconCriterion : AbstractCriterion<RedirectBeaconCriterion.Conditions>() {
    val IDENTIFIER = identifierOf("redirect_beacon")

    @JvmStatic
    fun trigger(player: ServerPlayerEntity) {
        trigger(player) {
            it.matches()
        }
    }

    override fun getId(): Identifier = IDENTIFIER

    override fun conditionsFromJson(
        obj: JsonObject,
        playerPredicate: LootContextPredicate,
        predicateDeserializer: AdvancementEntityPredicateDeserializer,
    ): Conditions {
        return Conditions(playerPredicate)
    }

    class Conditions(playerPredicate: LootContextPredicate) : AbstractCriterionConditions(IDENTIFIER, playerPredicate) {
        fun matches(): Boolean = true

        companion object {
            fun create(): Conditions {
                return Conditions(LootContextPredicate.EMPTY)
            }
        }
    }
}
*/