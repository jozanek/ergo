<!-- DRAFT - would live at .github/pull_request_template.md -->
<!-- Security issue? Stop. Do not open a public PR. See SECURITY.md. -->

## What and why
<!-- One or two sentences. Link the issue: "Fixes #123". Features need an issue labelled S-accepted. -->

## Base branch
<!-- Which branch and why (normally the current integration branch). -->

## What could go wrong
<!-- The worst realistic outcome if this is wrong, and who would notice. -->

## Testing
<!-- Commands you ran and their output (paste it). For a bug fix: a test that fails without the fix. -->

## Revert and downgrade
<!-- How to undo this. Does it change on-disk formats, schema versions, P2P messages, REST API, or
     validity rules? If yes, can a node downgrade to the previous release afterwards? -->
- [ ] Changes what nodes accept (validity/consensus)
- [ ] Changes an on-disk format or schema version
- [ ] Changes a P2P message or wire format
- [ ] Changes REST API behaviour, auth or defaults
- [ ] None of the above

## Depends on / overlaps
<!-- Other PRs this needs merged first, or touches the same code as. -->