# Wiring: agents, dispatcher, permissions

Principle: issues, PRs and comments are hostile input. Agents that read them are read-only and
hold no GitHub token. A separate **dispatcher** is deterministic code (no model) that is the
only component allowed to write to the public repo, and only through an allowlist.

## Components

```
ergoplatform/ergo (public)                 <ops repo> (private, maintainers only)
  existing CI on pull_request                 collect   cron every 15 min, read-only App token
    (forks get no secrets)                    gates     deterministic, read-only App token
  CODEOWNERS + rulesets                       agent     model token only, PR checked out as data
  ergo-tier check  <-------------------+      dispatch  write App token, no model token,
  labels, gate comment, COMMENT        |                never checks out PR code
  reviews  <---------------------------+----  store     packets, metrics, fingerprints, evals
                                              digest    one private issue per day for maintainers
```

- **GitHub App** ("ergo-dispatcher"; jozanek creates it, and an owner or admin installs it on
  ergoplatform/ergo when phase 2 starts; phase 1 reads public data with jozanek's own read-only
  token): `metadata:read`, `contents:read`, `issues:write`, `pull_requests:write`, `checks:write`.
  No `contents:write` (so it cannot push or merge) and no `administration`. Installation tokens
  for `collect` and `gates` are minted with read-only permissions; only `dispatch` gets the write
  set. The App is never a code owner, so its reviews cannot satisfy CODEOWNERS.
- **Ops repo** (private, in jozanek's account; kushti is a collaborator): dispatcher code,
  prompts, REVIEW prompt versions, JSON schemas, flaky ledger, eval set, metrics, daily digest.
  Changes to the dispatcher or prompts are reviewed like T2.
- **Runner**: a self-hosted runner in a container on jozanek's own server (decision 2026-10-22).
  Self-hosted runners use no Actions minutes. GitHub-hosted minutes would not cover the
  15-minute `collect` job (about 2,900 runs a month). The agent job runs in a throwaway container.

## Agent job (read-only)

Any available agent can fill an agent slot (decision 2026-10-22) if it meets this contract:
- read-only file tools; no shell, edit or web tools;
- no GitHub token in the job; no network access except the model API;
- REVIEW.md, AGENTS.md and tier-rules.yml come from the base branch;
- the output is JSON that validates against the schema in the ops repo.

Each PR still gets one AI review, whichever agent runs it. The Claude Code setup below is one
example (the seats are on the Max plan).

Authentication: `CLAUDE_CODE_OAUTH_TOKEN` from `claude setup-token` (subscription plans). Do
not set `ANTHROPIC_API_KEY` in the job: in print mode it takes precedence over the OAuth token.

Invocation shape (flags per the Claude Code CLI docs; check against your installed version):

```
claude -p "$(cat prompts/review.md)" \
  --tools "Read,Grep,Glob" \
  --disallowedTools Bash Edit Write WebFetch WebSearch "mcp__*" \
  --permission-mode dontAsk --max-turns 25 \
  --settings '{"permissions":{"blockReadsOutsideWorkingDirectories":true}}' \
  --json-schema schemas/review.json --output-format json > out.json
```

- The workspace holds the PR head checked out as files, plus `REVIEW.md`, `AGENTS.md` and
  `tier-rules.yml` **from the base branch** (a PR cannot rewrite its own review rules). Nothing is
  built or executed: test evidence comes from CI results the `collect` job fetched.
- The only secret in the job is the model token. No GitHub token is present, so injected text
  has nothing to write with.
- `structured_output` must validate against the schema or the run is discarded (counted as an
  agent failure, retried once, then the PR is flagged for a human).
- Usage and cost estimates from the JSON output are logged per run. Billing is by subscription,
  so these are a relative budget meter, not an invoice.

## Dispatch job (dumb writer)

Validates `out.json` against the schema and applies policy. Nothing the model wrote reaches
GitHub except through these slots:

| Allowed | Detail |
|---|---|
| Labels | only from the allowlist in labels.yml; `S-accepted`, `T2-both` removal and `X-regression` are maintainer-only |
| Gate comment | one per PR, edited in place, from fixed templates with escaped parameters |
| Findings | one `COMMENT` review per round; event type is hard-coded; at most 5 Important findings |
| Check runs | `ergo-gates` (advisory), `ergo-tier` (required) |
| On maintainer command only | retarget base branch, close with template, post a drafted first-contact reply |

| Never (enforced in code and, where possible, by App permissions) |
|---|
| `APPROVE` or `REQUEST_CHANGES` reviews, merging, pushing, editing others' comments, deleting anything |
| any public write on an item on the security hold list (checked before every write) |

**Outbound filter** on every model-originated string: reject on secret-like patterns (GitHub,
Anthropic and OAuth token formats, private keys), URLs outside `github.com/ergoplatform/`,
markdown images and HTML (exfiltration channels), `@` mentions other than the PR author, and any
`/ergo` command text (the bot must not trigger itself). Over-length text is truncated.

## Commands

Parsed only from comments whose author is a listed maintainer login **and** has
`author_association` OWNER, MEMBER or COLLABORATOR.

`/ergo review`, `/ergo deep` (large tier), `/ergo tier 2`, `/ergo waive <gate>`,
`/ergo send-draft`, `/ergo retarget <branch>`, `/ergo release-prep <v>`,
`/ergo wrong|intended|minor` (finding outcome, reply in the finding's thread).

From the PR author only: `/ergo ready` (diff-only re-review, at most once a day, within the
round cap).

## The packet (what a maintainer receives)

Stored in the ops repo; linked from the daily digest; never public.

```
#2712  [T2-both: rule storage/serializer]  verdict: ready-for-human  waited: 6h
What could go wrong:  (max 3 lines, as failure modes)
Look here (max 5):    file:line-range - why
Evidence on <sha>:    CI jobs + links; tests added/changed; fails-without-fix shown? y/n
Checks would miss:    one honest line
Overlaps/depends:     #nnnn (same hunk in X.scala)
AI findings:          2 important (1 fixed, 1 open), 3 nits (private), round 1/2
Template answers:     downgrade-safe: no (schema version 8 -> 9)
```

## Convergence

- Round cap: 2 AI rounds for untrusted authors, 3 for maintainers. Then `X-ai-review-exhausted`.
- Round 2+ is diff-only from the last reviewed SHA.
- Each finding has a fingerprint (path, bug class, symbol). A fingerprint is never posted twice on
  a PR. One answered with a reason is suppressed for the rest of the PR.
- Findings name the class and list every instance, so the author fixes them all in one push.
- The verifier pass drops findings it cannot tie to a `file:line`.

## Noise measurement

Every posted finding gets an outcome:
- `fixed`: thread resolved and a later commit touched the cited lines (computed);
- `wrong` / `intended` / `minor`: maintainer or author reply command (explicit);
- `dismissed`: resolved with no change and no command (unknown reason; counted separately).

Targets before widening: `wrong` at most 20%, `fixed` at least 50%. A known-clean set (merged PRs with no
follow-up fix in 60 days) runs with the eval set to count Important findings that would have
pushed someone into a harmful change.

## Alternatives considered

- **Managed Code Review (Team/Enterprise plans only).** Not available: the seats are on the Max
  plan (2026-10-22). It would also post directly rather than through the dispatcher, so the nit
  suppression, round cap and outbound filter here would not apply.
- **claude-code-action in the public repo.** Fork `pull_request` runs get no secrets, and
  `pull_request_target` would expose them to fork-controlled content. Not used for external PRs.
- **claude-code-security-review action.** Its README says it is not hardened against prompt
  injection and is meant for trusted PRs. Not used on external PRs; maintainers may run
  `/security-review` locally on a trusted checkout.
- **Two AI reviewers on one PR.** No. Any available agent may run the one review (contract
  above). Two reviewers double noise and rounds; the quota failures already seen on Copilot
  reviews add noise of their own.
