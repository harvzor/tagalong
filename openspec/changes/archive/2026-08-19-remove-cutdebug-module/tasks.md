## 1. Remove the module

- [x] 1.1 Delete the `cutdebug/` directory and all its contents
- [x] 1.2 Remove `include(":cutdebug")` from `settings.gradle.kts`

## 2. Verify the build

- [x] 2.1 Run `./gradlew.bat :app:assembleDebug` and confirm it succeeds with no reference to `:cutdebug`
- [x] 2.2 Confirm `./gradlew.bat :engine:assembleDebug` also succeeds (sanity check that the library plugin declaration in root `build.gradle.kts` still works for `:engine`)

## 3. Commit

- [x] 3.1 Stage and commit with message: `Remove :cutdebug bake-off harness (findings archived)`
