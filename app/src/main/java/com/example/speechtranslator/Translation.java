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

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
public class Translation {
    private final Translator translator;

    // Needs this interface because translation happens in background
    // Needs to be notified when translation is successful or not
    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onError(Exception e);
    }

    // Passes the Context from MainActivity
    public Translation(Context context, String fromCode, String toCode) {

        TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(fromCode)
                    .setTargetLanguage(toCode)
                    .build();


        translator = com.google.mlkit.nl.translate.Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> Log.d("Translator", "Model downloaded successfully"))
                .addOnFailureListener(e -> Log.e("Translator", "Model failed to download: " + e.getMessage()));
    }

    // Object callback is passed from MainActivity
    // Representing this instance of translation
    // If it's successful, then triggers onSuccess of the callback's and vice versa
    public void translateText(String transcript, TranslationCallback callback){
        translator.translate(transcript)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    public void close() {
        translator.close();
    }
}

