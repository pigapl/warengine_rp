package com.pigapl.warengine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pigapl.warengine.base.BaseService;
import com.pigapl.warengine.network.ClientboundOpenAdminScreenPayload;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

public final class WarStateCommand {

    private WarStateCommand() {}

    private static final SuggestionProvider<CommandSourceStack> TEAM_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(TeamService.ids(ctx.getSource().getServer()), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("warstate")
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .executes(WarStateCommand::admin))
                .then(Commands.literal("base")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(ctx -> setBase(ctx, StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(ctx -> clearBase(ctx, StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(ctx -> tpToBase(ctx, StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("tpall").executes(WarStateCommand::tpAll))
                        .then(Commands.literal("list").executes(WarStateCommand::listBases))));
    }

    private static int admin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player - it opens a screen on your client."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new ClientboundOpenAdminScreenPayload());
        return 1;
    }

    private static int setBase(CommandContext<CommandSourceStack> ctx, String team) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player - the base is set where you stand."));
            return 0;
        }
        if (TeamService.ids(src.getServer()).stream().noneMatch(id -> id.equalsIgnoreCase(team))) {
            src.sendFailure(Component.literal("No such team: " + team));
            return 0;
        }
        BaseService.set(src.getServer(), team, player);
        src.sendSuccess(() -> Component.literal("Base for '" + team + "' set to "
                + BaseService.describe(BaseService.of(src.getServer(), team)))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearBase(CommandContext<CommandSourceStack> ctx, String team) {
        CommandSourceStack src = ctx.getSource();
        if (!BaseService.clear(src.getServer(), team)) {
            src.sendFailure(Component.literal("Team '" + team + "' has no base set."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Base for '" + team + "' cleared - that team can pick "
                + "kits anywhere again.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int tpToBase(CommandContext<CommandSourceStack> ctx, String team) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        WarState.TeamBase base = BaseService.of(src.getServer(), team);
        if (base == null) {
            src.sendFailure(Component.literal("Team '" + team + "' has no base set."));
            return 0;
        }
        if (!BaseService.teleportTo(player, base)) {
            src.sendFailure(Component.literal("That base's dimension (" + base.dim + ") is not loaded."));
            return 0;
        }
        return 1;
    }

    private static int tpAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int moved = BaseService.teleportAllToBases(src.getServer());
        src.sendSuccess(() -> Component.literal("Sent " + moved + " player(s) to their team's base.")
                .withStyle(ChatFormatting.GREEN), true);
        return moved;
    }

    private static int listBases(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Map<String, WarState.TeamBase> bases = WarState.get(src.getServer()).bases();
        if (bases.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No team bases set. Stand where you want one and run "
                    + "/warstate base set <team>.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        bases.forEach((team, base) -> src.sendSuccess(() -> Component.literal(
                team + "  -  " + BaseService.describe(base) + "  (" + base.dim + ")"), false));
        return bases.size();
    }
}
