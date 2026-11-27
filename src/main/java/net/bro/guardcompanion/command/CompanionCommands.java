package net.bro.guardcompanion.command;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.bro.guardcompanion.ai.CompanionBuildPlan;
import net.bro.guardcompanion.entity.CompanionEntity;
import net.bro.guardcompanion.entity.CompanionMode;
import net.bro.guardcompanion.registry.ModSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "guardcompanion")
public final class CompanionCommands {
    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)");
    private static final double FIND_RADIUS = 24.0D;

    private CompanionCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("компаньон")
                .then(Commands.literal("режим")
                        .then(Commands.argument("режим", StringArgumentType.word())
                                .suggests(CompanionCommands::suggestModes)
                                .executes(ctx -> setMode(ctx, StringArgumentType.getString(ctx, "режим")))))
                .then(Commands.literal("построй")
                        .then(Commands.argument("что_построить", StringArgumentType.greedyString())
                                .executes(CompanionCommands::executeBuild)))
                .then(Commands.literal("рыбачь").executes(ctx -> executeFish(ctx)))
                .then(Commands.literal("стоп").executes(ctx -> executeStop(ctx)))
                .executes(CompanionCommands::executeStatus));
    }

    private static CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> context,
                                                               SuggestionsBuilder builder) {
        for (String mode : new String[]{"враждебные", "мирные", "всё", "стоять"}) {
            builder.suggest(mode);
        }
        return builder.buildFuture();
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CompanionEntity companion = findCompanion(player);
        if (companion == null) {
            context.getSource().sendFailure(Component.translatable("message.guardcompanion.not_found"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.guardcompanion.status", companion.getName(),
                companion.getMode().displayName()), false);
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> context, String modeName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CompanionEntity companion = findCompanion(player);
        if (companion == null) {
            context.getSource().sendFailure(Component.translatable("message.guardcompanion.not_found"));
            return 0;
        }
        CompanionMode mode = parseMode(modeName.toLowerCase(Locale.ROOT));
        if (mode == null) {
            context.getSource().sendFailure(Component.translatable("message.guardcompanion.unknown_mode", modeName));
            return 0;
        }
        companion.setMode(mode);
        companion.playSound(ModSounds.COMPANION_COMMAND_ACCEPTED.get(), 1.0F, 1.0F);
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.guardcompanion.mode_set", mode.displayName()), false);
        return 1;
    }

    private static int executeBuild(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CompanionEntity companion = findCompanion(player);
        if (companion == null) {
            context.getSource().sendFailure(Component.translatable("message.guardcompanion.not_found"));
            return 0;
        }
        String order = StringArgumentType.getString(context, "что_построить");
        CompanionBuildOrder parsed = CompanionBuildOrder.parse(order);
        if (parsed == null) {
            context.getSource().sendFailure(Component.translatable("message.guardcompanion.builder.unknown_order", order));
            return 0;
        }

        Direction facing = player.getDirection();
        BlockPos origin = player.blockPosition().relative(facing, 3);
        CompanionBuildPlan plan = CompanionBuildPlan.house(origin, facing, parsed.size(), parsed.stories(), parsed.label());
        companion.setBuildPlan(plan);
        companion.setMode(CompanionMode.BUILDER);
        companion.playSound(ModSounds.COMPANION_COMMAND_ACCEPTED.get(), 1.0F, 1.0F);
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.guardcompanion.builder.accepted", parsed.label(),
                plan.entries().size()), false);
        return 1;
    }

    private static int executeFish(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CompanionEntity companion = findCompanion(player);
        if (companion == null) {
            context.getSource().sendFailure(Component.translatable("message.guardcompanion.not_found"));
            return 0;
        }
        companion.setMode(CompanionMode.FISHER);
        companion.playSound(ModSounds.COMPANION_COMMAND_ACCEPTED.get(), 1.0F, 1.0F);
        context.getSource().sendSuccess(() ->
                Component.translatable("message.guardcompanion.fisher.accepted"), false);
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CompanionEntity companion = findCompanion(player);
        if (companion == null) {
            context.getSource().sendFailure(Component.translatable("message.guardcompanion.not_found"));
            return 0;
        }
        companion.setBuildPlan(null);
        companion.setMode(CompanionMode.HOSTILES);
        companion.playSound(ModSounds.COMPANION_COMMAND_ACCEPTED.get(), 1.0F, 1.0F);
        context.getSource().sendSuccess(() ->
                Component.translatable("message.guardcompanion.stopped"), false);
        return 1;
    }

    public static CompanionEntity findCompanion(ServerPlayer player) {
        HitResult hit = player.pick(5.0D, 1.0F, false);
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof CompanionEntity companion
                && companion.isTame() && companion.isOwnedBy(player)) {
            return companion;
        }
        List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                CompanionEntity.class, player.getBoundingBox().inflate(FIND_RADIUS),
                companion -> companion.isTame() && companion.isOwnedBy(player));
        return companions.stream()
                .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                .orElse(null);
    }

    private static CompanionMode parseMode(String name) {
        return switch (name) {
            case "враждебные", "hostiles" -> CompanionMode.HOSTILES;
            case "мирные", "passives" -> CompanionMode.PASSIVES;
            case "всё", "все", "all" -> CompanionMode.ALL;
            case "стоять", "stay" -> CompanionMode.STAY;
            default -> null;
        };
    }

    public record CompanionBuildOrder(String label, int size, int stories) {
        public static CompanionBuildOrder parse(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (!lower.contains("построй") && !lower.contains("дом")) {
                return null;
            }
            int stories = lower.contains("двухэтаж") ? 2 : 1;
            int size = 9;
            Matcher matcher = SIZE_PATTERN.matcher(lower);
            if (matcher.find()) {
                int width = Integer.parseInt(matcher.group(1));
                int depth = Integer.parseInt(matcher.group(2));
                size = Math.max(width, depth);
            }
            size = Math.min(13, Math.max(5, size));
            if (size % 2 == 0) {
                size++;
            }
            String label = lower.contains("дом") ? "дом" : "постройка";
            return new CompanionBuildOrder(label, size, stories);
        }
    }
}
