package com.pigapl.warengine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pigapl.warengine.kit.KitStorage;
import com.pigapl.warengine.network.KitNetworking;
import com.pigapl.warengine.network.SquadNetworking;
import com.pigapl.warengine.squad.SquadService;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * {@code /squad} command tree - the console equivalent of the squad picker, for testing without it.
 * Squads sit between team and kit. See {@code docs/squads.md} for the listing.
 *
 * <p>Validation lives in {@link SquadService} and the refresh in
 * {@link SquadNetworking#refreshAfterSquadChange}, both shared with the payload handlers so the two
 * paths can never enforce or announce different things.</p>
 */
public final class SquadCommand {

    private static final SuggestionProvider<CommandSourceStack> SQUAD_IDS = (ctx, builder) -> {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return builder.buildFuture();
        }
        String team = TeamService.getTeam(ctx.getSource().getServer(), player);
        return SharedSuggestionProvider.suggest(
                SquadService.squadsFor(ctx.getSource().getServer(), team).stream().map(r -> r.id), builder);
    };

    private SquadCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("squad")
                .then(Commands.literal("list")
                        .executes(SquadCommand::list))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("limit", IntegerArgumentType.integer(
                                        SquadService.MIN_LIMIT, SquadService.MAX_LIMIT))
                                        .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "limit"))))))
                .then(Commands.literal("join")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(SQUAD_IDS)
                                .executes(ctx -> join(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("leave")
                        .executes(SquadCommand::leave))
                .then(Commands.literal("reserve")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ANY_SQUAD_IDS)
                                .then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                .executes(ctx -> reserve(ctx,
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "kit"),
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))));
    }

    private static final SuggestionProvider<CommandSourceStack> ANY_SQUAD_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    WarState.get(ctx.getSource().getServer()).squads().keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> KIT_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(KitStorage.ids(), builder);

    private static int reserve(CommandContext<CommandSourceStack> ctx, String squadId, String kit, int count) {
        CommandSourceStack src = ctx.getSource();
        SquadService.UpdateResult result = SquadService.setReservation(src.getServer(), squadId, kit, count);
        switch (result) {
            case UNKNOWN_SQUAD -> {
                src.sendFailure(Component.literal("No such squad: " + squadId + "."));
                return 0;
            }
            case NO_BUDGET -> {
                src.sendFailure(Component.literal(
                        "That squad's team has no budget for '" + kit + "'. Set one: /kit budget <team> "
                        + kit + " <n>."));
                return 0;
            }
            case BUDGET_EXCEEDED -> {
                src.sendFailure(Component.literal(
                        "Not enough of '" + kit + "' left in the team budget for that."));
                return 0;
            }
            case BAD_LIMIT -> {
                src.sendFailure(Component.literal("Count can't be negative."));
                return 0;
            }
            case OK -> { }
            default -> {
                src.sendFailure(Component.literal("Couldn't set that reservation."));
                return 0;
            }
        }
        WarState.SquadRecord squad = WarState.get(src.getServer()).getSquadRecord(squadId);
        KitNetworking.sendCatalogToSquad(src.getServer(), squadId);
        src.sendSuccess(() -> Component.literal(
                "Squad '" + squadId + "' now reserves " + count + " x " + KitStorage.normalizeId(kit)
                + (squad == null ? "" : " (" + squad.name + ")") + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        String team = TeamService.getTeam(src.getServer(), player);
        if (team == null) {
            src.sendFailure(Component.literal("Join a team first: /team join <id>."));
            return 0;
        }
        var squads = SquadService.squadsFor(src.getServer(), team);
        if (squads.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "No squads yet on '" + team + "'. Create one: /squad create <name> <limit>.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        String mine = SquadService.getSquadId(src.getServer(), player);
        for (var squad : squads) {
            int members = SquadService.memberCount(src.getServer(), squad.id);
            boolean isMine = squad.id.equals(mine);
            String line = "  " + squad.id + "  \"" + squad.name + "\"  " + members + "/" + squad.limit
                    + (isMine ? "  (yours)" : "");
            src.sendSuccess(() -> Component.literal(line)
                    .withStyle(isMine ? ChatFormatting.GREEN : ChatFormatting.WHITE), false);
        }
        return squads.size();
    }

    private static int create(CommandContext<CommandSourceStack> ctx, String name, int limit) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        String oldSquad = SquadService.getSquadId(src.getServer(), player);
        // No reservations - that flow is UI-only; add them afterwards with /squad reserve.
        SquadService.CreateResult result = SquadService.create(player, name, limit, Map.of());
        switch (result) {
            case NO_TEAM -> {
                src.sendFailure(Component.literal("Join a team first: /team join <id>."));
                return 0;
            }
            case BLANK_NAME -> {
                src.sendFailure(Component.literal("Squad name can't be blank."));
                return 0;
            }
            case NAME_TOO_LONG -> {
                src.sendFailure(Component.literal(
                        "Squad name is too long (max " + SquadService.NAME_MAX_LENGTH + " characters)."));
                return 0;
            }
            case BAD_LIMIT -> {
                src.sendFailure(Component.literal("Squad limit must be between "
                        + SquadService.MIN_LIMIT + " and " + SquadService.MAX_LIMIT + "."));
                return 0;
            }
            case BUDGET_EXCEEDED -> {
                src.sendFailure(Component.literal("A kit reservation exceeds the team budget."));
                return 0;
            }
            case OK -> { }
        }
        String newSquad = SquadService.getSquadId(src.getServer(), player);
        SquadNetworking.refreshAfterSquadChange(player, oldSquad, newSquad);
        src.sendSuccess(() -> Component.literal(
                "Squad '" + name + "' created (" + newSquad + ", limit " + limit + ") - you're in it.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        String oldSquad = SquadService.getSquadId(src.getServer(), player);
        SquadService.JoinResult result = SquadService.join(player, id);
        switch (result) {
            case UNKNOWN_SQUAD -> {
                src.sendFailure(Component.literal("No such squad: " + id + ". Try /squad list."));
                return 0;
            }
            case WRONG_TEAM -> {
                src.sendFailure(Component.literal("That squad isn't on your team."));
                return 0;
            }
            case FULL -> {
                src.sendFailure(Component.literal("Squad '" + id + "' is full."));
                return 0;
            }
            case OK -> { }
        }
        SquadNetworking.refreshAfterSquadChange(player, oldSquad, id);
        src.sendSuccess(() -> Component.literal("Joined squad '" + id + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        String oldSquad = SquadService.leave(player);
        if (oldSquad == null) {
            src.sendFailure(Component.literal("You're not in a squad."));
            return 0;
        }
        SquadNetworking.refreshAfterSquadChange(player, oldSquad, null);
        src.sendSuccess(() -> Component.literal("Left your squad.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
