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
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.drive.DriveScopes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private EditText etTitle;
    private EditText etContent;
    private Button btnSave;
    private Button btnBack;
    private Button btnExport;
    private Button btnSaveGoogle;

    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> signInLauncher;

    private DatabaseHelper dbHelper;
    private long currentNoteId = -1; // -1이면 신규 작성, 목록에서 클릭 시 전달받은 id

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        initViews();
        loadNoteDataFromIntent();
        setupListeners();
        googleSignInOuptions();

        btnSaveGoogle.setOnClickListener(v -> checkAuthAndUpload());
    }

    private void googleSignInOuptions() {
        Scope docsScope = new Scope(DocsScopes.DOCUMENTS);
        Scope driveScope = new Scope(DriveScopes.DRIVE_FILE);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(docsScope, driveScope)
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            // E-ink 단말기 부하 방지를 위해 1.5초 대기 후 업로드 시작
                            Toast.makeText(this, "로그인 완료! 잠시 후 동기화를 시작합니다...", Toast.LENGTH_SHORT).show();
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                uploadNote(account);
                            }, 1500);
                        }
                    } catch (ApiException e) {
                        Toast.makeText(this, "로그인 실패: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void checkAuthAndUpload() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        Scope docsScope = new Scope(DocsScopes.DOCUMENTS);
        Scope driveScope = new Scope(DriveScopes.DRIVE_FILE);

        if (account != null && GoogleSignIn.hasPermissions(account, docsScope, driveScope)) {
            uploadNote(account);
        } else {
            signInLauncher.launch(mGoogleSignInClient.getSignInIntent());
        }
    }

    private void uploadNote(GoogleSignInAccount account) {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString();

        if (title.isEmpty()) {
            title = "새 메모";
        }

        btnSaveGoogle.setEnabled(false);
        Toast.makeText(this, "Google Docs에 저장 중...", Toast.LENGTH_SHORT).show();

        GoogleDocsUploader.createDoc(this, account, title, content, new GoogleDocsUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSaveGoogle.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Google Docs에 성공적으로 저장되었습니다.", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    btnSaveGoogle.setEnabled(true);

                    // 1. 에러 상세 내용을 문자열로 변환
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String exceptionAsString = sw.toString();

                    // 2. 팝업창(Dialog)으로 에러 전체 내용 띄우기 (또는 텍스트 뷰에 출력)
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("에러 상세 로그")
                            .setMessage(exceptionAsString)
                            .setPositiveButton("확인", null)
                            .show();
                });
            }
        });
    }

    private void initViews() {
        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
        btnExport = findViewById(R.id.btnExport);
        btnSaveGoogle = findViewById(R.id.btnSaveGoogle);
    }

    /**
     * 목록(RecyclerView)에서 진입 시 기존 메모 데이터 바인딩
     */
    private void loadNoteDataFromIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("note_id")) {
            currentNoteId = intent.getLongExtra("note_id", -1);
            String title = intent.getStringExtra("note_title");
            String content = intent.getStringExtra("note_content");

            if (title != null) etTitle.setText(title);
            if (content != null) etContent.setText(content);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 설정 화면에서 변경한 폰트 및 크기 실시간 반영
        applyCustomFontAndSize();
    }

    private void setupListeners() {
        // [저장] 버튼: 로컬 SQLite DB에 저장
        btnSave.setOnClickListener(v -> {
            saveNoteToLocalDb();
            Toast.makeText(this, "로컬 DB에 저장되었습니다.", Toast.LENGTH_SHORT).show();
        });

        // [뒤로] 버튼: 로컬 DB에 자동 저장 후 화면 닫기
        btnBack.setOnClickListener(v -> {
            saveNoteToLocalDb();
            finish();
        });

        // [내보내기] 버튼: 설정된 저장소(내부/외부 SD카드)의 einknote/ 폴더로 내보내기
        btnExport.setOnClickListener(v -> checkPermissionAndExport());
    }

    /**
     * 기존 DatabaseHelper.insertOrUpdate() 호출
     */
    private void saveNoteToLocalDb() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString();

        // 제목과 본문이 둘 다 비어있는 경우 저장 건너뜀
        if (title.isEmpty() && content.isEmpty()) {
            return;
        }

        if (title.isEmpty()) {
            title = "제목 없음";
        }

        // DB에 insert 또는 update 수행 후 갱신된 id 보관
        currentNoteId = dbHelper.insertOrUpdate(currentNoteId, title, content);
    }

    /**
     * 안드로이드 11 이상 최상위 루트 접근 권한 체크 후 내보내기 수행
     */
    private void checkPermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("저장소 권한 필요")
                        .setMessage("메모를 최상위 루트의 einknote 폴더에 저장하기 위해 '모든 파일 관리' 권한이 필요합니다.")
                        .setPositiveButton("설정 이동", (dialog, which) -> {
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
        exportMemoToFile();
    }

    /**
     * SettingActivity에서 선택한 저장소 위치에 맞춰 [저장소]/einknote/ 폴더로 텍스트 파일 저장
     */
    private void exportMemoToFile() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString();

        if (title.isEmpty()) {
            title = "메모";
        }

        // 1. SharedPreferences에서 저장소 설정값 확인
        SharedPreferences prefs = getSharedPreferences(SettingActivity.PREF_NAME, Context.MODE_PRIVATE);
        boolean isExternal = prefs.getBoolean(SettingActivity.KEY_STORAGE_IS_EXTERNAL, false);

        File rootDir;
        if (isExternal) {
            // 외장 마이크로 SD 카드 최상위 루트 (/storage/xxxx-xxxx)
            rootDir = getPhysicalSdCardRootDir();
            if (rootDir == null) {
                Toast.makeText(this, "장착된 외장 SD 카드를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // 기기 내부 기본 공유 저장소 최상위 루트 (/storage/emulated/0)
            rootDir = Environment.getExternalStorageDirectory();
        }

        // 2. [루트]/einknote/ 폴더 확보
        File exportFolder = new File(rootDir, "einknote");
        if (!exportFolder.exists()) {
            boolean created = exportFolder.mkdirs();
            if (!created && !exportFolder.exists()) {
                Toast.makeText(this, "폴더 생성 실패: " + exportFolder.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 파일명 특수문자 치환 후 파일 생성
        String safeFileName = title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        File targetFile = new File(exportFolder, safeFileName);

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

            writer.write(content);
            writer.flush();

            String locationLabel = isExternal ? "외부 SD카드" : "내부 저장소";
            Toast.makeText(this, "[" + locationLabel + "] 저장 완료:\n" + targetFile.getName(), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "내보내기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 탈착식 외장 SD 카드의 최상위 루트 디렉터리(/storage/XXXX-XXXX) 추출
     */
    private File getPhysicalSdCardRootDir() {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(this, null);
        for (File file : externalFilesDirs) {
            if (file != null && Environment.isExternalStorageRemovable(file)) {
                String path = file.getAbsolutePath();
                int androidIndex = path.indexOf("/Android");
                if (androidIndex != -1) {
                    return new File(path.substring(0, androidIndex));
                }
            }
        }
        return null;
    }

    /**
     * SharedPreferences에 저장된 사용자 폰트 및 폰트 크기 적용
     */
    private void applyCustomFontAndSize() {
        SharedPreferences prefs = getSharedPreferences(SettingActivity.PREF_NAME, Context.MODE_PRIVATE);

        // 글자 크기
        int fontSize = prefs.getInt(SettingActivity.KEY_FONT_SIZE, 16);
        etContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        etTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(fontSize + 2, 32));

        // 사용자 폰트
        String fontPath = prefs.getString(SettingActivity.KEY_FONT_PATH, null);
        if (fontPath != null) {
            File fontFile = new File(fontPath);
            if (fontFile.exists()) {
                try {
                    Typeface customTypeface = Typeface.createFromFile(fontFile);
                    etTitle.setTypeface(customTypeface);
                    etContent.setTypeface(customTypeface);
                    return;
                } catch (Exception ignored) {}
            }
        }
        // 기본 시스템 폰트
        etTitle.setTypeface(Typeface.DEFAULT);
        etContent.setTypeface(Typeface.DEFAULT);
    }

    @Override
    public void onBackPressed() {
        // 기기 뒤로가기 버튼 클릭 시에도 자동 저장 후 종료
        saveNoteToLocalDb();
        super.onBackPressed();
    }
}