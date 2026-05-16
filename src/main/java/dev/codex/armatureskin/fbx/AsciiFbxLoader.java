package dev.codex.armatureskin.fbx;

import dev.codex.armatureskin.model.ArmatureModel;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AsciiFbxLoader {
    private static final Pattern OBJECT_HEADER = Pattern.compile("(?m)^\\s*(Model|Geometry|Deformer|Material):\\s*(\\d+),\\s*\"([^\"]*)\",\\s*\"([^\"]*)\"\\s*\\{");
    private static final Pattern CONNECTION = Pattern.compile("(?m)^\\s*C:\\s*\"OO\",\\s*(\\d+),\\s*(\\d+)");
    private static final Pattern PROPERTY = Pattern.compile("(?m)^\\s*P:\\s*\"([^\"]+)\"\\s*,[^\\n\\r]*");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    public ArmatureModel load(Path path) throws IOException {
        String text = Files.readString(path);
        return loadText(text, path.getFileName().toString());
    }

    public ArmatureModel loadText(String text, String sourceName) throws IOException {
        if (text.indexOf('\0') >= 0 || !text.contains("FBXHeaderExtension")) {
            throw new IOException("Only ASCII FBX files are supported by this loader: " + sourceName);
        }

        Map<Long, FbxObject> objects = parseObjects(text);
        List<long[]> connections = parseConnections(text);
        Map<Long, Long> childToParent = new HashMap<>();
        for (long[] connection : connections) {
            childToParent.put(connection[0], connection[1]);
        }

        List<FbxObject> boneObjects = objects.values().stream()
                .filter(obj -> obj.kind.equals("Model") && obj.type.toLowerCase(Locale.ROOT).contains("limbnode"))
                .toList();
        Map<Long, Integer> boneIndexById = buildBoneIndexMap(boneObjects, childToParent, objects);
        List<ArmatureModel.Bone> bones = buildBones(boneIndexById, childToParent, objects);

        Map<Long, Map<Integer, List<VertexWeight>>> weightsByGeometry = buildWeights(objects, connections, boneIndexById);
        Map<Long, List<String>> materialsByGeometry = buildMaterials(objects, connections, childToParent);
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

    private static Map<Long, FbxObject> parseObjects(String text) throws IOException {
        Map<Long, FbxObject> objects = new HashMap<>();
        Matcher matcher = OBJECT_HEADER.matcher(text);
        while (matcher.find()) {
            int bodyStart = matcher.end();
            int bodyEnd = findMatchingBrace(text, bodyStart - 1);
            if (bodyEnd < 0) {
                throw new IOException("Malformed FBX object near " + matcher.group(2));
            }
            long id = Long.parseLong(matcher.group(2));
            objects.put(id, new FbxObject(
                    matcher.group(1),
                    id,
                    cleanName(matcher.group(3)),
                    matcher.group(4),
                    text.substring(bodyStart, bodyEnd)
            ));
        }
        return objects;
    }

    private static List<long[]> parseConnections(String text) {
        List<long[]> connections = new ArrayList<>();
        Matcher matcher = CONNECTION.matcher(text);
        while (matcher.find()) {
            connections.add(new long[]{Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))});
        }
        return connections;
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

    private static List<ArmatureModel.Bone> buildBones(Map<Long, Integer> boneIndexById, Map<Long, Long> childToParent, Map<Long, FbxObject> objects) {
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

                    Matrix4f local = localTransform(object.body);
                    Matrix4f global = parentIndex >= 0 ? new Matrix4f(globals[parentIndex]).mul(local) : new Matrix4f(local);
                    globals[index] = global;
                    bones[index] = new ArmatureModel.Bone(object.id, object.name, parentIndex, local, new Matrix4f(global).invert());
                });

        return List.of(bones);
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
            if (child.kind.equals("Deformer") && childType.contains("cluster") && parent != null && parent.kind.equals("Model")) {
                clusterToBone.put(child.id, parent.id);
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

            int[] indexes = toInts(readNumberArray(cluster.body, "Indexes"));
            float[] weights = toFloats(readNumberArray(cluster.body, "Weights"));
            Map<Integer, List<VertexWeight>> geometryWeights = result.computeIfAbsent(geometryId, ignored -> new HashMap<>());
            for (int i = 0; i < indexes.length && i < weights.length; i++) {
                geometryWeights.computeIfAbsent(indexes[i], ignored -> new ArrayList<>()).add(new VertexWeight(boneIndex, weights[i]));
            }
        }
        return result;
    }

    private static Map<Long, List<String>> buildMaterials(Map<Long, FbxObject> objects, List<long[]> connections, Map<Long, Long> childToParent) {
        Map<Long, List<String>> result = new HashMap<>();
        for (FbxObject geometry : objects.values()) {
            if (!geometry.kind.equals("Geometry")) {
                continue;
            }
            Long modelId = childToParent.get(geometry.id);
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
        float[] vertices = toFloats(readNumberArray(geometry.body, "Vertices"));
        int[] polygonVertexIndex = toInts(readNumberArray(geometry.body, "PolygonVertexIndex"));
        float[] uv = toFloats(readNumberArray(geometry.body, "UV"));
        int[] uvIndex = toInts(readNumberArray(geometry.body, "UVIndex"));
        int[] materialIndices = toInts(readNumberArray(geometry.body, "Materials"));

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
                .map(entry -> new ArmatureModel.Mesh(entry.getKey(), outVertices, entry.getValue().stream().mapToInt(Integer::intValue).toArray()))
                .filter(mesh -> mesh.indices().length > 0)
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

    private static Matrix4f localTransform(String body) {
        float[] t = propertyVec3(body, "Lcl Translation", 0.0F, 0.0F, 0.0F);
        float[] r = propertyVec3(body, "Lcl Rotation", 0.0F, 0.0F, 0.0F);
        float[] s = propertyVec3(body, "Lcl Scaling", 1.0F, 1.0F, 1.0F);
        return new Matrix4f()
                .translate(t[0], t[1], t[2])
                .rotateXYZ((float) Math.toRadians(r[0]), (float) Math.toRadians(r[1]), (float) Math.toRadians(r[2]))
                .scale(s[0], s[1], s[2]);
    }

    private static float[] propertyVec3(String body, String name, float x, float y, float z) {
        Matcher matcher = PROPERTY.matcher(body);
        while (matcher.find()) {
            if (!matcher.group(1).equals(name)) {
                continue;
            }
            float[] numbers = toFloats(readNumbers(matcher.group()));
            if (numbers.length >= 3) {
                return new float[]{numbers[numbers.length - 3], numbers[numbers.length - 2], numbers[numbers.length - 1]};
            }
        }
        return new float[]{x, y, z};
    }

    private static List<String> readNumberArray(String body, String key) {
        Pattern block = Pattern.compile("(?s)\\b" + Pattern.quote(key) + "\\s*:\\s*(?:\\*\\d+\\s*)?\\{\\s*a:\\s*([^}]*)}");
        Matcher blockMatcher = block.matcher(body);
        if (blockMatcher.find()) {
            return readNumbers(blockMatcher.group(1));
        }

        Pattern inline = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*([^\\n\\r]+)");
        Matcher inlineMatcher = inline.matcher(body);
        if (inlineMatcher.find()) {
            return readNumbers(inlineMatcher.group(1));
        }
        return List.of();
    }

    private static List<String> readNumbers(String text) {
        List<String> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private static float[] toFloats(List<String> numbers) {
        float[] values = new float[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            values[i] = Float.parseFloat(numbers.get(i));
        }
        return values;
    }

    private static int[] toInts(List<String> numbers) {
        int[] values = new int[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            values[i] = Integer.parseInt(numbers.get(i));
        }
        return values;
    }

    private static int findMatchingBrace(String text, int openBrace) {
        int depth = 0;
        boolean quoted = false;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (quoted) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String cleanName(String raw) {
        int marker = raw.indexOf("::");
        return marker >= 0 ? raw.substring(marker + 2) : raw;
    }

    private static String materialName(List<String> materialNames, int[] materialIndices, int polygonIndex) {
        int materialIndex = polygonIndex < materialIndices.length ? materialIndices[polygonIndex] : 0;
        if (materialIndex >= 0 && materialIndex < materialNames.size()) {
            return materialNames.get(materialIndex);
        }
        return materialNames.isEmpty() ? "" : materialNames.get(0);
    }

    private record FbxObject(String kind, long id, String name, String type, String body) {
    }

    private record VertexWeight(int boneIndex, float weight) {
    }

    private record Corner(int index) {
    }
}
