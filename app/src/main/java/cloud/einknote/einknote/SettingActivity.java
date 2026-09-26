package cloud.einknote.einknote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends AppCompatActivity {

    public static final String PREF_NAME = "EinkNotePrefs";
    public static final String KEY_STORAGE_IS_EXTERNAL = "key_storage_external";
    public static final String KEY_FONT_PATH = "key_font_path";
    public static final String KEY_FONT_SIZE = "key_font_size";

    private static final int MIN_FONT_SIZE = 5;
    private static final int MAX_FONT_SIZE = 30;

    private RadioGroup rgStorage;
    private RadioButton rbInternalStorage, rbExternalStorage;
    private TextView tvFontStatus, tvCurrentSize, tvFontPreview;
    private Button btnSelectFont, btnSizeMinus, btnSizePlus, btnBack;
    private TextView tvTerms, tvPrivacy;

    private SharedPreferences prefs;
    private int currentFontSize = 16;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        initViews();
        loadSavedSettings();
        applySavedFontPreview();
        setupListeners();
    }

    private void initViews() {
        rgStorage = findViewById(R.id.rgStorage);
        rbInternalStorage = findViewById(R.id.rbInternalStorage);
        rbExternalStorage = findViewById(R.id.rbExternalStorage);

        btnSelectFont = findViewById(R.id.btnSelectFont);
        tvFontStatus = findViewById(R.id.tvFontStatus);
        tvCurrentSize = findViewById(R.id.tvCurrentSize);
        tvFontPreview = findViewById(R.id.tvFontPreview);

        btnSizeMinus = findViewById(R.id.btnSizeMinus);
        btnSizePlus = findViewById(R.id.btnSizePlus);
        btnBack = findViewById(R.id.btnBack);

        tvTerms = findViewById(R.id.tvTerms);
        tvPrivacy = findViewById(R.id.tvPrivacy);
    }

    private void loadSavedSettings() {
        boolean isExternal = prefs.getBoolean(KEY_STORAGE_IS_EXTERNAL, false);
        if (isExternal) {
            rbExternalStorage.setChecked(true);
        } else {
            rbInternalStorage.setChecked(true);
        }

        currentFontSize = prefs.getInt(KEY_FONT_SIZE, 16);
        updateFontSizeDisplay();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        rgStorage.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isExternal = (checkedId == R.id.rbExternalStorage);
            prefs.edit().putBoolean(KEY_STORAGE_IS_EXTERNAL, isExternal).apply();
        });

        // 폰트 지정 버튼 클릭 시 저장소 권한 확인 후 폴더 탐색
        btnSelectFont.setOnClickListener(v -> checkPermissionAndLoadFont());

        btnSizeMinus.setOnClickListener(v -> {
            if (currentFontSize > MIN_FONT_SIZE) {
                currentFontSize--;
                saveAndApplyFontSize();
            } else {
                Toast.makeText(this, "최소 크기는 " + MIN_FONT_SIZE + "sp 입니다.", Toast.LENGTH_SHORT).show();
            }
        });

        btnSizePlus.setOnClickListener(v -> {
            if (currentFontSize < MAX_FONT_SIZE) {
                currentFontSize++;
                saveAndApplyFontSize();
            } else {
                Toast.makeText(this, "최대 크기는 " + MAX_FONT_SIZE + "sp 입니다.", Toast.LENGTH_SHORT).show();
            }
        });

        tvTerms.setOnClickListener(v -> openUrl("https://soorichu.github.io/einknote/service/"));
        tvPrivacy.setOnClickListener(v -> openUrl("https://soorichu.github.io/privacy/"));
    }

    /**
     * 안드로이드 11 이상 최상위 루트 접근을 위한 모든 파일 관리 권한 체크
     */
    private void checkPermissionAndLoadFont() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("저장소 권한 필요")
                        .setMessage("내부/외장 저장소 최상위 루트의 폴더를 읽기 위해 '모든 파일에 대한 접근' 권한이 필요합니다.")
                        .setPositiveButton("설정으로 이동", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("취소", null)
                        .show();
                return;
            }
        }
        checkAndApplyCustomFont();
    }

    /**
     * [내부저장소 최상위]: /sdcard/einknote/
     * [외장 SD카드 최상위]: /storage/xxxx-xxxx/einknote/
     */
    private void checkAndApplyCustomFont() {
        boolean isExternal = rbExternalStorage.isChecked();
        File rootDir;

        if (isExternal) {
            rootDir = getPhysicalSdCardRootDir();
            if (rootDir == null) {
                new AlertDialog.Builder(this)
                        .setTitle("SD 카드 없음")
                        .setMessage("장착된 외장 SD 카드를 찾을 수 없습니다.\nSD 카드가 삽입되어 있는지 확인해 주세요.")
                        .setPositiveButton("확인", null)
                        .show();
                return;
            }
        } else {
            rootDir = Environment.getExternalStorageDirectory();
        }

        File einkNoteFolder = new File(rootDir, "einknote");

        if (!einkNoteFolder.exists()) {
            einkNoteFolder.mkdirs();
        }

        // 폰트 파일 탐색 (.ttf, .otf, .tts)
        File[] fontFiles = einkNoteFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".tts");
        });

        if (fontFiles == null || fontFiles.length == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("폰트 파일 없음")
                    .setMessage("einknote 폴더에 폰트(.ttf/.otf/.tts) 파일이 없습니다.\n\n확인된 폴더 위치:\n" + einkNoteFolder.getAbsolutePath())
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }

        // 다이얼로그 목록 구성 (첫 번째 항목: 시스템 기본 폰트)
        List<String> fontNames = new ArrayList<>();
        fontNames.add("기본 시스템 폰트 사용");
        for (File f : fontFiles) {
            fontNames.add(f.getName());
        }

        String[] items = fontNames.toArray(new String[0]);

        // 버튼 없이 항목 클릭 즉시 적용 및 닫기
        new AlertDialog.Builder(this)
                .setTitle("사용할 폰트 선택")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        // 1) 기본 폰트 복원
                        prefs.edit().remove(KEY_FONT_PATH).apply();
                        tvFontPreview.setTypeface(Typeface.DEFAULT);
                        tvFontStatus.setText("선택된 폰트: 시스템 기본 폰트");
                        Toast.makeText(this, "기본 폰트로 설정되었습니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        // 2) 선택한 커스텀 폰트 적용
                        File selectedFile = fontFiles[which - 1];
                        try {
                            Typeface typeface = Typeface.createFromFile(selectedFile);
                            tvFontPreview.setTypeface(typeface);
                            tvFontStatus.setText("선택된 폰트: " + selectedFile.getName());

                            prefs.edit().putString(KEY_FONT_PATH, selectedFile.getAbsolutePath()).apply();
                            Toast.makeText(this, selectedFile.getName() + " 폰트가 적용되었습니다.", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "폰트를 적용할 수 없습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    dialog.dismiss();
                })
                .show();
    }
    /**
     * 물리적 외장 SD 카드의 최상위 루트(/storage/XXXX-XXXX) 디렉터리를 추출합니다.
     */
    private File getPhysicalSdCardRootDir() {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(this, null);
        for (File file : externalFilesDirs) {
            if (file != null && Environment.isExternalStorageRemovable(file)) {
                // file: /storage/XXXX-XXXX/Android/data/com.soorinote.einknote/files
                String path = file.getAbsolutePath();
                int androidIndex = path.indexOf("/Android");
                if (androidIndex != -1) {
                    return new File(path.substring(0, androidIndex)); // 최상위 루트 리턴
                }
            }
        }
        return null;
    }

    private void applySavedFontPreview() {
        String savedPath = prefs.getString(KEY_FONT_PATH, null);
        if (savedPath != null) {
            File fontFile = new File(savedPath);
            if (fontFile.exists()) {
                try {
                    Typeface typeface = Typeface.createFromFile(fontFile);
                    tvFontPreview.setTypeface(typeface);
                    tvFontStatus.setText("선택된 폰트: " + fontFile.getName());
                    return;
                } catch (Exception ignored) {}
            }
        }
        tvFontPreview.setTypeface(Typeface.DEFAULT);
        tvFontStatus.setText("선택된 폰트: 시스템 기본 폰트");
    }

    private void saveAndApplyFontSize() {
        prefs.edit().putInt(KEY_FONT_SIZE, currentFontSize).apply();
        updateFontSizeDisplay();
    }

    private void updateFontSizeDisplay() {
        tvCurrentSize.setText(currentFontSize + " sp");
        tvFontPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "브라우저를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}