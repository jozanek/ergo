# Assumptions and open questions

Status on 2026-10-22: all questions are answered, and the facts below were checked on GitHub.
kushti still confirms the K answers in one private message; K4 waits until the setup is needed.
"A" numbers are assumptions and "Q" numbers are questions, as first listed on 2026-10-17.

## Assumptions, checked on 2026-10-22

| # | Assumption | Result |
|---|---|---|
| A1 | kushti and jozanek are co-equal; an org admin does the GitHub setup | Partly wrong. The repo admins are kushti and aslesarenko, and the org has 8 owners. jozanek has write access and stays at write. aslesarenko keeps admin but is not a code owner. Only an owner or admin can install the App, set rulesets or create a private repo. |
| A2 | Contributors target `v6.0.9`; `master` takes only release PRs; 6.1.x comes from `v5.1.0` | Incomplete. Open PRs by base branch: `master` 169, `v6.0.9` 66, `weak-blocks` 24, `v6.0.3-RC1` 18, `v6.0.10` 2, `v5.1.0` 1. Contributions go to the release branches and to feature branches such as `weak-blocks` (K5). |
| A3 | The plan allows headless use with `claude setup-token` | The seats are on the Max plan, which supports `setup-token`. Still open: check the plan terms for scheduled use before phase 3. |
| A4 | No local models | Confirmed. |
| A5 | The second paid agent is Copilot review | Replaced. The agent slots are open to any available agent that meets the contract in dispatcher.md. Each PR still gets one AI review. |
| A6 | Numbers come from a short sample and a shallow clone | The T2 rate was recomputed on a full clone; see tiers.md. The other rates are still from the sample. |
| A7 | Questions arrive on chat and the forum | Confirmed. |
| A8 | GitHub-hosted runner minutes on a private ops repo are enough | Wrong. A private repo gets 2,000 free minutes a month on GitHub Free, or 3,000 on Pro and Team. The 15-minute `collect` job alone runs about 2,900 times a month. The ops repo uses a self-hosted runner, which uses no Actions minutes. |
| A9 | Ruleset details behave as described | Partly verified in the GitHub docs. A required check can name its source app. "Restrict deletions", "Dismiss stale pull request approvals" and "Require approval of the most recent reviewable push" exist. Not verified: code-owner review with 0 required approvals. Test it on the fork before phase 3. |

Also found on 2026-10-22: private vulnerability reporting is already on, only `master` is
protected (with no required checks), and there are no rulesets.

## Answered by jozanek on 2026-10-22

| # | Question | Answer |
|---|---|---|
| Q2 | Who holds org admin? | See A1. jozanek stays at write. |
| Q5 | A public item turns out to be a security report | Hide the comments, lock the issue, transfer it to a private repo, then open a private advisory. A transfer needs write access on both repos, not admin. An owner creates the private repo once (K4). |
| Q6 | PR limit and size cap | No per-author PR limit, so gate G5 is removed. The size cap stays at 800 changed non-test lines. |
| Q7 | AI-assistance disclosure | Not asked. The PR template has no AI field. |
| Q8 | Backlog sweep | No sweep, so stream 10 is removed. Nothing is closed for age. |
| Q9 | First-response target | None in CONTRIBUTING.md until phase 1 has measured today's wait (proposed default, no objection). |
| Q11 | Schema ledger and "downgrade-safe" | The author of the release PR updates the ledger. The approving maintainer confirms "downgrade-safe" (proposed default, no objection). |
| Q12 | T2 on the 6.1.x line | Yes, the same rules (proposed default, no objection). |
| Q13 | Plan | Max. Managed Code Review needs Team or Enterprise, so the dispatcher runs its own review. |
| Q14 | Private digest | A daily issue in the ops repo. |
| new | scalafmt check | Dropped for now. The build has no scalafmt plugin, and many files are not formatted. Decide again later. |
| new | Ops repo | Private, in jozanek's account, with a self-hosted runner. An owner or admin only installs the App, from phase 2. |

## Questions for kushti, answered on 2026-10-22

kushti confirms these answers in one private message. K4 is asked when the setup is needed.

| # | Question | Answer |
|---|---|---|
| K1 (Q1) | Direct pushes to `master` and the release branches; T2 approvals | No direct pushes, for anyone: fixes and merges from base also go through PRs. T2 PRs need both maintainers; on a maintainer's own PR, the other maintainer's approval is enough. Since 2026-09-01, 7 commits went straight to `v6.0.8`/`v6.0.9`, and 3 of them change T2 files. T2 PRs were 27 of 111 merged PRs since 2026-01-21 (24%; see tiers.md). |
| K2 (Q3) | Emergency path: may anyone bypass the rulesets? | No. The bypass list stays empty, also in an emergency. When one maintainer is away, the other's PRs wait. |
| K3 (Q4) | Security contact, response target, supported versions | Private vulnerability reporting is the only channel; there is no security email and no bug bounty to mention. Acknowledge within 3 working days. Supported: the latest 6.0.x release. |
| K4 | Admin setup | Open, asked when needed: rulesets, one private repo for moved security reports, and from phase 2 the App. |
| K5 (A2) | Which branches take contributions? | The release branches (`v6.x`; `v5.1.0` builds 6.1.x) and feature branches such as `weak-blocks`. `master` takes only release PRs. |
| K6 (Q10) | Is there a testnet step before a mainnet release? | Yes. The test release is announced on Discord. |

## Not decided here on purpose
- Which model each tier maps to: pick the fastest, standard and most capable models your plan
  offers, then let the eval set decide.
- Whether any change ever merges with zero human reads. Today the answer is no for all
  production code, by your choice.
