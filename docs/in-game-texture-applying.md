# In-Game Texture Applying

The intended "model edit" scope is texture applying, not full mesh editing.

## Current Client-Only Flow

1. Put an FBX model under `.minecraft/fbx`.
2. Put UV textures (`.png`, `.jpg`, `.jpeg`) in the same folder or a subfolder under `.minecraft/fbx`.
3. Press `K` in game.
4. Select the FBX on the left.
5. Select a UV texture on the right.

The selected texture is loaded as a Minecraft `DynamicTexture` and applied to the whole FBX render using the UV coordinates parsed from the FBX.

## What This Means

This supports the common "I already have a UV-unwrapped model and an image texture" workflow without Unity.

It does not yet support per-material texture slots. If an FBX has multiple material assignments, the current renderer still uses one chosen texture for the whole model.

## Next Practical Step

Add material/slot awareness:

- parse FBX material names and polygon material indices
- split `ArmatureModel.Mesh` by material slot
- store texture assignment per material slot in config
- extend the in-game screen to show `Material Slot -> Texture`

That would make Unity unnecessary for most texture assignment work while still avoiding full in-game modeling complexity.
