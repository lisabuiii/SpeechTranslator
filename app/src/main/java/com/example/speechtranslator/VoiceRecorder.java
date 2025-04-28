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

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

public class VoiceRecorder {
    // Array of sample rates to initialize the AudioRecord
    private static final int[] SAMPLE_RATE_OPTIONS= new int[]{16000,11025,22050,44100, 8000};
    // Channel configuration: mono (single channel)
    private static final int SINGLE_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    // Audio encoding format: 16-bit PCM
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    // Minimum amplitude level to consider as voice activity
    private static final int AMPLITUDE_THRESHOLD = 1500;
    // Duration after which to consider speech ended if voice is not detected
    private static final int SPEECH_TIMEOUT_MILLIS = 2000;
    // Maximum duration for a single speech utterance
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;
    // Callback interface to notify client about voice recording events
    private final onVoiceListener thisVoiceListener;
    // Android AudioRecord object used for capturing audio
    private AudioRecord thisAudioRecord;
    // Thread on which audio processing will occur
    private Thread thisThread;
    // Buffer to hold raw audio data read from AudioRecord
    private byte[] thisBuffer;
    // Lock object to synchronize accesss to shared resources
    private final Object thisLock = new Object();
    // Timestamp for last time voice is detected
    private long thisLastVoiceHeardMillis = Long.MAX_VALUE;
    // Timestamp for when current voice utterance started
    private long thisVoiceStartedMillis;
    public static abstract class onVoiceListener {
        // Called when recorder starts detecting sound above threshold
        public void onVoiceStart() {

        }

        // Called continuously while recorder is actively detecting sound above threshold
        // Provides raw audio data and its size
        public void onVoice(byte[] data, int size) {

        }

        // Called when recorder stops detecting sound above threshold after timeout
        // or when maximum speech length is reached
        public void onVoiceEnd() {

        }
    }

    public VoiceRecorder(@NonNull onVoiceListener VoiceListener) {
        thisVoiceListener = VoiceListener;
    }

    // Starts recording audio
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void start() {
        // Stops ongoing recording before starting new one
        stop();
        // Checks if AudioRecord was successfully initialized
        if (thisAudioRecord == null) {
            thisAudioRecord = createAudioRecord();
        }
        if (thisAudioRecord != null) {
            try {
                Log.d("VoiceRecorder", "audioRecord returned successful");
                thisAudioRecord.startRecording();
                thisThread = new Thread(new ProcessVoice());
                thisThread.start();
            } catch (IllegalStateException e) {
                Log.e("VoiceRecorder", "Error starting recording: " + e.getMessage());
                releaseAudioRecord(); // Release resources if starting fails
                // Optionally, notify listener of the error
                if (thisVoiceListener != null) {
                    Log.d("VoiceListener","Failed to start audio recording");
                }

                return; // Exit the start() method
            }
        } else {
            Log.e("VoiceRecorder", "Failed to create AudioRecord instance.");
            if (thisVoiceListener != null) {
                Log.d("VoiceListener",  "Failed to initialize audio recorder.");
            }
        }
    }

    private void releaseAudioRecord() {
        if (thisAudioRecord != null) {
            if (thisAudioRecord.getState() == AudioRecord.RECORDSTATE_RECORDING) {
                thisAudioRecord.stop();
            }
            thisAudioRecord.release();
            thisAudioRecord = null;
        }
    }

    //Stops recording audio
    public void stop() {
        // If no other thread currently holds the lock on thisLock, current thread successfully
        // acquires the lock and is allowed to execute code within this block to ensure
        // thread-safe operations
        synchronized (thisLock) {
            // Dismisses current ongoing utterance
            dismiss();
            // Interrupts audio processing thread of it's running
            if (thisThread != null) {
                thisThread.interrupt();
                thisThread = null;
            }
            // Stops and releases AudioRecord object if it's initialized
            if (thisAudioRecord != null) {
                thisAudioRecord.stop();
                thisAudioRecord.release();
                thisAudioRecord = null;
            }
            // Releases audio data buffer
            thisBuffer = null;
        }
    }

    // Dismisses currently ongoing utterance without waiting for timeout
    public void dismiss() {
        // Checks if voice utterance was in progress
        if (thisLastVoiceHeardMillis != Long.MAX_VALUE) {
            // Resets last voice heard timestamp
            thisLastVoiceHeardMillis = Long.MAX_VALUE;
            // Notifies callback that voice utterance has ended
            thisVoiceListener.onVoiceEnd();
        }
    }

    // Retrieves sample rate currently used to record audio
    public int getSampleRate() {
        if (thisAudioRecord != null) {
            return thisAudioRecord.getSampleRate();
        }
        return 0;
    }

    // Attempts to create and initialize an AudioRecord object with one of the options for sample rates
    private AudioRecord createAudioRecord() {
        for (int sampleRate: SAMPLE_RATE_OPTIONS) {
            Log.d("VoiceRecorder", "Trying sampleRate: " + sampleRate);
            // Get minimum buffer size required for current sample rate, channel, and encoding
            final int sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, SINGLE_CHANNEL, AUDIO_ENCODING);
            Log.d("VoiceRecorder", "Calculated buffer size: " + sizeInBytes);
            // If buffer size is invalid, skip to next sample rate
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                Log.w("VoiceRecorder", "Error bad value for sampleRate: " + sampleRate);
                continue;
            }
            // Creates new AudioRecord instance
            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, SINGLE_CHANNEL, AUDIO_ENCODING, sizeInBytes);
            Log.d("VoiceRecorder", "AudioRecord state for " + sampleRate + ": " + audioRecord.getState());
            // Checks if AudioRecord object was successfully initialized
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                // Allocates audio data buffer with according size
                thisBuffer = new byte[sizeInBytes];
                Log.d("VoiceRecorder", "Returning audioRecord");
                // Returns initialized AudioRecord object
                return audioRecord;
            } else {
                Log.w("VoiceRecorder", "Failed to initialize AudioRecord for sampleRate: " + sampleRate + ", state: " + audioRecord.getState());
                // If initialization failed, release AudioRecord object and try  next sample rate
                audioRecord.release();
            }
        }
        // If none of sample rates was successfully initialized, returns null
        return null;
    }

    // Runnable class responsible for continuously processing captured audio
    private class ProcessVoice implements Runnable {

        // Main execution method of thread
        public void run() {
            // Keeps running while thread is not interrupted
            while (true) {
                // Acquires a lock for thread-safe access to shared resources
                synchronized (thisLock) {
                    // Checks if thread is interrupted and breaks loop if it is
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    Log.d("ProcessVoice", "Processing voice");
                    // Reads audio data from AudioRecord into buffer
                    final int size =  thisAudioRecord.read(thisBuffer, 0, thisBuffer.length);
                    final long now = System.currentTimeMillis();
                    // Checks if voice activity is detected in current audio buffer
                    if (isHearingVoice(thisBuffer, size)) {
                        // If this is first time voice is detected in current utterance
                        if (thisLastVoiceHeardMillis == Long.MAX_VALUE) {
                            // Saves down start time of voice utterance
                            thisVoiceStartedMillis = now;
                            // Notifies callback that voice has started
                            thisVoiceListener.onVoiceStart();
                        }
                        // Notifies callback with current audio data
                        thisVoiceListener.onVoice(thisBuffer, size);
                        // Updates timestamp of last voice detected
                        thisLastVoiceHeardMillis = now;
                        // Checks ojf maximum speech length has exceeded
                        if (now - thisVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                            // Ends current utterance
                            end();
                        }
                    } else if (thisLastVoiceHeardMillis != Long.MAX_VALUE) {
                        // If no voice is currently detected but a voice utterance was in progress
                        // provides current (silent) audio data to callback
                        thisVoiceListener.onVoice(thisBuffer, size);
                        // Checks if speech timeout has reached since last voice activity
                        if (now - thisLastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS) {
                            // Ends current utterance
                            end();
                        }
                    }
                }
            }
        }
    }

    // Ends current voice utterance
    private void end() {
        // Resets timestamp
        thisLastVoiceHeardMillis = Long.MAX_VALUE;
        thisVoiceListener.onVoiceEnd();
    }

    // Method to determine if voice activity is present in audio buffer
    private boolean isHearingVoice(byte[] buffer, int size) {
        // Loops through buffer, processing two bytes at a time (for 16-bit PCM)
        for (int i = 0; i < size - 1; i += 2) {
            // Buffer has LINEAR16 in little endian format
            // Extracts lower byte
            int s = buffer[i + 1];
            // Takes s's absolute value
            s = Math.abs(s);
            // Shifts higher byte 8 bits to the left to make sure the whole thing is 16 bits
            s <<= 8;
            // Adds absolute value of lower byte to get the 16-bit amplitude
            s += Math.abs(buffer[i]);
            // If calculated amplitude is above defined threshold, returns true for voice activity
            if (s > AMPLITUDE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }
}
