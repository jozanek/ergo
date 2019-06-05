package org.ergoplatform.nodeView.state

import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.{Extension, PoPowAlgos}
import org.ergoplatform.settings.Parameters._
import org.ergoplatform.utils.HistoryTestHelpers

class ErgoStateContextSpec extends HistoryTestHelpers {

  property("Header votes") {
    val fb = genChain(1).head
    val header = fb.header

    def fbWithVotes(votes: Array[Byte], h: Int = 1): ErgoFullBlock = {
      val newHeader = header.copy(votes = votes, version = 0: Byte, height = h)
      fb.copy(header = newHeader)
    }

    //double vote
    val wrongVotes1 = Array(StorageFeeFactorIncrease, StorageFeeFactorIncrease, NoParameter)
    emptyStateContext.appendFullBlock(fbWithVotes(wrongVotes1), votingSettings) shouldBe 'failure

    //contradictory votes
    val wrongVotes2 = Array(StorageFeeFactorIncrease, StorageFeeFactorDecrease, NoParameter)
    emptyStateContext.appendFullBlock(fbWithVotes(wrongVotes2), votingSettings) shouldBe 'failure

    //too many votes - only two ordinary changes allowed per epoch
    val wrongVotes3 = Array(StorageFeeFactorIncrease, MaxBlockCostIncrease, MaxBlockSizeDecrease)
    emptyStateContext.appendFullBlock(fbWithVotes(wrongVotes3), votingSettings) shouldBe 'failure

    //a vote proposed on non-existing parameter
    val wrongVotes4 = Array((-50).toByte, NoParameter, MaxBlockSizeDecrease)
    emptyStateContext.appendFullBlock(fbWithVotes(wrongVotes4, 2), votingSettings) shouldBe 'failure

    //correct votes
    val correctVotes = Array(StorageFeeFactorIncrease, MaxBlockSizeDecrease, NoParameter)
    emptyStateContext.appendFullBlock(fbWithVotes(correctVotes), votingSettings) shouldBe 'success

  }

  property("Extension validation") {
    val chain = genChain(2)
    val sc = emptyStateContext.appendFullBlock(chain.head, votingSettings).get
    val fb = chain.last
    val extension = fb.extension
    val oldFields = extension.fields

    def fbWithFields(newFields: Seq[(Array[Byte], Array[Byte])]): ErgoFullBlock = {
      val newExtension = extension.copy(fields = newFields)
      fb.copy(extension = newExtension)
    }

    // checks, specific for extension
    // validation of field keys size
    val imvKey = extensionKvGen(Extension.FieldKeySize - 1, Extension.FieldValueMaxSize).sample.get
    sc.appendFullBlock(fbWithFields(imvKey +: oldFields), votingSettings) shouldBe 'failure

    // validation of field value sizes
    val imvValue = extensionKvGen(Extension.FieldKeySize, Extension.FieldValueMaxSize + 1).sample.get
    sc.appendFullBlock(fbWithFields(imvValue +: oldFields), votingSettings) shouldBe 'failure

    // validation of incorrect interlinks
    val invalidInterlinks = PoPowAlgos.interlinksToExtension(
      PoPowAlgos.unpackInterlinks(fb.extension.fields).get ++ Seq(fb.header.id)
    ).fields
    sc.appendFullBlock(fbWithFields(invalidInterlinks ++ oldFields), votingSettings) shouldBe 'failure

    // validation of key duplicates in fields
    val validMKV = extensionKvGen(Extension.FieldKeySize, Extension.FieldValueMaxSize).sample.get
    sc.appendFullBlock(fbWithFields(Seq(validMKV, validMKV) ++ oldFields), votingSettings) shouldBe 'failure

    // valid application of correct extension
    sc.appendFullBlock(fbWithFields(validMKV +: oldFields), votingSettings) shouldBe 'success

  }


}
