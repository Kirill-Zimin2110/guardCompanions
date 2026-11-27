package net.bro.guardcompanion;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

public final class CompanionAttributes {
    public static final AttributeSupplier.Builder BUILDER = Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 60.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.35D)
            .add(Attributes.ATTACK_DAMAGE, 3.0D)
            .add(Attributes.ARMOR, 8.0D)
            .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
            .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);

    private CompanionAttributes() {
    }
}
