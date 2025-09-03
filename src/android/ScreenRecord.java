
package rs.prdc.screenrecord;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.util.DisplayMetrics;
import android.content.pm.PackageManager;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.provider.MediaStore;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.NotificationChannel;

import android.content.ContentValues;
import android.content.ContentResolver;

import android.view.Surface;
import android.view.View;

import java.util.Timer;
import java.util.TimerTask;

import android.net.Uri;

import rs.prdc.screenrecord.ScreenRecordService;

// ScreenRecord class
// Class for cordova plugin
// --------------------
public class ScreenRecord extends CordovaPlugin implements ServiceConnection {

  private final static String TAG = "ScreenRecord";
  private MediaProjectionManager mProjectionManager;
  private MediaRecorder mMediaRecorder;
  private MediaProjection mMediaProjection;
  private VirtualDisplay mVirtualDisplay;
  private ScreenRecordService mScreenRecordService;

  protected final static String permission = Manifest.permission.RECORD_AUDIO;
  private Context context;

  private static final int FRAME_RATE = 60; // fps
  private final static int SCREEN_RECORD_CODE = 1000;
  private final static int WRITE_EXTERNAL_STORAGE_CODE = 1001;

  private CallbackContext callbackContext;
  private JSONObject options;
  private boolean recordAudio;
  private String fileName;
  private String filePath;
  private String notifTitle;
  private String notifText;
  private int mWidth;
  private int mHeight;
  private int mBitRate;
  private int mDpi;
  private int mScreenDensity;
  private boolean serviceStarted = false;
  private Uri mUri;

  private boolean isHide = true;

  // execute()
  // Execute functionality based on javascript action
  // --------------------
  @Override
  public boolean execute(String action, JSONArray args,
      CallbackContext callbackContext) throws JSONException {
    this.callbackContext = callbackContext;
    // Start recording
    if (action.equals("startRecord")) {
      options = args.getJSONObject(0);
      Log.d(TAG, options.toString());
      recordAudio = options.getBoolean("recordAudio");
      mBitRate = options.getInt("bitRate");
      notifTitle = options.getString("title");
      notifText = options.getString("text");
      isHide = options.getBoolean("isHide");

      fileName = args.getString(1);
      this.startRecord();
      return true;
    }
    // Stop recording
    else if (action.equals("stopRecord")) {
      this.stopRecord();
      return true;
    }
    return false;
  }

  // startRecord()
  // Start screen recording
  // --------------------
  private void startRecord() {
    Log.d(TAG, "Start recording");
    if (cordova != null) {
      try {
        if (!serviceStarted) {
          startForegroundService();
        } else {
          callScreenRecord();
        }
      } catch (IllegalArgumentException e) {
        callbackContext.error("Illegal Argument Exception.");
        PluginResult r = new PluginResult(PluginResult.Status.ERROR);
        callbackContext.sendPluginResult(r);
      }
    }
  }

  // startForegroundService()
  // Start foreground service
  // --------------------
  public void startForegroundService() {
    Activity activity = cordova.getActivity();
    Intent bindIntent = new Intent(activity, ScreenRecordService.class);
    activity.getApplicationContext().bindService(bindIntent, this, 0);
    activity.getApplicationContext().startService(bindIntent);
  }

  // onServiceConnected()
  // Start recording when service is connected
  // --------------------
  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    ScreenRecordService.LocalBinder binder = (ScreenRecordService.LocalBinder) service;
    mScreenRecordService = binder.getService();
    serviceStarted = true;
    callScreenRecord();
  }

  // onServiceDisconnected()
  // Service disconnected
  // --------------------
  @Override
  public void onServiceDisconnected(ComponentName name) {
    Log.i(TAG, "Service disconnected");
    serviceStarted = false;
  }

  // callScreenRecord()
  // Configures screen recording, called from startRecord()
  // --------------------
  private void callScreenRecord() {
    Activity activity = cordova.getActivity();

    // Create notification
    Resources activityRes = activity.getResources();
    int notifkResId = activityRes.getIdentifier("ic_notification",
        "drawable", activity.getPackageName());
    mScreenRecordService.showNotification(notifTitle, notifText,
        activity.getApplicationContext(), notifkResId);

    // Get display metrics
    DisplayMetrics displayMetrics = new DisplayMetrics();
    activity.getWindowManager()
        .getDefaultDisplay().getMetrics(displayMetrics);
    mScreenDensity = displayMetrics.densityDpi;
    mWidth = displayMetrics.widthPixels;
    mHeight = displayMetrics.heightPixels;

    // Create Media Recorder object
    mMediaRecorder = new MediaRecorder();
    mProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

    // Ask for write to external storage permission
    cordova.requestPermission(this, WRITE_EXTERNAL_STORAGE_CODE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE);

    // Ask for screen recording permission
    Intent captureIntent = mProjectionManager.createScreenCaptureIntent();
    cordova.startActivityForResult(this, captureIntent, SCREEN_RECORD_CODE);
  }

  // onRequestPermissionsResult()
  // Real start of recording, called from callScreenRecord()
  // --------------------
  @Override
  public void onRequestPermissionResult(int requestCode,
      String[] permissions, int[] grantResults) throws JSONException {
    if (requestCode == WRITE_EXTERNAL_STORAGE_CODE) {
      if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Log.d(TAG, "Permission for external storage write granted.");
      } else {
        Log.d(TAG, "Permission for external storage write denied.");
        callbackContext.error("Permission for external storage write denied.");
      }
    }
  }

  // onActivityResult()
  // After permission for screen recording granted, called from callScreenRecord()
  // --------------------
  // onActivityResult()
  // After permission for screen recording granted, called from callScreenRecord()
  // --------------------
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {

    Log.d(TAG, "resultCode: " + resultCode);
    Log.d(TAG, "Build.VERSION.SDK_INT: " + Build.VERSION.SDK_INT);
    Log.d(TAG, "Build.VERSION_CODES.Q: " + Build.VERSION_CODES.Q);

    if (requestCode == SCREEN_RECORD_CODE) {
      context = cordova.getActivity().getApplicationContext();

      // File cacheDir = context.getCacheDir();
      // File videoFile = new File(cacheDir, fileName);
      // filePath = videoFile.getAbsolutePath();

      // ----------------不放在相册里
      // // Create output file path
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + "PR-DC");
        contentValues.put(MediaStore.Video.Media.IS_PENDING, true);
        contentValues.put(MediaStore.Video.Media.TITLE, fileName);
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        mUri = context.getContentResolver()
            .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
        Log.d(TAG, "Output file: " + mUri.toString());
      } else {
        File file = new File(context.getExternalFilesDir("PR-DC"), fileName);
        filePath = file.getAbsolutePath();
        Log.d(TAG, "Output file: " + filePath);
      }

      // Set MediaRecorder options following correct order
      try {
        Log.d(TAG, "Configuring MediaRecorder in correct order");
//
//        // Step 1: Set audio and video sources
//        if (recordAudio) {
//          mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//          Log.d(TAG, "Audio source set to MIC");
//        }
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//        Log.d(TAG, "Video source set to SURFACE");
//
//        // Step 2: Set output format
//        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        Log.d(TAG, "Output format set to MPEG_4");
//
//        // Step 3: Set encoders
//        if (recordAudio) {
//          mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//          Log.d(TAG, "Audio encoder set to AAC");
//        }
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        Log.d(TAG, "Video encoder set to H264");
//
//        // Step 4: Set video parameters
//        mMediaRecorder.setVideoSize(mWidth, mHeight);
//
//        // Use more conservative bitrate to prevent errors
//        int safeBitRate = Math.min(mBitRate, 5000000); // Cap at 5Mbps
//        mMediaRecorder.setVideoEncodingBitRate(safeBitRate);
//
//        // Use more conservative frame rate
//        int safeFrameRate = Math.min(FRAME_RATE, 30); // Cap at 30fps
//        mMediaRecorder.setVideoFrameRate(safeFrameRate);
//
//        mMediaRecorder.setOrientationHint(270);
//       // Log.d(TAG, "Video parameters configured: {}x{}, bitrate: {}, fps: {}", mWidth, mHeight, safeBitRate, safeFrameRate);

        final int VIDEO_WIDTH = 1280;
        final int VIDEO_HEIGHT = 720;
        final int VIDEO_BITRATE = 2000000; // 2Mbps
        final int VIDEO_FRAMERATE = 20;    // 20fpsmMediaRecorder = new MediaRecorder();// 1. 视频源
        if (recordAudio) {
          mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
          Log.d(TAG, "Audio source set to MIC");
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);// 2. 输出格式
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);// 3. 视频编码
        if (recordAudio) {
          mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
          Log.d(TAG, "Audio encoder set to AAC");
        }
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);// 4. 视频参数
        //mMediaRecorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
        mMediaRecorder.setVideoSize(mWidth, Math.min(mHeight, 1920));

        mMediaRecorder.setVideoEncodingBitRate(VIDEO_BITRATE);
        mMediaRecorder.setVideoFrameRate(VIDEO_FRAMERATE);// 5. 屏幕方向（可选，视你的屏幕方向而定）
        mMediaRecorder.setOrientationHint(270);


        // Step 5: Set output file
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          mMediaRecorder.setOutputFile(context.getContentResolver()
              .openFileDescriptor(mUri, "rw")
              .getFileDescriptor());
          Log.d(TAG, "Output file set via content resolver");
        } else {
          mMediaRecorder.setOutputFile(filePath);
          Log.d(TAG, "Output file set directly: " + filePath);
        }

        // Step 6: Prepare MediaRecorder
        Log.d(TAG, "Preparing MediaRecorder");
        mMediaRecorder.prepare();
        Log.d(TAG, "MediaRecorder preparation completed successfully");
        
      } catch (Exception e) {
        Log.e(TAG, "MediaRecorder configuration failed", e);
        if (mMediaRecorder != null) {
          try {
            mMediaRecorder.release();
          } catch (Exception releaseException) {
            Log.e(TAG, "Failed to release MediaRecorder after configuration error", releaseException);
          }
          mMediaRecorder = null;
        }
        callbackContext.error("MediaRecorder configuration failed: " + e.getMessage());
        return;
      }

      // Create virtual display
      try {

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        
        // Register callback for Android 14+ (API 34+)
        if (Build.VERSION.SDK_INT >= 34) {
          mMediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
              Log.d(TAG, "MediaProjection stopped");
            }
          }, null);
        }

      } catch (Exception e) {
        e.printStackTrace();
        Log.d(TAG, "mMediaProjection: ", e);

      }

      mVirtualDisplay = mMediaProjection.createVirtualDisplay("MainActivity", mWidth, mHeight, mScreenDensity,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          mMediaRecorder.getSurface(), null, null);

      // MediaRecorder onError callback
      mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mediaRecorder, int what, int extra) {
          String errorMsg = "MediaRecorder error: what=" + what + ", extra=" + extra;
          Log.e(TAG, errorMsg);
          
          // Clean up resources when error occurs
          isRecording = false;
          try {
            if (mVirtualDisplay != null) {
              mVirtualDisplay.release();
              mVirtualDisplay = null;
            }
            if (mMediaProjection != null) {
              mMediaProjection.stop();
              mMediaProjection = null;
            }
            if (mScreenRecordService != null) {
              mScreenRecordService.removeNotification();
            }
          } catch (Exception cleanupException) {
            Log.e(TAG, "Error during cleanup: " + cleanupException.getMessage());
          }
          
          callbackContext.error(errorMsg);
        }
      });

      // MediaRecorder onInfo callback
      mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
          Log.i(TAG, "onInfo: what = " + what + " extra = " + what);
        }
      });

      // Start recording
      mMediaRecorder.start();
      isRecording = true;
      Log.d(TAG, "Screenrecord service is running");
      callbackContext.success("Screenrecord service is running");

      // Sometimes we do not need to show the app
      if (isHide) {
        cordova.getActivity().moveTaskToBack(true);
      }

      if (mMediaProjection == null) {
        Log.e(TAG, "No screen recording in process");
        callbackContext.error("No screen recording in process");
        return;
      }
    }
  }

  // stopRecord()
  // Stop screen recording
  // --------------------
   private void stopRecord() {
    try {
      // 1. 释放虚拟显示
      if (mVirtualDisplay != null) {
        mVirtualDisplay.release();
        mVirtualDisplay = null;
      }
      // 2. 停止 MediaProjection
      if (mMediaProjection != null) {
        mMediaProjection.stop();
        mMediaProjection = null;
      }
      // 3. 停止并释放录制
      if (mMediaRecorder != null) {
        mMediaRecorder.setOnErrorListener(null);
        mMediaRecorder.setOnInfoListener(null);
        if (isRecording) {
          try {
            mMediaRecorder.stop();
          } catch (RuntimeException e) {
            Log.d(TAG, "MediaRecorder stop failed: " + e.getMessage());
          }
          isRecording = false;
        }
        mMediaRecorder.reset();
        mMediaRecorder.release();
        mMediaRecorder = null;
      } else {
        callbackContext.error("No screen recording in process");
        return;
      }
      // 4. 移除通知
      if (mScreenRecordService != null) {
        mScreenRecordService.removeNotification();
      }
      Log.d(TAG, "Screen recording finished.");

      // 5. 更新 IS_PENDING 状态
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.IS_PENDING, false);
        context.getContentResolver().update(mUri, contentValues, null, null);

        filePath = mUri.toString();

        // 6. 手动刷新媒体库，增加兼容性
        MediaScannerConnection.scanFile(
                context,
                new String[]{filePath},
                new String[]{"video/mp4"},
                null
        );
      } else {
        // 老系统，插入到媒体库
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.TITLE, fileName);
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.DATA, filePath);
        context.getContentResolver()
                .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);

        // 也可刷新
        MediaScannerConnection.scanFile(
                context,
                new String[]{filePath},
                new String[]{"video/mp4"},
                null
        );
      }

      // 7. 最后返回路径
      callbackContext.success(filePath);

    } catch (Exception e) {
      e.printStackTrace();
      Log.d(TAG, "stopRecord Error: ", e);
      callbackContext.error("stopRecord Error: " + e.getMessage());
    }
  }
}