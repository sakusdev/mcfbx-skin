package dev.codex.armatureskin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.codex.armatureskin.config.ArmatureSkinConfig;
import dev.codex.armatureskin.model.ArmatureModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

public final class ArmatureSkinRenderer {
    private static final float TARGET_MODEL_HEIGHT = 1.8F;

    private ArmatureModel model;
    private ArmatureSkinConfig config = ArmatureSkinConfig.defaults();
    private SkinRenderTexture texture;
    private Map<String, SkinRenderTexture> materialTextures = Map.of();
    private Map<String, SkinRenderTexture> meshTextures = Map.of();

    public void setModel(ArmatureModel model, ArmatureSkinConfig config, SkinRenderTexture texture) {
        setModel(model, config, texture, Map.of(), Map.of());
    }

    public void setModel(ArmatureModel model, ArmatureSkinConfig config, SkinRenderTexture texture, Map<String, SkinRenderTexture> materialTextures) {
        setModel(model, config, texture, materialTextures, Map.of());
    }

    public void setModel(ArmatureModel model, ArmatureSkinConfig config, SkinRenderTexture texture, Map<String, SkinRenderTexture> materialTextures, Map<String, SkinRenderTexture> meshTextures) {
        this.model = model;
        this.config = config;
        this.texture = texture;
        this.materialTextures = Map.copyOf(materialTextures == null ? Map.of() : materialTextures);
        this.meshTextures = Map.copyOf(meshTextures == null ? Map.of() : meshTextures);
    }

    public void clear() {
        this.model = null;
        this.texture = null;
        this.materialTextures = Map.of();
        this.meshTextures = Map.of();
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
            matrices.mulPose(Axis.YP.rotationDegrees(config.modelYawOffsetDegrees() - yaw));
            matrices.translate(0.0F, config.yOffset(), 0.0F);
            Bounds bounds = Bounds.of(current, config);
            if (!bounds.valid()) {
                return false;
            }
            float autoScale = TARGET_MODEL_HEIGHT / Math.max(0.001F, bounds.height());
            float userScale = config.scale() <= 0.05F ? 1.0F : config.scale();
            float renderScale = autoScale * userScale;
            matrices.scale(renderScale, renderScale, renderScale);
            matrices.translate(-bounds.centerX(), -bounds.minY(), -bounds.centerZ());
            if (player.isCrouching() && config.mirrorVanillaSneak()) {
                matrices.translate(0.0F, -0.25F / renderScale, 0.0F);
            }

            Matrix4f[] skinMatrices = buildSkinMatrices(current, player, tickDelta);
            PoseStack.Pose entry = matrices.last();

            for (ArmatureModel.Mesh mesh : current.meshes()) {
                if (config.isMeshDisabled(mesh.key())) {
                    continue;
                }
                SkinRenderTexture renderTexture = textureForMesh(mesh, player);
                RenderType renderType = renderTexture.renderType();
                VertexConsumer consumer = buffers.getBuffer(renderType);
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

    private SkinRenderTexture textureForMesh(ArmatureModel.Mesh mesh, AbstractClientPlayer player) {
        SkinRenderTexture assignedTexture = meshTextures.get(mesh.key());
        if (assignedTexture != null) {
            return assignedTexture;
        }
        SkinRenderTexture hintedTexture = materialTextures.get(normalizeMaterialName(mesh.textureHint()));
        if (hintedTexture != null) {
            return hintedTexture;
        }
        SkinRenderTexture materialTexture = materialTextures.get(normalizeMaterialName(mesh.materialName()));
        if (materialTexture != null) {
            return materialTexture;
        }
        return texture == null ? new SkinRenderTexture(player.getSkin().texture(), SkinRenderTexture.AlphaMode.CUTOUT) : texture;
    }

    private Matrix4f[] buildSkinMatrices(ArmatureModel current, AbstractClientPlayer player, float tickDelta) {
        List<ArmatureModel.Bone> bones = current.bones();
        Matrix4f[] global = new Matrix4f[bones.size()];
        Matrix4f[] skin = new Matrix4f[bones.size()];

        float limbAngle = config.animationEnabled() ? player.walkAnimation.position(tickDelta) : 0.0F;
        float limbDistance = config.animationEnabled() ? player.walkAnimation.speed(tickDelta) : 0.0F;
        float age = player.tickCount + tickDelta;
        float headYaw = player.getYHeadRot() - player.yBodyRot;
        float headPitch = player.getXRot();

        for (int i = 0; i < bones.size(); i++) {
            ArmatureModel.Bone bone = bones.get(i);
            Matrix4f local = ProceduralAnimator.animateBone(bone, limbAngle, limbDistance, age, headYaw, headPitch, config.animationStrength());
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
        Vector3f faceNormal = new Vector3f(positionB).sub(positionA).cross(new Vector3f(positionC).sub(positionA));
        if (faceNormal.lengthSquared() > 0.000001F) {
            faceNormal.normalize();
        } else {
            faceNormal.set(0.0F, 1.0F, 0.0F);
        }

        emitVertex(consumer, entry, a, positionA, skinNormal(a, faceNormal, skinMatrices), light);
        emitVertex(consumer, entry, b, positionB, skinNormal(b, faceNormal, skinMatrices), light);
        emitVertex(consumer, entry, c, positionC, skinNormal(c, faceNormal, skinMatrices), light);
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
        float appliedWeight = 0.0F;
        for (int i = 0; i < boneIndices.length && i < weights.length; i++) {
            int boneIndex = boneIndices[i];
            if (boneIndex < 0 || boneIndex >= skinMatrices.length || weights[i] <= 0.0F) {
                continue;
            }
            Vector3f transformed = new Vector3f(source).mulPosition(skinMatrices[boneIndex]).mul(weights[i]);
            result.add(transformed);
            appliedWeight += weights[i];
        }
        if (appliedWeight <= 0.0001F) {
            return source;
        }
        if (appliedWeight < 0.999F) {
            result.fma(1.0F - appliedWeight, source);
        }
        return result;
    }

    private Vector3f skinNormal(ArmatureModel.Vertex vertex, Vector3f fallback, Matrix4f[] skinMatrices) {
        Vector3f source = new Vector3f(vertex.nx(), vertex.ny(), vertex.nz());
        if (source.lengthSquared() <= 0.000001F) {
            return new Vector3f(fallback);
        }
        source.normalize();

        int[] boneIndices = vertex.boneIndices();
        float[] weights = vertex.weights();
        if (boneIndices.length == 0 || skinMatrices.length == 0) {
            return source;
        }

        Vector3f result = new Vector3f();
        float appliedWeight = 0.0F;
        for (int i = 0; i < boneIndices.length && i < weights.length; i++) {
            int boneIndex = boneIndices[i];
            if (boneIndex < 0 || boneIndex >= skinMatrices.length || weights[i] <= 0.0F) {
                continue;
            }
            Vector3f transformed = new Vector3f(source).mulDirection(skinMatrices[boneIndex]).mul(weights[i]);
            result.add(transformed);
            appliedWeight += weights[i];
        }
        if (appliedWeight <= 0.0001F) {
            return source;
        }
        if (appliedWeight < 0.999F) {
            result.fma(1.0F - appliedWeight, source);
        }
        if (result.lengthSquared() <= 0.000001F) {
            return new Vector3f(fallback);
        }
        return result.normalize();
    }

    public static String normalizeMaterialName(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return "";
        }
        return materialName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static Bounds of(ArmatureModel model, ArmatureSkinConfig config) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (ArmatureModel.Mesh mesh : model.meshes()) {
                if (config.isMeshDisabled(mesh.key())) {
                    continue;
                }
                for (ArmatureModel.Vertex vertex : mesh.vertices()) {
                    minX = Math.min(minX, vertex.x());
                    minY = Math.min(minY, vertex.y());
                    minZ = Math.min(minZ, vertex.z());
                    maxX = Math.max(maxX, vertex.x());
                    maxY = Math.max(maxY, vertex.y());
                    maxZ = Math.max(maxZ, vertex.z());
                }
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        boolean valid() {
            return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                    && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
                    && height() > 0.0F;
        }

        float height() {
            return maxY - minY;
        }

        float centerX() {
            return (minX + maxX) * 0.5F;
        }

        float centerZ() {
            return (minZ + maxZ) * 0.5F;
        }
    }
}
