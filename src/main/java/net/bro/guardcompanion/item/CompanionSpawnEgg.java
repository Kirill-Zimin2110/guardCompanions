package net.bro.guardcompanion.item;

import java.util.function.Supplier;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

public class CompanionSpawnEgg extends DeferredSpawnEggItem {
    public CompanionSpawnEgg(Supplier<EntityType<? extends Mob>> type,
                             int backgroundColor, int highlightColor, Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
    }
}
