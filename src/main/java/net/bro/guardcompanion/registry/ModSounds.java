package net.bro.guardcompanion.registry;

import net.bro.guardcompanion.GuardCompanionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, GuardCompanionMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> COMPANION_AMBIENT = register("companion_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPANION_HURT = register("companion_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPANION_DEATH = register("companion_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPANION_ATTACK = register("companion_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPANION_COMMAND_ACCEPTED = register("companion_command_accepted");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPANION_BUILD = register("companion_build");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(GuardCompanionMod.MOD_ID, name)));
    }

    private ModSounds() {
    }
}
