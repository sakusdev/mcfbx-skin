package dev.codex.armatureskin.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.codex.armatureskin.ArmatureSkinMod;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void armatureFbxSkin$render(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (ArmatureSkinMod.renderer().renderPlayer(player, yaw, tickDelta, matrices, buffers, light)) {
            ci.cancel();
        }
    }
}
