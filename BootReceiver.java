package __PKG__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** ตั้งปลุกใหม่หลังรีสตาร์ท / อัปเดตแอป / เปลี่ยนเวลาหรือเขตเวลา */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context ctx, Intent intent) { AlarmStore.scheduleAll(ctx); }
}
