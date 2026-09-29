## Purpose

Governs the release build when Java/Kotlin code shrinking is active: that the transformation which ships has actually been executed, that it changes nothing observable, and that failures it produces can still be read.

## ADDED Requirements

### Requirement: The shipped transformation is executed by the automated suites

The project SHALL provide a repeatable way to run the existing instrumented suites against a build whose shrinking behaviour is the same as the shipped release build's. The automated suites SHALL NOT be required to describe a configuration they cannot reach.

The verification path SHALL NOT introduce a second shrinking configuration maintained alongside the shipped one.

#### Scenario: Shrunk build runs the suites

- **WHEN** a developer runs the documented verification command for shrunk release code
- **THEN** the instrumented suites execute against a build that has been through the same shrinking as ships
- **AND** the result is a pass or a failure, not an unavailable task

#### Scenario: The two configurations cannot drift

- **WHEN** a shrinking-related setting is changed for release
- **THEN** the verification build uses the same value, without a second place to remember it

### Requirement: Shrinking does not change observable behaviour

A cut performed by a shrunk release build SHALL produce the same set of surviving metadata tags as the same cut of the same source performed by an unshrunk build of the same commit. Shrinking SHALL NOT alter which tags are preserved.

#### Scenario: Same source, shrunk and unshrunk

- **WHEN** the same source video is cut by a shrunk and an unshrunk build of the same commit
- **THEN** the set of tags surviving to the output is identical in both

#### Scenario: Shrinking removes something it should not have

- **WHEN** a tag that survived the unshrunk build is absent from the shrunk build's output
- **THEN** the change is treated as a defect to fix or a signal to stop, not a tolerated difference

### Requirement: Native-reachable entry points survive shrinking

A shrunk release build SHALL complete a cut end to end on a device. Code reachable only from native code by name SHALL survive shrinking, and this SHALL be demonstrated by execution rather than inferred from keep rules.

#### Scenario: Cut completes on a shrunk build

- **WHEN** a cut is performed on a device by a shrunk, signed release build
- **THEN** the cut completes without a native linkage failure

#### Scenario: Keep rules are not accepted as proof

- **WHEN** keep rules are added and no device run is performed
- **THEN** verification is not complete

### Requirement: Shrinking is enabled against a recorded measurement

Code shrinking SHALL NOT be enabled for shipped releases until the measured size saving, per published artifact, has been recorded against the unshrunk baseline for the same commit. A saving below the recorded threshold SHALL result in shrinking being left off, with the measurement kept.

#### Scenario: Measurement recorded before enabling

- **WHEN** shrinking is proposed for a shipped release
- **THEN** a per-artifact before-and-after size figure exists for the same commit

#### Scenario: Saving does not justify the risk

- **WHEN** the measured saving falls below the recorded threshold
- **THEN** shrinking remains disabled
- **AND** the measurement that decided it is kept where a later reader can find it

### Requirement: Failures in a shrunk release remain diagnosable

Each published release SHALL retain the mapping needed to translate an obfuscated stack trace back to source, retrievable after publication.

#### Scenario: Field stack trace can be read

- **WHEN** an obfuscated stack trace from a published release is examined later
- **THEN** the mapping for that release is available and resolves it to source names

#### Scenario: Mapping not retained blocks the release

- **WHEN** a shrunk release would publish without its mapping being retained
- **THEN** the release is treated as incomplete
