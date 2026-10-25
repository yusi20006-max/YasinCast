# YasinCast Phased Roadmap

**Tracking issue:** [Issue #1](https://github.com/yusi20006-max/YasinCast/issues/1)  
**Delivery principle:** scoped issues, reviewable PRs, relevant tests, and verifiable CI evidence.

## Phase 0 — Architecture and repository baseline

- [x] Establish repository and issue backlog.
- [x] Document architecture, boundaries, constraints, and acceptance criteria.
- [ ] Review and merge the architecture documentation PR.

**Exit gate:** documentation is merged and scope is agreed before implementation.

## Phase 1 — Native Android build foundation

**Issue:** [#2](https://github.com/yusi20006-max/YasinCast/issues/2)

- Inspect the available build environment and Android/Gradle toolchain before choosing versions.
- Create Kotlin Android project and Gradle Wrapper.
- Establish application ID, minimum/target SDK policy, basic app entry point, and Persian RTL baseline.
- Add reproducible debug build and GitHub Actions build workflow.
- Document local build/install commands and environment limitations.

**Exit gate:** clean checkout builds a debug APK reproducibly and CI succeeds. No player feature is claimed yet.

## Phase 2 — Core playback and local library

- [ ] [Issue #3 — Media3 playback service and system media controls](https://github.com/yusi20006-max/YasinCast/issues/3)
- [ ] [Issue #4 — browse and import local audio files](https://github.com/yusi20006-max/YasinCast/issues/4)

Implement playback through Media3/MediaSessionService and SAF-based user-authorized import. Validate representative files rather than assuming extensions guarantee support.

**Exit gate:** import and playback work together, including notification/lock-screen controls and recoverable errors.

## Phase 3 — Persistence and Persian UI

- [ ] [Issue #5 — progress, played status, and history](https://github.com/yusi20006-max/YasinCast/issues/5)
- [ ] [Issue #6 — Persian RTL library and player screens](https://github.com/yusi20006-max/YasinCast/issues/6)

Add Room-backed resume/history and build library, now-playing, and settings screens. Keep UI state lifecycle-aware and separate from service ownership.

**Exit gate:** main listening workflow works end-to-end and state survives app/process restarts.

## Phase 4 — Listening quality-of-life features

**Issue:** [#8](https://github.com/yusi20006-max/YasinCast/issues/8)

Add sleep timer, position bookmarks with optional notes, and local library search.

**Exit gate:** features persist appropriately and tests cover timer behavior during background playback.

## Phase 5 — Physical-device verification and MVP acceptance

**Issue:** [#7](https://github.com/yusi20006-max/YasinCast/issues/7)

Test on the target phone and document device/OS details and evidence. The matrix includes installation/relaunch, tested formats, background/screen-off playback, notification and lock-screen controls, headphones/Bluetooth, audio focus, seeking/speed, persistence, airplane mode, missing/corrupt files, and RTL rendering.

**Exit gate:** failures are fixed or clearly recorded as blockers; no test is marked passed without evidence. Publish a debug APK artifact/release only after build and distribution details are checked.

## Phase 6 — Online expansion (not part of MVP)

Begin only after Phase 5 passes and a separate issue set is approved. Candidate work:

- API and deployment architecture, with provider availability and total cost reviewed first.
- Remote audio object storage and access control.
- Streaming and optional offline downloads.
- RSS import/publishing and public episode pages.
- Accounts and cross-device synchronization.
- Backup/restore, operational monitoring, and privacy policy.

This phase requires a threat model, data-retention policy, authentication/authorization design, cost estimate, and hosting-accessibility review for expected regions. It must not delay or complicate the offline MVP.

## Delivery rules

1. Work only in yusi20006-max/YasinCast for YasinCast issues.
2. Inspect repository and issue acceptance criteria before coding.
3. Use a dedicated branch and PR for scoped changes.
4. Run relevant tests/builds and inspect CI before merge.
5. Never report an unexecuted test as passed or a merge as complete without tool evidence.
6. Do not make unrelated Termux/global environment changes as part of app implementation.
