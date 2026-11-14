package vn.herotran.tvcontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

// Man hinh TV - v1.26: giao dien XML giong app 2 (nen toi, card bo tron).
// Logic giu nguyen app 1, chi thay UI + cac doi sau:
// - Da XOA phan "Nhat ky khoi dong" khoi man hinh TV (Cfg.log van chay nen de chan doan, chi khong hien nua).
// - Da XOA nut "Chan Cai dat TV": Cai dat TV luon mo, khong bi chan.
// - Chay nen khi thu nho: KHONG dung finish/finishAndRemoveTask, chi moveTaskToBack();
//   truoc khi thu nho goi Cfg.ensureService() + KeepService.start() de dich vu foreground giu TvService song.
//   KeepService (START_STICKY, foreground) + WakeJob + BootReceiver giu app song khi TV tat/bat lai.
public class TvActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView info, urlTv, codeTv;
    private EditText urlEt, codeEt;
    private Button saveBtn, codeSaveBtn, iconBtn, forceBtn;
    private View rootView;
    static volatile boolean visible = false;
    static volatile long createdAt = 0;
    private boolean alive;
    private volatile boolean checking;

    private void refreshForceBtn() {
        forceBtn.setText(Cfg.forceOffEnabled(this)
                ? "Nút nguồn điều khiển = TẮT NGUỒN THẬT: đang BẬT (bấm để tắt)"
                : "Nút nguồn điều khiển = TẮT NGUỒN THẬT: đang TẮT (bấm để bật)");
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            Cfg.ensureService(TvActivity.this);
            info.setText(build());
            h.postDelayed(this, 2000);
        }
    };

    private String build() {
        StringBuilder b = new StringBuilder();
        if (!Cfg.urlOk(this)) b.append("!! CHƯA CÓ URL FIREBASE - bấm \"Thay đổi URL\" để nhập !!\n\n");
        boolean en = Cfg.serviceEnabled(this);
        b.append("Dịch vụ trợ năng: ").append(en ? "ĐÃ BẬT" : "CHƯA BẬT").append("\n");
        b.append("Kết nối: ").append(TvService.connected ? TvService.lastMsg : "dịch vụ chưa chạy").append("\n");
        long end = Cfg.sp(this).getLong("endAt", 0);
        if (Cfg.sp(this).getBoolean("locked", false)) b.append("Hẹn giờ: đang KHÓA\n");
        else if (end > System.currentTimeMillis())
            b.append("Hẹn giờ: sẽ khóa/tắt TV lúc ").append(new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(end))).append("\n");
        else b.append("Hẹn giờ: không giới hạn thời gian xem TV\n");
        b.append("Tắt nguồn TV (ADB nội bộ): ").append(TvService.powerMsg.isEmpty() ? "chưa kiểm tra - bấm nút \"Kiểm tra quyền tắt nguồn\"" : TvService.powerMsg).append("\n");
        if (TvService.msgOn) b.append("Đang hiển thị tin nhắn\n");
        b.append("\nCài đặt TV luôn mở, không bị chặn.\n");
        if (!en) {
            b.append("\nBật nhanh nhất - chạy ADB trên máy tính (1 lần, sau đó app tự bật mỗi khi mở/khởi động TV):\n");
            b.append("adb shell pm grant ").append(getPackageName()).append(" ").append(Cfg.WSS).append("\n");
            b.append("\nHoặc bật thẳng bằng ADB:\n");
            b.append("adb shell settings put secure enabled_accessibility_services ").append(Cfg.serviceComponent(this)).append("\n");
            b.append("adb shell settings put secure accessibility_enabled 1\n");
        } else if (!Cfg.canWriteSecure(this)) {
            b.append("\nLưu ý: chưa cấp quyền tự bật lại dịch vụ. Chạy 1 lần trên máy tính:\n");
            b.append("adb shell pm grant ").append(getPackageName()).append(" ").append(Cfg.WSS).append("\n");
        }
        // v1.26: da XOA dong "Nhat ky khoi dong" khoi man hinh TV.
        b.append("\nMở lại app khi icon bị ẩn:\nadb shell am start -n ").append(getPackageName()).append("/.MainActivity\n");
        return b.toString();
    }

    private void refreshUrl() {
        urlTv.setText("URL Firebase: " + (Cfg.urlOk(this) ? Cfg.dbUrl(this) : "(chưa có)"));
    }

    private void refreshCode() {
        codeTv.setText("Mã TV: " + Cfg.tvCode(this));
    }

    private void refreshIconBtn() {
        iconBtn.setText(Cfg.iconHidden(this) ? "Hiện lại biểu tượng app" : "Ẩn biểu tượng app khỏi màn hình chính");
    }

    // Thu nho that su: dam bao dich vu nen chay truoc, roi chi day Activity xuong nen.
    // Khong goi finish(): Activity van ton tai, dich vu giu nen khong bi he thong diet.
    private void minimizeNow() {
        try { Cfg.ensureService(this); } catch (Exception ignored) { }
        try { KeepService.start(this); } catch (Exception ignored) { }
        try { moveTaskToBack(true); } catch (Exception ignored) { }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        createdAt = android.os.SystemClock.elapsedRealtime();
        Cfg.hideFromRecents(this, true);
        Cfg.log(this, "Mở app" + (getIntent() != null && getIntent().getBooleanExtra("minimize", false) ? " (tự động)" : " (thủ công)")
                + ": trợ năng " + (Cfg.serviceEnabled(this) ? "đang bật" : "ĐANG TẮT")
                + ", dịch vụ " + (TvService.connected ? "đã kết nối" : "CHƯA kết nối"));
        Cfg.recover(this, "mở app");
        setContentView(R.layout.activity_tv);
        rootView = findViewById(android.R.id.content);
        info = findViewById(R.id.info);
        urlTv = findViewById(R.id.urlTv);
        codeTv = findViewById(R.id.codeTv);
        urlEt = findViewById(R.id.urlEt);
        codeEt = findViewById(R.id.codeEt);
        saveBtn = findViewById(R.id.urlSaveBtn);
        codeSaveBtn = findViewById(R.id.codeSaveBtn);
        iconBtn = findViewById(R.id.btnIcon);
        forceBtn = findViewById(R.id.btnForceOff);
        refreshUrl();
        refreshCode();
        urlEt.setText(Cfg.savedDbUrl(this));
        codeEt.setText(Cfg.savedTvCode(this));

        findViewById(R.id.btnChangeUrl).setOnClickListener(v -> {
            boolean show = urlEt.getVisibility() != View.VISIBLE;
            urlEt.setVisibility(show ? View.VISIBLE : View.GONE);
            saveBtn.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) urlEt.requestFocus();
        });
        saveBtn.setOnClickListener(v -> {
            Cfg.setDbUrl(this, urlEt.getText().toString());
            urlEt.setText(Cfg.savedDbUrl(this));
            urlEt.setVisibility(View.GONE);
            saveBtn.setVisibility(View.GONE);
            refreshUrl();
            info.setText(build());
        });
        findViewById(R.id.btnChangeCode).setOnClickListener(v -> {
            boolean show = codeEt.getVisibility() != View.VISIBLE;
            codeEt.setVisibility(show ? View.VISIBLE : View.GONE);
            codeSaveBtn.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) codeEt.requestFocus();
        });
        codeSaveBtn.setOnClickListener(v -> {
            if (!Cfg.setTvCode(this, codeEt.getText().toString())) {
                info.setText("Mã TV không hợp lệ: chỉ gồm chữ a-z và số 0-9, dài 4-40 ký tự\n\n" + build());
                return;
            }
            codeEt.setText(Cfg.savedTvCode(this));
            codeEt.setVisibility(View.GONE);
            codeSaveBtn.setVisibility(View.GONE);
            refreshCode();
            info.setText(build());
        });

        findViewById(R.id.btnAcc).setOnClickListener(v -> {
            if (Cfg.enableService(this)) { info.setText(build()); return; }
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                info.setText("TV không có màn hình Trợ năng, hãy dùng ADB (xem hướng dẫn bên dưới)\n\n" + build());
            }
        });
        findViewById(R.id.btnPowerCheck).setOnClickListener(v -> checkPower());

        // v1.26: da XOA nut "Chan Cai dat TV" - Cai dat TV luon mo, khong bi chan.

        forceBtn.setOnClickListener(v -> {
            boolean on = !Cfg.forceOffEnabled(this);
            Cfg.sp(this).edit().putBoolean("forceOff", on).apply();
            if (on) Cfg.startWatcher(this, true); else Cfg.stopWatcher(this);
            refreshForceBtn();
        });
        refreshForceBtn();

        iconBtn.setOnClickListener(v -> {
            Cfg.setIconHidden(this, !Cfg.iconHidden(this));
            refreshIconBtn();
        });
        refreshIconBtn();

        findViewById(R.id.btnMinimize).setOnClickListener(v -> minimizeNow());

        findViewById(R.id.btnSwitchMode).setOnClickListener(v -> {
            Cfg.setRole(this, "");
            Cfg.hideFromRecents(this, false);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        findViewById(R.id.btnAcc).requestFocus();
        handleLaunch(getIntent());
    }

    private void checkPower() {
        if (checking) return;
        checking = true;
        TvService.powerMsg = "Đang kết nối ADB... nếu TV hiện hộp thoại, hãy chọn \"Luôn cho phép\" rồi bấm OK";
        info.setText(build());
        new Thread(() -> {
            try {
                AdbLocal.exec(getFilesDir(), 5555, "echo tvguard_ok");
                Cfg.sp(this).edit().putBoolean("adbOk", true).apply();
                Cfg.selfGrant(this);
                Cfg.startWatcher(this, true);
                TvService.powerMsg = "SẴN SÀNG - app có quyền tắt nguồn TV [" + AdbLocal.lastKeyInfo + "]";
            } catch (Exception e) {
                TvService.powerMsg = "Chưa được: " + AdbLocal.explain(e) + " [" + AdbLocal.lastKeyInfo + "]";
            } finally {
                checking = false;
            }
            runOnUiThread(() -> info.setText(build()));
        }, "tvguard-adbcheck").start();
    }

    private void handleLaunch(Intent it) {
        if (it == null || !it.getBooleanExtra("minimize", false)) return;
        it.removeExtra("minimize");
        if (rootView != null) rootView.setAlpha(0f);
        h.postDelayed(() -> {
            minimizeNow();
            if (rootView != null) rootView.setAlpha(1f);
        }, 1200);
    }

    @Override
    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        setIntent(it);
        handleLaunch(it);
    }

    @Override public void onBackPressed() { minimizeNow(); }

    @Override protected void onResume() { super.onResume(); visible = true; alive = true; h.post(refresh); }
    @Override protected void onPause() { super.onPause(); visible = false; alive = false; }
}
