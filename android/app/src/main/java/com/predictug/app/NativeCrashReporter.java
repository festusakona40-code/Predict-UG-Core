package com.predictug.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NativeCrashReporter {
    private static final String PREFS = "predict_ug_native_crash";
    private static final String ENDPOINT =
            "https://rnvyrlmrwhfauuskyhje.supabase.co/functions/v1/client-error-report";

    private NativeCrashReporter() {}

    @SuppressLint("ApplySharedPref")
    public static synchronized void install(Context context, String source, String appVersion) {
        Context app = context.getApplicationContext();
        flushPrevious(app, source, appVersion);

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                String stack = sw.toString();
                if (stack.length() > 6000) stack = stack.substring(0, 6000);

                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("pending", true)
                        .putString("message", safe(error.getMessage(), 700))
                        .putString("stack", stack)
                        .putString("source", source)
                        .putString("version", appVersion)
                        .commit();
            } catch (Exception ignored) {
            }

            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                System.exit(10);
            }
        });
    }

    private static String safe(String value, int max) {
        String v = value == null || value.trim().isEmpty() ? "Native Android crash" : value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    public static void reportNonFatal(
            Context context,
            String source,
            String appVersion,
            String message,
            String stack
    ) {
        Context app = context.getApplicationContext();
        new Thread(() -> postReport(
                app,
                source == null ? "android" : source,
                appVersion == null ? "unknown" : appVersion,
                safe(message, 700),
                stack == null ? "" : stack,
                "native_nonfatal"
        ), "predict-ug-nonfatal").start();
    }

    private static void postReport(
            Context app,
            String source,
            String version,
            String message,
            String stack,
            String kind
    ) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("source", source);
            body.put("app_version", version);
            body.put("message", safe(message, 700));
            body.put("stack", stack.length() > 6000 ? stack.substring(0, 6000) : stack);

            JSONObject reportContext = new JSONObject();
            reportContext.put("kind", kind);
            reportContext.put("sdk_int", android.os.Build.VERSION.SDK_INT);
            reportContext.put("manufacturer", android.os.Build.MANUFACTURER);
            reportContext.put("model", android.os.Build.MODEL);
            body.put("context", reportContext);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
            connection.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void flushPrevious(Context app, String fallbackSource, String fallbackVersion) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("pending", false)) return;

        String message = prefs.getString("message", "Native Android crash");
        String stack = prefs.getString("stack", "");
        String source = prefs.getString("source", fallbackSource);
        String version = prefs.getString("version", fallbackVersion);

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject();
                body.put("source", source == null ? fallbackSource : source);
                body.put("app_version", version == null ? fallbackVersion : version);
                body.put("message", safe(message, 700));
                body.put("stack", stack == null ? "" : stack);

                JSONObject context = new JSONObject();
                context.put("kind", "native_uncaught_exception");
                context.put("sdk_int", android.os.Build.VERSION.SDK_INT);
                context.put("manufacturer", android.os.Build.MANUFACTURER);
                context.put("model", android.os.Build.MODEL);
                body.put("context", context);

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    prefs.edit().clear().apply();
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "predict-ug-crash-flush").start();
    }
}
