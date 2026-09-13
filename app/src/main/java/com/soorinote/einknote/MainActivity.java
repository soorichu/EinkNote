package com.soorinote.einknote;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.soorinote.einknote.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "EinkNote";

    private ActivityMainBinding binding;
    private DatabaseHelper dbHelper;
    private long currentNoteId = -1;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private final Scope docsScope = new Scope("https://www.googleapis.com/auth/documents");
    private final Scope driveScope = new Scope("https://www.googleapis.com/auth/drive.file");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new DatabaseHelper(this);

        // 수정 모드로 진입 시 데이터 바인딩
        currentNoteId = getIntent().getLongExtra("note_id", -1);
        if (currentNoteId != -1) {
            binding.etTitle.setText(getIntent().getStringExtra("note_title"));
            binding.etContent.setText(getIntent().getStringExtra("note_content"));
        }

        initGoogleClient();
        initListeners();
    }

    private void initListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnSaveLocal.setOnClickListener(v -> {
            saveToLocal();
            Toast.makeText(this, "로컬에 저장되었습니다.", Toast.LENGTH_SHORT).show();
            finish();
        });

        binding.btnSaveGoogle.setOnClickListener(v -> handleGoogleSave());
    }

    private String resolveTitle(String content) {
        String title = binding.etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            String trimmedContent = content.trim();
            if (trimmedContent.length() <= 5) {
                title = trimmedContent.isEmpty() ? "새 메모" : trimmedContent;
            } else {
                title = trimmedContent.substring(0, 5);
            }
        }
        return title;
    }

    private void saveToLocal() {
        String content = binding.etContent.getText().toString();
        String title = resolveTitle(content);
        currentNoteId = dbHelper.insertOrUpdate(currentNoteId, title, content);
    }

    private void initGoogleClient() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(docsScope, driveScope)
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // 구글 로그인 결과 처리 콜백 (상세 실패 코드 토스트 반영)
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            Toast.makeText(this, "로그인 성공: " + account.getEmail(), Toast.LENGTH_SHORT).show();
                            exportToGoogleDocs(account);
                        }
                    } catch (ApiException e) {
                        int statusCode = e.getStatusCode();
                        String reason;

                        switch (statusCode) {
                            case 10: // CommonStatusCodes.DEVELOPER_ERROR
                                reason = "지문(SHA-1) 또는 패키지명 불일치 (코드: 10)";
                                break;
                            case 12500: // GoogleSignInStatusCodes.SIGN_IN_FAILED
                                reason = "OAuth 동의화면 설정 미비 또는 GMS 미지원 (코드: 12500)";
                                break;
                            case 7: // CommonStatusCodes.NETWORK_ERROR
                                reason = "네트워크 연결 오류 (코드: 7)";
                                break;
                            case 12501: // GoogleSignInStatusCodes.SIGN_IN_CANCELLED
                                reason = "사용자가 로그인을 취소함 (코드: 12501)";
                                break;
                            case 12502: // GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS
                                reason = "로그인이 이미 진행 중입니다 (코드: 12502)";
                                break;
                            default:
                                reason = "오류 발생: " + e.getLocalizedMessage() + " (코드: " + statusCode + ")";
                                break;
                        }

                        Log.e(TAG, "구글 로그인 실패: " + reason);
                        Toast.makeText(this, "구글 로그인 실패: " + reason, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void handleGoogleSave() {
        saveToLocal(); // 동기화 전 로컬 우선 저장

        // 이미 로그인된 계정 및 권한 보유 여부 확인
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && GoogleSignIn.hasPermissions(account, docsScope, driveScope)) {
            exportToGoogleDocs(account);
        } else {
            // 미로그인 또는 권한 부족 시 로그인 화면 호출
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        }
    }

    private void exportToGoogleDocs(GoogleSignInAccount account) {
        String content = binding.etContent.getText().toString();
        String title = resolveTitle(content);

        Toast.makeText(this, "구글 문서 생성 중...", Toast.LENGTH_SHORT).show();

        GoogleDocsUploader.createDoc(this, account, title, content, new GoogleDocsUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "구글 워드(Docs) 저장 완료", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "전송 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
}