# Client-Only Baseline

This mod must work as a normal client-only NeoForge mod first.

## Client-Only Inputs

Files placed under `.minecraft/fbx` are discovered at client startup and when reloaded:

- `.fbx`: direct FBX skin model
- `.mc3dskin`: packaged FBX plus textures
- `.png`, `.jpg`, `.jpeg`: external UV textures for direct `.fbx` skins

The selector opens with `K` by default.

## Client-Only Package Modes

- `plain_zip` `.mc3dskin` works entirely offline.
- `aes_gcm_zip` can work client-only when `licenseServerUrl` points to an external HTTPS license service.
- If no cached grant and no HTTPS license is available, the Minecraft server handshake is tried last.

The client license provider order is:

```text
cached grant -> HTTPS provider -> Minecraft server handshake provider
```

This keeps normal client-only use from depending on a Minecraft server.

## Server Upload Boundary

When a Minecraft server is used, the server should only receive or distribute viewer-facing assets:

- encrypted `.mc3dskin` package bytes
- public package metadata
- viewer-specific signed license responses

The server upload/distribution path must not include:

- provider private keys
- raw content keys outside server-only config
- decrypted FBX or texture sources
- Unity project source assets unless intentionally published

The intended server use is "let other clients see this avatar", not "give other clients a reusable skin package". Viewer grants are bound to the viewer Minecraft UUID, viewer device public key hash, and issuing server id.
