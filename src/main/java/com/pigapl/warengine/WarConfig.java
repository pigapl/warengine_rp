package com.pigapl.warengine;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config. Lives at {@code <server>/config/warengine_pigapl-server.toml}.
 * Only knobs that admins might reasonably tune during an event season belong here;
 * kit contents live as JSON files under {@code config/warengine_pigapl/kits/}.
 */
public final class WarConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RECONCILE_ON_RESPAWN = B
            .comment("Automatically top up a player's kit when they respawn.",
                     "Keeps looted blocks and crafted tools; only re-adds missing/depleted kit items.")
            .define("kit.reconcileOnRespawn", true);

    public static final ModConfigSpec.IntValue DEFERRED_RESUPPLY_SECONDS = B
            .comment("If the inventory is too full to fit missing kit items on respawn, show a big",
                     "on-screen countdown for this many seconds, then try once more. Anything that",
                     "still does not fit is not given. Set to 0 to skip the countdown (chat line only).")
            .defineInRange("kit.deferredResupplySeconds", 10, 0, 120);

    public static final ModConfigSpec.IntValue RESUPPLY_GRACE_SECONDS = B
            .comment("Grace period after respawn before the 'drop your junk' countdown starts,",
                     "so the player can move away from spawn before dropping loot. 0 = start immediately.")
            .defineInRange("kit.resupplyGraceSeconds", 5, 0, 60);

    public static final ModConfigSpec.BooleanValue CLEAR_ON_KIT_COMMAND = B
            .comment("When a player runs /kit <class>, wipe their inventory before handing out the fresh kit.")
            .define("kit.clearInventoryOnCommand", true);

    public static final ModConfigSpec.BooleanValue DROP_OVERFLOW = B
            .comment("If reconcile cannot fit a missing kit item, drop it at the player's feet",
                     "instead of scheduling the timed re-supply.")
            .define("kit.dropOverflow", false);

    public static final ModConfigSpec.IntValue TEAM_NAG_INTERVAL_SECONDS = B
            .comment("How often to remind an unassigned player (actionbar) to pick a team.",
                     "Set to 0 to disable the recurring reminder (the once-per-login title still shows).",
                     "Teams themselves are vanilla scoreboard teams - create them with /team add.")
            .defineInRange("team.nagIntervalSeconds", 30, 0, 600);

    public static final ModConfigSpec.IntValue ROUND_DEFAULT_MINUTES = B
            .comment("Default length of a war when /war start is run with no argument.")
            .defineInRange("round.defaultDurationMinutes", 10, 1, 240);

    public static final ModConfigSpec.IntValue ROUND_TICKET_CAP = B
            .comment("Every team starts a war at 0 tickets. The first team to reach this many wins",
                     "immediately. Tickets come only from holding the capture zone (see",
                     "round.zoneTicketsPerSecond) - kills no longer cost or grant tickets.")
            .defineInRange("round.ticketCap", 60, 1, 1000000);

    public static final ModConfigSpec.IntValue ZONE_TICKETS_PER_SECOND = B
            .comment("Tickets gained per second by whichever team holds the capture zone",
                     "UNCONTESTED - i.e. it has at least one player inside and the enemy has none.",
                     "An empty or contested zone (both teams present) awards nothing that second.")
            .defineInRange("round.zoneTicketsPerSecond", 1, 1, 1000);

    public static final ModConfigSpec.DoubleValue ZONE_RADIUS = B
            .comment("Default radius, in blocks, of the marker circle drawn by /war setzone.",
                     "Also the capture radius used for round.zoneTicketsPerSecond scoring.")
            .defineInRange("round.zoneRadius", 5.0, 1.0, 128.0);

    public static final ModConfigSpec.DoubleValue BASE_KIT_RADIUS = B
            .comment("How close to their own team's base a player must be to pick or change a kit.",
                     "Set a base from the admin panel (Teams & Squads -> Base) or /warstate base set <team>.",
                     "A team with no base set is never restricted, so this does nothing until bases exist.",
                     "0 = no distance restriction at all.")
            .defineInRange("bases.kitRadius", 40.0, 0.0, 1000.0);

    public static final ModConfigSpec SPEC = B.build();

    private WarConfig() {}
}
