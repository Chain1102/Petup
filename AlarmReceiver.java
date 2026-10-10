package __PKG__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

/** ระบบเรียกตอนถึงเวลาปลุก (แอปไม่ต้องเปิดอยู่) */
public class AlarmReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context ctx, Intent intent) {
    String label = intent.getStringExtra("label");
    try {
      ContextCompat.startForegroundService(ctx, new Intent(ctx, AlarmService.class).putExtra("label", label));
    } catch (Exception e) {
      AlarmService.fallback(ctx, label); // เริ่ม service ไม่ได้ -> แจ้งเตือนเสียงปลุก + เต็มจอ
    }
    AlarmStore.scheduleAll(ctx); // ตั้งปลุกของวันถัดไป
  }
}
