# Armature FBX Skin

Client-only NeoForge mod for Minecraft 1.21.1 that renders an FBX model with an armature as the local player skin. The server does not need the mod because the replacement happens entirely in the client renderer.

## Usage

1. Export an ASCII FBX from Blender. Include the mesh, armature, skin weights, UVs, and a material that uses a Minecraft-compatible skin texture layout.
2. Build the mod with Java 21 and put the generated jar into a NeoForge 1.21.1 client's `mods` folder.
3. Start Minecraft once so the config file is created at `config/armature-fbx-skin.json`.
4. Put ASCII `.fbx` skins in the `.minecraft/fbx` folder. Subfolders are supported.
5. Set `selectedSkinId` to the discovered skin id, or set `selectedSkinPath` to an absolute path or a path relative to the `.minecraft` game directory.
6. Press `R` in game to reload the model.

```json
{
  "enabled": true,
  "localPlayerOnly": true,
  "selectedSkinId": "",
  "selectedSkinPath": "",
  "fbxPath": "",
  "scale": 0.01,
  "yOffset": 0.0,
  "mirrorVanillaSneak": true,
  "forceOpaqueSkin": true
}
```

Skin ids are based on the path under `.minecraft/fbx` without the `.fbx` extension. For example, `.minecraft/fbx/player_ascii.fbx` has id `player_ascii`, and `.minecraft/fbx/custom/alex.fbx` has id `custom/alex`. If no configured selection resolves, the first discovered ASCII FBX skin is used. `fbxPath` is kept as a legacy fallback for older configs. `forceOpaqueSkin` is enabled by default so transparent pixels in the Minecraft skin overlay do not make FBX surfaces disappear.

## Notes

- This first implementation targets ASCII FBX files, which Blender can export.
- Minecraft 1.21.1/NeoForge targets Java 21.
- Bone animation is generated procedurally from the player's walking state. Bone names containing common words such as `arm`, `forearm`, `thigh`, `leg`, `shin`, `foot`, `head`, `neck`, `spine`, or `chest` receive sensible Minecraft-style motion.
- The renderer uses the player's current skin texture and the FBX UVs.
