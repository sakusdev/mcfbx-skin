using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using UnityEditor;
using UnityEngine;

namespace Sakutarooo.Minecraft3DSkinExporter.Editor
{
    public static class Mc3dSkinExporter
    {
        public static void ExportPlainPackage(GameObject avatarRoot, string outputPath, string packageId, string displayName, string author)
        {
            if (avatarRoot == null)
            {
                throw new ArgumentNullException(nameof(avatarRoot));
            }

            string modelAssetPath = FindModelAssetPath(avatarRoot);
            if (string.IsNullOrEmpty(modelAssetPath))
            {
                throw new InvalidOperationException("The selected avatar must be an imported FBX prefab or an instance of one.");
            }

            string projectRoot = Directory.GetParent(Application.dataPath)!.FullName;
            string modelFilePath = Path.Combine(projectRoot, modelAssetPath);
            if (!File.Exists(modelFilePath))
            {
                throw new FileNotFoundException("FBX model file was not found.", modelFilePath);
            }

            List<Texture2D> textures = CollectTextures(avatarRoot);
            Directory.CreateDirectory(Path.GetDirectoryName(outputPath)!);

            using FileStream fileStream = File.Create(outputPath);
            using ZipArchive archive = new(fileStream, ZipArchiveMode.Create);

            string modelEntry = "model/" + Path.GetFileName(modelFilePath);
            archive.CreateEntryFromFile(modelFilePath, modelEntry, CompressionLevel.Optimal);

            List<string> textureEntries = new();
            foreach (Texture2D texture in textures)
            {
                string entry = "textures/" + SafeFileName(texture.name) + ".png";
                byte[] pngBytes = ExportTexturePng(texture);
                ZipArchiveEntry textureEntry = archive.CreateEntry(entry, CompressionLevel.Optimal);
                using Stream stream = textureEntry.Open();
                stream.Write(pngBytes, 0, pngBytes.Length);
                textureEntries.Add(entry);
            }

            Mc3dSkinManifest manifest = new()
            {
                packageId = string.IsNullOrWhiteSpace(packageId) ? Guid.NewGuid().ToString("N") : packageId,
                displayName = string.IsNullOrWhiteSpace(displayName) ? avatarRoot.name : displayName,
                author = author,
                model = modelEntry,
                defaultTexture = textureEntries.FirstOrDefault() ?? "",
                textures = textureEntries,
                rig = DetectRig(avatarRoot)
            };

            string json = JsonUtility.ToJson(manifest, true);
            ZipArchiveEntry manifestEntry = archive.CreateEntry("manifest.json", CompressionLevel.Optimal);
            using StreamWriter writer = new(manifestEntry.Open());
            writer.Write(json);
        }

        public static List<string> Validate(GameObject avatarRoot)
        {
            List<string> errors = new();
            if (avatarRoot == null)
            {
                errors.Add("Select an avatar root or imported FBX prefab.");
                return errors;
            }
            if (!avatarRoot.GetComponentsInChildren<Renderer>(true).Any())
            {
                errors.Add("No MeshRenderer or SkinnedMeshRenderer was found.");
            }
            if (string.IsNullOrEmpty(FindModelAssetPath(avatarRoot)))
            {
                errors.Add("Could not resolve an imported FBX asset path.");
            }
            return errors;
        }

        private static string FindModelAssetPath(GameObject avatarRoot)
        {
            string prefabPath = AssetDatabase.GetAssetPath(avatarRoot);
            if (!string.IsNullOrEmpty(prefabPath) && prefabPath.EndsWith(".fbx", StringComparison.OrdinalIgnoreCase))
            {
                return prefabPath;
            }

            GameObject prefab = PrefabUtility.GetCorrespondingObjectFromSource(avatarRoot);
            string sourcePath = prefab == null ? "" : AssetDatabase.GetAssetPath(prefab);
            return sourcePath.EndsWith(".fbx", StringComparison.OrdinalIgnoreCase) ? sourcePath : "";
        }

        private static List<Texture2D> CollectTextures(GameObject avatarRoot)
        {
            HashSet<Texture2D> textures = new();
            foreach (Renderer renderer in avatarRoot.GetComponentsInChildren<Renderer>(true))
            {
                foreach (Material material in renderer.sharedMaterials)
                {
                    if (material == null)
                    {
                        continue;
                    }
                    AddTexture(material, "_BaseMap", textures);
                    AddTexture(material, "_MainTex", textures);
                }
            }
            return textures.ToList();
        }

        private static void AddTexture(Material material, string property, HashSet<Texture2D> textures)
        {
            if (!material.HasProperty(property))
            {
                return;
            }
            if (material.GetTexture(property) is Texture2D texture)
            {
                textures.Add(texture);
            }
        }

        private static byte[] ExportTexturePng(Texture2D texture)
        {
            string path = AssetDatabase.GetAssetPath(texture);
            if (!string.IsNullOrEmpty(path) && File.Exists(path) && path.EndsWith(".png", StringComparison.OrdinalIgnoreCase))
            {
                return File.ReadAllBytes(path);
            }

            RenderTexture temporary = RenderTexture.GetTemporary(texture.width, texture.height, 0, RenderTextureFormat.ARGB32);
            Graphics.Blit(texture, temporary);
            RenderTexture previous = RenderTexture.active;
            RenderTexture.active = temporary;
            Texture2D readable = new(texture.width, texture.height, TextureFormat.RGBA32, false);
            readable.ReadPixels(new Rect(0, 0, texture.width, texture.height), 0, 0);
            readable.Apply();
            RenderTexture.active = previous;
            RenderTexture.ReleaseTemporary(temporary);
            byte[] png = readable.EncodeToPNG();
            UnityEngine.Object.DestroyImmediate(readable);
            return png;
        }

        private static string DetectRig(GameObject avatarRoot)
        {
            Animator animator = avatarRoot.GetComponentInChildren<Animator>(true);
            return animator != null && animator.avatar != null && animator.avatar.isHuman ? "humanoid" : "generic";
        }

        private static string SafeFileName(string value)
        {
            foreach (char invalid in Path.GetInvalidFileNameChars())
            {
                value = value.Replace(invalid, '_');
            }
            return string.IsNullOrWhiteSpace(value) ? "texture" : value;
        }
    }
}
