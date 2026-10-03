# Protocol / consensus review

**Default severity for findings in this file is `BLOCKER`.** A consensus or wire-format mistake is not recoverable in production — nodes running the released client either reject your blocks or refuse to sync with you. Downgrade to `MAJOR`/`MINOR` only when you can articulate why the change is wire-compatible AND behaviourally identical (or gated behind a soft-fork rule or activation height).

## Red flags

### Serializers ([ergo-core/](../../ergo-core/))

Every `*Serializer.scala` under `ergo-core/` defines on-the-wire bytes. Critical examples:

- [HeaderSerializer.scala](../../ergo-core/src/main/scala/org/ergoplatform/modifiers/history/header/HeaderSerializer.scala)
- [HistoryModifierSerializer.scala](../../ergo-core/src/main/scala/org/ergoplatform/modifiers/history/HistoryModifierSerializer.scala)
- [ExtensionSerializer.scala](../../ergo-core/src/main/scala/org/ergoplatform/modifiers/history/extension/ExtensionSerializer.scala)
- `DifficultySerializer`, `HandshakeSerializer`, transaction / box / proof serializers, `NipopowProofSerializer`, `SubtreeSerializer` / `ManifestSerializer` (avldb, UTXO snapshot chunks), …

Things to flag:

- **Field reordered, added, or removed without a protocol-version gate.** Even a "trivial" reorder breaks sync with released peers.
- **Optional field added unconditionally.** New optional fields must either be appended at a version boundary or controlled by an explicit flag byte in the existing format.
- **Length prefix size changed** (`VLQ` → `Int` or vice versa). Silent breakage.
- **Endianness or VLQ helper swapped** for a "cleaner" alternative.
- **Round-trip property test missing or weakened.** A serializer change without `forall x => deserialize(serialize(x)) == x` is unreviewable.
- **Deserializer accepts inputs the previous one rejected** (or vice versa) — that's a fork unless the change is gated.

If a wire change is genuinely needed: it must be tied to a new modifier type, a new protocol version handshake, or an activation height in the validation rules below. Document which in the PR.

### Validation rules ([ValidationRules.scala](../../ergo-core/src/main/scala/org/ergoplatform/settings/ValidationRules.scala), `ErgoValidationSettings`)

Consensus rules carry a stable numeric `RuleId` and ship with the soft-fork machinery — `rulesToDisable` is how operators retroactively turn them off. Get this wrong and you've forked the chain silently.

- **New validation rule without a stable `RuleId`** assigned and entered in the rules table.
- **`RuleId` reused or reassigned.** They are immutable once shipped — pick the next free id.
- **Rule semantics changed without an activation height** (or without making the change strictly more permissive in a way that's safe).
- **Rule removed entirely** instead of being added to the deprecated/disabled set.
- **`rulesToDisable` config touched** without considering how operators with the previous config layout will load (see the JVM-`-D` HOCON-array stringification gotcha in [testing.md](testing.md)).

### Autolykos PoW ([ergo-core/.../mining/](../../ergo-core/src/main/scala/org/ergoplatform/mining/))

- **Any change to `AutolykosPowScheme`, `AutolykosSolution`, or the verification path** that alters how `(N, k)` are derived, how the message-hash is computed, or how the nonce is checked.
- **Difficulty / target conversion changed** (`DifficultySerializer` is in scope here too).
- **Autolykos v1/v2 selection** (by `header.version` in `AutolykosPowScheme`) changed for a height range without explicit activation logic.

### NiPoPoW ([ergo-core/.../popow/](../../ergo-core/src/main/scala/org/ergoplatform/modifiers/history/popow/), `PopowProcessor`)

- **Superchain level computation altered** — even a one-off changes prover/verifier symmetry.
- **Suffix-proof length constants changed.**
- **Verifier accepts a proof shape the prover never produces** (or vice versa). Prove symmetry with paired property tests.

### State transitions

- **`UtxoState` change that affects which inputs are accepted** at a given height.
- **`DigestState` ↔ `UtxoState` semantics drift.** A digest node and a full node must accept exactly the same blocks; any diff in the validation surface is a consensus split.
- **ADProofs construction / verification changed.** Roothash determinism is non-negotiable.
- **Box / token / asset accounting logic touched** — re-derive the invariants, don't trust the diff.

### Block-version / activation gates

- **Behaviour change not gated on `header.version` or activation height** when the chain has historical blocks at the old version.
- **`BlockVersion` constant bumped** without paired prover code and miners' awareness.
- **Activation-height constant changed** in `application.conf` / network-specific configs ([src/main/resources/mainnet.conf](../../src/main/resources/mainnet.conf), `testnet.conf`, `devnet.conf`). Mainnet activation changes are operator-coordinated events.

### Genesis / emission / re-emission ([ergo-core/.../reemission/](../../ergo-core/src/main/scala/), `EmissionApiRoute`)

- **Emission curve coefficients or thresholds changed.**
- **Re-emission rules altered** without a clear hard-fork plan.

### Network protocol ([src/main/scala/org/ergoplatform/network/](../../src/main/scala/org/ergoplatform/network/))

- **New peer-message type without a feature-flag / version handshake** so older peers know to ignore it.
- **Existing message ID reused for a new shape.**
- **Magic bytes / network ID changed** for mainnet without coordinated upgrade.

### Block application through `ErgoNodeViewHolder`

Only `ErgoNodeViewHolder` writes `ErgoHistory` and `ErgoState`; `restoreConsistentState` reconciles them on restart (see [architecture.md](architecture.md)). A new code path writing either from outside the holder can leave a state the restart cannot reconcile — `BLOCKER` unless the PR shows the write goes through the holder or is rebuilt on restart.

## What "wire-compatible" means in a PR description

When a serializer/validator change is unavoidable, the PR should explicitly answer:

1. What's the new on-disk / on-wire byte sequence, or the new acceptance rule?
2. Can a node running the previous release still parse and accept it (forward)?
3. Can this node still parse and accept what the previous release produced (backward)?
4. If either is `no`: what's the activation gate (height / version / rule id)?
5. Where is the round-trip property test that proves (1)–(3)?

Absence of a clear answer = `BLOCKER`.
