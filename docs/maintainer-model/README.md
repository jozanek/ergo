# Maintainer operating model (draft)

How ergoplatform/ergo is maintained when agents do most of the reading and maintainers stay
"on the loop": present where judgment matters, absent where a machine check is enough.

Status: **proposal**. Nothing here is active until the maintainers adopt it. Draft files that
would live elsewhere (`.github/`, repo root) are in [`drafts/`](drafts/) so they cannot take
effect by accident.

| File | Contents |
|---|---|
| this file | baseline, decisions, one-page map, where this differs from the source article |
| [streams.md](streams.md) | per-stream spec (trigger, automation, hand-off, decisions, permissions, failure modes, cost) |
| [tiers.md](tiers.md) | risk tiers, hard-block paths, branch protection settings |
| [dispatcher.md](dispatcher.md) | how agents and the write-token holder are wired, convergence and noise rules |
| [feedback-loops.md](feedback-loops.md) | repeated comment -> rule, bad merge -> test and eval |
| [rollout.md](rollout.md) | rollout order, metrics, loosen/tighten rules |
| [open-questions.md](open-questions.md) | assumptions to confirm |
| [drafts/](drafts/) | CODEOWNERS, REVIEW.md, SECURITY.md, CONTRIBUTING.md, PR/issue templates, labels, tier rules |

## Baseline (read-only sample, 2026-10-17)

- Scala 2.12 (ergo-core/ergo-wallet also 2.13), sbt, Java 8; ~40k lines main, ~37k test;
  consensus also lives in the external `sigma-state` dependency.
- 09-01..10-17: 167 PRs and 89 issues (about 3.5 PRs and 2 issues a day). Jan..Jun 2026 had
  119 PRs and 8 issues in total. Backlog: ~265 open PRs, ~258 open issues.
- 86% of sampled PRs are external. One external author accounts for 54% of PRs since 09-01 and
  usually files an issue and its fix PR seconds apart.
- Issues are almost all pre-diagnosed bug reports; support questions do not arrive on GitHub.
- Effort sinks seen: flaky integration tests argued about on most PRs, release/migration
  sequencing, duplicate and overlapping PRs, stale-base diffs, approved PRs waiting to merge.
- Risk signals seen: security-relevant DoS findings discussed in public, merges with no
  approving review, a schema-version dispute settled by a rule that existed only in one
  maintainer's head ("only released schemas matter"), a deleted release branch auto-closing
  approved PRs, no templates/CODEOWNERS/SECURITY.md.
- Maintainer time today: ~2-3 h/day, roughly 60% PR review, 25% issue triage, 15% prompts.

## Decisions taken (from the interview)

| Topic | Decision |
|---|---|
| Decision rights | kushti and jozanek are co-equal: either approves and merges any tier; T2 needs both |
| Enforcement | required review + CODEOWNERS on `master` and release branches (`v*`) |
| Risk | everything in production code is treated as high risk: no zero-human tier for `src/main` |
| Approval | one maintainer by default; both when a path rule, the classifier or the first reviewer flags it |
| Trust | no external author is on the trusted list yet; trust is earned (see tiers.md) |
| Models | paid subscription seats only, no local models; deterministic checks do most of the work |
| Kept by humans | deep review of P2P/sync, first contact with newcomers, direction with kushti, tuning agents |
| Hard rules | a human is accountable for everything that ships; no bot approves or merges; security reports never touch a public-facing agent or a public thread |

## One-page map

Legend: **Auto** = deterministic, no model. **Agent** = model, read-only, output goes through
the dispatcher. **Human** = a maintainer decides.

| Stream | Auto | Agent | Human |
|---|---|---|---|
| Issue triage | form validation, security hold, issue/PR pairing, dedupe candidates, area labels from paths | classify, judge duplicates, draft reply (small tier) | accept direction, priority, first contact |
| PR intake gates | template, size, base branch, stale base, per-author WIP limit, direction-first, overlap, first-timer | none (0 tokens) | waive a gate |
| PR review | CI, scalafmt, version ledger, tier path rules, CODEOWNERS, flaky annotation | one review + one verifier pass, evidence packet (mid tier) | read and approve on the head commit; T2 second approval; merge click |
| Questions | redirect links in issue forms | weekly FAQ drafts from questions you paste (small tier) | which answers become docs |
| Architecture | triggers: new route, config key, P2P message, storage format, published API | decision brief and ADR draft (mid tier, on demand) | you and kushti decide; ADR merged |
| Security | SECURITY.md, private reporting, silent hold on public items | private only, on demand, no GitHub token | everything |
| Dependencies | Dependabot (actions) and Scala Steward with pins, CI | changelog risk summary (small tier) | merge; T2 for crypto/sigma/serialization |
| Releases | do-release.sh, tag check, retarget-before-delete, merged-PR list | release notes and upgrade/downgrade notes (mid tier) | go/no-go (both), publish |
| CI health (added) | failing-test extraction, flaky ledger, per-test failure rate | diagnose a flaky test, test-only fix (on demand) | prioritise |
| Backlog (added) | stale/superseded detection | one-line salvage note per candidate (small tier) | approve closure batches |

## Where this differs from the source article, and why

The reference is "How the Claude Code team does code review" (claude.dev, 2026-10-15). Its
reasoning carries over; its structure does not, because the situation differs:

1. **No zero-human tier for code.** The article lets trusted owners merge low-risk changes on
   automated checks. Here authors are mostly strangers and you rate all production code as high
   risk, so the floor for `src/main` is one maintainer who reads the diff. Savings come from
   fewer and better-prepared PRs reaching that maintainer, not from skipping the read.
2. **Gates before tokens.** The article has trusted authors and a large budget. Here a single
   external pipeline produces half the volume and tokens are capped, so deterministic gates
   (WIP limit, size, direction-first, stale base) run first and most PRs that fail them never
   cost a model call.
3. **One reviewer plus one verifier, not a voting panel.** Same intent (only verified findings
   are posted), at a fraction of the tokens.
4. **Nits stay private.** The article posts tagged nits. A stranger's agent will "fix" every
   nit and trigger another round, so only Important findings go to the author; nits go to the
   maintainer packet.
5. **Round cap 2 for untrusted authors** (article: 3), with diff-only re-review from round 2.
6. **Agents never run in the public repo's CI with secrets.** The article's reviewer runs in a
   trusted setting. Here PR content is hostile input, so agents run read-only in a private ops
   repo and a separate dumb dispatcher holds the only write token.
7. **Evals on change, not daily.** The incident eval set runs when the prompt, REVIEW.md or
   model changes, to fit the token budget.
8. **No feature flags or employee dogfooding.** A node release cannot be flagged off remotely,
   and storage-format changes can make downgrade impossible, so the release stream carries a
   mandatory "downgrade-safe?" answer and testnet-first where possible.
9. **Security is a separate, private stream**, not just a blocking automated review on the PR:
   the danger is public disclosure, not only missed bugs.
10. **A circuit breaker that tightens.** The article notes it lacks one; this model has one from
    day one (rollout.md): a regression from a T1 merge moves its path to T2 for 30 days.
