package com.pigapl.warengine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.round.RoundService;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.state.WarState.CapturePoint;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;

/**
 * {@code /war} command tree - see {@code docs/war-rounds.md} for the listing. State is in
 * {@link WarState}, behaviour in {@link RoundService}.
 *
 * <p>Everything lives under the one {@code war} root on purpose: Brigadier MERGES a duplicate root
 * literal rather than rejecting it, silently overwriting same-named children.</p>
 */
public final class WarCommand {

    private WarCommand() {}

    private static final SuggestionProvider<CommandSourceStack> POINT_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    WarState.get(ctx.getSource().getServer()).points().stream().map(p -> p.id).toList(),
                    builder);

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
                .then(Commands.literal("point")
                        .then(Commands.literal("list")
                                .executes(WarCommand::listPoints))
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> addPoint(ctx, WarConfig.ZONE_RADIUS.get()))
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 128.0))
                                                .executes(ctx -> addPoint(ctx,
                                                        DoubleArgumentType.getDouble(ctx, "radius"))))))
                        .then(Commands.literal("remove")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(POINT_IDS)
                                        .executes(WarCommand::removePoint)))
                        .then(Commands.literal("clear")
                                .requires(src -> src.hasPermission(2))
                                .executes(WarCommand::clearPoints))
                        .then(Commands.literal("tp")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(POINT_IDS)
                                        .executes(WarCommand::tpToPoint)))));
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
        if (!st.hasPoints()) {
            src.sendSuccess(() -> Component.literal(
                    "No capture points exist - nobody can score. Add some with /war point add <id>.")
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
        return listPoints(ctx);
    }

    private static int listPoints(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<CapturePoint> points = WarState.get(src.getServer()).points();
        if (points.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No capture points - /war point add <id>")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (CapturePoint p : points) {
            String owner = p.owner == null ? "neutral" : p.owner;
            String capturing = p.capturingTeam == null ? ""
                    : "  <- " + p.capturingTeam + " " + p.progress + "/" + WarConfig.CAPTURE_SECONDS.get();
            src.sendSuccess(() -> Component.literal(
                    p.id + ": " + p.x + " " + p.y + " " + p.z + " r=" + p.radius
                            + "  [" + owner + "]" + capturing).withStyle(ChatFormatting.AQUA), false);
        }
        return points.size();
    }

    private static int addPoint(CommandContext<CommandSourceStack> ctx, double radius) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player - it uses your position."));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        if (id.length() > 8) {
            src.sendFailure(Component.literal("Point ids are 8 characters or fewer - they go on the HUD."));
            return 0;
        }
        WarState st = WarState.get(src.getServer());
        boolean replaced = st.getPoint(id) != null;
        BlockPos pos = player.blockPosition();
        String dim = player.level().dimension().location().toString();
        CapturePoint point = st.addPoint(id, dim, pos.getX(), pos.getY(), pos.getZ(), radius);
        RoundService.broadcastPoints(src.getServer(), st, true);
        src.sendSuccess(() -> Component.literal(
                (replaced ? "Point " + point.id + " moved to " : "Point " + point.id + " created at ")
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + " with a " + radius + "-block radius.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removePoint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        WarState st = WarState.get(src.getServer());
        if (!st.removePoint(id)) {
            src.sendFailure(Component.literal("No capture point called " + id + "."));
            return 0;
        }
        RoundService.broadcastPoints(src.getServer(), st, true);
        src.sendSuccess(() -> Component.literal("Point " + id + " removed.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearPoints(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        WarState st = WarState.get(src.getServer());
        if (!st.hasPoints()) {
            src.sendFailure(Component.literal("No capture points are set."));
            return 0;
        }
        int n = st.clearPoints();
        RoundService.broadcastPoints(src.getServer(), st, true);
        src.sendSuccess(() -> Component.literal(n + " capture point(s) removed.")
                .withStyle(ChatFormatting.GREEN), true);
        return n;
    }

    private static int tpToPoint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        CapturePoint point = WarState.get(src.getServer()).getPoint(id);
        if (point == null) {
            src.sendFailure(Component.literal("No capture point called " + id + "."));
            return 0;
        }
        ServerLevel level = RoundService.levelOf(src.getServer(), point);
        if (level == null) {
            src.sendFailure(Component.literal("Point " + point.id + " is in an unloaded dimension ("
                    + point.dim + ")."));
            return 0;
        }
        player.teleportTo(level, point.x + 0.5, point.y + 1.0, point.z + 0.5, Set.of(),
                player.getYRot(), player.getXRot());
        src.sendSuccess(() -> Component.literal("Teleported to point " + point.id + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
