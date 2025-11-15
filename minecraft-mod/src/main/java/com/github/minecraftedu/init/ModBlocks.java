package com.github.minecraftedu.init;

import com.github.minecraftedu.MinecraftEduMod;
import com.github.minecraftedu.block.VerticalSlabBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block registration for custom blocks
 *
 * This class manages the registration of all custom blocks in the MinecraftEdu MOD.
 * Currently includes vertical slab variants for directional placement.
 */
public class ModBlocks {

    /**
     * Deferred register for blocks
     * Uses the MOD_ID from MinecraftEduMod to properly namespace all custom blocks
     */
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, MinecraftEduMod.MOD_ID);

    /**
     * Vertical Oak Slab - Prototype vertical slab block
     *
     * Properties:
     * - facing: north, south, east, west (horizontal directional placement)
     * - waterlogged: true/false (compatibility with water)
     * - Collision box: 8x16x16 (half width, full height and depth)
     *
     * Unlike vanilla slabs which only have vertical positioning (bottom/top/double),
     * vertical slabs can be placed facing any of the 4 horizontal directions.
     */
    public static final RegistryObject<Block> VERTICAL_OAK_SLAB = BLOCKS.register(
        "vertical_oak_slab",
        () -> new VerticalSlabBlock(
            BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                .strength(2.0F, 3.0F)  // Same as oak planks
                .requiresCorrectToolForDrops()
        )
    );

    // Future vertical slabs can be added here following the same pattern:
    // public static final RegistryObject<Block> VERTICAL_STONE_SLAB = BLOCKS.register(
    //     "vertical_stone_slab",
    //     () -> new VerticalSlabBlock(BlockBehaviour.Properties.copy(Blocks.STONE))
    // );
}
