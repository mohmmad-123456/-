package com.consentcamera.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;

/**
 * Foreground-only, user-operated camera handoff.
 * There is no remote command receiver and no automatic upload.
 */
public class MainActivity extends Activity {
    private static final int CAPTURE = 100;
    private File photo;
    private boolean approvedCapture;
    private ImageView preview;
    private Button share;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            String name = state.getString("photo");
            if (name != null && name.matches("photo_[A-Za-z0-9._-]+\\.jpg"))
                photo = new File(new File(getCacheDir(), "photos"), name);
            approvedCapture = state.getBoolean("approved", false);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(24, 36, 24, 24);
        root.setBackgroundColor(0xfff4f0f3);
        TextView title = new TextView(this);
        title.setText("دوربین، با اجازهٔ شما");
        title.setTextSize(26);
        title.setTextColor(0xff482a3e);
        root.addView(title);
        TextView info = new TextView(this);
        info.setText("درخواست تلگرام فقط یک پیام است. این برنامه هیچ عکسی خودکار نمی‌گیرد یا ارسال نمی‌کند. بعد از گرفتن عکس، پیش‌نمایش را بررسی کنید و اگر خواستید از طریق تلگرام به اشتراک بگذارید.");
        info.setTextSize(17);
        info.setPadding(0, 20, 0, 20);
        root.addView(info);
        Button capture = new Button(this);
        capture.setText("باز کردن دوربین با رضایت من");
        capture.setOnClickListener(v -> capture());
        root.addView(capture);
        preview = new ImageView(this);
        preview.setContentDescription("پیش‌نمایش عکس انتخاب‌شده");
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status);
        share = new Button(this);
        share.setText("تأیید عکس و انتخاب مقصد ارسال");
        share.setOnClickListener(v -> share());
        root.addView(share);
        Button remove = new Button(this);
        remove.setText("حذف نسخهٔ محلی عکس");
        remove.setOnClickListener(v -> { discard(); refresh(); });
        root.addView(remove);
        setContentView(root);
        refresh();
    }

    private Uri uri() {
        return FileProvider.getUriForFile(this, getPackageName() + ".files", photo);
    }

    private void capture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            toast("برنامهٔ دوربین در این گوشی پیدا نشد."); return;
        }
        discard();
        try {
            File dir = new File(getCacheDir(), "photos");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException();
            photo = File.createTempFile("photo_", ".jpg", dir);
            Uri output = uri();
            intent.putExtra(MediaStore.EXTRA_OUTPUT, output);
            intent.setClipData(ClipData.newRawUri("Consent photo", output));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAPTURE);
        } catch (Exception e) {
            discard(); toast("دوربین باز نشد. دوباره امتحان کنید.");
        }
        refresh();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAPTURE) return;
        if (photo != null) {
            revokeUriPermission(uri(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                                       Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        approvedCapture = result == RESULT_OK && photo != null && photo.length() > 0;
        if (!approvedCapture) discard();
        refresh();
    }

    private void refresh() {
        boolean ready = approvedCapture && photo != null && photo.exists() && photo.length() > 0;
        share.setEnabled(ready);
        preview.setImageDrawable(null);
        if (ready) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photo.getPath(), opts);
            opts.inSampleSize = 1;
            while (Math.max(opts.outWidth, opts.outHeight) / opts.inSampleSize > 1200)
                opts.inSampleSize *= 2;
            opts.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(photo.getPath(), opts);
            preview.setImageBitmap(bitmap);
        }
        status.setText(ready
            ? "عکس هنوز ارسال نشده. در صفحهٔ اشتراک‌گذاری، تلگرام و گیرنده را خودتان انتخاب کنید."
            : "هیچ عکس آمادهٔ ارسال نیست.");
    }

    private void share() {
        if (!approvedCapture || photo == null || !photo.exists()) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/jpeg");
        Uri content = uri();
        send.putExtra(Intent.EXTRA_STREAM, content);
        send.setClipData(ClipData.newRawUri("Consent photo", content));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "ارسال با تأیید شما"));
    }

    private void discard() {
        if (photo != null) {
            revokeUriPermission(uri(), Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                       Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            photo.delete();
        }
        photo = null;
        approvedCapture = false;
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (photo != null) state.putString("photo", photo.getName());
        state.putBoolean("approved", approvedCapture);
    }
}
