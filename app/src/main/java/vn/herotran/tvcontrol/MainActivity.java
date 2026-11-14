package vn.herotran.tvcontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

// Man hinh chon che do - v1.26: giao dien XML giong app 2 (nen toi, card bo tron).
// Da BO extra guard_settings / allow_min (chan Cai dat TV da bi xoa): Cai dat TV luon mo.
// Con lai: unlock (cuu ho khi TV bi khoa ma mat mang), db_url (doi URL tam thoi qua ADB).
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Cho phep dat URL qua ADB: adb shell am start -n vn.herotran.tvcontrol/.MainActivity --es db_url "https://..."
        String du = getIntent() == null ? null : getIntent().getStringExtra("db_url");
        if (du != null && !du.trim().isEmpty()) Cfg.setDbUrl(this, du);
        // Cuu ho qua ADB khi TV dang khoa / mat mang:
        //   --ez unlock true      mo khoa ngay
        Intent it = getIntent();
        if (it != null) {
            if (it.getBooleanExtra("unlock", false))
                Cfg.sp(this).edit().putBoolean("locked", false).putLong("endAt", 0).apply();
        }
        String r = Cfg.role(this);
        if ("tv".equals(r)) {
            Cfg.hideFromRecents(this, true);
            Intent ti = new Intent(this, TvActivity.class);
            if (it != null && it.getBooleanExtra("minimize", false)) ti.putExtra("minimize", true);
            startActivity(ti);
            finish();
            return;
        }
        if ("parent".equals(r)) { startActivity(new Intent(this, ParentActivity.class)); finish(); return; }

        setContentView(R.layout.activity_main);
        Button tv = findViewById(R.id.btnTv);
        Button p = findViewById(R.id.btnParent);
        tv.setOnClickListener(v -> {
            Cfg.setRole(this, "tv");
            Cfg.hideFromRecents(this, true);
            startActivity(new Intent(this, TvActivity.class));
            finish();
        });
        p.setOnClickListener(v -> {
            Cfg.setRole(this, "parent");
            startActivity(new Intent(this, ParentActivity.class));
            finish();
        });
        tv.requestFocus();
    }
}
