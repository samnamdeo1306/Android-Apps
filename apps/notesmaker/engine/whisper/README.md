# :engine:whisper

On-device speech recognition through [whisper.cpp](https://github.com/ggml-org/whisper.cpp), built from source by the NDK (ADR-24, ADR-32).

- `src/main/cpp/CMakeLists.txt` downloads the pinned release archive with `FetchContent`, verifies its SHA-256, and builds `ggml` and `whisper` as static libraries into `libnotesmaker_whisper.so` (16 KB page aligned).
- `src/main/cpp/jni_bridge.cpp` is a thin JNI layer: load a model, run `whisper_full` on one PCM window, copy segments out, and abort on request.
- Windowing, resume, boundary handling and error mapping live in Kotlin so they can be tested on the JVM.

## Tests

| Command | What it covers |
| --- | --- |
| `./gradlew :engine:whisper:testDebugUnitTest` | Segment mapping and marker filtering (JVM) |
| `./gradlew :engine:whisper:connectedDebugAndroidTest` | The native library loads, reports the pinned version, and fails safely without a model |

## Upgrading whisper.cpp

The version and checksum are two lines in `gradle/libs.versions.toml` (`whisperCpp`, `whisperCppSha256`). Compute the checksum from the tag rather than trusting a download. GitHub's tag archive is byte for byte `git archive | gzip -n`:

```sh
git clone --branch v1.9.6 --depth 1 https://github.com/ggml-org/whisper.cpp.git /tmp/whisper.cpp
cd /tmp/whisper.cpp
git archive --format=tar --prefix=whisper.cpp-1.9.6/ v1.9.6 | gzip -n | sha256sum
```

Compare it with `curl -sL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.6.tar.gz | sha256sum` when you can reach GitHub, update both catalog lines, and run the instrumented test above. A wrong hash fails the CMake configure step, so a tampered or changed archive never builds.

To step through the native code, add `-DNOTESMAKER_NATIVE_DEBUG=ON` to the CMake arguments in `build.gradle.kts`; the default is a Release build even for debug APKs, because an unoptimised whisper is too slow to be useful.
