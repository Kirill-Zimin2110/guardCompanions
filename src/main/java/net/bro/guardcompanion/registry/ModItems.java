package net.bro.guardcompanion.registry;

import net.bro.guardcompanion.GuardCompanionMod;
import net.bro.guardcompanion.item.CompanionSpawnEgg;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GuardCompanionMod.MOD_ID);

    public static final DeferredItem<CompanionSpawnEgg> COMPANION_SPAWN_EGG =
            ITEMS.registerItem("companion_spawn_egg", props -> new CompanionSpawnEgg(
                    () -> ModEntities.COMPANION.get(), 0x2C4A7C, 0xE0C060, props),
                    new Item.Properties().stacksTo(64));

    private ModItems() {
    }
}
