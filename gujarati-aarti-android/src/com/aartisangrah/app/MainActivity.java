package com.aartisangrah.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
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
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
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
        else web.loadUrl("file:///android_asset/www/index.html");
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
            if (lang != null && !lang.isEmpty()) tts.setLanguage(Locale.forLanguageTag(lang));
            tts.setSpeechRate(rate > 0 ? rate : 0.85f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        }

        @JavascriptInterface
        public void stop() { if (tts != null) tts.stop(); }
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
