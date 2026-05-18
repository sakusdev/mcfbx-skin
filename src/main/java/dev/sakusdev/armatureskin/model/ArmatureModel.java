package dev.sakusdev.armatureskin.model;

import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

public record ArmatureModel(List<Bone> bones, List<Mesh> meshes) {
    public int rootBoneIndex() {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).parentIndex() < 0) {
                return i;
            }
        }
        return bones.isEmpty() ? -1 : 0;
    }

    public record Bone(long id, String name, int parentIndex, Matrix4f localBindTransform, Matrix4f inverseBindTransform) {
    }

    public record Mesh(String key, String name, String materialName, String textureHint, List<Vertex> vertices, int[] indices) {
        public Mesh(List<Vertex> vertices, int[] indices) {
            this("", "", "", "", vertices, indices);
        }

        public Mesh(String materialName, List<Vertex> vertices, int[] indices) {
            this("", "", materialName, "", vertices, indices);
        }

        public Mesh(String name, String materialName, List<Vertex> vertices, int[] indices) {
            this(stableKey(name, materialName), name, materialName, "", vertices, indices);
        }

        public Mesh(String key, String name, String materialName, List<Vertex> vertices, int[] indices) {
            this(key, name, materialName, "", vertices, indices);
        }

        public String displayName() {
            if (name != null && !name.isBlank()) {
                return materialName == null || materialName.isBlank() ? name : name + " / " + materialName;
            }
            return materialName == null || materialName.isBlank() ? "Mesh" : materialName;
        }

        public Mesh {
            key = key == null || key.isBlank() ? stableKey(name, materialName) : key;
            name = name == null ? "" : name;
            materialName = materialName == null ? "" : materialName;
            textureHint = textureHint == null ? "" : textureHint;
        }

        private static String stableKey(String name, String materialName) {
            String value = (name == null ? "" : name) + "|" + (materialName == null ? "" : materialName);
            String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._|-]+", "_");
            return normalized.isBlank() || normalized.equals("|") ? "mesh" : normalized;
        }
    }

    public record Vertex(float x, float y, float z, float u, float v, int[] boneIndices, float[] weights, float nx, float ny, float nz) {
        public Vertex(float x, float y, float z, float u, float v, int[] boneIndices, float[] weights) {
            this(x, y, z, u, v, boneIndices, weights, 0.0F, 1.0F, 0.0F);
        }

        public Vertex {
            boneIndices = boneIndices == null ? new int[0] : boneIndices;
            weights = weights == null ? new float[0] : weights;
            if (!Float.isFinite(nx) || !Float.isFinite(ny) || !Float.isFinite(nz)) {
                nx = 0.0F;
                ny = 1.0F;
                nz = 0.0F;
            }
        }
    }
}
