# Feedback loops

Recurring problems are fixed at the source. Prefer an artifact the toolchain enforces (lint
rule, test, CI check, gate) over prose that will rot (REVIEW.md, CONTRIBUTING.md). Prose is the
fallback when no tool can check the rule.

## Loop 1: a repeated comment becomes a rule

**Collect (automatic).** The dispatcher logs every maintainer review comment and every AI
finding with its outcome (dispatcher.md, "Noise measurement").

**Cluster (weekly, small tier, in your agent-tuning time).** Group the week's maintainer
comments and `wrong`/`intended` AI outcomes by theme. Output: themes seen 3 or more times in
30 days, each with the PRs where it occurred.

**Choose the artifact (you decide), in this order of preference:**

| The comment is about... | Artifact | Example from this repo |
|---|---|---|
| something a regex or AST can see | scalastyle/scalafix rule or CI grep | conflict markers or stray merge text; `${{ github.event.* }}` in `run:` |
| behaviour | a test in `src/test` (fits the AGENTS.md test-only rule) | version-ledger test (below) |
| PR shape | a gate in `tier-rules.yml` | stale-base and overlap gates |
| missing information | a PR/issue template field | "downgrade-safe?" |
| a judgement only a reader can make | one REVIEW.md line citing the PR | "count per-peer limits per peer, not per connection" |
| a decision | an ADR | "only released schema versions count" |
| a false positive from the AI | a REVIEW.md "do not flag" line, or a prompt change scored on evals | |

**Keep the prose short.** REVIEW.md is capped at 80 lines. Every line carries its source PR.
A line unused for 90 days (no finding cites it) is reviewed for deletion. When a lint rule or
test takes over a rule, the REVIEW.md line is deleted.

**What this misses:** issues that surface once and do severe damage. Loop 2 covers those.

## Loop 2: a bad merge becomes a test, and an eval for the reviewer

**Trigger.** A PR is labelled `X-regression` with `Regressed-by: #n` in its body (maintainer
only), or a release is reverted or hotfixed.

**Within the fixing PR:** a regression test, with its failing output on the pre-fix code
pasted in the Testing section. The dispatcher checks that a test file changed and the section
is filled in; the reviewing maintainer checks that the output is real.

**Incident review (15 minutes, you):** which layer should have caught it (gate, CI, path rule,
classifier, AI review, human read, release checklist), and what that layer did not have in
front of it. Fix that layer:
- **Path rule missed it:** add the path to `t2_paths`. The circuit breaker (rollout.md) does
  this for 30 days automatically.
- **Reviewer missed it:** add an eval case (below).
- **Human missed it:** packet change (what to show), or move the path to T2.

**Eval case.** Stored in the private ops repo, because some cases are security fixes:
`{pr: n, base_sha, head_sha, expected: [{file, line_range, bug_class}], source: "#fix-pr"}`.
A maintainer who knows the code confirms where the bug lived before it enters the set.

**Clean set.** Merged PRs with no follow-up fix within 60 days. It measures false positives
that would push an author into a harmful change.

**When evals run.** On every change to the review prompt, REVIEW.md, schema, model choice or
tool flags, the reviewer runs against all cases. Keep the change only if recall does not drop
and clean-set Important findings do not rise. With subscription caps, keep the set at about 20
cases plus 10 clean PRs; retire the weakest case when adding a new one.

**What this misses:** classes of bug that have never shipped. The weekly digest of open
`pre-existing` findings is a cheap probe for those; there is no nightly whole-repo sweep, to
save tokens.

## Seed cases from the sample

| Source | Layer that should have caught it | Artifact |
|---|---|---|
| Indexer schema version changed by a merge and disputed (#2695, #2697) | decision rule existed nowhere in writing; the AI triage recommended the opposite of the ruling | ADR-0001 "only released schema versions count"; version-ledger test; REVIEW.md line |
| Stray text left by a merge (#2684) | deterministic check | CI grep for conflict markers and merge artifacts in changed lines (misses well-formed but wrong text) |
| Flaky `ForkResolutionSpec` argued on many PRs | CI health | flaky ledger entry and failure-rate tracking (stream 9) |
| Release branch deletion closed approved PRs (#2428, #2583) | release checklist | ruleset blocks deletion; retarget list |
| Duplicate PRs on two branches (#2731, #2733) | intake gate | G8 overlap |
| Merges with no approving review | enforcement | rulesets + CODEOWNERS |

### Version-ledger test (sketch, test-only)

A checked-in ledger `src/test/resources/schema-ledger.conf` records, per release tag, the
indexer `NewestVersion` (and any other stored-format version). A unit test asserts that the
current constant is not below the last released value, and that it is above it when the
ledger's "format changed since release" marker is set. The release checklist updates the
ledger.

**What it misses:** a format change where nobody sets the marker. The serializer path rule makes
those PRs T2, so two people are asked the question.
