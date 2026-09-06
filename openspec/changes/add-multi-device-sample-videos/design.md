## Context

See `proposal.md` for the motivation. The current repository has one canonical video under `fixtures/`, duplicated into each module's `androidTest/assets/` directory. Engine and app instrumentation helpers each hard-code `xiaomi-poco-x5.mp4`, while the emulator seeding script scans the repository directory independently.

The new `test-fixtures` capability must keep repository samples available to both Android test APKs, preserve the existing Xiaomi coverage, add the Pixel 10a sample, and allow future device samples to enter the matrix without per-file test-source edits.

## Goals / Non-Goals

**Goals:**

- Establish `sample-videos/` as the single source of truth for repository video samples.
- Package the same source directory into both Android instrumented-test APKs without checked-in duplicate copies.
- Discover supported samples deterministically and exercise each one in engine and app metadata tests.
- Keep each sample's filename visible in test diagnostics, temporary paths, generated outputs, and emulator-seeding logs.
- Make test isolation explicit so one sample cannot reuse another sample's cache, MediaStore row, or output.
- Keep the emulator seeding workflow aligned with the test corpus.

**Non-Goals:**

- Changing production cut behavior or metadata-preservation semantics.
- Changing the frozen `:cutdebug` module.
- Adding a runtime sample-video browser or shipping sample videos in the production APK.
- Defining device-specific expected metadata snapshots; each source remains the baseline for its own preservation assertions.

## Decisions

### Use one canonical Android-test asset source

Configure the `androidTest` asset source set in `:engine` and `:app` to include the repository's `sample-videos/` directory. Remove the checked-in duplicates under module-local `src/androidTest/assets/` so the test APKs are built from one corpus and cannot silently diverge.

**Alternative considered:** Keep a copy in each module and add a synchronization script. Rejected because it leaves two copies per sample, requires an extra synchronization step, and permits stale or mismatched assets.

### Discover sample videos from packaged assets

The instrumentation helpers will enumerate the packaged asset root, filter the supported video extensions already recognized by the emulator seeding workflow, and sort filenames deterministically. A new supported file in `sample-videos/` therefore enters both test matrices without adding a constant or test method.

**Alternative considered:** Maintain a Kotlin list of fixture names. Rejected because every new device sample would require test-source changes and the list could diverge from the repository directory.

### Run the existing contract once per sample and mode

The engine contract harness will retain its shared assertions but execute them independently for each discovered sample and for both cut modes. Source probes and hashes will be captured per sample, and output filenames will include both the sample stem and mode. The cut interval will be derived from the source duration so future samples do not need to match the current clips' exact lengths; a sample that cannot provide a valid cut interval will fail with its filename in the diagnostic.

**Alternative considered:** Add a separate test class for every device. Rejected because it scales test code with the fixture corpus and makes coverage easy to forget.

### Run app end-to-end coverage as a fixture loop

The app instrumentation test will use the same asset discovery model, seed one sample at a time, select it by its filename (or an unambiguous filename stem), and run the existing pick-to-cut-to-verify flow. Each iteration will return the app to a clean starting state and remove its MediaStore and cache artifacts before the next sample.

**Alternative considered:** Keep one JUnit test method per device. Rejected because dynamically added assets cannot create JUnit methods and future additions would still require source edits.

### Preserve stable sample identity

The full filename will be the stable identity used for asset lookup, cache materialization, output naming, picker matching, cleanup, and assertion messages. Sample names must be unique within `sample-videos/`; ambiguous duplicate display names are treated as an invalid corpus rather than allowing a test to select the wrong video.

### Keep emulator seeding aligned with the corpus

The existing seeding script will retain its command name and extension filtering but use `sample-videos/` as its default source directory. Its optional directory override remains available for local experimentation. Active documentation will describe the single step for adding a new sample.

## Risks / Trade-offs

- **Larger instrumentation APKs as samples are added** → Keep samples limited to representative clips, and do not package the directory into production variants.
- **Gradle asset-source configuration could accidentally omit the root directory** → Verify the built test APK assets and make asset discovery fail with a clear missing-corpus message rather than silently running zero tests.
- **A file picker may show multiple samples or vary its ordering** → Always match the current sample's unique filename/stem and reject ambiguous selection instead of relying on the first item.
- **Device samples may lack required metadata or be too short for a cut** → Fail with the sample identity and the unmet contract condition; do not silently skip it or substitute another sample.
- **Dynamic discovery can make runtime longer as the corpus grows** → Keep the matrix deterministic and isolated, and report per-sample progress so the additional cost is visible.
- **Renaming `fixtures/` may affect local scripts or documentation outside the active references** → Update active references and verify repository-wide references; leave archived OpenSpec records unchanged because they document historical work.

## Migration Plan

1. Move the existing canonical Xiaomi sample and the new Pixel 10a sample into `sample-videos/`.
2. Point both Android-test source sets and the emulator seeding script at the new directory.
3. Remove the two module-local duplicate asset files.
4. Replace single-fixture constants with deterministic asset discovery and per-sample isolation.
5. Run the engine and app instrumentation suites against the connected device and verify both samples are reported.
6. If the migration must be rolled back, restore the old directory name, restore the module-local assets, and revert the source-set and discovery changes; no production data migration is involved.
