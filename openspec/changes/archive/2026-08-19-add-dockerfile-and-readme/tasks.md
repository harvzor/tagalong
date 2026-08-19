## 1. Signing config in app/build.gradle.kts

- [x] 1.1 Add `signingConfigs` block reading `releaseKeystorePath`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword` from Gradle project properties (same pattern as bouncer)
- [x] 1.2 Add `buildTypes.release { signingConfig = signingConfigs.findByName("release") }` block
- [x] 1.3 Replace hardcoded `versionCode = 1` / `versionName = "0.1.0"` with `findProperty("appVersionName")`-driven derivation (same formula as bouncer; fallback `0.0.0-dev` / `1`)

## 2. .dockerignore

- [x] 2.1 Create `.dockerignore` at project root — exclude `local.properties`, `.gradle/`, `build/`, `*/build/`, `.idea/`, `.kotlin/`, `*.iml`, `.git/`, `out/`, `**/src/androidTest/`

## 3. Dockerfile

- [x] 3.1 Add Stage 1 (`builder`): base image `eclipse-temurin:25-jdk-noble`, install `unzip wget`
- [x] 3.2 Add Android SDK cmdline-tools layer (pinned version, same as bouncer)
- [x] 3.3 Add SDK packages layer: accept licences, install `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`
- [x] 3.4 Add build-config layer: copy `gradlew`, `gradlew.bat`, `gradle.properties`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `app/build.gradle.kts`, `engine/build.gradle.kts`; write stub `AndroidManifest.xml` for both `:app` and `:engine`; `chmod +x gradlew`
- [x] 3.5 Add dependency-warmup layer: `RUN ./gradlew dependencies --no-daemon`
- [x] 3.6 Add source layer: `COPY app/ app/` and `COPY engine/ engine/`
- [x] 3.7 Add assemble step with `BUILD_TYPE` and `VERSION` args, BuildKit secret mounts for keystore and credentials, APK copy to `/out/` (named `tagalong-<VERSION>.apk` when VERSION is set)
- [x] 3.8 Add Stage 2 (`export`): `FROM scratch`, `COPY --from=builder /out/ /`

## 4. Verify debug build

- [x] 4.1 Run `docker build --output=out .` and confirm an APK appears in `out/`
- [x] 4.2 Confirm no container is left running after the build

## 5. README.md

- [x] 5.1 Write `README.md` with: app description, why `ACTION_OPEN_DOCUMENT` is used, and a Building section documenting the debug and signed release Docker commands (same structure as bouncer's Building section)

## 6. Commit

- [x] 6.1 Stage and commit: `Add Dockerfile, .dockerignore, signing config, and README`
