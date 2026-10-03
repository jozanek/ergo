---
applyTo: "**"
excludeAgent: "cloud-agent"
---

# Pull request reviews in this repository

- Review every pull request with the `code-review` skill in `.github/skills/code-review/SKILL.md`, following its steps in order.
- The "Development Restrictions" section of `AGENTS.md` tells coding agents which directories they may edit. It is not a review criterion: never comment that a pull request changes `src/main/` or other production code.
- A change that alters the bytes, or the acceptance or selection rules, of serializers, validation rules, PoW, NiPoPoW, state transitions, fork choice, activation heights or P2P message types is consensus-critical: BLOCKER until the pull request answers the five questions in the skill's step 5 (see also `docs/review/protocol.md`). Editing a file in these areas is not consensus-critical by itself.
