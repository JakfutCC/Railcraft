package mods.railcraft.mixin;

import java.util.Hashtable;

import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import mods.railcraft.common.util.anchors.AnchorManager;

@Mixin(value = DimensionManager.class, remap = false)
public abstract class DimensionUnloadMixin {

    @Inject(method = "unloadWorlds", at = @At("HEAD"))
    private static void enqueueEvictions(Hashtable<Integer, long[]> timings, CallbackInfo ci) {
        AnchorManager.INSTANCE.enqueueEvictions();
    }

    @ModifyVariable(method = "unloadWorlds", at = @At("STORE"), ordinal = 0)
    private static WorldServer checkEviction(WorldServer world) {
        return AnchorManager.INSTANCE.beginEviction(world);
    }

    @WrapOperation(
            method = "unloadWorlds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldServer;saveAllChunks(ZLnet/minecraft/util/IProgressUpdate;)V",
                    remap = true))
    private static void saveOnce(WorldServer world, boolean all, IProgressUpdate progress, Operation<Void> original)
            throws MinecraftException {
        if (!AnchorManager.INSTANCE.consumeEvictionSave(world)) original.call(world, all, progress);
    }
}
