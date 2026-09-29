## MODIFIED Requirements

### Requirement: Version stamping via build argument

The `Dockerfile` SHALL accept a `VERSION` build argument. When supplied, every APK produced by the build SHALL be named `tagalong-<VERSION>-<identifier>.apk`, where `<identifier>` distinguishes the architecture the APK targets and is `universal` for the combined APK. When absent, the default APK filenames SHALL be used.

No two produced APKs SHALL be written to the same destination path.

#### Scenario: Named release APK when VERSION is set

- **WHEN** `docker build --build-arg VERSION=1.0.0 --output=out .` is run
- **THEN** every APK the build produced is present in `./out/`, named `tagalong-1.0.0-<identifier>.apk`
- **AND** each such file has a distinct name

#### Scenario: No build output is lost to a shared destination name

- **WHEN** the build produces more than one APK and `VERSION` is set
- **THEN** every produced APK is present in `./out/`
- **AND** no produced APK has overwritten another

#### Scenario: Default filenames when VERSION is absent

- **WHEN** `docker build --output=out .` is run without a `VERSION` build argument
- **THEN** every produced APK is present in `./out/` under its build-default filename

## ADDED Requirements

### Requirement: Output enumeration is scoped to the shipped build

The step that collects APKs for export SHALL select only outputs of the build variant being exported. It SHALL NOT collect test-build outputs, whatever exists in the build directory.

#### Scenario: Instrumented-test APKs are never exported

- **WHEN** the export step runs and `app/build/outputs/apk/androidTest/` exists on disk
- **THEN** no APK from that directory appears in `./out/`

#### Scenario: Every shipped APK is collected

- **WHEN** the export step runs after a successful `assembleRelease`
- **THEN** each release APK the build produced appears in `./out/`
