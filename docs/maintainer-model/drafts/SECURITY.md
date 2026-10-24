# Security policy (DRAFT - would live at the repo root)

## Report privately

Do **not** open a public issue, pull request or discussion for a vulnerability, and do not post
details in chat.

Use GitHub private vulnerability reporting: "Report a vulnerability" on this repository's
Security tab. It is the only channel.

No bot or AI agent handles these reports. The repository admins read them.

## What must be reported privately

Anything a remote party could trigger or exploit, for example:
- crashing, stalling or exhausting memory, CPU, disk or bandwidth of a node (DoS);
- making nodes disagree about validity (chain split) or accept invalid blocks/transactions;
- loss or theft of funds, exposure of keys, seeds or wallet secrets;
- bypassing REST API authentication or widening access via defaults/CORS;
- getting honest peers banned or isolated.

General hardening with no remote trigger can be a normal public PR. If unsure, report privately.

## What happens next

| Step | Target |
|---|---|
| Acknowledgement | 3 working days |
| Initial assessment and severity | 10 working days (proposed) |
| Fix and coordinated release | depends on severity; agreed with the reporter |
| Public disclosure | after a fixed release has had time to reach operators |

Reporters are credited unless they ask not to be.

If a report was already posted publicly, maintainers may hide comments, lock the thread, or move
it to a private repository while the fix is prepared.

## Supported versions

The latest 6.0.x release.
