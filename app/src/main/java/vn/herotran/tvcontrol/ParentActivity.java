package vn.herotran.tvcontrol;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// Dien thoai phu huynh - v1.26: giao dien XML giong app 2 (nen toi, card bo tron).
// Logic giu nguyen app 1 (lenh minutes/mode/id, status ts/endAt/remain...), chi thay UI + cac doi sau:
// - Chup man hinh TV dua len dau (card hanh dong dau tien sau Trang thai TV).
// - Da BO nut "Cong them", BO 3 nut Bao ve TV (Mo Cai dat 10 phut / Chan / Tat chan).
// - Them card Nhat ky xem TV hom nay: TV duoc Mo / Tat luc nao (doc tu /watchlog.json).
// - Nut bam xep theo hang 2 cot, padding gon, toi uu khong gian hien thi.
public class ParentActivity extends Activity {
    static final int PICK_LOCK = 1, PICK_SEND = 2;
    private final Handler h = new Handler(Looper.getMainLooper());
    private EditText codeEt, minEt, secEt, urlEt, msgEt;
    private TextView msgCountTv;
    private TextView urlTv, codeTv;
    private TextView statusTv, msgTv, watchlogTv;
    private ImageView shotIv;
    private RadioGroup modeRg;
    private View cardConfig;
    private boolean alive;
    private long lastTs = -1, lastChange = 0;
    private volatile long lastEnd = 0;   // gio TV se khoa (epoch ms) theo trang thai TV gui ve

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Cfg.hideFromRecents(this, false);
        setContentView(R.layout.activity_parent);
        urlTv = findViewById(R.id.urlTv);
        codeTv = findViewById(R.id.codeTv);
        statusTv = findViewById(R.id.statusTv);
        msgTv = findViewById(R.id.msgTv);
        watchlogTv = findViewById(R.id.watchlogTv);
        shotIv = findViewById(R.id.shotIv);
        minEt = findViewById(R.id.minEt);
        secEt = findViewById(R.id.secEt);
        urlEt = findViewById(R.id.urlEt);
        codeEt = findViewById(R.id.codeEt);
        modeRg = findViewById(R.id.modeRg);
        msgEt = findViewById(R.id.msgEt);
        msgCountTv = findViewById(R.id.msgCountTv);
        msgEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { msgCountTv.setText(s.length() + "/300"); }
        });
        cardConfig = findViewById(R.id.cardConfig);
        urlEt.setInputType(InputType.TYPE_CLASS_TEXT);
        codeEt.setInputType(InputType.TYPE_CLASS_TEXT);
        minEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        secEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        urlEt.setText(Cfg.savedDbUrl(this));
        codeEt.setText(Cfg.parentCode(this));
        refreshUrl();
        refreshCode();

        findViewById(R.id.btnToggleConfig).setOnClickListener(v ->
                cardConfig.setVisibility(cardConfig.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        findViewById(R.id.btnChangeUrl).setOnClickListener(v -> toggleEdit(urlEt, R.id.urlSaveBtn));
        findViewById(R.id.urlSaveBtn).setOnClickListener(v -> {
            Cfg.setDbUrl(this, urlEt.getText().toString());
            urlEt.setText(Cfg.savedDbUrl(this));
            urlEt.setVisibility(View.GONE);
            findViewById(R.id.urlSaveBtn).setVisibility(View.GONE);
            refreshUrl();
            msg("Đã lưu URL");
        });
        findViewById(R.id.btnChangeCode).setOnClickListener(v -> toggleEdit(codeEt, R.id.codeSaveBtn));
        findViewById(R.id.codeSaveBtn).setOnClickListener(v -> {
            String c = codeEt.getText().toString().trim().toLowerCase();
            if (!c.isEmpty() && !Cfg.validCode(c)) { msg("Mã TV không hợp lệ: chỉ gồm a-z, 0-9, dài 4-40 ký tự"); return; }
            Cfg.setParentCode(this, c);
            codeEt.setText(Cfg.parentCode(this));
            codeEt.setVisibility(View.GONE);
            findViewById(R.id.codeSaveBtn).setVisibility(View.GONE);
            refreshCode();
            lastTs = -1;
            msg("Đã lưu mã TV");
        });

        findViewById(R.id.btnShot).setOnClickListener(v -> shot());
        findViewById(R.id.btnSetTimer).setOnClickListener(v -> timer("settimer"));
        findViewById(R.id.btnCancelTimer).setOnClickListener(v -> cmd("canceltimer"));
        findViewById(R.id.btnLock).setOnClickListener(v -> cmd("lock", "mode", selectedMode()));
        findViewById(R.id.btnUnlock).setOnClickListener(v -> cmd("unlock"));
        findViewById(R.id.btnPickLock).setOnClickListener(v -> pick(PICK_LOCK));
        findViewById(R.id.btnPickSend).setOnClickListener(v -> pick(PICK_SEND));
        findViewById(R.id.btnHideImg).setOnClickListener(v -> cmd("hideimg"));
        findViewById(R.id.btnVolUp).setOnClickListener(v -> cmd("volup"));
        findViewById(R.id.btnVolDown).setOnClickListener(v -> cmd("voldn"));
        findViewById(R.id.btnSendMsg).setOnClickListener(v -> sendMsg());
        findViewById(R.id.btnHideMsg).setOnClickListener(v -> cmd("hidemsg"));
        findViewById(R.id.btnHideIcon).setOnClickListener(v -> cmd("hideicon"));
        findViewById(R.id.btnShowIcon).setOnClickListener(v -> cmd("showicon"));
        findViewById(R.id.btnSwitchMode).setOnClickListener(v -> {
            Cfg.setRole(this, "");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void toggleEdit(EditText et, int saveBtnId) {
        boolean show = et.getVisibility() != View.VISIBLE;
        et.setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(saveBtnId).setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) et.requestFocus();
    }

    private String base() {
        String c = Cfg.parentCode(this);
        if (c.isEmpty()) return null;
        if (!Cfg.urlOk(this)) return null;
        return Cfg.dbUrl(this) + "/tv/" + c;
    }

    private String baseForSend() { return base(); }

    private void refreshCode() { codeTv.setText("Mã TV: " + Cfg.parentCode(this)); }

    private void refreshUrl() { urlTv.setText("URL Firebase: " + (Cfg.urlOk(this) ? Cfg.dbUrl(this) : "(chưa có)")); }

    private String noBase() {
        if (!Cfg.urlOk(this)) return "Chưa có URL Firebase (bấm Thay đổi URL)";
        return "Chưa nhập mã TV";
    }

    private void msg(final String s) { runOnUiThread(() -> msgTv.setText(s)); }

    private String selectedMode() {
        int id = modeRg.getCheckedRadioButtonId();
        return id == R.id.rbImage ? "image" : id == R.id.rbShutdown ? "shutdown" : "black";
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private String fmtTime(long ms) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTimeInMillis(ms);
        boolean same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
        return same ? t : t + " ngày " + new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(ms));
    }

    private String actionText(String mode) {
        if ("shutdown".equals(mode) || "sleep".equals(mode)) return "tắt nguồn TV";
        if ("image".equals(mode)) return "khóa TV (khung ảnh)";
        return "khóa TV (màn hình đen)";
    }

    // Hen gio: chi con "Dat gio" (settimer). Da BO "Cong them" (addtime).
    private void timer(String type) {
        final int min = parseInt(minEt.getText().toString(), 0);
        if (min <= 0) { msg("Nhập số phút lớn hơn 0"); return; }
        final String mode = selectedMode();
        long now = System.currentTimeMillis();
        long when = now + min * 60000L;
        String ok = "Đã đặt giờ " + min + " phút: " + fmtTime(when) + " sẽ " + actionText(mode);
        send(type, ok, "minutes", min, "mode", mode);
    }

    // Gui tin nhan len man hinh TV (toi da 300 ky tu, gioi han o XML maxLength).
    // TV hien tin nhan cho den khi bam Back / Home tren dieu khien TV.
    private void sendMsg() {
        String text = msgEt.getText().toString().trim();
        if (text.isEmpty()) { msg("Nhập tin nhắn trước"); return; }
        send("showmsg", "Đã gửi tin nhắn lên TV", "text", text);
    }

    private void cmd(final String type, final Object... kv) {
        String ok = "Đã gửi lệnh: " + type;
        if ("canceltimer".equals(type)) ok = "Đã hủy hẹn giờ - không giới hạn thời gian xem TV";
        if ("volup".equals(type)) ok = "Đã tăng âm lượng TV";
        if ("voldn".equals(type)) ok = "Đã giảm âm lượng TV";
        if ("hidemsg".equals(type)) ok = "Đã ẩn tin nhắn trên TV";
        send(type, ok, kv);
    }

    private void send(final String type, final String okMsg, final Object... kv) {
        final String base = baseForSend();
        if (base == null) { msg(noBase()); return; }
        final long id = System.currentTimeMillis();
        new Thread(() -> {
            try {
                JSONObject c = new JSONObject();
                c.put("id", id);
                c.put("type", type);
                for (int i = 0; i + 1 < kv.length; i += 2) c.put((String) kv[i], kv[i + 1]);
                Net.put(base + "/cmd.json", c.toString());
                msg(okMsg);
            } catch (Exception e) {
                msg("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private void shot() {
        final String base = baseForSend();
        if (base == null) { msg(noBase()); return; }
        msg("Đang chụp màn hình TV...");
        new Thread(() -> {
            try {
                long id = System.currentTimeMillis();
                JSONObject c = new JSONObject();
                c.put("id", id);
                c.put("type", "shot");
                Net.put(base + "/cmd.json", c.toString());
                long t0 = System.currentTimeMillis();
                while (System.currentTimeMillis() - t0 < 40000) {
                    Thread.sleep(2000);
                    String s = Net.get(base + "/shotid.json").trim();
                    if (s.equals(String.valueOf(id))) {
                        JSONObject o = Net.getObj(base + "/shot.json");
                        if (o != null && o.has("data")) {
                            final Bitmap bm = ImgUtil.fromB64(o.getString("data"));
                            runOnUiThread(() -> { shotIv.setVisibility(View.VISIBLE); shotIv.setImageBitmap(bm); msgTv.setText("Đã nhận ảnh"); });
                        } else {
                            msg("TV không chụp được (mã lỗi " + (o == null ? "?" : String.valueOf(o.optInt("err"))) + ")");
                        }
                        return;
                    }
                }
                msg("TV không phản hồi (offline?)");
            } catch (Exception e) {
                msg("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Chọn ảnh"), req);
    }

    @Override
    protected void onActivityResult(final int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        final String base = baseForSend();
        final int sec = parseInt(secEt.getText().toString(), 30);
        if (base == null) { msg(noBase()); return; }
        msg("Đang tải ảnh lên...");
        new Thread(() -> {
            try {
                byte[] data = ImgUtil.readAll(getContentResolver().openInputStream(u));
                Bitmap bm = ImgUtil.decode(data, 1280);
                if (bm == null) { msg("Không đọc được ảnh"); return; }
                long id = System.currentTimeMillis();
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("data", ImgUtil.toB64(bm, 1280, 70));
                JSONObject c = new JSONObject();
                c.put("id", id + 1);
                if (req == PICK_LOCK) {
                    Net.put(base + "/lockimg.json", o.toString());
                    c.put("type", "lockimg");
                } else {
                    Net.put(base + "/img.json", o.toString());
                    c.put("type", "showimg");
                    c.put("seconds", sec);
                }
                Net.put(base + "/cmd.json", c.toString());
                msg(req == PICK_LOCK ? "Đã gửi ảnh khung khóa (chọn 'Khung ảnh' khi hết giờ)" : "Đã gửi ảnh lên TV");
            } catch (Exception e) {
                msg("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private String describe(JSONObject s) {
        long now = System.currentTimeMillis();
        if (s == null) return "TV: chưa có dữ liệu (kiểm tra mã / bật dịch vụ trên TV)";
        long ts = s.optLong("ts");
        if (ts != lastTs) {
            lastChange = (lastTs == -1 && Math.abs(now - ts) > 300000) ? 0 : now;
            lastTs = ts;
        }
        boolean online = now - lastChange < 25000;
        StringBuilder b = new StringBuilder(online ? "TV: ONLINE" : "TV: OFFLINE");
        String mode = s.optString("mode", "black");
        lastEnd = s.optLong("endAt", 0);
        if (s.optBoolean("locked")) {
            b.append("\nĐang KHÓA (hết giờ / khóa ngay)");
            if ("shutdown".equals(mode) || "sleep".equals(mode)) {
                b.append("\nChế độ: tắt nguồn TV");
                String pw = s.optString("pw", "");
                if (!pw.isEmpty()) b.append("\nTắt nguồn: ").append(pw);
            }
        } else if (s.optBoolean("timer")) {
            long r = s.optLong("remain");
            if (lastEnd > 0) b.append("\nĐã hẹn: ").append(fmtTime(lastEnd)).append(" sẽ ").append(actionText(mode));
            b.append(String.format("\nCòn lại: %d phút %02d giây", r / 60, r % 60));
        } else b.append("\nKhông giới hạn thời gian xem TV");
        if (s.optBoolean("img")) b.append("\nĐang hiển thị ảnh của bạn");
        if (s.optBoolean("msg")) b.append("\nĐang hiển thị tin nhắn");
        // v1.26: da XOA chan Cai dat TV -> khong con dong "Cai dat TV dang duoc MO" nua.
        // Cai dat TV luon mo, khong bi chan.
        if (s.optBoolean("hidden")) b.append("\nIcon app trên TV: đang ẩn");
        return b.toString();
    }

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            final String base = base();
            if (base != null) {
                new Thread(() -> {
                    String t;
                    String wlText = "Hôm nay chưa có lịch sử Mở / Tắt TV";
                    try {
                        t = describe(Net.getObj(base + "/status.json"));
                    } catch (Exception e) {
                        t = "TV: lỗi - " + Net.explain(e);
                    }
                    try {
                        wlText = WatchLog.format(Net.getObj(Cfg.dbUrl(ParentActivity.this) + "/watchlog.json"));
                    } catch (Exception ignored) { }
                    final String tt = t;
                    final String wl = wlText;
                    runOnUiThread(() -> { statusTv.setText(tt); watchlogTv.setText(wl); });
                }).start();
            } else if (!Cfg.urlOk(ParentActivity.this)) {
                statusTv.setText("TV: " + noBase());
            }
            h.postDelayed(this, 4000);
        }
    };

    @Override protected void onResume() { super.onResume(); alive = true; h.post(poller); }
    @Override protected void onPause() { super.onPause(); alive = false; }
}
