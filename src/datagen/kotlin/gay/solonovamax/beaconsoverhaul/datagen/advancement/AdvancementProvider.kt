package gay.solonovamax.beaconsoverhaul.datagen.advancement

/*import gay.solonovamax.beaconsoverhaul.advancement.RedirectBeaconCriterion
import gay.solonovamax.beaconsoverhaul.datagen.util.AdvancementExporter
import gay.solonovamax.beaconsoverhaul.datagen.util.advancementOf
import gay.solonovamax.beaconsoverhaul.datagen.util.buildExportedAdvancement
import gay.solonovamax.beaconsoverhaul.util.identifierOf
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider
import net.minecraft.advancement.AdvancementFrame
import net.minecraft.advancement.AdvancementRewards
import net.minecraft.advancement.criterion.ConstructBeaconCriterion
import net.minecraft.block.Blocks
import net.minecraft.predicate.NumberRange
import net.minecraft.text.Text

class AdvancementProvider(output: FabricDataOutput) : FabricAdvancementProvider(output) {
    override fun generateAdvancement(exporter: AdvancementExporter) {
        val summonWither = advancementOf(identifierOf("minecraft:nether/summon_wither"))

        val createBeacon = exporter.buildExportedAdvancement(identifierOf("minecraft:nether/create_beacon")) {
            parent(summonWither)

            display(
                Blocks.BEACON,
                Text.translatable("advancements.nether.create_beacon.title"),
                Text.translatable("advancements.nether.create_beacon.description"),
                null,
                AdvancementFrame.TASK,
                true,
                true,
                false
            )

            criterion("beacon", ConstructBeaconCriterion.Conditions.level(NumberRange.IntRange.atLeast(1)))
            rewards(AdvancementRewards.Builder.loot(identifierOf("beacon_guide")))
        }

        val createFullBeacon = exporter.buildExportedAdvancement(identifierOf("minecraft:nether/create_full_beacon")) {
            parent(createBeacon)

            display(
                Blocks.BEACON,
                Text.translatable("advancements.nether.create_full_beacon.title"),
                Text.translatable("advancements.nether.create_full_beacon.description"),
                null,
                AdvancementFrame.GOAL,
                true,
                true,
                false
            )

            criterion("beacon", ConstructBeaconCriterion.Conditions.level(NumberRange.IntRange.atLeast(4)))

        }

        val redirectBeacon = exporter.buildExportedAdvancement(identifierOf("nether/redirect_beacon")) {
            parent(createFullBeacon)

            display(
                Blocks.AMETHYST_CLUSTER,
                Text.translatable("advancements.nether.beaconoverhaul.redirect_beacon.title"),
                Text.translatable("advancements.nether.beaconoverhaul.redirect_beacon.description"),
                null,
                AdvancementFrame.CHALLENGE,
                true,
                true,
                false
            )

            criterion("redirect_beacon", RedirectBeaconCriterion.Conditions.create())
        }
    }
}
*/