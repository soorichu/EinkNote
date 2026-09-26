package cloud.einknote.einknote;

import android.content.Context;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

// [추가된 최신 인증 라이브러리들]
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleDocsUploader {

    public interface UploadCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private static final String FOLDER_NAME = "EinkNote";

    public static void createDoc(Context context, GoogleSignInAccount account, String title, String content, UploadCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 1. 구글 인증 세팅 (Docs + Drive 권한) - 최신 2.x 방식
                // GoogleAuthUtil은 "oauth2:권한1 권한2" 형태의 문자열을 요구합니다.
                String scopes = "oauth2:" + DocsScopes.DOCUMENTS + " " + DriveScopes.DRIVE_FILE;
                Thread.sleep(800); // E-ink 숨고르기 (0.8초)

                // 기기 내장 계정 정보를 바탕으로 토큰 발급 (백그라운드 스레드에서 안전하게 실행됨)
                String token = GoogleAuthUtil.getToken(context, account.getAccount(), scopes);

                // 발급받은 토큰을 최신 GoogleCredentials 객체로 감싸기
                GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(token, null));
                HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);

                NetHttpTransport transport = new NetHttpTransport();
                GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                // Drive API 빌더 설정
                Drive driveService = new Drive.Builder(transport, jsonFactory, credentialsAdapter)
                        .setApplicationName("EinkNote")
                        .build();
                Thread.sleep(800);

                // Docs API 빌더 설정
                Docs docsService = new Docs.Builder(transport, jsonFactory, credentialsAdapter)
                        .setApplicationName("EinkNote")
                        .build();
                Thread.sleep(800);

                // 2. 'EinkNote' 폴더 ID 가져오기 (없으면 생성)
                String folderId = getOrCreateFolder(driveService);

                // 3. 해당 폴더 안에 Google Docs 빈 문서 생성
                File fileMetadata = new File();
                fileMetadata.setName(title);
                fileMetadata.setMimeType("application/vnd.google-apps.document");
                fileMetadata.setParents(Collections.singletonList(folderId));

                File createdFile = driveService.files().create(fileMetadata)
                        .setFields("id")
                        .execute();

                String documentId = createdFile.getId();
                Thread.sleep(800);

                // 4. Docs API로 본문 텍스트 채우기
                if (content != null && !content.isEmpty()) {
                    List<Request> requests = new ArrayList<>();
                    requests.add(new Request().setInsertText(new InsertTextRequest()
                            .setText(content)
                            .setLocation(new Location().setIndex(1))));

                    BatchUpdateDocumentRequest batchUpdate = new BatchUpdateDocumentRequest().setRequests(requests);
                    docsService.documents().batchUpdate(documentId, batchUpdate).execute();
                }

                callback.onSuccess();

            } catch (Exception e) {
                callback.onError(e);
            } finally {
                executor.shutdown(); // 스레드 풀 정상 종료
            }
        });
    }

    // 폴더 검색 및 생성 도우미 메서드
    private static String getOrCreateFolder(Drive driveService) throws Exception {
        // 휴지통에 있지 않고 이름이 EinkNote인 폴더 검색
        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '" + FOLDER_NAME + "' and trashed = false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0).getId(); // 기존 폴더 반환
        }

        // 폴더가 없으면 새로 생성
        File folderMetadata = new File();
        folderMetadata.setName(FOLDER_NAME);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();

        return folder.getId();
    }
}