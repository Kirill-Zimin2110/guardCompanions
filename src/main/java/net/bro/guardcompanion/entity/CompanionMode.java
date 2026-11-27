package net.bro.guardcompanion.entity;

import net.minecraft.network.chat.Component;

public enum CompanionMode {
    HOSTILES("guardcompanion.mode.hostiles"),
    PASSIVES("guardcompanion.mode.passives"),
    ALL("guardcompanion.mode.all"),
    STAY("guardcompanion.mode.stay"),
    BUILDER("guardcompanion.mode.builder"),
    FISHER("guardcompanion.mode.fisher");

    public static final CompanionMode[] ATTACK_MODES = {HOSTILES, PASSIVES, ALL, STAY};

    private final String translationKey;

    CompanionMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public boolean isTaskMode() {
        return this == BUILDER || this == FISHER;
    }

    public CompanionMode nextAttackMode() {
        for (int i = 0; i < ATTACK_MODES.length; i++) {
            if (ATTACK_MODES[i] == this) {
                return ATTACK_MODES[(i + 1) % ATTACK_MODES.length];
            }
        }
        return ATTACK_MODES[0];
    }
}
