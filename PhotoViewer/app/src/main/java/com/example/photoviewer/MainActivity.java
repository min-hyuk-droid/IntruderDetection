package com.example.photoviewer;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ImageAdapter.OnPostClickListener {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    // 연결·응답 대기 시간 (강의자료는 3초. 클라우드 HTTPS 첫 연결이 3초를 넘는 경우가 있어 늘림)
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    ImageView imgView;
    TextView textView;
    String site_url = "http://10.0.2.2:8000";
    JSONObject post_json;
    String imageUrl = null;
    Bitmap bmImg = null;
    CloadImage taskDownload;
    PutPost taskUpload;

    // 서버 주소/토큰은 git 에 올리지 않는 secrets.properties → BuildConfig 로 주입
    String token = BuildConfig.LOCAL_TOKEN;

    // [편의기능] 서버 선택: 0 = 로컬(에뮬레이터 → PC), 1 = PythonAnywhere 클라우드
    private static final String[] SERVER_NAMES = {"로컬", "클라우드"};
    private static final String[] SERVER_URLS = {BuildConfig.LOCAL_URL, BuildConfig.REMOTE_URL};
    private static final String[] SERVER_TOKENS = {BuildConfig.LOCAL_TOKEN, BuildConfig.REMOTE_TOKEN};
    private static final String PREF_SERVER = "server";
    private SharedPreferences prefs;
    private SwipeRefreshLayout swipeRefresh;
    private EditText editSearch;
    // [편의기능] 실시간 검색: 입력을 멈추고 잠시(SEARCH_DELAY_MS) 뒤 자동 검색 (글자마다 요청하지 않도록)
    private static final long SEARCH_DELAY_MS = 400;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = () -> onClickSearch(null);
    private boolean clearingSearch = false;

    // 마지막으로 동기화한 목록 ([편의기능] 상세보기/삭제에서 사용)
    private List<Bitmap> imageList = new ArrayList<>();
    private List<Post> postList = new ArrayList<>();

    // Upload 화면에서 선택한 이미지
    private Uri selectedImageUri;
    private ImageView uploadPreview;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImage;
    // [편의기능] 카메라로 찍어서 바로 게시
    private ActivityResultLauncher<Uri> takePhoto;
    private Uri cameraPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //imgView = (ImageView) findViewById(R.id.imgView);
        textView = (TextView) findViewById(R.id.textView);
        editSearch = findViewById(R.id.editSearch);

        // 이미지 선택 (Android Photo Picker, 저장소 권한 없이 사용 가능)
        pickImage = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                if (uploadPreview != null) {
                    uploadPreview.setImageURI(uri);
                }
            }
        });

        // [편의기능] 카메라 촬영: 카메라 앱이 cameraPhotoUri 파일에 사진을 저장하면 그 사진을 게시 이미지로 사용
        takePhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
            if (Boolean.TRUE.equals(saved) && cameraPhotoUri != null) {
                selectedImageUri = cameraPhotoUri;
                if (uploadPreview != null) {
                    uploadPreview.setImageURI(null);
                    uploadPreview.setImageURI(cameraPhotoUri);
                }
            }
        });

        setupServerSpinner();

        // [편의기능] 당겨서 새로고침: 지금 보고 있는 목록(검색어가 있으면 검색 결과)을 다시 받아옴
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(getColor(R.color.brand_yellow));
        swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.surface));
        swipeRefresh.setOnRefreshListener(() -> onClickSearch(null));

        // [편의기능] 앱을 켜면 자동 동기화 (화면을 돌려 화면이 다시 만들어져도 목록을 다시 받아옴)
        onClickDownload(null);

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchHandler.removeCallbacks(searchRunnable);
                if (!clearingSearch) {
                    searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                }
            }
        });

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                onClickSearch(v);
                return true;
            }
            return false;
        });
    }

    private void setupServerSpinner() {
        prefs = getSharedPreferences("photoviewer", MODE_PRIVATE);
        Spinner spinner = findViewById(R.id.spinnerServer);
        ArrayAdapter<String> serverAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SERVER_NAMES);
        serverAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(serverAdapter);
        // 마지막으로 고른 서버로 시작 (자동 동기화가 이 서버로 요청하도록 먼저 지정)
        int saved = prefs.getInt(PREF_SERVER, 0);
        if (saved < SERVER_URLS.length && !TextUtils.isEmpty(SERVER_URLS[saved])) {
            site_url = SERVER_URLS[saved];
            token = SERVER_TOKENS[saved];
        } else {
            saved = 0;
        }
        spinner.setSelection(saved);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (TextUtils.isEmpty(SERVER_URLS[position])) {
                    Toast.makeText(MainActivity.this, "secrets.properties 에 서버 주소가 없습니다.",
                            Toast.LENGTH_SHORT).show();
                    spinner.setSelection(0);
                    return;
                }
                boolean changed = !SERVER_URLS[position].equals(site_url);
                site_url = SERVER_URLS[position];
                token = SERVER_TOKENS[position];
                prefs.edit().putInt(PREF_SERVER, position).apply();
                if (changed) {
                    // 이전 서버의 목록은 지우고(실패해도 다른 서버 글이 남아 보이지 않게) 바로 새 서버로 동기화
                    showImages(new ArrayList<>(), new ArrayList<>());
                    onClickDownload(null);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ------------------------------------------------------------------ 동기화 (강의자료 20~23p)

    public void onClickDownload(View v) {
        // 검색어를 지울 때는 실시간 검색이 다시 돌지 않게 함 (아래에서 전체 목록을 바로 받아옴)
        clearingSearch = true;
        editSearch.setText("");
        clearingSearch = false;
        searchHandler.removeCallbacks(searchRunnable);
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    // [편의기능] 검색: GET /api_root/Post/?search=키워드
    public void onClickSearch(View v) {
        String keyword = editSearch.getText().toString().trim();
        searchHandler.removeCallbacks(searchRunnable);
        if (v != null) {
            hideKeyboard();  // 검색 버튼·엔터일 때만 키보드를 내림 (입력 중 자동 검색은 계속 타이핑 가능)
        }
        String url = site_url + "/api_root/Post/";
        if (!keyword.isEmpty()) {
            url += "?search=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        }
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(url);
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        // [편의기능] 이미지와 같은 순서로 게시물 정보(제목·작성자·게시일·id)도 보관
        private final List<Post> loadedPosts = new ArrayList<>();
        // 실패 원인 (null 이면 정상). 연결 실패 / 서버 오류 / 응답 형식 오류를 구분해서 보여줌
        private String errorMessage;

        @Override
        protected void onPreExecute() {
            swipeRefresh.setRefreshing(true);  // 동기화 중 표시 (버튼·자동·당겨서 새로고침 모두)
        }

        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();
            try {
                String apiUrl = urls[0];
                URL urlAPI = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                if (!TextUtils.isEmpty(token)) {  // 토큰이 없으면(조회만) 헤더를 보내지 않음
                    conn.setRequestProperty("Authorization", "Token " + token);
                }
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    is.close();

                    String strJson = result.toString();
                    JSONArray aryJson = new JSONArray(strJson);
                    // 배열 내 모든 이미지 다운로드
                    // (강의자료는 post_json·imageUrl 필드를 쓰지만, 검색으로 동기화가 겹치면 다른 작업이 값을
                    //  덮어써 제목과 사진이 섞일 수 있어 이 작업 안의 지역 변수로 처리)
                    for (int i = 0; i < aryJson.length() && !isCancelled(); i++) {
                        JSONObject postJson = (JSONObject) aryJson.get(i);
                        String postImageUrl = postJson.getString("image");
                        if (!postImageUrl.equals("")) {
                            bitmapList.add(downloadImage(postImageUrl)); // 이미지 리스트에 추가
                            loadedPosts.add(new Post(postJson));
                        }
                    }
                } else {
                    errorMessage = "서버 응답 오류 (HTTP " + responseCode + ")";
                }
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                errorMessage = "서버 응답이 늦습니다 (" + site_url + ").\n잠시 후 다시 시도하거나 아래로 당겨 새로고침하세요.";
            } catch (ConnectException | UnknownHostException e) {
                e.printStackTrace();
                errorMessage = "서버에 연결할 수 없습니다 (" + site_url + ").\n서버가 켜져 있는지 확인하세요.";
            } catch (JSONException e) {
                e.printStackTrace();
                errorMessage = "서버 응답 형식 오류 (게시물 JSON Array 가 아님)";
            } catch (IOException e) {
                e.printStackTrace();
                errorMessage = "네트워크 오류: " + e.getMessage();
            }
            return bitmapList;
        }

        /** 사진 1장 다운로드: 긴 변 800px 근처로 줄여서 읽고(카드·상세보기에 충분), 실패하면 null (나머지 목록은 계속) */
        private Bitmap downloadImage(String url) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            try (InputStream imgStream = conn.getInputStream()) {
                return decodeScaled(readAll(imgStream), 800);
            } catch (java.io.FileNotFoundException e) {
                e.printStackTrace();  // 서버에 사진 파일이 없음(404) → 빈 칸으로 표시
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            swipeRefresh.setRefreshing(false);
            if (errorMessage != null) {
                // 실패하면 이전에 받아 둔 목록은 그대로 두고 원인만 표시
                setStatus(errorMessage);
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                return;
            }
            if (images.isEmpty()) {
                setStatus("불러올 이미지가 없습니다.");
            } else {
                setStatus(null);  // 성공하면 안내 문구 없이 목록만 보여줌
            }
            showImages(images, loadedPosts);
        }
    }

    /** 상태 안내 줄: 오류·빈 목록·업로드 중일 때만 보이고, 정상일 때(null)는 숨김 */
    private void setStatus(String message) {
        textView.setText(message == null ? "" : message);
        textView.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private void showImages(List<Bitmap> images, List<Post> posts) {
        imageList = images;
        postList = posts;
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        ImageAdapter adapter = new ImageAdapter(images, posts, MainActivity.this);
        // [편의기능] 한 줄에 2장 (가로 화면처럼 넓으면 4장)
        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 4 : 2;
        recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, columns));
        recyclerView.setAdapter(adapter);
    }

    // ------------------------------------------------------------------ 새로운 이미지 게시 (강의자료 19p)

    /** Upload (Post 에 따른 UI 제공): Post 의 title / text / image 를 입력받아 PutPost 로 게시 */
    public void onClickUpload(View v) {
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, "업로드에는 토큰이 필요합니다 (secrets.properties)", Toast.LENGTH_LONG).show();
            return;
        }
        View form = getLayoutInflater().inflate(R.layout.dialog_upload, null);
        EditText editTitle = form.findViewById(R.id.editTitle);
        EditText editText = form.findViewById(R.id.editText);
        Button btnPick = form.findViewById(R.id.btnPickImage);
        uploadPreview = form.findViewById(R.id.imagePreview);
        selectedImageUri = null;
        btnPick.setOnClickListener(b -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        Button btnTakePhoto = form.findViewById(R.id.btnTakePhoto);
        btnTakePhoto.setOnClickListener(b -> startCamera());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("새로운 이미지 게시")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("게시", null)
                .create();
        // 바깥을 잘못 눌러도 입력한 제목·내용·사진이 사라지지 않게 함 (취소 버튼·뒤로가기로만 닫힘)
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String title = editTitle.getText().toString().trim();
            String text = editText.getText().toString().trim();
            // Post 모델 규칙에 맞춘 입력 검사: title(필수, 200자), text(필수), image
            if (title.isEmpty()) {
                editTitle.setError("제목을 입력하세요");
                editTitle.requestFocus();  // 빠진 칸으로 커서 이동 + 오류 메시지 표시
                return;
            }
            if (text.isEmpty()) {
                editText.setError("내용을 입력하세요");
                editText.requestFocus();  // 빠진 칸으로 커서 이동 + 오류 메시지 표시
                return;
            }
            if (selectedImageUri == null) {
                Toast.makeText(this, "이미지를 선택하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (taskUpload != null && taskUpload.getStatus() == AsyncTask.Status.RUNNING) {
                taskUpload.cancel(true);
            }
            taskUpload = new PutPost(selectedImageUri, dialog, editTitle, editText);
            taskUpload.execute(site_url + "/api_root/Post/", title, text);
        }));
        dialog.show();

        Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
    }

    /** POST /api_root/Post/ — Multipart : Text + Image (강의자료 7p). params = {url, title, text} */
    private class PutPost extends AsyncTask<String, Void, Void> {
        private static final String BOUNDARY = "----PhotoViewerBoundary7MA4YWxk";
        private final Uri imageUri;
        private final AlertDialog dialog;
        private final EditText titleInput;
        private final EditText textInput;
        private int responseCode;
        private String responseBody = "";

        PutPost(Uri imageUri, AlertDialog dialog, EditText titleInput, EditText textInput) {
            this.imageUri = imageUri;
            this.dialog = dialog;
            this.titleInput = titleInput;
            this.textInput = textInput;
        }

        @Override
        protected void onPreExecute() {
            // 업로드가 끝날 때까지 게시 화면을 유지하고 중복 게시를 막음 (실패하면 입력 내용 그대로 재시도 가능)
            setUploading(true);
            setStatus("업로드 중...");
        }

        /** 서버 검증 오류(400: {"필드": ["메시지"]})를 해당 입력칸에 표시 */
        private void showFieldErrors() {
            if (responseCode != HttpURLConnection.HTTP_BAD_REQUEST) {
                return;
            }
            try {
                JSONObject errors = new JSONObject(responseBody);
                JSONArray title = errors.optJSONArray("title");
                JSONArray text = errors.optJSONArray("text");
                if (title != null) {
                    titleInput.setError(title.optString(0));
                }
                if (text != null) {
                    textInput.setError(text.optString(0));
                }
            } catch (JSONException ignored) {
            }
        }

        private void setUploading(boolean uploading) {
            dialog.setTitle(uploading ? "업로드 중..." : "새로운 이미지 게시");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!uploading);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!uploading);
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                // 선택한 이미지를 최대 1600px, 촬영 방향(EXIF) 그대로 JPEG 로 압축
                Bitmap bitmap = loadUploadImage(imageUri, 1600);
                if (bitmap == null) {
                    responseBody = "이미지를 읽을 수 없습니다.";
                    return null;
                }
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);

                URL urlAPI = new URL(params[0]);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                if (!TextUtils.isEmpty(token)) {  // 토큰이 없으면(조회만) 헤더를 보내지 않음
                    conn.setRequestProperty("Authorization", "Token " + token);
                }
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoOutput(true);
                try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                    writeField(out, "title", params[1]);
                    writeField(out, "text", params[2]);
                    String fileName = "android_" + System.currentTimeMillis() + ".jpg";
                    out.write(("--" + BOUNDARY + "\r\n"
                            + "Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"\r\n"
                            + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(jpeg.toByteArray());
                    out.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                responseCode = conn.getResponseCode();
                InputStream body = responseCode < 400 ? conn.getInputStream() : conn.getErrorStream();
                if (body != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    body.close();
                    responseBody = result.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
                responseBody = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            return null;
        }

        private void writeField(DataOutputStream out, String name, String value) throws IOException {
            out.write(("--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected void onPostExecute(Void unused) {
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                dialog.dismiss();
                String title = "";
                try {
                    title = new JSONObject(responseBody).optString("title");
                } catch (JSONException ignored) {
                }
                Toast.makeText(MainActivity.this, "게시 완료: " + title, Toast.LENGTH_LONG).show();
                setStatus(null);  // 업로드 끝 → "업로드 중..." 안내 지움
                onClickDownload(null); // 목록 다시 동기화
            } else {
                setUploading(false);
                showFieldErrors();
                String message = "업로드 실패 (" + responseCode + ") " + responseBody;
                setStatus(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    /** [편의기능] 앱 캐시에 새 파일을 만들고 그 주소를 카메라 앱에 넘겨 촬영 (CAMERA 권한 없이 카메라 앱 사용) */
    private void startCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("카메라 저장 폴더를 만들 수 없습니다");
            }
            File photo = new File(dir, "camera_" + System.currentTimeMillis() + ".jpg");
            cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            takePhoto.launch(cameraPhotoUri);
        } catch (IOException | android.content.ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "카메라를 실행할 수 없습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    /** 긴 변이 maxSize 근처가 되도록 2의 배수로 줄여서 디코딩 (원본 그대로 읽으면 사진 1장에 7~10MB 메모리) */
    private static Bitmap decodeScaled(byte[] data, int maxSize) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        int sample = 1;
        while (Math.max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSize) {
            sample *= 2;
        }
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    /** 큰 사진은 maxSize 이하로 줄여서 읽고, EXIF 회전 정보대로 바로 세운다 (메모리 부족·눕은 사진 방지) */
    private Bitmap loadUploadImage(Uri uri, int maxSize) throws IOException {
        byte[] data;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            data = readAll(in);
        }
        Bitmap bitmap = decodeScaled(data, maxSize);
        if (bitmap == null) {
            return null;
        }
        int orientation = new ExifInterface(new ByteArrayInputStream(data))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int degrees = orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        if (degrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    // ------------------------------------------------------------------ [편의기능] 상세보기 / 삭제

    @Override
    public void onClick(int position) {
        if (position < 0 || position >= postList.size()) {
            return;
        }
        Post post = postList.get(position);
        View view = getLayoutInflater().inflate(R.layout.dialog_detail, null);
        ((ImageView) view.findViewById(R.id.imageDetail)).setImageBitmap(imageList.get(position));
        ((TextView) view.findViewById(R.id.textDetailMeta)).setText(post.meta());
        ((TextView) view.findViewById(R.id.textDetailBody)).setText(post.text);
        new AlertDialog.Builder(this)
                .setTitle(post.title)
                .setView(view)
                .setPositiveButton("닫기", null)
                .show();
    }

    @Override
    public void onLongClick(int position) {
        if (position < 0 || position >= postList.size()) {
            return;
        }
        Post post = postList.get(position);
        new AlertDialog.Builder(this)
                .setTitle("게시물 삭제")
                .setMessage("'" + post.title + "' 게시물을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> new DeletePost().execute(post.id))
                .show();
    }

    /** DELETE /api_root/Post/{id}/ — 서버는 작성자 본인만 허용 */
    private class DeletePost extends AsyncTask<Integer, Void, Integer> {
        @Override
        protected Integer doInBackground(Integer... ids) {
            try {
                URL url = new URL(site_url + "/api_root/Post/" + ids[0] + "/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (!TextUtils.isEmpty(token)) {  // 토큰이 없으면(조회만) 헤더를 보내지 않음
                    conn.setRequestProperty("Authorization", "Token " + token);
                }
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                return conn.getResponseCode();
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }

        @Override
        protected void onPostExecute(Integer code) {
            if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                Toast.makeText(MainActivity.this, "삭제했습니다", Toast.LENGTH_SHORT).show();
                onClickDownload(null);
            } else if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                Toast.makeText(MainActivity.this, "작성자만 삭제할 수 있습니다 (403)", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "삭제 실패 (" + code + ")", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
    }
}
