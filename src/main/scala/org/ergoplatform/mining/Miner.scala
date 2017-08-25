package org.ergoplatform.mining

import org.ergoplatform.mining.difficulty.RequiredDifficulty
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.{ADProof, Header}
import org.ergoplatform.modifiers.mempool.AnyoneCanSpendTransaction
import org.ergoplatform.settings.{Algos, Constants}
import scorex.core.NodeViewModifier.ModifierId
import scorex.core.block.Block._

import scala.annotation.tailrec
import scala.util.Random

object Miner {


  def genBlock(difficulty: BigInt,
               parent: Header,
               stateRoot: Array[Byte],
               adProofs: ADProof,
               transactions: Seq[AnyoneCanSpendTransaction],
               timestamp: Timestamp): ErgoFullBlock = {
    ???
  }

  def genHeader(nBits: Long,
                parentOpt: Option[Header],
                stateRoot: Array[Byte],
                adProofsRoot: Array[Byte],
                transactionsRoot: Array[Byte],
                votes: Array[Byte],
                timestamp: Timestamp): Header = {

    val parentHeight = parentOpt.map(_.height).getOrElse(-1)
    val isGenesis = parentOpt.isEmpty
    val parentId = parentOpt.map(_.id).getOrElse(Header.GenesisParentId)

    val interlinks: Seq[Array[Byte]] = if (!isGenesis) constructInterlinkVector(parentOpt.get) else Seq()

    val difficulty = RequiredDifficulty.decodeCompactBits(nBits)
    val height = parentHeight + 1

    @tailrec
    def generateHeader(): Header = {
      val nonce = Random.nextInt
      val header = Header(0.toByte, parentId, interlinks, adProofsRoot, stateRoot, transactionsRoot, timestamp, nonce,
        nBits, height, votes)
      if (correctWorkDone(header.id, difficulty)) header
      else generateHeader()
    }
    generateHeader()
  }

  private[mining] def constructInterlinks(parentInterlinks: Seq[Array[Byte]],
                                          parentRealDifficulty: BigInt,
                                          parentId: ModifierId): Seq[Array[Byte]] = {

    val genesisId = parentInterlinks.head

    def generateInterlinks(curDifficulty: BigInt, acc: Seq[Array[Byte]]): Seq[Array[Byte]] = {
      if (parentRealDifficulty >= curDifficulty) {
        generateInterlinks(curDifficulty * 2, acc :+ parentId)
      } else {
        parentInterlinks.tail.find(pId => Algos.blockIdDifficulty(pId) >= curDifficulty) match {
          case Some(id) => generateInterlinks(curDifficulty * 2, acc :+ id)
          case _ => acc
        }
      }
    }

    val interinks = generateInterlinks(Constants.InitialDifficulty * 2, Seq[Array[Byte]]())
    assert(interinks.length >= parentInterlinks.length - 1)

    genesisId +: interinks

  }

  private def constructInterlinkVector(parent: Header): Seq[Array[Byte]] = if (parent.isGenesis) {
    Seq(parent.id)
  } else {
    constructInterlinks(parent.interlinks,
      parent.realDifficulty,
      parent.id)
  }

  def correctWorkDone(id: Array[Version], difficulty: BigInt): Boolean = {
    val target = Constants.MaxTarget / difficulty
    BigInt(1, id) < target
  }

}
