# Repository conventions

## Independent sample projects

Each `apps/<sample-name>/` directory is a complete Android Studio project, with its own Gradle settings, wrapper, dependency catalog, application ID, modules, and build outputs. This supports different Android architectures and toolchains without forcing every sample to upgrade together.

Use lowercase directory names with hyphens when needed. Give applications distinct package/application IDs. Do not create nested Git repositories: all samples belong to this repository and share its version history.

## Ownership

| Location | Contents |
| --- | --- |
| Root `README.md` | Sample catalog, repository layout, navigation |
| Root `docs/` | Conventions applying to all samples |
| `.github/workflows/<sample>.yml` | CI for that sample, with correct working directory and artifact paths |
| `apps/<sample>/README.md` | Purpose, setup, build/test commands, module map, limitations |
| `apps/<sample>/docs/` | Product requirements, architecture, decisions, tests, operations, roadmap |
| `apps/<sample>/CHANGELOG.md` | Release notes for that application |
| `apps/<sample>/core/` | That sample's domain/data modules when its architecture warrants them |

`core` inside Daybreak describes layers within Daybreak; it is not a shared API for future applications. Avoid cross-sample project dependencies. If duplication becomes a real maintenance problem, propose a shared library with a clear owner, version/build contract, and tests before extracting it.

## Adding a sample

1. Create `apps/<sample>/` with its independent Gradle project. Avoid scaffold modules or a `core` layer unless the app needs them.
2. Keep signing keys, SDK paths, caches, and generated build outputs out of version control. Root ignore rules apply throughout the repository.
3. Document the app's purpose, supported SDK/JDK versions, startup, and meaningful validation commands.
4. Include requirements, design decisions with reasons, acceptance tests, known limitations, and release notes at a depth proportional to the sample.
5. Add a sample-specific workflow under `.github/workflows/`. Scope its path filters to the sample and its workflow. Set shell working directory to the sample; artifact paths remain relative to the repository root.
6. Add the sample to the root README catalog. Validate commands from the exact working directory documented.

## Cloud environment

Toolchains and dependency caches may be shared outside the checkout under `/workspace/toolchains`. Application source, Gradle projects, and documents remain inside their sample. Use the existing task checkout; another worktree is unnecessary unless explicitly requested.

The current environment installation script validates Daybreak from `/workspace/Android-Apps/apps/daybreak`. Add prerequisites and validation for future samples deliberately, preserving existing workflows. A successful build does not establish on-device alarm delivery or other hardware-dependent behavior.
