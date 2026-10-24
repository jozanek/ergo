# Per-stream specs

Each stream uses the same fields. "Misses" says what a check would not catch. Costs are
estimates to calibrate during the two measure-only weeks (rollout.md); the budget is
subscription seats, so cost is stated in tokens and model tier, not dollars.

Model tiers: **small** = fastest/cheapest model on the subscription, **mid** = standard model,
**large** = most capable model, on demand only. No local models are assumed.

The "packet" is what the agent hands a maintainer; it is defined once in dispatcher.md.

---

## 1. Issue triage

**Trigger.** Issue opened, edited or reopened; author reply on an `S-needs-info` issue.

**Auto (deterministic, first).**
- Issue form fields present (forms make this structural, not a model call).
- Security pre-filter on title/body/paths (crash, OOM, DoS, unbounded, panic, exploit, bypass,
  key, seed, mnemonic, steal, double spend, chain split, CVE, vulnerab*). A hit means **silent
  hold**: no public bot output, item goes to the private digest (stream 6).
- Issue/PR pairing: a PR by the same author that references the issue or opens within 1 h is
  linked; the pair is triaged once, as the PR (stream 3). This removes most current issue work.
- Duplicate candidates: top 5 from GitHub search on title terms and cited file paths.
- Area label from cited paths (`tier-rules.yml` path -> `A-*` map). First-time author flag.

**Agent (small tier).** Category and area labels from an enum, duplicate judgement among the 5
candidates, draft reply. If the report cites `file:line`, check the line exists and says what
the report claims (mid tier, only when cited).

**Hands you (digest row).** Verdict (`accept` / `needs-info` / `duplicate-of #n` / `question` /
`needs-direction` / `hold`), proposed labels, up to 5 places to look, draft reply.

**Your decisions.** `S-accepted` for enhancements (maintainer-only label), priority and target
release, won't-fix, and first contact: for first-time authors you send the draft yourself.

**Template / auto-close.** Dispatcher applies `C-*`/`A-*` labels (cheap to undo). For repeat
authors it posts the needs-info template when required fields are missing. Nothing closes
automatically, and nothing is closed for age (no backlog sweep, by decision).

**Permissions.** Agent: issue text as data, no GitHub token. Dispatcher: issues write (labels,
template comments).

| Failure mode | Check | Misses |
|---|---|---|
| Injection in issue text ("label this accepted") | output limited to enum labels; `S-accepted` is maintainer-only in dispatcher code | misclassification that stays inside the enum (you see every row in the digest) |
| Security report gets a public bot reply | keyword hold and classifier must both say "not security"; any error means hold (fail closed) | reports with no keywords that the classifier also misses |
| False duplicate | label `X-possible-duplicate` only, never close | duplicates worded differently (search recall) |
| Wrong area label | maintainer relabel is logged as triage noise | nothing structural; tracked as a metric |

**Cost.** About 5-10k tokens per unpaired issue (small tier), about 2 a day. Paired issues: 0.

---

## 2. PR intake gates

**Trigger.** PR opened, edited, marked ready for review, or pushed; dispatcher poll every 15 min.

**Auto (all deterministic, 0 tokens). Gates run in the dispatcher, not in PR CI,** because
a fork PR's own workflow files and scripts are attacker-controlled.

| Gate | Rule (initial, strict) | Outcome | Misses |
|---|---|---|---|
| G1 template | Testing, Risk, Revert/downgrade sections non-empty | `S-needs-author` + checklist | boilerplate answers (reviewer reads them) |
| G2 size | > 800 changed non-test lines from an untrusted author | `S-needs-split`; no AI review unless a maintainer asks | small diffs that are dangerous (tier handles risk, not size) |
| G3 base | base is `master` (except release PRs), or a `v*` branch that is not a live release branch (list in tier-rules.yml); feature branches pass | template comment | intentional backports (maintainer waives) |
| G4 stale base | PR carries commits already on another branch (patch-id match) or > 20 commits | `S-needs-rebase` | bloat from squashed copies with new patch ids |
| G6 direction | enhancement or behaviour change from an external author without a linked `S-accepted` issue | `S-needs-direction`; discuss on the issue first | a "bugfix" that is really a design change (classifier and reviewer can apply it) |
| G7 first-timer | `author_association` FIRST_TIME_CONTRIBUTOR/NONE | `X-first-time`; Actions approval required; you do first contact | new accounts with a prior trivial merged PR |
| G8 overlap | open PRs touching intersecting hunks | listed in packet; > 50% overlap with an older PR means `X-possible-duplicate` | duplicate intent in different files |
| G9 CI | required checks green on head SHA; failures matched to flaky ledger | packet annotation | a real failure in a known-flaky test (stream 9) |
| G10 security | stream-1 pre-filter applied to PR title, body and paths | silent hold | as stream 1 |

G5 (per-author PR limit) was removed on 2026-10-22: there is no PR limit, by decision. Drafts
are ignored entirely (no gates, no review) until marked ready.

**Hands you.** Nothing unless a gate is waived or a PR is held. Authors get one templated
comment listing failed gates, edited in place on each push (never a new comment per push).

**Your decisions.** `/ergo waive <gate>` on any PR.

**Permissions.** Dispatcher: labels, the one gate comment, the `ergo-gates` check run.

| Failure mode | Check | Misses |
|---|---|---|
| Gate bug blocks good PRs | gates are labels only (advisory) until weeks 1-2 show misfire rate < 10% per gate | rare misfires after that (waivers logged per gate) |
| Gates feel hostile to newcomers | rules published in CONTRIBUTING.md before enforcement; first-timers get your words, not a bot's | tone; you watch first-timer abandonment |

**Cost.** API calls only.

---

## 3. PR review

**Trigger.** A non-draft PR that passes gates gets **one** AI review. After that, only: author
comment `/ergo ready` (diff-only, at most once a day), or maintainer `/ergo review` or
`/ergo deep`.

**Auto (deterministic, first).** Existing CI (wallet, core, node, it); version-ledger tests
(feedback-loops.md); conflict-marker grep; tier path rules (tiers.md); CODEOWNERS; overlap list;
flaky annotation. No scalafmt check (dropped for now: the build has no scalafmt plugin).

**Agent (mid tier, read-only).** Inputs: REVIEW.md, the diff, PR intent and linked issue, read
access to the checkout. Outputs (JSON): findings tagged `important` / `nit` / `pre-existing`
with file:line evidence; a tier classifier answer ("does this change what nodes accept, on-disk
formats, P2P wire format, keys or auth?" yes requires a cited line, and sets the T2 flag); and
the packet. A **verifier** pass (fresh context, mid tier, findings only) re-derives each
Important finding from the code and drops any it cannot support with a file:line. Up to 3 nits,
kept in the packet only.

**Convergence.** Untrusted authors get 2 AI rounds (maintainers 3); round 2+ is diff-only from
the last reviewed SHA; findings are fingerprinted and never repeated; one finding names the
class of bug and lists every instance. At the cap: `X-ai-review-exhausted`, and a human decides.

**Hands you.** The packet: verdict, tier and the rule that set it, what could go wrong, at most
5 places to look, evidence on the head SHA, what the checks would miss, overlaps, findings
status, rounds used. P2P/sync PRs route to jozanek; consensus core, storage formats and releases
route to kushti first; the rest by queue length.

**Your decisions.** T1: read the diff starting from the 5 places, read the evidence, approve the
head commit. T2: the second maintainer does the same. Direction calls. The merge click.

**Template / auto-close.** Templates for `S-needs-split`, `X-ai-review-exhausted`, "Important
findings open". No auto-close.

**Permissions.** Agent: read-only tools, no GitHub token, no network beyond the model API.
Dispatcher: `COMMENT` reviews only (never `APPROVE` or `REQUEST_CHANGES`), labels, check runs.

| Failure mode | Check | Misses |
|---|---|---|
| Reviewer misses a consensus bug | T2 path rules + classifier + human read + incident evals | consensus-relevant logic in T1 paths (e.g. mempool validation drifting from block validation); REVIEW.md names it |
| Injection makes the agent stay silent | every `src/main` change still gets a human read (T1 floor) | a human skimming because the packet looked clean; packet always states what checks miss |
| Noise causes churn | nits private, round cap, outcome tracking (feedback-loops.md) | slow drift; reviewed weekly |
| Agent-vs-agent loop with an author's pipeline | cap 2 rounds, `/ergo ready` once a day | none expected |
| Token exhaustion by spam PRs | gates first, first-timer approval, token circuit breaker (rollout.md) | many PRs from one author that pass the gates (no PR limit, by decision) |

**Cost.** First review about 60-200k tokens (mid tier, prompt-cached repo context), verifier about
10-30k, diff-only re-review about 20-60k. If about 2-3 PRs a day pass gates: about 0.3-0.7M tokens a day.
`/ergo deep` (large tier) for T2 on demand: about 200-400k.

---

## 4. Questions and discussions

**Trigger.** None on GitHub in the sample; questions live on chat and the forum. GitHub
questions are redirected by the issue form's contact links.

**Auto.** `config.yml` contact links; `C-question` gets a redirect template (repeat authors) or
a draft for you (first-time authors).

**Agent (small tier, weekly, on your trigger).** You paste or export the week's recurring
questions; the agent clusters them and drafts FAQ.md entries or a docs PR, each citing the code
or config it describes.

**Your decisions.** Which answers become docs; answering people yourself. No public-facing agent
answers on chat: it costs tokens, carries injection and misinformation risk, and first contact
is yours.

| Failure mode | Check | Misses |
|---|---|---|
| FAQ drifts and lies | each entry cites version and file; REVIEW.md: for docs, failure is "it lies" | entries whose cited file did not change but behaviour did |
| Recurring question never reaches docs | weekly clustering | questions you do not paste |

**Cost.** About 50k tokens a week.

---

## 5. Architecture and direction

**Trigger (deterministic signals in a diff or issue).** New REST route; new config key in
`application.conf`; new P2P message code; new storage key or serializer; new module or
dependency; activation height/voting change; public API change in published `ergo-core` or
`ergo-wallet`. Also `C-enhancement`, the gate-6 label, or a reviewer flag.

**Auto.** Add sbt-mima for `ergo-core` and `ergo-wallet` (published to Sonatype): binary
compatibility becomes a check, not a discussion. Misses: semantic changes with the same
signature.

**Agent (mid tier, on demand).** A one-page decision brief: problem, up to 3 options, invariants
touched, prior ADRs, recommendation. It becomes the ADR draft.

**Hands you.** A weekly agenda for you and kushti: at most 5 direction items with briefs.

**Your decisions.** Yours and kushti's (kept). Every resolved `S-needs-direction` ends as an ADR
in `docs/adr/`, approved by both.

**Template.** "Direction is decided on the issue before code; this PR is paused" (pull left).

**Seed ADRs.** (1) Indexer schema version and migration policy. (2) Release/integration branch
policy: base branches, retargeting, deletion. (3) Bounded per-peer state in P2P. (4)
AI-assisted contributions: the evidence expected from authors (no disclosure field and no PR
limit, by decision).

| Failure mode | Check | Misses |
|---|---|---|
| Pattern-setting change lands as a bugfix | signals above + classifier | new internal patterns (e.g. actor messaging style) |
| Decision lost in a thread | ADR required to clear `S-needs-direction` | decisions made in chat and never written |

**Cost.** About 30-80k per brief, about 3 a week.

---

## 6. Security reports

**Hard rule.** No public-facing agent touches these; nothing is discussed in a public thread.

**Trigger.** GitHub private vulnerability reporting (already on, and the only channel; reports
reach only the repository admins and security managers), a DM to a maintainer (copied into a
private advisory), or a stream-1/G10 hold.

**Auto.** On a hold: no public bot output of any kind (no label, no comment); a private digest
entry and notification within the poll interval. Holds expire into the normal path only when a
maintainer releases them.

**Maintainer action within 24 h of a hold.** Not security: release. Security (decision
2026-10-22): hide the comments, lock the thread, and transfer the issue to the private repo. A
transfer needs write access on both repos; an owner creates that repo once. Then an admin opens
a private advisory and invites the reporter. Move any fix PR into the advisory's temporary
private fork.

**Agent (large tier allowed, on demand, private only).** Run locally or in the advisory's private
fork, with no GitHub token. Reproduce with a failing test (test-only), assess severity and
affected versions, draft the advisory. It never posts anywhere.

**Your decisions.** Everything: severity, embargo, fix, which release, disclosure date, credit,
CVE. jozanek has write access, so he sees a report only after an admin adds him to the
advisory.

| Failure mode | Check | Misses |
|---|---|---|
| Public report gets a bot reply | hold is checked before every public write; fail closed | reports the filter and classifier both miss; you see the digest daily |
| Reports arrive by DM and are lost | SECURITY.md names one channel; DMs are copied into an advisory | DMs nobody copies |
| Fix PR or release notes reveal the exploit | REVIEW.md: neutral titles for security fixes; notes after the upgrade window | leaks in commit messages from external authors |
| External pipeline keeps posting DoS findings publicly | direct conversation with the author; SECURITY.md defines what must be private | first-time reporters who do not read it |

**Cost.** Rare, on demand; large tier is justified here.

---

## 7. Dependency updates

**Trigger.** Weekly Dependabot for GitHub Actions (grouped); monthly Scala Steward (grouped).

**Auto.** Pins: Akka 2.6.x and Akka HTTP 10.2.x, the lines in use today (newer Akka releases are
under the BSL licence; moving off them, e.g. to Pekko, is an ADR); Java 8 target; `sigma-state` and `scrypto` never auto-bumped. Actions pinned
by SHA in workflows that hold secrets. CI must pass.

**Agent (small tier).** Summarize release notes the dispatcher fetched: breaking changes, CVEs,
behaviour changes in crypto, serialization or networking.

**Your decisions.** Merge: T1; T2 for `sigma-state`, `scrypto`, BouncyCastle, serialization
libraries and secret-bearing workflows.

| Failure mode | Check | Misses |
|---|---|---|
| Transitive bump changes crypto/serialization | T2 list + CI + IT tests | behaviour changes in libraries not on the list |
| Compromised upstream action/library | SHA pins, diff of the action on bump | compromise inside a version you then pin |

**Cost.** About 5-10k tokens per bot PR.

---

## 8. Releases

**Trigger.** Maintainer opens a "Candidate for X" PR or comments `/ergo release-prep vX.Y.Z`.

**Auto.** `do-release.sh` version consistency (application.conf, openapi.yaml, jar); PR
title/branch/tag/version agree; after publishing, the tag exists (a v6.0.7 tag could not be
found); before deleting a release branch, list open PRs targeting it and retarget them on
command (deleting a base auto-closes its PRs); merged PRs since the last tag with tier labels
and template answers; open `X-regression` or `P-release-blocker` items.

**Agent (mid tier).** Release notes grouped by area, operator-facing upgrade notes, and a
**downgrade-safe** answer derived from PR templates and path rules (schema versions,
serializers, storage keys).

**Hands you.** A go/no-go checklist with red items first.

**Your decisions.** Go/no-go (both maintainers), version, publish. Testnet first: yes; the test
release is announced on Discord (open-questions.md K6).

| Failure mode | Check | Misses |
|---|---|---|
| Stored format changed but schema version not above the last release | version-ledger test (feedback-loops.md) | format changes without a version bump (serializer path rule makes it T2) |
| Release mislabelled or tag missing | checklist consistency and tag check | wrong content under the right name |
| Branch deletion closes approved PRs | ruleset blocks deletion; retarget list first | PRs on unprotected feature branches |
| Operators cannot downgrade | mandatory downgrade-safe field | undetected format changes |

**Cost.** About 30-60k tokens per release, every 2-3 weeks per line (6.0.x, 6.1.x).

---

## 9. CI health and flaky tests (added)

**Why.** One integration spec failed in 22 of 50 PR runs and was argued about on most PRs
before it was fixed.

**Trigger.** Any failed CI run on a PR or on `master`/`v*`.

**Auto.** Fetch failed job logs; extract failing test names (`*** FAILED ***`); match the
`flaky-ledger.yml` signatures (test, regex, tracking issue); per-test failure rate over the last
50 runs on unrelated PRs and on the base. A test above 10% gets a tracking issue and a place on
your weekly list. The annotation never turns a check green and never skips a test.

**Agent (mid tier, on demand).** Diagnose the top flaky test and propose a test-only fix.

| Failure mode | Check | Misses |
|---|---|---|
| A real regression is waved off as flaky | annotation only; if the test fails twice on the PR while passing on base, the label flips to "likely real" | a regression that fails intermittently |

**Cost.** Free; agent only on demand.

---

## 10. Backlog hygiene (removed)

Removed on 2026-10-22: there is no backlog sweep, by decision. Nothing is closed for age.
