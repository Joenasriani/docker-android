package com.questrequestvr.diorama;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Genuine emulator captures using the existing game UI, without changing game source. */
@RunWith(AndroidJUnit4.class)
public class StoreCaptureTest {
    private String js(ActivityScenario<MainActivity> a, String source) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        a.onActivity(v -> v.getBridge().getWebView().evaluateJavascript(source, x -> { result.set(x); done.countDown(); }));
        assertTrue(done.await(15, TimeUnit.SECONDS));
        return result.get();
    }
    private void await(ActivityScenario<MainActivity> a, String predicate) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 90000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if ("true".equals(js(a, predicate))) return;
            SystemClock.sleep(250);
        }
        fail("Timed out: " + predicate);
    }
    private void capture(String name) throws Exception {
        SystemClock.sleep(1800);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "store-screens");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        image.recycle();
    }
    @Test public void captureActualAndroidLayouts() throws Exception {
        try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
            a.onActivity(v -> v.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            await(a, "Boolean(document.querySelector('#stage canvas') && document.body.classList.contains('diorama-assets-ready'))");
            capture("DIORAMA_01_Title_Landscape");
            js(a, "localStorage.setItem('diorama.save.v1.market.release',JSON.stringify({unlocked:24,last:0,best:{}})); document.getElementById('startPage').click(); true");
            await(a, "document.body.classList.contains('game-started')");
            capture("DIORAMA_02_Level_01_Landscape");
            for (int level : new int[]{4,10,18,25}) {
                js(a, "document.getElementById('levelSelectBtn').click();true");
                await(a, "Boolean(document.querySelector('[aria-label=\"Go to level " + level + "\"]'))");
                js(a, "document.querySelector('[aria-label=\"Go to level " + level + "\"]').click();true");
                capture(String.format("DIORAMA_%02d_Level_%02d_Landscape", level==4?3:level==10?4:level==18?5:6, level));
            }
            a.onActivity(v -> v.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            await(a, "innerHeight>innerWidth");
            capture("DIORAMA_07_Level_25_Portrait");
            assertEquals("\"https://localhost\"",js(a,"location.origin"));
            assertEquals("true",js(a,"Boolean(document.querySelector('#stage canvas'))"));
        }
    }
}
