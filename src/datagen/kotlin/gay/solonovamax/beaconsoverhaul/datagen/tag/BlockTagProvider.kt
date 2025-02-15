package gay.solonovamax.beaconsoverhaul.datagen.tag

import gay.solonovamax.beaconsoverhaul.register.BlockRegistry
import gay.solonovamax.beaconsoverhaul.register.TagRegistry
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider
import net.minecraft.block.Blocks
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.tag.BlockTags
import java.util.concurrent.CompletableFuture

class BlockTagProvider(
    output: FabricDataOutput,
    registriesFuture: CompletableFuture<RegistryWrapper.WrapperLookup>,
) : FabricTagProvider.BlockTagProvider(output, registriesFuture) {
    override fun configure(lookup: RegistryWrapper.WrapperLookup) {
        getOrCreateTagBuilder(TagRegistry.BEACON_TRANSPARENT)
            .add(Blocks.BEDROCK)

        getOrCreateTagBuilder(BlockTags.WALLS)
            .add(BlockRegistry.PRISMARINE_BRICK_WALL)
            .add(BlockRegistry.DARK_PRISMARINE_WALL)
    }
}
