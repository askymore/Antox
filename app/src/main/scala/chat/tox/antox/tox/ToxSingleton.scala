package chat.tox.antox.tox

import java.io._
import java.util
import java.util.Collections

import android.content.{Context, SharedPreferences}
import android.net.ConnectivityManager
import android.preference.PreferenceManager
import chat.tox.antox.data.{AntoxDB, State}
import chat.tox.antox.utils._
import chat.tox.antox.wrapper.{ToxCore, _}
import im.tox.core.network.Port
import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.enums.ToxUserStatus
import im.tox.tox4j.core.options.ToxOptions
import org.json.{JSONException, JSONObject}
import org.scaloid.common.LoggerTag
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.{IOScheduler, NewThreadScheduler}
import scala.util.control.Breaks
object ToxSingleton {

  var tox: ToxCore = _

  var toxAv: ToxAv = _

  private var groupList: GroupList = _

  var dataFile: ToxDataFile = _

  var qrFile: File = _

  var typingMap: util.HashMap[ContactKey, Boolean] = new util.HashMap[ContactKey, Boolean]()

  var isInited: Boolean = false

  private val nodeFileName = "Nodefile.json"

  private var dhtNodes = Seq[DhtNode]()

  var bootstrapped = false

  def interval: Int = {
    Math.min(State.transfers.interval, tox.interval)
  }

  def getGroupList: GroupList = groupList

  def getGroup(groupNumber: Int): Group = getGroupList.getGroup(groupNumber)

  def getGroup(groupKey: GroupKey): Group = getGroupList.getGroup(groupKey)

  def getGroupPeer(groupNumber: Int, peerNumber: Int): GroupPeer = getGroupList.getPeer(groupNumber, peerNumber)

  def changeActiveKey(key: ContactKey) {
    State.activeKey.onNext(Some(key))
  }

  def exportDataFile(dest: File): Unit = {
    dataFile.exportFile(dest)
    ToxSingleton.save()
  }

  def bootstrap(ctx: Context, updateNodes: Boolean = false): Boolean = {
    val TAG = LoggerTag("Bootstrap")

    if (updateNodes) updateCachedDhtNodes(ctx)

    //todo :to increase bootstrap,delete json temporary  change request askymore-1
//    dhtNodes =
//      (if (dhtNodes.isEmpty) None else Some(dhtNodes))
//        .orElse(readCachedDhtNodes(ctx))
//        .orElse({
//          // if all else fails, try to pull the nodes from the server again
//          updateCachedDhtNodes(ctx)
//          readCachedDhtNodes(ctx)
//        })
//        .getOrElse(Nil)

    dhtNodes +:= DhtNode("luanadd","178.62.250.138",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("788236D34978D1D5BD822F0A5BEBD2C53C64CC31CD3149350EE27D4D9A2F9B6B")),Port.unsafeFromInt(33445))
    dhtNodes +:= DhtNode("luanadd","nodes.tox.chat",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("6FC41E2BD381D37E9748FC0E0328CE086AF9598BECC8FEB7DDF2E440475F300E")),Port.unsafeFromInt(33445))
    dhtNodes +:= DhtNode("luanadd","130.133.110.14",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("461FA3776EF0FA655F1A05477DF1B3B614F7D6B124F7DB1DD4FE3C08B03B640F")),Port.unsafeFromInt(33445))
    dhtNodes +:= DhtNode("luanadd","163.172.136.118",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("2C289F9F37C20D09DA83565588BF496FAB3764853FA38141817A72E3F18ACA0B")),Port.unsafeFromInt(33445))
    dhtNodes +:= DhtNode("luanadd","217.182.143.254",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("7AED21F94D82B05774F697B209628CD5A9AD17E0C073D9329076A4C28ED28147")),Port.unsafeFromInt(443))
    dhtNodes +:= DhtNode("luanadd","185.14.30.213",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("2555763C8C460495B14157D234DD56B86300A2395554BCAE4621AC345B8C1B1B")),Port.unsafeFromInt(443))
    dhtNodes +:= DhtNode("luanadd","128.199.199.197",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("B05C8869DBB4EDDD308F43C1A974A20A725A36EACCA123862FDE9945BF9D3E09")),Port.unsafeFromInt(33445))
    dhtNodes +:= DhtNode("luanadd","biribiri.org",ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes("F404ABAA1C99A9D37D61AB54898F56793E1DEF8BD46B1038B9D822E8460FAB67")),Port.unsafeFromInt(33445))

    //avoid always hitting the first node in the list
    Collections.shuffle(util.Arrays.asList(dhtNodes: _*))

    AntoxLog.debug("Trying to bootstrap", TAG)
    AntoxLog.debug("Current nodes: " + dhtNodes.mkString("|"), TAG)

    val loop = new Breaks;
    loop.breakable {
      for (i <- dhtNodes.indices) {
        try {
          AntoxLog.debug(s"Bootstrapping to ${dhtNodes(i).ipv4}:${dhtNodes(i).port.value}", TAG)
          tox.bootstrap(dhtNodes(i).ipv4, dhtNodes(i).port, dhtNodes(i).key)
          bootstrapped = true
          loop.break
        } catch {
          case _: Exception =>
            AntoxLog.error(s"Couldn't bootstrap to node ${dhtNodes(i).ipv4}:${dhtNodes(i).port.value}")
        }
      }
    }

    if (bootstrapped) {
      AntoxLog.debug("Successfully bootstrapped", TAG)
      true
    } else if (!updateNodes) { //prevent infinite loop
      AntoxLog.debug("Could not find a node to bootstrap to, fetching new Nodefile and trying again", TAG)
      bootstrap(ctx, updateNodes = true)
    } else {
      AntoxLog.debug("Failed to bootstrap", TAG)
      false
    }
  }

  def updateCachedDhtNodes(ctx: Context): Unit = {
    val nodeFileUrl = "https://nodes.tox.chat/json"

    JsonReader.readFromUrl(nodeFileUrl) match {
      case Some(downloadedJson) =>
        FileUtils.writePrivateFile(nodeFileName, downloadedJson, ctx)
      case None =>
        AntoxLog.debug("Failed to download nodefile")
    }
  }

  def readCachedDhtNodes(ctx: Context): Option[Seq[DhtNode]] = {
    val savedNodeFile = new File(ctx.getFilesDir, nodeFileName)

    for (
      json <- JsonReader.readJsonFromFile(savedNodeFile);
      nodes <- parseDhtNodes(json)
    ) yield nodes
  }

  private def parseDhtNodes(json: JSONObject): Option[Seq[DhtNode]] = {
    try {
      var dhtNodes: Array[DhtNode] = Array()
      val serverArray = json.getJSONArray("nodes")
      for (i <- 0 until serverArray.length) {
        val jsonObject = serverArray.getJSONObject(i)
        if (jsonObject.getBoolean("status_tcp")) {
          dhtNodes +:= DhtNode(
            jsonObject.getString("maintainer"),
            jsonObject.getString("ipv4"),
            ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes(jsonObject.getString("public_key"))),
            Port.unsafeFromInt(jsonObject.getInt("port")))
        }
      }

      Some(dhtNodes)
    } catch {
      case e: JSONException =>
        e.printStackTrace()
        None
    }
  }

  private def bootstrapFromCustomNode(preferences: SharedPreferences) = {
    try {
      val ip = preferences.getString("custom_node_address", "127.0.0.1")
      val port = Port.unsafeFromInt(preferences.getString("custom_node_port", "33445").toInt)
      val address = ToxPublicKey.unsafeFromValue(Hex.hexStringToBytes(preferences.getString("custom_node_key", "")))

      val node = DhtNode("custom", ip, address, port)
      tox.bootstrap(node.ipv4, node.port, node.key)
    } catch {
      case e: Exception =>
        AntoxLog.error("Failed to bootstrap from custom node")
        e.printStackTrace()
    }
  }

  def isToxConnected(preferences: SharedPreferences, context: Context): Boolean = {
    val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    val wifiOnly = preferences.getBoolean("wifi_only", true)
    val wifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

    !(wifiOnly && !wifiInfo.isConnected)
  }



  def initTox(ctx: Context) {
    isInited = true
    val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)

    val userDb = State.userDb(ctx)

    groupList = new GroupList()

    qrFile = ctx.getFileStreamPath("userkey_qr.png")
    dataFile = new ToxDataFile(ctx, userDb.getActiveUser)

    val udpEnabled = preferences.getBoolean("enable_udp", false)
    val proxyOptions = ProxyUtils.toxProxyFromPreferences(preferences)
    val options = ToxOptions(
      ipv6Enabled = Options.ipv6Enabled,
      proxy = proxyOptions,
      udpEnabled = udpEnabled,
      saveData = dataFile.loadAsSaveType())

    try {
      tox = new ToxCore(groupList, options)
      if (!dataFile.doesFileExist()) dataFile.saveFile(tox.getSaveData)
      val editor = preferences.edit()
      editor.putString("tox_id", tox.getAddress.toString)
      editor.commit()


      State.db = new AntoxDB(ctx, userDb.getActiveUser, tox.getSelfKey)
      val db = State.db

      toxAv = new ToxAv(tox.getTox)

      db.clearFileNumbers()
      db.setAllOffline()

      db.synchroniseWithTox(tox)

      val details = userDb.getActiveUserDetails
      tox.setName(details.nickname)
      tox.setStatusMessage(details.statusMessage)
      var newStatus: ToxUserStatus = ToxUserStatus.NONE
      val newStatusString = details.status
      newStatus = UserStatus.getToxUserStatusFromString(newStatusString)
      tox.setStatus(newStatus)

      Observable[Boolean](_ =>
        if (preferences.getBoolean("enable_custom_node", false)) {
          bootstrapFromCustomNode(preferences)
        } else {
          bootstrap(ctx)
        })
        .subscribeOn(IOScheduler())
        .observeOn(NewThreadScheduler())
        .subscribe()
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def save(): Unit = {
    dataFile.saveFile(tox.getSaveData)
  }
}
