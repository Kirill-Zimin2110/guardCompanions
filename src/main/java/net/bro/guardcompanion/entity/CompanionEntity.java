package net.bro.guardcompanion.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.bro.guardcompanion.CompanionAttributes;
import net.bro.guardcompanion.ai.CompanionBuildPlan;
import net.bro.guardcompanion.ai.CompanionGoals;
import net.bro.guardcompanion.registry.ModItems;
import net.bro.guardcompanion.registry.ModSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

public class CompanionEntity extends TamableAnimal {
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);

    public static final float EGG_DROP_CHANCE = 0.5F;
    private static final double OWNER_SEARCH_RADIUS = 20.0D;
    private static final int INVENTORY_SIZE = 18;

    private CompanionMode lastAttackMode = CompanionMode.HOSTILES;
    @Nullable
    private CompanionBuildPlan buildPlan;
    private final NonNullList<ItemStack> backpack = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CompanionAttributes.BUILDER;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, CompanionMode.HOSTILES.ordinal());
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new CompanionGoals.CompanionStayGoal(this));
        this.goalSelector.addGoal(1, new CompanionGoals.CompanionBuildGoal(this));
        this.goalSelector.addGoal(1, new CompanionGoals.CompanionFishGoal(this));
        this.goalSelector.addGoal(2, new CompanionGoals.CompanionFollowOwnerGoal(this, 1.0D, 3.0D, 5.0D));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.25D, true));
        this.targetSelector.addGoal(1, new CompanionGoals.CompanionGuardTargetGoal(this));
    }

    public CompanionMode getMode() {
        CompanionMode[] modes = CompanionMode.values();
        int index = Mth.clamp(this.entityData.get(DATA_MODE), 0, modes.length - 1);
        return modes[index];
    }

    public void setMode(CompanionMode mode) {
        if (!mode.isTaskMode()) {
            this.lastAttackMode = mode;
        }
        this.entityData.set(DATA_MODE, mode.ordinal());
        this.setOrderedToSit(mode == CompanionMode.STAY);
        this.setTarget(null);
        this.getNavigation().stop();
    }

    @Nullable
    public CompanionBuildPlan getBuildPlan() {
        return buildPlan;
    }

    public void setBuildPlan(@Nullable CompanionBuildPlan plan) {
        this.buildPlan = plan;
    }

    public NonNullList<ItemStack> getBackpack() {
        return backpack;
    }

    public int countInBackpack(ItemStack stack) {
        int count = 0;
        for (ItemStack slot : backpack) {
            if (ItemStack.isSameItemSameComponents(slot, stack)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    public boolean addToBackpack(ItemStack stack) {
        for (ItemStack slot : backpack) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                int space = slot.getMaxStackSize() - slot.getCount();
                int moved = Math.min(space, stack.getCount());
                slot.grow(moved);
                stack.shrink(moved);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        for (int i = 0; i < backpack.size(); i++) {
            if (backpack.get(i).isEmpty()) {
                backpack.set(i, stack.copy());
                stack.setCount(0);
                return true;
            }
        }
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isAlive() && this.getMode().isTaskMode()) {
            this.setOrderedToSit(false);
        }
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (this.addToBackpack(stack)) {
            this.take(itemEntity, stack.getCount());
            itemEntity.discard();
            this.playSound(SoundEvents.ITEM_PICKUP, 0.2F,
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (this.random.nextFloat() < EGG_DROP_CHANCE) {
            this.spawnAtLocation(new ItemStack(ModItems.COMPANION_SPAWN_EGG.get()));
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame() && stack.is(Items.BONE)) {
            if (!this.level().isClientSide) {
                this.tame(player);
                this.setOrderedToSit(false);
                this.level().broadcastEntityEvent(this, (byte) 7);
                this.setMode(CompanionMode.HOSTILES);
                stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        EquipmentSlot slot = this.getEquipmentSlotForItem(stack);
        boolean isTool = stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof PickaxeItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof ShovelItem
                || stack.getItem() instanceof HoeItem;
        boolean isEquipment = slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || isTool;
        if (!stack.isEmpty() && isEquipment && (!this.isTame() || this.isOwnedBy(player))) {
            ItemStack current = this.getItemBySlot(slot);
            if (current.isEmpty() || player.isShiftKeyDown()) {
                this.setItemSlot(slot, stack.copyWithCount(1));
                this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, current);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        if (this.isTame() && this.isOwnedBy(player) && stack.isEmpty()) {
            if (!this.level().isClientSide) {
                CompanionMode current = this.getMode();
                CompanionMode next = current.isTaskMode() ? this.lastAttackMode : current.nextAttackMode();
                this.setMode(next);
                this.playSound(ModSounds.COMPANION_COMMAND_ACCEPTED.get(), 1.0F, 1.0F);
                player.displayClientMessage(
                        Component.translatable("message.guardcompanion.mode_set", next.displayName()), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        if (!level.isClientSide() && spawnType == MobSpawnType.SPAWN_EGG) {
            AABB searchBox = this.getBoundingBox().inflate(OWNER_SEARCH_RADIUS);
            List<Player> players = level.getEntitiesOfClass(Player.class, searchBox);
            Optional<Player> owner = players.stream().min((a, b) ->
                    Float.compare(a.distanceTo(this), b.distanceTo(this)));
            owner.ifPresent(player -> {
                this.tame(player);
                this.setPersistenceRequired();
            });
        }
        return data;
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target == owner) {
            return false;
        }
        return !(target instanceof CompanionEntity);
    }

    @Nullable
    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel level,
            net.minecraft.world.entity.AgeableMob otherParent) {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GuardMode", this.getMode().ordinal());
        tag.putString("LastAttackMode", this.lastAttackMode.name());
        if (this.buildPlan != null) {
            tag.put("BuildPlan", this.buildPlan.save(this.level().registryAccess()));
        }
        ListTag backpackTag = new ListTag();
        for (ItemStack slot : backpack) {
            if (!slot.isEmpty()) {
                backpackTag.add(slot.save(this.level().registryAccess(), new CompoundTag()));
            }
        }
        tag.put("Backpack", backpackTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GuardMode")) {
            this.entityData.set(DATA_MODE, Mth.clamp(tag.getInt("GuardMode"), 0, CompanionMode.values().length - 1));
        }
        if (tag.contains("LastAttackMode")) {
            try {
                this.lastAttackMode = CompanionMode.valueOf(tag.getString("LastAttackMode"));
            } catch (IllegalArgumentException ignored) {
                this.lastAttackMode = CompanionMode.HOSTILES;
            }
        }
        if (tag.contains("BuildPlan")) {
            this.buildPlan = CompanionBuildPlan.load(tag.getCompound("BuildPlan"), this.level().registryAccess());
        }
        this.setOrderedToSit(this.getMode() == CompanionMode.STAY);
        ListTag backpackTag = tag.getList("Backpack", Tag.TAG_COMPOUND);
        for (int i = 0; i < backpackTag.size() && i < backpack.size(); i++) {
            backpack.set(i, ItemStack.parse(this.level().registryAccess(), backpackTag.getCompound(i))
                    .orElse(ItemStack.EMPTY));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.COMPANION_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.COMPANION_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.COMPANION_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + this.random.nextFloat() * 0.2F;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !this.isTame();
    }

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return super.getOwnerUUID();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.COMPANION_SPAWN_EGG.get());
    }
}
