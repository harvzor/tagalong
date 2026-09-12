## ADDED Requirements

### Requirement: Metadata handling completes on files larger than the app memory

The engine's container-metadata stages (probing a file's location representation, and finalizing a cut output to retain a source QuickTime `©xyz` atom) SHALL complete using memory that does not grow with the size of the video file. A cut, or a probe of the picked file, SHALL NOT fail with memory exhaustion on any video the source storage can hold, including files larger than the application's heap limit and files larger than 2 GB. The preservation guarantees for `©xyz`, vendor tags, and `creation_time` SHALL hold unchanged at those sizes, and unsupported-layout handling SHALL still fail loudly rather than emit an output whose offsets cannot be trusted.

#### Scenario: Cut of a file larger than the app heap preserves location

- **WHEN** the location finalizer runs on a source and output whose file sizes exceed the test JVM's pinned heap budget
- **THEN** finalization completes successfully
- **AND** the output retains the source `moov/udta/©xyz` payload byte-for-byte
- **AND** chunk offsets and enclosing box sizes in the output remain valid (the media tracks are readable)

#### Scenario: Probing a file larger than the app heap reports its representation

- **WHEN** the location-representation inspector runs on a video file larger than the pinned heap budget
- **THEN** it returns the file's location representation without buffering the whole file into memory

#### Scenario: Files beyond 2 GB are not structurally excluded

- **WHEN** metadata handling runs on a video file larger than 2 GB
- **THEN** the operation succeeds or fails for a layout reason surfaced to the user
- **AND** it does not fail solely because the file exceeds the addressable range of a 32-bit file position
