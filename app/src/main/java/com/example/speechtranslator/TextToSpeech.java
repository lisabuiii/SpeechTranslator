/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.speechtranslator;
import android.content.Context;
import android.util.Log;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
public class TextToSpeech {
    public TextToSpeech(){

    }
    public ByteString synthesizeText(String text, Context context, String targetLanguage) throws Exception {
        TextToSpeechClient textToSpeechClient = null;
        try {
            // Getting credentials from Google API
            InputStream credentialsStream = context.getResources().openRawResource(R.raw.credentials);
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);

            TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .build();

            textToSpeechClient = TextToSpeechClient.create(settings);

            // Sets text input to be synthesized
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(targetLanguage)
                    .setSsmlGender(SsmlVoiceGender.FEMALE)
                    .build();

            // Selects type of audio file to return
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();

            // Performs text-to-speech request
            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

            // Gets audio contents from response
            ByteString audioContents = response.getAudioContent();

            // Writes response to output file
            File tempAudioFile = new File(context.getCacheDir(), "output.mp3");
            try (OutputStream out = new FileOutputStream(tempAudioFile)) {
                out.write(audioContents.toByteArray());
                Log.d("TTS", "Audio saved to: " + tempAudioFile.getAbsolutePath());
                return audioContents;
            }
        } catch (IOException e) {
            Log.e("TTS", "failed " + e.getMessage());
        }

        return null;
    }
}
