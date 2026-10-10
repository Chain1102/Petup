package __PKG__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/** เก็บรายการปลุกในเครื่อง และตั้งปลุกกับ AlarmManager (setAlarmClock = ปลุกตรงเวลา แม้ Doze/จอดับ) */
public class AlarmStore {
  static final String PREFS = "petup_alarms";

  static void save(Context c, String json) { c.getSharedPreferences(PREFS, 0).edit().putString("list", json).apply(); }
  static String load(Context c) { return c.getSharedPreferences(PREFS, 0).getString("list", "[]"); }

  static long next(int h, int m) {
    Calendar t = Calendar.getInstance();
    t.set(Calendar.HOUR_OF_DAY, h); t.set(Calendar.MINUTE, m); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
    if (t.getTimeInMillis() <= System.currentTimeMillis() + 1000) t.add(Calendar.DAY_OF_MONTH, 1);
    return t.getTimeInMillis();
  }

  static PendingIntent pi(Context c, int id, String label) {
    Intent i = new Intent(c, AlarmReceiver.class).setAction("__PKG__.ALARM").putExtra("label", label);
    return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static boolean canExact(Context c) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
  }

  static void set(Context c, int id, long at, String label) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent show = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi(c, id, label));
  }

  /** ตั้งปลุกครั้งถัดไปของทุกรายการ (เรียกซ้ำได้ ตัวเดิมถูกแทนที่) คืนจำนวนที่ตั้งได้ */
  static int scheduleAll(Context c) {
    if (!canExact(c)) return 0;
    int n = 0;
    try {
      JSONArray a = new JSONArray(load(c));
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        set(c, o.getInt("id"), next(o.getInt("hour"), o.getInt("minute")), o.optString("label", ""));
        n++;
      }
    } catch (Exception e) { /* ข้อมูลเสีย: ข้าม */ }
    return n;
  }

  static void cancelAll(Context c) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    try {
      JSONArray a = new JSONArray(load(c));
      for (int i = 0; i < a.length(); i++) am.cancel(pi(c, a.getJSONObject(i).getInt("id"), ""));
    } catch (Exception e) { }
  }
}
