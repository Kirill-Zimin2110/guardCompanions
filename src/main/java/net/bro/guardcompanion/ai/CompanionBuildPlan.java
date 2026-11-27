package net.bro.guardcompanion.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class CompanionBuildPlan {
    public record Entry(BlockPos pos, BlockState state, Item item, int itemCost) {
    }

    private final List<Entry> entries;
    private final String label;
    private final int size;
    private final int stories;

    private CompanionBuildPlan(List<Entry> entries, String label, int size, int stories) {
        this.entries = List.copyOf(entries);
        this.label = label;
        this.size = size;
        this.stories = stories;
    }

    public List<Entry> entries() {
        return entries;
    }

    public String label() {
        return label;
    }

    public Map<Item, Integer> requiredItems() {
        Map<Item, Integer> required = new HashMap<>();
        for (Entry entry : entries) {
            required.merge(entry.item(), entry.itemCost(), Integer::sum);
        }
        return required;
    }

    public static CompanionBuildPlan house(BlockPos origin, Direction facing, int size, int stories, String label) {
        int half = size / 2;
        Direction right = facing.getClockWise();
        BlockPos frontCenter = origin.relative(facing, half);
        int centerX = 0;

        int wallHeight = 3;

        List<Entry> result = new ArrayList<>();
        Direction outward = facing;

        for (int story = 0; story < stories; story++) {
            int baseY = storyTop(story, wallHeight);
            boolean isTopStory = story == stories - 1;

            for (int lx = -half; lx <= half; lx++) {
                for (int lz = -half; lz <= half; lz++) {
                    BlockPos floorPos = localToWorld(origin, facing, right, lx, lz, baseY);
                    result.add(new Entry(floorPos, Blocks.OAK_PLANKS.defaultBlockState(), Items.OAK_PLANKS, 1));
                }
            }

            for (int y = 1; y <= wallHeight; y++) {
                for (int lx = -half; lx <= half; lx++) {
                    for (int lz = -half; lz <= half; lz++) {
                        boolean onXWall = Math.abs(lx) == half;
                        boolean onZWall = Math.abs(lz) == half;
                        if (!onXWall && !onZWall) {
                            continue;
                        }
                        boolean isDoor = lz == -half && lx == centerX && y == 1;
                        if (isDoor) {
                            BlockPos doorPos = localToWorld(origin, facing, right, lx, lz, baseY + y);
                            BlockState doorState = Blocks.OAK_DOOR.defaultBlockState()
                                    .setValue(DoorBlock.FACING, outward)
                                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                            result.add(new Entry(doorPos, doorState, Items.OAK_DOOR, 1));
                            continue;
                        }
                        if (lz == -half && lx == centerX && y == 2) {
                            continue;
                        }
                        boolean isWindow = y == 2
                                && ((onXWall && Math.abs(lz) >= half - 2 && Math.abs(lz) <= half - 1 && Math.abs(lx) == half)
                                || (onZWall && Math.abs(lx) >= half - 2 && Math.abs(lx) <= half - 1));
                        if (isWindow) {
                            continue;
                        }
                        boolean isCorner = onXWall && onZWall;
                        Block wallBlock = isCorner ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS;
                        Item wallItem = isCorner ? Items.COBBLESTONE : Items.OAK_PLANKS;
                        BlockPos wallPos = localToWorld(origin, facing, right, lx, lz, baseY + y);
                        result.add(new Entry(wallPos, wallBlock.defaultBlockState(), wallItem, 1));
                    }
                }
            }

            int ceilingY = baseY + wallHeight + 1;
            if (isTopStory) {
                for (int lx = -half; lx <= half; lx++) {
                    for (int lz = -half; lz <= half; lz++) {
                        boolean isRim = Math.abs(lx) == half || Math.abs(lz) == half;
                        Block roofBlock = isRim ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS;
                        Item roofItem = isRim ? Items.COBBLESTONE : Items.OAK_PLANKS;
                        BlockPos roofPos = localToWorld(origin, facing, right, lx, lz, ceilingY);
                        result.add(new Entry(roofPos, roofBlock.defaultBlockState(), roofItem, 1));
                    }
                }
            } else {
                for (int lx = -half; lx <= half; lx++) {
                    for (int lz = -half; lz <= half; lz++) {
                        if (lx == centerX && lz == -half + 1) {
                            continue;
                        }
                        BlockPos slabPos = localToWorld(origin, facing, right, lx, lz, ceilingY);
                        result.add(new Entry(slabPos, Blocks.OAK_PLANKS.defaultBlockState(), Items.OAK_PLANKS, 1));
                    }
                }
                for (int y = 1; y <= wallHeight + 1; y++) {
                    BlockPos ladderPos = localToWorld(origin, facing, right, centerX + 1, -half + 1, baseY + y);
                    BlockState ladderState = Blocks.LADDER.defaultBlockState()
                            .setValue(LadderBlock.FACING, outward);
                    result.add(new Entry(ladderPos, ladderState, Items.LADDER, 1));
                }
            }
        }

        result.sort(Comparator.comparingInt((Entry e) -> e.pos().getY())
                .thenComparingInt(e -> Math.abs(e.pos().getX() - frontCenter.getX())
                        + Math.abs(e.pos().getZ() - frontCenter.getZ())));
        return new CompanionBuildPlan(result, label, size, stories);
    }

    private static int storyTop(int story, int wallHeight) {
        return story * (wallHeight + 1);
    }

    private static BlockPos localToWorld(BlockPos origin, Direction facing, Direction right, int lx, int lz, int y) {
        return origin.relative(facing, lz).relative(right, lx).above(y);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Label", label);
        tag.putInt("Size", size);
        tag.putInt("Stories", stories);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("Pos", entry.pos().asLong());
            entryTag.put("State", NbtUtils.writeBlockState(entry.state()));
            entryTag.putString("Item", BuiltInRegistries.ITEM.getKey(entry.item()).toString());
            entryTag.putInt("Cost", entry.itemCost());
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }

    @Nullable
    public static CompanionBuildPlan load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("Entries", Tag.TAG_LIST)) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("Pos"));
            BlockState state = NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK),
                    entryTag.getCompound("State"));
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entryTag.getString("Item"))).orElse(Items.AIR);
            if (item == Items.AIR || state.isAir()) {
                continue;
            }
            entries.add(new Entry(pos, state, item, entryTag.getInt("Cost")));
        }
        if (entries.isEmpty()) {
            return null;
        }
        return new CompanionBuildPlan(entries, tag.getString("Label"), tag.getInt("Size"), tag.getInt("Stories"));
    }

    public record MaterialCount(int have, int required) {
    }

    public Optional<Entry> firstIncomplete(java.util.function.BiPredicate<BlockPos, BlockState> matches) {
        for (Entry entry : entries) {
            if (!matches.test(entry.pos(), entry.state())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
