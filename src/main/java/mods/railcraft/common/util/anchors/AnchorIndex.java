package mods.railcraft.common.util.anchors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import mods.railcraft.common.blocks.machine.alpha.TileAnchorWorld;
import mods.railcraft.common.plugins.forge.PlayerPlugin;

/** Locations only: the tile remains authoritative for fuel, ownership and volume. */
public final class AnchorIndex extends WorldSavedData {

    private static final String NAME = "railcraft_anchor_index";
    public final Map<UUID, NBTTagCompound> entries = new LinkedHashMap<>();

    public AnchorIndex(String name) {
        super(name);
    }

    public static AnchorIndex get() {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) return null;
        AnchorIndex index = (AnchorIndex) overworld.mapStorage.loadData(AnchorIndex.class, NAME);
        if (index == null) {
            index = new AnchorIndex(NAME);
            overworld.mapStorage.setData(NAME, index);
        }
        return index;
    }

    public void update(TileAnchorWorld anchor) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", anchor.getUUID().toString());
        tag.setInteger("dim", anchor.getWorldObj().provider.dimensionId);
        tag.setInteger("x", anchor.xCoord);
        tag.setInteger("y", anchor.yCoord);
        tag.setInteger("z", anchor.zCoord);
        tag.setString("type", anchor.getMachineType().getTag());
        tag.setBoolean("restore", anchor.shouldRestore());
        tag.setBoolean("active", anchor.hasActiveTicket());
        tag.setBoolean("waitingForBudget", anchor.isWaitingForBudget());
        tag.setInteger("cost", (int) anchor.getCubeCost());
        AnchorVolume volume = anchor.getVolume();
        tag.setIntArray(
                "bounds",
                new int[] { volume.minX, volume.minY, volume.minZ, volume.maxX, volume.maxY, volume.maxZ });
        tag.setBoolean("cubic", AnchorLoading.isCubic(anchor.getWorldObj()));
        PlayerPlugin.writeOwnerToNBT(tag, anchor.getOwner());
        if (!tag.equals(entries.put(anchor.getUUID(), tag))) markDirty();
    }

    public void remove(UUID id) {
        if (entries.remove(id) != null) markDirty();
    }

    public List<NBTTagCompound> restorationOrder() {
        List<NBTTagCompound> ordered = new ArrayList<>(entries.values());
        ordered.sort(Comparator.comparing(entry -> !entry.getBoolean("active")));
        return ordered;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        entries.clear();
        NBTTagList list = tag.getTagList("anchors", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            try {
                entries.put(UUID.fromString(entry.getString("id")), entry);
            } catch (IllegalArgumentException ignored) {
                // Ignore a malformed index entry, never touch the corresponding world data.
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound entry : entries.values()) list.appendTag(entry.copy());
        tag.setTag("anchors", list);
    }
}
