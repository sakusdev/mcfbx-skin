using System.Collections.Generic;
using UnityEditor;
using UnityEngine;

namespace Sakutarooo.Minecraft3DSkinExporter.Editor
{
    public sealed class Mc3dSkinExporterWindow : EditorWindow
    {
        private GameObject avatarRoot;
        private string packageId = "";
        private string displayName = "";
        private string author = "Sakutarooo";
        private string outputPath = "avatar.mc3dskin";
        private Vector2 scroll;

        [MenuItem("Window/Minecraft 3D Skin Exporter")]
        public static void Open()
        {
            GetWindow<Mc3dSkinExporterWindow>("MC 3D Skin");
        }

        private void OnGUI()
        {
            scroll = EditorGUILayout.BeginScrollView(scroll);
            EditorGUILayout.LabelField("Avatar", EditorStyles.boldLabel);
            avatarRoot = (GameObject)EditorGUILayout.ObjectField("Root / FBX Prefab", avatarRoot, typeof(GameObject), true);
            packageId = EditorGUILayout.TextField("Package ID", packageId);
            displayName = EditorGUILayout.TextField("Display Name", displayName);
            author = EditorGUILayout.TextField("Author", author);

            EditorGUILayout.Space(8);
            EditorGUILayout.LabelField("Output", EditorStyles.boldLabel);
            EditorGUILayout.BeginHorizontal();
            outputPath = EditorGUILayout.TextField("Path", outputPath);
            if (GUILayout.Button("Browse", GUILayout.Width(80)))
            {
                string selected = EditorUtility.SaveFilePanel("Export .mc3dskin", "", string.IsNullOrWhiteSpace(displayName) ? "avatar.mc3dskin" : displayName + ".mc3dskin", "mc3dskin");
                if (!string.IsNullOrEmpty(selected))
                {
                    outputPath = selected;
                }
            }
            EditorGUILayout.EndHorizontal();

            EditorGUILayout.Space(8);
            List<string> errors = Mc3dSkinExporter.Validate(avatarRoot);
            foreach (string error in errors)
            {
                EditorGUILayout.HelpBox(error, MessageType.Error);
            }

            using (new EditorGUI.DisabledScope(errors.Count > 0 || string.IsNullOrWhiteSpace(outputPath)))
            {
                if (GUILayout.Button("Export .mc3dskin", GUILayout.Height(32)))
                {
                    Export();
                }
            }

            EditorGUILayout.HelpBox("This first exporter writes a plain .mc3dskin package containing the original FBX and collected material textures. Server-assisted encryption will wrap this payload in the next step.", MessageType.Info);
            EditorGUILayout.EndScrollView();
        }

        private void Export()
        {
            try
            {
                Mc3dSkinExporter.ExportPlainPackage(avatarRoot, outputPath, packageId, displayName, author);
                EditorUtility.DisplayDialog("Minecraft 3D Skin Exporter", "Exported .mc3dskin package.", "OK");
            }
            catch (System.Exception ex)
            {
                Debug.LogException(ex);
                EditorUtility.DisplayDialog("Minecraft 3D Skin Exporter", ex.Message, "OK");
            }
        }
    }
}
