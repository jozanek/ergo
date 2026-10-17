# Assumptions and open questions

## Assumptions (correct any that are wrong)

1. The maintainers are kushti and jozanek, co-equal. An org admin can install a GitHub App,
   create a team, set rulesets and enable private vulnerability reporting.
2. Contributors should target the current integration branch (`v6.0.9` today); `master` only
   takes release PRs; 6.1.x pre-releases come from `v5.1.0`.
3. Your Claude subscription plan allows headless use with `claude setup-token`; the docs list
   Pro, Max, Team and Enterprise. Check your plan's terms for automated use before phase 3.
4. No local models. If you later run some, move the small-tier jobs first (triage
   classification, duplicate judging, changelog summaries); keep review on the subscription.
5. The "other paid agent" is Copilot review (a quota message was seen on one PR). This model
   uses one AI reviewer.
6. Numbers come from a 12-day PR sample, 50 issues, search counts since 09-01, and a shallow
   clone (about 1,300 commits). The T2 rate (19 of 68 merged PRs) is from that clone.
7. Questions arrive on chat and the forum; no agent operates there.
8. GitHub-hosted runner minutes on a private ops repo are enough (about 3 agent runs a day);
   otherwise use a self-hosted runner in a container.
9. Ruleset details (code-owner review with 0 required approvals, required checks pinned to an
   App, deletion blocking) behave as described. Verify in the settings UI during phase 0.

## Open questions for you

**Governance (blocks phase 3).**
1. Will kushti accept no direct pushes to `master`/`v*` (his merge-from-base and fix commits
   become PRs), your approval on his PRs, and both approvals on roughly 1 in 4 merged PRs?
2. Who holds org admin for the App, team, rulesets and private reporting?
3. Emergency path: who may bypass rulesets, and how is each bypass reviewed?

**Security.**
4. Security contact email, supported versions, acknowledgement and fix targets for SECURITY.md.
   Is there a bug bounty to mention?
5. For public items that turn out to be security reports: hide and transfer to a private repo, or
   close and re-file as an advisory? (Transfer needs admin rights.)

**Inflow policy.**
6. WIP limit (proposed 5) and size cap (proposed 800 changed non-test lines): right numbers?
7. Require AI-assistance disclosure in PRs? What happens to undisclosed agent PRs?
8. May the backlog sweep close PRs and issues untouched for 180 days, in batches you approve?
9. Do you want the first-response target in CONTRIBUTING.md, and what is it?

**Releases.**
10. Is there a testnet or canary-operator step before mainnet announcements? Who are the canary
    operators?
11. Who updates the schema ledger and decides "downgrade-safe" in release notes?
12. Should T2 also cover the 6.1.x RocksDB line identically?

**Tooling.**
13. Which plan are the seats on? If Team/Enterprise, do you want to trial managed Code Review as
    the single AI review instead of the dispatcher's own (trade-offs in dispatcher.md)?
14. Where do you want the private digest: a daily issue in the ops repo (default), email, or
    something else?

## Not decided here on purpose
- Which model each tier maps to: pick the fastest, standard and most capable models your plan
  offers, then let the eval set decide.
- Whether any change ever merges with zero human reads. Today the answer is no for all
  production code, by your choice.
