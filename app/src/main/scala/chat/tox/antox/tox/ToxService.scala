package chat.tox.antox.tox

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import chat.tox.antox.R
import chat.tox.antox.av.CallService
import chat.tox.antox.callbacks.{AntoxOnSelfConnectionStatusCallback, ToxCallbackListener, ToxavCallbackListener}
import chat.tox.antox.utils.AntoxLog
import im.tox.tox4j.core.enums.ToxConnection
import rx.lang.scala.schedulers.AndroidMainThreadScheduler
import rx.lang.scala.{Observable, Subscription}

import scala.concurrent.duration._

class ToxService extends Service {
private val FOREGROUND_ID = 313399
  private var serviceThread: Thread = _

  private var keepRunning: Boolean = true

  private val connectionCheckInterval = 10000 //in ms

  private val reconnectionIntervalSeconds = 60

  private var callService: CallService = _

  override def onCreate() {
    if (!ToxSingleton.isInited) {
      ToxSingleton.initTox(getApplicationContext)
      AntoxLog.debug("Initting ToxSingleton")
    }

    keepRunning = true
    val thisService = this

    val start = new Runnable() {

      override def run() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext)
        //change request askymore-1   give up video and audio
        val toxCallbackListener = new ToxCallbackListener(thisService)

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

        while (keepRunning) {
          if (!ToxSingleton.isToxConnected(preferences, thisService)) {
            try {
              Thread.sleep(connectionCheckInterval)
            } catch {
              case e: Exception =>
            }
          } else {
            try {
              //change request askymore-1   give up video and audio
              ToxSingleton.tox.iterate(toxCallbackListener)
              Thread.sleep(ToxSingleton.interval)
            } catch {
              case e: Exception =>
                e.printStackTrace()
            }
          }
        }

        connectionSubscription.unsubscribe()
      }
    }

    serviceThread = new Thread(start)
    serviceThread.start()
  }

  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, id: Int): Int = {
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
    ToxSingleton.save()
    ToxSingleton.isInited = false
    ToxSingleton.tox.close()
    AntoxLog.debug("onDestroy() called for Tox service")
  }
}