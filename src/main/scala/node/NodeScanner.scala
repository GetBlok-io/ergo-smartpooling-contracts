package node

import explorer.CustomExplorerApi
import io.circe.Json
import logging.LoggingHandler
import org.ergoplatform.appkit.JavaHelpers.UniversalConverter
import org.ergoplatform.appkit.impl.{BlockchainContextBase, InputBoxImpl}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox, InputBoxesSelectionException}
import org.ergoplatform.restapi.client.{ApiClient, JSON, NodeApi, ScanApi, ScanId, ScanRequest, ScanningPredicate}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.Values.ErgoTree

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class NodeScanner(apiClient: ApiClient){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_NODE_HANDLER)

  logger.info("Creating node scanner")
  val scanApi: ScanningApi = apiClient.createService(classOf[ScanningApi])
  var registeredScans = Array.empty[Scan]

  def currentScans: List[Scan] = {
    val listScansReq = scanApi.listAllScans().execute()
    if(listScansReq.isSuccessful){
      logger.info("Scans found:")
      val scans = listScansReq.body().asScala
      scans.foreach(s => logger.info(s.toString))
      scans.toList
    }
    List[Scan]()
  }

  def loadCurrentScans(): Unit = {
    val listScansReq = scanApi.listAllScans().execute()
    logger.info("Loading current scans")
    if(listScansReq.isSuccessful){
      logger.info("Scans found:")
      val scans = listScansReq.body().asScala
      scans.foreach(s => logger.info(s.toString))
      registeredScans = scans.toArray
    }else{
      logger.warn("Request was not successful!")
    }
  }


  def registerBoxScan(address: Address, scanName: String): Unit = {
    val req = new Scan(scanName, scriptRule(address.getErgoAddress.script))
    // val otrReq = new ScanRequest().scanName(req.scanName).trackingRule(new ScanningPredicate().)
    logger.info(apiClient.getGson.toJson(req).toString)
    val scanRequest = scanApi.registerScan(req).execute()
    if(scanRequest.isSuccessful){
      logger.info(s"Registered box scan for address $address")
      registeredScans = registeredScans++Array(new Scan(scanRequest.body(), req.scanName, req.walletInteraction, req.trackingRule, req.removeOffchain))

    }else{
      logger.warn(s"Scan registration for address $address could not be completed!")
    }
  }

  def registerAssetScan(tokenId: ErgoId): Unit = {
    val req = new Scan(tokenId.toString + " asset tracker", assetRule(tokenId))
    val scanRequest = scanApi.registerScan(req).execute()
    if(scanRequest.isSuccessful){
      logger.info(s"Registered box scan for asset $tokenId")
      registeredScans = registeredScans++Array(new Scan(scanRequest.body(), req.scanName, req.walletInteraction, req.trackingRule, req.removeOffchain))
    }else{
      logger.warn(s"Scan registration for asset $tokenId could not be completed!")
    }
  }

  def registerDualScan(address: Address, tokenId: ErgoId): Unit = {
    val req = new Scan(address.toString + " with asset " + tokenId.toString + " tracker", dualRule(address.getErgoAddress.script, tokenId))
    val scanRequest = scanApi.registerScan(req).execute()
    if(scanRequest.isSuccessful){
      logger.info(s"Registered box scan for address $address and asset $tokenId")
      registeredScans = registeredScans++Array(new Scan(scanRequest.body(), req.scanName, req.walletInteraction, req.trackingRule, req.removeOffchain))
    }else{
      logger.warn(s"Scan registrationfor address $address and asset $tokenId could not be completed!")
    }
  }

  def scriptRule(ergoTree: ErgoTree): TrackingRule = {
    new TrackingRule(Predicates.CONTAINS, ergoTree.bytesHex)
  }

  def assetRule(ergoId: ErgoId): TrackingRule = {
    new TrackingRule(Predicates.CONTAINS_ASSET, ergoId.toString)
  }

  def dualRule(ergoTree: ErgoTree, ergoId: ErgoId): TrackingRule = {
    val rules = Array(scriptRule(ergoTree), assetRule(ergoId))
    new TrackingRule(Predicates.AND, rules)
  }

  def scanBoxes(ctx: BlockchainContext, address: Address, pageSize: Int, offset: Int): Array[InputBox] = {
    val addressScans = registeredScans.filter(s => s.trackingRule.getValue != null)
    val scanList = for(s <- addressScans) yield (s.scanId, s.trackingRule.value)
    val currentScan = scanList.find(s => s._2 == address.getErgoAddress.script.toString)
    if(currentScan.isDefined){
      val scanRequest = scanApi.listUnspentScans(currentScan.get._1.getScanId, 0, 0).execute()
      if(scanRequest.isSuccessful){
        val inputBoxes: Array[InputBox] = scanRequest.body().asScala.toArray.map(b => new InputBoxImpl(ctx.asInstanceOf[BlockchainContextBase], b.getBox))
        inputBoxes.slice(offset, pageSize)
      }else{
        logger.warn(s"Scan request for address $address with scanId ${currentScan.get._1.getScanId} was unsuccessful")
        Array[InputBox]()
      }
    }else{
      Array[InputBox]()
    }
  }

  def boxesById(ctx: BlockchainContext, ids: Array[String]): Array[InputBox] = {
    var boxes = Array[InputBox]()
    for(id <- ids){
      val boxRequest = scanApi.getBoxById(id).execute()
      if(boxRequest.isSuccessful){
        boxes = boxes++Array(new InputBoxImpl(ctx.asInstanceOf[BlockchainContextBase], boxRequest.body()))
      }else{
        throw new InputBoxesSelectionException(s"A box with id $id could not be found!")
      }
    }
    boxes
  }






}
