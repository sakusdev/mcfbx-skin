using System;
using System.Collections.Generic;

namespace Sakutarooo.Minecraft3DSkinExporter.Editor
{
    [Serializable]
    public sealed class Mc3dSkinManifest
    {
        public string format = "mc3dskin";
        public int formatVersion = 1;
        public string packageId;
        public string packageVersion = "1.0.0";
        public string displayName;
        public string author;
        public string model;
        public string defaultTexture;
        public List<string> textures = new();
        public string rig = "generic";
        public string target = "player_skin";
        public string contentSha256 = "";
    }
}
