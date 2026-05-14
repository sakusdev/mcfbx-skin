package dev.codex.armatureskin.fbx;

import dev.codex.armatureskin.model.ArmatureModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AsciiFbxLoaderFixtureSanity {
    private AsciiFbxLoaderFixtureSanity() {
    }

    public static void main(String[] args) throws IOException {
        Path fixture = Files.createTempFile("armature-loader-fixture-", ".fbx");
        Files.writeString(fixture, minimalWeightedTriangleFbx(), StandardCharsets.UTF_8);

        ArmatureModel model = new AsciiFbxLoader().load(fixture);
        require(model.bones().size() == 1, "expected one LimbNode bone");
        require(model.rootBoneIndex() == 0, "expected the only bone to be the root");
        require(model.bones().getFirst().name().equals("Root"), "expected FBX namespace to be stripped from bone name");

        require(model.meshes().size() == 1, "expected one mesh");
        ArmatureModel.Mesh mesh = model.meshes().getFirst();
        require(mesh.vertices().size() == 3, "expected one triangle worth of emitted vertices");
        require(mesh.indices().length == 3, "expected triangulated index triplet");
        require(mesh.indices()[0] == 0 && mesh.indices()[1] == 1 && mesh.indices()[2] == 2, "expected triangle indices in winding order");

        ArmatureModel.Vertex first = mesh.vertices().getFirst();
        require(first.x() == 0.0F && first.y() == 0.0F && first.z() == 0.0F, "expected first control point position");
        require(first.u() == 0.0F && first.v() == 1.0F, "expected FBX V coordinate to be flipped");
        require(first.boneIndices().length == 1 && first.boneIndices()[0] == 0, "expected first vertex to bind to root bone");
        require(first.weights().length == 1 && first.weights()[0] == 1.0F, "expected first vertex weight to normalize to 1.0");

        expectAsciiRejection();
    }

    public static String minimalWeightedTriangleFbx() {
        return """
                FBXHeaderExtension:  {
                    FBXHeaderVersion: 1003
                }
                Objects:  {
                    Model: 100, "Model::Root", "LimbNode" {
                        Properties70:  {
                            P: "Lcl Translation", "Lcl Translation", "", "A",0,0,0
                        }
                    }
                    Geometry: 200, "Geometry::Triangle", "Mesh" {
                        Vertices: *9 {
                            a: 0,0,0, 1,0,0, 0,1,0
                        }
                        PolygonVertexIndex: *3 {
                            a: 0,1,-3
                        }
                        LayerElementUV: 0 {
                            UV: *6 {
                                a: 0,0, 1,0, 0,1
                            }
                            UVIndex: *3 {
                                a: 0,1,2
                            }
                        }
                    }
                    Deformer: 300, "Deformer::Skin", "Skin" {
                    }
                    Deformer: 400, "SubDeformer::RootCluster", "Cluster" {
                        Indexes: *3 {
                            a: 0,1,2
                        }
                        Weights: *3 {
                            a: 1,1,1
                        }
                    }
                }
                Connections:  {
                    C: "OO",400,100
                    C: "OO",400,300
                    C: "OO",300,200
                }
                """;
    }

    private static void expectAsciiRejection() throws IOException {
        Path fixture = Files.createTempFile("armature-loader-binary-marker-", ".fbx");
        Files.write(fixture, new byte[]{'F', 'B', 'X', 0});
        try {
            new AsciiFbxLoader().load(fixture);
        } catch (IOException expected) {
            require(expected.getMessage().contains("ASCII FBX"), "expected ASCII-only rejection message");
            return;
        }
        throw new IllegalStateException("expected binary-like FBX fixture to be rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
