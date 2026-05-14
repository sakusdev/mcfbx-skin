package dev.codex.armatureskin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.codex.armatureskin.config.ArmatureSkinConfig;
import dev.codex.armatureskin.model.ArmatureModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public final class ArmatureSkinRenderer {
    private ArmatureModel model;
    private ArmatureSkinConfig config = ArmatureSkinConfig.defaults();

    public void setModel(ArmatureModel model, ArmatureSkinConfig config) {
        this.model = model;
        this.config = config;
    }

    public void clear() {
        this.model = null;
    }

    public boolean renderPlayer(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource buffers, int light) {
        ArmatureModel current = model;
        if (current == null || !config.enabled()) {
            return false;
        }
        if (config.localPlayerOnly() && player != Minecraft.getInstance().player) {
            return false;
        }

        matrices.pushPose();
        try {
            matrices.scale(config.scale(), config.scale(), config.scale());
            matrices.translate(0.0F, config.yOffset(), 0.0F);
            if (player.isCrouching() && config.mirrorVanillaSneak()) {
                matrices.translate(0.0F, -12.0F, 0.0F);
            }

            Matrix4f[] skinMatrices = buildSkinMatrices(current, player, tickDelta);
            RenderType renderType = config.forceOpaqueSkin()
                    ? RenderType.entitySolid(player.getSkin().texture())
                    : RenderType.entityCutoutNoCull(player.getSkin().texture());
            VertexConsumer consumer = buffers.getBuffer(renderType);
            PoseStack.Pose entry = matrices.last();

            for (ArmatureModel.Mesh mesh : current.meshes()) {
                int[] indices = mesh.indices();
                List<ArmatureModel.Vertex> meshVertices = mesh.vertices();
                for (int i = 0; i + 2 < indices.length; i += 3) {
                    emitTriangle(
                            consumer,
                            entry,
                            meshVertices.get(indices[i]),
                            meshVertices.get(indices[i + 1]),
                            meshVertices.get(indices[i + 2]),
                            skinMatrices,
                            light
                    );
                }
            }
        } finally {
            matrices.popPose();
        }
        return true;
    }

    private Matrix4f[] buildSkinMatrices(ArmatureModel current, AbstractClientPlayer player, float tickDelta) {
        List<ArmatureModel.Bone> bones = current.bones();
        Matrix4f[] global = new Matrix4f[bones.size()];
        Matrix4f[] skin = new Matrix4f[bones.size()];

        float limbAngle = player.walkAnimation.position(tickDelta);
        float limbDistance = player.walkAnimation.speed(tickDelta);
        float age = player.tickCount + tickDelta;
        float headYaw = player.getYHeadRot() - player.yBodyRot;
        float headPitch = player.getXRot();

        for (int i = 0; i < bones.size(); i++) {
            ArmatureModel.Bone bone = bones.get(i);
            Matrix4f local = ProceduralAnimator.animateBone(bone, limbAngle, limbDistance, age, headYaw, headPitch);
            if (bone.parentIndex() >= 0) {
                global[i] = new Matrix4f(global[bone.parentIndex()]).mul(local);
            } else {
                global[i] = local;
            }
            skin[i] = new Matrix4f(global[i]).mul(bone.inverseBindTransform());
        }
        return skin;
    }

    private void emitTriangle(VertexConsumer consumer, PoseStack.Pose entry, ArmatureModel.Vertex a, ArmatureModel.Vertex b, ArmatureModel.Vertex c, Matrix4f[] skinMatrices, int light) {
        Vector3f positionA = skin(a, skinMatrices);
        Vector3f positionB = skin(b, skinMatrices);
        Vector3f positionC = skin(c, skinMatrices);
        Vector3f normal = new Vector3f(positionB).sub(positionA).cross(new Vector3f(positionC).sub(positionA)).normalize();
        if (!Float.isFinite(normal.x()) || !Float.isFinite(normal.y()) || !Float.isFinite(normal.z())) {
            normal.set(0.0F, 1.0F, 0.0F);
        }

        emitVertex(consumer, entry, a, positionA, normal, light);
        emitVertex(consumer, entry, b, positionB, normal, light);
        emitVertex(consumer, entry, c, positionC, normal, light);
    }

    private void emitVertex(VertexConsumer consumer, PoseStack.Pose entry, ArmatureModel.Vertex vertex, Vector3f position, Vector3f normal, int light) {
        Matrix4f modelMatrix = entry.pose();

        consumer.addVertex(modelMatrix, position.x(), position.y(), position.z())
                .setColor(255, 255, 255, 255)
                .setUv(vertex.u(), vertex.v())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, normal.x(), normal.y(), normal.z());
    }

    private Vector3f skin(ArmatureModel.Vertex vertex, Matrix4f[] skinMatrices) {
        int[] boneIndices = vertex.boneIndices();
        float[] weights = vertex.weights();
        if (boneIndices.length == 0 || skinMatrices.length == 0) {
            return new Vector3f(vertex.x(), vertex.y(), vertex.z());
        }

        Vector3f result = new Vector3f();
        Vector3f source = new Vector3f(vertex.x(), vertex.y(), vertex.z());
        for (int i = 0; i < boneIndices.length && i < weights.length; i++) {
            int boneIndex = boneIndices[i];
            if (boneIndex < 0 || boneIndex >= skinMatrices.length || weights[i] <= 0.0F) {
                continue;
            }
            Vector3f transformed = new Vector3f(source).mulPosition(skinMatrices[boneIndex]).mul(weights[i]);
            result.add(transformed);
        }
        return result;
    }
}
