package cloud.einknote.note;

import android.content.Context;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
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
import java.util.concurrent.Executors;

public class GoogleDocsUploader {

    public interface UploadCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private static final String FOLDER_NAME = "EinkNote";

    public static void createDoc(Context context, GoogleSignInAccount account, String title, String content, UploadCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 1. 구글 인증 세팅 (Docs + Drive 권한)
                List<String> scopes = new ArrayList<>();
                scopes.add(DocsScopes.DOCUMENTS);
                scopes.add(DriveScopes.DRIVE_FILE);

                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, scopes);
                credential.setSelectedAccount(account.getAccount());

                NetHttpTransport transport = new NetHttpTransport();
                GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                Drive driveService = new Drive.Builder(transport, jsonFactory, credential)
                        .setApplicationName("EinkNote")
                        .build();

                Docs docsService = new Docs.Builder(transport, jsonFactory, credential)
                        .setApplicationName("EinkNote")
                        .build();

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