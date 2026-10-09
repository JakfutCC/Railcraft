package mods.railcraft.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.gtnewhorizons.navigator.api.NavigatorApi;
import com.gtnewhorizons.navigator.api.model.SupportedMods;
import com.gtnewhorizons.navigator.api.model.buttons.ButtonManager;
import com.gtnewhorizons.navigator.api.model.layers.InteractableLayerManager;
import com.gtnewhorizons.navigator.api.model.layers.LayerRenderer;
import com.gtnewhorizons.navigator.api.model.layers.UniversalInteractableRenderer;
import com.gtnewhorizons.navigator.api.model.locations.ILocationProvider;
import com.gtnewhorizons.navigator.api.model.steps.UniversalLocationInteractableStep;
import com.gtnewhorizons.navigator.api.util.DrawUtils;

import mods.railcraft.common.util.network.PacketAnchorMapRequest;
import mods.railcraft.common.util.network.PacketDispatcher;

/** An X/Z projection, not a claim or a request to load an entire column. */
public final class AnchorMapLayer extends InteractableLayerManager {

    private static AnchorMapLayer instance;
    private final List<Location> locations = new ArrayList<>();
    private World snapshotWorld;
    private long lastRequest;
    private boolean truncated;

    private AnchorMapLayer() {
        super(new ButtonManager() {

            @Override
            public ResourceLocation getIcon(SupportedMods mod, String theme) {
                return new ResourceLocation("railcraft", "textures/items/cart.anchor.png");
            }

            @Override
            public String getButtonText() {
                return "Railcraft anchors";
            }
        });
    }

    public static void init() {
        instance = new AnchorMapLayer();
        NavigatorApi.registerLayerManager(instance);
    }

    public static void receive(List<NBTTagCompound> entries, boolean truncated) {
        if (instance == null) return;
        instance.locations.clear();
        instance.snapshotWorld = Minecraft.getMinecraft().theWorld;
        instance.truncated = truncated;
        for (NBTTagCompound entry : entries) instance.locations.add(new Location(entry, instance.locations.size()));
        instance.clearFullCache();
    }

    @Override
    protected LayerRenderer addLayerRenderer(InteractableLayerManager manager, SupportedMods mod) {
        return new UniversalInteractableRenderer(manager).withRenderStep(location -> new Step((Location) location));
    }

    @Override
    protected Collection<? extends ILocationProvider> generateVisibleLocations(int minX, int minZ, int maxX, int maxZ,
            int dimension) {
        List<Location> visible = new ArrayList<>();
        for (Location location : locations) if (location.getDimensionId() == dimension && location.box[0] * 16L <= maxX
                && (location.box[3] + 1L) * 16 > minX
                && location.box[2] * 16L <= maxZ
                && (location.box[5] + 1L) * 16 > minZ)
            visible.add(location);
        return visible;
    }

    @Override
    public void onUpdatePre(int minX, int maxX, int minZ, int maxZ) {
        if (!isLayerActive()) return;
        if (snapshotWorld != Minecraft.getMinecraft().theWorld) {
            locations.clear();
            clearFullCache();
            snapshotWorld = Minecraft.getMinecraft().theWorld;
            lastRequest = 0;
        }
        long now = System.nanoTime();
        if (now - lastRequest >= 2_000_000_000L) {
            lastRequest = now;
            PacketDispatcher.sendToServer(new PacketAnchorMapRequest(minX, maxX, minZ, maxZ));
        }
    }

    private static final class Location implements ILocationProvider {

        final NBTTagCompound entry;
        final int[] box;
        final long id;

        Location(NBTTagCompound entry, long id) {
            this.entry = entry;
            this.box = entry.getIntArray("bounds");
            this.id = id;
        }

        @Override
        public int getDimensionId() {
            return entry.getInteger("dim");
        }

        @Override
        public double getBlockX() {
            return box[0] * 16D + 0.5;
        }

        @Override
        public double getBlockZ() {
            return box[2] * 16D + 0.5;
        }

        @Override
        public long toLong() {
            return id;
        }
    }

    private static final class Step extends UniversalLocationInteractableStep<Location> {

        Step(Location location) {
            super(location);
            setSize((location.box[3] - location.box[0] + 1D) * 16, (location.box[5] - location.box[2] + 1D) * 16);
        }

        @Override
        public void preRender(double x, double y, float scale, double zoom) {
            setOffset(isJourneyMap ? -blockSize / 2 : 0);
        }

        @Override
        public void draw(double x, double y, float scale, double zoom) {
            int color = location.entry.getBoolean("active") ? 0x40d990 : 0xaaaaaa;
            DrawUtils.drawRect(x, y, getAdjustedWidth(), getAdjustedHeight(), color, 60);
            DrawUtils.drawHollowRect(x, y, getAdjustedWidth(), getAdjustedHeight(), color, 230);
        }

        @Override
        public void getTooltip(List<String> lines) {
            lines.add("Railcraft anchor: " + (location.entry.getBoolean("active") ? "Active" : "Inactive"));
            lines.add(location.entry.getString("owner"));
            lines.add(
                    "Y " + (location.entry.getBoolean("cubic") ? location.box[1] * 16L : 0)
                            + " to "
                            + (location.entry.getBoolean("cubic") ? (location.box[4] + 1L) * 16 - 1 : 255));
            if (instance.truncated) lines.add("First 128 anchors shown; zoom in for more.");
        }
    }
}
