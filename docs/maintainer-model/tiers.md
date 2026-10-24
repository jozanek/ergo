# Risk tiers and hard-blocks

The tier comes from **the change**, not the author. Authors change only which gates apply
(streams.md, stream 2). A bot's output never fills a human slot: tiers count maintainer
approvals only.

## Tiers

| Tier | Who must approve | What the approver must do | Applies to |
|---|---|---|---|
| **T0 none** | no approval required; a maintainer still clicks merge | read the packet and evidence, not the diff | docs-only: `*.md`, `papers/**`, `docs/**`, except AGENTS.md, REVIEW.md, SECURITY.md, CONTRIBUTING.md, `docs/adr/**`, `docs/maintainer-model/**` |
| **T1 one maintainer** | either maintainer (CODEOWNERS) | read the full diff, starting at the packet's 5 places; read the evidence | everything else, including tests, build, configs, `src/main/**` |
| **T2 both maintainers** | kushti **and** jozanek, on the same head commit | both do the T1 read; second reviewer starts from the first one's notes | any change that trips a T2 flag (below) |

A maintainer's own PR always needs the other maintainer: GitHub does not let authors approve
their own PR. For T2, a maintainer author counts as one of the two, so the other maintainer's
approval is enough. On an agent-written PR, the maintainer who directed the agent is accountable
as its author.

"Everything is top tier" is honoured by T1 being a real read for all production code. There is
no tier where code merges unread.

## T2 flags: when the second maintainer is pulled in

You chose "one human, both on flag". The flag comes from three sources, in this order:

1. **Path and content rules (deterministic).** A narrow list where a mistake splits the chain,
   corrupts storage, leaks keys, or exposes CI secrets. Listed below and in
   `drafts/tier-rules.yml`. A path rule cannot be removed by a label; changing the list is
   itself a T2 change.
2. **Classifier (agent).** Answers "does this change what nodes accept, on-disk formats, P2P
   wire format, keys or auth?" A yes must cite a line; a yes without a line is dropped.
3. **First reviewer.** Applies `T2-both` when unsure. Rule of thumb from the article: if you
   cannot say what the checks would miss, ask for the second review.

Measured cost of rule 1, recomputed on 2026-10-22: **27 of 111** PRs merged from 2026-01-21
would have tripped it (24%). The count covers PRs into `master`, `v*` and `weak-blocks`,
includes squash merges, and excludes release PRs. In the last 90 days it was 17 of 76 (22%),
about 6 a month. By category: consensus 17, supply chain 6, wire format 3, storage format 2,
keys and auth 2 (a PR can count in several). 4 of the 27 only touched CI workflows, and 1 only
touched `testnet.conf`. The first estimate (19 of 68) counted only "Merge pull request" commits
on `master` in a shallow clone, so it missed squash merges, `weak-blocks` and unreleased
`v6.0.9` work. If this is too much work for the maintainers, the options, in order, are: move
`ci.yml` to T1 once actions are SHA-pinned and snapshot publishing has its own workflow; then
move testnet-only config to T1.

Where this departs from your answer: rule 1 makes some flags automatic rather than waiting for a
model or reviewer to notice. The reason is that these paths are where "what nodes accept"
lives, and a deterministic rule cannot be argued with by injected text or missed on a busy day.

### T2 path and content rules (initial)

Consensus and validity:
- `ergo-core/src/main/scala/org/ergoplatform/settings/{ValidationRules,ErgoValidationSettings*,Parameters,LaunchParameters,VotingSettings,ChainSettings*,Constants,ReemissionSettings}.scala`
- `ergo-core/src/main/scala/org/ergoplatform/mining/Autolykos*.scala`, `mining/difficulty/**`
- `ergo-core/src/main/scala/org/ergoplatform/modifiers/**` (headers, block sections, NiPoPoW, transactions)
- `ergo-core/src/main/scala/org/ergoplatform/nodeView/state/**`, `ergo-core/.../nodeView/history/storage/modifierprocessors/**`
- `**/reemission/**`
- `src/main/scala/org/ergoplatform/nodeView/state/{UtxoState*,DigestState,ErgoState*}.scala`
- `src/main/scala/org/ergoplatform/nodeView/history/storage/modifierprocessors/{HeadersProcessor,FullBlock*,PopowProcessor}.scala`
- `src/main/resources/{mainnet,testnet}.conf`
- `build.sbt` lines matching `sigmaStateVersion|sigma-state|scrypto` (content rule)

P2P wire format:
- `ergo-core/src/main/scala/org/ergoplatform/network/{Version,Handshake*,*PeerFeature*}.scala`, `network/message/**`
- `src/main/scala/org/ergoplatform/network/message/**`

Storage formats and migrations:
- `avldb/src/main/**`
- `**/*Serializer.scala`
- `ExtraIndexer.scala` lines matching `NewestVersion` (content rule); any schema/version constant

Keys and auth:
- `ergo-wallet/src/main/scala/org/ergoplatform/wallet/{secrets,mnemonic,crypto,interpreter}/**`
- `src/main/scala/scorex/core/api/http/**`, `src/main/scala/org/ergoplatform/http/ErgoHttpService.scala` (API key, CORS)

Supply chain and governance:
- `.github/workflows/{ci,release,release-binaries,docker-publish}.yml` (they hold secrets)
- `.github/CODEOWNERS`, `.github/tier-rules.yml`, `REVIEW.md`, `AGENTS.md`, `SECURITY.md`, `docs/adr/**`
  (the dispatcher reads `tier-rules.yml` from the base branch only, so a PR cannot loosen its own tier)
- `do-release.sh`, `ci/**`, `Dockerfile`, `ergo-installer.sh`, `project/**`

**What the path rules miss:** consensus-relevant logic outside these files (mempool validation
drifting from block validation, sync logic that makes a node pick a different chain, a default in
`application.conf` that changes behaviour). The classifier, REVIEW.md and the T1 read cover these.
Path rules also over-flag pure refactors in listed files; that cost is accepted.

### T1 routing defaults (not a hard-block)

- P2P/sync (`src/main/scala/scorex/core/network/**`, `src/main/scala/org/ergoplatform/network/**`,
  `ergo-core/.../network/**`, `ToDownloadProcessor`): jozanek, who keeps deep review of these.
- Consensus core, storage formats, releases: kushti first.
- Everything else: whoever has the shorter queue.

## Trusted authors (lighter path, earned)

Today: maintainers only. An external author becomes trusted by decision of both maintainers
after, for example: 10+ merged non-trivial PRs over 8+ weeks, no attributed regressions, no
gate waivers needed in the last month, findings answered within the round cap. Trusted means:
gates G2/G6/G7 relaxed and AI review on open without a trigger. It never removes CODEOWNERS
approval or a T2 flag. Trust is revoked by either maintainer after a regression or a policy
breach.

## Enforcement (CODEOWNERS and rulesets, not a model)

Repository rulesets on `master`, the release branches (`v*`) and the feature branches that take
contributions (today `weak-blocks`; open-questions.md K5). An admin or owner applies them;
jozanek has write access.

| Setting | Value | Why |
|---|---|---|
| Require a pull request | on, no direct pushes | stops unreviewed merges; fixes and merges from base also go through PRs, for both maintainers (open-questions.md K1) |
| Required approvals | 0, plus **Require review from Code Owners** | CODEOWNERS owns everything but docs, so code needs one maintainer and docs need none (0 approvals with code-owner review is not verified; test it on the fork first, open-questions.md A9) |
| Dismiss stale approvals on push; require approval of the most recent push | on | approval covers one commit; if a maintainer pushes to someone's PR, that maintainer's approval no longer counts, so the other maintainer approves |
| Require conversation resolution | on | every finding is fixed or answered |
| Required status checks | `test_wallet`, `test_core`, `test_node`, `ergo-tier` (pinned to the dispatcher's GitHub App) | IT stays non-required until its flake rate is under 5%; no scalafmt check for now |
| Block force pushes and deletion | on | deletion auto-closed approved PRs before |
| Bypass list | empty: no bypass, also in an emergency (open-questions.md K2) | with two code owners and no backup approver, each maintainer's own PR waits for the other; the digest still lists any PR merged without the required approvals |
| Merge queue | later (rollout phase 3) | stale-base breakage is handled by G4 for now |

`ergo-tier` is a deterministic check posted by the dispatcher. It fails when: a T2 flag is set
and the head commit lacks approvals from both maintainers (a maintainer author counts as one); an
Important AI finding is unresolved; or any bot account has an `APPROVED` review on the PR. It
cannot approve anything; it can only block.

Actions settings: workflows default to read-only `GITHUB_TOKEN`; fork PR workflows from
first-time contributors need approval; no `pull_request_target` workflow checks out PR code.

**What enforcement misses:** a maintainer who approves without reading. That is what the packet's
"what the checks would miss" line and the T2 second read are for; the guardrail metrics
(rollout.md) show whether it happens.
