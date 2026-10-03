# SWE-hygiene checklist

Catch-all for style, configuration back-compat, logging, and CI items. None of these are the headline of a review, but they accumulate.

## Code style (`.scalafmt.conf`, `scalastyle-config.xml`)

- `println` in production code. Should be a `ScorexLogging` call.
- Non-`ScorexLogging` logger used (raw SLF4J, log4j). All node components extend `ScorexLogging`.
- `throw new X` in production code. Use `Try` / `Either` / `ValidationResult`.
- Wildcard import (`import x._`) added in new code. The project convention is none, but no tool enforces it and existing code has many, so `MINOR` at most.
- Unsorted imports.
- Line longer than 160 chars; file longer than 800 lines (`scalastyle-config.xml`, warning level).
- `maxColumn` violation (.scalafmt.conf is 90) — `sbt scalafmtCheck` would have caught this if run.
- Public method without an explicit return type annotation.
- Public class / object missing a one-line docstring when its purpose isn't obvious from the name.
- `var` outside an Actor's private state.
- Mutable collection (`mutable.Map`, `mutable.Buffer`, …) used where an immutable one would work.
- `Any` / `AnyRef` in a public signature where a concrete type exists.

## API surface

- HTTP endpoint added, removed, or its response shape changed without a matching diff in [src/main/resources/api/openapi.yaml](../../src/main/resources/api/openapi.yaml).
- Backwards-incompatible response change without a version bump path.
- New endpoint not authenticated when its siblings are (or vice versa).

## Configuration back-compat

- New config key without a default in [src/main/resources/application.conf](../../src/main/resources/application.conf).
- Existing config key renamed, moved, or its type changed — operators' `application.conf` will silently break.
- Network-specific defaults changed in [mainnet.conf](../../src/main/resources/mainnet.conf) / `testnet.conf` / `devnet.conf` without a release-note entry.
- Documented config keys whose docs don't match the new behaviour.

## Build / CI ([.github/workflows/ci.yml](../../.github/workflows/ci.yml))

- New module added to `build.sbt` but not wired into the aggregate root project.
- Cross-build target changed in a module that ships to 2.12 / 2.13.
- New dependency that overlaps with an existing one — version drift.
- New dependency licensed incompatibly (GPL into a non-GPL module).
- CI workflow change that skips or weakens a previously-required check.

## Documentation

- New top-level module / directory without a brief comment describing its responsibility.
- README / docs reference a config key, endpoint, or behaviour the diff just removed or changed.
- `papers/` referenced from prose but link broken / file renamed.

## CI

CI ([.github/workflows/ci.yml](../../.github/workflows/ci.yml)) runs `ergoWallet/test`, `ergoCore/test`, `test` and `it:test` — not `scalafmtCheck` or scalastyle. Don't ask whether the author ran them; cite the CI result if it has finished. Flag a workflow change that stops running one (see Build / CI above).

## PR description sanity

- Title describes the change, not the area ("fix off-by-one in NiPoPoW suffix length" beats "NiPoPoW updates").
- Body explains *why*. Reviewer should not have to reverse-engineer the motivation.
- Breaking changes called out explicitly (config keys, API shape, on-disk format).
- Any consensus / protocol concern surfaced with the answers from [protocol.md](protocol.md) (wire-compat, activation gate, round-trip test location).
