package org.ergoplatform.modifiers

import io.circe.Json
import org.ergoplatform.modifiers.history.{ADProofs, BlockTransactions, Header}
import org.ergoplatform.settings.Algos
import scorex.core.NodeViewModifier.{ModifierId, ModifierTypeId}
import scorex.core.serialization.Serializer

//TODO we need it to be ErgoPersistentModifier just to put it to ProcessInfo
case class ErgoFullBlock(header: Header, blockTransactions: BlockTransactions, aDProofs: ADProofs) extends ErgoPersistentModifier {
  override val modifierTypeId: ModifierTypeId = ErgoFullBlock.modifierTypeId

  override lazy val id: ModifierId = Algos.hash(header.id ++ blockTransactions.id ++ aDProofs.id)

  override lazy val json: Json = ???

  override type M = ErgoFullBlock

  override lazy val serializer: Serializer[ErgoFullBlock] = ???
}

object ErgoFullBlock {
  val modifierTypeId: ModifierTypeId = (-127).toByte
}