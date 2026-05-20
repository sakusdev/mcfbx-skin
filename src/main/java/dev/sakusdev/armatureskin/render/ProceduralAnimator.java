package dev.sakusdev.armatureskin.render;

import dev.sakusdev.armatureskin.model.ArmatureModel;
import org.joml.Matrix4f;

import java.util.Locale;

final class ProceduralAnimator {
    private static final float MAX_WALK_SWING = 0.42F;

    private ProceduralAnimator() {
    }

    static Matrix4f animateBone(ArmatureModel.Bone bone, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, float strength, boolean crouching) {
        Matrix4f animated = new Matrix4f(bone.localBindTransform());
        String name = bone.name().toLowerCase(Locale.ROOT);

        float animationStrength = Math.max(0.0F, Math.min(strength, 1.5F));
        float speed = Math.min(Math.max(limbDistance - 0.02F, 0.0F), 0.65F) * animationStrength * 2.4F;
        float phase = limbAngle * 0.6662F;
        float walk = clamp((float) Math.sin(phase) * speed, -MAX_WALK_SWING, MAX_WALK_SWING);
        float counterWalk = -walk;
        boolean left = isLeft(name);
        float sideWalk = left ? walk : counterWalk;
        float crouch = crouching ? 1.0F : 0.0F;

        if (isHead(name)) {
            animated.rotateY(clamp((float) Math.toRadians(headYaw), -0.75F, 0.75F));
            animated.rotateX(clamp((float) Math.toRadians(headPitch), -0.75F, 0.75F));
        } else if (isNeck(name)) {
            animated.rotateY(clamp((float) Math.toRadians(headYaw) * 0.35F, -0.35F, 0.35F));
            animated.rotateX(clamp((float) Math.toRadians(headPitch) * 0.35F, -0.35F, 0.35F));
        } else if (isSpine(name)) {
            animated.rotateX(0.18F * crouch);
        } else if (isUpperLeg(name)) {
            animated.rotateX(sideWalk - 0.32F * crouch);
        } else if (isLowerLeg(name)) {
            animated.rotateX(Math.max(0.0F, -sideWalk) * 0.45F + 0.42F * crouch);
        } else if (isUpperArm(name)) {
            animated.rotateX(sideWalk * 0.28F + 0.04F * crouch);
        } else if (isLowerArm(name)) {
            animated.rotateX(sideWalk * 0.10F);
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
        return startsWithAny(name, "upperleg", "upper_leg", "upleg", "thigh", "leftupperleg", "rightupperleg", "leftleg", "rightleg")
                || containsAny(name, "j_bip_l_upperleg", "j_bip_r_upperleg");
    }

    private static boolean isLowerLeg(String name) {
        return startsWithAny(name, "lowerleg", "lower_leg", "shin", "calf", "leftlowerleg", "rightlowerleg")
                || containsAny(name, "j_bip_l_lowerleg", "j_bip_r_lowerleg");
    }

    private static boolean isUpperArm(String name) {
        if (isAuxiliaryArmBone(name)) {
            return false;
        }
        return startsWithAny(name, "upperarm", "upper_arm", "leftupperarm", "rightupperarm", "leftarm", "rightarm")
                || containsAny(name, "j_bip_l_upperarm", "j_bip_r_upperarm");
    }

    private static boolean isLowerArm(String name) {
        if (isAuxiliaryArmBone(name)) {
            return false;
        }
        return startsWithAny(name, "lowerarm", "lower_arm", "forearm", "leftlowerarm", "rightlowerarm")
                || containsAny(name, "j_bip_l_lowerarm", "j_bip_r_lowerarm");
    }

    private static boolean isHead(String name) {
        return name.equals("head")
                || name.endsWith("_head")
                || name.endsWith(".head")
                || containsAny(name, "j_bip_c_head", "mixamorig:head", "mixamorig_head");
    }

    private static boolean isNeck(String name) {
        return name.equals("neck")
                || name.endsWith("_neck")
                || name.endsWith(".neck")
                || containsAny(name, "j_bip_c_neck", "mixamorig:neck", "mixamorig_neck");
    }

    private static boolean isSpine(String name) {
        return name.equals("spine")
                || name.equals("chest")
                || name.equals("upperchest")
                || containsAny(name, "spine", "chest", "j_bip_c_spine", "j_bip_c_chest", "mixamorig:spine", "mixamorig_spine");
    }

    private static boolean isAuxiliaryArmBone(String name) {
        return containsAny(name, "twist", "roll", "adjust", "correct", "helper");
    }

    private static boolean startsWithAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
