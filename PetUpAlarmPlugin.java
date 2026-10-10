package __PKG__;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PetUpAlarm")
public class PetUpAlarmPlugin extends Plugin {

  /** schedule({alarms:[{id,hour,minute,label}]}) */
  @PluginMethod public void schedule(PluginCall c) {
    Context x = getContext();
    JSArray a = c.getArray("alarms", new JSArray());
    AlarmStore.cancelAll(x);
    AlarmStore.save(x, a.toString());
    int n = AlarmStore.scheduleAll(x);
    JSObject r = new JSObject(); r.put("scheduled", n); r.put("exact", AlarmStore.canExact(x));
    c.resolve(r);
  }

  /** ปลุกทดสอบใน N วินาที (ใช้ลองตอนล็อกจอ) */
  @PluginMethod public void testIn(PluginCall c) {
    Context x = getContext();
    if (!AlarmStore.canExact(x)) { c.reject("ต้องเปิดสิทธิ์ปลุกตรงเวลาก่อน"); return; }
    int s = c.getInt("seconds", 10);
    AlarmStore.set(x, 9999, System.currentTimeMillis() + s * 1000L, c.getString("label", "ทดสอบปลุก"));
    c.resolve();
  }

  @PluginMethod public void status(PluginCall c) {
    JSObject r = new JSObject(); r.put("ringing", AlarmService.ringing); r.put("label", AlarmService.label == null ? "" : AlarmService.label);
    c.resolve(r);
  }

  @PluginMethod public void stop(PluginCall c) {
    Context x = getContext();
    x.stopService(new Intent(x, AlarmService.class));
    ((NotificationManager) x.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(AlarmService.NID);
    c.resolve();
  }

  @PluginMethod public void readiness(PluginCall c) {
    Context x = getContext();
    JSObject r = new JSObject();
    r.put("exact", AlarmStore.canExact(x));
    NotificationManager nm = (NotificationManager) x.getSystemService(Context.NOTIFICATION_SERVICE);
    r.put("fullScreen", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent());
    r.put("notif", NotificationManagerCompat.from(x).areNotificationsEnabled());
    r.put("battery", ((PowerManager) x.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(x.getPackageName()));
    c.resolve(r);
  }

  /** fix({type: exact|fullScreen|notif|battery}) เปิดหน้าตั้งค่าที่เกี่ยวข้อง */
  @PluginMethod public void fix(PluginCall c) {
    Context x = getContext();
    String t = c.getString("type", ""), pk = x.getPackageName();
    Intent i = null;
    if (t.equals("exact") && Build.VERSION.SDK_INT >= 31) i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pk));
    else if (t.equals("fullScreen") && Build.VERSION.SDK_INT >= 34) i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + pk));
    else if (t.equals("battery")) i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    else if (t.equals("notif")) i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pk);
    if (i != null) {
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try { x.startActivity(i); }
      catch (Exception e) { x.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pk)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    }
    c.resolve();
  }
}
