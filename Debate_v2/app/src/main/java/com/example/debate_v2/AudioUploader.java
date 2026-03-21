package com.example.debate_v2;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class AudioUploader {

    private static final String TAG = "AudioUploader";

    // 🔴 CHANGE THIS to your Flask server
    private static final String UPLOAD_URL = "http://192.168.1.8:8000/analyze/debate";



    // =====================================================
    // Public API (used by AgoraAudioCallActivity)
    // =====================================================

    /**
     * Upload audio + metadata asynchronously
     */
    public static void uploadCall(
            File audioFile,
            File metadataFile,
            Runnable onComplete
    ) {
        new Thread(() -> {
            boolean success = false;
            try {
                uploadCall(audioFile, metadataFile);
                success = true;
                Log.d(TAG, "Upload completed");

            } catch (Exception e) {
                Log.e(TAG, "Upload failed", e);

            } finally {
                if (onComplete != null) {
                    boolean finalSuccess = success;
                    onComplete.run();

                    Log.d(TAG, "Upload finished, success=" + finalSuccess);
                }
            }

        }).start();
    }

    /**
     * Core upload logic (blocking)
     */
    public static void uploadCall(
            File audioFile,
            File metadataFile
    ) throws Exception {

        if (!audioFile.exists() || !metadataFile.exists()) {
            throw new IllegalArgumentException("Audio or metadata file missing");
        }

        String boundary = UUID.randomUUID().toString();
        String LINE_FEED = "\r\n";

        URL url = new URL(UPLOAD_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setConnectTimeout(15_000); // 15s
        conn.setReadTimeout(60_000);    // AI can take time
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");

        conn.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=" + boundary
        );
        conn.setChunkedStreamingMode(0);


        OutputStream outputStream = conn.getOutputStream();

        // ---------------- AUDIO FILE ----------------
        writeFilePart(
                outputStream,
                boundary,
                "audio",
                audioFile,
                "audio/wav"
        );

        // ---------------- METADATA FILE ----------------
        writeFilePart(
                outputStream,
                boundary,
                "metadata",
                metadataFile,
                "application/json"
        );

        // ---------------- END ----------------
        outputStream.write(("--" + boundary + "--").getBytes());
        outputStream.write(LINE_FEED.getBytes());
        outputStream.flush();
        outputStream.close();

        int responseCode = conn.getResponseCode();

        InputStream responseStream =
                (responseCode >= 200 && responseCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

        StringBuilder response = new StringBuilder();
        if (responseStream != null) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = responseStream.read(buf)) != -1) {
                response.append(new String(buf, 0, len));
            }
            responseStream.close();
        }

        Log.d(TAG, "Server response code: " + responseCode);
        Log.d(TAG, "Server response body: " + response.toString());

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Upload failed: " + response);
        }


        conn.disconnect();
    }

    // =====================================================
    // Helpers
    // =====================================================

    private static void writeFilePart(
            OutputStream out,
            String boundary,
            String fieldName,
            File uploadFile,
            String contentType
    ) throws Exception {

        String LINE_FEED = "\r\n";

        out.write(("--" + boundary + LINE_FEED).getBytes());
        out.write((
                "Content-Disposition: form-data; name=\"" + fieldName +
                        "\"; filename=\"" + uploadFile.getName() + "\"" +
                        LINE_FEED
        ).getBytes());
        out.write(("Content-Type: " + contentType + LINE_FEED).getBytes());
        out.write(LINE_FEED.getBytes());

        FileInputStream inputStream = new FileInputStream(uploadFile);
        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        out.write(LINE_FEED.getBytes());
    }
}
