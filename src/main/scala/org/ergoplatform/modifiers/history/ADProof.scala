package org.ergoplatform.modifiers.history

import com.google.common.primitives.Bytes
import io.circe.Json
import org.ergoplatform.modifiers.ModifierWithDigest
import org.ergoplatform.settings.{Algos, Constants}
import scorex.core.NodeViewModifier.{ModifierId, ModifierTypeId}
import scorex.core.serialization.Serializer
import scorex.crypto.encode.Base58
import ADProof.ProofRepresentation
import org.ergoplatform.modifiers.mempool.AnyoneCanSpendTransaction
import org.ergoplatform.modifiers.mempool.proposition.{AnyoneCanSpendNoncedBox, AnyoneCanSpendProposition}
import org.ergoplatform.nodeView.state.ErgoState.Digest
import scorex.core.transaction.state.BoxStateChanges

import scala.util.Try

case class ADProof(headerId: ModifierId, proofBytes: ProofRepresentation) extends HistoryModifier
  with ModifierWithDigest {

  override def digest: Array[ModifierTypeId] = ADProof.proofDigest(proofBytes)

  override val modifierTypeId: ModifierTypeId = ADProof.ModifierTypeId

  override type M = ADProof

  override lazy val serializer: Serializer[ADProof] = ADProofSerializer

  override lazy val json: Json = ???

  override def toString: String = s"ADProofs(${Base58.encode(id)},${Base58.encode(headerId)},${Base58.encode(proofBytes)})"

  def verify(changes: BoxStateChanges[AnyoneCanSpendProposition, AnyoneCanSpendNoncedBox], expectedHash: Digest): Try[Unit] = ???
}

object ADProof {
  type ProofRepresentation = Array[Byte]

  val ModifierTypeId: Byte = 104: Byte

  def proofDigest(proofBytes: ProofRepresentation): Array[Byte] = Algos.hash(proofBytes)
}

object ADProofSerializer extends Serializer[ADProof] {
  override def toBytes(obj: ADProof): Array[Byte] = Bytes.concat(obj.headerId, obj.proofBytes)

  override def parseBytes(bytes: Array[Byte]): Try[ADProof] = Try {
    ADProof(bytes.take(Constants.ModifierIdSize), bytes.slice(Constants.ModifierIdSize, bytes.length))
  }
}
