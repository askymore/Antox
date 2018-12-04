package chat.tox.antox.activities

import java.util

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.{Build, Bundle}
import android.preference.PreferenceManager
import android.support.v4.content.IntentCompat
import android.support.v7.app.AppCompatActivity
import android.view.{View, WindowManager}
import android.widget._
import chat.tox.antox.{LogToFile, R}
import chat.tox.antox.data.State
import chat.tox.antox.tox.ToxService
import chat.tox.antox.utils.Options
import pub.devrel.easypermissions.{AppSettingsDialog, EasyPermissions}

import scala.collection.JavaConversions._

class LoginActivity extends AppCompatActivity with AdapterView.OnItemSelectedListener with EasyPermissions.PermissionCallbacks {

  private var profileSelected: String = _
  private val RC_ANTOX_PERM = 146
  private val perms = Array(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.VIBRATE, Manifest.permission.RECEIVE_BOOT_COMPLETED, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WAKE_LOCK, Manifest.permission.MODIFY_AUDIO_SETTINGS)


  protected override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    //change request askymore-1 get rid of the splash
    LogToFile.init(this.getApplicationContext)
    getPermissions()
    //change request askymore-1 get rid of the splash

    setContentView(R.layout.activity_login)
    getSupportActionBar.hide()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow.setStatusBarColor(getResources.getColor(R.color.black))
    }

    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.JELLY_BEAN &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      getWindow.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }

    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val userDb = State.userDb(this)

    State.setAutoAcceptFt(preferences.getBoolean("autoacceptft", false))
    Options.videoCallStartWithNoVideo = preferences.getBoolean("videocallstartwithnovideo", false)
    State.setBatterySavingMode(preferences.getBoolean("batterysavingmode", false))

    // if the user is starting the app for the first
    // time, go directly to the register account screen
    if (userDb.numUsers() == 0) {
      val createAccount = new Intent(getApplicationContext, classOf[CreateAccountActivity])
      createAccount.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
        Intent.FLAG_ACTIVITY_CLEAR_TOP )
      startActivity(createAccount)
      finish()
    } else if (userDb.loggedIn) {
      val startTox = new Intent(getApplicationContext, classOf[ToxService])
      getApplicationContext.startService(startTox)

      val main = new Intent(getApplicationContext, classOf[MainActivity])
      startActivity(main)
      finish()
    } else {
      val profiles = userDb.getAllProfiles
      val profileSpinner = findViewById(R.id.login_account_name).asInstanceOf[Spinner]
      val adapter = new ArrayAdapter[String](this, android.R.layout.simple_spinner_dropdown_item, profiles)
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      profileSpinner.setAdapter(adapter)
      profileSpinner.setSelection(0)
      profileSpinner.setOnItemSelectedListener(this)
    }


    // this may get the app banned from google play :-(
    // ShowPermissionDialog()
  }

  //change request askymore-1 get rid of the splash
  def getPermissions(): Unit = {
    if (EasyPermissions.hasPermissions(this, perms.toString)) { // already have granted all permissions
      return
    }
    // List<String> perms2 = new ArrayList<String>(Arrays.asList(perms));
    if (EasyPermissions.hasPermissions(this, perms.toString)) {
    }
    else { // Ask for permissions
      EasyPermissions.requestPermissions(this, getString(R.string.request_permissions_text), RC_ANTOX_PERM, perms.toString)
    }
  }

  override def onPermissionsGranted(requestCode: Int, perms2: util.List[String]): Unit = { // Some permissions have been granted
    if (EasyPermissions.hasPermissions(this, perms.toString)) { // already have granted all permissions
      finish()
      startActivity(getIntent)
    }
    else getPermissions()
  }

  override def onPermissionsDenied(requestCode: Int, perms2: util.List[String]): Unit = { // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
    // This will display a dialog directing them to enable the permission in app settings.
    if (EasyPermissions.somePermissionPermanentlyDenied(this, perms2)) new AppSettingsDialog.Builder(this).build.show()
    else getPermissions()
  }


  def onItemSelected(parent: AdapterView[_], view: View, pos: Int, id: Long) {
    profileSelected = parent.getItemAtPosition(pos).toString

    if (parent.getChildAt(0) != null) {
      // getChildAt(pos) returns a view, or null if non-existant
      parent.getChildAt(0).asInstanceOf[TextView].setTextColor(Color.BLACK)
    }
  }

  def onNothingSelected(parent: AdapterView[_]) {
  }

  def onClickLogin(view: View) {
    val account = profileSelected
    if (account == "") {
      val context = getApplicationContext
      val text = getString(R.string.login_must_fill_in)
      val duration = Toast.LENGTH_SHORT
      val toast = Toast.makeText(context, text, duration)
      toast.show()
    } else {
      val userDb = State.userDb(this)
      if (userDb.doesUserExist(account)) {
        val details = userDb.getUserDetails(account)
        State.login(account, this)
        val startTox = new Intent(getApplicationContext, classOf[ToxService])
        getApplicationContext.startService(startTox)
        val main = new Intent(getApplicationContext, classOf[MainActivity])
        startActivity(main)
        finish()
      } else {
        val context = getApplicationContext
        val text = getString(R.string.login_bad_login)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
      }
    }
  }

  def onClickCreateAccount(view: View) {
    val createAccount = new Intent(getApplicationContext, classOf[CreateAccountActivity])
    startActivityForResult(createAccount, 1)
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    if (requestCode == 1) {
      if (resultCode == Activity.RESULT_OK) {
        finish()
      }
    }
  }
}
