package net.bro.guardcompanion.ai;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.bro.guardcompanion.entity.CompanionEntity;
import net.bro.guardcompanion.entity.CompanionMode;
import net.bro.guardcompanion.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CompanionGoals {
    private static final double REACH_DISTANCE_SQR = 12.0D;
    private static final double TELEPORT_DISTANCE_SQR = 30.0D * 30.0D;

    private CompanionGoals() {
    }

    public static void sendMessageToOwner(CompanionEntity companion, Component message, boolean actionBar) {
        if (companion.getOwner() instanceof Player player) {
            player.displayClientMessage(message, actionBar);
        }
    }

    public static boolean isWithinReach(CompanionEntity companion, BlockPos pos, double reachSqr) {
        return companion.distanceToSqr(Vec3.atCenterOf(pos)) <= reachSqr;
    }

    public static boolean navigateTo(CompanionEntity companion, BlockPos pos, double speed) {
        return companion.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, speed);
    }

    public static boolean tryTeleportToOwner(CompanionEntity companion, Player owner) {
        if (companion.isLeashed() || companion.isPassenger()) {
            return false;
        }
        BlockPos ownerPos = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = companion.getRandom().nextInt(3) - 1;
            int dy = companion.getRandom().nextInt(3) - 1;
            int dz = companion.getRandom().nextInt(3) - 1;
            BlockPos candidate = ownerPos.offset(dx, dy, dz);
            if (companion.randomTeleport(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D, true)) {
                return true;
            }
        }
        return false;
    }

    public static class CompanionFollowOwnerGoal extends Goal {
        private final CompanionEntity companion;
        private final double speedModifier;
        private final double startDistanceSqr;
        private final double stopDistanceSqr;
        private int timeToRecalcPath;

        public CompanionFollowOwnerGoal(CompanionEntity companion, double speedModifier,
                                        double startDistance, double stopDistance) {
            this.companion = companion;
            this.speedModifier = speedModifier;
            this.startDistanceSqr = startDistance * startDistance;
            this.stopDistanceSqr = stopDistance * stopDistance;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.companion.getMode().isTaskMode()
                    || this.companion.getMode() == CompanionMode.STAY
                    || this.companion.getTarget() != null
                    || this.companion.isInSittingPose()) {
                return false;
            }
            if (!(this.companion.getOwner() instanceof Player owner)) {
                return false;
            }
            if (owner.isSpectator()) {
                return false;
            }
            return this.companion.distanceToSqr(owner) > this.stopDistanceSqr;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.companion.getMode() == CompanionMode.STAY
                    || this.companion.getMode().isTaskMode()
                    || this.companion.getTarget() != null) {
                return false;
            }
            if (!(this.companion.getOwner() instanceof Player owner)) {
                return false;
            }
            double distanceSqr = this.companion.distanceToSqr(owner);
            if (distanceSqr > TELEPORT_DISTANCE_SQR) {
                return !tryTeleportToOwner(this.companion, owner);
            }
            return distanceSqr > this.startDistanceSqr;
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            this.companion.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (!(this.companion.getOwner() instanceof Player owner)) {
                return;
            }
            this.companion.getLookControl().setLookAt(owner, 10.0F, this.companion.getMaxHeadXRot());
            if (--this.timeToRecalcPath > 0) {
                return;
            }
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            double distanceSqr = this.companion.distanceToSqr(owner);
            if (distanceSqr > this.stopDistanceSqr) {
                navigateTo(this.companion, owner.blockPosition(), this.speedModifier);
            } else {
                this.companion.getNavigation().stop();
            }
        }
    }

    public static class CompanionStayGoal extends Goal {
        private final CompanionEntity companion;

        public CompanionStayGoal(CompanionEntity companion) {
            this.companion = companion;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.companion.getMode() == CompanionMode.STAY;
        }

        @Override
        public boolean canContinueToUse() {
            return this.companion.getMode() == CompanionMode.STAY;
        }

        @Override
        public void start() {
            this.companion.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.companion.getNavigation().stop();
        }
    }

    public static class CompanionGuardTargetGoal extends TargetGoal {
        private static final double TARGET_RANGE = 16.0D;

        private final CompanionEntity companion;
        @Nullable
        private LivingEntity foundTarget;

        public CompanionGuardTargetGoal(CompanionEntity companion) {
            super(companion, false);
            this.companion = companion;
        }

        @Override
        public boolean canUse() {
            CompanionMode mode = this.companion.getMode();
            if (mode.isTaskMode() || mode == CompanionMode.STAY || this.companion.isInSittingPose()) {
                return false;
            }
            this.foundTarget = findTarget(mode);
            return this.foundTarget != null;
        }

        @Override
        public void start() {
            this.companion.setTarget(this.foundTarget);
            this.unseenMemoryTicks = 60;
            super.start();
        }

        @Nullable
        private LivingEntity findTarget(CompanionMode mode) {
            Class<? extends LivingEntity> targetClass = switch (mode) {
                case HOSTILES -> Monster.class;
                case PASSIVES -> Animal.class;
                case ALL -> Mob.class;
                default -> null;
            };
            if (targetClass == null) {
                return null;
            }
            AABB searchArea = this.companion.getBoundingBox().inflate(TARGET_RANGE, 8.0D, TARGET_RANGE);
            TargetingConditions conditions = TargetingConditions.forCombat()
                    .range(TARGET_RANGE)
                    .selector(candidate -> candidate != this.companion.getOwner());
            LivingEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (LivingEntity candidate : this.companion.level().getEntitiesOfClass(targetClass, searchArea)) {
                if (candidate instanceof CompanionEntity || candidate.isInvulnerable()) {
                    continue;
                }
                if (!this.companion.canAttack(candidate, conditions)) {
                    continue;
                }
                double distance = this.companion.distanceToSqr(candidate);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
            return nearest;
        }
    }

    public static class CompanionBuildGoal extends Goal {
        private enum Phase {
            PREPARE, GATHER, BUILD, DONE
        }

        private static final int GATHER_SEARCH_RADIUS = 16;
        private static final int STUCK_LIMIT = 300;

        private static final Map<Item, Item> LOG_TO_PLANKS = buildLogToPlanks();

        private final CompanionEntity companion;
        private final RandomSource random = RandomSource.create();

        private Phase phase = Phase.PREPARE;
        private int stuckTicks;
        private int gatherCooldown;
        @Nullable
        private BlockPos gatherTarget;
        private final Set<BlockPos> skipped = new HashSet<>();

        public CompanionBuildGoal(CompanionEntity companion) {
            this.companion = companion;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.companion.getMode() == CompanionMode.BUILDER && this.companion.getBuildPlan() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && this.phase != Phase.DONE;
        }

        @Override
        public void stop() {
            this.companion.getNavigation().stop();
        }

        @Override
        public void tick() {
            CompanionBuildPlan plan = this.companion.getBuildPlan();
            if (plan == null) {
                return;
            }
            switch (this.phase) {
                case PREPARE -> this.prepare(plan);
                case GATHER -> this.gather(plan);
                case BUILD -> this.build(plan);
                case DONE -> {
                }
            }
        }

        private void prepare(CompanionBuildPlan plan) {
            Map<Item, Integer> required = plan.requiredItems();
            boolean satisfied = true;
            for (Map.Entry<Item, Integer> need : required.entrySet()) {
                if (countOf(need.getKey()) < need.getValue()) {
                    satisfied = false;
                    break;
                }
            }
            if (satisfied) {
                this.craftMaterials(plan);
                this.phase = Phase.BUILD;
                return;
            }
            this.phase = Phase.GATHER;
            sendMessageToOwner(this.companion,
                    Component.translatable("message.guardcompanion.builder.gathering"), false);
        }

        private void gather(CompanionBuildPlan plan) {
            Map<Item, Integer> required = plan.requiredItems();
            Item missing = findMissingPrimary(required);
            if (missing == null) {
                this.craftMaterials(plan);
                this.phase = Phase.BUILD;
                return;
            }

            if (this.gatherCooldown > 0) {
                this.gatherCooldown--;
                return;
            }

            if (this.gatherTarget == null || !this.isLogOrStone(this.gatherTarget, missing)) {
                this.gatherTarget = findNearestBlock(missing);
            }
            if (this.gatherTarget == null) {
                sendMessageToOwner(this.companion,
                        Component.translatable("message.guardcompanion.builder.no_resources",
                                missing.getDescription()), false);
                this.craftMaterials(plan);
                this.phase = Phase.BUILD;
                return;
            }

            if (isWithinReach(this.companion, this.gatherTarget, REACH_DISTANCE_SQR)) {
                this.breakBlock(this.gatherTarget);
                this.gatherTarget = null;
                this.gatherCooldown = 10;
            } else {
                if (this.companion.getNavigation().isDone()) {
                    navigateTo(this.companion, this.gatherTarget, 1.1D);
                }
                if (++this.stuckTicks > STUCK_LIMIT) {
                    this.gatherTarget = null;
                    this.stuckTicks = 0;
                }
            }
        }

        @Nullable
        private Item findMissingPrimary(Map<Item, Integer> required) {
            if (required.getOrDefault(Items.OAK_PLANKS, 0) > countOf(Items.OAK_PLANKS)) {
                return Items.OAK_LOG;
            }
            if (required.getOrDefault(Items.COBBLESTONE, 0) > countOf(Items.COBBLESTONE)) {
                return Items.COBBLESTONE;
            }
            return null;
        }

        private boolean isLogOrStone(BlockPos pos, Item wanted) {
            BlockState state = this.companion.level().getBlockState(pos);
            if (wanted == Items.OAK_LOG) {
                return state.is(BlockTags.LOGS);
            }
            return state.is(net.minecraft.world.level.block.Blocks.STONE)
                    || state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE);
        }

        @Nullable
        private BlockPos findNearestBlock(Item wanted) {
            Level level = this.companion.level();
            BlockPos center = this.companion.blockPosition();
            BlockPos min = center.offset(-GATHER_SEARCH_RADIUS, -2, -GATHER_SEARCH_RADIUS);
            BlockPos max = center.offset(GATHER_SEARCH_RADIUS, 6, GATHER_SEARCH_RADIUS);
            BlockPos nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockState state = level.getBlockState(pos);
                boolean matches;
                if (wanted == Items.OAK_LOG) {
                    matches = state.is(BlockTags.LOGS);
                } else {
                    matches = state.is(net.minecraft.world.level.block.Blocks.STONE)
                            || state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE);
                }
                if (!matches) {
                    continue;
                }
                double distance = this.companion.distanceToSqr(Vec3.atCenterOf(pos));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = pos.immutable();
                }
            }
            return nearest;
        }

        private void breakBlock(BlockPos pos) {
            Level level = this.companion.level();
            BlockState state = level.getBlockState(pos);
            this.companion.swing(InteractionHand.MAIN_HAND);
            level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
            level.destroyBlock(pos, true, this.companion);
            this.companion.playSound(ModSounds.COMPANION_BUILD.get(), 0.8F, 0.8F);
        }

        private void craftMaterials(CompanionBuildPlan plan) {
            Map<Item, Integer> required = plan.requiredItems();

            int planksNeeded = required.getOrDefault(Items.OAK_PLANKS, 0) - countOf(Items.OAK_PLANKS);
            while (planksNeeded > 0 && countOf(Items.OAK_LOG) > 0) {
                consumeOne(Items.OAK_LOG);
                give(Items.OAK_PLANKS, 4);
                planksNeeded -= 4;
            }

            int doorsNeeded = required.getOrDefault(Items.OAK_DOOR, 0) - countOf(Items.OAK_DOOR);
            while (doorsNeeded > 0 && countOf(Items.OAK_PLANKS) >= 6) {
                consume(Items.OAK_PLANKS, 6);
                give(Items.OAK_DOOR, 1);
                doorsNeeded--;
            }

            int laddersNeeded = required.getOrDefault(Items.LADDER, 0) - countOf(Items.LADDER);
            while (laddersNeeded > 0 && countOf(Items.OAK_PLANKS) >= 2) {
                consume(Items.OAK_PLANKS, 2);
                give(Items.LADDER, 3);
                laddersNeeded -= 3;
            }
        }

        private int countOf(Item item) {
            int count = 0;
            for (int i = 0; i < this.companion.getBackpack().size(); i++) {
                ItemStack stack = this.companion.getBackpack().get(i);
                if (matches(item, stack)) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private boolean matches(Item item, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (item == Items.OAK_PLANKS) {
                return stack.is(ItemTags.PLANKS);
            }
            if (item == Items.OAK_LOG) {
                return stack.is(net.minecraft.tags.ItemTags.LOGS);
            }
            return stack.is(item);
        }

        private void consumeOne(Item item) {
            consume(item, 1);
        }

        private void consume(Item item, int amount) {
            for (int i = 0; i < this.companion.getBackpack().size() && amount > 0; i++) {
                ItemStack stack = this.companion.getBackpack().get(i);
                if (matches(item, stack)) {
                    int taken = Math.min(stack.getCount(), amount);
                    stack.shrink(taken);
                    amount -= taken;
                    if (stack.isEmpty()) {
                        this.companion.getBackpack().set(i, ItemStack.EMPTY);
                    }
                }
            }
        }

        private void give(Item item, int amount) {
            ItemStack stack = new ItemStack(item, Math.min(amount, item.getMaxStackSize(new ItemStack(item))));
            if (!this.companion.addToBackpack(stack)) {
                this.companion.spawnAtLocation(stack);
            }
        }

        private void build(CompanionBuildPlan plan) {
            Set<BlockPos> skip = this.skipped;
            Optional<CompanionBuildPlan.Entry> next = plan.firstIncomplete((pos, state) ->
                    this.companion.level().getBlockState(pos).getBlock() == state.getBlock()
                            || skip.contains(pos));
            if (next.isEmpty()) {
                this.finish(plan);
                return;
            }
            CompanionBuildPlan.Entry entry = next.get();

            if (isWithinReach(this.companion, entry.pos(), REACH_DISTANCE_SQR)) {
                if (!this.place(entry)) {
                    this.skipped.add(entry.pos());
                }
                this.stuckTicks = 0;
                return;
            }

            if (this.companion.getNavigation().isDone()) {
                navigateTo(this.companion, entry.pos(), 1.1D);
            }
            if (++this.stuckTicks > STUCK_LIMIT) {
                this.skipped.add(entry.pos());
                this.stuckTicks = 0;
            }
        }

        private boolean place(CompanionBuildPlan.Entry entry) {
            Level level = this.companion.level();
            ItemStack cost = entry.state().getBlock().asItem() == entry.item().asItem()
                    ? new ItemStack(entry.item())
                    : new ItemStack(Items.OAK_PLANKS);
            if (entry.item() == Items.OAK_PLANKS && !matches(Items.OAK_PLANKS, cost)) {
                return false;
            }
            if (entry.item() == Items.COBBLESTONE && this.countOf(Items.COBBLESTONE) <= 0) {
                return false;
            }
            if (entry.item() == Items.OAK_DOOR && this.countOf(Items.OAK_DOOR) <= 0) {
                return false;
            }
            if (entry.item() == Items.LADDER && this.countOf(Items.LADDER) <= 0) {
                return false;
            }
            this.consumeOne(entry.item() == Items.OAK_PLANKS ? Items.OAK_PLANKS : entry.item());

            level.setBlock(entry.pos(), entry.state(), 3);
            this.companion.swing(InteractionHand.MAIN_HAND);
            this.companion.playSound(ModSounds.COMPANION_BUILD.get(), 0.8F, 1.0F);

            if (entry.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                BlockPos above = entry.pos().above();
                level.setBlock(above, entry.state().setValue(
                        net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            }
            return true;
        }

        private void finish(CompanionBuildPlan plan) {
            sendMessageToOwner(this.companion,
                    Component.translatable("message.guardcompanion.builder.done", plan.label()), false);
            this.companion.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
            this.companion.setBuildPlan(null);
            this.phase = Phase.DONE;
            this.companion.setMode(this.companion.getMode().isTaskMode()
                    ? net.bro.guardcompanion.entity.CompanionMode.HOSTILES : this.companion.getMode());
        }

        private static Map<Item, Item> buildLogToPlanks() {
            Map<Item, Item> map = new HashMap<>();
            map.put(Items.OAK_LOG, Items.OAK_PLANKS);
            map.put(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS);
            map.put(Items.BIRCH_LOG, Items.BIRCH_PLANKS);
            map.put(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS);
            map.put(Items.ACACIA_LOG, Items.ACACIA_PLANKS);
            map.put(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS);
            map.put(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS);
            map.put(Items.CHERRY_LOG, Items.CHERRY_PLANKS);
            return map;
        }
    }

    public static class CompanionFishGoal extends Goal {
        private static final int WATER_SEARCH_RADIUS = 10;
        private static final int CHEST_SEARCH_RADIUS = 12;
        private static final int FISH_BEFORE_DEPOSIT = 6;

        private final CompanionEntity companion;
        private final RandomSource random = RandomSource.create();

        private int fishCooldown = 60;
        @Nullable
        private BlockPos waterSpot;
        @Nullable
        private BlockPos chestSpot;
        private boolean warnedNoChest;

        public CompanionFishGoal(CompanionEntity companion) {
            this.companion = companion;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.companion.getMode() == CompanionMode.FISHER;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void stop() {
            this.companion.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.countFish() >= FISH_BEFORE_DEPOSIT || this.isBackpackNearlyFull()) {
                this.depositFish();
            } else {
                this.fish();
            }
        }

        private void fish() {
            if (this.waterSpot == null || !this.isWater(this.waterSpot)) {
                this.waterSpot = this.findWater();
                this.chestSpot = null;
            }
            if (this.waterSpot == null) {
                this.stayPut();
                return;
            }
            if (!isWithinReach(this.companion, this.waterSpot, 9.0D)) {
                if (this.companion.getNavigation().isDone()) {
                    navigateTo(this.companion, this.waterSpot, 1.0D);
                }
                return;
            }
            this.stayPut();
            this.companion.getLookControl().setLookAt(
                    this.waterSpot.getX() + 0.5D, this.waterSpot.getY() + 0.5D, this.waterSpot.getZ() + 0.5D);
            if (--this.fishCooldown > 0) {
                return;
            }
            this.fishCooldown = 80 + this.random.nextInt(80);
            this.companion.swing(InteractionHand.MAIN_HAND);
            this.companion.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.6F,
                    0.9F + this.random.nextFloat() * 0.2F);
            this.giveFish(this.randomFish());
        }

        private void stayPut() {
            this.companion.getNavigation().stop();
        }

        private void depositFish() {
            if (this.chestSpot == null || !this.isContainer(this.chestSpot)) {
                this.chestSpot = this.findChest();
                this.warnedNoChest = false;
            }
            if (this.chestSpot == null) {
                if (!this.warnedNoChest) {
                    this.warnedNoChest = true;
                    sendMessageToOwner(this.companion,
                            Component.translatable("message.guardcompanion.fisher.no_chest"), false);
                }
                return;
            }
            if (!isWithinReach(this.companion, this.chestSpot, 9.0D)) {
                if (this.companion.getNavigation().isDone()) {
                    navigateTo(this.companion, this.chestSpot, 1.1D);
                }
                return;
            }
            this.transferToChest();
        }

        private void transferToChest() {
            Level level = this.companion.level();
            BlockEntity blockEntity = level.getBlockEntity(this.chestSpot);
            if (blockEntity instanceof net.minecraft.world.Container container) {
                int deposited = 0;
                for (int i = 0; i < this.companion.getBackpack().size(); i++) {
                    ItemStack stack = this.companion.getBackpack().get(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    int before = stack.getCount();
                    ItemStack rest = this.transferTo(container, stack);
                    if (rest.isEmpty()) {
                        this.companion.getBackpack().set(i, ItemStack.EMPTY);
                        deposited += before;
                    } else {
                        deposited += before - rest.getCount();
                        this.companion.getBackpack().set(i, rest);
                    }
                }
                if (deposited > 0) {
                    this.companion.playSound(SoundEvents.ITEM_PICKUP, 0.7F, 1.2F);
                    sendMessageToOwner(this.companion,
                            Component.translatable("message.guardcompanion.fisher.deposited", deposited), false);
                }
            }
        }

        private ItemStack transferTo(net.minecraft.world.Container container, ItemStack stack) {
            ItemStack rest = stack.copy();
            for (int j = 0; j < container.getContainerSize() && !rest.isEmpty(); j++) {
                ItemStack target = container.getItem(j);
                if (target.isEmpty()) {
                    container.setItem(j, rest);
                    rest = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(target, rest)) {
                    int max = Math.min(container.getMaxStackSize(), target.getMaxStackSize());
                    int moved = Math.min(max - target.getCount(), rest.getCount());
                    if (moved > 0) {
                        target.grow(moved);
                        container.setItem(j, target);
                        rest.shrink(moved);
                    }
                }
            }
            return rest;
        }

        private ItemStack randomFish() {
            float roll = this.random.nextFloat();
            if (roll < 0.55F) {
                return new ItemStack(Items.COD);
            }
            if (roll < 0.9F) {
                return new ItemStack(Items.SALMON);
            }
            return new ItemStack(Items.TROPICAL_FISH);
        }

        private void giveFish(ItemStack fish) {
            if (!this.companion.addToBackpack(fish)) {
                this.companion.spawnAtLocation(fish);
            }
        }

        private int countFish() {
            int count = 0;
            for (ItemStack stack : this.companion.getBackpack()) {
                if (!stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.FISHES)) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private boolean isBackpackNearlyFull() {
            int free = 0;
            for (ItemStack stack : this.companion.getBackpack()) {
                if (stack.isEmpty()) {
                    free++;
                }
            }
            return free <= 1;
        }

        @Nullable
        private BlockPos findWater() {
            Level level = this.companion.level();
            BlockPos center = this.companion.blockPosition();
            for (BlockPos pos : BlockPos.randomInCube(this.random, 64, center, WATER_SEARCH_RADIUS)) {
                if (this.isWater(pos)) {
                    return pos.immutable();
                }
            }
            return null;
        }

        private boolean isWater(BlockPos pos) {
            return this.companion.level().getBlockState(pos).getFluidState()
                    .is(net.minecraft.world.level.material.Fluids.WATER);
        }

        @Nullable
        private BlockPos findChest() {
            Level level = this.companion.level();
            BlockPos center = this.companion.blockPosition();
            BlockPos min = center.offset(-CHEST_SEARCH_RADIUS, -3, -CHEST_SEARCH_RADIUS);
            BlockPos max = center.offset(CHEST_SEARCH_RADIUS, 3, CHEST_SEARCH_RADIUS);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof RandomizableContainerBlockEntity) {
                    return pos.immutable();
                }
            }
            return null;
        }

        private boolean isContainer(BlockPos pos) {
            return this.companion.level().getBlockEntity(pos) instanceof net.minecraft.world.Container;
        }
    }
}
