# Testing review

The unit/property test infrastructure lives in [src/test/scala/org/ergoplatform/utils/](../../src/test/scala/org/ergoplatform/utils/) (`ErgoTestHelpers`, `HistoryTestHelpers`, `MempoolTestHelpers`, `WalletTestOps`, `generators/`). Integration tests (`it:test`) spin up Docker containers and have a well-known set of footguns, listed below.

## Red flags — unit / property tests

- **New spec rolls its own setup** instead of extending one of the existing helpers (`ErgoTestHelpers`, `HistoryTestHelpers`, `MempoolTestHelpers`, `WalletTestOps`). The helpers wire up clocks, settings, generators, and key material — reinventing them invites drift.
- **New protocol type lacks a `Gen`** in [src/test/scala/org/ergoplatform/utils/generators/](../../src/test/scala/org/ergoplatform/utils/generators/). Property tests downstream will need it.
- **A non-trivial change without a property test** for the new function/method. ScalaCheck via `ScalaCheckPropertyChecks` is the project's default style; a hand-rolled "table of three examples" misses the cases properties would catch.
- **Test mocks the database / storage** instead of using a real LevelDB store under `createTempDir`, as `HistoryTestHelpers` and `Stubs` do. Mocked storage hides real LevelDB semantics (iterator close, snapshot release, batch atomicity).
- **A new `*Serializer.scala`** without a round-trip property test (`forall x => deserialize(serialize(x)) == x`).
- **`ignored`, `pending`, or `cancel()` left in the spec.** These pass CI silently.
- **Test depends on wall-clock time** (`System.currentTimeMillis`) instead of an injected clock. Flaky in CI.

## Red flags — integration tests (`it:test`)

Flag anything that re-introduces these gotchas:

- **Production code changed but no note to run `sbt docker` first.** `it:test` rebuilds the image; `it:testOnly *Spec` does not — without the rebuild, containers run a stale JAR.
- **Spec depends on devnet launch parameters but does not set `ergo.networkType = "devnet"`** in its per-node config. The fallback in `application.conf` is `testnet`; `--devnet` is not passed and `devnet.conf` is not loaded automatically.
- **`-D` JVM override used for an int or HOCON array** that the code reads via `.render().toInt` (gets the quoted string and fails) or expects as a typed array (becomes an indexed object `rulesToDisable.1=215`). Use `config.getInt(path)` / `.unwrapped().toString()`, or put the value in `devnetTemplate.conf` where types are preserved.
- **Multi-miner spec without `onlineGeneratingPeerConfig` on followers.** `devnetTemplate.conf` sets `node.offlineGeneration = true`, which causes parallel forks at startup. Either: node 0 bootstraps alone and followers use `mining=true, offlineGeneration=false`; or single miner with `mining=false` on every follower.
- **Fast block production (sub-second polling) with multiple miners.** Docker-network gossip can't keep them in sync at that speed — persistent forks. Use single-miner topology.
- **Waiting until every node reaches height H, then reading `/info` immediately.** The slowest node reaching H means the seed has already raced past — `/info` returns the *next* epoch's parameters. Fix: `waitForHeight` on the seed, then poll `/info` until `parameters.height >= H`; keep all-node waits for final consistency checks.
- **Asserting `headerIdsByHeight` consistency at the chain tip** instead of tip-minus-K. Nodes briefly disagree at the tip; compare a few blocks below it. (Only matters with more than one miner.)
- **New integration test added to the default `it:test` set** when it actually belongs in `it2:test` (bootstrapping/mainnet sync) — or vice versa.

## Red flags — coverage and signal

- **Test asserts only the happy path.** Validation errors, eviction edge cases, fork-resolution branches should each have a case.
- **Test extends a base trait but overrides `actorSystem` / dispatcher** in a way that diverges from the rest of the suite. Suggests the test is fighting the harness.
- **Test passes by changing the assertion** rather than fixing the code. Look for relaxed bounds, `>=` becoming `>` or vice versa, expected values fudged.
- **No test added for a non-trivial bug fix.** A regression test should accompany every fix.
- **Test would pass without the fix**: it never reaches the changed path (inputs below a sampling or batch threshold, a stub instead of the real wiring), or it doesn't assert the behaviour the PR claims.

## Quick checks

```bash
# Pending / ignored tests
rg -n 'ignored|pending|cancel\(' src/test/scala src/it

# Tests not extending the standard helpers (worth a look, not always wrong)
rg -L 'ErgoTestHelpers|HistoryTestHelpers|MempoolTestHelpers|WalletTestOps' \
   $(rg -l 'class.*Spec' src/test/scala)

# Wall-clock dependencies
rg -n 'System\.currentTimeMillis|new java\.util\.Date' src/test/scala src/it
```
