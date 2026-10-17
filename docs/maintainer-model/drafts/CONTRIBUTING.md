# Contributing (DRAFT - would live at the repo root)

Thanks for helping. Maintainer time is the scarce resource, so these rules decide what gets
reviewed and in what order. They are checked automatically and published here first so they
are predictable.

## Before you write code
- **Bugs:** open an issue with the bug form, or link your fix PR to one.
- **Features and behaviour changes:** open an issue first and wait for the `S-accepted` label.
  PRs without an accepted issue are paused (`S-needs-direction`). Direction is decided on the
  issue, before code exists, so nobody's work is wasted.
- **Security problems:** never in public. See [SECURITY.md](SECURITY.md).

## Pull requests
- Target the current integration branch: `<TO CONFIRM, e.g. v6.0.9>`. `master` only receives
  release PRs.
- One change per PR. Over about 800 changed non-test lines, expect a request to split.
- Keep it current with the base branch; PRs that carry other branches' commits are paused until
  rebased or merged.
- Fill in every section of the PR template: testing with the commands you ran and their
  output, what could go wrong, how to revert, and whether a downgrade stays safe.
- At most 5 open, non-draft PRs per author waiting on maintainers. Further PRs are queued
  (`S-queued`) and picked up as earlier ones close. Drafts are not reviewed.
- Run `sbt scalafmtCheck` and the tests for the modules you touched.

## AI-assisted contributions
Welcome, with disclosure. Say in the PR which tools you used and what you verified yourself. You
are responsible for every line, as if you typed it. Automated review comments marked important
must be fixed or answered with a reason; after two rounds a maintainer takes over.

## What to expect
- Automated checks and one automated review run first; a maintainer reviews after that.
- Every production-code change is read by at least one maintainer. Changes to consensus, storage
  formats, wire formats, keys or CI need both maintainers, which takes longer.
- First response target: `<TO CONFIRM>`.
