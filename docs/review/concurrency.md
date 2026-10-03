# Concurrency review

The node is an Akka actor system. Most concurrency bugs here come from blocking inside actors, sharing mutable state across actor and `Future` boundaries, or assuming message ordering that Akka does not guarantee.

## Red flags

### Blocking inside actors

An actor's `receive` runs on a shared dispatcher thread. Blocking it starves every other actor scheduled on the same dispatcher.

- **`Await.result` / `Await.ready` inside `receive`** — even with a short timeout. Use `pipeTo(self)` to deliver the result as a message instead.
- **`Thread.sleep` anywhere in actor code.** Use `context.system.scheduler.scheduleOnce`.
- **Blocking JDBC / file / network call** inside `receive`. Move it to a `Future` on a dedicated dispatcher and `pipeTo` the result.
- **Synchronous LevelDB iteration of unbounded size** inside `receive`. Bounded iteration is fine; "walk the whole history" is not.

### `Future` and `ExecutionContext`

- **`scala.concurrent.ExecutionContext.Implicits.global` imported in node code.** Use the actor system's dispatcher or a named dispatcher; `global` mixes node work with whatever else the JVM has on it.
- **`Future { ... }` without an explicit EC** in a file where multiple ECs are in scope — relying on implicit resolution is brittle.
- **CPU-bound work submitted to `actor.dispatcher`.** Use a dedicated thread pool for crypto / signing / proof work so it doesn't pre-empt message processing.
- **`Future.sequence` over a large collection** without a parallelism bound. Bound it with `Source(...).mapAsync(n)` or a chunked approach.

### Actor anti-patterns

- **`sender()` captured inside a `Future` callback.** `sender()` is mutable — by the time the callback runs, it points at whoever sent the *next* message. Save it to a `val` first.
- **`var` or mutable collection inside an Actor leaked into a `Future` closure.** The `Future` runs on a different thread; reads/writes from inside its callback race against `receive`.
- **`become` chain without `unbecome` or a fall-through.** Actors silently swallow unhandled messages — easy to leave a behavior stuck.
- **`Stash` used without `unstashAll`** at the transition. The stash grows unboundedly.
- **Long-running `receiveTimeout`** as a state-machine clock. Use `Timers`.
- **`scheduler.schedule*` handle discarded in `preStart`.** The default `postRestart` re-runs `preStart`, so each restart adds another timer, and nothing cancels them on stop (a closure-based schedule keeps running after the actor dies). Use `Timers` (`timers.startTimerWithFixedDelay`), which Akka cancels on stop and restart.
- **New `.get` on a `Try`/`Option`, or a new throw, on a path `ErgoNodeViewHolder` runs (including the health check).** The holder is a top-level actor, so the default guardian strategy restarts it: `postStop` closes history and state, the message being processed is lost, and the view is reloaded from disk. (Its own `supervisorStrategy` covers only its children.) Ask whether that is intended or the failure should stay a `Try`.

### Inter-actor protocol

- **New message added to `ErgoNodeViewHolder`, `ErgoNodeViewSynchronizer`, `ErgoMiner`, `PeerManager`, or the wallet actor without updating the receiver's case match.** Akka logs a "unhandled message" warning at runtime, not a compile error.
- **`tell`/`ask` ordering assumed across actors.** Akka guarantees ordering only between a single sender/receiver pair on the same dispatcher. If actor A sends `M1` to B and `M2` to C, and B forwards to C, the relative order of `M1` (via B) and `M2` (direct) is not defined.
- **Reply expected for a `tell`-only message.** `tell` is fire-and-forget; if the protocol needs a response, use `ask` (and handle the timeout) or a typed reply message.
- **`ask` without an explicit `Timeout`.** Picks up whatever implicit is in scope — often the default `5.seconds` — which is wrong for slow IO.

### Wallet and signing

- **Signing performed on the wallet actor's thread synchronously** when the diff makes it slower (e.g. new context resolver IO). Sign asynchronously and `pipeTo`.
- **Wallet state observed across multiple messages** assuming it didn't change between them. Snapshot once at the start.

## Quick checks

```bash
# Blocking primitives anywhere in src/main
rg -n 'Await\.(result|ready)|Thread\.sleep' src/main/scala

# global EC imports
rg -n 'ExecutionContext\.Implicits\.global|import.*global' src/main/scala

# sender() in a Future closure (heuristic)
rg -n -A2 'Future\s*\{' src/main/scala | rg 'sender\(\)'

# Unhandled messages risk: receive blocks ending without a fall-through
rg -n 'receive: Receive' src/main/scala
```

Remember: a sync `Future.successful(...)` is fine on the actor thread. The problem is awaiting an *unfinished* future or doing the slow work synchronously.
