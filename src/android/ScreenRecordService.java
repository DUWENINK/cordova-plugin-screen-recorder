package rs.prdc.screenrecord;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.app.Constants;

import android.os.Build;
import android.os.Binder;
import android.os.IBinder;

import android.content.pm.ServiceInfo;

// ScreenRecordService class
// Class for ScreenRecord service
// --------------------
public class ScreenRecordService extends Service {
  private static final String TAG = "ScreenRecordService";
  private NotificationManager mNotificationManager;
  private static final int NOTIFICATION = 1000;
  private String NOTIFICATION_CHANNEL_ID = "rs.prdc.screenrecord";
  private PendingIntent pendingIntent;
  private final IBinder mBinder = new LocalBinder();
  
  // LocalBinder class
  // Class for service binder
  // --------------------
  public class LocalBinder extends Binder {
    ScreenRecordService getService() {
      // Return this instance of LocalService so clients can call public methods
      return ScreenRecordService .this;
    }
  }

  // onBind
  // --------------------
  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }
  
  // ScreenRecordService
  // Constructor
  // --------------------
	public ScreenRecordService() {
		super();
	}

  // onCreate
  // --------------------
	@Override
	public void onCreate() {
		super.onCreate();
		mNotificationManager = (NotificationManager) 
      getSystemService(NOTIFICATION_SERVICE);

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        String channelName = "Screen Recording";
        NotificationChannel chan = new NotificationChannel(
          NOTIFICATION_CHANNEL_ID, channelName, 
          NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(chan);
    }
	}


  public void showNewNotification(String title, String text, Context context, int notifkResId) {
    // Create a notification channel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel = new NotificationChannel("channel01", "Screen Record Service",
            NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    // Build the notification
    Notification.Builder builder = new Notification.Builder(context, "channel01")
        .setSmallIcon(notifkResId)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(Notification.PRIORITY_HIGH);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.addMetadata(Constants.KEY_BACKGROUND_SERVICE, "true");
    }

    Notification notification = builder.build();
    
    // get the notification manager
    NotificationManager notificationManager = 
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    
    // notify the notification
    notificationManager.notify(1, notification);
}



  // showNotification()
  // Start foreground and show notification
  // --------------------
  public void showNotification(final CharSequence title, 
      final CharSequence text, Context context, int icon) {

    try {
      // Get MainActivity of application
      Class mainActivity;
      Intent launchIntent = context.getPackageManager()
        .getLaunchIntentForPackage(context.getPackageName());
      String className = launchIntent.getComponent().getClassName();
      mainActivity = Class.forName(className);
      
      // Create Intent for notification
      Intent notificationIntent = new Intent(this, mainActivity);
      pendingIntent = PendingIntent.getActivity(this, 0, 
        notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        
      Builder notiBuilder;
      // Create notification
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notiBuilder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
      } else {
        notiBuilder = new Notification.Builder(this);
      }
          
      Notification notification = notiBuilder.setSmallIcon(icon)
        .setTicker(text)
        .setWhen(System.currentTimeMillis())
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pendingIntent)
        .build();
          
      startForeground(NOTIFICATION, notification);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  // removeNotification()
  // Remove notification and stop foreground
  // --------------------
  public void removeNotification() {
		stopForeground(NOTIFICATION);
    mNotificationManager.cancel(NOTIFICATION);
  }
}
