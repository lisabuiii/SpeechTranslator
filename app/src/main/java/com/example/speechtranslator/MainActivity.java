package com.example.speechtranslator;

import static android.graphics.Color.*;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.SpeechSettings;

import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // Request code for audio recording permission
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    // Button to start - stop recording
    private Button startButton;
    // Displays transcription result
    private TextView resultTextView;
    // Indicates loading during transcription
    private ProgressBar progressBar;
    private Spinner spinnerFrom, spinnerTo;
    // Flag to track if recording permission is granted
    private boolean permissionToRecordAccepted = false;
    // Asks for permission to record audio
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};
    // Thread to handle network call for transcription
    private Thread recordingThread;
    // Byte array to add recorded audio data
    private byte[] byteArray;
    // Instance of VoiceRecorder class
    private VoiceRecorder thisVoiceRecorder;
    // Media Player to play audio
    private MediaPlayer mediaPlayer;
    private SpeechClient speechClient;

    // Implementation of onVoiceListener interface to receive callbacks from VoiceRecorder
    private final VoiceRecorder.onVoiceListener thisVoiceListener = new VoiceRecorder.onVoiceListener() {

        // Called when VoiceRecorder starts detecting voice
        public void onVoiceStart() {}

        // Called repeatedly while VoiceRecorder is actively recording voice data
        public void onVoice(byte[] data, int size) {
            byteArray = appendByteArrays(byteArray, data);
        }

        // Called when VoiceRecorder stops detecting voice activity
        public void onVoiceEnd() {
            // Shows progress bar on UI thread to show that transcription is in progress
            runOnUiThread(new Runnable() {
                public void run() {
                    progressBar.setVisibility(View.VISIBLE);
                }
            });
            // Logs the recorded byte array for debugging
            Log.e("ByteArray", "" + byteArray);
            // Starts the transcription process with audio data
            transcribeRecording(byteArray);

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Initializes UI elements
        startButton = findViewById(R.id.start_button);
        resultTextView = findViewById(R.id.result_text_view);
        progressBar = findViewById(R.id.progress_bar);
        spinnerFrom = findViewById(R.id.spinnerFrom);
        spinnerTo = findViewById(R.id.spinnerTo);
        List<String> languages = Arrays.asList("English", "Vietnamese", "Spanish", "Mandarin", "Cantonese", "French", "Arabic");
        ArrayAdapter adapter = new ArrayAdapter(getApplicationContext(), R.layout.menu, languages);
        spinnerFrom.setDropDownWidth(R.layout.menu);
        spinnerTo.setDropDownWidth(R.layout.menu);
        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);
        // Requests audio recording permission from user
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        // Sets an OnClickListener for startButton
        startButton.setOnClickListener(new View.OnClickListener() {
            @RequiresPermission(Manifest.permission.RECORD_AUDIO)
            public void onClick(View v) {
                if (permissionToRecordAccepted) {
                    String languageFrom = spinnerFrom.getSelectedItem().toString();
                    Toast.makeText(getApplicationContext(), languageFrom, Toast.LENGTH_SHORT).show();
                    String languageTo = spinnerTo.getSelectedItem().toString();
                    Toast.makeText(getApplicationContext(), languageTo, Toast.LENGTH_SHORT).show();

                    // Checks if audio recording permission is granted
                    if (startButton.getText().toString().equals("Start")) {
                        startButton.setText("Loading...");
                        startButton.setBackgroundColor(RED);
                        // Disables button to prevent further clicks
                        startButton.setEnabled(false);
                        startVoiceRecorder();
                    } else {
                        // Stops recording
                        stopVoiceRecorder();
                        startButton.setText("Start");
                    }
                } else {
                    Log.w("MainActivity", "Permission not granted");
                }
            }
        });
        // Initializes connection to Google Cloud Speech-to-Text API
        initializeSpeechClient();
    }

    // Initializes SpeechClient for interacting with Google Cloud Speech-to-Text API
    private void initializeSpeechClient() {
        try {
            // Loads Google Cloud credentials from raw source file
            GoogleCredentials credentials = GoogleCredentials.fromStream(getResources().openRawResource(R.raw.credentials));
            // Creates this using loaded credentials
            FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
            // Builds SpeechSettings with credentials provider and creates SpeechClient
            speechClient = SpeechClient.create(SpeechSettings.newBuilder().setCredentialsProvider(credentialsProvider).build());

        } catch (IOException e) {
            Log.e("Init", "InitException" + e.getMessage());
        }
    }

    // Sends recorded audio data to Google Cloud Speech-to-Text API for transcription
    private void transcribeRecording(byte[] data) {
        try {
            Log.d("API_CALL", "API CALL STARTED..."); // Logs when API call starts

            // Creates new thread to perform network operation - API call
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        // Calls the Speech-to-Text API to recognize audio
                        // speechClient.recognize = sends audio data to Google Cloud Speech-to-Text service
                        // createRecognizeRequestFromVoice = takes recorded audio data (byte array) and creates
                        // a RecognizeRequest object, which includes audio content, desired audio encoding
                        // sample rate, and language code for transcription
                        // recognize() returns a RecognizeResponse object - contains result of transcription
                        // including a list of possible transcriptions
                        // also produces/contains a list of SpeechRecognitionResult objects
                        RecognizeResponse response = speechClient.recognize(createRecognizeRequestFromVoice(data));

                        StringBuilder transcriptBuilder = new StringBuilder();

                        // Loops through list of recognition results in response
                        // getResutlsList returns a list of SpeechRecogntionResult objects
                        // Each of them represents a potential transcription of audio
                        // with different confidence levels
                        for (SpeechRecognitionResult result : response.getResultsList()) {
                            // Gets the most likely transcription from first alternative
                            String transcript = result.getAlternativesList().get(0).getTranscript();
                            transcriptBuilder.append(transcript).append(" ");
                            updateResult(transcript);
                        }

                        String languageFrom = spinnerFrom.getSelectedItem().toString();
                        String languageTo = spinnerTo.getSelectedItem().toString();

                        // Instantiating TranslateText class from MainActivity
                        // MainActivity is an Activity - subclass of Context
                        // MainActivity.this provides reference to the Context associated with the currently
                        // running MainActivity
                        Translation translator;

                        String fromCode = "";
                        String toCode = "";

                        if (languageFrom.equals("English")) {
                            fromCode = "en";
                        } else if (languageFrom.equals("Vietnamese")) {
                            fromCode = "vi";
                        } else if (languageFrom.equals("Spanish")) {
                            fromCode = "es";
                        } else if (languageFrom.equals("Mandarin") || languageTo.equals("Cantonese")) {
                            fromCode = "zh";
                        } else if (languageFrom.equals("French")) {
                            fromCode = "fr";
                        } else if (languageFrom.equals("Arabic")) {
                            fromCode = "ar";
                        }

                        if (languageTo.equals("English")) {
                            toCode = "en";
                        } else if (languageTo.equals("Vietnamese")) {
                            toCode = "vi";
                        } else if (languageTo.equals("Spanish")) {
                            toCode = "es";
                        } else if (languageTo.equals("Mandarin") || languageTo.equals("Cantonese")) {
                            toCode = "zh";
                        } else if (languageTo.equals("French")) {
                            toCode = "fr";
                        } else if (languageTo.equals("Arabic")) {
                            toCode = "ar";
                        }

                        translator = new Translation(MainActivity.this, fromCode, toCode);

                        translator.translateText(transcriptBuilder.toString(), new Translation.TranslationCallback() {
                            @Override
                            public void onSuccess(String translatedText) {
                                updateResult(translatedText);
                                try {
                                    // Text to Speech process
                                    TextToSpeech textToSpeech = new TextToSpeech();
                                    String targetCode = "";
                                    if (languageTo.equals("English")) {
                                        targetCode = "en-US";
                                    } else if (languageTo.equals("Vietnamese")) {
                                        targetCode = "vi-VN";
                                    } else if (languageTo.equals("Spanish")) {
                                        targetCode = "es-US";
                                    } else if (languageTo.equals("Mandarin")) {
                                        targetCode = "cmn-CN";
                                    } else if (languageTo.equals("Cantonese")) {
                                        targetCode = "yue-HK";
                                    } else if (languageTo.equals("French")) {
                                        targetCode = "fr-FR";
                                    } else if (languageTo.equals("Arabic")) {
                                        targetCode = "ar-XA";
                                    }

                                    ByteString audioContents = textToSpeech.synthesizeText(translatedText, MainActivity.this, targetCode);
                                    playAudio(audioContents);
                                } catch (Exception e) {
                                    Log.e("TTS", "failed: " + e.getMessage());
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.e("Translation", "Translation failed: " + e.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        Log.e("STT", " " + e.getMessage());
                    }
                }
            });
            // Starts transcription thread
            recordingThread.start();
        } catch (Exception e) {
            Log.e("SpeechTranslator", "failed" + e.getMessage());
        }
    }

    // Creates RecognizeRequest object to be sent to Google Cloud Speech-to-Text API
    private RecognizeRequest createRecognizeRequestFromVoice(byte[] audioData) {
        String fromLanguage = spinnerFrom.getSelectedItem().toString();
        // Creates RecognitionAudio object with recorded audio data as bytes
        RecognitionAudio audioBytes = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioData))
                .build();

        // Creates RecognitionConfig object with audio encoding, sample rate, and language code
        if (fromLanguage.equals("English")){
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("en-US")
                    .build();

            // Builds the RecognizeRequest with configuration and audio
            return RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audioBytes)
                    .build();
        } else if (fromLanguage.equals("Vietnamese")) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("vi-VN")
                    .build();

            // Builds the RecognizeRequest with configuration and audio
            return RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audioBytes)
                    .build();
        } else if (fromLanguage.equals("Spanish")) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("es-US")
                    .build();

            // Builds the RecognizeRequest with configuration and audio
            return RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audioBytes)
                    .build();
        } else if (fromLanguage.equals("Mandarin")) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("cmn-Hans-CN")
                    .build();

            // Builds the RecognizeRequest with configuration and audio
            return RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audioBytes)
                    .build();
        } else if (fromLanguage.equals("Cantonese")) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("yue-Hant-HK")
                    .build();

            // Builds the RecognizeRequest with configuration and audio
            return RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audioBytes)
                    .build();
        } else if (fromLanguage.equals("French")) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("fr-FR")
                    .build();

            // Builds the RecognizeRequest with configuration and audio
            return RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audioBytes)
                    .build();
        } else if (fromLanguage.equals("Arabic")) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("ar-IQ")
                    .build();

            // Builds the RecognizeRequest with configuration and audio
            return RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audioBytes)
                    .build();
        }

        return null;
    }

    // Updates result TextView with transcription on main UI thread
    private void updateResult(final String transcript) {
        runOnUiThread(new Runnable() {
            public void run() {
                progressBar.setVisibility(View.GONE); // Hides progress bar
                playSound(); // Plays a sound to indicate transcription completion
                resultTextView.setText(transcript); // Shows transcription on result TextView
                clearByteArray(byteArray); // Clears byteArray to prepare for next recording
                startButton.setEnabled(true);
                startButton.setText("Start"); // Changes startButton back to Start
                startButton.setBackgroundColor(Color.GREEN);
                stopVoiceRecorder(); // Stops voice recorder
            }
        });
    }

    // Starts voice recording process
    private void startVoiceRecorder() {
        // Stops any current voice recorder if it's running
        if (thisVoiceRecorder != null) {
            thisVoiceRecorder.stop();
        }
        // Creates new instance of VoiceRecorder, passing voice listener
        thisVoiceRecorder = new VoiceRecorder(thisVoiceListener);
        thisVoiceRecorder.start(); // Starts audio recording
    }

    // Stops voice recording process
    private void stopVoiceRecorder() {
        // Stops voice recorder if it's running
        if (thisVoiceRecorder != null) {
            thisVoiceRecorder.stop();
            thisVoiceRecorder = null;
        }

        // Waits for transcription thread to finish if it's running
        if (recordingThread != null) {
            try {
                // Blocks current thread until recordingThread completes
                // When join() is called, the calling thread enters waiting state
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordingThread = null;
        }

        startButton.setText("Start");
    }

    // Method to append two byte arrays
    private byte[] appendByteArrays(byte[] array1, byte[] array2) {
        // ByteArrayOutputStream handles flexible and unknown length of new byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            outputStream.write(array1);
            outputStream.write(array2);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return outputStream.toByteArray(); // Converts to byte array
    }

    private void clearByteArray(byte[] array) {
        Arrays.fill(array, (byte) 0);
    }

    // Called after user responds to permission request
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            // Sets permission flag based on user's response
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionToRecordAccepted) {
            // Disables start button if permission is not granted
            startButton.setEnabled(false);
        }
    }

    // Plays a sound to indicate completion of transcription
    private void playSound() {
        // Creates a MediaPlayer instance from audio resource
        mediaPlayer = MediaPlayer.create(this, R.raw.audio);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                // Releases MediaPlayer resources when playback is complete
                if (mediaPlayer != null){
                    mediaPlayer.release();
                }

            }
        });
        // Plays sound
        mediaPlayer.start();
    }

    private void playAudio(ByteString audioContents) {
        try {
            // Creates temp file in app's cache directory
            File tempAudio = new File(getCacheDir(), "temp_audio.mp3");
            FileOutputStream output = new FileOutputStream(tempAudio);
            output.write(audioContents.toByteArray());
            output.close();

            // Starts MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(tempAudio.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> {
                // cleans up resources when playback is complete
                mp.release();
                mediaPlayer = null;
                tempAudio.delete();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("AudioPlayer", "Error playing audio");
                mp.release();
                mediaPlayer = null;
                tempAudio.delete();
                return true;
            });
        } catch (IOException e) {
            Log.e("AudioPlayer", "Error saving or playing audio " + e.getMessage());
        }


    }

}
