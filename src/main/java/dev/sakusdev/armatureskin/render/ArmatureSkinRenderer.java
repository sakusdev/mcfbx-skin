package dev.sakusdev.armatureskin.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.sakusdev.armatureskin.config.ArmatureSkinConfig;
import dev.sakusdev.armatureskin.model.ArmatureModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ArmatureSkinRenderer {
    private static final float TARGET_MODEL_HEIGHT = 1.8F;
    private static final float MIN_VALID_TRIANGLE_AREA = 0.0000001F;
    private static final float MIN_NEEDLE_EDGE_LENGTH_SQUARED = 0.0036F;
    private static final float NEEDLE_MIN_EDGE_RATIO_SQUARED = 0.0016F;
    private static final float NEEDLE_AREA_RATIO_SQUARED = 0.00025F;

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

        return renderModel(current, player, yaw, tickDelta, matrices, buffers, light, player.isCrouching());
    }

    public boolean renderFirstPersonModelHand(AbstractClientPlayer player, boolean rightHand, float swingProgress, float equipProgress, float tickDelta, PoseStack matrices, MultiBufferSource buffers, int light) {
        ArmatureModel current = model;
        if (current == null || !config.enabled() || !config.firstPersonModelHands() || player == null || player != Minecraft.getInstance().player) {
            return false;
        }

        ArmatureModel.Bounds armBounds = boundsOfMatching(current, config, rightHand ? ArmatureSkinRenderer::isRightArmMesh : ArmatureSkinRenderer::isLeftArmMesh);
        if (!armBounds.valid()) {
            return false;
        }

        matrices.pushPose();
        try {
            float side = rightHand ? 1.0F : -1.0F;
            float swing = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
            matrices.translate(side * (0.48F - equipProgress * 0.18F), -0.48F + swing * 0.08F, -0.72F - swing * 0.08F);
            matrices.mulPose(Axis.YP.rotationDegrees(side * (18.0F + swing * 10.0F)));
            matrices.mulPose(Axis.XP.rotationDegrees(-22.0F - swing * 18.0F));
            matrices.mulPose(Axis.ZP.rotationDegrees(side * (8.0F + swing * 7.0F)));

            float scale = 0.62F / Math.max(0.001F, armBounds.height());
            matrices.scale(scale, scale, scale);
            matrices.translate(-armBounds.centerX(), -armBounds.centerY(), -armBounds.centerZ());

            Matrix4f[] skinMatrices = buildSkinMatrices(current, player, tickDelta, player.isCrouching());
            PoseStack.Pose entry = matrices.last();
            boolean rendered = false;
            for (ArmatureModel.Mesh mesh : current.meshes()) {
                if (config.isMeshDisabled(mesh.key()) || !(rightHand ? isRightArmMesh(mesh) : isLeftArmMesh(mesh))) {
                    continue;
                }
                SkinRenderTexture renderTexture = renderTextureForMesh(mesh, player);
                VertexConsumer consumer = buffers.getBuffer(renderTexture.renderType());
                rendered |= drawMesh(consumer, entry, mesh, skinMesh(mesh, skinMatrices), light);
            }
            return rendered;
        } finally {
            matrices.popPose();
        }
    }

    public boolean renderGuiPreview(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        ArmatureModel current = model;
        if (current == null || !config.enabled() || width <= 8 || height <= 8) {
            return false;
        }

        guiGraphics.enableScissor(x, y, x + width, y + height);
        guiGraphics.flush();
        RenderSystem.enableDepthTest();
        Lighting.setupForEntityInInventory();
        PoseStack matrices = guiGraphics.pose();
        matrices.pushPose();
        try {
            int modelHeight = Math.max(40, height - 28);
            float pixelScale = modelHeight / TARGET_MODEL_HEIGHT * 0.82F;
            float spin = ((Minecraft.getInstance().player == null ? 0.0F : Minecraft.getInstance().player.tickCount) + partialTick) * 0.45F;
            matrices.translate(x + width * 0.5F, y + height - 12.0F, 200.0F);
            matrices.scale(pixelScale, -pixelScale, pixelScale);
            matrices.mulPose(Axis.YP.rotationDegrees(spin));
            renderModel(current, null, 0.0F, partialTick, matrices, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT, false);
            guiGraphics.flush();
        } finally {
            matrices.popPose();
            Lighting.setupFor3DItems();
            RenderSystem.disableDepthTest();
            guiGraphics.disableScissor();
        }
        return true;
    }

    private boolean renderModel(ArmatureModel current, AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource buffers, int light, boolean crouching) {
        matrices.pushPose();
        try {
            matrices.mulPose(Axis.YP.rotationDegrees(config.modelYawOffsetDegrees() - yaw));
            matrices.translate(0.0F, config.yOffset(), 0.0F);
            ArmatureModel.Bounds bounds = boundsOf(current, config);
            if (!bounds.valid()) {
                return false;
            }
            float autoScale = TARGET_MODEL_HEIGHT / Math.max(0.001F, bounds.height());
            float userScale = config.scale() <= 0.05F ? 1.0F : config.scale();
            float renderScale = autoScale * userScale;
            matrices.scale(renderScale, renderScale, renderScale);
            matrices.translate(-bounds.centerX(), -bounds.minY(), -bounds.centerZ());
            if (crouching && config.mirrorVanillaSneak()) {
                matrices.translate(0.0F, -0.25F / renderScale, 0.0F);
            }

            Matrix4f[] skinMatrices = buildSkinMatrices(current, player, tickDelta, crouching);
            PoseStack.Pose entry = matrices.last();

            List<RenderMesh> primaryMeshes = new ArrayList<>();
            List<RenderMesh> lateMeshes = new ArrayList<>();
            for (ArmatureModel.Mesh mesh : current.meshes()) {
                if (config.isMeshDisabled(mesh.key())) {
                    continue;
                }
                SkinRenderTexture renderTexture = renderTextureForMesh(mesh, player);
                boolean latePass = renderTexture.alphaMode() == SkinRenderTexture.AlphaMode.TRANSLUCENT;
                RenderMesh renderMesh = new RenderMesh(mesh, renderTexture, skinMesh(mesh, skinMatrices));
                if (latePass) {
                    lateMeshes.add(renderMesh);
                } else {
                    primaryMeshes.add(renderMesh);
                }
            }
            drawMeshes(primaryMeshes, entry, buffers, light);
            drawMeshes(lateMeshes, entry, buffers, light);
        } finally {
            matrices.popPose();
        }
        return true;
    }

    private SkinRenderTexture renderTextureForMesh(ArmatureModel.Mesh mesh, AbstractClientPlayer player) {
        SkinRenderTexture renderTexture = textureForMesh(mesh, player);
        if (renderTexture.alphaMode() == SkinRenderTexture.AlphaMode.TRANSLUCENT && !isTrueTranslucencyMesh(mesh)) {
            return renderTexture.withAlphaMode(SkinRenderTexture.AlphaMode.CUTOUT);
        }
        return renderTexture;
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
        if (texture != null) {
            return texture;
        }
        if (player != null) {
            return new SkinRenderTexture(player.getSkin().texture(), SkinRenderTexture.AlphaMode.CUTOUT);
        }
        return new SkinRenderTexture(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png"), SkinRenderTexture.AlphaMode.OPAQUE);
    }

    private Matrix4f[] buildSkinMatrices(ArmatureModel current, AbstractClientPlayer player, float tickDelta, boolean crouching) {
        List<ArmatureModel.Bone> bones = current.bones();
        Matrix4f[] global = new Matrix4f[bones.size()];
        Matrix4f[] skin = new Matrix4f[bones.size()];
        int[] armAimChildren = armAimChildren(bones);

        float limbAngle = config.animationEnabled() && player != null ? player.walkAnimation.position(tickDelta) : 0.0F;
        float limbDistance = config.animationEnabled() && player != null ? player.walkAnimation.speed(tickDelta) : 0.0F;
        float age = player == null ? partialPreviewAge(tickDelta) : player.tickCount + tickDelta;
        float headYaw = player == null ? 0.0F : player.getYHeadRot() - player.yBodyRot;
        float headPitch = player == null ? 0.0F : player.getXRot();
        float armWalk = armWalk(limbAngle, limbDistance, config.animationStrength());

        for (int i = 0; i < bones.size(); i++) {
            ArmatureModel.Bone bone = bones.get(i);
            Matrix4f local = ProceduralAnimator.animateBone(bone, limbAngle, limbDistance, age, headYaw, headPitch, config.animationStrength(), crouching);
            if (i < armAimChildren.length && armAimChildren[i] >= 0) {
                Matrix4f parentGlobal = bone.parentIndex() >= 0 ? global[bone.parentIndex()] : new Matrix4f();
                aimUpperArmAtRestPose(local, parentGlobal, bones.get(armAimChildren[i]), isLeftBoneName(bone.name()), armWalk);
            }
            if (bone.parentIndex() >= 0) {
                global[i] = new Matrix4f(global[bone.parentIndex()]).mul(local);
            } else {
                global[i] = local;
            }
            skin[i] = new Matrix4f(global[i]).mul(bone.inverseBindTransform());
        }
        return skin;
    }

    private float partialPreviewAge(float tickDelta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        return player == null ? tickDelta : player.tickCount + tickDelta;
    }

    private static float armWalk(float limbAngle, float limbDistance, float strength) {
        float animationStrength = Math.max(0.0F, Math.min(strength, 1.5F));
        float speed = Math.min(Math.max(limbDistance - 0.02F, 0.0F), 0.65F) * animationStrength * 2.4F;
        float phase = limbAngle * 0.6662F;
        return clamp((float) Math.sin(phase) * speed, -0.42F, 0.42F);
    }

    private static int[] armAimChildren(List<ArmatureModel.Bone> bones) {
        int[] children = new int[bones.size()];
        java.util.Arrays.fill(children, -1);
        for (int lowerIndex = 0; lowerIndex < bones.size(); lowerIndex++) {
            if (!isLowerArmBoneName(bones.get(lowerIndex).name())) {
                continue;
            }
            int childOnPath = lowerIndex;
            int parent = bones.get(lowerIndex).parentIndex();
            while (parent >= 0 && parent < bones.size()) {
                if (isUpperArmBoneName(bones.get(parent).name())) {
                    children[parent] = childOnPath;
                    break;
                }
                childOnPath = parent;
                parent = bones.get(parent).parentIndex();
            }
        }
        return children;
    }

    private static void aimUpperArmAtRestPose(Matrix4f local, Matrix4f parentGlobal, ArmatureModel.Bone aimChild, boolean left, float walk) {
        Vector3f childDirection = translationOf(aimChild.localBindTransform());
        if (childDirection.lengthSquared() <= 0.000001F) {
            return;
        }
        childDirection.normalize();

        Matrix4f upperBasis = new Matrix4f(parentGlobal).mul(local);
        Vector3f currentModelDirection = upperBasis.transformDirection(childDirection, new Vector3f());
        if (currentModelDirection.lengthSquared() <= 0.000001F) {
            return;
        }
        currentModelDirection.normalize();
        if (currentModelDirection.y() < -0.55F) {
            return;
        }

        Matrix4f inverseUpperBasis = new Matrix4f(upperBasis);
        if (Math.abs(inverseUpperBasis.determinant()) <= 0.000001F) {
            return;
        }
        inverseUpperBasis.invert();

        float side = left ? -1.0F : 1.0F;
        float armForward = (left ? -walk : walk) * 0.22F;
        Vector3f targetModelDirection = new Vector3f(side * 0.18F, -1.0F, armForward).normalize();
        Vector3f targetLocalDirection = inverseUpperBasis.transformDirection(targetModelDirection, new Vector3f());
        if (targetLocalDirection.lengthSquared() <= 0.000001F) {
            return;
        }
        targetLocalDirection.normalize();
        if (childDirection.dot(targetLocalDirection) > 0.995F) {
            return;
        }
        local.rotate(new Quaternionf().rotationTo(childDirection, targetLocalDirection));
    }

    private static Vector3f translationOf(Matrix4f matrix) {
        return new Vector3f(matrix.m30(), matrix.m31(), matrix.m32());
    }

    private void drawMeshes(List<RenderMesh> meshes, PoseStack.Pose entry, MultiBufferSource buffers, int light) {
        for (RenderMesh mesh : meshes) {
            VertexConsumer consumer = buffers.getBuffer(mesh.texture().renderType());
            drawMesh(consumer, entry, mesh.mesh(), mesh.skinned(), light);
        }
    }

    private boolean drawMesh(VertexConsumer consumer, PoseStack.Pose entry, ArmatureModel.Mesh mesh, SkinnedMesh skinned, int light) {
        boolean emitted = false;
        int[] indices = mesh.indices();
        List<ArmatureModel.Vertex> vertices = mesh.vertices();
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int ia = indices[i];
            int ib = indices[i + 1];
            int ic = indices[i + 2];
            if (ia < 0 || ib < 0 || ic < 0 || ia >= vertices.size() || ib >= vertices.size() || ic >= vertices.size()) {
                continue;
            }
            if (isBrokenTriangle(mesh, skinned.positions()[ia], skinned.positions()[ib], skinned.positions()[ic])) {
                continue;
            }
            emitVertex(consumer, entry, vertices.get(ia), skinned.positions()[ia], skinned.normals()[ia], light);
            emitVertex(consumer, entry, vertices.get(ib), skinned.positions()[ib], skinned.normals()[ib], light);
            emitVertex(consumer, entry, vertices.get(ic), skinned.positions()[ic], skinned.normals()[ic], light);
            emitted = true;
        }
        return emitted;
    }

    private SkinnedMesh skinMesh(ArmatureModel.Mesh mesh, Matrix4f[] skinMatrices) {
        List<ArmatureModel.Vertex> vertices = mesh.vertices();
        Vector3f[] positions = new Vector3f[vertices.size()];
        Vector3f[] normals = new Vector3f[vertices.size()];
        ArmatureModel.Bounds bounds = ArmatureModel.Bounds.invalid();
        for (int i = 0; i < vertices.size(); i++) {
            ArmatureModel.Vertex vertex = vertices.get(i);
            positions[i] = skinPosition(vertex, mesh.meshToModelTransform(), skinMatrices);
            normals[i] = skinNormal(vertex, mesh.meshToModelTransform(), skinMatrices);
            bounds = bounds.include(positions[i]);
        }
        return new SkinnedMesh(positions, normals, bounds);
    }

    private boolean isBrokenTriangle(ArmatureModel.Mesh mesh, Vector3f a, Vector3f b, Vector3f c) {
        if (!isFinite(a) || !isFinite(b) || !isFinite(c)) {
            return true;
        }
        float ab = a.distanceSquared(b);
        float bc = b.distanceSquared(c);
        float ca = c.distanceSquared(a);
        float maxEdge = Math.max(ab, Math.max(bc, ca));
        if (maxEdge <= MIN_VALID_TRIANGLE_AREA) {
            return true;
        }
        float doubleArea = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).length();
        if (doubleArea <= MIN_VALID_TRIANGLE_AREA) {
            return true;
        }

        float longTriangleLimit = mesh.longTriangleEdgeLimitSquared();
        if (Float.isFinite(longTriangleLimit) && maxEdge > longTriangleLimit) {
            return true;
        }

        ArmatureModel.Bounds bindBounds = mesh.bindBounds();
        if (bindBounds == null || !bindBounds.valid()) {
            return false;
        }
        float bindDiagonal = bindBounds.width() * bindBounds.width()
                + bindBounds.height() * bindBounds.height()
                + bindBounds.depth() * bindBounds.depth();
        if (bindDiagonal <= 0.0000001F) {
            return false;
        }

        float longEnoughToBeVisible = Math.max(MIN_NEEDLE_EDGE_LENGTH_SQUARED, bindDiagonal * 0.035F);
        if (maxEdge < longEnoughToBeVisible) {
            return false;
        }

        float minEdge = Math.min(ab, Math.min(bc, ca));
        boolean needleByEdgeRatio = minEdge < maxEdge * NEEDLE_MIN_EDGE_RATIO_SQUARED;
        boolean needleByArea = doubleArea * doubleArea < maxEdge * maxEdge * NEEDLE_AREA_RATIO_SQUARED;
        return needleByEdgeRatio || needleByArea;
    }

    private boolean isFinite(Vector3f vector) {
        return vector != null && Float.isFinite(vector.x()) && Float.isFinite(vector.y()) && Float.isFinite(vector.z());
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

    private Vector3f skinPosition(ArmatureModel.Vertex vertex, Matrix4f meshToModelTransform, Matrix4f[] skinMatrices) {
        int[] boneIndices = vertex.boneIndices();
        float[] weights = vertex.weights();
        Vector3f source = new Vector3f(vertex.x(), vertex.y(), vertex.z());
        Vector3f fallback = new Vector3f(source).mulPosition(meshToModelTransform == null ? new Matrix4f() : meshToModelTransform);
        if (boneIndices.length == 0 || skinMatrices.length == 0) {
            return fallback;
        }

        Vector3f result = new Vector3f();
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
            return fallback;
        }
        if (appliedWeight < 0.999F) {
            result.fma(1.0F - appliedWeight, fallback);
        }
        return result;
    }

    private Vector3f skinNormal(ArmatureModel.Vertex vertex, Matrix4f meshToModelTransform, Matrix4f[] skinMatrices) {
        Vector3f source = new Vector3f(vertex.nx(), vertex.ny(), vertex.nz());
        if (source.lengthSquared() <= 0.000001F) {
            source.set(0.0F, 1.0F, 0.0F);
        }
        source.normalize();
        Vector3f fallback = new Vector3f(source).mulDirection(meshToModelTransform == null ? new Matrix4f() : meshToModelTransform);
        if (fallback.lengthSquared() <= 0.000001F) {
            fallback.set(0.0F, 1.0F, 0.0F);
        } else {
            fallback.normalize();
        }

        int[] boneIndices = vertex.boneIndices();
        float[] weights = vertex.weights();
        if (boneIndices.length == 0 || skinMatrices.length == 0) {
            return fallback;
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
            return fallback;
        }
        if (appliedWeight < 0.999F) {
            result.fma(1.0F - appliedWeight, fallback);
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

    private static boolean isTrueTranslucencyMesh(ArmatureModel.Mesh mesh) {
        String text = (mesh.materialName() + " " + mesh.textureHint() + " " + mesh.name()).toLowerCase(java.util.Locale.ROOT);
        return text.contains("transparent") || text.contains("translucent") || text.contains("glass") || text.contains("blend");
    }

    private static boolean isLeftArmMesh(ArmatureModel.Mesh mesh) {
        String key = armMatchText(mesh);
        return containsAny(key, "leftarm", "left_arm", "left hand", "lefthand", "_l_", ".l.", "-l-", "arm_l", "hand_l", "j_bip_l");
    }

    private static boolean isRightArmMesh(ArmatureModel.Mesh mesh) {
        String key = armMatchText(mesh);
        return containsAny(key, "rightarm", "right_arm", "right hand", "righthand", "_r_", ".r.", "-r-", "arm_r", "hand_r", "j_bip_r");
    }

    private static boolean isUpperArmBoneName(String value) {
        String name = normalizeBoneName(value);
        if (isAuxiliaryArmBoneName(name) || isArmTerminalBoneName(name)) {
            return false;
        }
        return startsWithAny(name, "upperarm", "upper_arm", "leftupperarm", "rightupperarm", "leftarm", "rightarm")
                || containsAny(name, "j_bip_l_upperarm", "j_bip_r_upperarm", "mixamorig:leftarm", "mixamorig:rightarm", "mixamorig_leftarm", "mixamorig_rightarm");
    }

    private static boolean isLowerArmBoneName(String value) {
        String name = normalizeBoneName(value);
        if (isAuxiliaryArmBoneName(name) || isArmTerminalBoneName(name)) {
            return false;
        }
        return startsWithAny(name, "lowerarm", "lower_arm", "forearm", "leftlowerarm", "rightlowerarm")
                || containsAny(name, "j_bip_l_lowerarm", "j_bip_r_lowerarm", "mixamorig:leftforearm", "mixamorig:rightforearm", "mixamorig_leftforearm", "mixamorig_rightforearm");
    }

    private static boolean isLeftBoneName(String value) {
        String name = normalizeBoneName(value);
        return name.contains("left")
                || name.contains("_l_")
                || name.contains(".l.")
                || name.contains(":l_")
                || name.contains("j_bip_l")
                || name.endsWith(".l")
                || name.endsWith("_l")
                || name.endsWith(" l");
    }

    private static boolean isAuxiliaryArmBoneName(String name) {
        return containsAny(name, "twist", "roll", "adjust", "correct", "helper", "offset", "aim", "ik", "pole");
    }

    private static boolean isArmTerminalBoneName(String name) {
        return containsAny(name, "hand", "wrist", "finger", "thumb", "index", "middle", "ring", "pinky");
    }

    private static String normalizeBoneName(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String armMatchText(ArmatureModel.Mesh mesh) {
        return (mesh.key() + " " + mesh.name() + " " + mesh.displayName()).toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean startsWithAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private static ArmatureModel.Bounds boundsOf(ArmatureModel model, ArmatureSkinConfig config) {
        ArmatureModel.Bounds bounds = ArmatureModel.Bounds.invalid();
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            if (!config.isMeshDisabled(mesh.key())) {
                bounds = bounds.union(mesh.bindBounds());
            }
        }
        return bounds;
    }

    private static ArmatureModel.Bounds boundsOfMatching(ArmatureModel model, ArmatureSkinConfig config, java.util.function.Predicate<ArmatureModel.Mesh> predicate) {
        ArmatureModel.Bounds bounds = ArmatureModel.Bounds.invalid();
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            if (!config.isMeshDisabled(mesh.key()) && predicate.test(mesh)) {
                bounds = bounds.union(mesh.bindBounds());
            }
        }
        return bounds;
    }

    private record RenderMesh(ArmatureModel.Mesh mesh, SkinRenderTexture texture, SkinnedMesh skinned) {
    }

    private record SkinnedMesh(Vector3f[] positions, Vector3f[] normals, ArmatureModel.Bounds bounds) {
    }
}
