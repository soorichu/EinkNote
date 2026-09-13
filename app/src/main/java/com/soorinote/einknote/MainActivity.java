package com.soorinote.einknote;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.soorinote.einknote.databinding.ActivityMainBinding;
import com.soorinote.einknote.TxtSave;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "EinkNote";

    private ActivityMainBinding binding;
    private DatabaseHelper dbHelper;
    private long currentNoteId = -1;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private final Scope docsScope = new Scope("https://www.googleapis.com/auth/documents");
    private final Scope driveScope = new Scope("https://www.googleapis.com/auth/drive.file");

    // 타이핑 후 화면을 정리할 지연 시간 (1.5초)
    private static final long TYPING_DELAY_MS = 1500;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable delayedRefreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new DatabaseHelper(this);

        // 1. E-ink 깜빡임 차단 옵션 적용 (제목, 본문 둘 다 적용)
        applyEinkDisplayOptions(binding.etTitle, false);  // 제목은 단일 행
        applyEinkDisplayOptions(binding.etContent, true);  // 본문은 다중 행

        // 2. 타이핑 멈췄을 때만 갱신하는 딜레이 리스너 연결
        setupTypingDebounce(binding.etTitle);
        setupTypingDebounce(binding.etContent);

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
        binding.btnBack.setOnClickListener(v -> {
            saveToLocal();
            Toast.makeText(this, "저장 후 목록으로...", Toast.LENGTH_SHORT).show();
            finish();
        });

        binding.btnSaveLocal.setOnClickListener(v -> {
            saveToLocal();
            Toast.makeText(this, "로컬 DB에 저장되었습니다.", Toast.LENGTH_SHORT).show();
            //    finish();
        });

        binding.btnSaveGoogle.setOnClickListener(v -> handleGoogleSave());
        binding.btnExport.setOnClickListener((v -> exportNote()));
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


    private void exportNote() {
        String content = binding.etContent.getText().toString();
        String title = resolveTitle(content);

        TxtSave.export(this, title, content, new TxtSave.SaveCallback() {
            @Override
            public void onSuccess(String pathOrName) {
                Toast.makeText(MainActivity.this, "저장 완료: " + pathOrName, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == TxtSave.REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportNote(); // 권한 획득 후 즉시 재시도
            } else {
                Toast.makeText(this, "파일 저장을 위해 저장소 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void applyEinkDisplayOptions(EditText editText, boolean isMultiLine) {
        // [핵심 1] 하드웨어 가속 비활성화 (소프트웨어 렌더링)
        editText.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // [수정] 커서는 보이도록 켭니다.
        editText.setCursorVisible(true);

        // [핵심 3] 맞춤법 밑줄 및 키보드 단어 추천 비활성화
        int inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (isMultiLine) {
            inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        editText.setInputType(inputType);

        // [핵심 4] 글자 선택 핸들/팝업으로 인한 리프레시 방지
        editText.setHighlightColor(Color.TRANSPARENT);
        editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
           public boolean onCreateActionMode(ActionMode mode, Menu menu) { return false; }
           @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override
            public void onDestroyActionMode(ActionMode mode) {}
        });
    }

    // 리플렉션을 통해 안드로이드 내부의 커서 깜빡임(Blink) 스레드를 중단시키는 메서드
    private void stopCursorBlinking(EditText editText) {
        try {
            java.lang.reflect.Field editorField = TextView.class.getDeclaredField("mEditor");
            editorField.setAccessible(true);
            Object editor = editorField.get(editText);

            if (editor != null) {
                java.lang.reflect.Method stopBlinkMethod = editor.getClass().getDeclaredMethod("stopBlink");
                stopBlinkMethod.setAccessible(true);
                stopBlinkMethod.invoke(editor);
            }
        } catch (Exception ignored) {
            // 리플렉션 실패 시 기본 동작 유지
        }
    }

    private void setupTypingDebounce(EditText editText) {
        delayedRefreshRunnable = () -> {
            // 타이핑이 완전히 멈춘 후 1회만 화면 갱신
            editText.invalidate();
        };

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 키를 입력하는 동안에는 갱신 카운트다운을 계속 취소
                refreshHandler.removeCallbacks(delayedRefreshRunnable);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 입력이 멈추고 1.5초가 지나면 1회만 갱신
                refreshHandler.postDelayed(delayedRefreshRunnable, TYPING_DELAY_MS);
            }
        });
    }
}