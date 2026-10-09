package mods.railcraft.common.util.anchors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import mods.railcraft.common.plugins.forge.PlayerPlugin;
import mods.railcraft.common.util.network.PacketAnchorList;
import mods.railcraft.common.util.network.PacketDispatcher;

public final class CommandAnchors extends CommandBase {

    @Override
    public String getCommandName() {
        return "rcanchors";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/rcanchors [all] [page] | /rcanchors evict <dimension>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        boolean admin = sender.canCommandSenderUseCommand(2, "rcanchors");
        if (args.length == 2 && args[0].equals("evict")) {
            if (!admin) throw new CommandException("commands.generic.permission");
            WorldServer world = DimensionManager.getWorld(parseInt(sender, args[1]));
            if (world == null || !AnchorManager.INSTANCE.evict(world)) {
                throw new CommandException(
                        "Dimension is absent, occupied, spawn-loaded, save-disabled, or has active loaders.");
            }
            sender.addChatMessage(new ChatComponentText("Idle dimension queued for save and unload."));
            return;
        }
        boolean all = args.length > 0 && args[0].equals("all");
        if (all && !admin) throw new CommandException("commands.generic.permission");
        int page = args.length > (all ? 1 : 0) ? parseIntBounded(sender, args[all ? 1 : 0], 1, 100000) - 1 : 0;
        AnchorManager.INSTANCE.rememberAll();
        List<NBTTagCompound> matching = new ArrayList<>();
        AnchorIndex index = AnchorIndex.get();
        if (index != null) for (NBTTagCompound entry : index.entries.values()) {
            if (!all && sender instanceof EntityPlayerMP player
                    && !PlayerPlugin.isSamePlayer(PlayerPlugin.readOwnerFromNBT(entry), player.getGameProfile()))
                continue;
            NBTTagCompound copy = (NBTTagCompound) entry.copy();
            copy.setBoolean("active", AnchorManager.INSTANCE.isActive(UUID.fromString(entry.getString("id"))));
            matching.add(copy);
        }
        int pages = Math.max(1, (matching.size() + PacketAnchorList.PAGE_SIZE - 1) / PacketAnchorList.PAGE_SIZE);
        page = Math.min(page, pages - 1);
        int start = page * PacketAnchorList.PAGE_SIZE;
        List<NBTTagCompound> entries = matching
                .subList(start, Math.min(start + PacketAnchorList.PAGE_SIZE, matching.size()));
        if (sender instanceof EntityPlayerMP player) {
            PacketDispatcher.sendToPlayer(new PacketAnchorList(entries, page, pages, all), player);
        } else {
            for (NBTTagCompound entry : entries) sender.addChatMessage(new ChatComponentText(entry.toString()));
            sender.addChatMessage(
                    new ChatComponentText("Anchors: " + matching.size() + ", page " + (page + 1) + "/" + pages));
        }
    }
}
