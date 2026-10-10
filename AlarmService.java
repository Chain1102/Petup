package __PKG__;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;

/** Foreground service: เล่นเสียงปลุกวน + สั่น + เปิดหน้าจอ Quest (full-screen intent) จนกว่าแอปจะสั่งหยุด (หรือครบ 10 นาที) */
public class AlarmService extends Service {
  static final String CH = "petup_alarm", CH_FB = "petup_alarm_fb";
  static final int NID = 4242;
  static volatile boolean ringing = false;
  static volatile String label = "";

  private MediaPlayer mp;
  private Vibrator vib;
  private PowerManager.WakeLock wl;
  private final Handler h = new Handler(Looper.getMainLooper());

  static Uri alarmUri() {
    Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    return u;
  }

  static void channels(Context c) {
    if (Build.VERSION.SDK_INT < 26) return;
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    NotificationChannel a = new NotificationChannel(CH, "ปลุก PetUp", NotificationManager.IMPORTANCE_HIGH);
    a.setSound(null, null); a.enableVibration(false); a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    nm.createNotificationChannel(a);
    NotificationChannel b = new NotificationChannel(CH_FB, "ปลุก PetUp (สำรอง)", NotificationManager.IMPORTANCE_HIGH);
    b.setSound(alarmUri(), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
    b.enableVibration(true); b.setVibrationPattern(new long[]{0, 800, 600, 800}); b.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    nm.createNotificationChannel(b);
  }

  static Notification build(Context c, String lb, String ch) {
    Intent o = new Intent(c, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra("petup_alarm", true);
    PendingIntent pi = PendingIntent.getActivity(c, 1, o, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent del = PendingIntent.getService(c, 2, new Intent(c, AlarmService.class).setAction("STOP"),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    String text = (lb == null || lb.isEmpty()) ? "ถึงเวลาตื่นแล้ว แตะเพื่อทำ Quest" : lb + " — แตะเพื่อทำ Quest";
    return new NotificationCompat.Builder(c, ch)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("⏰ PetUp").setContentText(text)
        .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true).setAutoCancel(false)
        .setContentIntent(pi).setFullScreenIntent(pi, true).setDeleteIntent(del).build();
  }

  static void fallback(Context c, String lb) {
    channels(c);
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NID, build(c, lb, CH_FB));
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
    label = intent == null ? "" : intent.getStringExtra("label");
    ringing = true;
    channels(this);
    Notification n = build(this, label, CH);
    if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else startForeground(NID, n);

    wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "petup:alarm");
    wl.acquire(10 * 60 * 1000L);
    try { // ถ้าเสียงปลุกถูกปิดไว้ 0 ให้เปิดครึ่งหนึ่ง
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2, 0);
    } catch (Exception e) { }
    try {
      mp = new MediaPlayer();
      mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
      mp.setDataSource(this, alarmUri());
      mp.setLooping(true); mp.prepare(); mp.start();
    } catch (Exception e) { }
    try {
      vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      long[] p = {0, 800, 600};
      if (Build.VERSION.SDK_INT >= 26)
        vib.vibrate(VibrationEffect.createWaveform(p, 0), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
      else vib.vibrate(p, 0);
    } catch (Exception e) { }
    h.postDelayed(this::stopSelf, 10 * 60 * 1000L); // กันดังไม่หยุดถ้าแอปค้าง
    return START_NOT_STICKY;
  }

  @Override public void onDestroy() {
    ringing = false;
    h.removeCallbacksAndMessages(null);
    try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception e) { }
    try { if (vib != null) vib.cancel(); } catch (Exception e) { }
    try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
    stopForeground(STOP_FOREGROUND_REMOVE);
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NID);
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent i) { return null; }
}
