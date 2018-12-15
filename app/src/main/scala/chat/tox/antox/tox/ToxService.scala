package chat.tox.antox.tox

import android.app.Service
import android.content.{ Intent, SharedPreferences}
import android.os.{IBinder}
import android.preference.PreferenceManager
import chat.tox.antox.av.CallService
import chat.tox.antox.callbacks.{AntoxOnSelfConnectionStatusCallback, ToxCallbackListener, ToxavCallbackListener}
import chat.tox.antox.utils.AntoxLog
import im.tox.tox4j.core.enums.ToxConnection
import rx.lang.scala.schedulers.AndroidMainThreadScheduler
import rx.lang.scala.{Observable, Subscription}

import scala.concurrent.duration._
import android.support.v4.app.NotificationCompat
import chat.tox.antox.R

class ToxService extends Service{
  private val FOREGROUND_ID = 313399
  private var serviceThread: Thread = _
  private var keepRunning: Boolean = true
  private val connectionCheckInterval = 10 * 1000 //in ms
  private val reconnectionIntervalSeconds = 20 //in second
  private var callService: CallService = _
  private var thisService :ToxService = _
  private var preferences :SharedPreferences = _
  private var toxCallbackListener:ToxCallbackListener = _

  override def onCreate() {
    if (!ToxSingleton.isInited) {
      ToxSingleton.initTox(getApplicationContext)
      AntoxLog.debug("Initting ToxSingleton")
    }
    keepRunning = true
    thisService = this
    preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext)
    toxCallbackListener = new ToxCallbackListener(thisService)

    val start = new Runnable() {

      override  def run() {
        var reconnection: Subscription = null
        val connectionSubscription = AntoxOnSelfConnectionStatusCallback.connectionStatusSubject
          .observeOn(AndroidMainThreadScheduler())
          .distinctUntilChanged
          .subscribe(toxConnection => {
            if (toxConnection != ToxConnection.NONE) {
              if (reconnection != null && !reconnection.isUnsubscribed) {
                reconnection.unsubscribe()
              }
              AntoxLog.debug("Tox connected. Stopping reconnection")
            } else {
              reconnection = Observable
                .interval(reconnectionIntervalSeconds seconds)
                .subscribe(x => {
                  AntoxLog.debug("Reconnecting")
                  Observable[Boolean](_ => ToxSingleton.bootstrap(getApplicationContext)).subscribe()
                })
              AntoxLog.debug(s"Tox disconnected. Scheduled reconnection every $reconnectionIntervalSeconds seconds")
            }
          })
        retrieve()

        connectionSubscription.unsubscribe()
      }
    }
    serviceThread = new Thread(start)
    serviceThread.start()
  }

  def retrieve(): Unit =  {
    while (keepRunning) {
      if (!ToxSingleton.isToxConnected(preferences, thisService)) {
        try {
          Thread.sleep(connectionCheckInterval)
        } catch {
          case e:InterruptedException =>
            AntoxLog.debug("Retrieve Thread interrupted")
            throw e
          case e: Exception =>AntoxLog.debug("Retrieve Thread interrupted")
        }
      } else {
        try {
          ToxSingleton.tox.iterate(toxCallbackListener)
          //change request askymore-1   give up video and audio
          Thread.sleep(ToxSingleton.interval)
//          onion_client.h  ONION_NODE_PING_INTERVAL--the minimum interval is 15s
        }
        catch {
          case e:InterruptedException =>
            AntoxLog.debug("Retrieve Thread interrupted")
            throw e
          case e: Exception =>
            AntoxLog.debug("Retrieve Thread interrupted")
        }
      }
    }
  }

  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    super.onStartCommand(intent, flags, startId)
        val builder=new NotificationCompat.Builder(this)
        builder.setContentTitle("Antox")
        builder.setPriority(NotificationCompat.PRIORITY_MIN)
        builder.setWhen(0)
        builder.setSmallIcon(R.drawable.ic_action_add)
    startForeground(FOREGROUND_ID, builder.build)
    Service.START_STICKY
  }

  override def onDestroy() {
    super.onDestroy()
    keepRunning = false
    serviceThread.interrupt()
    serviceThread.join()
//    callService.destroy()
    ToxSingleton.save()
    ToxSingleton.isInited = false
    ToxSingleton.tox.close()
    AntoxLog.debug("onDestroy() called for Tox service")
  }

}