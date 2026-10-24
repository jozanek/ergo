# Rollout and metrics

Order: hours saved per hour of effort. Start strict, loosen only on evidence, tighten
automatically on regressions.

## Phase 0: preconditions (week 0, about 1 day of effort)

| Item | Why now |
|---|---|
| Publish SECURITY.md (private vulnerability reporting is already on) | public security disclosure already happened; this is a policy, not a bot |
| Talk to the highest-volume external author about private reporting for DoS findings | this author sends about half of all PRs |
| Confirm the K1-K6 answers with kushti in one private message (open-questions.md) | phase 3 is not enforceable without it |
| Create the ops repo in jozanek's account with a self-hosted runner; sync labels | plumbing; the App and the team wait until phase 2 |

If you want the first two weeks to be strictly measure-only, move CONTRIBUTING.md and the
templates to phase 2. Keep SECURITY.md in phase 0 either way.

## Phase 1: measure only (weeks 1-2)

No public bot output and no enforcement changes. The dispatcher runs in **dry-run**: it
computes gates, tiers, packets and triage verdicts and writes them only to the private digest.
It needs no App and no admin help: it reads public data with jozanek's own read-only token.

You log minutes per stream once a day (one line in the ops repo: `2026-10-20 pr:70 issues:30
prompts:20 other:10`). Compare the dispatcher's calls with yours:
- tier agreement (did it flag T2 where you would?);
- gate misfire rate (would a gate have blocked a PR you would merge?);
- triage label agreement;
- tokens per run, from the JSON output, to calibrate the costs in streams.md.

Exit criteria: at least 85% tier agreement, gate misfire under 10% per gate, and no security-hold misses
in the sample.

## Phase 2: deterministic first (weeks 3-4)

1. **Intake gates as labels plus one gate comment** (stream 2). The biggest saving for the
   least effort: direction-first, size, stale base and overlap keep work from reaching you at
   all. 0 tokens. Needs the App (open-questions.md K4).
2. **Flaky-CI annotation** (stream 9). Stops the repeated "is this flaky?" threads. 0 tokens.
3. **Issue triage on the dispatcher** (stream 1): move your existing triage prompts into the ops
   repo with a schema; auto `C-*`/`A-*` labels; first-contact drafts stay yours.
4. Version-ledger test; conflict-marker grep.

## Phase 3: one AI review and enforcement (weeks 5-6)

5. **AI review to the private packet only** for one week; measure outcomes against your own
   review. Then post Important findings publicly if the measured `wrong` rate is at most 20%.
6. **Rulesets + CODEOWNERS + `ergo-tier`** as required checks (after kushti agrees and phase 1
   showed the T2 rate).
7. **Release checklist automation** at the next release.

## Phase 4: widen (week 7 onward)

8. Dependabot (actions) and Scala Steward with pins.
9. Direction agenda and ADRs; seed ADR-0001..0004.
10. Merge queue, if stale-base breakage persists after G4.

## Metrics

**Human time (the goal is down).**
| Metric | Source |
|---|---|
| Minutes per stream per day | your daily line (phase 1), then one sample week a month |
| Maintainer comments per merged PR (median, p95) | API |
| Human review rounds per merged PR | API |
| Wait: ready-for-human to first maintainer action; approved to merged; T2 second-approval wait | dispatcher timestamps |
| Open `S-ready-for-human` count and age | dispatcher |

**Reviewer quality and noise.**
| Metric | Target before widening |
|---|---|
| AI findings `wrong` | at most 20% |
| AI findings `fixed` | at least 50% |
| PRs passing after one AI round | rising; at most 10% hit the cap |
| Gate waivers / gate triggers | under 10% per gate |
| Triage labels corrected by maintainers | under 15% |
| Eval recall on incident set; clean-set Important findings | no drop on any change |

**Guardrails (if these rise, time was moved, not saved).**
- Reverts, fix-forwards (`X-regression`), hotfix releases.
- Operator bug reports naming a release within 14 days of it.
- Security-held items that received any public bot output (target 0); time to private
  acknowledgement.
- First-time contributors with no human response in 7 days; first-time PR abandonment.
- Ruleset changes, and PRs merged without the required approvals (target 0; there is no bypass).

## Loosen on evidence (both maintainers decide)

| Candidate | Evidence needed |
|---|---|
| Maintainer-authored additive tests-only PRs to T0 | 4 weeks, 0 regressions from tests-only PRs (15% of merged PRs today) |
| `ci.yml` and `bootstrap-*` from T2 to T1 | actions SHA-pinned; secrets moved to release-only workflows |
| Nits posted publicly | `wrong` under 10% on nits for 4 weeks |
| Raise the size cap | waiver rate over 20% on G2 |
| First external trusted author | criteria in tiers.md |
| Integration tests as a required check | flake rate under 5% for 4 weeks |

## Tighten automatically (circuit breaker)

- A regression traced to a T1 merge: that path goes to T2 for 30 days and becomes an eval case.
- AI `wrong` rate over 30% for two weeks: AI review goes back to private-packet-only.
- A security-hold miss: public triage output pauses until the filter is fixed and replayed on the
  last 100 items.
- Token budget at 80% of the window: AI review only on maintainer command until the window
  resets; gates continue.
