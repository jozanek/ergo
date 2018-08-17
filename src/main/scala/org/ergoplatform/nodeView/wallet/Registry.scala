package org.ergoplatform.nodeView.wallet

import org.ergoplatform.nodeView.history.ErgoHistory.Height
import scorex.core.{ModifierId, bytesToId}

import scala.collection.mutable

class Registry {

  private val registry = mutable.Map[ModifierId, TrackedBox]()

  private val confirmedIndex = mutable.TreeMap[Height, Seq[ModifierId]]()

  //todo: build indexes instead of iteration
  def unspentBoxes: Iterator[UnspentBox] = registry.valuesIterator.collect {
    case ub: UnspentBox => ub
  }

  def uncertainBoxes: Iterator[TrackedBox] = registry.valuesIterator.filterNot(_.certain)

  //todo: extract a random element, not head
  def nextUncertain(): Option[TrackedBox] = uncertainBoxes.toSeq.headOption

  def registryContains(boxId: ModifierId): Boolean = synchronized {
    registry.contains(boxId)
  }

  def putToRegistry(trackedBox: TrackedBox): Option[TrackedBox] = synchronized {
    registry.put(trackedBox.boxId, trackedBox)
  }

  def removeFromRegistry(boxId: ModifierId): Option[TrackedBox] = synchronized {
    registry.remove(boxId)
  }

  def putToConfirmedIndex(height: Height, boxId: ModifierId): Unit = synchronized {
    confirmedIndex.put(height, confirmedIndex.getOrElse(height, Seq.empty) :+ boxId)
  }

  def confirmedAt(height: Height): Seq[ModifierId] = synchronized {
    confirmedIndex.getOrElse(height, Seq.empty)
  }

  private var _confirmedBalance: Long = 0
  private val _confirmedAssetBalances: mutable.Map[ModifierId, Long] = mutable.Map()

  private var _unconfirmedBalance: Long = 0
  private val _unconfirmedAssetBalances: mutable.Map[ModifierId, Long] = mutable.Map()

  def confirmedBalance: Long = _confirmedBalance

  def confirmedAssetBalances: scala.collection.Map[ModifierId, Long] = _confirmedAssetBalances

  def unconfirmedBalance: Long = _unconfirmedBalance

  def unconfirmedAssetBalances: scala.collection.Map[ModifierId, Long] = _unconfirmedAssetBalances

  def increaseBalances(unspentBox: UnspentBox): Unit = synchronized {
    val box = unspentBox.box
    val tokenDelta = box.value
    val assetDeltas = box.additionalTokens

    //todo: reduce boilerplate below?
    if (unspentBox.onchain) {
      _confirmedBalance += tokenDelta
      assetDeltas.foreach { case (id, amount) =>
        val wid = bytesToId(id)
        val updBalance = _confirmedAssetBalances.getOrElse(wid, 0L) + amount
        _confirmedAssetBalances.put(wid, updBalance)
      }
    } else {  //offchain box case
      _unconfirmedBalance += tokenDelta
      assetDeltas.foreach { case (id, amount) =>
        val wid = bytesToId(id)
        val updBalance = _unconfirmedAssetBalances.getOrElse(wid, 0L) + amount
        _unconfirmedAssetBalances.put(wid, updBalance)
      }
    }
  }

  def decreaseBalances(unspentBox: UnspentBox): Unit = synchronized {
    val box = unspentBox.box
    val tokenDelta = box.value
    val assetDeltas = box.additionalTokens

    //todo: reduce boilerplate below?
    if (unspentBox.onchain) {
      _confirmedBalance -= tokenDelta
      assetDeltas.foreach { case (id, amount) =>
        val wid = bytesToId(id)
        val currentBalance = _confirmedAssetBalances.getOrElse(wid, 0L)
        if (currentBalance == amount) {
          _confirmedAssetBalances.remove(wid)
        } else {
          val updBalance = currentBalance - amount
          _confirmedAssetBalances.put(wid, updBalance)
        }
      }
    } else {  //offchain box case
      _unconfirmedBalance -= tokenDelta
      assetDeltas.foreach { case (id, amount) =>
        val wid = bytesToId(id)
        val currentBalance = _unconfirmedAssetBalances.getOrElse(wid, 0L)
        if (currentBalance == amount) {
          _unconfirmedAssetBalances.remove(wid)
        } else {
          val updBalance = currentBalance - amount
          _unconfirmedAssetBalances.put(wid, updBalance)
        }
      }
    }
  }

  def makeTransition(boxId: ModifierId, transition: Transition): Unit = {
    makeTransition(registry(boxId), transition)
  }

  def makeTransition(trackedBox: TrackedBox, transition: Transition): Unit = {
    val transitionResult: Option[TrackedBox] = transition match {
      case ProcessRollback(toHeight) =>
        trackedBox.transitionBack(toHeight)
      case CreationConfirmation(creationHeight) =>
        trackedBox.transition(creationHeight)
      case ProcessSpending(spendingTransaction, spendingHeightOpt) =>
        trackedBox.transition(spendingTransaction, spendingHeightOpt)
    }
    transitionResult match {
      case Some(newTrackedBox) =>
        makeTransition(trackedBox, newTrackedBox)
      case None =>
    }
  }

  def makeTransition(oldTrackedBox: TrackedBox, newTrackedBox: TrackedBox): Unit = {
    oldTrackedBox.deregister(this)
    newTrackedBox.register(this)
  }
}