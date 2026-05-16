package dev.codex.armatureskin.model;

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

    public record Mesh(String key, String name, String materialName, List<Vertex> vertices, int[] indices) {
        public Mesh(List<Vertex> vertices, int[] indices) {
            this("", "", "", vertices, indices);
        }

        public Mesh(String materialName, List<Vertex> vertices, int[] indices) {
            this("", "", materialName, vertices, indices);
        }

        public Mesh(String name, String materialName, List<Vertex> vertices, int[] indices) {
            this(stableKey(name, materialName), name, materialName, vertices, indices);
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
        }

        private static String stableKey(String name, String materialName) {
            String value = (name == null ? "" : name) + "|" + (materialName == null ? "" : materialName);
            String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._|-]+", "_");
            return normalized.isBlank() || normalized.equals("|") ? "mesh" : normalized;
        }
    }

    public record Vertex(float x, float y, float z, float u, float v, int[] boneIndices, float[] weights) {
    }
}
