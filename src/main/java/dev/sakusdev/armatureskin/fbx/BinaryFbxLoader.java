package dev.sakusdev.armatureskin.fbx;

import dev.sakusdev.armatureskin.model.ArmatureModel;
import org.joml.Matrix4f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.InflaterInputStream;

public final class BinaryFbxLoader {
    private static final int MAGIC_LENGTH = 23;

    public ArmatureModel load(byte[] data) throws IOException {
        Reader reader = new Reader(data);
        reader.skip(MAGIC_LENGTH);
        int version = reader.readI32();
        boolean wideNodes = version >= 7500;

        List<FbxNode> topLevel = new ArrayList<>();
        while (reader.remaining() > nullRecordSize(wideNodes)) {
            FbxNode node = readNode(reader, wideNodes);
            if (node == null) {
                break;
            }
            topLevel.add(node);
        }

        FbxNode root = new FbxNode("Root", List.of(), topLevel);
        return buildModel(root);
    }

    private static FbxNode readNode(Reader reader, boolean wideNodes) throws IOException {
        if (reader.remaining() < nullRecordSize(wideNodes)) {
            return null;
        }

        long endOffset = wideNodes ? reader.readI64() : reader.readU32();
        long propertyCount = wideNodes ? reader.readI64() : reader.readU32();
        long propertyListLength = wideNodes ? reader.readI64() : reader.readU32();
        int nameLength = reader.readU8();

        if (endOffset == 0 && propertyCount == 0 && propertyListLength == 0 && nameLength == 0) {
            return null;
        }
        reader.requireOffset(endOffset);

        String name = reader.readAscii(nameLength);
        List<Object> properties = new ArrayList<>();
        for (long i = 0; i < propertyCount; i++) {
            properties.add(readProperty(reader));
        }

        List<FbxNode> children = new ArrayList<>();
        while (reader.position() < endOffset) {
            FbxNode child = readNode(reader, wideNodes);
            if (child == null) {
                break;
            }
            children.add(child);
        }
        reader.seek(endOffset);
        return new FbxNode(name, properties, children);
    }

    private static Object readProperty(Reader reader) throws IOException {
        char type = (char) reader.readU8();
        return switch (type) {
            case 'Y' -> (int) reader.readI16();
            case 'C' -> reader.readU8() != 0;
            case 'I' -> reader.readI32();
            case 'F' -> reader.readF32();
            case 'D' -> reader.readF64();
            case 'L' -> reader.readI64();
            case 'S' -> reader.readString();
            case 'R' -> reader.readBytes(reader.readLength());
            case 'i' -> readIntArray(reader);
            case 'l' -> readLongArray(reader);
            case 'f' -> readFloatArray(reader);
            case 'd' -> readDoubleArray(reader);
            case 'b', 'c' -> readBooleanArray(reader);
            default -> throw new IOException("Unsupported binary FBX property type: " + type);
        };
    }

    private static int[] readIntArray(Reader reader) throws IOException {
        ByteBuffer buffer = arrayPayload(reader, Integer.BYTES);
        int[] values = new int[buffer.remaining() / Integer.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getInt();
        }
        return values;
    }

    private static long[] readLongArray(Reader reader) throws IOException {
        ByteBuffer buffer = arrayPayload(reader, Long.BYTES);
        long[] values = new long[buffer.remaining() / Long.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getLong();
        }
        return values;
    }

    private static float[] readFloatArray(Reader reader) throws IOException {
        ByteBuffer buffer = arrayPayload(reader, Float.BYTES);
        float[] values = new float[buffer.remaining() / Float.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat();
        }
        return values;
    }

    private static double[] readDoubleArray(Reader reader) throws IOException {
        ByteBuffer buffer = arrayPayload(reader, Double.BYTES);
        double[] values = new double[buffer.remaining() / Double.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getDouble();
        }
        return values;
    }

    private static boolean[] readBooleanArray(Reader reader) throws IOException {
        ByteBuffer buffer = arrayPayload(reader, 1);
        boolean[] values = new boolean[buffer.remaining()];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.get() != 0;
        }
        return values;
    }

    private static ByteBuffer arrayPayload(Reader reader, int elementSize) throws IOException {
        int arrayLength = reader.readLength();
        int encoding = reader.readLength();
        int compressedLength = reader.readLength();
        byte[] payload = reader.readBytes(compressedLength);
        int expectedLength = Math.multiplyExact(arrayLength, elementSize);
        byte[] raw = switch (encoding) {
            case 0 -> payload;
            case 1 -> inflate(payload, expectedLength);
            default -> throw new IOException("Unsupported binary FBX array encoding: " + encoding);
        };
        if (raw.length < expectedLength) {
            throw new IOException("Short binary FBX array payload.");
        }
        return ByteBuffer.wrap(raw, 0, expectedLength).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] inflate(byte[] compressed, int expectedLength) throws IOException {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream(expectedLength)) {
            input.transferTo(output);
            byte[] raw = output.toByteArray();
            if (raw.length != expectedLength) {
                throw new IOException("Inflated binary FBX array size mismatch: expected " + expectedLength + ", got " + raw.length);
            }
            return raw;
        }
    }

    private static ArmatureModel buildModel(FbxNode root) throws IOException {
        FbxNode objectsNode = root.firstChild("Objects");
        FbxNode connectionsNode = root.firstChild("Connections");
        if (objectsNode == null) {
            throw new IOException("The FBX does not contain an Objects section.");
        }

        Map<Long, FbxObject> objects = parseObjects(objectsNode);
        List<long[]> connections = parseConnections(connectionsNode);
        Map<Long, Long> modelParentByChild = buildModelParentMap(objects, connections);

        List<FbxObject> boneObjects = objects.values().stream()
                .filter(obj -> obj.kind.equals("Model") && obj.type.toLowerCase(Locale.ROOT).contains("limbnode"))
                .toList();
        Map<Long, Integer> boneIndexById = buildBoneIndexMap(boneObjects, modelParentByChild, objects);
        Map<Integer, Matrix4f> globalBindByBone = buildClusterGlobalBinds(objects, connections, boneIndexById);
        List<ArmatureModel.Bone> bones = buildBones(boneIndexById, modelParentByChild, objects, globalBindByBone);

        Map<Long, Map<Integer, List<VertexWeight>>> weightsByGeometry = buildWeights(objects, connections, boneIndexById);
        Map<Long, Long> geometryToModel = buildGeometryModelMap(objects, connections);
        Map<Long, List<String>> materialsByGeometry = buildMaterials(objects, connections, geometryToModel);
        List<ArmatureModel.Mesh> meshes = new ArrayList<>();
        for (FbxObject geometry : objects.values()) {
            if (geometry.kind.equals("Geometry") && geometry.type.toLowerCase(Locale.ROOT).contains("mesh")) {
                meshes.addAll(buildMeshes(geometry, weightsByGeometry.getOrDefault(geometry.id, Map.of()), materialsByGeometry.getOrDefault(geometry.id, List.of())));
            }
        }

        if (bones.isEmpty()) {
            throw new IOException("The FBX does not contain armature LimbNode bones.");
        }
        if (meshes.isEmpty()) {
            throw new IOException("The FBX does not contain mesh geometry.");
        }
        return new ArmatureModel(bones, meshes);
    }

    private static Map<Long, FbxObject> parseObjects(FbxNode objectsNode) throws IOException {
        Map<Long, FbxObject> objects = new HashMap<>();
        for (FbxNode node : objectsNode.children()) {
            if (!node.name.equals("Model") && !node.name.equals("Geometry") && !node.name.equals("Deformer") && !node.name.equals("Material")) {
                continue;
            }
            if (node.properties.size() < 3) {
                continue;
            }
            long id = asLong(node.properties.get(0));
            String name = cleanName(String.valueOf(node.properties.get(1)));
            String type = String.valueOf(node.properties.get(2));
            objects.put(id, new FbxObject(node.name, id, name, type, node));
        }
        return objects;
    }

    private static List<long[]> parseConnections(FbxNode connectionsNode) {
        if (connectionsNode == null) {
            return List.of();
        }
        List<long[]> connections = new ArrayList<>();
        for (FbxNode node : connectionsNode.children()) {
            if (!node.name.equals("C") || node.properties.size() < 3) {
                continue;
            }
            if (!String.valueOf(node.properties.get(0)).equals("OO")) {
                continue;
            }
            connections.add(new long[]{asLong(node.properties.get(1)), asLong(node.properties.get(2))});
        }
        return connections;
    }

    private static Map<Long, Long> buildModelParentMap(Map<Long, FbxObject> objects, List<long[]> connections) {
        Map<Long, Long> result = new HashMap<>();
        for (long[] connection : connections) {
            FbxObject child = objects.get(connection[0]);
            FbxObject parent = objects.get(connection[1]);
            if (child != null && parent != null && child.kind.equals("Model") && parent.kind.equals("Model")) {
                result.put(child.id, parent.id);
            }
        }
        return result;
    }

    private static Map<Long, Long> buildGeometryModelMap(Map<Long, FbxObject> objects, List<long[]> connections) {
        Map<Long, Long> result = new HashMap<>();
        for (long[] connection : connections) {
            FbxObject child = objects.get(connection[0]);
            FbxObject parent = objects.get(connection[1]);
            if (child != null && parent != null && child.kind.equals("Geometry") && parent.kind.equals("Model")) {
                result.put(child.id, parent.id);
            }
        }
        return result;
    }

    private static Map<Long, Integer> buildBoneIndexMap(List<FbxObject> bones, Map<Long, Long> childToParent, Map<Long, FbxObject> objects) {
        Map<Long, Integer> indexById = new HashMap<>();
        Set<Long> boneIds = new HashSet<>();
        for (FbxObject bone : bones) {
            boneIds.add(bone.id);
        }
        for (FbxObject bone : bones) {
            visitBone(bone.id, childToParent, objects, boneIds, indexById);
        }
        return indexById;
    }

    private static void visitBone(long id, Map<Long, Long> childToParent, Map<Long, FbxObject> objects, Set<Long> boneIds, Map<Long, Integer> indexById) {
        if (indexById.containsKey(id)) {
            return;
        }
        Long parent = childToParent.get(id);
        if (parent != null && boneIds.contains(parent)) {
            visitBone(parent, childToParent, objects, boneIds, indexById);
        }
        if (objects.containsKey(id)) {
            indexById.put(id, indexById.size());
        }
    }

    private static List<ArmatureModel.Bone> buildBones(Map<Long, Integer> boneIndexById, Map<Long, Long> childToParent, Map<Long, FbxObject> objects, Map<Integer, Matrix4f> globalBindByBone) {
        ArmatureModel.Bone[] bones = new ArmatureModel.Bone[boneIndexById.size()];
        Matrix4f[] globals = new Matrix4f[boneIndexById.size()];

        boneIndexById.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> {
                    FbxObject object = objects.get(entry.getKey());
                    int index = entry.getValue();
                    int parentIndex = -1;
                    Long parentId = childToParent.get(object.id);
                    if (parentId != null && boneIndexById.containsKey(parentId)) {
                        parentIndex = boneIndexById.get(parentId);
                    }

                    Matrix4f globalBind = globalBindByBone.get(index);
                    Matrix4f local;
                    Matrix4f global;
                    if (globalBind != null) {
                        global = new Matrix4f(globalBind);
                        local = parentIndex >= 0 && globals[parentIndex] != null
                                ? new Matrix4f(globals[parentIndex]).invert().mul(global)
                                : new Matrix4f(global);
                    } else {
                        local = localTransform(object.node);
                        global = parentIndex >= 0 ? new Matrix4f(globals[parentIndex]).mul(local) : new Matrix4f(local);
                    }
                    globals[index] = global;
                    bones[index] = new ArmatureModel.Bone(object.id, object.name, parentIndex, local, new Matrix4f(global).invert());
                });

        return List.of(bones);
    }

    private static Map<Integer, Matrix4f> buildClusterGlobalBinds(Map<Long, FbxObject> objects, List<long[]> connections, Map<Long, Integer> boneIndexById) {
        Map<Long, Long> clusterToBone = new HashMap<>();
        for (long[] connection : connections) {
            FbxObject child = objects.get(connection[0]);
            FbxObject parent = objects.get(connection[1]);
            if (child != null && parent != null
                    && child.kind.equals("Model")
                    && parent.kind.equals("Deformer")
                    && parent.type.toLowerCase(Locale.ROOT).contains("cluster")) {
                clusterToBone.put(parent.id, child.id);
            }
        }

        Map<Integer, Matrix4f> globalBinds = new HashMap<>();
        for (Map.Entry<Long, Long> entry : clusterToBone.entrySet()) {
            Integer boneIndex = boneIndexById.get(entry.getValue());
            FbxObject cluster = objects.get(entry.getKey());
            if (boneIndex == null || cluster == null || globalBinds.containsKey(boneIndex)) {
                continue;
            }
            float[] transformLink = toFloats(firstProperty(cluster.node.firstDescendant("TransformLink")));
            if (transformLink.length == 16) {
                globalBinds.put(boneIndex, matrixFromFbx(transformLink));
            }
        }
        return globalBinds;
    }

    private static Map<Long, Map<Integer, List<VertexWeight>>> buildWeights(Map<Long, FbxObject> objects, List<long[]> connections, Map<Long, Integer> boneIndexById) {
        Map<Long, Long> clusterToBone = new HashMap<>();
        Map<Long, Long> clusterToSkin = new HashMap<>();
        Map<Long, Long> skinToGeometry = new HashMap<>();

        for (long[] connection : connections) {
            FbxObject child = objects.get(connection[0]);
            FbxObject parent = objects.get(connection[1]);
            if (child == null) {
                continue;
            }
            String childType = child.type.toLowerCase(Locale.ROOT);
            if (child.kind.equals("Model") && parent != null && parent.kind.equals("Deformer") && parent.type.toLowerCase(Locale.ROOT).contains("cluster")) {
                clusterToBone.put(parent.id, child.id);
            } else if (child.kind.equals("Deformer") && childType.contains("cluster") && parent != null && parent.kind.equals("Deformer")) {
                clusterToSkin.put(child.id, parent.id);
            } else if (child.kind.equals("Deformer") && childType.contains("skin") && parent != null && parent.kind.equals("Geometry")) {
                skinToGeometry.put(child.id, parent.id);
            }
        }

        Map<Long, Map<Integer, List<VertexWeight>>> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : clusterToBone.entrySet()) {
            long clusterId = entry.getKey();
            Integer boneIndex = boneIndexById.get(entry.getValue());
            Long skinId = clusterToSkin.get(clusterId);
            Long geometryId = skinId == null ? null : skinToGeometry.get(skinId);
            FbxObject cluster = objects.get(clusterId);
            if (boneIndex == null || geometryId == null || cluster == null) {
                continue;
            }

            Object indexValue = firstProperty(cluster.node.firstDescendant("Indexes"));
            Object weightValue = firstProperty(cluster.node.firstDescendant("Weights"));
            if (indexValue == null || weightValue == null) {
                continue;
            }
            int[] indexes = toInts(indexValue);
            float[] weights = toFloats(weightValue);
            Map<Integer, List<VertexWeight>> geometryWeights = result.computeIfAbsent(geometryId, ignored -> new HashMap<>());
            for (int i = 0; i < indexes.length && i < weights.length; i++) {
                geometryWeights.computeIfAbsent(indexes[i], ignored -> new ArrayList<>()).add(new VertexWeight(boneIndex, weights[i]));
            }
        }
        return result;
    }

    private static Map<Long, List<String>> buildMaterials(Map<Long, FbxObject> objects, List<long[]> connections, Map<Long, Long> geometryToModel) {
        Map<Long, List<String>> result = new HashMap<>();
        for (FbxObject geometry : objects.values()) {
            if (!geometry.kind.equals("Geometry")) {
                continue;
            }
            Long modelId = geometryToModel.get(geometry.id);
            List<String> materialNames = new ArrayList<>();
            for (long[] connection : connections) {
                FbxObject child = objects.get(connection[0]);
                if (child == null || !child.kind.equals("Material")) {
                    continue;
                }
                if ((modelId != null && connection[1] == modelId) || connection[1] == geometry.id) {
                    materialNames.add(child.name);
                }
            }
            if (!materialNames.isEmpty()) {
                result.put(geometry.id, materialNames);
            }
        }
        return result;
    }

    private static List<ArmatureModel.Mesh> buildMeshes(FbxObject geometry, Map<Integer, List<VertexWeight>> weights, List<String> materialNames) {
        float[] vertices = toFloats(firstProperty(geometry.node.firstDescendant("Vertices")));
        int[] polygonVertexIndex = toInts(firstProperty(geometry.node.firstDescendant("PolygonVertexIndex")));
        float[] uv = toFloats(firstProperty(geometry.node.firstDescendant("UV")));
        int[] uvIndex = toInts(firstProperty(geometry.node.firstDescendant("UVIndex")));
        int[] materialIndices = toInts(firstProperty(geometry.node.firstDescendant("Materials")));

        List<ArmatureModel.Vertex> outVertices = new ArrayList<>();
        Map<String, List<Integer>> indicesByMaterial = new java.util.LinkedHashMap<>();
        List<Corner> polygon = new ArrayList<>();
        int cornerIndex = 0;
        int polygonIndex = 0;

        for (int raw : polygonVertexIndex) {
            boolean end = raw < 0;
            int controlPoint = end ? -raw - 1 : raw;
            int emitted = appendVertex(outVertices, vertices, uv, uvIndex, cornerIndex, controlPoint, weights);
            polygon.add(new Corner(emitted));
            cornerIndex++;

            if (end) {
                String materialName = materialName(materialNames, materialIndices, polygonIndex);
                List<Integer> outIndices = indicesByMaterial.computeIfAbsent(materialName, ignored -> new ArrayList<>());
                for (int i = 1; i + 1 < polygon.size(); i++) {
                    outIndices.add(polygon.get(0).index);
                    outIndices.add(polygon.get(i).index);
                    outIndices.add(polygon.get(i + 1).index);
                }
                polygon.clear();
                polygonIndex++;
            }
        }

        return indicesByMaterial.entrySet().stream()
                .map(entry -> new ArmatureModel.Mesh("geometry:" + geometry.id + "|material:" + normalizeKey(entry.getKey()), geometry.name, entry.getKey(), outVertices, entry.getValue().stream().mapToInt(Integer::intValue).toArray()))
                .filter(mesh -> !mesh.vertices().isEmpty() && mesh.indices().length > 0)
                .toList();
    }

    private static int appendVertex(List<ArmatureModel.Vertex> out, float[] positions, float[] uv, int[] uvIndex, int cornerIndex, int controlPoint, Map<Integer, List<VertexWeight>> weights) {
        int base = controlPoint * 3;
        float x = base + 2 < positions.length ? positions[base] : 0.0F;
        float y = base + 2 < positions.length ? positions[base + 1] : 0.0F;
        float z = base + 2 < positions.length ? positions[base + 2] : 0.0F;

        int uvCorner = cornerIndex < uvIndex.length ? uvIndex[cornerIndex] : controlPoint;
        int uvBase = uvCorner * 2;
        float u = uvBase + 1 < uv.length ? uv[uvBase] : 0.0F;
        float v = uvBase + 1 < uv.length ? 1.0F - uv[uvBase + 1] : 0.0F;

        List<VertexWeight> vertexWeights = normalizedTopWeights(weights.getOrDefault(controlPoint, List.of()));
        int[] boneIndices = new int[vertexWeights.size()];
        float[] boneWeights = new float[vertexWeights.size()];
        for (int i = 0; i < vertexWeights.size(); i++) {
            boneIndices[i] = vertexWeights.get(i).boneIndex;
            boneWeights[i] = vertexWeights.get(i).weight;
        }

        out.add(new ArmatureModel.Vertex(x, y, z, u, v, boneIndices, boneWeights));
        return out.size() - 1;
    }

    private static List<VertexWeight> normalizedTopWeights(List<VertexWeight> weights) {
        if (weights.isEmpty()) {
            return List.of();
        }
        List<VertexWeight> top = weights.stream()
                .sorted((a, b) -> Float.compare(b.weight, a.weight))
                .limit(4)
                .toList();
        float total = 0.0F;
        for (VertexWeight weight : top) {
            total += weight.weight;
        }
        if (total <= 0.0F) {
            return List.of();
        }
        List<VertexWeight> normalized = new ArrayList<>();
        for (VertexWeight weight : top) {
            normalized.add(new VertexWeight(weight.boneIndex, weight.weight / total));
        }
        return normalized;
    }

    private static Matrix4f localTransform(FbxNode node) {
        float[] t = propertyVec3(node, "Lcl Translation", 0.0F, 0.0F, 0.0F);
        float[] r = propertyVec3(node, "Lcl Rotation", 0.0F, 0.0F, 0.0F);
        float[] s = propertyVec3(node, "Lcl Scaling", 1.0F, 1.0F, 1.0F);
        return new Matrix4f()
                .translate(t[0], t[1], t[2])
                .rotateXYZ((float) Math.toRadians(r[0]), (float) Math.toRadians(r[1]), (float) Math.toRadians(r[2]))
                .scale(s[0], s[1], s[2]);
    }

    private static Matrix4f matrixFromFbx(float[] values) {
        return new Matrix4f(
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
                values[12], values[13], values[14], values[15]
        );
    }

    private static float[] propertyVec3(FbxNode node, String name, float x, float y, float z) {
        FbxNode properties = node.firstChild("Properties70");
        if (properties == null) {
            return new float[]{x, y, z};
        }
        for (FbxNode property : properties.children()) {
            if (!property.name.equals("P") || property.properties.size() < 4 || !String.valueOf(property.properties.get(0)).equals(name)) {
                continue;
            }
            List<Number> numbers = property.properties.stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .toList();
            if (numbers.size() >= 3) {
                int offset = numbers.size() - 3;
                return new float[]{numbers.get(offset).floatValue(), numbers.get(offset + 1).floatValue(), numbers.get(offset + 2).floatValue()};
            }
        }
        return new float[]{x, y, z};
    }

    private static Object firstProperty(FbxNode node) {
        if (node == null || node.properties.isEmpty()) {
            return null;
        }
        return node.properties.get(0);
    }

    private static float[] toFloats(Object value) {
        return switch (value) {
            case double[] values -> {
                float[] out = new float[values.length];
                for (int i = 0; i < values.length; i++) {
                    out[i] = (float) values[i];
                }
                yield out;
            }
            case float[] values -> values;
            case int[] values -> {
                float[] out = new float[values.length];
                for (int i = 0; i < values.length; i++) {
                    out[i] = values[i];
                }
                yield out;
            }
            case long[] values -> {
                float[] out = new float[values.length];
                for (int i = 0; i < values.length; i++) {
                    out[i] = values[i];
                }
                yield out;
            }
            default -> new float[0];
        };
    }

    private static int[] toInts(Object value) {
        return switch (value) {
            case int[] values -> values;
            case long[] values -> {
                int[] out = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    out[i] = (int) values[i];
                }
                yield out;
            }
            case float[] values -> {
                int[] out = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    out[i] = (int) values[i];
                }
                yield out;
            }
            case double[] values -> {
                int[] out = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    out[i] = (int) values[i];
                }
                yield out;
            }
            default -> new int[0];
        };
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String cleanName(String raw) {
        int namespaceMarker = raw.indexOf("::");
        String name = namespaceMarker >= 0 ? raw.substring(namespaceMarker + 2) : raw;
        int binaryMarker = name.indexOf("\u0000\u0001");
        return binaryMarker >= 0 ? name.substring(0, binaryMarker) : name;
    }

    private static String materialName(List<String> materialNames, int[] materialIndices, int polygonIndex) {
        int materialIndex = polygonIndex < materialIndices.length ? materialIndices[polygonIndex] : 0;
        if (materialIndex >= 0 && materialIndex < materialNames.size()) {
            return materialNames.get(materialIndex);
        }
        return materialNames.isEmpty() ? "" : materialNames.get(0);
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private static int nullRecordSize(boolean wideNodes) {
        return wideNodes ? 25 : 13;
    }

    private record FbxNode(String name, List<Object> properties, List<FbxNode> children) {
        FbxNode firstChild(String childName) {
            for (FbxNode child : children) {
                if (child.name.equals(childName)) {
                    return child;
                }
            }
            return null;
        }

        FbxNode firstDescendant(String descendantName) {
            FbxNode child = firstChild(descendantName);
            if (child != null) {
                return child;
            }
            for (FbxNode nested : children) {
                FbxNode match = nested.firstDescendant(descendantName);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }
    }

    private record FbxObject(String kind, long id, String name, String type, FbxNode node) {
    }

    private record VertexWeight(int boneIndex, float weight) {
    }

    private record Corner(int index) {
    }

    private static final class Reader {
        private final byte[] data;
        private int position;

        Reader(byte[] data) {
            this.data = data;
        }

        int position() {
            return position;
        }

        int remaining() {
            return data.length - position;
        }

        void skip(int length) throws IOException {
            seek((long) position + length);
        }

        void seek(long newPosition) throws IOException {
            requireOffset(newPosition);
            position = (int) newPosition;
        }

        void requireOffset(long offset) throws IOException {
            if (offset < 0 || offset > data.length || offset > Integer.MAX_VALUE) {
                throw new IOException("Invalid binary FBX offset: " + offset);
            }
        }

        int readU8() throws IOException {
            requireAvailable(1);
            return data[position++] & 0xFF;
        }

        short readI16() throws IOException {
            requireAvailable(Short.BYTES);
            short value = ByteBuffer.wrap(data, position, Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).getShort();
            position += Short.BYTES;
            return value;
        }

        int readI32() throws IOException {
            requireAvailable(Integer.BYTES);
            int value = ByteBuffer.wrap(data, position, Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt();
            position += Integer.BYTES;
            return value;
        }

        long readU32() throws IOException {
            return readI32() & 0xFFFFFFFFL;
        }

        long readI64() throws IOException {
            requireAvailable(Long.BYTES);
            long value = ByteBuffer.wrap(data, position, Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).getLong();
            position += Long.BYTES;
            return value;
        }

        float readF32() throws IOException {
            requireAvailable(Float.BYTES);
            float value = ByteBuffer.wrap(data, position, Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            position += Float.BYTES;
            return value;
        }

        double readF64() throws IOException {
            requireAvailable(Double.BYTES);
            double value = ByteBuffer.wrap(data, position, Double.BYTES).order(ByteOrder.LITTLE_ENDIAN).getDouble();
            position += Double.BYTES;
            return value;
        }

        int readLength() throws IOException {
            long length = readU32();
            if (length > Integer.MAX_VALUE) {
                throw new IOException("Binary FBX length is too large: " + length);
            }
            return (int) length;
        }

        String readString() throws IOException {
            return readAscii(readLength());
        }

        String readAscii(int length) throws IOException {
            return new String(readBytes(length), StandardCharsets.UTF_8);
        }

        byte[] readBytes(int length) throws IOException {
            requireAvailable(length);
            byte[] out = new byte[length];
            System.arraycopy(data, position, out, 0, length);
            position += length;
            return out;
        }

        private void requireAvailable(int length) throws IOException {
            if (length < 0 || position + length > data.length) {
                throw new IOException("Unexpected end of binary FBX data.");
            }
        }
    }
}
