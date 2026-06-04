package dev.sakusdev.armatureskin.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArmatureSkinRendererNormalTest {
    @Test
    void usesInverseTransposeForNonUniformScaleNormals() {
        Matrix4f nonUniformScale = new Matrix4f().scaling(2.0F, 3.0F, 1.0F);
        Vector3f normal = new Vector3f(1.0F, 1.0F, 0.0F).normalize();

        Vector3f transformed = ArmatureSkinRenderer.transformNormal(normal, ArmatureSkinRenderer.normalMatrix(nonUniformScale)).normalize();

        assertEquals(0.83205F, transformed.x(), 0.0001F);
        assertEquals(0.55470F, transformed.y(), 0.0001F);
        assertEquals(0.0F, transformed.z(), 0.0001F);
    }
}
