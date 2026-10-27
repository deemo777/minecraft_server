# Repository workflow

- The user explicitly authorized ongoing Git management for ServerCore_2: after requested work is complete, run relevant checks, commit the intended changes, and push the current branch to its configured upstream. Do not stop to ask again for an ordinary commit/push within this scope.
- Current project: deemo777/minecraft_server, branch plugins. Inspect current branch/upstream before acting; do not silently switch to main or rewrite remote history.
- Do not force-push, reset user work, or commit unrelated changes. Preserve concurrent edits and report any blocking merge/authentication issue.
- Never stage .env files, authentication tokens, private SSH keys, live world/player data, plugin caches, or FAWE clipboard/history files. Use explicit paths; inspect staged content and sizes before committing.
- The local .env must remain on disk. A removal from Git tracking must be explained because other checkouts can remove the formerly tracked file on pull.
- Keep generated build directories and release binaries out of source commits. Build with Java 25, run Gradle build (including verifyLandRules and verifyEnhancementRules), and validate changed resource-pack JSON/PNG/font references as appropriate.
- Update the relevant docs and changelog with behavior changes. Report the pushed branch and commit, check results, and whether deployment/live-game validation remains outstanding.
- A push authorizes source publication only. Do not change the running OCI server, worlds, credentials, GitHub release assets or hosting without task-specific authorization.

# Resource-pack release updates

- The user explicitly authorized ongoing updates of `servercore_pack.zip` on GitHub release tag `servercore` in `deemo777/minecraft_server`. After resource-pack changes, build and validate the full pack, replace that release asset, verify the downloadable ZIP against the local build, and report the current SHA-1 for server.properties. Do not ask for ordinary replacement approval again.
- This authorization covers the full resource-pack ZIP only; other release assets and live OCI settings still require task-specific authorization. Keep unrelated release files and release notes intact.
- Prefer uploading a temporary asset and verifying it before replacing the existing asset, to preserve the working download if upload fails. Never claim a release is updated if authentication or upload fails.
