package com.pigapl.warengine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.round.RoundService;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /war} command tree - the round module's control surface.
 *
 * <pre>
 *   /war start [minutes]      (op)  start a war (default {@code round.defaultDurationMinutes})
 *   /war end                 (op)  end it now and announce the result
 *   /war status                    time left + each team's tickets + zone
 *   /war setzone [radius]     (op)  put the marker circle at your feet (default {@code round.zoneRadius})
 *   /war clearzone           (op)  remove the marker circle
 * </pre>
 *
 * <p>State is held in {@link WarState}; behaviour is in {@link RoundService}.</p>
 */
public final class WarCommand {

    private WarCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("war")
                .then(Commands.literal("start")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> start(ctx, WarConfig.ROUND_DEFAULT_MINUTES.get()))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                                .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))))
                .then(Commands.literal("end")
                        .requires(src -> src.hasPermission(2))
                        .executes(WarCommand::end))
                .then(Commands.literal("status")
                        .executes(WarCommand::status))
                .then(Commands.literal("setzone")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> setzone(ctx, WarConfig.ZONE_RADIUS.get()))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 128.0))
                                .executes(ctx -> setzone(ctx, DoubleArgumentType.getDouble(ctx, "radius")))))
                .then(Commands.literal("clearzone")
                        .requires(src -> src.hasPermission(2))
                        .executes(WarCommand::clearzone)));
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int minutes) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        WarState st = WarState.get(server);
        if (st.roundActive()) {
            src.sendFailure(Component.literal("A war is already running - /war end it first."));
            return 0;
        }
        if (TeamService.ids(server).isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "No teams exist - the war will run with no tickets. Create teams with /team add.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        RoundService.start(server, minutes);
        src.sendSuccess(() -> Component.literal("War started for " + minutes + " minute(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        WarState st = WarState.get(src.getServer());
        if (!st.roundActive()) {
            src.sendFailure(Component.literal("No war is running."));
            return 0;
        }
        RoundService.end(src.getServer(), "ENDED BY ADMIN");
        src.sendSuccess(() -> Component.literal("War ended.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        WarState st = WarState.get(src.getServer());
        if (st.roundActive()) {
            src.sendSuccess(() -> Component.literal(
                    "War running - " + RoundService.timeLeftString(st) + " left   "
                            + RoundService.ticketSummary(st)).withStyle(ChatFormatting.GOLD), false);
        } else {
            src.sendSuccess(() -> Component.literal("No war running.").withStyle(ChatFormatting.GRAY), false);
        }
        if (st.hasZone()) {
            src.sendSuccess(() -> Component.literal(
                    "Zone: " + st.zoneX() + " " + st.zoneY() + " " + st.zoneZ()
                            + " r=" + st.zoneRadius() + " (" + st.zoneDim() + ")")
                    .withStyle(ChatFormatting.AQUA), false);
        } else {
            src.sendSuccess(() -> Component.literal("Zone: unset - /war setzone")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setzone(CommandContext<CommandSourceStack> ctx, double radius) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player - it uses your position."));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        String dim = player.level().dimension().location().toString();
        WarState.get(src.getServer()).setZone(dim, pos.getX(), pos.getY(), pos.getZ(), radius);
        src.sendSuccess(() -> Component.literal(
                "Zone set at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + " with a " + radius + "-block radius.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearzone(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        WarState st = WarState.get(src.getServer());
        if (!st.hasZone()) {
            src.sendFailure(Component.literal("No zone is set."));
            return 0;
        }
        st.clearZone();
        src.sendSuccess(() -> Component.literal("Zone cleared.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
