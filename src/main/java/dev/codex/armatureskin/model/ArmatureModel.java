package dev.codex.armatureskin.model;

import org.joml.Matrix4f;

import java.util.List;

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

    public record Mesh(String materialName, List<Vertex> vertices, int[] indices) {
        public Mesh(List<Vertex> vertices, int[] indices) {
            this("", vertices, indices);
        }
    }

    public record Vertex(float x, float y, float z, float u, float v, int[] boneIndices, float[] weights) {
    }
}
