package com.pigapl.warengine.round;

import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.WarEngine;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The war round: a fixed-length timer, per-team tickets, and a capture zone. Stateless - all state is
 * in {@link WarState}. {@link #tick} runs once per server tick from {@code GameEvents}.
 *
 * <p><b>Tickets are earned, not spent.</b> Every team starts at 0; once a second the team holding the
 * zone UNCONTESTED (present, no enemy inside) gains {@code round.zoneTicketsPerSecond}, and an empty
 * or contested zone awards nobody. First to {@code round.ticketCap} wins. Kills do not touch tickets -
 * see {@link #onPlayerDeath}.</p>
 *
 * <p>End time is wall-clock epoch millis, not a tick count, so it survives a mid-war restart.</p>
 */
public final class RoundService {

    private RoundService() {}

    // ------------------------------------------------------------------ lifecycle

    /** Begins a war of {@code minutes} length. Every existing team starts at 0 tickets. */
    public static void start(MinecraftServer server, int minutes) {
        WarState st = WarState.get(server);
        Map<String, Integer> initial = new LinkedHashMap<>();
        for (String team : TeamService.ids(server)) {
            initial.put(team, 0);
        }
        long endAt = System.currentTimeMillis() + minutes * 60_000L;
        st.startRound(endAt, initial);

        // The whistle is the one moment scarce weapons enter the world - kit pick and respawn both
        // withhold them. See KitService#issueScarceWeapons. No-op when the scarce list is empty.
        int scarce = com.pigapl.warengine.kit.KitService.issueScarceWeapons(server);

        int cap = WarConfig.ROUND_TICKET_CAP.get();
        WarEngine.LOGGER.info("[war] started: {} min, {} teams, first to {} tickets wins, {} scarce stack(s) issued",
                minutes, initial.size(), cap, scarce);
        broadcastTitle(server,
                Component.literal("WAR").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal(minutes + " min  -  hold the zone - first to " + cap + " wins")
                        .withStyle(ChatFormatting.YELLOW),
                5, 60, 20);
        broadcast(server, Component.literal("The war has begun. " + ticketSummary(st))
                .withStyle(ChatFormatting.GOLD));
        playEverywhere(server, SoundEvents.WITHER_SPAWN, 0.6f, 1.2f);
    }

    /** Ends a running war and announces the result. No-op if none is running. */
    public static void end(MinecraftServer server, String reason) {
        WarState st = WarState.get(server);
        if (!st.roundActive()) {
            return;
        }
        st.endRound();
        String winner = leadingTeam(st);
        Component result = winner == null
                ? Component.literal(reason + "  -  Draw  (" + ticketSummary(st) + ")")
                        .withStyle(ChatFormatting.YELLOW)
                : Component.literal(reason + "  -  " + winner + " wins  (" + ticketSummary(st) + ")")
                        .withStyle(ChatFormatting.YELLOW);

        WarEngine.LOGGER.info("[war] ended: {} / winner={}", reason, winner == null ? "draw" : winner);
        broadcastTitle(server,
                Component.literal("WAR OVER").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                result, 10, 90, 20);
        broadcast(server, result);
        playEverywhere(server, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
    }

    /**
     * Deliberate no-op: kills no longer cost tickets, they are earned by holding the zone. Kept so
     * {@code GameEvents}' {@code LivingDeathEvent} hook needs no change, and so a future kill-feed has
     * somewhere to land without re-touching that shared file.
     */
    public static void onPlayerDeath(ServerPlayer player) {
    }

    // ------------------------------------------------------------------ per-tick

    public static void tick(MinecraftServer server) {
        WarState st = WarState.get(server);
        long tick = server.getTickCount();

        if (st.roundActive()) {
            if (System.currentTimeMillis() >= st.roundEndEpochMillis()) {
                end(server, "TIME UP");
            } else if (tick % 20L == 0L) {
                tickCaptureZone(server, st);
                // It may have just ended the round (cap reached) - don't show a stale "WAR ..."
                // actionbar on the same tick as "WAR OVER".
                if (st.roundActive()) {
                    int cap = WarConfig.ROUND_TICKET_CAP.get();
                    Component hud = Component.literal("WAR  " + timeLeftString(st) + "   "
                            + ticketSummary(st) + "   (first to " + cap + ")")
                            .withStyle(ChatFormatting.GOLD);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.displayClientMessage(hud, true);
                    }
                }
            }
        }

        // Drawn whenever a zone is set, war or not, so /war setzone gives immediate feedback.
        // 8-tick cadence looks solid without spamming particles.
        if (st.hasZone() && tick % 8L == 0L) {
            drawZone(server, st);
        }
    }

    /**
     * Once a second: credit the zone to whichever ticketed team holds it UNCONTESTED, and end the war
     * the instant someone reaches {@code round.ticketCap}. Also drives the admin panel's history log -
     * the holder is recomputed every call, but logged only when it actually CHANGES.
     */
    private static void tickCaptureZone(MinecraftServer server, WarState st) {
        Map<String, java.util.List<String>> occupants = zoneOccupantsByTeam(server, st);
        // Exactly one ticketed team present = held; zero or 2+ = empty/contested, nobody scores.
        String holder = occupants.size() == 1 ? occupants.keySet().iterator().next() : null;

        if (!java.util.Objects.equals(holder, st.zoneHolderTeam())) {
            java.util.List<String> present = holder != null ? occupants.get(holder)
                    : occupants.values().stream().flatMap(java.util.List::stream).toList();
            st.setZoneHolder(holder, present);
        }

        if (holder == null) {
            return; // empty or contested this second - nobody scores
        }
        int cap = WarConfig.ROUND_TICKET_CAP.get();
        int now = Math.min(cap, st.getTickets(holder) + WarConfig.ZONE_TICKETS_PER_SECOND.get());
        st.setTickets(holder, now);
        if (now >= cap) {
            end(server, holder.toUpperCase(Locale.ROOT) + " CAPTURED THE POINT");
        }
    }

    /**
     * Every ticketed player inside the capture zone, by team. Public so the admin panel's live "who's
     * in it" view uses the exact rule {@link #tickCaptureZone} scores by, rather than a second
     * implementation that could drift. Empty if there is no zone, its dimension is unloaded, or nobody
     * ticketed is inside.
     */
    public static Map<String, java.util.List<String>> zoneOccupantsByTeam(MinecraftServer server, WarState st) {
        Map<String, java.util.List<String>> byTeam = new LinkedHashMap<>();
        if (!st.hasZone()) {
            return byTeam;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(st.zoneDim())));
        if (level == null) {
            return byTeam;
        }
        double cx = st.zoneX() + 0.5;
        double cy = st.zoneY() + 0.5;
        double cz = st.zoneZ() + 0.5;
        double rSq = st.zoneRadius() * st.zoneRadius();

        for (ServerPlayer player : level.players()) {
            String team = TeamService.getTeam(server, player);
            if (team == null || !st.tickets().containsKey(team)) {
                continue; // not on a team that was in the war at start
            }
            if (player.position().distanceToSqr(cx, cy, cz) > rSq) {
                continue;
            }
            byTeam.computeIfAbsent(team, t -> new java.util.ArrayList<>())
                    .add(player.getGameProfile().getName());
        }
        return byTeam;
    }

    // ------------------------------------------------------------------ display helpers

    /** e.g. {@code "red 42  blue 37"}; {@code "(no teams)"} when nothing is ticketed. */
    public static String ticketSummary(WarState st) {
        if (st.tickets().isEmpty()) {
            return "(no teams)";
        }
        StringBuilder sb = new StringBuilder();
        st.tickets().forEach((team, n) -> {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(team).append(' ').append(n);
        });
        return sb.toString();
    }

    /** {@code m:ss} remaining, clamped at zero. */
    public static String timeLeftString(WarState st) {
        long secs = Math.max(0L, (st.roundEndEpochMillis() - System.currentTimeMillis()) / 1000L);
        return (secs / 60L) + ":" + String.format(Locale.ROOT, "%02d", secs % 60L);
    }

    // ------------------------------------------------------------------ internals

    /** Team with the most tickets, or {@code null} if empty or tied for the lead. */
    private static String leadingTeam(WarState st) {
        String best = null;
        int bestN = Integer.MIN_VALUE;
        boolean tied = false;
        for (Map.Entry<String, Integer> e : st.tickets().entrySet()) {
            if (e.getValue() > bestN) {
                best = e.getKey();
                bestN = e.getValue();
                tied = false;
            } else if (e.getValue() == bestN) {
                tied = true;
            }
        }
        return tied ? null : best;
    }

    private static void drawZone(MinecraftServer server, WarState st) {
        ServerLevel level = server.getLevel(ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(st.zoneDim())));
        if (level == null) {
            return;
        }
        double cx = st.zoneX() + 0.5;
        double cy = st.zoneY() + 1.0;
        double cz = st.zoneZ() + 0.5;
        double r = st.zoneRadius();
        int points = Math.max(24, (int) Math.round(r * 8.0));
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            level.sendParticles(ParticleTypes.END_ROD,
                    cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        // A short pillar at the centre so the zone reads from a distance / from above.
        for (int y = 0; y < 4; y++) {
            level.sendParticles(ParticleTypes.END_ROD, cx, cy + y, cz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void broadcastTitle(MinecraftServer server, Component title, Component sub,
                                       int in, int stay, int out) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        }
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void playEverywhere(MinecraftServer server, net.minecraft.sounds.SoundEvent sound,
                                       float volume, float pitch) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        }
    }
}
