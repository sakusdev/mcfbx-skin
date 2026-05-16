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

        float speed = Math.min(limbDistance, 1.0F);
        float phase = limbAngle * 0.6662F;
        float walk = (float) Math.sin(phase) * speed;
        float counterWalk = (float) Math.sin(phase + Math.PI) * speed;
        boolean left = isLeft(name);
        float sideWalk = left ? walk : counterWalk;

        if (isUpperLeg(name)) {
            animated.rotateX(sideWalk * 0.95F);
            animated.rotateZ((left ? -1.0F : 1.0F) * speed * 0.03F);
        } else if (isLowerLeg(name)) {
            animated.rotateX(Math.max(0.0F, -sideWalk) * 0.75F);
        } else if (containsAny(name, "foot", "toe")) {
            animated.rotateX(sideWalk * 0.3F);
        } else if (isUpperArm(name)) {
            animated.rotateX(-sideWalk * 0.75F);
            animated.rotateZ((left ? 1.0F : -1.0F) * speed * 0.04F);
        } else if (isLowerArm(name)) {
            animated.rotateX(-sideWalk * 0.35F);
        } else if (containsAny(name, "head", "neck")) {
            animated.rotateY(headYaw * 0.017453292F);
            animated.rotateX(headPitch * 0.017453292F);
        } else if (containsAny(name, "hips", "pelvis", "root")) {
            animated.translate(0.0F, Math.abs((float) Math.sin(phase * 2.0F)) * speed * 0.035F, 0.0F);
            animated.rotateZ((float) Math.sin(phase) * speed * 0.035F);
        } else if (containsAny(name, "spine", "chest", "torso")) {
            animated.rotateZ((float) Math.sin(phase) * speed * 0.025F + (float) Math.sin(age * 0.08F) * 0.01F);
        }

        return animated;
    }

    private static boolean isLeft(String name) {
        return name.contains("left")
                || name.contains("_l_")
                || name.contains(".l.")
                || name.contains(":l_")
                || name.endsWith(".l")
                || name.endsWith("_l")
                || name.endsWith(" l");
    }

    private static boolean isUpperLeg(String name) {
        return containsAny(name, "thigh", "upperleg", "upleg", "upper_leg", "upleg", "leg_l", "leg_r", "j_bip_l_upperleg", "j_bip_r_upperleg");
    }

    private static boolean isLowerLeg(String name) {
        return containsAny(name, "shin", "calf", "lowerleg", "lower_leg", "legdown", "j_bip_l_lowerleg", "j_bip_r_lowerleg");
    }

    private static boolean isUpperArm(String name) {
        return containsAny(name, "upperarm", "upper_arm", "arm_l", "arm_r", "j_bip_l_upperarm", "j_bip_r_upperarm");
    }

    private static boolean isLowerArm(String name) {
        return containsAny(name, "forearm", "lowerarm", "lower_arm", "j_bip_l_lowerarm", "j_bip_r_lowerarm", "hand");
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
