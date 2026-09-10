package com.pigapl.warengine.client.map;

import com.pigapl.warengine.client.WarEngineClient;
import com.pigapl.warengine.network.CapturePointLoc;
import com.pigapl.warengine.network.CapturePointStatus;
import com.pigapl.warengine.network.client.ClientCapturePointCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;

/**
 * Draws a labelled marker per capture point on top of Xaero's World Map screen.
 *
 * <p>Xaero's own highlighter and element registries both close before any other mod can reach them,
 *  so this paints over the screen instead. Camera state is read reflectively from
 * {@code GuiMap}: {@code world = local/scale + camera}, and Xaero draws in raw pixels
 * ({@code screenScale} is just the GUI scale), so screen = centre + (world-camera)*scale/guiScale.</p>
 */
@EventBusSubscriber(modid = WarEngineClient.MODID, value = Dist.CLIENT)
public final class WorldMapOverlay {

    private WorldMapOverlay() {}

    private static final String MOD_ID = "xaeroworldmap";
    private static final String MAP_SCREEN = "xaero.map.gui.GuiMap";
    private static final int NEUTRAL_RGB = 0xBFBFBF;

    private static Boolean present;
    private static boolean broken;
    private static Field fCameraX;
    private static Field fCameraZ;
    private static Field fScale;

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (broken || !present()) {
            return;
        }
        try {
            draw(event.getScreen(), event.getGuiGraphics());
        } catch (Throwable t) {
            broken = true;
            WarEngineClient.LOGGER.warn("[warengine] world map overlay disabled: {}", t.toString());
        }
    }

    /** Matched by class NAME - GuiMap extends a XaeroLib type that is JarJar'd and off our classpath. */
    private static void draw(Screen screen, GuiGraphics g) throws Exception {
        Class<?> mapClass = mapClassOf(screen);
        if (mapClass == null) {
            return;
        }
        Object map = screen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientCapturePointCache.locations().isEmpty()) {
            return;
        }
        if (fCameraX == null) {
            fCameraX = field(mapClass, "cameraX");
            fCameraZ = field(mapClass, "cameraZ");
            fScale = field(mapClass, "scale");
        }
        double camX = fCameraX.getDouble(map);
        double camZ = fCameraZ.getDouble(map);
        double scale = fScale.getDouble(map);
        double gui = mc.getWindow().getGuiScale();
        if (scale <= 0 || gui <= 0) {
            return;
        }

        String dim = mc.level.dimension().location().toString();
        double cx = screen.width / 2.0;
        double cz = screen.height / 2.0;

        for (CapturePointLoc p : ClientCapturePointCache.locations()) {
            if (!p.dim().equals(dim)) {
                continue;
            }
            int sx = (int) Math.round(cx + (p.x() - camX) * scale / gui);
            int sy = (int) Math.round(cz + (p.z() - camZ) * scale / gui);
            if (sx < -32 || sy < -32 || sx > screen.width + 32 || sy > screen.height + 32) {
                continue;
            }
            int rgb = teamRgb(owner(p.id()));
            g.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF000000 | rgb);
            g.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xFF000000);
            String label = p.id();
            g.drawString(mc.font, label, sx - mc.font.width(label) / 2, sy - 14,
                    0xFF000000 | rgb, true);
        }
    }

    private static Class<?> mapClassOf(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(MAP_SCREEN)) {
                return c;
            }
        }
        return null;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static String owner(String pointId) {
        for (CapturePointStatus s : ClientCapturePointCache.points()) {
            if (s.id().equals(pointId)) {
                return s.owner();
            }
        }
        return "";
    }

    private static int teamRgb(String team) {
        Minecraft mc = Minecraft.getInstance();
        if (team.isEmpty() || mc.level == null) {
            return NEUTRAL_RGB;
        }
        PlayerTeam t = mc.level.getScoreboard().getPlayerTeam(team);
        ChatFormatting c = t == null ? null : t.getColor();
        Integer rgb = c == null ? null : c.getColor();
        return rgb == null ? NEUTRAL_RGB : rgb;
    }

    private static boolean present() {
        if (present == null) {
            present = ModList.get().isLoaded(MOD_ID);
        }
        return present;
    }
}
