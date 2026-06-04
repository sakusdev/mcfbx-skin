package dev.sakusdev.armatureskin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArmatureModelBoundsTest {
    @Test
    void acceptsFlatButNonEmptyBounds() {
        ArmatureModel.Bounds flatXz = new ArmatureModel.Bounds(-1.0F, 0.0F, -1.0F, 1.0F, 0.0F, 1.0F);

        assertTrue(flatXz.valid());
    }

    @Test
    void rejectsSinglePointBounds() {
        ArmatureModel.Bounds point = new ArmatureModel.Bounds(1.0F, 2.0F, 3.0F, 1.0F, 2.0F, 3.0F);

        assertFalse(point.valid());
    }
}
