## Purpose

Defines the project's open-source license and the obligations that flow from it, ensuring every distributed build carries the correct legal notice.

## Requirements

### Requirement: Repo carries a GPL v3 license file
The repo root SHALL contain a `LICENSE` file with the full, unmodified text of the GNU General Public License version 3 (GPL-3.0-or-later).

#### Scenario: LICENSE file present at repo root
- **WHEN** a user checks out the repository
- **THEN** a `LICENSE` file SHALL exist at the repo root containing the GPL v3 full text

### Requirement: License is GPL-compatible
Any dependency whose license requires copyleft MUST be GPL-compatible. The `:engine` module's dependency on `ffmpeg-kit-full-gpl` (GPL v3) is the binding constraint; any future engine replacement MUST be evaluated for license compatibility before the project license can be changed.

#### Scenario: Evaluating a new engine dependency
- **WHEN** a candidate replacement for `ffmpeg-kit-full-gpl` is considered
- **THEN** its license MUST be confirmed GPL-compatible (or the project license updated accordingly) before the dependency is adopted
