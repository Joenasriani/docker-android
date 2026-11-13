package com.questrequestvr.diorama;

import static org.junit.Assert.*;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real WebView tests. These have not been run in the preparation environment. */
@RunWith(AndroidJUnit4.class)
public class LocalGameTest {
    private String evaluate(ActivityScenario<MainActivity> activity, String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        activity.onActivity(a -> a.getBridge().getWebView().evaluateJavascript(script, result -> {
            value.set(result); latch.countDown();
        }));
        assertTrue("WebView callback timed out", latch.await(10, TimeUnit.SECONDS));
        return value.get();
    }

    private void waitForGame(ActivityScenario<MainActivity> activity) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 45000;
        while (SystemClock.elapsedRealtime() < deadline) {
            String failed = evaluate(activity, "document.body?.innerText.includes('failed to start') || false");
            assertNotEquals("WebGL startup failed", "true", failed);
            if ("true".equals(evaluate(activity, "Boolean(document.querySelector('#stage canvas') && window.__dioramaAndroid)"))) return;
            SystemClock.sleep(250);
        }
        fail("Game did not create its renderer within 45 seconds");
    }

    @Test
    public void localLaunchAndStorageSurviveActivityRecreation() throws Exception {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            waitForGame(activity);
            assertEquals("\"https://localhost\"", evaluate(activity, "location.origin"));
            evaluate(activity, "localStorage.setItem('__android_storage_test', 'preserved')");
            activity.recreate();
            waitForGame(activity);
            assertEquals("\"preserved\"", evaluate(activity, "localStorage.getItem('__android_storage_test')"));
            evaluate(activity, "localStorage.removeItem('__android_storage_test')");
        }
    }

    @Test
    public void packagedEndingRetainsReplayAndRemovesBackingLink() throws Exception {
        android.content.Context context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (java.io.InputStream input = context.getAssets().open("public/ending/index.html")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            String html = bytes.toString("UTF-8");
            assertTrue(html.contains("const ENDING_DURATION = 60;"));
            assertTrue(html.contains("Replay Diorama"));
            assertFalse(html.contains("<a id=\"invest\""));
        }
        try {
            context.getAssets().open("public/invest/index.html").close();
            fail("Backing page must not be bundled");
        } catch (java.io.FileNotFoundException expected) { /* Excluded by Android packaging. */ }
    }
}
