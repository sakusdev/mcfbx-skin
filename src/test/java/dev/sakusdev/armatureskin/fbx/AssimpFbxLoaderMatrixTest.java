package dev.sakusdev.armatureskin.fbx;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AIMatrix4x4;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AssimpFbxLoaderMatrixTest {
    @Test
    void convertsAssimpTranslationIntoJomlModelSpaceTranslation() {
        AIMatrix4x4 assimp = AIMatrix4x4.calloc();
        try {
            assimp.a1(1.0F);
            assimp.b2(1.0F);
            assimp.c3(1.0F);
            assimp.d4(1.0F);
            assimp.a4(3.0F);
            assimp.b4(4.0F);
            assimp.c4(5.0F);

            Matrix4f converted = AssimpFbxLoader.toJoml(assimp);
            Vector3f transformed = new Vector3f(1.0F, 2.0F, 3.0F).mulPosition(converted);

            assertEquals(4.0F, transformed.x(), 0.0001F);
            assertEquals(6.0F, transformed.y(), 0.0001F);
            assertEquals(8.0F, transformed.z(), 0.0001F);
        } finally {
            assimp.free();
        }
    }
}
