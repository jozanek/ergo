# Ergo PR review playbook

An agent-agnostic, repo-specific playbook for reviewing changes to the Ergo node. Usable by any coding agent (Claude Code, GitHub Copilot, Cursor, Aider, ...) or a human reviewer. Nothing in this directory is tool-specific.

## How to use it

1. **Fetch the diff** (see below).
2. For each changed file, look up the matching focus file(s) in the [routing table](#routing-table) and apply their checks.
3. Emit findings in the [output format](#output-format).

### Quick start

- **GitHub Copilot code review**: the skill at [.github/skills/code-review/SKILL.md](../../.github/skills/code-review/SKILL.md) applies this playbook inside a why-first review and uses its own output rules instead of the [output format](#output-format) below.
- **Any other agent / human**: paste this file into the agent's context and tell it to follow the steps; or read each focus file directly.

## Fetch the diff

With a PR number:

```bash
gh pr view <N>  --json title,body,baseRefName,files,additions,deletions
gh pr diff <N>
```

Without a PR number (review the current local branch against `master`):

```bash
git log origin/master..HEAD --oneline
git diff origin/master...HEAD
```

For longer-running branches also skim:

```bash
git diff --stat origin/master...HEAD
```

## Routing table

Map each changed path to the focus file(s) whose checks apply. A file can match more than one row — apply all of them. Unmatched paths still get the [checklist](checklist.md).

| Path glob | Focus files |
| --- | --- |
| `src/main/scala/org/ergoplatform/http/api/**` | [architecture](architecture.md), [checklist](checklist.md) |
| `src/main/resources/api/openapi.yaml` | [architecture](architecture.md) |
| `src/main/scala/org/ergoplatform/nodeView/ErgoNodeViewHolder*.scala` | [architecture](architecture.md), [concurrency](concurrency.md), [protocol](protocol.md) |
| `src/main/scala/org/ergoplatform/nodeView/mempool/**` | [performance](performance.md), [concurrency](concurrency.md) |
| `src/main/scala/org/ergoplatform/nodeView/state/**` | [architecture](architecture.md), [protocol](protocol.md), [performance](performance.md) |
| `src/main/scala/org/ergoplatform/nodeView/wallet/**` | [architecture](architecture.md), [concurrency](concurrency.md) |
| `src/main/scala/org/ergoplatform/network/**` | [concurrency](concurrency.md), [performance](performance.md), [protocol](protocol.md) |
| `src/main/scala/scorex/core/network/**` | [concurrency](concurrency.md), [performance](performance.md), [protocol](protocol.md) |
| `src/main/scala/org/ergoplatform/mining/**` | [performance](performance.md), [protocol](protocol.md) |
| `src/main/scala/org/ergoplatform/nodeView/history/**` (other than `extra/`) | [protocol](protocol.md), [architecture](architecture.md), [performance](performance.md) |
| `src/main/scala/org/ergoplatform/nodeView/history/extra/**` | [performance](performance.md), [architecture](architecture.md) |
| `ergo-core/**/*Serializer.scala` | [protocol](protocol.md) |
| `ergo-core/**/settings/ValidationRules.scala`, `ergo-core/**/validation/**` | [protocol](protocol.md) |
| `ergo-core/**/mining/**` (Autolykos) | [protocol](protocol.md) |
| `ergo-core/**/popow/**`, `**/PopowProcessor.scala` (NiPoPoW) | [protocol](protocol.md) |
| `ergo-core/**/reemission/**` | [protocol](protocol.md) |
| `ergo-core/**` (other) | [architecture](architecture.md), [protocol](protocol.md) |
| `ergo-wallet/**` | [architecture](architecture.md), [protocol](protocol.md) |
| `avldb/**` | [performance](performance.md), [protocol](protocol.md) |
| `src/main/resources/application.conf` | [architecture](architecture.md), [checklist](checklist.md) |
| `src/main/resources/*.conf` (mainnet/testnet/devnet) | [protocol](protocol.md) |
| `build.sbt`, `project/**` | [architecture](architecture.md) |
| `**/src/test/**`, `src/it/**`, `src/it2/**` | [testing](testing.md) |
| `.github/workflows/**` | [checklist](checklist.md) |

**Skip** (never review): `target/**`, `papers/**`, `*.pdf`, `*.gz`, `mainnet/**`, `devnet/**`, `*-local*.conf`.

## Output format

One finding per bullet. Group by focus area. Order severities highest first within each group.

```
SEVERITY | path/to/file.scala:LINE | finding | suggestion
```

Severity ladder:

- `BLOCKER` — must fix before merge. Default for anything flagged by [protocol.md](protocol.md) (consensus-critical) until the reviewer can prove the change is wire-compatible / behind an activation gate.
- `MAJOR` — should fix before merge: correctness bug, perf regression on a hot path, race condition, missing test for non-trivial logic.
- `MINOR` — fix recommended: style violation, missing OpenAPI update, redundant allocation off the hot path.
- `NIT` — optional polish.

End the review with a one-sentence verdict: `LGTM`, `Approve with changes`, or `Request changes` plus the count by severity.

## Focus files

- [architecture.md](architecture.md) — module boundaries, actor wiring, sync-mode coverage, HTTP API conventions
- [performance.md](performance.md) — LevelDB/AVL+ hot paths, allocation patterns, mempool/cache bounds, mailbox load
- [concurrency.md](concurrency.md) — Akka blocking, `Future`/EC pitfalls, actor protocol changes
- [testing.md](testing.md) — test helpers, generators, `it:test` gotchas
- [protocol.md](protocol.md) — consensus-critical: serializers, validation rules, PoW, NiPoPoW, state transitions
- [checklist.md](checklist.md) — SWE hygiene: style, OpenAPI, logging, file size, config back-compat

## Extending the playbook

To add a focus area:

1. Create `docs/review/<focus>.md`. Follow the existing structure: short purpose paragraph, then a flat list of specific red flags with file/line examples where possible. Generic advice ("watch for race conditions") doesn't belong here — it has to be specific to *this* codebase.
2. Add a row (or rows) to the [routing table](#routing-table) mapping the relevant path globs.
3. Add a bullet to the [Focus files](#focus-files) list.
4. No code or tool config changes needed.

To extend an existing focus file: add new red-flag bullets directly. Prefer specific patterns (file paths, function names, anti-patterns observed in real PRs) over generic principles.
