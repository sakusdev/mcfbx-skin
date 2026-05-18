package dev.codex.armatureskin.fbx;

import dev.codex.armatureskin.model.ArmatureModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVertexWeight;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AssimpFbxLoader {
    private static final int IMPORT_FLAGS = Assimp.aiProcess_Triangulate
            | Assimp.aiProcess_LimitBoneWeights
            | Assimp.aiProcess_ValidateDataStructure
            | Assimp.aiProcess_FindInvalidData
            | Assimp.aiProcess_PopulateArmatureData
            | Assimp.aiProcess_SortByPType
            | Assimp.aiProcess_FlipUVs;

    ArmatureModel load(byte[] data, String sourceName) throws IOException {
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        AIScene scene;
        try {
            buffer.put(data).flip();
            scene = Assimp.aiImportFileFromMemory(buffer, IMPORT_FLAGS, extensionHint(sourceName));
        } finally {
            MemoryUtil.memFree(buffer);
        }
        if (scene == null) {
            throw new IOException("Assimp failed to import " + sourceName + ": " + Assimp.aiGetErrorString());
        }

        try {
            return convert(scene);
        } finally {
            Assimp.aiReleaseImport(scene);
        }
    }

    private static ArmatureModel convert(AIScene scene) throws IOException {
        PointerBuffer sceneMeshes = scene.mMeshes();
        if (sceneMeshes == null || scene.mNumMeshes() <= 0) {
            throw new IOException("Assimp imported the FBX, but it contains no meshes.");
        }

        SceneNodes nodes = SceneNodes.collect(scene.mRootNode());
        Matrix4f rootInverse = nodes.rootInverse();
        Map<String, Integer> boneIndexByName = new LinkedHashMap<>();
        Map<String, Matrix4f> inverseBindByBone = new LinkedHashMap<>();
        collectBones(scene, boneIndexByName, inverseBindByBone);
        boneIndexByName = orderBones(boneIndexByName.keySet(), nodes);

        List<ArmatureModel.Bone> bones = buildBones(boneIndexByName, inverseBindByBone, nodes, rootInverse);
        List<MaterialInfo> materials = readMaterials(scene);
        List<ArmatureModel.Mesh> meshes = new ArrayList<>();
        for (int meshIndex = 0; meshIndex < scene.mNumMeshes(); meshIndex++) {
            AIMesh mesh = AIMesh.create(sceneMeshes.get(meshIndex));
            ArmatureModel.Mesh converted = convertMesh(mesh, meshIndex, materialInfo(materials, mesh.mMaterialIndex()), boneIndexByName, nodes.meshTransform(meshIndex, rootInverse));
            if (!converted.vertices().isEmpty() && converted.indices().length > 0) {
                meshes.add(converted);
            }
        }

        if (meshes.isEmpty()) {
            throw new IOException("Assimp imported the FBX, but all meshes were empty.");
        }
        return new ArmatureModel(bones, meshes);
    }

    private static void collectBones(AIScene scene, Map<String, Integer> boneIndexByName, Map<String, Matrix4f> inverseBindByBone) {
        PointerBuffer sceneMeshes = scene.mMeshes();
        for (int meshIndex = 0; meshIndex < scene.mNumMeshes(); meshIndex++) {
            AIMesh mesh = AIMesh.create(sceneMeshes.get(meshIndex));
            PointerBuffer meshBones = mesh.mBones();
            if (meshBones == null) {
                continue;
            }
            for (int boneSlot = 0; boneSlot < mesh.mNumBones(); boneSlot++) {
                AIBone bone = AIBone.create(meshBones.get(boneSlot));
                String boneName = name(bone.mName());
                if (boneName.isBlank()) {
                    continue;
                }
                boneIndexByName.computeIfAbsent(boneName, ignored -> boneIndexByName.size());
                inverseBindByBone.putIfAbsent(boneName, toJoml(bone.mOffsetMatrix()));
            }
        }
    }

    private static Map<String, Integer> orderBones(Set<String> boneNames, SceneNodes nodes) {
        LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String boneName : boneNames) {
            appendBone(boneName, boneNames, nodes, ordered, visiting, visited);
        }
        return ordered;
    }

    private static void appendBone(String boneName, Set<String> boneNames, SceneNodes nodes, LinkedHashMap<String, Integer> ordered, Set<String> visiting, Set<String> visited) {
        if (visited.contains(boneName)) {
            return;
        }
        if (!visiting.add(boneName)) {
            ordered.computeIfAbsent(boneName, ignored -> ordered.size());
            return;
        }
        String parentName = nodes.nearestBoneParent(boneName, boneNames);
        if (parentName != null) {
            appendBone(parentName, boneNames, nodes, ordered, visiting, visited);
        }
        visiting.remove(boneName);
        visited.add(boneName);
        ordered.computeIfAbsent(boneName, ignored -> ordered.size());
    }

    private static List<ArmatureModel.Bone> buildBones(Map<String, Integer> boneIndexByName, Map<String, Matrix4f> inverseBindByBone, SceneNodes nodes, Matrix4f rootInverse) {
        ArmatureModel.Bone[] bones = new ArmatureModel.Bone[boneIndexByName.size()];
        for (Map.Entry<String, Integer> entry : boneIndexByName.entrySet()) {
            String boneName = entry.getKey();
            int boneIndex = entry.getValue();
            String parentName = nodes.nearestBoneParent(boneName, boneIndexByName);
            int parentIndex = parentName == null ? -1 : boneIndexByName.get(parentName);
            Matrix4f globalBind = nodes.normalizedGlobal(boneName, rootInverse);
            Matrix4f localBind;
            if (parentName == null) {
                localBind = new Matrix4f(globalBind);
            } else {
                localBind = nodes.normalizedGlobal(parentName, rootInverse).invert().mul(globalBind);
            }
            Matrix4f inverseBind = inverseBindByBone.getOrDefault(boneName, new Matrix4f());
            bones[boneIndex] = new ArmatureModel.Bone(stableId(boneName), boneName, parentIndex, localBind, new Matrix4f(inverseBind));
        }
        return List.of(bones);
    }

    private static ArmatureModel.Mesh convertMesh(AIMesh mesh, int meshIndex, MaterialInfo material, Map<String, Integer> boneIndexByName, Matrix4f staticMeshTransform) {
        int vertexCount = mesh.mNumVertices();
        AIVector3D.Buffer positions = mesh.mVertices();
        if (positions == null || vertexCount <= 0) {
            return new ArmatureModel.Mesh("assimp:" + meshIndex, name(mesh.mName()), material.name(), material.textureHint(), List.of(), new int[0]);
        }

        List<VertexWeight>[] weightsByVertex = collectWeights(mesh, vertexCount, boneIndexByName);
        boolean skinnedMesh = hasWeights(weightsByVertex);
        AIVector3D.Buffer texCoords = mesh.mTextureCoords(0);
        AIVector3D.Buffer normals = mesh.mNormals();
        List<ArmatureModel.Vertex> vertices = new ArrayList<>(vertexCount);
        for (int vertexIndex = 0; vertexIndex < vertexCount; vertexIndex++) {
            AIVector3D position = positions.get(vertexIndex);
            Vector3f bakedPosition = new Vector3f(position.x(), position.y(), position.z());
            if (!skinnedMesh) {
                bakedPosition.mulPosition(staticMeshTransform);
            }

            Vector3f bakedNormal = new Vector3f(0.0F, 1.0F, 0.0F);
            if (normals != null && vertexIndex < normals.limit()) {
                AIVector3D normal = normals.get(vertexIndex);
                bakedNormal.set(normal.x(), normal.y(), normal.z());
                if (!skinnedMesh) {
                    bakedNormal.mulDirection(staticMeshTransform);
                }
                if (bakedNormal.lengthSquared() > 0.000001F) {
                    bakedNormal.normalize();
                } else {
                    bakedNormal.set(0.0F, 1.0F, 0.0F);
                }
            }

            float u = 0.0F;
            float v = 0.0F;
            if (texCoords != null && vertexIndex < texCoords.limit()) {
                AIVector3D uv = texCoords.get(vertexIndex);
                u = uv.x();
                v = uv.y();
            }

            List<VertexWeight> normalized = normalizedTopWeights(weightsByVertex[vertexIndex]);
            int[] boneIndices = new int[normalized.size()];
            float[] boneWeights = new float[normalized.size()];
            for (int i = 0; i < normalized.size(); i++) {
                boneIndices[i] = normalized.get(i).boneIndex();
                boneWeights[i] = normalized.get(i).weight();
            }
            vertices.add(new ArmatureModel.Vertex(bakedPosition.x(), bakedPosition.y(), bakedPosition.z(), u, v, boneIndices, boneWeights, bakedNormal.x(), bakedNormal.y(), bakedNormal.z()));
        }

        List<Integer> indices = new ArrayList<>(mesh.mNumFaces() * 3);
        AIFace.Buffer faces = mesh.mFaces();
        if (faces != null) {
            for (int faceIndex = 0; faceIndex < mesh.mNumFaces(); faceIndex++) {
                AIFace face = faces.get(faceIndex);
                IntBuffer faceIndices = face.mIndices();
                if (faceIndices == null || face.mNumIndices() < 3) {
                    continue;
                }
                int first = faceIndices.get(0);
                for (int i = 1; i + 1 < face.mNumIndices(); i++) {
                    indices.add(first);
                    indices.add(faceIndices.get(i));
                    indices.add(faceIndices.get(i + 1));
                }
            }
        }

        String meshName = name(mesh.mName());
        String key = "assimp:" + meshIndex + "|mesh:" + normalizeKey(meshName) + "|material:" + normalizeKey(material.name());
        return new ArmatureModel.Mesh(key, meshName, material.name(), material.textureHint(), vertices, indices.stream().mapToInt(Integer::intValue).toArray());
    }

    @SuppressWarnings("unchecked")
    private static List<VertexWeight>[] collectWeights(AIMesh mesh, int vertexCount, Map<String, Integer> boneIndexByName) {
        List<VertexWeight>[] weightsByVertex = new List[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            weightsByVertex[i] = new ArrayList<>(4);
        }

        PointerBuffer meshBones = mesh.mBones();
        if (meshBones == null) {
            return weightsByVertex;
        }

        for (int boneSlot = 0; boneSlot < mesh.mNumBones(); boneSlot++) {
            AIBone bone = AIBone.create(meshBones.get(boneSlot));
            Integer boneIndex = boneIndexByName.get(name(bone.mName()));
            if (boneIndex == null) {
                continue;
            }
            AIVertexWeight.Buffer weights = bone.mWeights();
            if (weights == null) {
                continue;
            }
            for (int weightIndex = 0; weightIndex < bone.mNumWeights(); weightIndex++) {
                AIVertexWeight weight = weights.get(weightIndex);
                int vertexId = weight.mVertexId();
                if (vertexId >= 0 && vertexId < vertexCount && weight.mWeight() > 0.0F) {
                    weightsByVertex[vertexId].add(new VertexWeight(boneIndex, weight.mWeight()));
                }
            }
        }
        return weightsByVertex;
    }

    private static boolean hasWeights(List<VertexWeight>[] weightsByVertex) {
        for (List<VertexWeight> weights : weightsByVertex) {
            if (!weights.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<VertexWeight> normalizedTopWeights(List<VertexWeight> weights) {
        if (weights == null || weights.isEmpty()) {
            return List.of();
        }
        List<VertexWeight> top = weights.stream()
                .sorted((a, b) -> Float.compare(b.weight(), a.weight()))
                .limit(4)
                .toList();
        float total = 0.0F;
        for (VertexWeight weight : top) {
            total += weight.weight();
        }
        if (total <= 0.0F) {
            return List.of();
        }
        List<VertexWeight> normalized = new ArrayList<>(top.size());
        for (VertexWeight weight : top) {
            normalized.add(new VertexWeight(weight.boneIndex(), weight.weight() / total));
        }
        return normalized;
    }

    private static List<MaterialInfo> readMaterials(AIScene scene) {
        PointerBuffer materials = scene.mMaterials();
        if (materials == null || scene.mNumMaterials() <= 0) {
            return List.of();
        }

        List<MaterialInfo> names = new ArrayList<>(scene.mNumMaterials());
        for (int i = 0; i < scene.mNumMaterials(); i++) {
            AIMaterial material = AIMaterial.create(materials.get(i));
            String textureName = diffuseTextureName(material);
            String materialName = materialName(material);
            if (materialName.isBlank()) {
                materialName = textureName.isBlank() ? "material_" + i : baseName(textureName);
            }
            names.add(new MaterialInfo(materialName, baseName(textureName)));
        }
        return names;
    }

    private static String materialName(AIMaterial material) {
        AIString name = AIString.calloc();
        try {
            int result = Assimp.aiGetMaterialString(material, Assimp.AI_MATKEY_NAME, Assimp.aiTextureType_NONE, 0, name);
            return result == Assimp.aiReturn_SUCCESS ? name(name) : "";
        } finally {
            name.free();
        }
    }

    private static String diffuseTextureName(AIMaterial material) {
        AIString path = AIString.calloc();
        try {
            int result = Assimp.aiGetMaterialTexture(material, Assimp.aiTextureType_DIFFUSE, 0, path, (int[]) null, (int[]) null, (float[]) null, (int[]) null, (int[]) null, (int[]) null);
            return result == Assimp.aiReturn_SUCCESS ? name(path) : "";
        } finally {
            path.free();
        }
    }

    private static MaterialInfo materialInfo(List<MaterialInfo> materials, int materialIndex) {
        if (materialIndex >= 0 && materialIndex < materials.size()) {
            return materials.get(materialIndex);
        }
        return new MaterialInfo("material_" + materialIndex, "");
    }

    private static Matrix4f toJoml(AIMatrix4x4 matrix) {
        if (matrix == null) {
            return new Matrix4f();
        }
        return new Matrix4f(
                matrix.a1(), matrix.b1(), matrix.c1(), matrix.d1(),
                matrix.a2(), matrix.b2(), matrix.c2(), matrix.d2(),
                matrix.a3(), matrix.b3(), matrix.c3(), matrix.d3(),
                matrix.a4(), matrix.b4(), matrix.c4(), matrix.d4()
        );
    }

    private static String name(AIString value) {
        return value == null ? "" : value.dataString();
    }

    private static String baseName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash < 0 ? normalized : normalized.substring(slash + 1);
        int extension = fileName.lastIndexOf('.');
        return extension < 0 ? fileName : fileName.substring(0, extension);
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._|-]+", "_");
    }

    private static long stableId(String value) {
        long hash = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            hash = 31L * hash + value.charAt(i);
        }
        return hash;
    }

    private static String extensionHint(String sourceName) {
        if (sourceName == null) {
            return "fbx";
        }
        int dot = sourceName.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= sourceName.length()) {
            return "fbx";
        }
        return sourceName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private record VertexWeight(int boneIndex, float weight) {
    }

    private record MaterialInfo(String name, String textureHint) {
        private MaterialInfo {
            name = name == null ? "" : name;
            textureHint = textureHint == null ? "" : textureHint;
        }
    }

    private record SceneNodes(String rootName, Map<String, String> parentByNode, Map<String, Matrix4f> globalByNode, Map<Integer, Matrix4f> meshGlobalByIndex) {
        static SceneNodes collect(AINode root) {
            Map<String, String> parentByNode = new HashMap<>();
            Map<String, Matrix4f> globalByNode = new HashMap<>();
            Map<Integer, Matrix4f> meshGlobalByIndex = new HashMap<>();
            String rootName = root == null ? "" : name(root.mName());
            if (root != null) {
                collect(root, null, new Matrix4f(), parentByNode, globalByNode, meshGlobalByIndex);
            }
            return new SceneNodes(rootName, parentByNode, globalByNode, meshGlobalByIndex);
        }

        private static void collect(AINode node, String parentName, Matrix4f parentGlobal, Map<String, String> parentByNode, Map<String, Matrix4f> globalByNode, Map<Integer, Matrix4f> meshGlobalByIndex) {
            String nodeName = name(node.mName());
            Matrix4f global = new Matrix4f(parentGlobal).mul(toJoml(node.mTransformation()));
            if (!nodeName.isBlank()) {
                globalByNode.putIfAbsent(nodeName, global);
                if (parentName != null) {
                    parentByNode.putIfAbsent(nodeName, parentName);
                }
            }

            IntBuffer meshes = node.mMeshes();
            if (meshes != null) {
                for (int i = 0; i < node.mNumMeshes(); i++) {
                    meshGlobalByIndex.putIfAbsent(meshes.get(i), new Matrix4f(global));
                }
            }

            PointerBuffer children = node.mChildren();
            if (children == null) {
                return;
            }
            for (int i = 0; i < node.mNumChildren(); i++) {
                collect(AINode.create(children.get(i)), nodeName.isBlank() ? parentName : nodeName, global, parentByNode, globalByNode, meshGlobalByIndex);
            }
        }

        Matrix4f rootInverse() {
            Matrix4f root = globalByNode.get(rootName);
            return root == null ? new Matrix4f() : new Matrix4f(root).invert();
        }

        Matrix4f normalizedGlobal(String nodeName, Matrix4f rootInverse) {
            Matrix4f global = globalByNode.get(nodeName);
            if (global == null) {
                return new Matrix4f();
            }
            return new Matrix4f(rootInverse).mul(global);
        }

        Matrix4f meshTransform(int meshIndex, Matrix4f rootInverse) {
            Matrix4f global = meshGlobalByIndex.get(meshIndex);
            if (global == null) {
                return new Matrix4f();
            }
            return new Matrix4f(rootInverse).mul(global);
        }

        String nearestBoneParent(String boneName, Map<String, Integer> boneIndexByName) {
            String parent = parentByNode.get(boneName);
            while (parent != null && !boneIndexByName.containsKey(parent)) {
                parent = parentByNode.get(parent);
            }
            return parent;
        }

        String nearestBoneParent(String boneName, Set<String> boneNames) {
            String parent = parentByNode.get(boneName);
            while (parent != null && !boneNames.contains(parent)) {
                parent = parentByNode.get(parent);
            }
            return parent;
        }
    }
}
