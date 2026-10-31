# Repository workflow

- The user explicitly authorized ongoing Git management for ServerCore_2: after requested work is complete, run relevant checks, commit the intended changes, and push the current branch to its configured upstream. Do not stop to ask again for an ordinary commit/push within this scope.
- Current project: deemo777/minecraft_server, branch plugins. Inspect current branch/upstream before acting; do not silently switch to main or rewrite remote history.
- Do not force-push, reset user work, or commit unrelated changes. Preserve concurrent edits and report any blocking merge/authentication issue.
- Never stage .env files, authentication tokens, private SSH keys, live world/player data, plugin caches, or FAWE clipboard/history files. Use explicit paths; inspect staged content and sizes before committing.
- The local .env must remain on disk. A removal from Git tracking must be explained because other checkouts can remove the formerly tracked file on pull.
- Keep generated build directories and release binaries out of source commits. Build with Java 25, run Gradle build (including verifyLandRules and verifyEnhancementRules), and validate changed resource-pack JSON/PNG/font references as appropriate.
- Update the relevant docs and changelog with behavior changes. Report the pushed branch and commit, check results, and whether deployment/live-game validation remains outstanding.
- A push authorizes source publication only. Do not change the running OCI server, worlds, credentials, GitHub release assets or hosting without task-specific authorization.

# Item tooltip policy

- Do not add descriptive, instructional, drop-chance, attack-speed, or base-stat lore to physical items. Keep names, enhancement stars, and concise additional options only. The user explicitly allows a single concise attack-damage value on weapons; do not add attack speed or other base-stat explanations. GUI items may show necessary transaction values and ownership/status, without explanatory sentences. Keep detailed mechanics in documentation, not item hover text.

# Resource-pack release updates

- The user explicitly authorized ongoing updates of `servercore_pack.zip` on GitHub release tag `servercore` in `deemo777/minecraft_server`. After resource-pack changes, build and validate the full pack, replace that release asset, verify the downloadable ZIP against the local build, and report the current SHA-1 for server.properties. Do not ask for ordinary replacement approval again.
- This authorization covers the full resource-pack ZIP only; other release assets and live OCI settings still require task-specific authorization. Keep unrelated release files and release notes intact.
- Prefer uploading a temporary asset and verifying it before replacing the existing asset, to preserve the working download if upload fails. Never claim a release is updated if authentication or upload fails.

# Fixed mining rules

- Pickaxe fortune: none at +0..9, I at +10, III at +15, IV at +20..25; interpolate only +10..20. Never increase fortune beyond IV after +20.
- Connected ore mining is an absolute fixed limit: +0..20 mine only the direct block; +21/+22/+23/+24/+25 mine 2/3/4/5/6 blocks total, including the direct block. Do not smooth, randomize, or expand these quotas. Woodcutting has its own existing rules.
- Enhanced weapons must not qualify for native sword sweeping. Use only the plugin player-centred area attack and preserve the native sweep event cancellation as a fallback.

# Tool durability milestones

- Keep custom tool identity/model/PDC. Match diamond tool stats instead of replacing the item with a plain vanilla tool.
- Pickaxe/axe/hoe: +5 must match diamond durability 1561 and work speed 8. Keep maximum durability 1561 through +19; retain the existing netherite 2031 baseline from +20.
- Work tools: +5 no Unbreaking, +10 Unbreaking I equivalent, +15 Unbreaking III equivalent, +20 IV equivalent, +25 infinite. Implement per-point numeric wear probability rather than increasing the displayed durability or attaching enchantments/lore. Fishing rods/cans retain their own baseline maximum (64/25). Weapon durability is a separate existing rule.
