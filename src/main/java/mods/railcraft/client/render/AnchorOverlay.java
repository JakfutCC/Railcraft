package mods.railcraft.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorWorld;
import mods.railcraft.common.util.anchors.AnchorLoading;
import mods.railcraft.common.util.anchors.AnchorVolume;

public final class AnchorOverlay {

    private static final AnchorOverlay INSTANCE = new AnchorOverlay();
    private static boolean registered;
    private static TileAnchorWorld selected;
    private static World snapshotWorld;
    private static AnchorVolume snapshot;
    private static boolean snapshotCubic;

    public static void toggle(TileAnchorWorld tile) {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
        selected = selected == tile ? null : tile;
        snapshot = null;
    }

    public static void highlight(NBTTagCompound entry) {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
        selected = null;
        snapshotWorld = Minecraft.getMinecraft().theWorld;
        int[] box = entry.getIntArray("bounds");
        if (box.length != 6 || snapshotWorld == null || snapshotWorld.provider.dimensionId != entry.getInteger("dim"))
            return;
        snapshot = new AnchorVolume(box[0], box[1], box[2], box[3], box[4], box[5]);
        snapshotCubic = entry.getBoolean("cubic");
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (snapshot != null && mc.theWorld != snapshotWorld) snapshot = null;
        if (selected != null && (mc.theWorld != selected.getWorldObj() || selected.isInvalid())) {
            selected = null;
        }
        if (selected == null && snapshot == null) return;
        AnchorVolume box = selected == null ? snapshot : selected.getVolume();
        boolean cubic = selected == null ? snapshotCubic : AnchorLoading.isCubic(mc.theWorld);
        Entity camera = mc.renderViewEntity;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(
                box.minX * 16D - x,
                (cubic ? box.minY * 16D : 0) - y,
                box.minZ * 16D - z,
                (box.maxX + 1D) * 16 - x,
                (cubic ? (box.maxY + 1D) * 16 : 256) - y,
                (box.maxZ + 1D) * 16 - z);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2);
        GL11.glColor4f(0.2F, 0.9F, 0.5F, 1);
        RenderGlobal.drawOutlinedBoundingBox(bounds, -1);
        GL11.glPopAttrib();
    }
}
