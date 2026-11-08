package com.aartisangrah.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.webkit.WebResourceResponse;
import java.io.IOException;
import java.io.InputStream;
import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aarti Sangrah: hosts the bundled web app (assets/www) in a WebView and exposes
 * Android's native Text-to-Speech to it as window.AndroidTTS, because WebView does
 * not implement the browser speechSynthesis API.
 */
public class MainActivity extends Activity {

    private static final int FILE_CHOOSER = 41;
    private static final int MIC_PERMISSION = 42;
    private PermissionRequest pendingMic;
    /** Bundled assets are served from this https origin so localStorage / IndexedDB
     *  (favourites, saved recordings) persist like on a website. */
    private static final String HOST = "appassets.androidplatform.net";
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1E1A38"));

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                try {
                    InputStream in = getAssets().open("www" + path);
                    return new WebResourceResponse(mime(path), "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    // YouTube and other web links open in the browser / YouTube app
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
                    return true;
                }
                return false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            /** "🎙 Record now" asks for the microphone: grant it after the Android permission dialog. */
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsMic = false;
                for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                if (!wantsMic) { request.deny(); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pendingMic = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) { ttsReady = (status == TextToSpeech.SUCCESS); }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { notifyJs("done", id); }
            @Override public void onError(String id) { notifyJs("error", id); }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("https://" + HOST + "/index.html");
    }

    private static String mime(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".mp3")) return "audio/mpeg";
        if (path.endsWith(".m4a") || path.endsWith(".aac")) return "audio/mp4";
        if (path.endsWith(".ogg") || path.endsWith(".opus")) return "audio/ogg";
        if (path.endsWith(".wav")) return "audio/wav";
        if (path.endsWith(".json") || path.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }

    /** Picks the best installed voice for a locale (highest quality, already downloaded). */
    private void useBestVoice(Locale loc) {
        tts.setLanguage(loc);
        try {
            Voice best = null;
            for (Voice v : tts.getVoices()) {
                if (!v.getLocale().getLanguage().equals(loc.getLanguage())) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                if (best == null || v.getQuality() > best.getQuality()
                        || (v.getQuality() == best.getQuality() && !v.isNetworkConnectionRequired() && best.isNetworkConnectionRequired())) best = v;
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) { }
    }

    private void notifyJs(final String type, final String id) {
        final String js = "window.__ttsEvent && window.__ttsEvent('" + type + "','" + id.replace("'", "") + "')";
        runOnUiThread(new Runnable() {
            @Override public void run() { web.evaluateJavascript(js, null); }
        });
    }

    private class TtsBridge {
        @JavascriptInterface
        public boolean ready() { return ttsReady; }

        /** Comma-separated list of the Indian voices installed on this phone. */
        @JavascriptInterface
        public String langs() {
            if (!ttsReady) return "";
            String[] tags = {"gu-IN", "hi-IN", "mr-IN", "en-IN"};
            List<String> ok = new ArrayList<>();
            for (String t : tags) {
                int r = tts.isLanguageAvailable(Locale.forLanguageTag(t));
                if (r >= TextToSpeech.LANG_AVAILABLE) ok.add(t);
            }
            StringBuilder sb = new StringBuilder();
            for (String t : ok) { if (sb.length() > 0) sb.append(','); sb.append(t); }
            return sb.toString();
        }

        @JavascriptInterface
        public void speak(String text, String lang, float rate, String id) {
            if (!ttsReady) return;
            if (lang != null && !lang.isEmpty()) useBestVoice(Locale.forLanguageTag(lang));
            tts.setSpeechRate(rate > 0 ? rate : 0.8f);
            tts.setPitch(0.95f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        }

        @JavascriptInterface
        public void stop() { if (tts != null) tts.stop(); }

        /** Opens the screen where the user downloads Hindi / Gujarati voice data. */
        @JavascriptInterface
        public void installVoices() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (ActivityNotFoundException e) {
                        try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
                        catch (ActivityNotFoundException ignored) { }
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == MIC_PERMISSION && pendingMic != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pendingMic.deny();
            pendingMic = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
