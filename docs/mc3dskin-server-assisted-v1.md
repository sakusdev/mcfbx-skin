# mc3dskin Server-Assisted v1

This draft is intentionally local-only until the protocol is settled.

## Modes

- `plain_zip`: unsigned development package. Useful for Unity exporter iteration.
- `aes_gcm_zip`: encrypted ZIP payload. The client obtains the AES content key from a provider.

## Providers

- External HTTPS provider: client-only Minecraft use. The client POSTs package/player metadata to `licenseServerUrl`.
- Server handshake provider: server+client mod use. The same `LicenseProvider` interface will be backed by NeoForge custom payloads.
- Cached provider: offline fallback using grants previously returned by a live provider.

## Framed Encrypted Package

```text
magic:   8 bytes  "MC3DSK2\0"
header:  uint32 big-endian JSON byte length
header:  UTF-8 JSON Mc3dSkinHeader
payload: plain ZIP bytes or AES-GCM ciphertext of ZIP bytes
```

`Mc3dSkinHeader` includes:

- `format`: `mc3dskin`
- `formatVersion`: `1`
- `packageId`
- `packageVersion`
- `displayName`
- `author`
- `contentMode`: `plain_zip` or `aes_gcm_zip`
- `keyId`
- `nonce`: base64 AES-GCM nonce
- `licenseServerUrl`
- `licenseProviderPublicKey`: base64 X.509 Ed25519 public key used to verify license responses
- `signature`: reserved for signed headers

## Plain ZIP Payload

```text
manifest.json
model/avatar.fbx
textures/body.png
textures/hair.png
```

The Java mod can load this today. Encrypted packages use the same ZIP payload after decryption.

## Minecraft Server License Flow

The client keeps a local X25519 device key pair under:

```text
.minecraft/config/armature-fbx-skin/device-x25519-key.json
```

When an encrypted package needs a key, the client creates a `LicenseRequest`:

```json
{
  "protocol": "mc3dskin-license",
  "protocolVersion": 1,
  "packageId": "creator.avatar",
  "packageVersion": "1.0.0",
  "keyId": "content-key-v1",
  "minecraftUuid": "player uuid",
  "minecraftName": "player name",
  "modId": "armature_fbx_skin",
  "devicePublicKey": "base64 X25519 X.509 public key",
  "clientNonce": "base64 random nonce",
  "requestedAt": "2026-05-15T00:00:00Z"
}
```

For server+client mod operation, `ServerHandshakeLicenseProvider.drainPendingRequests()` is the handoff point for a NeoForge custom payload sender. The server mod decides entitlement, wraps the content key to `devicePublicKey`, signs the response, and sends it back. The client calls `ServerHandshakeLicenseProvider.acceptServerResponse(...)` when the packet arrives.

The implemented NeoForge payloads are:

- `armature_fbx_skin:license_request`: client to Minecraft server, JSON encoded `LicenseRequest`
- `armature_fbx_skin:license_response`: Minecraft server to client, JSON encoded `SignedLicenseResponse`

The Minecraft server reads grants from:

```text
config/armature-fbx-skin/server-licenses.json
```

If the file does not exist, the server creates a template. `providerPrivateKey` is a base64 PKCS#8 Ed25519 private key and must stay server-side. `providerPublicKey` must match the public key embedded in the encrypted `.mc3dskin` header. `contentKey` is the base64 AES package content key. `allowedPlayers` accepts player UUIDs, player names, or `*`.

## Signed Response + Wrapped Key

The server returns a `SignedLicenseResponse`:

```json
{
  "protocol": "mc3dskin-license",
  "protocolVersion": 1,
  "status": "granted",
  "packageId": "creator.avatar",
  "packageVersion": "1.0.0",
  "licenseId": "server-license-id",
  "issuedAt": "2026-05-15T00:00:00Z",
  "expiresAt": "2026-05-22T00:00:00Z",
  "offlineUntil": "2026-05-16T00:00:00Z",
  "keyId": "content-key-v1",
  "minecraftUuid": "authorized viewer uuid",
  "devicePublicKeyHash": "sha256 of authorized viewer X25519 public key",
  "serverId": "issuing minecraft server id",
  "clientNonce": "copied from request",
  "serverNonce": "base64 random nonce",
  "providerKeyId": "minecraft-server-key-1",
  "signatureAlgorithm": "Ed25519",
  "wrappedKeys": [
    {
      "keyId": "content-key-v1",
      "algorithm": "X25519-HKDF-SHA256+A256GCM",
      "ephemeralPublicKey": "base64 X25519 X.509 public key",
      "nonce": "base64 AES-GCM nonce",
      "aad": "mc3dskin-license:creator.avatar",
      "ciphertext": "base64 wrapped AES content key"
    }
  ],
  "signature": "base64 Ed25519 signature"
}
```

The signature covers a deterministic newline-delimited canonical representation of every response field except `signature`, including every wrapped key field. `minecraftUuid`, `devicePublicKeyHash`, and `serverId` are signed so a response issued for one viewer/device/server cannot be replayed as a reusable unlock token for a different viewer.

The wrapped key algorithm:

1. Server generates ephemeral X25519 key pair.
2. Server performs X25519 with client `devicePublicKey`.
3. Both sides derive a 32-byte KEK with HKDF-SHA256.
4. Server encrypts the package AES content key with AES-GCM.
5. Client verifies Ed25519 signature before unwrapping.
6. Client unwraps with its local X25519 private key.

`LicenseCrypto.wrapContentKeyForClient(...)` and `LicenseCrypto.signResponse(...)` are server-side helpers. `LicenseCrypto.verifyAndUnwrap(...)` is the client-side verifier/unwrapper.

## Viewer Delivery Goal

For "send this avatar to another client so they can see it, but cannot reuse it", the intended flow is:

1. The Minecraft server sends or references the encrypted `.mc3dskin` package to a viewer client.
2. The viewer client requests a key with its own X25519 device public key and Minecraft UUID.
3. The server checks whether that viewer is allowed to see the package.
4. The server returns a signed response whose content key is wrapped only to that viewer device key.
5. The viewer can render the model, but the response is not useful on another client because the signature binds it to `minecraftUuid`, `devicePublicKeyHash`, and `serverId`, while the wrapped key can only be unwrapped by that device private key.

This does not prevent extraction by a fully compromised authorized viewer after the model is decrypted for rendering. It does prevent simple file reuse, response replay on another client, and copying `.mc3dskin` without receiving a fresh server-issued viewer grant.
