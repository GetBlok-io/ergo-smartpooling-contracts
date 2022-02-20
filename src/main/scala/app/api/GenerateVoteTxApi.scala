package app.api
import app.AppParameters
import app.api.models.APIMapper
import app.api.models.APIModels.{FullUnsignedTransaction, ReducedTransaction}
import boxes.BoxHelpers
import com.google.gson.Gson
import configs.SmartPoolConfig
import contracts.voting.ProxyBallotContract
import org.ergoplatform.appkit.JavaHelpers.UniversalConverter
import org.ergoplatform.appkit.impl.ScalaBridge.{isoErgoTransactionDataInput, isoErgoTransactionInput, isoErgoTransactionOutput}
import org.ergoplatform.{DataInput, ErgoBox, ErgoLikeTransaction, Input, UnsignedErgoLikeTransaction, UnsignedInput}
import org.ergoplatform.appkit.impl.{InputBoxImpl, ScalaBridge, UnsignedTransactionBuilderImpl, UnsignedTransactionImpl}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, ErgoToken, ErgoValue, Iso, JavaHelpers, NetworkType, Parameters, RestApiErgoClient}
import org.ergoplatform.restapi.client.{ApiClient, ErgoTransaction, ErgoTransactionDataInput, ErgoTransactionInput, ErgoTransactionOutput, ErgoTransactionUnsignedInput, JSON, UnsignedErgoTransaction}
import org.ergoplatform.settings.ErgoAlgos
import org.ergoplatform.wallet.transactions.TransactionBuilder
import sigmastate.interpreter.ContextExtension

import java.util
import java.util.List
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

class GenerateVoteTxApi(config: SmartPoolConfig, args: Array[String]) extends ApiCommand(config, args) {

  val address: String = args(0)
  val vote: String = args(1).toLowerCase
  val method: String = args(2).toLowerCase

  final val VOTE_YES = "yes"
  final val VOTE_NO = "no"
  final val CONNECTOR = "conn"
  final val ERGOPAY = "pay"

  val voteTokenId: ErgoId = ErgoId.create(voteConf.getVoteTokenId)
  val recordingNFT: ErgoId = ErgoId.create(voteConf.getPovTokenId)
  val restApiClient = new ApiClient(nodeConf.getNodeApi.getApiUrl, "ApiKeyAuth", nodeConf.getNodeApi.getApiKey)
  restApiClient.createDefaultAdapter()

  override def execute: ApiCommand = {
    ergoClient.execute{(ctx: BlockchainContext) =>
      val voterAddress = Address.create(address)
      val votingContract =
        if(vote == VOTE_YES)
          ProxyBallotContract.generateContract(ctx, voteTokenId, voteYes = true, recordingNFT)
        else
          ProxyBallotContract.generateContract(ctx, voteTokenId, voteYes = false, recordingNFT)

      val (votingInputs, numTokens) = BoxHelpers.findAllTokenBoxes(ctx, voterAddress, voteTokenId)
      val txB = ctx.newTxBuilder()

      if(method == CONNECTOR) {
        val boxCandidate = JavaHelpers.createBoxCandidate(
          Parameters.MinFee, votingContract.getErgoTree,
          Seq(new ErgoToken(voteTokenId, numTokens)), Seq.empty[ErgoValue[_]], ctx.getHeight)

        val builtTx = Try(APIMapper.buildEIP12Tx(votingInputs, Array(boxCandidate), voterAddress, Parameters.MinFee, ctx.getHeight))

        if (builtTx.isSuccess) {
          val txJson = eip12ToJson(builtTx.get, prettyPrint = true)
          println("Naut Tx: " + txJson)
          outputStrings = Array(eip12ToJson(builtTx.get, prettyPrint = false))
        } else {
          println("Tx could not be processed")
          outputStrings = Array("ERROR")
        }
      }else if(method == ERGOPAY) {
        val output = txB.outBoxBuilder()
          .value(Parameters.MinFee)
          .contract(votingContract.asErgoContract)
          .tokens(new ErgoToken(voteTokenId, numTokens))
          .build()
        val unsignedTx =
          txB
            .boxesToSpend(votingInputs.toList.asJava)
            .outputs(output)
            .fee(Parameters.MinFee)
            .build()

        val reducedTx = ctx.newProverBuilder().build().reduce(unsignedTx, 0)
        val builtTx = Try(APIMapper.buildErgoPayTx(reducedTx))
        if(builtTx.isSuccess){

          val txJson = ergoPayToJson(builtTx.get, prettyPrint = true)
          println("ErgoPay Tx: " + txJson)
          outputStrings = Array(ergoPayToJson(builtTx.get, prettyPrint = false))
        } else {
          println("Tx could not be processed")
          outputStrings = Array("ERROR")
        }
      }
    }
    this
  }

  def eip12ToJson(tx: FullUnsignedTransaction, prettyPrint: Boolean): String = {
    val gson = if (prettyPrint) JSON.createGson.setPrettyPrinting.create
    else restApiClient.getGson
    val json = gson.toJson(tx)
    json
  }

  def ergoPayToJson(tx: ReducedTransaction, prettyPrint: Boolean): String = {
    val gson = if (prettyPrint) JSON.createGson.setPrettyPrinting.create
    else restApiClient.getGson
    val json = gson.toJson(tx)
    json
  }

}
