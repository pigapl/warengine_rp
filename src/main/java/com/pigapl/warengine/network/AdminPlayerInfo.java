package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * One player as the admin panel sees them. The per-player booleans and ids live in a nested
 * {@link Flags} because StreamCodec.composite tops out at 6 field/getter pairs and this record hit it;
 * the delegating accessors below keep every call site reading {@code member.online()} as before.
 */
public record AdminPlayerInfo(String name, UUID uuid, String kitId, Flags flags) {

    /**
     * {@code farFromBase}: metres from own base when outside the kit radius before a war, else -1.
     * {@code squadId}: empty when in no squad - the panels grey out squad-only actions with it.
     * {@code exempt}: an admin who also plays - skips base restrictions and nags.
     * {@code commander} / {@code leader}: the team roles, see {@code RoleService}. Six pairs is this
     * codec's ceiling too, so anything further needs another nesting.
     */
    public record Flags(boolean online, int farFromBase, String squadId, boolean exempt,
                        boolean commander, boolean leader) {

        public static final StreamCodec<ByteBuf, Flags> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Flags::online,
                ByteBufCodecs.VAR_INT, Flags::farFromBase,
                ByteBufCodecs.STRING_UTF8, Flags::squadId,
                ByteBufCodecs.BOOL, Flags::exempt,
                ByteBufCodecs.BOOL, Flags::commander,
                ByteBufCodecs.BOOL, Flags::leader,
                Flags::new
        );
    }

    public AdminPlayerInfo(String name, UUID uuid, boolean online, String kitId, int farFromBase,
                           String squadId, boolean exempt, boolean commander, boolean leader) {
        this(name, uuid, kitId, new Flags(online, farFromBase, squadId, exempt, commander, leader));
    }

    public boolean commander() {
        return flags.commander();
    }

    public boolean leader() {
        return flags.leader();
    }

    public boolean online() {
        return flags.online();
    }

    public int farFromBase() {
        return flags.farFromBase();
    }

    public String squadId() {
        return flags.squadId();
    }

    public boolean exempt() {
        return flags.exempt();
    }

    public static final StreamCodec<ByteBuf, AdminPlayerInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminPlayerInfo::name,
            UUIDUtil.STREAM_CODEC, AdminPlayerInfo::uuid,
            ByteBufCodecs.STRING_UTF8, AdminPlayerInfo::kitId,
            Flags.STREAM_CODEC, AdminPlayerInfo::flags,
            AdminPlayerInfo::new
    );
}
