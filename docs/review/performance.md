# Performance review

Hot paths in this node are: block validation, mempool admission, peer message dispatch, mining candidate generation, and LevelDB/AVL+ access. A regression here shows up as slower sync, missed mining tips, or DoS exposure.

## Red flags

### LevelDB / AVL+ access ([avldb/](../../avldb/))

- **Per-element `put`/`get` inside a loop** instead of a batched write. LevelDB's `WriteBatch` exists for a reason; `LDBVersionedStore` / `LDBKVStore` wrap it.
- **Iterator opened but not closed.** All LevelDB iterators must be `close()`d (use `try`/`finally` or an existing `withResource`-style helper). A leaked iterator pins SSTable files and balloons memory.
- **Snapshot acquired but not released.** Same problem as iterators.
- **Reading the whole keyspace into memory** (`asScala.toList`, `.foldLeft`) instead of streaming via the iterator.
- **AVL+ proof generated inside an inner loop** when one proof for the batch would do. Proof generation is O(log n) per touched key but allocates substantial intermediate state.
- **Roothash recomputed inside a comparator or filter** — should be computed once, cached.

### Block validation hot path

The per-block path runs for every header/block during sync; allocations and redundant work compound across thousands of blocks.

- **Header/Extension/ADProofs serializer churn** in [ergo-core/.../modifiers/history/](../../ergo-core/src/main/scala/org/ergoplatform/modifiers/history/). Boxing of primitives, intermediate `Array.copy`, building a `String` just to log on the success path.
- **`scala.collection.Seq` materialisations** where an `Iterator` or `IndexedSeq` would suffice.
- **Validation step that runs unconditionally** but only matters past an activation height — gate on the height.
- **`groupGen` / curve generator allocated per call** instead of reused.

### Mempool ([nodeView/mempool/](../../src/main/scala/org/ergoplatform/nodeView/mempool/))

Mempool is the primary DoS surface — its bounds must be tight.

- **Eviction policy change** in `OrderedTxPool` that allows the pool to grow unbounded under flood, or removes a deterministic tiebreaker.
- **`ExpiringApproximateCache` / `FixedSizeApproximateCacheQueue` capacity changed or removed.** Both are bounded by design; loosening the bound is a memory DoS.
- **Linear scan of the pool inside a message handler.** The pool can hold thousands of txs — O(n) per inbound message is a stall.
- **New score / priority function** without considering monotonicity. A non-monotonic ordering can churn the pool under steady-state load.
- **Validation re-run on every re-evaluation.** Mempool re-evaluation already does the necessary work; double validation is wasted compute.

### Akka mailbox load

- **Full blocks (or larger) sent as a regular actor message** via `tell`/`ask` to a shared actor. Use the existing block-section delivery path; a fat mailbox blocks all other messages.
- **A new actor without a custom dispatcher** that does any IO. Defaults can starve other actors.
- **`Future` chained with `ask` on the actor's hot path** — each round-trip is a context switch and a timer registration.

### Mining

- **`CandidateGenerator` doing IO** (DB read, network call) on the candidate refresh loop. Refresh is invoked frequently; IO there delays nonce delivery.
- **`ErgoMiningThread` per nonce range too small.** Thread setup cost dominates if ranges are tiny.

### Extra indexer ([nodeView/history/extra/](../../src/main/scala/org/ergoplatform/nodeView/history/extra/))

- **Index entry written without batching** during reindex / catch-up. Reindex touches every box ever created — unbatched writes are minutes turning into hours.
- **Synchronous index lookup on the block-application path** when an async lookup would do.

### Allocation patterns (Scala-specific)

- **`def` returning a constant-shaped value** that callers invoke repeatedly. Should be `lazy val` or a `val` on the companion.
- **`Some(x)` / `None` re-boxed** in tight loops. Use the value-class `OptionVal` (or just pattern-match) where it matters.
- **`x.toList`/`toSeq` immediately followed by another traversal.** Each `toX` is a copy.
- **String concatenation inside log statements that the log level filters out.** Use `logger.debug(s"...")` only behind `if (logger.isDebugEnabled)` for non-trivial expressions, or use lazy-eval helpers if `ScorexLogging` provides one.

## Quick checks

```bash
# Unbatched writes in LevelDB call sites
rg -n '\.put\(' avldb/src/main/scala src/main/scala | rg -v 'WriteBatch|batch'

# Iterators not in try/finally or `using`
rg -n 'newIterator|iterator\(\)' avldb/ src/main/scala/org/ergoplatform

# Linear mempool traversal
rg -n 'toSeq|toList|foreach|filter' src/main/scala/org/ergoplatform/nodeView/mempool/
```
