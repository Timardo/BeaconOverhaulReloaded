package gay.solonovamax.beaconsoverhaul.datagen

import gay.solonovamax.beaconsoverhaul.BeaconConstants
//import gay.solonovamax.beaconsoverhaul.datagen.advancement.AdvancementProvider
import gay.solonovamax.beaconsoverhaul.datagen.lang.LanguageProvider
import gay.solonovamax.beaconsoverhaul.datagen.loot.AdvancementLootTableProvider
import gay.solonovamax.beaconsoverhaul.datagen.loot.BlockDropLootTableProvider
import gay.solonovamax.beaconsoverhaul.datagen.model.ModelProvider
//import gay.solonovamax.beaconsoverhaul.datagen.recipe.RecipeProvider
import gay.solonovamax.beaconsoverhaul.datagen.tag.BlockTagProvider
import gay.solonovamax.beaconsoverhaul.datagen.tag.ItemTagProvider
import gay.solonovamax.beaconsoverhaul.datagen.util.addProviders
import gay.solonovamax.beaconsoverhaul.datagen.util.collectMissingTranslations
import gay.solonovamax.beaconsoverhaul.util.FabricLoader
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.loader.api.ModContainer
import net.minecraft.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.entity.attribute.DefaultAttributeRegistry
import net.minecraft.registry.Registries
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info

object BeaconOverhaulReloadedDataGenerator : DataGeneratorEntrypoint {
    private val logger by getLogger()

    val MOD: ModContainer
        get() = FabricLoader.getModContainer(BeaconConstants.NAMESPACE + "-datagen").get()

    override fun onInitializeDataGenerator(generator: FabricDataGenerator) {
        val pack = generator.createPack()

        pack.addProviders(
            //AdvancementProvider::class,
            AdvancementLootTableProvider::class,
            ModelProvider::class,
            //RecipeProvider::class,
            BlockDropLootTableProvider::class,
            ItemTagProvider::class,
            BlockTagProvider::class,
            LanguageProvider::class,
        )

        logMissing()
    }

    override fun getEffectiveModId() = BeaconConstants.NAMESPACE

    private fun logMissing() {
        logger.info { "Logging missing translations" }
        SharedConstants.isDevelopment = true

        Bootstrap.ensureBootstrapped { "validate" }
        if (SharedConstants.isDevelopment) {
            val missingTranslations = buildMissingTranslations()

            missingTranslations.forEach { key ->
                logger.error { "Missing translations: $key" }
            }
        }

        DefaultAttributeRegistry.checkMissing()
    }

    private fun buildMissingTranslations() = buildSet {
        addAll(Bootstrap.getMissingTranslations())

        with(Registries.STATUS_EFFECT) {
            collectMissingTranslations { obj -> listOf("${obj.translationKey}.description") }
        }
    }
}
