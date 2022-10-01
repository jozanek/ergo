package org.ergoplatform.nodeView.history

import java.io.File

import org.ergoplatform.ErgoLikeContext
import org.ergoplatform.mining.AutolykosPowScheme
import org.ergoplatform.modifiers.history._
import org.ergoplatform.modifiers.history.header.{Header, PreGenesisHeader}
import org.ergoplatform.modifiers.state.UTXOSnapshotChunk
import org.ergoplatform.modifiers.{BlockSection, ErgoFullBlock, NonHeaderBlockSection}
import org.ergoplatform.nodeView.history.storage.HistoryStorage
import org.ergoplatform.nodeView.history.storage.modifierprocessors._
import org.ergoplatform.nodeView.history.storage.modifierprocessors.popow.{EmptyPoPoWProofsProcessor, FullPoPoWProofsProcessor}
import org.ergoplatform.settings._
import org.ergoplatform.utils.LoggingUtil
import scorex.core.consensus.ModifierSemanticValidity.Invalid
import scorex.core.consensus.ProgressInfo
import scorex.core.utils.NetworkTimeProvider
import scorex.core.validation.RecoverableModifierError
import scorex.util.{ModifierId, ScorexLogging, idToBytes}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

/**
  *
  * History of a blockchain system is some blocktree in fact
  * (like this: http://image.slidesharecdn.com/sfbitcoindev-chepurnoy-2015-150322043044-conversion-gate01/95/proofofstake-its-improvements-san-francisco-bitcoin-devs-hackathon-12-638.jpg),
  * where longest chain is being considered as canonical one, containing right kind of history.
  *
  * In cryptocurrencies of today blocktree view is usually implicit, means code supports only linear history,
  * but other options are possible.
  *
  * To say "longest chain" is the canonical one is simplification, usually some kind of "cumulative difficulty"
  * function has been used instead.
  *
  * History implementation. It is processing persistent modifiers generated locally or coming from the network.
  * Depending on chosen node settings, it will process modifiers in a different way, different processors define how to
  * process different type of modifiers.
  *
  * HeadersProcessor: processor of block headers. It's the same for all node settings
  * ADProofsProcessor: processor of ADProofs. ADProofs may
  *   1. Be downloaded from other nodes (ADState == true)
  *   2. Be calculated by using local state (ADState == false)
  *   3. Be ignored by history in light mode (verifyTransactions == false)
  * PoPoWProofsProcessor: processor of PoPoWProof. PoPoWProof may
  *   1. Be downloaded once during bootstrap from other peers (poPoWBootstrap == true)
  *   2. Be ignored by history (poPoWBootstrap == false)
  * BlockTransactionsProcessor: Processor of BlockTransactions. BlockTransactions may
  *   1. Be downloaded from other peers (verifyTransactions == true)
  *   2. Be ignored by history (verifyTransactions == false)
  */
trait ErgoHistory
  extends ErgoHistoryReader {

  override protected lazy val requireProofs: Boolean = nodeSettings.stateType.requireProofs

  def closeStorage(): Unit = historyStorage.close()

  /**
    * Dump modifier identifier and bytes to database.
    *
    * Used to dump ADProofs generated locally.
    *
    * @param mId - modifier identifier
    * @param bytes - modifier bytes
    * @return Success if modifier inserted into database successfully, Failure otherwise
    */
  def dumpToDb(mId: Array[Byte], bytes: Array[Byte]): Try[Unit] = {
    historyStorage.insert(mId, bytes)
  }

  /**
    * Append ErgoPersistentModifier to History if valid
    */
  def append(modifier: BlockSection): Try[(ErgoHistory, ProgressInfo[BlockSection])] = synchronized {
    log.debug(s"Trying to append modifier ${modifier.encodedId} of type ${modifier.modifierTypeId} to history")
    applicableTry(modifier).flatMap { _ =>
      modifier match {
        case header: Header =>
          process(header)
        case section: NonHeaderBlockSection =>
          process(section)
        case poPoWProof: NipopowProofModifier =>
          process(poPoWProof)
        case chunk: UTXOSnapshotChunk =>
          process(chunk)
      }
    }.map(this -> _).recoverWith { case e =>
      if (!e.isInstanceOf[RecoverableModifierError]) {
        log.warn(s"Error while applying modifier ${modifier.encodedId} of type ${modifier.modifierTypeId}, " +
          s"reason: ${LoggingUtil.getReasonMsg(e)} ")
      }
      Failure(e)
    }
  }

  /**
    * Mark modifier as valid
    */
  def reportModifierIsValid(modifier: BlockSection): Try[ErgoHistory] = synchronized {
    log.debug(s"Modifier ${modifier.encodedId} of type ${modifier.modifierTypeId} is marked as valid ")
    modifier match {
      case fb: ErgoFullBlock =>
        val nonMarkedIds = (fb.header.id +: fb.header.sectionIds.map(_._2))
          .filter(id => historyStorage.getIndex(validityKey(id)).isEmpty)

        if (nonMarkedIds.nonEmpty) {
          historyStorage.insert(
            nonMarkedIds.map(id => validityKey(id) -> Array(1.toByte)),
            Nil).map(_ => this)
        } else {
          Success(this)
        }
      case _ =>
        historyStorage.insert(
          Array(validityKey(modifier.id) -> Array(1.toByte)),
          Nil).map(_ => this)
    }
  }

  /**
    * Mark modifier and all modifiers in child chains as invalid
    *
    * @param modifier that is invalid from State point of view
    * @return ProgressInfo with next modifier to try to apply
    */
  @SuppressWarnings(Array("OptionGet", "TraversableHead"))
  def reportModifierIsInvalid(modifier: BlockSection,
                              progressInfo: ProgressInfo[BlockSection]
                             ): Try[(ErgoHistory, ProgressInfo[BlockSection])] = synchronized {
    log.warn(s"Modifier ${modifier.encodedId} of type ${modifier.modifierTypeId} is marked as invalid")
    correspondingHeader(modifier) match {
      case Some(invalidatedHeader) =>
        val invalidatedHeaders = continuationHeaderChains(invalidatedHeader, _ => true).flatten.distinct
        val invalidatedIds = invalidatedHeaders.map(_.id).toSet
        val validityRow = invalidatedHeaders.flatMap(h => Seq(h.id, h.transactionsId, h.ADProofsId)
          .map(id => validityKey(id) -> Array(0.toByte)))
        log.info(s"Going to invalidate ${invalidatedHeader.encodedId} and ${invalidatedHeaders.map(_.encodedId)}")
        val bestHeaderIsInvalidated = bestHeaderIdOpt.exists(id => invalidatedIds.contains(id))
        val bestFullIsInvalidated = bestFullBlockIdOpt.exists(id => invalidatedIds.contains(id))
        (bestHeaderIsInvalidated, bestFullIsInvalidated) match {
          case (false, false) =>
            // Modifiers from best header and best full chain are not involved, no rollback and links change required
            historyStorage.insert(validityRow, Nil).map { _ =>
              this -> ProgressInfo[BlockSection](None, Seq.empty, Seq.empty, Seq.empty)
            }
          case _ =>
            // Modifiers from best header and best full chain are involved, links change required
            val newBestHeaderOpt = loopHeightDown(headersHeight, id => !invalidatedIds.contains(id))

            if (!bestFullIsInvalidated) {
              //Only headers chain involved
              historyStorage.insert(
                newBestHeaderOpt.map(h => BestHeaderKey -> idToBytes(h.id)).toSeq,
                Seq.empty
              ).map { _ =>
                this -> ProgressInfo[BlockSection](None, Seq.empty, Seq.empty, Seq.empty)
              }
            } else {
              val invalidatedChain: Seq[ErgoFullBlock] = bestFullBlockOpt.toSeq
                .flatMap(f => headerChainBack(fullBlockHeight + 1, f.header, h => !invalidatedIds.contains(h.id)).headers)
                .flatMap(getFullBlock)
                .ensuring(_.lengthCompare(1) >= 0, "invalidatedChain should contain at least bestFullBlock")

              val genesisInvalidated = invalidatedChain.lengthCompare(1) == 0
              val branchPointHeader = if (genesisInvalidated) PreGenesisHeader else invalidatedChain.head.header

              val validHeadersChain =
                continuationHeaderChains(branchPointHeader,
                  h => getFullBlock(h).isDefined && !invalidatedIds.contains(h.id))
                  .maxBy(_.lastOption.flatMap(x => scoreOf(x.id)).getOrElse(BigInt(0)))

              val validChain = validHeadersChain.tail.flatMap(getFullBlock)

              val chainStatusRow = validChain.map(b =>
                FullBlockProcessor.chainStatusKey(b.id) -> FullBlockProcessor.BestChainMarker) ++
                invalidatedHeaders.map(h =>
                  FullBlockProcessor.chainStatusKey(h.id) -> FullBlockProcessor.NonBestChainMarker)

              val changedLinks = validHeadersChain.lastOption.map(b => BestFullBlockKey -> idToBytes(b.id)) ++
                newBestHeaderOpt.map(h => BestHeaderKey -> idToBytes(h.id)).toSeq
              val toInsert = validityRow ++ changedLinks ++ chainStatusRow
              historyStorage.insert(toInsert, Seq.empty).map { _ =>
                val toRemove = if (genesisInvalidated) invalidatedChain else invalidatedChain.tail
                this -> ProgressInfo(Some(branchPointHeader.id), toRemove, validChain, Seq.empty)
              }
            }
        }
      case None =>
        //No headers become invalid. Just mark this modifier as invalid
        log.warn(s"Modifier ${modifier.encodedId} of type ${modifier.modifierTypeId} is missing corresponding header")
        historyStorage.insert(Array(validityKey(modifier.id) -> Array(0.toByte)), Nil).map { _ =>
          this -> ProgressInfo[BlockSection](None, Seq.empty, Seq.empty, Seq.empty)
        }
    }
  }

  /**
    * @return header, that corresponds to modifier
    */
  protected def correspondingHeader(modifier: BlockSection): Option[Header] = modifier match {
    case h: Header => Some(h)
    case full: ErgoFullBlock => Some(full.header)
    case proof: ADProofs => typedModifierById[Header](proof.headerId)
    case txs: BlockTransactions => typedModifierById[Header](txs.headerId)
    case _ => None
  }

  /**
    * Remove header, corresponding block parts, and corresponding indexes from storage and caches
    * @param headerId - header id
    * @return
    */
  def forgetHeader(headerId: ModifierId): Try[Unit] = Try {
    val hOpt = typedModifierById[Header](headerId)
      val hRes = historyStorage.remove(
        indicesToRemove = Array(validityKey(headerId), headerHeightKey(headerId), headerScoreKey(headerId)),
        idsToRemove = Array(headerId)
      )
    log.info(s"Result of removing header $headerId: " + hRes)

    hOpt.foreach { h =>
      h.sectionIds.foreach { case (_, mId) =>
        val mRes = historyStorage.remove(
          indicesToRemove = Array(validityKey(mId)),
          idsToRemove = Array(mId)
        )
        log.info(s"Result of removing modifier $mId: " + mRes)
      }
    }
  }

  /**
    * @return read-only copy of this history
    */
  def getReader: ErgoHistoryReader = this

}

object ErgoHistory extends ScorexLogging {

  type Height = ErgoLikeContext.Height // Int
  type Score = BigInt
  type Difficulty = BigInt
  type NBits = Long

  val CharsetName = "UTF-8"

  val EmptyHistoryHeight: Int = 0
  val GenesisHeight: Int = EmptyHistoryHeight + 1 // first block has height == 1

  def heightOf(headerOpt: Option[Header]): Int = headerOpt.map(_.height).getOrElse(EmptyHistoryHeight)

  def historyDir(settings: ErgoSettings): File = {
    val dir = new File(s"${settings.directory}/history")
    dir.mkdirs()
    dir
  }

  // check if there is possible database corruption when there is header after
  // recognized blockchain tip marked as invalid
  protected[nodeView] def repairIfNeeded(history: ErgoHistory): Unit = history.historyStorage.synchronized {
    val RepairDepth = 128

    val bestHeaderHeight = history.headersHeight

    @tailrec
    def checkHeightsFrom(h: Int): Unit = {
      val headerIds = history.headerIdsAtHeight(h)
      if (headerIds.nonEmpty) {
        val notInvalidHeaders = headerIds.filter { headerId =>
          if (history.isSemanticallyValid(headerId) == Invalid) {
            log.warn(s"Clearing invalid header: $headerId at height $h")
            history.forgetHeader(headerId)
            false
          } else {
            true
          }
        }
        val updatedHeightIdsValue: Array[Byte] = notInvalidHeaders.foldLeft(Array.empty[Byte]) { case (acc, id) =>
          acc ++ idToBytes(id)
        }
        if(updatedHeightIdsValue.isEmpty) {
          //could be the case after bestHeaderHeight
          history.historyStorage.remove(Array(history.heightIdsKey(h)), Nil)
        } else {
          history.historyStorage.insert(Array(history.heightIdsKey(h) -> updatedHeightIdsValue), Nil)
        }
        checkHeightsFrom(h + 1)
      }
    }

    log.info("Checking invalid headers started")
    checkHeightsFrom(bestHeaderHeight - RepairDepth)
    log.info("Checking invalid headers finished")
  }

  def readOrGenerate(ergoSettings: ErgoSettings, ntp: NetworkTimeProvider): ErgoHistory = {
    val db = HistoryStorage(ergoSettings)
    val nodeSettings = ergoSettings.nodeSettings

    val history: ErgoHistory = (nodeSettings.verifyTransactions, nodeSettings.poPoWBootstrap) match {
      case (true, true) =>
        new ErgoHistory with FullBlockSectionProcessor
          with FullPoPoWProofsProcessor {
          override protected val settings: ErgoSettings = ergoSettings
          override protected[history] val historyStorage: HistoryStorage = db
          override val powScheme: AutolykosPowScheme = chainSettings.powScheme
          override protected val timeProvider: NetworkTimeProvider = ntp
        }

      case (false, true) =>
        new ErgoHistory with EmptyBlockSectionProcessor
          with FullPoPoWProofsProcessor {
          override protected val settings: ErgoSettings = ergoSettings
          override protected[history] val historyStorage: HistoryStorage = db
          override val powScheme: AutolykosPowScheme = chainSettings.powScheme
          override protected val timeProvider: NetworkTimeProvider = ntp
        }

      case (true, false) =>
        new ErgoHistory with FullBlockSectionProcessor
          with EmptyPoPoWProofsProcessor {
          override protected val settings: ErgoSettings = ergoSettings
          override protected[history] val historyStorage: HistoryStorage = db
          override val powScheme: AutolykosPowScheme = chainSettings.powScheme
          override protected val timeProvider: NetworkTimeProvider = ntp
        }

      case (false, false) =>
        new ErgoHistory with EmptyBlockSectionProcessor
          with EmptyPoPoWProofsProcessor {
          override protected val settings: ErgoSettings = ergoSettings
          override protected[history] val historyStorage: HistoryStorage = db
          override val powScheme: AutolykosPowScheme = chainSettings.powScheme
          override protected val timeProvider: NetworkTimeProvider = ntp
        }
    }

    repairIfNeeded(history)

    log.info("History database read")
    history
  }

}
