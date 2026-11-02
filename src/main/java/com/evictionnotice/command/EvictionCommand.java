package com.evictionnotice.command;

import com.evictionnotice.EvictionNotice;
import com.evictionnotice.eviction.EvictionService;
import com.evictionnotice.zone.Zone;
import com.evictionnotice.zone.ZoneManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Predicate;

/**
 * {@code /eviction} commands (alias: {@code /evictionnotice}).
 *
 * <p>Zones are keyed by the player's UUID and each subcommand only works on the caller's own
 * zones. Every subcommand needs a player: the console and command blocks only get an error,
 * unless run through {@code /execute as <player>}.
 */
public final class EvictionCommand {

    private static final Predicate<CommandSourceStack> IS_OPERATOR =
            Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private static final int MAX_RADIUS = 256;

    private static final ChatFormatting BRAND = ChatFormatting.GOLD;
    private static final ChatFormatting OK = ChatFormatting.GREEN;
    private static final ChatFormatting INFO = ChatFormatting.GRAY;
    private static final ChatFormatting VALUE = ChatFormatting.WHITE;
    private static final ChatFormatting NAME = ChatFormatting.AQUA;
    private static final ChatFormatting WARN = ChatFormatting.YELLOW;

    private EvictionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("eviction")
                .requires(src -> src.isPlayer() || IS_OPERATOR.test(src))
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                        .executes(ctx -> set(ctx, false))
                                        .then(Commands.argument("yMin", IntegerArgumentType.integer())
                                                .then(Commands.argument("yMax", IntegerArgumentType.integer())
                                                        .executes(ctx -> set(ctx, true)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(EvictionCommand::remove)))
                .then(Commands.literal("list")
                        .executes(EvictionCommand::list))
                .then(Commands.literal("here")
                        .executes(EvictionCommand::here)));

        dispatcher.register(Commands.literal("evictionnotice")
                .requires(src -> src.isPlayer() || IS_OPERATOR.test(src))
                .redirect(root));
    }

    private static MutableComponent prefix() {
        return Component.literal("» ").withStyle(BRAND);
    }

    private static MutableComponent value(String v) {
        return Component.literal(v).withStyle(VALUE);
    }

    private static void pass(CommandSourceStack src, boolean toOps, MutableComponent body) {
        src.sendSuccess(() -> prefix().append(body), toOps);
    }

    private static void fail(CommandSourceStack src, String message) {
        src.sendFailure(prefix().append(Component.literal(message).withStyle(ChatFormatting.RED)));
    }

    private static ZoneManager store(CommandSourceStack src, ServerPlayer player, String needs) {
        if (player == null) {
            fail(src, needs);
            return null;
        }
        ZoneManager zm = EvictionNotice.zones();
        if (zm == null) {
            fail(src, "Zone store is not ready yet.");
        }
        return zm;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean withY) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        ZoneManager zm = store(src, player, "Only players can define a zone (it is centered on you).");
        if (zm == null) {
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        ServerLevel level = src.getLevel();
        BlockPos pos = player.blockPosition();

        int yMin;
        int yMax;
        if (withY) {
            yMin = IntegerArgumentType.getInteger(ctx, "yMin");
            yMax = IntegerArgumentType.getInteger(ctx, "yMax");
            if (yMin > yMax) {
                int tmp = yMin;
                yMin = yMax;
                yMax = tmp;
            }
        } else {
            yMin = level.getMinY();
            yMax = level.getMaxY();
        }

        Zone zone = new Zone(player.getUUID(), name, level.dimension().identifier().toString(),
                pos.getX(), pos.getY(), pos.getZ(), radius, yMin, yMax);
        if (!zm.addZone(zone)) {
            fail(src, "You already have a zone named '" + name + "'. Remove it first or pick another name.");
            return 0;
        }
        EvictionService.releaseExistingClaims(src.getServer());

        String dim = level.dimension().identifier().toString();
        pass(src, true, Component.literal("Zone ").withStyle(OK)
                .append(Component.literal(name).withStyle(NAME))
                .append(Component.literal(" saved.").withStyle(OK)));

        pass(src, false, Component.literal("").withStyle(INFO)
                .append(Component.literal("  radius ").withStyle(INFO)).append(value(String.valueOf(radius)))
                .append(Component.literal("  •  center ").withStyle(INFO))
                .append(value(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()))
                .append(Component.literal("  •  Y ").withStyle(INFO))
                .append(value(yMin + " to " + yMax))
                .append(Component.literal("  •  ").withStyle(INFO)).append(value(dim)));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        ZoneManager zm = store(src, player, "Only players own zones.");
        if (zm == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (zm.removeZone(player.getUUID(), name)) {
            pass(src, true, Component.literal("Removed zone ").withStyle(OK)
                    .append(Component.literal(name).withStyle(NAME))
                    .append(Component.literal(".").withStyle(OK)));
            return 1;
        }
        fail(src, "You have no zone named '" + name + "'.");
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        ZoneManager zm = store(src, player, "Only players own zones.");
        if (zm == null) {
            return 0;
        }
        List<Zone> zones = zm.listZones(player.getUUID());
        if (zones.isEmpty()) {
            pass(src, false, Component.literal("You have no villager zones.").withStyle(INFO));
            return 0;
        }
        pass(src, false, Component.literal("Your villager zones ").withStyle(BRAND)
                .append(Component.literal("(" + zones.size() + ")").withStyle(VALUE)));
        for (Zone z : zones) {
            src.sendSuccess(() -> Component.literal("  • ").withStyle(BRAND)
                    .append(Component.literal(z.name()).withStyle(NAME))
                    .append(Component.literal("  r").withStyle(INFO)).append(value(String.valueOf(z.radius())))
                    .append(Component.literal(" @ ").withStyle(INFO))
                    .append(value(z.centerX() + "," + z.centerY() + "," + z.centerZ()))
                    .append(Component.literal("  Y ").withStyle(INFO)).append(value(z.yMin() + " to " + z.yMax()))
                    .append(Component.literal("  ").withStyle(INFO)).append(value(z.dimensionId())), false);
        }
        return zones.size();
    }

    private static int here(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        ZoneManager zm = store(src, player, "Only players have a position to check.");
        if (zm == null) {
            return 0;
        }
        List<String> names = zm.zoneNamesContaining(src.getLevel().dimension(), player.blockPosition());
        if (names.isEmpty()) {
            pass(src, false, Component.literal("You are ").withStyle(INFO)
                    .append(Component.literal("not").withStyle(OK))
                    .append(Component.literal(" inside any villager zone.").withStyle(INFO)));
            return 0;
        }
        pass(src, false, Component.literal("You are inside: ").withStyle(WARN)
                .append(Component.literal(String.join(", ", names)).withStyle(NAME)));
        return 1;
    }
}
