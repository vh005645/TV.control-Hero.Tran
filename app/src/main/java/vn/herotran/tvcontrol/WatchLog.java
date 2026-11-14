package vn.herotran.tvcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Nhat ky xem TV trong NGAY HOM NAY: TV duoc MO / TAT luc nao.
// TV day su kien len Firebase node /watchlog.json (cung cap voi /tv/<ma>/...), dien thoai doc node do de hien.
// Dinh dang tren Firebase: {"date":"2026-11-14","events":[{"t":"08:12","kind":"open"},{"t":"12:30","kind":"close"}]}
// Qua ngay moi -> tu xoa danh sach cu. Chong ghi trung: cung loai su kien trong 60 giay thi bo qua.
public class WatchLog {
    private static final String SP = "watchlog_local";
    private static final int MAX_EVENTS = 60;
    private static final long DEDUP_MS = 60 * 1000L;

    static String todayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    static String nowTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private static String base(Context ctx) {
        if (!Cfg.urlOk(ctx)) return null;
        return Cfg.dbUrl(ctx) + "/watchlog";
    }

    // TV goi khi: dich vu Tro nang ket noi / man hinh sang (open), man hinh tat / sap tat nguon (close).
    // Chay nen, khong lam dung vong lap Firebase chinh.
    static void pushEvent(final Context ctx, final String kind) {
        if (ctx == null) return;
        if (!"tv".equals(Cfg.role(ctx))) return;
        if (!"open".equals(kind) && !"close".equals(kind)) return;
        final SharedPreferences sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String lastKind = sp.getString("last_kind", "");
        long lastTs = sp.getLong("last_ts", 0);
        if (kind.equals(lastKind) && now - lastTs < DEDUP_MS) return;
        sp.edit().putString("last_kind", kind).putLong("last_ts", now).apply();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String b = base(ctx);
                    if (b == null) return;
                    JSONObject cur = null;
                    try { cur = Net.getObj(b + ".json"); } catch (Exception ignored) { }
                    String today = todayDate();
                    JSONArray events;
                    if (cur != null && today.equals(cur.optString("date", ""))) {
                        events = cur.optJSONArray("events");
                        if (events == null) events = new JSONArray();
                    } else {
                        events = new JSONArray();
                    }
                    JSONObject e = new JSONObject();
                    e.put("t", nowTime());
                    e.put("kind", kind);
                    events.put(e);
                    while (events.length() > MAX_EVENTS) events.remove(0);
                    JSONObject out = new JSONObject();
                    out.put("date", today);
                    out.put("events", events);
                    Net.put(b + ".json", out.toString());
                } catch (Exception ignored) { }
            }
        }).start();
    }

    // Dien thoai goi de hien thi: tra ve chuoi nhieu dong "HH:mm - Mo TV" / "HH:mm - Tat TV".
    static String format(JSONObject obj) {
        if (obj == null) return "Hôm nay chưa có lịch sử Mở / Tắt TV";
        if (!todayDate().equals(obj.optString("date", ""))) return "Hôm nay chưa có lịch sử Mở / Tắt TV";
        JSONArray events = obj.optJSONArray("events");
        if (events == null || events.length() == 0) return "Hôm nay chưa có lịch sử Mở / Tắt TV";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            if (sb.length() > 0) sb.append("
");
            sb.append(e.optString("t", "--:--"));
            sb.append(" - ");
            sb.append("open".equals(e.optString("kind", "")) ? "Mở TV" : "Tắt TV");
        }
        return sb.length() == 0 ? "Hôm nay chưa có lịch sử Mở / Tắt TV" : sb.toString();
    }
}
