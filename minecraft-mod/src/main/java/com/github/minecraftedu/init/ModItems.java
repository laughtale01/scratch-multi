package com.github.minecraftedu.init;

import com.github.minecraftedu.MinecraftEduMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Item registration for custom items and block items
 *
 * This class manages the registration of all items in the MinecraftEdu MOD,
 * including BlockItems that represent blocks in the player's inventory.
 */
public class ModItems {

    /**
     * Deferred register for items
     * Uses the MOD_ID from MinecraftEduMod to properly namespace all custom items
     */
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MinecraftEduMod.MOD_ID);

    /**
     * Vertical Oak Slab Item
     *
     * This is the item form of the vertical_oak_slab block.
     * When placed, it will place the VerticalSlabBlock with appropriate facing direction.
     */
    public static final RegistryObject<Item> VERTICAL_OAK_SLAB = ITEMS.register(
        "vertical_oak_slab",
        () -> new BlockItem(
            ModBlocks.VERTICAL_OAK_SLAB.get(),
            new Item.Properties()
        )
    );

    // Future vertical slab items can be added here:
    // public static final RegistryObject<Item> VERTICAL_STONE_SLAB = ITEMS.register(
    //     "vertical_stone_slab",
    //     () -> new BlockItem(ModBlocks.VERTICAL_STONE_SLAB.get(), new Item.Properties())
    // );
}
