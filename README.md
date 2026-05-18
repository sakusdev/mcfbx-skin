# Armature FBX Skin

Client-only NeoForge mod for Minecraft 1.21.1 that renders an FBX model with an armature as the local player skin. The server does not need the mod because the replacement happens entirely in the client renderer.

## Usage

1. Export an FBX from Blender. ASCII and binary FBX are supported. Include the mesh, armature, skin weights, UVs, and UVs for your texture image.
2. Build the mod with Java 21 and put the generated jar into a NeoForge 1.21.1 client's `mods` folder.
3. Start Minecraft once so the config file is created at `config/armature-fbx-skin.json`.
4. Put `.fbx` skins in the `.minecraft/fbx` folder. Subfolders are supported.
5. Put optional `.png`, `.jpg`, or `.jpeg` UV textures in the same `.minecraft/fbx` folder tree. A texture next to an FBX with the same basename is preferred automatically, such as `alex.fbx` and `alex.png`. Multiple textures are supported as selectable candidates.
6. Set `selectedSkinId` to the discovered skin id, or set `selectedSkinPath` to an absolute path or a path relative to the `.minecraft` game directory. Set `selectedTextureId` or `selectedTexturePath` to override the automatically preferred texture.
7. Press `K` in game to open the FBX/Texture selector. Use it to choose the FBX model and UV texture; chat prompts also point back to `K` when no model is loaded.
8. Press `R` in game to reload the model.

```json
{
  "enabled": true,
  "localPlayerOnly": true,
  "selectedSkinId": "",
  "selectedSkinPath": "",
  "selectedTextureId": "",
  "selectedTexturePath": "",
  "fbxPath": "",
  "disabledMeshKeys": "",
  "meshTextureAssignments": {},
  "scale": 1.0,
  "yOffset": 0.0,
  "modelYawOffsetDegrees": 0.0,
  "animationEnabled": true,
  "animationStrength": 0.18,
  "mirrorVanillaSneak": true,
  "forceOpaqueSkin": true
}
```

Skin ids are based on the path under `.minecraft/fbx` without the `.fbx` extension. For example, `.minecraft/fbx/player_binary.fbx` has id `player_binary`, and `.minecraft/fbx/custom/alex.fbx` has id `custom/alex`. Texture ids use the same rule with `.png`, `.jpg`, or `.jpeg` removed. If no configured skin selection resolves, the first discovered FBX skin is used. If no configured texture selection resolves, the selected skin prefers a sibling texture with the same basename, then the first sibling texture. Material textures are matched by FBX material name, so files such as `kipfel_body_skin.png` and `kipfel_hair.png` can be used together. `fbxPath` is kept as a legacy fallback for older configs. `forceOpaqueSkin` is enabled by default so transparent pixels use cutout rendering instead of making FBX surfaces disappear.

## Notes

- Binary FBX support covers the mesh, armature, UV, skin cluster, and object connection data used by Blender-style character exports.
- Minecraft 1.21.1/NeoForge targets Java 21.
- Bone animation is generated procedurally from the player's walking state. Common humanoid `upperleg`, `lowerleg`, `upperarm`, and `lowerarm` bones are driven with a relaxed standing arm pose and a visible walk swing; set `animationStrength` lower or `animationEnabled` false if an FBX uses unusual bone axes.
- The renderer uses the selected UV texture when one is available, then material-matched textures, and falls back to the player's current skin texture.
