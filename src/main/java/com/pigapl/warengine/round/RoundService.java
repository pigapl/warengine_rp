package com.pigapl.warengine.round;

import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.WarEngine;
import com.pigapl.warengine.network.CapturePointStatus;
import com.pigapl.warengine.network.ClientboundCapturePointsPayload;
import com.pigapl.warengine.network.TeamTicketEntry;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.state.WarState.CapturePoint;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The war round: fixed-length timer, per-team tickets, N capture points. Stateless - state lives in
 * {@link WarState}.
 *
 * <p><b>Ownership is sticky.</b> Presence TAKES a point, it does not hold it - an owned point pays
 * every second whether or not anyone is on it. Neutral and contested points pay nobody. Tickets are
 * earned, never spent; kills do not touch them.</p>
 *
 * <p>Markers are depth-tested particles sent per player, so a point behind a hill is invisible.
 * Detail thins with distance rather than cutting off. End time is epoch millis, not a tick count,
 * so it survives a restart.</p>
 */
public final class RoundService {

    private RoundService() {}

    private static final int NEUTRAL_RGB = 0xBFBFBF;

    /** Vanilla refuses a long-distance particle past this, so there is no point computing beyond it. */
    private static final double MARKER_RANGE = 512.0;
    private static final double RING_FULL_DISTANCE = 48.0;
    private static final double RING_SPARSE_DISTANCE = 144.0;
    private static final int GROUND_SCAN_UP = 4;
    private static final int GROUND_SCAN_DOWN = 12;

    /** Send filter only - without it this is a packet per player per second for the whole event. */
    private static ClientboundCapturePointsPayload lastBroadcast;


    public static void start(MinecraftServer server, int minutes) {
        WarState st = WarState.get(server);
        Map<String, Integer> initial = new LinkedHashMap<>();
        for (String team : TeamService.ids(server)) {
            initial.put(team, 0);
        }
        long endAt = System.currentTimeMillis() + minutes * 60_000L;
        st.startRound(endAt, initial);
        // Every round opens as a land grab - nobody inherits last round's points.
        st.resetPointsToNeutral();

        // The whistle is the one moment scarce weapons enter the world - kit pick and respawn both
        // withhold them. See KitService#issueScarceWeapons. No-op when the scarce list is empty.
        int scarce = com.pigapl.warengine.kit.KitService.issueScarceWeapons(server);

        int cap = WarConfig.ROUND_TICKET_CAP.get();
        WarEngine.LOGGER.info("[war] started: {} min, {} teams, {} point(s), first to {} tickets wins, "
                        + "{} scarce stack(s) issued",
                minutes, initial.size(), st.points().size(), cap, scarce);
        broadcastTitle(server,
                Component.literal("WAR").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal(minutes + " min  -  take the points - first to " + cap + " wins")
                        .withStyle(ChatFormatting.YELLOW),
                5, 60, 20);
        broadcast(server, Component.literal("The war has begun. " + ticketSummary(st))
                .withStyle(ChatFormatting.GOLD));
        playEverywhere(server, SoundEvents.WITHER_SPAWN, 0.6f, 1.2f);
        broadcastPoints(server, st, true);
    }

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
        broadcastPoints(server, st, true);
    }

    public static void onPlayerDeath(ServerPlayer player) {
    }


    public static void tick(MinecraftServer server) {
        WarState st = WarState.get(server);
        long tick = server.getTickCount();

        if (st.roundActive()) {
            if (System.currentTimeMillis() >= st.roundEndEpochMillis()) {
                end(server, "TIME UP");
            } else if (tick % 20L == 0L) {
                tickCapturePoints(server, st);
                // May have just ended the round (cap reached) - don't push a stale snapshot.
                if (st.roundActive()) {
                    broadcastPoints(server, st, false);
                }
            }
        } else if (tick % 20L == 0L) {
            // Points change outside a war too; the diff inside makes this free when nothing moved.
            broadcastPoints(server, st, false);
        }

        if (st.hasPoints() && tick % 10L == 0L) {
            for (CapturePoint point : st.points()) {
                drawPoint(server, point);
            }
        }
    }

    /** Owners are recomputed every call, but logged to the admin history only on an actual flip. */
    private static void tickCapturePoints(MinecraftServer server, WarState st) {
        int captureTotal = WarConfig.CAPTURE_SECONDS.get();
        int maxPlayers = WarConfig.CAPTURE_MAX_PLAYERS.get();
        int decay = WarConfig.CAPTURE_DECAY_PER_SECOND.get();
        int perPoint = WarConfig.ZONE_TICKETS_PER_SECOND.get();
        int cap = WarConfig.ROUND_TICKET_CAP.get();

        Map<String, Integer> earned = new LinkedHashMap<>();

        for (CapturePoint point : st.points()) {
            Map<String, List<String>> occupants = occupantsByTeam(server, point);

            if (occupants.size() >= 2) {
                // Contested: bar frozen, nobody paid. A defender standing on their own point while an
                // enemy caps it lands here on purpose - body-blocking a flip works, and costs income.
                continue;
            }

            String sole = occupants.isEmpty() ? null : occupants.keySet().iterator().next();

            if (sole != null && !sole.equals(point.owner)) {
                int speed = Math.min(occupants.get(sole).size(), maxPlayers);
                // A different team taking over restarts the bar rather than inheriting its progress.
                int from = sole.equals(point.capturingTeam) ? point.progress : 0;
                int next = from + speed;
                if (next >= captureTotal) {
                    st.setPointOwner(point.id, sole, occupants.get(sole));
                    announceCapture(server, point.id, sole);
                } else {
                    st.setPointProgress(point.id, sole, next);
                }
                continue; // an owner being flipped off their own point earns nothing this second
            }

            if (point.capturingTeam != null) {
                int next = point.progress - decay;
                st.setPointProgress(point.id, next > 0 ? point.capturingTeam : null, next);
            }
            if (point.owner != null && st.tickets().containsKey(point.owner)) {
                earned.merge(point.owner, perPoint, Integer::sum);
            }
        }

        String winner = null;
        for (Map.Entry<String, Integer> e : earned.entrySet()) {
            int now = Math.min(cap, st.getTickets(e.getKey()) + e.getValue());
            st.setTickets(e.getKey(), now);
            if (now >= cap && winner == null) {
                winner = e.getKey();
            }
        }
        if (winner != null) {
            end(server, winner.toUpperCase(Locale.ROOT) + " REACHED " + cap);
        }
    }

    /** Public so the admin panel's "who is on it" view uses the exact rule scoring uses - no drift. */
    public static Map<String, List<String>> occupantsByTeam(MinecraftServer server, CapturePoint point) {
        Map<String, List<String>> byTeam = new LinkedHashMap<>();
        ServerLevel level = levelOf(server, point);
        if (level == null) {
            return byTeam;
        }
        WarState st = WarState.get(server);
        double cx = point.x + 0.5;
        double cy = point.y + 0.5;
        double cz = point.z + 0.5;
        double rSq = point.radius * point.radius;

        for (ServerPlayer player : level.players()) {
            String team = TeamService.getTeam(server, player);
            if (team == null || !st.tickets().containsKey(team)) {
                continue; // not on a team that was in the war at start
            }
            if (player.position().distanceToSqr(cx, cy, cz) > rSq) {
                continue;
            }
            byTeam.computeIfAbsent(team, t -> new ArrayList<>())
                    .add(player.getGameProfile().getName());
        }
        return byTeam;
    }

    public static ServerLevel levelOf(MinecraftServer server, CapturePoint point) {
        ResourceLocation id = ResourceLocation.tryParse(point.dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }


    public static void broadcastPoints(MinecraftServer server, WarState st, boolean force) {
        ClientboundCapturePointsPayload payload = buildPointsPayload(server, st);
        if (!force && payload.equals(lastBroadcast)) {
            return;
        }
        lastBroadcast = payload;
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void sendPointsTo(ServerPlayer player) {
        MinecraftServer server = player.server;
        PacketDistributor.sendToPlayer(player, buildPointsPayload(server, WarState.get(server)));
    }

    private static ClientboundCapturePointsPayload buildPointsPayload(MinecraftServer server, WarState st) {
        int captureTotal = WarConfig.CAPTURE_SECONDS.get();
        List<CapturePointStatus> points = new ArrayList<>();
        for (CapturePoint p : st.points()) {
            boolean contested = st.roundActive() && occupantsByTeam(server, p).size() >= 2;
            points.add(new CapturePointStatus(p.id, orEmpty(p.owner), orEmpty(p.capturingTeam),
                    p.progress, captureTotal, contested));
        }
        List<TeamTicketEntry> tickets = new ArrayList<>();
        st.tickets().forEach((team, n) -> tickets.add(new TeamTicketEntry(team, n)));
        // Rounded to whole seconds so a ticking clock alone does not defeat the broadcast diff.
        long left = st.roundActive()
                ? Math.max(0L, (st.roundEndEpochMillis() - System.currentTimeMillis()) / 1000L) * 1000L
                : 0L;
        return new ClientboundCapturePointsPayload(points, tickets, WarConfig.ROUND_TICKET_CAP.get(),
                st.roundActive(), left);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }


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

    public static String timeLeftString(WarState st) {
        long secs = Math.max(0L, (st.roundEndEpochMillis() - System.currentTimeMillis()) / 1000L);
        return (secs / 60L) + ":" + String.format(Locale.ROOT, "%02d", secs % 60L);
    }


    private static void announceCapture(MinecraftServer server, String pointId, String team) {
        WarEngine.LOGGER.info("[war] point {} captured by {}", pointId, team);
        broadcast(server, Component.literal(team + " captured " + pointId)
                .withStyle(ChatFormatting.GOLD));
        playEverywhere(server, SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.4f);
    }

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

    /**
     * Ground-hugging ring plus a centre pillar. Every send uses the long-distance particle overload,
     * so vanilla's 512-block ceiling is the only hard cutoff; what changes with range is how much of
     * the ring is drawn, down to just the pillar. Depth-tested, so terrain still hides it.
     */
    private static void drawPoint(MinecraftServer server, CapturePoint point) {
        ServerLevel level = levelOf(server, point);
        if (level == null) {
            return;
        }
        List<ServerPlayer> viewers = level.players();
        if (viewers.isEmpty()) {
            return;
        }

        double cx = point.x + 0.5;
        double cz = point.z + 0.5;
        double r = point.radius;

        int ownerRgb = TeamService.colorOf(server, point.owner, NEUTRAL_RGB);
        DustParticleOptions ownerDust = dust(ownerRgb, 1.4f);
        DustParticleOptions captureDust = point.capturingTeam == null ? ownerDust
                : dust(TeamService.colorOf(server, point.capturingTeam, NEUTRAL_RGB), 1.4f);
        DustParticleOptions pillarDust = dust(ownerRgb, 2.2f);

        int total = Math.max(1, WarConfig.CAPTURE_SECONDS.get());
        int steps = Math.max(16, Math.min(48, (int) Math.round(r * 2.0)));
        int captured = point.capturingTeam == null ? 0
                : (int) Math.round(steps * Math.min(1.0, point.progress / (double) total));

        // Built once per draw and reused for every viewer - the terrain scan is the expensive part.
        double[] ringX = new double[steps];
        double[] ringY = new double[steps];
        double[] ringZ = new double[steps];
        for (int i = 0; i < steps; i++) {
            double a = (Math.PI * 2.0 * i) / steps;
            double px = cx + Math.cos(a) * r;
            double pz = cz + Math.sin(a) * r;
            ringX[i] = px;
            ringY[i] = groundY(level, Mth.floor(px), Mth.floor(pz), point.y);
            ringZ[i] = pz;
        }
        double pillarBase = groundY(level, point.x, point.z, point.y);

        for (ServerPlayer viewer : viewers) {
            double distance = Math.sqrt(viewer.distanceToSqr(cx, pillarBase, cz));
            if (distance > MARKER_RANGE) {
                continue;
            }
            int stride = distance <= RING_FULL_DISTANCE ? 1
                    : distance <= RING_SPARSE_DISTANCE ? 2 : 0;
            if (stride > 0) {
                for (int i = 0; i < steps; i += stride) {
                    level.sendParticles(viewer, i < captured ? captureDust : ownerDust, true,
                            ringX[i], ringY[i], ringZ[i], 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            for (int y = 0; y < 6; y++) {
                level.sendParticles(viewer, pillarDust, true, cx, pillarBase + y, cz,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Drops one ring position onto the ground under it - without this the ring is a flat disc that
     * floats across a slope. Deliberately scans a short way around the point's own Y rather than
     * using a heightmap: on a built map a heightmap lands on a roof or a bridge above the point.
     */
    private static double groundY(ServerLevel level, int x, int z, int aroundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, aroundY, z);
        if (!level.isLoaded(cursor)) {
            return aroundY + 1.1; // never force-load a chunk just to draw a marker
        }
        for (int y = aroundY + GROUND_SCAN_UP; y >= aroundY - GROUND_SCAN_DOWN; y--) {
            cursor.set(x, y, z);
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return y + 1.1;
            }
        }
        return aroundY + 1.1;
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(
                new Vector3f(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f,
                        (rgb & 0xFF) / 255.0f),
                scale);
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
