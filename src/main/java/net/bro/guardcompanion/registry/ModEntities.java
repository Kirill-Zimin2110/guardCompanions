package net.bro.guardcompanion.registry;

import net.bro.guardcompanion.GuardCompanionMod;
import net.bro.guardcompanion.entity.CompanionEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, GuardCompanionMod.MOD_ID);

    @SuppressWarnings("deprecation")
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> COMPANION =
            ENTITY_TYPES.register("companion", () -> EntityType.Builder
                    .of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("companion"));

    private ModEntities() {
    }
}
