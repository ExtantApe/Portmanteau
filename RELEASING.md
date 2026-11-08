# Releasing

Portmanteau ships as a jar on GitHub Releases. It isn't on Modrinth, Hangar or SpigotMC yet; those come later, after the owner's own review.

1. Run the unit tests and the live tests (`./gradlew build`, then `test/live/all.sh`). The crash and dupe suites must all pass before a release.
2. Set the version in `build.gradle.kts`, and add a section for it at the top of `CHANGELOG.md`, headed `## x.y.z`.
3. Commit as `Release x.y.z`, tag `vx.y.z` and push the tag.
4. The Release workflow checks that the tag matches the version, runs the leak check and the build, and attaches `Portmanteau-x.y.z.jar` to a GitHub Release with that changelog section as its notes.

Versions stay 0.x until Portmanteau has seen real use. Release notes never name people, servers or players.
