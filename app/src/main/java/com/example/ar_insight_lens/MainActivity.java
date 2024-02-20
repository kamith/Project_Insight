/*
    Project Insight Lens: Augmented Reality Optical Character Recognition Assistant for the Visually Impaired

    By: Kamith Mirissage, Alain Castro
    Copyrights:
        Copyright (c) 2017, Vuzix Corporation

*/

package com.example.ar_insight_lens;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.vuzix.sdk.speechrecognitionservice.VuzixSpeechClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.util.Base64;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Main activity for speech recognition sample
 */
public class MainActivity extends Activity {
    public final String LOG_TAG = "VoiceSample";
    public final String CUSTOM_SDK_INTENT = "com.vuzix.sample.vuzix_voicecontrolwithsdk.CustomIntent";
    Button buttonOpenAIApi;
    EditText textEntryField;
    VoiceCmdReceiver mVoiceCmdReceiver;
    private boolean mRecognizerActive = false;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    private String encoddedImage;
    private File photoFile;
    Uri imageUri;
    private String capturedImagePath = null;
    private Bitmap capturedImageBitmap = null;
    private ProgressBar progressBar;
    private TextView progressText;
    private ObjectAnimator progressAnimator;

    private String latestImagePath = null;

    private String base64Image = null;
    private final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    /**
     * when created we setup the layout and the speech recognition
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply the selected theme
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String themeName = prefs.getString("SelectedTheme", "RegularTheme"); // Default to regular theme
        switch (themeName) {
            case "RegularTheme":
                setTheme(R.style.RegularTheme_AR_Insight_Lens);
                break;
            case "BlackWhiteTheme":
                setTheme(R.style.BlackAndWhiteTheme_AR_Insight_Lens);
                break;
            case "DeuteranopiaTheme":
                setTheme(R.style.DeuteranopiaTheme_AR_Insight_Lens);
                break;
            case "ProtanopiaTheme":
                setTheme(R.style.ProtanopiaTheme_AR_Insight_Lens);
                break;
            case "TritanopiaTheme":
                setTheme(R.style.TritanopiaTheme_AR_Insight_Lens);
                break;
        }

        setContentView(R.layout.activity_main);
        buttonOpenAIApi = findViewById(R.id.btn_openai_api);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        setupButtonListeners();

        progressBar = (ProgressBar) findViewById(R.id.progressBar); // initiate the progress bar
        progressText = findViewById(R.id.progressText);
        progressBar.setVisibility(View.GONE);
        setProgressBarColor(themeName);

        // Create the voice command receiver class
        mVoiceCmdReceiver = new VoiceCmdReceiver(this);

        // Now register another intent handler to demonstrate intents sent from the service
        myIntentReceiver = new MyIntentReceiver();
        registerReceiver(myIntentReceiver , new IntentFilter(CUSTOM_SDK_INTENT));
    }

    private void setProgressBarColor(String cur) {
        int color = 0;

        String theme = cur;
        switch (theme) {
            case "RegularTheme":
                color = ContextCompat.getColor(this, R.color.completedRegularColor);
                break;
            case "BlackWhiteTheme":
                color = ContextCompat.getColor(this, R.color.gray);
                break;
            case "DeuteranopiaTheme":
                color = ContextCompat.getColor(this, R.color.completedDeuteranopiaColor);
                break;
            case "ProtanopiaTheme":
                color = ContextCompat.getColor(this, R.color.completedProtanopiaColor);
                break;
            case "TritanopiaTheme":
                color = ContextCompat.getColor(this, R.color.completedTritanopiaColor);
                break;
        }

        // Set the color to the progress bar
        if (progressBar != null) {
            progressBar.getProgressDrawable().setColorFilter(
                    color, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }



    private void setupButtonListeners() {
        buttonOpenAIApi.setOnClickListener(view -> OnOpenAIApiClick("Summarize the following menu items for me"));
    }
    /**
     * Sets up a button to change the application's theme.
     *
     * This method assigns an OnClickListener to a button identified by buttonId. When the button is clicked,
     * it saves the specified themeId to SharedPreferences and restarts the activity to apply the new theme.
     *
     * @param buttonId The resource ID of the button that will change the theme.
     * @param themeId The resource ID of the theme to be applied when the button is clicked.
     *
     * Note:
     * - The activity will be recreated when the theme is changed, so ensure to handle any necessary state
     *   saving/restoration.
     * - This method requires the activity to have a valid context for SharedPreferences and must be called
     *   within the lifecycle of an activity (typically in onCreate).
     * - The themes referenced by themeId should be defined in the styles.xml file.
     */
    private void setupThemeChangeButton(int buttonId, int themeId) {
        findViewById(buttonId).setOnClickListener(view -> {
            SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
            editor.putInt("themeId", themeId);
            editor.apply();

            // Restart the activity to apply the new theme
            recreate();
        });
    }


    /**
     * Unregister from the speech SDK
     */
    @Override
    protected void onDestroy() {
        mVoiceCmdReceiver.unregister();
        unregisterReceiver(myIntentReceiver);
        super.onDestroy();
    }


    /**
     * Utility to get the name of the current method for logging
     * @return String name of the current method
     */
    public String getMethodName() {
        return LOG_TAG + ":" + this.getClass().getSimpleName() + "." + new Throwable().getStackTrace()[1].getMethodName();
    }

    /**
     * Helper to show a toast
     * @param iStr String message to place in toast
     */
    private void popupToast(String iStr) {
        Toast myToast = Toast.makeText(MainActivity.this, iStr, Toast.LENGTH_LONG);
        myToast.show();
    }

    void OnMealPrompt(){
        OnOpenAIApiClick("Give me a sample meal with an appetizer, entree and salard, also a dessert if there is one, include total cost. Be brief and short");
    }
    void OnDrinkPrompt(){
        OnOpenAIApiClick("Give me a few drink options on the menu if you can find, be brief and short, if not say no drinks listed");
    }

    void OnChickenPrompt(){
        OnOpenAIApiClick("Give me a few chicken options on the menu, be brief and short");
    }

    void OnBeefPrompt(){
        OnOpenAIApiClick("Give me a few beef options on the menu, be brief and short");
    }

    void OnPorkPrompt(){
        OnOpenAIApiClick("Give me a few pork options on the menu, be brief and short");
    }

    void OnVegetarianPrompt(){
        OnOpenAIApiClick("Give me a few vegetarian options on the menu, be brief and short");
    }

    void OnSummarization(){
        OnOpenAIApiClick("Summarize what's on the image, if it is a menu, list out few general menu items. Be brief and short.");
    }
    void OnTakePhoto() {
        new Thread(() -> {
            try {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
                Log.d("Image", "IMAGE CAPTURED GOOD");
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();

        File cameraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        if (cameraDir.exists() && cameraDir.isDirectory()) {
            File[] files = cameraDir.listFiles();
            if (files != null && files.length > 0) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified())); // Sort in descending order
                File latestImage = files[0];

                latestImagePath = latestImage.getAbsolutePath();
                Log.d(LOG_TAG, "Latest image path: " + latestImagePath);

                // You can then use this path as needed
            } else {
                Log.d(LOG_TAG, "No files found in the directory");
            }
        } else {
            Log.d(LOG_TAG, "Camera directory does not exist");
        }

        if(latestImagePath != null){
            base64Image = encodeImage(latestImagePath);
            base64Image = base64Image.replace("\"", "\\\"");
        } else {
            Log.d(LOG_TAG, "In Encoding image, the latestImagePath was Null");
        }
    }

    void OnOpenAIApiClick(String userPrompt) {
        if (base64Image != null) {
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json");

            String jsonBody = "{\"model\": \"gpt-4-vision-preview\","
                    + "\"messages\": ["
                    + "    {"
                    + "        \"role\": \"user\","
                    + "        \"content\": ["
                    + "            {"
                    + "                \"type\": \"text\","
                    + "                \"text\": \"" + userPrompt + "\""
                    + "            },"
                    + "            {"
                    + "                \"type\": \"image_url\","
                    + "                \"image_url\": {"
                    + "                    \"url\": \"data:image/jpeg;base64," + base64Image + "\"" + "                }" + "            }"
                    + "        ]"
                    + "    }"
                    + "],"
                    + "\"max_tokens\": 300"
                    + "}";

            try {
                new JSONObject(jsonBody);
                Log.d("JSON CHECK", "Correct");
            } catch (JSONException ex) {
                ex.printStackTrace();
                Log.d("JSON CHECK", "Invalid");
            }
            RequestBody body = RequestBody.Companion.create(jsonBody, mediaType);

            Request request = new Request.Builder()
                    .url(OPENAI_API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + BuildConfig.OPENAI_API_KEY)
                    .build();

            new Thread(() -> {
                try {
                    runOnUiThread(() -> progressBar.setIndeterminate(true));
                    runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
                    runOnUiThread(() -> progressText.setText(""));
                    Response response = client.newCall(request).execute();
                    String responseData = response.body().string();

                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray choicesArray = jsonResponse.getJSONArray("choices");
                    if (choicesArray.length() > 0) {
                        JSONObject firstChoice = choicesArray.getJSONObject(0);
                        JSONObject messageObject = firstChoice.getJSONObject("message");
                        String content = messageObject.getString("content");

                        Log.i(LOG_TAG, "Extracted Content: " + content);
                    } else {
                        Log.i(LOG_TAG, "No content found in response");
                    }
                    runOnUiThread(() -> progressBar.setIndeterminate(false));
                    runOnUiThread(() -> progressText.setText("COMPLETED"));
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            capturedImageBitmap = (Bitmap) extras.get("data");

            // Log the bitmap details and save it globally
            if (capturedImageBitmap != null) {
                Log.d(LOG_TAG, "Bitmap details - Width: " + capturedImageBitmap.getWidth() + ", Height: " + capturedImageBitmap.getHeight());
                Log.d("Test", capturedImageBitmap.toString());
            } else {
                Log.d(LOG_TAG, "Failed to receive bitmap from camera");
            }
        }
    }

    private String encodeImage(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            Log.e(LOG_TAG, "Image path is null or empty");
            return null;
        }

        File imageFile = new File(imagePath);
        if (imageFile.exists()) {
            try (InputStream inputStream = new FileInputStream(imageFile)) {
                byte[] bytes = new byte[(int)imageFile.length()];
                inputStream.read(bytes);
                return Base64.encodeToString(bytes, Base64.NO_WRAP);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(LOG_TAG, "Image file does not exist");
        }
        return null;
    }

    /**
     * Handler called when "Listen" button is clicked. Activates the speech recognizer identically to
     * saying "Hello Vuzix".  Also handles "Stop" button clicks to terminate the recognizer identically
     * to a time-out
     */
    private void OnListenClick() {
        Log.e(LOG_TAG, getMethodName());
        // Trigger the speech recognizer to start/stop listening.  Listening has a time-out
        // specified in the Vuzix Smart Glasses settings menu, so it may terminate without us
        // requesting it.
        //
        // We want this to toggle to state opposite our current one.
        mRecognizerActive = !mRecognizerActive;
        // Manually calling this syncrhonizes our UI state to the recognizer state in case we're
        // requesting the current state, in which we won't be notified of a change.
        // Request the new state
        mVoiceCmdReceiver.TriggerRecognizerToListen(mRecognizerActive);
    }

    /**
     * Sample handler that will be called from the "popup message" button, or a voice command
     */
    public void OnPopupClick() {
        Log.e(LOG_TAG, getMethodName());
        popupToast(textEntryField.getText().toString());
    }

    /**
     * Sample handler that will be called from the "clear" button, or a voice command
     */
    public void OnClearClick() {
        Log.e(LOG_TAG, getMethodName());
        textEntryField.setText("");
    }

    /**
     * Sample handler that will be called from the "restore" button, or a voice command
     */
    public void OnRestoreClick() {
        Log.e(LOG_TAG, getMethodName());
        textEntryField.setText(getResources().getString(R.string.default_text));
    }

    /**
     * Sample handler that will be called from the secret "Edit Text" voice command (defined in VoiceCmdReceiver.java)
     */
    public void SelectTextBox() {
        Log.e(LOG_TAG, getMethodName());
        textEntryField.requestFocus();
        // Show soft keyboard for the user to enter the value.
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(textEntryField, InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * A callback for the SDK to notify us if the recognizer starts or stop listening
     *
     * @param isRecognizerActive boolean - true when listening
     */
    public void RecognizerChangeCallback(boolean isRecognizerActive) {
        Log.d(LOG_TAG, getMethodName());
        mRecognizerActive = isRecognizerActive;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    /**
     * You may prefer using explicit intents for each recognized phrase. This receiver demonstrates that.
     */
    private MyIntentReceiver  myIntentReceiver;

    public class MyIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(LOG_TAG, getMethodName());
            Toast.makeText(context, "Custom Intent Detected", Toast.LENGTH_LONG).show();
        }
    }


}
