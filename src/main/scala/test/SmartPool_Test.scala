package test

import contracts._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

object SmartPool_Test {
  /*
  * This file represents a test case for the smart pooling contract.
  * A rewards transaction is simulated that sends mining rewards to the smart pool.
  * First, the smart pool operator creates a metadata box.
  * The smart pool operator then creates a command box to use in the consensus Tx.
  * When the first reward is sent, the command and metadata box will be consumed
  * To create new command and metadata boxes.
   */


  def generateInitialContracts(ctx: BlockchainContext) = {
  }
  def loadInitialAddresses = {
  }



  def rewardToSmartPoolTx(ctx: BlockchainContext, miningPool: SecretString): Unit = {
  }
  // Send a test voting transaction
  def smartPoolToMinersTx(ctx: BlockchainContext, minerString: SecretString, minerAddress: Address, minerShareArray: Array[Long], minerVoteArray: Array[Int]): Unit = {

  }

  def main(args: Array[String]): Unit = {

    }

}