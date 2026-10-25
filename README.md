# YasinCast

YasinCast is a Persian-first native Android podcast and audio library app. The first milestone is an offline-first local audio player.

## Project documentation

- [Architecture and technical decisions](docs/architecture.md)
- [Phased roadmap and exit gates](docs/roadmap.md)
- [Issue #1: architecture and roadmap](https://github.com/yusi20006-max/YasinCast/issues/1)

## Current scope

The MVP targets Kotlin, Jetpack Compose, AndroidX Media3, Storage Access Framework (SAF), Room, and DataStore. Dependency versions will be selected after inspecting the available Android/Gradle toolchain; they are not pinned before that inspection.

The offline MVP comes first. Online hosting, accounts, RSS publishing, and synchronization are deferred until the offline application passes physical-device verification.

## Delivery status

Architecture documentation is under review. No Android app implementation or physical-device test is claimed complete yet.
