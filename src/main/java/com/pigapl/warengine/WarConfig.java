package com.pigapl.warengine;

import net.neoforged.neoforge.common.ModConfigSpec;

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
                     "immediately. Tickets come only from OWNING capture points (see",
                     "round.zoneTicketsPerSecond) - kills no longer cost or grant tickets.",
                     "This is point-seconds, not seconds: with 3 points at 1/s, owning all three",
                     "earns 3/s, so 500 = about 2:47 of total dominance, 4:10 on two points.",
                     "Retune it live from the admin panel once you see real round lengths.")
            .defineInRange("round.ticketCap", 500, 1, 1000000);

    public static final ModConfigSpec.IntValue ZONE_TICKETS_PER_SECOND = B
            .comment("Tickets gained per second, PER CAPTURE POINT a team owns. Ownership is sticky -",
                     "an owned point keeps paying with nobody standing on it. A point pays nothing",
                     "while contested (two or more teams inside) or while neutral.")
            .defineInRange("round.zoneTicketsPerSecond", 1, 1, 1000);

    public static final ModConfigSpec.DoubleValue ZONE_RADIUS = B
            .comment("Default radius, in blocks, for /war point add with no radius given.",
                     "Also the capture radius: who counts as being on the point.")
            .defineInRange("round.zoneRadius", 5.0, 1.0, 128.0);

    public static final ModConfigSpec.IntValue CAPTURE_SECONDS = B
            .comment("How much capture progress is needed to flip a point to a new owner.",
                     "One attacker adds 1 per second, so 30 = a 30-second solo capture.",
                     "See round.captureMaxPlayers for how a group speeds that up.")
            .defineInRange("round.captureSeconds", 30, 1, 3600);

    public static final ModConfigSpec.IntValue CAPTURE_MAX_PLAYERS = B
            .comment("Capture progress per second equals the number of attackers on the point,",
                     "capped at this. At the defaults: 1 player 30s, 2 -> 15s, 3 -> 10s, 6+ -> 5s.",
                     "Stops a 20-man blob from flipping a point instantly.")
            .defineInRange("round.captureMaxPlayers", 6, 1, 100);

    public static final ModConfigSpec.IntValue CAPTURE_DECAY_PER_SECOND = B
            .comment("Capture progress lost per second when nobody is pushing the bar - the attackers",
                     "died, left, or the owner retook the ground. Higher than the gain rate on purpose,",
                     "so an abandoned capture drains faster than it filled. 0 = progress never decays.")
            .defineInRange("round.captureDecayPerSecond", 2, 0, 1000);

    // Key kept as "kitRadius" so existing server configs keep their value - it now sizes the whole base.
    public static final ModConfigSpec.DoubleValue BASE_KIT_RADIUS = B
            .comment("Default base size in blocks: where a kit may be picked AND how far the base lock lets a",
                     "player go. Each base can override it in the admin panel (Teams & Squads, - / +).",
                     "Set a base from the admin panel (Teams & Squads -> Base) or /warstate base set <team>.",
                     "A team with no base set is never restricted. 0 = no restriction for bases on the default.")
            .defineInRange("bases.kitRadius", 40.0, 0.0, 1000.0);

    public static final ModConfigSpec.IntValue BASE_KEEP_GRACE_SECONDS = B
            .comment("Base lock: seconds of countdown outside the radius before the player is teleported back.")
            .defineInRange("bases.keepGraceSeconds", 5, 1, 60);

    public static final ModConfigSpec SPEC = B.build();

    private WarConfig() {}
}
