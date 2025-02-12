package org.ergoplatform.bench.misc

import java.io.{InputStream, OutputStream}
import com.google.common.primitives.Ints
import org.ergoplatform.Utils._
import org.ergoplatform.modifiers.{BlockSection, NetworkObjectTypeId}
import org.ergoplatform.modifiers.history._
import org.ergoplatform.modifiers.history.header.{Header, HeaderSerializer}
import scorex.core.serialization.ScorexSerializer
import scorex.core.NodeViewModifier

object ModifierWriter {

  val modifierSerializers: Map[NetworkObjectTypeId.Value, ScorexSerializer[_ <: BlockSection]] =
    Map(Header.modifierTypeId -> HeaderSerializer,
      BlockTransactions.modifierTypeId -> BlockTransactionsSerializer,
      ADProofs.modifierTypeId -> ADProofsSerializer)

  def write(mod: NodeViewModifier)(implicit fos: OutputStream): Unit = {
    val typeId = mod.modifierTypeId
    val bytes = mod.bytes
    val length: Int = bytes.length
    val lengthBytes = Ints.toByteArray(length)
    val bytesToWrite = typeId +: (lengthBytes ++ bytes)
    fos.write(bytesToWrite)
    fos.flush()
  }

  def read(implicit fis: InputStream): Option[BlockSection] = for {
    typeId <- readModId
    length <- readLength
    bytes <- readBytes(length)
    mod <- modifierSerializers(typeId).parseBytesTry(bytes).toOption
  } yield mod

  private def readModId(implicit fis: InputStream): Option[NetworkObjectTypeId.Value] = {
    val int = fis.read()
    if (int == -1) { None } else { Some(NetworkObjectTypeId.fromByte(int.toByte)) }
  }

}
