package com.soorinote.einknote;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.soorinote.einknote.databinding.ActivityNoteListBinding;

public class NoteListActivity extends AppCompatActivity {
    private ActivityNoteListBinding binding;
    private DatabaseHelper dbHelper;
    private NoteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNoteListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new DatabaseHelper(this);
        setupRecyclerView();

        binding.btnNew.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });


        binding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotes();
    }

    private void refreshNotes() {
        adapter.updateData(dbHelper.getAllNotes());
    }

    private void setupRecyclerView() {
        binding.rvNotes.setLayoutManager(new LinearLayoutManager(this));
        // E-ink 화면 잔상 유발 방지를 위해 애니메이터 비활성화
        binding.rvNotes.setItemAnimator(null);

        adapter = new NoteAdapter(dbHelper.getAllNotes(), new NoteAdapter.OnNoteListener() {
            @Override
            public void onEdit(Note note) {
                Intent intent = new Intent(NoteListActivity.this, MainActivity.class);
                intent.putExtra("note_id", note.id);
                intent.putExtra("note_title", note.title);
                intent.putExtra("note_content", note.content);
                startActivity(intent);
            }

            @Override
            public void onDelete(Note note) {
                dbHelper.deleteNote(note.id);
                refreshNotes();
                Toast.makeText(NoteListActivity.this, "삭제 완료", Toast.LENGTH_SHORT).show();
            }

       //     @Override
            public void onGoogleSave(Note note) {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(NoteListActivity.this);
                if (account == null) {
                    Toast.makeText(NoteListActivity.this, "수정 화면으로 들어가 먼저 구글 로그인을 진행해주세요.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(NoteListActivity.this, "구글 저장 시작...", Toast.LENGTH_SHORT).show();
                GoogleDocsUploader.createDoc(NoteListActivity.this, account, note.title, note.content, new GoogleDocsUploader.UploadCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> Toast.makeText(NoteListActivity.this, "[" + note.title + "] 구글 문서 동기화 완료", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> Toast.makeText(NoteListActivity.this, "업로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });
        binding.rvNotes.setAdapter(adapter);
    }
}