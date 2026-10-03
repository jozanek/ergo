# Architecture review

Module boundaries, actor wiring, HTTP API conventions, sync-mode coverage. The node is an Akka actor system: `ErgoNodeViewHolder` owns history, state, wallet and mempool; `ErgoNodeViewSynchronizer` drives block and header propagation; one `*ApiRoute` per HTTP domain.

## Red flags

### HTTP API

- **New `*ApiRoute` does not extend `ErgoBaseApiRoute`.** All routes in [src/main/scala/org/ergoplatform/http/api/](../../src/main/scala/org/ergoplatform/http/api/) extend it for shared JSON marshalling, error handling, and rejection conversion. A route that builds its own `Directives` stack will drift from the others.
- **New endpoint or response shape without an `openapi.yaml` change.** The spec at [src/main/resources/api/openapi.yaml](../../src/main/resources/api/openapi.yaml) is authoritative — clients regenerate from it.
- **JSON codecs defined inline in a route** instead of in `ApiRequestsCodecs` / `ApiCodecs` etc. New `Encoder`/`Decoder` instances should live next to the existing ones.
- **Endpoint that mutates node state via direct calls** (e.g. directly writing to wallet storage) rather than sending an actor message through `ErgoNodeViewHolder` or the wallet actor.

### Block application through `ErgoNodeViewHolder`

`ErgoNodeViewHolder` ([src/main/scala/org/ergoplatform/nodeView/ErgoNodeViewHolder.scala](../../src/main/scala/org/ergoplatform/nodeView/ErgoNodeViewHolder.scala)) owns `ErgoHistory`, `ErgoState`, `ErgoWallet` and `ErgoMemPool`. Only the holder writes history and state; on restart `restoreConsistentState` reconciles state to history. The wallet (via messages to `ErgoWalletActor`), the in-memory mempool, and `ExtraIndexer`'s extra-index rows are updated outside the holder by design.

- **History or state written from outside the holder** (a new API route, a new actor). Restart recovery can only reconcile what the holder wrote.
- **New `NodeViewModifier` flow that bypasses the holder.** Block sections must flow through the holder.
- **Wallet, mempool or extra-index update without rollback and restart handling.** These run outside the holder, so each needs its own answer for a rollback, a reorg, and a crash mid-update.

### Module dependency direction

The build is layered: `ergo` (node app) → `ergo-core` → {`ergo-wallet`, `avldb`}. `ergo-core`, `ergo-wallet` and `avldb` cross-build for Scala 2.12 and 2.13; the node `src/` builds for 2.12 only.

- **Reverse-direction dependency added** in `build.sbt` (e.g. `ergo-core` depending on the node `src/`).
- **Scala 2.13-only API used** in `ergo-core`, `ergo-wallet`, or `avldb`. Examples: `LazyList`, `.to(Vector)` literal, `Using.resource`, `IterableOnce` collection ops. These break the 2.12 build.
- **Library version bumped to one not published for both 2.12 and 2.13** in a cross-compiled module.
- **Code that should live in `ergo-core` placed in the node `src/`** — anything used by SPV clients (P2P messages, block-section structures, PoW, NiPoPoWs) belongs in `ergo-core`.

### Sync-mode coverage

The node supports these sync modes:

1. Full sync (default)
2. UTXO snapshot bootstrap (`node.utxo.utxoBootstrap = true`)
3. NiPoPoW bootstrap (`node.nipopow.nipopowBootstrap = true`)
4. Digest / stateless (`node.stateType = "digest"`)
5. Pruned history (`node.blocksToKeep >= 0`, usually with digest; `FullBlockPruningProcessor`)

A change to history, state, or the sync flow must work in all of them. The diff should either touch the relevant code paths for each or argue why a mode is unaffected.

- **A new history/state code path with a code branch only for `UtxoState`** but no handling for `DigestState`.
- **Bootstrap code path changed** without considering whether the snapshot/proof on which the new node started up provides the data the change needs.
- **A new validation step that requires the full UTXO set**, used unconditionally, breaks digest mode.

### Configuration

- **New config key without a default in [src/main/resources/application.conf](../../src/main/resources/application.conf).** Reading an absent key throws at startup.
- **Config key documented only in `devnetTemplate.conf`** — the file is not loaded for users, it's a test template.
- **Renamed config key without back-compat.** Operators' existing `application.conf` will silently miss the value.
- **HOCON path moved** between sections (`scorex.*` ↔ `ergo.*` ↔ `node.*`) without a migration note.

### Logging and error handling

- **A new component that does not extend `ScorexLogging`** for logging. Direct SLF4J/log4j calls bypass the project's logger conventions.
- **A new public method without a return type annotation.** Configured in `scalastyle-config.xml` (`PublicMethodsHaveTypeChecker`) — the inferred type can change silently across refactors.
- **`throw new ...` in production code.** Use `Try` / `Either` / `ValidationResult` instead.

## Quick grep helpers

```bash
# Routes that don't extend ErgoBaseApiRoute
rg -l 'class.*Route' src/main/scala/org/ergoplatform/http/api/ | \
  xargs rg -L 'extends ErgoBaseApiRoute'

# Direct calls into the four NodeView components from outside the holder
rg 'history\.|state\.|wallet\.|mempool\.' src/main/scala/org/ergoplatform/http/api/

# Scala 2.13-only APIs in cross-compiled modules
rg -n 'LazyList|IterableOnce|Using\.resource' ergo-core/ ergo-wallet/ avldb/
```
