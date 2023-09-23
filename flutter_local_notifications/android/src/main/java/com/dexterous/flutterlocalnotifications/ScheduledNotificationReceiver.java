package com.dexterous.flutterlocalnotifications;

import static com.dexterous.flutterlocalnotifications.FlutterLocalNotificationsPlugin.getAlarmManager;
import static com.dexterous.flutterlocalnotifications.FlutterLocalNotificationsPlugin.getBroadcastPendingIntent;
import static com.dexterous.flutterlocalnotifications.FlutterLocalNotificationsPlugin.getNotificationManager;
import static com.dexterous.flutterlocalnotifications.FlutterLocalNotificationsPlugin.removeNotificationFromCache;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Vibrator;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;

import com.dexterous.flutterlocalnotifications.models.NotificationDetails;
import com.dexterous.flutterlocalnotifications.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Created by michaelbui on 24/3/18. */
@Keep
public class ScheduledNotificationReceiver extends BroadcastReceiver {

  private static final String TAG = "ScheduledNotifReceiver";
  private static int notificationLen = 0;

  @Override
  @SuppressWarnings("deprecation")
  public void onReceive(final Context context, Intent intent) {
    String notificationDetailsJson =
        intent.getStringExtra(FlutterLocalNotificationsPlugin.NOTIFICATION_DETAILS);
    if (StringUtils.isNullOrEmpty(notificationDetailsJson)) {
      // This logic is needed for apps that used the plugin prior to 0.3.4

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        onPreShowNotification(context);
      }

      Notification notification;
      int notificationId = intent.getIntExtra("notification_id", 0);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        notification = intent.getParcelableExtra("notification", Notification.class);
      } else {
        notification = intent.getParcelableExtra("notification");
      }

      if (notification == null) {
        // This means the notification is corrupt
        removeNotificationFromCache(context, notificationId);
        Log.e(TAG, "Failed to parse a notification from  Intent. ID: " + notificationId);
        return;
      }

      notification.when = System.currentTimeMillis();
      NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
      notificationManager.notify(notificationId, notification);
      boolean repeat = intent.getBooleanExtra("repeat", false);
      if (!repeat) {
        removeNotificationFromCache(context, notificationId);
      }
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        onPreShowNotification(context);
      }
      Gson gson = FlutterLocalNotificationsPlugin.buildGson();
      Type type = new TypeToken<NotificationDetails>() {}.getType();
      NotificationDetails notificationDetails = gson.fromJson(notificationDetailsJson, type);

      FlutterLocalNotificationsPlugin.showNotification(context, notificationDetails);
      FlutterLocalNotificationsPlugin.scheduleNextNotification(context, notificationDetails);
    }
  }

  private  StatusBarNotification[] getActiveNotifications(final Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return new StatusBarNotification[0];
    }
    NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    try {
      return notificationManager.getActiveNotifications();
    } catch (Throwable e) {
        Log.e(TAG, "Failed to get active notifications", e);
        return new StatusBarNotification[0];
    }
  }

  private void cancelNotification(Context context, Integer id, String tag) {
    Intent intent = new Intent(context, ScheduledNotificationReceiver.class);
    PendingIntent pendingIntent = getBroadcastPendingIntent(context, id, intent);
    AlarmManager alarmManager = getAlarmManager(context);
    alarmManager.cancel(pendingIntent);
    NotificationManagerCompat notificationManager = getNotificationManager(context);
    if (tag == null) {
      notificationManager.cancel(id);
    } else {
      notificationManager.cancel(tag, id);
    }
    removeNotificationFromCache(context, id);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void onPreShowNotification(Context context) {
    //1. 删除之前的通知, 只显示一条喝水通知
    StatusBarNotification[] sn = getActiveNotifications(context);
    int len = sn.length;
    for (int i = 0; i < len; i++) {
      if(sn[i].getId() > 0 && sn[i].getId() < 100){
        cancelNotification(context, sn[i].getId(), sn[i].getTag());
      }
    }
    //2. 播放声音
    MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.notice);
    mediaPlayer.start();
    //3. 震动
    Vibrator vib = (Vibrator) context.getSystemService(Service.VIBRATOR_SERVICE);
    vib.vibrate(new long[] {500, 1000, 500, 1000}, -1);
  }
}
