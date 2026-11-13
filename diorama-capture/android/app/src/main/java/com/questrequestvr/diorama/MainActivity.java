package com.questrequestvr.diorama;

import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                String host = request.getUrl().getHost();
                if (("https".equals(scheme) && "localhost".equals(host)) || "data".equals(scheme) || "blob".equals(scheme)) {
                    return super.shouldInterceptRequest(view, request);
                }
                // Runtime content is local. No remote scripts, trackers or remote audio.
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Local resources only",
                    java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("about".equals(scheme)) return false; // Ending iframe teardown uses about:blank.
                if (!"https".equals(scheme) && !"mailto".equals(scheme)) return true;
                return super.shouldOverrideUrlLoading(view, request);
            }
        });
        bridge.getWebView().getSettings().setDomStorageEnabled(true);
        bridge.getWebView().getSettings().setMediaPlaybackRequiresUserGesture(true);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                bridge.getWebView().evaluateJavascript(
                    "Boolean(window.__dioramaAndroid?.closeOverlay())", result -> {
                        if (!"true".equals(result)) showAppMenu();
                    });
            }
        });
    }

    private void showAppMenu() {
        bridge.getWebView().evaluateJavascript("window.__dioramaAndroid?.pause()", null);
        new AlertDialog.Builder(this).setTitle("DIORAMA")
            .setItems(new String[]{"Continue playing", "Privacy policy", "Licenses", "Exit game"}, (dialog, which) -> {
                if (which == 1) showNotice("Privacy policy", "public/privacy-policy.txt");
                if (which == 2) showNotice("Licenses", "public/THIRD_PARTY_LICENSES.md",
                    "public/LICENSE", "public/licenses/ThreeJS-MIT.txt", "public/licenses/AmaticSC-OFL.txt", "public/licenses/Capacitor-MIT.txt",
                    "public/licenses/Apache-2.0.txt", "public/licenses/Native-Dependency-Notices.txt");
                if (which == 3) finish();
            })
            .setOnDismissListener(dialog -> bridge.getWebView().evaluateJavascript("window.__dioramaAndroid?.foreground()", null))
            .show();
    }

    private void showNotice(String title, String... files) {
        StringBuilder text = new StringBuilder();
        for (String file : files) {
            try (InputStream input = getAssets().open(file)) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                text.append(bytes.toString(StandardCharsets.UTF_8.name())).append("\n\n");
            } catch (java.io.IOException error) {
                text.append("Notice unavailable. Contact questrequestvr@gmail.com.\n");
            }
        }
        new AlertDialog.Builder(this).setTitle(title).setMessage(text.toString()).setPositiveButton("Close", null).show();
    }

    @Override
    public void onPause() {
        if (bridge != null) {
            bridge.getWebView().evaluateJavascript("window.__dioramaAndroid?.pause()", null);
            bridge.getWebView().onPause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bridge != null) {
            bridge.getWebView().onResume();
            bridge.getWebView().evaluateJavascript("window.__dioramaAndroid?.foreground()", null);
        }
    }
}
