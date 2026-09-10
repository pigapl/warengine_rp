package com.pigapl.warengine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.WarEngine;
import com.pigapl.warengine.kit.KitDefinition;
import com.pigapl.warengine.kit.KitService;
import com.pigapl.warengine.kit.KitStorage;
import com.pigapl.warengine.kit.ScarceItems;
import com.pigapl.warengine.kit.TeamKits;
import com.pigapl.warengine.network.KitNetworking;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class KitCommand {

    private static final SuggestionProvider<CommandSourceStack> KIT_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(KitStorage.ids(), builder);

    /**
     * Deliberately NOT {@link #KIT_IDS}: the public node's suggestions are the one place a non-op
     * could otherwise tab-complete every admin-only kit in the library.
     */
    private static final SuggestionProvider<CommandSourceStack> SELF_APPLY_KIT_IDS = (ctx, builder) -> {
        CommandSourceStack src = ctx.getSource();
        if (src.hasPermission(2)) {
            return SharedSuggestionProvider.suggest(KitStorage.ids(), builder);
        }
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            return builder.buildFuture();
        }
        String team = TeamService.getTeam(src.getServer(), player);
        return SharedSuggestionProvider.suggest(TeamKits.kitsFor(team), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> TEAM_IDS = (ctx, builder) -> {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add(TeamKits.ALL_TEAMS);
        options.addAll(TeamService.ids(ctx.getSource().getServer()));
        options.addAll(TeamKits.all().keySet());
        return SharedSuggestionProvider.suggest(options, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> COLORS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ChatFormatting.getNames(true, false).stream().toList(), builder);

    private KitCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kit")
                .then(Commands.literal("list")
                        .executes(KitCommand::list))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(KitCommand::reload))
                .then(Commands.literal("save")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "id"), null))
                                .then(Commands.argument("team", StringArgumentType.word()).suggests(TEAM_IDS)
                                        .executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "team"))))))
                .then(Commands.literal("name")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(KIT_IDS)
                                .then(Commands.argument("display", StringArgumentType.greedyString())
                                        .executes(ctx -> setName(ctx, StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "display"))))))
                .then(Commands.literal("limit")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(KIT_IDS)
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> setLimit(ctx, StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("budget")
                        .requires(src -> src.hasPermission(2))
                        .executes(KitCommand::budgetList)
                        .then(Commands.argument("team", StringArgumentType.word()).suggests(TEAM_IDS)
                                .then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setBudget(ctx,
                                                        StringArgumentType.getString(ctx, "team"),
                                                        StringArgumentType.getString(ctx, "kit"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("teams")
                        .requires(src -> src.hasPermission(2))
                        .executes(KitCommand::teams)
                        .then(Commands.literal("restore")
                                .requires(src -> src.hasPermission(2))
                                .executes(KitCommand::teamsRestore))
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> teamsAdd(ctx, StringArgumentType.getString(ctx, "team"), null))
                                        .then(Commands.argument("color", StringArgumentType.word()).suggests(COLORS)
                                                .executes(ctx -> teamsAdd(ctx, StringArgumentType.getString(ctx, "team"),
                                                        StringArgumentType.getString(ctx, "color"))))))
                        .then(Commands.literal("remove")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("team", StringArgumentType.word()).suggests(TEAM_IDS)
                                        .executes(ctx -> teamsRemove(ctx, StringArgumentType.getString(ctx, "team"))))))
                .then(Commands.literal("assign")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(KIT_IDS)
                                .then(Commands.argument("team", StringArgumentType.word()).suggests(TEAM_IDS)
                                        .executes(ctx -> assign(ctx, StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "team"))))))
                .then(Commands.literal("unassign")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(KIT_IDS)
                                .then(Commands.argument("team", StringArgumentType.word()).suggests(TEAM_IDS)
                                        .executes(ctx -> unassign(ctx, StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "team"))))))
                .then(Commands.literal("delete")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(KIT_IDS)
                                .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(KIT_IDS)
                                        .executes(ctx -> giveOthers(ctx, StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("scarce")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("setscarce")
                                .executes(KitCommand::scarceSet))
                        .then(Commands.literal("unsetscarce")
                                .executes(KitCommand::scarceUnset))
                        .then(Commands.literal("list")
                                .executes(KitCommand::scarceList))
                        .then(Commands.literal("sweep")
                                .executes(KitCommand::scarceSweep)))
                .then(Commands.literal("check")
                        .executes(KitCommand::check))
                .then(Commands.literal("resupply")
                        .requires(src -> src.hasPermission(2))
                        .executes(KitCommand::resupply))
                .then(Commands.literal("testcountdown")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> testCountdown(ctx, 5))
                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 60))
                                .executes(ctx -> testCountdown(ctx,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.argument("class", StringArgumentType.word()).suggests(SELF_APPLY_KIT_IDS)
                        .executes(ctx -> applySelf(ctx, StringArgumentType.getString(ctx, "class")))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.hasPermission(2)) {
            var ids = KitStorage.ids();
            if (ids.isEmpty()) {
                src.sendSuccess(() -> Component.literal("No kits defined yet. Use /kit save <id>.")
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                src.sendSuccess(() -> Component.literal(
                        "Kits (" + ids.size() + "): " + String.join(", ", ids)), false);
            }
            return ids.size();
        }

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
        List<String> allowed = TeamKits.kitsFor(team);
        if (allowed.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Team '" + team + "' has no kits yet - ask an admin.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal(
                    "Your kits (" + allowed.size() + "): " + String.join(", ", allowed)), false);
        }
        return allowed.size();
    }

    private static int teams(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Map<String, TeamKits.TeamDef> saved = TeamKits.all();
        if (saved.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "No teams saved yet. Use /kit teams add <team> [color].").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        List<String> live = TeamService.ids(src.getServer());
        int missing = 0;
        for (TeamKits.TeamDef def : saved.values()) {
            boolean isWildcard = def.id().equals(TeamKits.ALL_TEAMS);
            boolean exists = isWildcard || live.stream().anyMatch(t -> t.equalsIgnoreCase(def.id()));
            if (!exists) {
                missing++;
            }
            StringBuilder sb = new StringBuilder("  ").append(def.id());
            if (def.color() != null) {
                sb.append(" [").append(def.color()).append(']');
            }
            if (def.displayName() != null) {
                sb.append(" \"").append(def.displayName()).append('"');
            }
            sb.append(": ").append(def.kits().isEmpty() ? "(no kits)" : String.join(", ", def.kits()));
            if (!exists) {
                sb.append("  - NOT IN THIS WORLD");
            }
            String line = sb.toString();
            src.sendSuccess(() -> Component.literal(line)
                    .withStyle(exists ? ChatFormatting.WHITE : ChatFormatting.RED), false);
        }
        if (missing > 0) {
            int count = missing;
            src.sendSuccess(() -> Component.literal(
                    count + " saved team(s) missing here - run /kit teams restore.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return saved.size();
    }

    private static int teamsRestore(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int created = TeamKits.restore(src.getServer());
        if (created == 0) {
            src.sendSuccess(() -> Component.literal("Nothing to restore - all saved teams already exist.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal("Restored " + created + " team(s) from saved setup.")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return created;
    }

    private static int teamsAdd(CommandContext<CommandSourceStack> ctx, String team, String color) {
        CommandSourceStack src = ctx.getSource();
        try {
            TeamKits.TeamResult result = TeamKits.addTeam(src.getServer(), team, color);
            if (result == TeamKits.TeamResult.NOT_A_TEAM) {
                src.sendFailure(Component.literal(
                        "\"*\" is not a real team - use /kit assign <id> * to share a kit with every team."));
                return 0;
            }
            if (result == TeamKits.TeamResult.BAD_COLOR) {
                src.sendFailure(Component.literal("Not a color: " + color
                        + ". Use one of the 16 vanilla colors, e.g. dark_purple."));
                return 0;
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save team setup: " + e.getMessage()));
            return 0;
        }
        String norm = KitStorage.normalizeId(team);
        src.sendSuccess(() -> Component.literal("Team '" + norm + "' created and saved"
                + (color == null ? "." : " with color " + color + "."))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int teamsRemove(CommandContext<CommandSourceStack> ctx, String team) {
        CommandSourceStack src = ctx.getSource();
        try {
            if (TeamKits.removeTeam(src.getServer(), team) == TeamKits.TeamResult.NOT_FOUND) {
                src.sendFailure(Component.literal("No such team: " + team));
                return 0;
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save team setup: " + e.getMessage()));
            return 0;
        }
        String norm = KitStorage.normalizeId(team);
        src.sendSuccess(() -> Component.literal("Team '" + norm + "' deleted and forgotten.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setName(CommandContext<CommandSourceStack> ctx, String id, String display) {
        CommandSourceStack src = ctx.getSource();
        String norm = KitStorage.normalizeId(id);
        KitDefinition kit = KitStorage.get(norm).orElse(null);
        if (kit == null) {
            src.sendFailure(Component.literal("No such kit: " + id + ". Try /kit list."));
            return 0;
        }
        try {
            KitStorage.save(norm, kit.withDisplayName(display), src.getServer());
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save kit: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Kit '" + norm + "' now shows as \"" + display + "\".")
                .withStyle(ChatFormatting.GREEN), true);
        resendCatalogForKit(src, norm);
        return 1;
    }

    private static int setLimit(CommandContext<CommandSourceStack> ctx, String id, int count) {
        CommandSourceStack src = ctx.getSource();
        String norm = KitStorage.normalizeId(id);
        KitDefinition kit = KitStorage.get(norm).orElse(null);
        if (kit == null) {
            src.sendFailure(Component.literal("No such kit: " + id + ". Try /kit list."));
            return 0;
        }
        try {
            KitStorage.save(norm, kit.withLimit(count), src.getServer());
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save kit: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(count <= 0
                ? "Kit '" + norm + "' is now unlimited."
                : "Kit '" + norm + "' is now limited to " + count + " per team.")
                .withStyle(ChatFormatting.GREEN), true);
        resendCatalogForKit(src, norm);
        return 1;
    }

    private static int setBudget(CommandContext<CommandSourceStack> ctx, String team, String kit, int count) {
        CommandSourceStack src = ctx.getSource();
        String normTeam = KitStorage.normalizeId(team);
        String normKit = KitStorage.normalizeId(kit);
        if (normTeam.equals(TeamKits.ALL_TEAMS)) {
            src.sendFailure(Component.literal(
                    "Budgets are per real team - set one on each team that fields the kit."));
            return 0;
        }
        if (KitStorage.get(normKit).isEmpty()) {
            src.sendFailure(Component.literal("No such kit: " + normKit + ". Try /kit list."));
            return 0;
        }
        try {
            TeamKits.setBudget(normTeam, normKit, count);
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save team setup: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(count <= 0
                ? "Team '" + normTeam + "' budget for '" + normKit + "' cleared - it's a normal per-squad kit again."
                : "Team '" + normTeam + "' budget for '" + normKit + "' set to " + count
                        + ". Squads reserve from it at creation, or with /squad reserve.")
                .withStyle(ChatFormatting.GREEN), true);
        resendCatalogForKit(src, normKit);
        return 1;
    }

    private static int budgetList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        boolean[] any = {false};
        for (String team : TeamService.ids(server)) {
            Map<String, Integer> budgets = TeamKits.budgetsOf(team);
            if (budgets.isEmpty()) {
                continue;
            }
            any[0] = true;
            src.sendSuccess(() -> Component.literal(team + ":").withStyle(ChatFormatting.GOLD), false);
            budgets.forEach((kit, total) -> {
                int free = KitService.unreservedBudget(server, team, kit);
                src.sendSuccess(() -> Component.literal("  " + kit + "  " + (total - free) + " reserved / "
                        + total + " total  (" + free + " free)").withStyle(ChatFormatting.WHITE), false);
            });
        }
        if (!any[0]) {
            src.sendSuccess(() -> Component.literal(
                    "No kit budgets set. /kit budget <team> <kit> <count> to add one.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static void resendCatalogForKit(CommandSourceStack src, String normalizedKit) {
        for (ServerPlayer online : src.getServer().getPlayerList().getPlayers()) {
            String theirTeam = TeamService.getTeam(src.getServer(), online);
            if (theirTeam != null && TeamKits.isAllowed(theirTeam, normalizedKit)) {
                KitNetworking.sendCatalogFor(online);
            }
        }
    }

    private static int assign(CommandContext<CommandSourceStack> ctx, String id, String team) {
        CommandSourceStack src = ctx.getSource();
        String kit = KitStorage.normalizeId(id);
        // Refuse unknown kit ids outright - a typo would otherwise surface on event night as one
        // silently missing kit in a team's menu.
        if (KitStorage.get(kit).isEmpty()) {
            src.sendFailure(Component.literal("No such kit: " + id + ". Try /kit list."));
            return 0;
        }
        String norm = KitStorage.normalizeId(team);
        // Scoreboard names keep their case ("/team add Red") but the mapping is stored lowercase, so
        // this check must be case-insensitive or it rejects valid teams.
        boolean teamExists = TeamService.ids(src.getServer()).stream().anyMatch(t -> t.equalsIgnoreCase(norm));
        if (!norm.equals(TeamKits.ALL_TEAMS) && !teamExists) {
            src.sendFailure(Component.literal("No such team: " + team
                    + ". Create it with /team add <id>, or use \"*\" for every team."));
            return 0;
        }
        try {
            if (!TeamKits.assign(kit, norm)) {
                src.sendFailure(Component.literal("Team '" + norm + "' already has kit '" + kit + "'."));
                return 0;
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save team kits: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Team '" + norm + "' can now use kit '" + kit + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        resendCatalogToTeam(src, norm);
        return 1;
    }

    private static int unassign(CommandContext<CommandSourceStack> ctx, String id, String team) {
        CommandSourceStack src = ctx.getSource();
        String kit = KitStorage.normalizeId(id);
        String norm = KitStorage.normalizeId(team);
        try {
            if (!TeamKits.unassign(kit, norm)) {
                src.sendFailure(Component.literal("Team '" + norm + "' does not have kit '" + kit + "'."));
                return 0;
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save team kits: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Team '" + norm + "' can no longer use kit '" + kit + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        resendCatalogToTeam(src, norm);
        return 1;
    }

    private static void resendCatalogToTeam(CommandSourceStack src, String normalizedTeam) {
        for (ServerPlayer online : src.getServer().getPlayerList().getPlayers()) {
            String theirTeam = TeamService.getTeam(src.getServer(), online);
            if (normalizedTeam.equals(TeamKits.ALL_TEAMS) || normalizedTeam.equalsIgnoreCase(theirTeam)) {
                KitNetworking.sendCatalogFor(online);
            }
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        int count = KitStorage.loadAll(ctx.getSource().getServer());
        int teams = TeamKits.loadAll();
        int scarce = ScarceItems.loadAll(ctx.getSource().getServer());
        TeamKits.warnAboutMissingKits();
        TeamKits.warnAboutMissingTeams(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reloaded " + count + " kit(s), " + teams + " team mapping(s), " + scarce + " scarce item(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int save(CommandContext<CommandSourceStack> ctx, String id, String team) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player - it snapshots your inventory."));
            return 0;
        }
        KitDefinition def = KitService.capture(player);
        try {
            KitStorage.save(id, def, src.getServer());
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save kit: " + e.getMessage()));
            WarEngine.LOGGER.error("Saving kit '{}' failed", id, e);
            return 0;
        }
        String norm = KitStorage.normalizeId(id);
        src.sendSuccess(() -> Component.literal(
                "Saved kit '" + norm + "' (" + def.stackCount() + " stacks).")
                .withStyle(ChatFormatting.GREEN), true);
        int result = team != null ? assign(ctx, norm, team) : 1;
        // Re-saving changes icon and contents, so open menus are stale. Runs even on the assign path,
        // which only refreshes the one team it mapped to while the kit may be mapped to others.
        resendCatalogForKit(src, norm);
        return result;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        String norm = KitStorage.normalizeId(id);
        try {
            if (!KitStorage.delete(norm)) {
                src.sendFailure(Component.literal("No such kit: " + id));
                return 0;
            }
            // Drop it from every team mapping too, or teamkits.json keeps a dangling id.
            int unmapped = TeamKits.removeKitEverywhere(norm);
            src.sendSuccess(() -> Component.literal("Deleted kit '" + norm + "'"
                    + (unmapped > 0 ? " and removed it from " + unmapped + " team(s)." : "."))
                    .withStyle(ChatFormatting.GREEN), true);
            if (unmapped > 0) {
                for (ServerPlayer online : src.getServer().getPlayerList().getPlayers()) {
                    KitNetworking.sendCatalogFor(online);
                }
            }
            return 1;
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not delete kit: " + e.getMessage()));
            return 0;
        }
    }

    private static int applySelf(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can equip a kit. Use /kit give <players> <id>."));
            return 0;
        }
        String norm = KitStorage.normalizeId(id);
        // Ops bypass the team check so they can test any kit. The UI payload path applies the same
        // rule, since both call KitService.assign rather than duplicating it.
        KitService.AssignResult result = KitService.assign(player, norm, src.hasPermission(2));
        switch (result) {
            case UNKNOWN_KIT -> {
                src.sendFailure(Component.literal("No such kit: " + id + ". Try /kit list."));
                return 0;
            }
            case NO_TEAM -> {
                src.sendFailure(Component.literal("Join a team first: /team join <id>."));
                return 0;
            }
            case NO_SQUAD -> {
                src.sendFailure(Component.literal("Join or create a squad first: /squad list."));
                return 0;
            }
            case NOT_ALLOWED -> {
                src.sendFailure(Component.literal(
                        "Your team cannot use kit '" + norm + "'. Try /kit list."));
                return 0;
            }
            case NOT_RESERVED -> {
                src.sendFailure(Component.literal(
                        "Your squad has no '" + norm + "' reserved. An admin sets reservations with "
                        + "/squad reserve."));
                return 0;
            }
            case LIMIT_REACHED -> {
                src.sendFailure(Component.literal("Kit '" + norm + "' is full in your squad."));
                return 0;
            }
            case TOO_FAR_FROM_BASE -> {
                src.sendFailure(Component.literal("Too far from your team's base to change kit - "
                        + "get within " + Math.round(WarConfig.BASE_KIT_RADIUS.get()) + " blocks of it."));
                return 0;
            }
            case OK -> { }
        }
        KitNetworking.sendKitState(player);
        KitNetworking.sendCatalogToSquad(src.getServer(), WarState.get(src.getServer()).getSquad(player.getUUID()));
        src.sendSuccess(() -> Component.literal("Equipped kit '" + norm + "'.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Matched on reconcile's normalised identity, so a TACZ gun is identified by its {@code GunId},
     * not its shared item id - see {@link ScarceItems}.
     */
    private static int scarceSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player - it marks the item you're holding."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("Hold the item to mark as scarce first."));
            return 0;
        }
        try {
            if (!ScarceItems.add(held, src.getServer())) {
                src.sendFailure(Component.literal(
                        held.getHoverName().getString() + " is already marked scarce."));
                return 0;
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save scarce list: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(held.getHoverName().getString()
                + " is now scarce - kits will withhold it until /kit scarce sweep is run at round start.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int scarceUnset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player - it unmarks the item you're holding."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("Hold the item to unmark first."));
            return 0;
        }
        try {
            if (!ScarceItems.remove(held, src.getServer())) {
                src.sendFailure(Component.literal(
                        held.getHoverName().getString() + " is not marked scarce."));
                return 0;
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not save scarce list: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                held.getHoverName().getString() + " is no longer scarce.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int scarceList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<ItemStack> items = ScarceItems.all();
        if (items.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "No scarce items marked yet. Hold one and run /kit scarce setscarce.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Scarce items (" + items.size() + "):"), false);
        for (ItemStack item : items) {
            src.sendSuccess(() -> Component.literal("  " + item.getHoverName().getString())
                    .withStyle(ChatFormatting.GOLD), false);
        }
        return items.size();
    }

    private static int scarceSweep(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (ScarceItems.all().isEmpty()) {
            src.sendFailure(Component.literal("No scarce items marked - nothing to sweep."));
            return 0;
        }
        int issued = KitService.issueScarceWeapons(src.getServer());
        src.sendSuccess(() -> Component.literal(
                "Scarce sweep: issued " + issued + " item(s). Run this once, at round start.")
                .withStyle(ChatFormatting.GREEN), true);
        return issued;
    }

    private static KitDefinition assignedKit(CommandSourceStack src, ServerPlayer player) {
        String kitId = WarState.get(src.getServer()).getKit(player.getUUID());
        if (kitId == null) {
            src.sendFailure(Component.literal("You have no kit assigned. Run /kit <class> first."));
            return null;
        }
        KitDefinition kit = KitStorage.get(kitId).orElse(null);
        if (kit == null) {
            src.sendFailure(Component.literal("Your assigned kit '" + kitId + "' no longer exists."));
        }
        return kit;
    }

    private static int check(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        KitDefinition kit = assignedKit(src, player);
        if (kit == null) {
            return 0;
        }
        for (Component line : KitService.describeGaps(player, kit)) {
            src.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int resupply(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        KitDefinition kit = assignedKit(src, player);
        if (kit == null) {
            return 0;
        }
        KitService.reconcile(player, kit, 0);
        return 1;
    }

    private static int testCountdown(CommandContext<CommandSourceStack> ctx, int seconds) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Starting a " + seconds + "s resupply countdown (debug).")
                .withStyle(ChatFormatting.GRAY), false);
        KitService.debugCountdown(player, seconds);
        return 1;
    }

    private static int giveOthers(CommandContext<CommandSourceStack> ctx, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        KitDefinition kit = KitStorage.get(id).orElse(null);
        if (kit == null) {
            src.sendFailure(Component.literal("No such kit: " + id));
            return 0;
        }
        String norm = KitStorage.normalizeId(id);
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        WarState state = WarState.get(src.getServer());
        for (ServerPlayer target : targets) {
            KitService.apply(target, kit);
            state.setKit(target.getUUID(), norm);
            KitNetworking.sendKitState(target);
            KitNetworking.sendCatalogToSquad(src.getServer(), state.getSquad(target.getUUID()));
        }
        src.sendSuccess(() -> Component.literal(
                "Gave kit '" + norm + "' to " + targets.size() + " player(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return targets.size();
    }
}
