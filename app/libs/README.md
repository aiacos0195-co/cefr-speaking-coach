# app/libs

Drop `sherpa-onnx-<version>.aar` here (from
https://github.com/k2-fsa/sherpa-onnx/releases) and uncomment the
`fileTree(...)` line in `app/build.gradle.kts`.

Do not commit the AAR: it is ~20 MB per ABI. Add to `.gitignore`:

    app/libs/*.aar
