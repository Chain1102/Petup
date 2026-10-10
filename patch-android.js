// ติดตั้งโมดูลปลุกเนทีฟลงโปรเจกต์ Android (รันซ้ำได้ ไม่ซ้ำซ้อน)
const fs = require('fs'), path = require('path');
const appId = JSON.parse(fs.readFileSync('capacitor.config.json', 'utf8')).appId;
const root = 'android/app/src/main';
if (!fs.existsSync(root)) { console.error('ยังไม่มีโปรเจกต์ Android: รัน npx cap add android ก่อน'); process.exit(1); }

// 1) คัดลอกไฟล์ Java
const dir = path.join(root, 'java', ...appId.split('.'));
fs.mkdirSync(dir, { recursive: true });
for (const f of fs.readdirSync('native/android')) {
  fs.writeFileSync(path.join(dir, f), fs.readFileSync(path.join('native/android', f), 'utf8').replace(/__PKG__/g, appId));
}
console.log('คัดลอกไฟล์ Java ไปที่', dir);

// 2) แก้ AndroidManifest.xml
const mp = path.join(root, 'AndroidManifest.xml');
let x = fs.readFileSync(mp, 'utf8');
const perms = [
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.USE_EXACT_ALARM',
  'android.permission.USE_FULL_SCREEN_INTENT',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
  'android.permission.WAKE_LOCK',
  'android.permission.VIBRATE',
  'android.permission.RECEIVE_BOOT_COMPLETED'
].filter(p => !x.includes(p)).map(p => `    <uses-permission android:name="${p}" />`);
if (perms.length) x = x.replace('</manifest>', perms.join('\n') + '\n</manifest>');

if (!x.includes('.AlarmService')) {
  const comps = `
        <service android:name=".AlarmService" android:exported="false" android:foregroundServiceType="mediaPlayback" />
        <receiver android:name=".AlarmReceiver" android:exported="false" />
        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>
    `;
  x = x.replace('</application>', comps + '</application>');
}
if (!x.includes('android:showWhenLocked'))
  x = x.replace(/(<activity[^>]*android:name="\.MainActivity")/, '$1 android:showWhenLocked="true" android:turnScreenOn="true"');
fs.writeFileSync(mp, x);
console.log('แก้ AndroidManifest.xml แล้ว');
