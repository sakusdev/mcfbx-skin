# Test Fixtures

`java-library` provides a `src/test` source set, but this project does not currently configure a test framework dependency. The Java-side loader sanity helper is therefore a plain executable fixture check:

```powershell
.\.gradle-local\gradle-8.14\bin\gradle.bat compileTestJava
```

After test classes compile, run `dev.sakusdev.armatureskin.fbx.AsciiFbxLoaderFixtureSanity` on the Gradle test runtime classpath. The helper builds an in-memory ASCII FBX fixture with:

- one `LimbNode` armature model
- one weighted triangle mesh
- UV coordinates that verify FBX V-coordinate flipping
- one negative binary-like fixture that must be rejected by the ASCII loader

