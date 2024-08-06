package gay.solonovamax.beaconsoverhaul.datagen.util

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator.Pack
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider
import net.minecraft.advancement.Advancement
import net.minecraft.block.Block
import net.minecraft.data.DataProvider
import net.minecraft.data.client.BlockStateModelGenerator
import net.minecraft.data.client.ModelIds
import net.minecraft.data.client.TexturedModel
import net.minecraft.data.family.BlockFamily
import net.minecraft.data.server.recipe.RecipeExporter
import net.minecraft.data.server.recipe.RecipeProvider
import net.minecraft.loot.LootPool
import net.minecraft.loot.LootTable
import net.minecraft.loot.provider.number.BinomialLootNumberProvider
import net.minecraft.loot.provider.number.ConstantLootNumberProvider
import net.minecraft.loot.provider.number.ScoreLootNumberProvider
import net.minecraft.loot.provider.number.UniformLootNumberProvider
import net.minecraft.recipe.Ingredient
import net.minecraft.recipe.book.RecipeCategory
import net.minecraft.util.Identifier
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import net.minecraft.data.family.BlockFamily.Variant as FamilyVariant
import net.minecraft.loot.context.LootContext.EntityTarget as LootEntityTarget

typealias AdvancementExporter = Consumer<Advancement>

/*fun AdvancementExporter.buildExportedAdvancement(id: Identifier, builder: Advancement.Builder.() -> Unit): Advancement {
    return Advancement.Builder.createUntelemetered().apply(builder).build(id).also { advancement ->
        accept(advancement)
    }
}

fun FabricAdvancementProvider.advancementOf(id: Identifier): Advancement {
    return Advancement.Builder.createUntelemetered().build(id)
}*/

typealias LootTableExporter = BiConsumer<Identifier, LootTable.Builder>

fun buildLootTable(action: LootTable.Builder.() -> Unit): LootTable.Builder {
    return LootTable.builder().apply(action)
}

fun buildLootPool(action: LootPool.Builder.() -> Unit): LootPool.Builder {
    return LootPool.builder().apply(action)
}

context(LootPool.Builder)
fun constant(value: Float): ConstantLootNumberProvider = ConstantLootNumberProvider.create(value)

context(LootPool.Builder)
fun binomial(trials: Int, probability: Float): BinomialLootNumberProvider = BinomialLootNumberProvider.create(trials, probability)

context(LootPool.Builder)
fun score(target: LootEntityTarget, score: String, scale: Float = 1.0f): ScoreLootNumberProvider =
    ScoreLootNumberProvider.create(target, score, scale)

context(LootPool.Builder)
fun uniform(min: Float, max: Float): UniformLootNumberProvider = UniformLootNumberProvider.create(min, max)

fun BlockStateModelGenerator.registerParentedItemModel(block: Block) {
    return registerParentedItemModel(block, ModelIds.getBlockModelId(block))
}

fun BlockStateModelGenerator.registerWallModelTexturePool(family: BlockFamily): BlockStateModelGenerator.BlockTexturePool {
    return this.BlockTexturePool(TexturedModel.CUBE_ALL[family.baseBlock].textures)
        .wall(family[FamilyVariant.WALL]) // evil
}

operator fun BlockFamily.get(variant: FamilyVariant): Block = getVariant(variant)

typealias RecipeExporter = Consumer<RecipeExporter>

fun RecipeExporter.offerWallRecipe(family: BlockFamily) {
    val wallVariantName = FamilyVariant.WALL.getName()
    val baseBlock = family.baseBlock

    val recipeBuilder = RecipeProvider.getWallRecipe(RecipeCategory.DECORATIONS, family[FamilyVariant.WALL], Ingredient.ofItems(baseBlock))

    family.group.ifPresent { group -> recipeBuilder.group(group + "_" + wallVariantName) }

    recipeBuilder.criterion(
        family.unlockCriterionName.orElseGet { RecipeProvider.hasItem(baseBlock) },
        RecipeProvider.conditionsFromItem(baseBlock)
    )

    recipeBuilder.offerTo(this)
}

fun RecipeExporter.offerStonecuttingRecipe(
    category: RecipeCategory,
    family: BlockFamily,
    variant: FamilyVariant,
) = RecipeProvider.offerStonecuttingRecipe(this, category, family[variant], family.baseBlock)


fun <T : DataProvider> Pack.addProvider(providerClass: KClass<T>) {
    val primaryConstructor = requireNotNull(providerClass.primaryConstructor)
    val params = primaryConstructor.parameters

    when {
        !FabricDataOutput::class.createType().isSubtypeOf(params[0].type) -> error("First parameter must always be a FabricDataOutput")

        params.size == 1 -> addProvider { output ->
            primaryConstructor.call(output)
        }

        params.size == 2 && CompletableFuture::class.isSubclassOf(params[1].type.jvmErasure) -> addProvider { output, wrapperLookup ->
            primaryConstructor.call(output, wrapperLookup)
        }

        else -> error("Could not match constructor")
    }
}

fun <T : DataProvider> Pack.addProviders(vararg providerClasses: KClass<out T>) {
    for (providerClass in providerClasses) {
        addProvider(providerClass)
    }
}
