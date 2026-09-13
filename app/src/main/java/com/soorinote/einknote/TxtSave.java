package com.soorinote.einknote;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TxtSave {

    public static final int REQUEST_STORAGE_PERMISSION = 2001;

    public interface SaveCallback {
        void onSuccess(String pathOrName);
        void onError(String message);
    }

    /**
     * 외부 저장소로 텍스트 파일 저장 진입점
     */
    public static void export(Activity activity, String rawTitle, String content, SaveCallback callback) {
        String title = sanitizeTitle(rawTitle);

        // Android 9 (API 28) 이하 기기: 런타임 쓰기 권한 확인
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION
                );
                if (callback != null) {
                    callback.onError("저장소 권한 요청 중...");
                }
                return;
            }
        }

        executeSave(activity, title, content, callback);
    }

    /**
     * 권한 허용 후 실제 파일 쓰기 실행
     */
    public static void executeSave(Activity activity, String title, String content, SaveCallback callback) {
        String fileName = sanitizeTitle(title) + ".txt";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(activity, fileName, content, callback);
        } else {
            saveViaLegacy(activity, fileName, content, callback);
        }
    }

    private static void saveViaMediaStore(Activity activity, String fileName, String content, SaveCallback callback) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/einknote");

        Uri uri = activity.getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);

        if (uri == null) {
            if (callback != null) callback.onError("MediaStore 파일 생성 실패");
            return;
        }

        try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
            if (os != null) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
                if (callback != null) callback.onSuccess("Documents/einknote/" + fileName);
            }
        } catch (IOException e) {
            if (callback != null) callback.onError("파일 쓰기 오류: " + e.getMessage());
        }
    }

    private static void saveViaLegacy(Activity activity, String fileName, String content, SaveCallback callback) {
        File externalStorage = Environment.getExternalStorageDirectory();
        File dir = new File(externalStorage, "einknote");

        if (!dir.exists() && !dir.mkdirs()) {
            if (callback != null) callback.onError("디렉터리 생성 실패 (/einknote)");
            return;
        }

        File targetFile = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            if (callback != null) callback.onSuccess(targetFile.getAbsolutePath());
        } catch (IOException e) {
            if (callback != null) callback.onError("파일 쓰기 오류: " + e.getMessage());
        }
    }

    private static String sanitizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "새_메모";
        }
        return title.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}