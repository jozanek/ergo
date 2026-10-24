# Handoff: maintainer operating model branch

For anyone (person or agent) who continues this work. Read this, then [README.md](README.md).

## What this branch is

A **proposal**, documents only, for how ergoplatform/ergo maintainers run issues, PRs, security
reports and releases with agents doing most of the reading. Nothing on this branch is active:
draft repo files sit in [`drafts/`](drafts/) so they cannot take effect until a maintainer moves
them into place. No production or test code is changed.

Owner: jozanek (maintainer). Co-maintainer whose agreement is needed for governance: kushti.

## Where the branch lives

| Copy | State |
|---|---|
| `jozanek/ergo`, branch `maintainer-model`, draft PR #12 against the fork's `master` | **canonical**; continue here |
| `ergoplatform/ergo` | no copy; the authoring session had no push access (403) |
| Base | upstream `master` at `c1a69f65` (version 6.0.8) |

A cloud session cannot hold `ergoplatform/ergo` and `jozanek/ergo` at once (both check out to
`ergo/`). Start a session with the fork, `git checkout maintainer-model`.

## Decisions already made (do not re-ask)

From an interview with the maintainer on 2026-10-17:

| Topic | Answer |
|---|---|
| Decision rights | kushti and jozanek are co-equal; either approves/merges; T2 needs both |
| Enforcement acceptable | required review + CODEOWNERS on `master` and `v*` branches |
| Agent tooling | Claude Code plus another paid agent (assumed Copilot review; replaced on 2026-10-22, see below) |
| Budget | subscription seats (Max plan, confirmed 2026-10-22), no per-token API bill |
| Local GPU | not used; no local-model tier |
| Time today | about 2-3 h/day: roughly 60% PR review, 25% issue triage, 15% writing prompts |
| What went wrong | slop/drive-by PRs; security issues discussed publicly; burden and bottlenecks |
| Risk | "everything is top tier": every `src/main` change gets a real human read |
| Approval | one maintainer by default; both when a path rule, classifier or first reviewer flags it |
| Trusted externals | none yet |
| Intake outside GitHub | security reports both private (email/DM) and public; questions on chat/forum |
| Maintainer keeps | deep P2P/sync review, first contact with newcomers, direction with kushti, tuning agents |

From answers on 2026-10-22 (details and the facts checked that day are in
[open-questions.md](open-questions.md)):

| Topic | Answer |
|---|---|
| GitHub roles | jozanek stays at write; the admins (kushti, aslesarenko) apply all settings; aslesarenko is not a code owner |
| Questions for kushti | K1-K6 answered on 2026-10-22: no direct pushes, T2 needs both, no bypass, private reporting only, contributions to release and feature branches, testnet first; kushti confirms in one private message |
| Public security report | hide, lock, transfer to a private repo, then a private advisory |
| Inflow | no per-author PR limit (G5 removed), no AI-disclosure field, no backlog sweep (stream 10 removed); size cap 800 |
| scalafmt check | dropped for now |
| Plan and agents | Max plan; agent slots open to any available agent; one AI review per PR |
| Ops | private ops repo in jozanek's account, self-hosted runner, daily digest issue |

Hard rules from the maintainer: a human is accountable for everything that ships; no bot ever
approves or merges; security reports never touch a public-facing agent or a public thread.

## Evidence and how it was gathered

- Step 0 sample (2026-10-17): the 50 latest PRs (#2671-#2749, created 10-05..10-17) and 50 latest
  issues, plus search counts for 09-01..10-17. Counts in README.md come from that; the sample is
  short, so treat rates as rough.
- T2 hit rate, recomputed on 2026-10-22 on a full clone: 27 of 111 merged PRs since 2026-01-21
  (24%). The count uses the GitHub API, so it includes squash merges. It matches files and
  changed lines against `t2_paths`/`t2_content` in `drafts/tier-rules.yml`, and it excludes
  release PRs. tiers.md has the breakdown. The first estimate (19 of 68, shallow clone) counted
  only "Merge pull request" commits on `master`.
- The #2695 ruling ("only schemas in released versions matter") overruled the AI triage
  recommendation; it is the seed for ADR-0001 and the REVIEW.md schema-version line.

## Facts checked against documentation, and facts not checked

Checked (Claude Code docs, 2026-10-17):
- Headless Claude Code can authenticate with a subscription via `claude setup-token`
  (`CLAUDE_CODE_OAUTH_TOKEN`); `ANTHROPIC_API_KEY`, if set, takes precedence in print mode.
- `--tools`, `--disallowedTools`, `--permission-mode dontAsk`, `--max-turns`,
  `permissions.blockReadsOutsideWorkingDirectories`, `--json-schema` (result in
  `structured_output`), and usage/cost estimates in `--output-format json` exist.
- Managed Code Review is Team/Enterprise only, comments only, reads REVIEW.md from the base branch,
  skips fork PRs unless a write-access user asks.
- The claude-code-security-review action's README says it is not hardened against prompt
  injection; use only on trusted PRs.

Not checked (verify before relying on them):
- Ruleset behaviour: code-owner review with 0 required approvals. The docs do not say; test it on
  the fork. (Checked 2026-10-22: a required check can name its source app, and deletion
  blocking exists.) See open-questions.md, A9.
- Whether the Max plan terms allow scheduled/CI use.
- Whether Akka HTTP versions after 10.2.x share Akka's licence change (REVIEW.md states only what
  is in use today).
- The team name `@ergoplatform/node-maintainers` in `drafts/CODEOWNERS` is invented.

## What is not done, in order

1. **kushti's confirmation** of the K1-K6 answers in [open-questions.md](open-questions.md), in one
   private message. K4 (admin setup) is asked when phase 2 starts.
2. **Phase 0** in [rollout.md](rollout.md): SECURITY.md (private vulnerability reporting is
   already on), the conversation with the highest-volume external author about private
   reporting, the agreement with kushti.
3. **Build the dispatcher in dry-run mode.** This is the piece that makes the model save time;
   none of its code exists yet. Spec: [dispatcher.md](dispatcher.md), rules in
   `drafts/tier-rules.yml`, labels in `drafts/labels.yml`, gates G1-G10 (G5 removed) in
   [streams.md](streams.md) stream 2.
   - Shape: small Python program in a private ops repo in jozanek's account, on a self-hosted
     runner, scheduled every 15 min, jozanek's own read-only token, no model calls at first.
     Phase 1 needs no App and no admin help.
   - First deliverable: a daily private digest (one issue in the ops repo) listing each open PR and
     issue with gate results, tier and rule that set it, overlaps, flaky-CI matches. No public
     writes.
   - Acceptance: run it over the last 50 PRs and compare with what maintainers actually did; see
     phase 1 exit criteria in rollout.md.
4. Version-ledger test in `src/test` (sketch in [feedback-loops.md](feedback-loops.md)); allowed
   by AGENTS.md's test-only rule.
5. Only after phase 1 evidence: labels/comments on, then one AI review, then rulesets.

## Rules for editing this branch

- Keep drafts inert under `drafts/` until a maintainer adopts them; adoption moves them to
  `.github/` or the repo root in a separate PR that both maintainers approve.
- Do not name individual external contributors in these docs; describe patterns and cite
  PR/issue numbers.
- Do not put model identifiers in files or commits; refer to "small / mid / large tier".
- Keep REVIEW.md under 80 lines, every rule with its source PR.
- Keep `tiers.md` and `drafts/tier-rules.yml` in sync; the YAML is what the dispatcher reads.
- When a decision changes, update the "Decisions" table above and the README.
- AGENTS.md limits code changes to `src/test/`; production code under `src/main/` is out of
  scope for this work.

## Housekeeping

- Commit author on the first commit is "Claude <noreply@anthropic.com>"; the owner may rewrite it
  with `git commit --amend --reset-author` before sharing.
- A private page holding the original patch exists for the owner (claude.ai artifact
  `6dkJkUuw58hktbhb4cvBAv`); it is a transport copy, not the source of truth.
