package chat.tox.antox.tox

import android.app.{Activity, Application, Service}
import android.content.{ComponentName, Context, Intent}
import android.os.{Build, Bundle, IBinder}
import android.preference.PreferenceManager
import chat.tox.antox.av.CallService
import chat.tox.antox.callbacks.{AntoxOnSelfConnectionStatusCallback, ToxCallbackListener, ToxavCallbackListener}
import chat.tox.antox.utils.AntoxLog
import im.tox.tox4j.core.enums.ToxConnection
import rx.lang.scala.schedulers.AndroidMainThreadScheduler
import rx.lang.scala.{Observable, Subscription}

import scala.concurrent.duration._
import android.app.Application.ActivityLifecycleCallbacks
import android.app.job.{JobInfo, JobScheduler}

object ToxService {
  def initToxJobService(activity:Activity): Unit ={
    if (!ToxSingleton.isInited) {
      ToxSingleton.initTox(activity.getApplicationContext)
      AntoxLog.debug("Initting ToxSingleton")
    }
    val jobScheduler = activity.getSystemService(Context.JOB_SCHEDULER_SERVICE).asInstanceOf[JobScheduler]
    jobScheduler.cancelAll()
    val builder = new JobInfo.Builder(1024, new ComponentName(activity.getPackageName, classOf[ToxJobService].getName))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //android N之后时间必须在15分钟以上
      builder.setPeriodic(15 * 60 * 1000)
    }
    else builder.setPeriodic(60 * 1000)
    builder.setPersisted(true)
    builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
    builder.setRequiresCharging(false)
    builder.setRequiresDeviceIdle(false)
    jobScheduler.schedule(builder.build)
  }
}
class ToxService extends Service{

  private var serviceThread: Thread = _

  private var keepRunning: Boolean = true

  private val connectionCheckInterval = 3 * 60 * 1000 //in ms

  private val reconnectionIntervalSeconds = 60 //in second

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
//              change request askymore-1   give up video and audio
//        callService = new CallService(thisService)
//        callService.start()
        val toxCallbackListener = new ToxCallbackListener(thisService)
//        val toxAvCallbackListener = new ToxavCallbackListener(thisService)

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
              ToxSingleton.tox.iterate(toxCallbackListener)
//              change request askymore-1   give up video and audio
                Thread.sleep(ToxSingleton.interval)
              }
             catch {
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

  override def onStartCommand(intent: Intent, flags: Int, id: Int): Int = Service.START_NOT_STICKY

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