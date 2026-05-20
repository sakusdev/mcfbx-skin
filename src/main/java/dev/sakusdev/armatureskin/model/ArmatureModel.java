package dev.sakusdev.armatureskin.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

public record ArmatureModel(List<Bone> bones, List<Mesh> meshes) {
    public ArmatureModel {
        bones = List.copyOf(bones == null ? List.of() : bones);
        meshes = List.copyOf(meshes == null ? List.of() : meshes);
    }

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

    public record Mesh(String key, String name, String materialName, String textureHint, List<Vertex> vertices, int[] indices, Matrix4f meshToModelTransform, Bounds bindBounds) {
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

        public Mesh(String key, String name, String materialName, String textureHint, List<Vertex> vertices, int[] indices) {
            this(key, name, materialName, textureHint, vertices, indices, new Matrix4f(), null);
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
            vertices = List.copyOf(vertices == null ? List.of() : vertices);
            indices = indices == null ? new int[0] : indices;
            meshToModelTransform = meshToModelTransform == null ? new Matrix4f() : new Matrix4f(meshToModelTransform);
            bindBounds = bindBounds == null ? Bounds.fromVertices(vertices, meshToModelTransform) : bindBounds;
        }

        private static String stableKey(String name, String materialName) {
            String value = (name == null ? "" : name) + "|" + (materialName == null ? "" : materialName);
            String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._|-]+", "_");
            return normalized.isBlank() || normalized.equals("|") ? "mesh" : normalized;
        }
    }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public static Bounds invalid() {
            return new Bounds(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        }

        public static Bounds fromVertices(List<Vertex> vertices, Matrix4f transform) {
            Bounds bounds = invalid();
            Matrix4f effectiveTransform = transform == null ? new Matrix4f() : transform;
            for (Vertex vertex : vertices == null ? List.<Vertex>of() : vertices) {
                bounds = bounds.include(new Vector3f(vertex.x(), vertex.y(), vertex.z()).mulPosition(effectiveTransform));
            }
            return bounds;
        }

        public Bounds include(Vector3f position) {
            if (position == null || !Float.isFinite(position.x()) || !Float.isFinite(position.y()) || !Float.isFinite(position.z())) {
                return this;
            }
            return new Bounds(
                    Math.min(minX, position.x()),
                    Math.min(minY, position.y()),
                    Math.min(minZ, position.z()),
                    Math.max(maxX, position.x()),
                    Math.max(maxY, position.y()),
                    Math.max(maxZ, position.z())
            );
        }

        public Bounds union(Bounds other) {
            if (other == null || !other.valid()) {
                return this;
            }
            if (!valid()) {
                return other;
            }
            return new Bounds(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ)
            );
        }

        public boolean valid() {
            return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                    && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
                    && height() > 0.0F;
        }

        public float width() {
            return maxX - minX;
        }

        public float height() {
            return maxY - minY;
        }

        public float depth() {
            return maxZ - minZ;
        }

        public float centerX() {
            return (minX + maxX) * 0.5F;
        }

        public float centerY() {
            return (minY + maxY) * 0.5F;
        }

        public float centerZ() {
            return (minZ + maxZ) * 0.5F;
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
