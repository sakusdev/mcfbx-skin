package dev.codex.armatureskin.render;

import dev.codex.armatureskin.model.ArmatureModel;
import org.joml.Matrix4f;

import java.util.Locale;

final class ProceduralAnimator {
    private ProceduralAnimator() {
    }

    static Matrix4f animateBone(ArmatureModel.Bone bone, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        Matrix4f animated = new Matrix4f(bone.localBindTransform());
        String name = bone.name().toLowerCase(Locale.ROOT);

        float walk = (float) Math.sin(limbAngle * 0.6662F) * Math.min(limbDistance, 1.0F);
        float counterWalk = (float) Math.sin(limbAngle * 0.6662F + Math.PI) * Math.min(limbDistance, 1.0F);
        boolean left = name.contains("left") || name.endsWith(".l") || name.endsWith("_l");
        float sideWalk = left ? walk : counterWalk;

        if (containsAny(name, "thigh", "upperleg", "leg_l", "leg.r", "leg_r", "upleg")) {
            animated.rotateX(sideWalk * 0.85F);
        } else if (containsAny(name, "shin", "calf", "lowerleg")) {
            animated.rotateX(Math.max(0.0F, -sideWalk) * 0.55F);
        } else if (containsAny(name, "foot", "toe")) {
            animated.rotateX(sideWalk * 0.25F);
        } else if (containsAny(name, "upperarm", "arm_l", "arm.r", "arm_r")) {
            animated.rotateX(-sideWalk * 0.65F);
        } else if (containsAny(name, "forearm", "lowerarm", "hand")) {
            animated.rotateX(-sideWalk * 0.25F);
        } else if (containsAny(name, "head", "neck")) {
            animated.rotateY(headYaw * 0.017453292F);
            animated.rotateX(headPitch * 0.017453292F);
        } else if (containsAny(name, "spine", "chest", "torso")) {
            animated.rotateZ((float) Math.sin(age * 0.08F) * 0.015F);
        }

        return animated;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
