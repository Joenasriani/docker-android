package com.questrequestvr.diorama;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual Android WebView pixels. Disposable QA saves select authored layouts. */
@RunWith(AndroidJUnit4.class)
public class StoreCaptureTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private WebView web;
    private String js(String source) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> web.evaluateJavascript(source, x -> { value.set(x); done.countDown(); }));
        assertTrue("JS callback timeout", done.await(20, TimeUnit.SECONDS));
        return value.get();
    }
    private void await(String predicate) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 90000;
        while (SystemClock.uptimeMillis() < deadline) {
            if ("true".equals(js(predicate))) return;
            SystemClock.sleep(250);
        }
        fail("Timed out: " + predicate);
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
             java.io.FileInputStream input = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            byte[] bytes = new byte[4096]; while (input.read(bytes) != -1) { /* drain */ }
        }
    }
    private void currentActivity() {
        instrumentation.runOnMainSync(() -> {
            for (android.app.Activity resumed : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (resumed instanceof MainActivity) { activity = (MainActivity) resumed; web = activity.getBridge().getWebView(); }
            }
        });
    }
    private Rect bounds() {
        Rect rect = new Rect();
        instrumentation.runOnMainSync(() -> { int[] xy = new int[2]; web.getLocationInWindow(xy); rect.set(xy[0], xy[1], xy[0]+web.getWidth(), xy[1]+web.getHeight()); });
        return rect;
    }
    private void viewport(int width, int height, int density) throws Exception {
        shell("wm density " + density); SystemClock.sleep(1500); currentActivity();
        int displayWidth = width, displayHeight = height;
        for (int attempt=0; attempt<5; attempt++) {
            shell("wm size " + displayWidth + "x" + displayHeight);
            SystemClock.sleep(1800); currentActivity();
            Rect actual = bounds();
            if (actual.width()==width && actual.height()==height) return;
            displayWidth += width-actual.width();
            displayHeight += height-actual.height();
        }
        assertEquals(width,bounds().width()); assertEquals(height,bounds().height());
    }
    private void capture(File dir, String name, JSONArray records, int level) throws Exception {
        SystemClock.sleep(1500);
        Rect rect = bounds(); Bitmap bitmap = Bitmap.createBitmap(rect.width(),rect.height(),Bitmap.Config.ARGB_8888);
        CountDownLatch done = new CountDownLatch(1); int[] result = {-1};
        instrumentation.runOnMainSync(() -> PixelCopy.request(activity.getWindow(),rect,bitmap,x -> { result[0]=x; done.countDown(); },new Handler(Looper.getMainLooper())));
        assertTrue("PixelCopy timeout",done.await(20,TimeUnit.SECONDS)); assertEquals(PixelCopy.SUCCESS,result[0]);
        bitmap.setHasAlpha(false);
        try (FileOutputStream out = new FileOutputStream(new File(dir,name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        records.put(new JSONObject().put("file",name+".png").put("level",level).put("width",bitmap.getWidth()).put("height",bitmap.getHeight())); bitmap.recycle();
        System.out.println("CAPTURED " + name);
    }
    private void level(int number) throws Exception {
        js("document.getElementById('levelSelectBtn').click();true");
        await("Boolean(document.querySelector('[aria-label=\"Go to level " + number + "\"]'))");
        js("document.querySelector('[aria-label=\"Go to level " + number + "\"]').click();true");
        SystemClock.sleep(1000);
        assertEquals("true",js("Boolean(document.querySelector('#stage canvas'))"));
    }
    @Test public void captureActualAndroidLayouts() throws Exception {
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> web = activity.getBridge().getWebView());
        assertNotNull(web);
        File output = new File(instrumentation.getTargetContext().getExternalFilesDir(null),"store-screens");
        assertTrue(output.isDirectory() || output.mkdirs());
        JSONArray records = new JSONArray();
        try {
            viewport(1920,1080,320);
            await("Boolean(document.querySelector('#stage canvas') && document.body.classList.contains('diorama-assets-ready'))");
            capture(output,"DIORAMA_Phone_01_Title_1920x1080",records,0);
            js("localStorage.setItem('diorama.save.v1.market.release',JSON.stringify({unlocked:24,last:0,best:{}}));document.getElementById('startPage').click();true");
            await("document.body.classList.contains('game-started')");
            for (int number : new int[]{1,4,10,18,25}) { level(number); capture(output,String.format("DIORAMA_Phone_Level_%02d_1920x1080",number),records,number); }
            viewport(1080,1920,420);
            capture(output,"DIORAMA_Phone_Level_25_1080x1920",records,25);
            for (int density : new int[]{320,240}) {
                viewport(2560,1440,density);
                String profile = density==320 ? "Tablet_7_Class" : "Tablet_10_Class";
                for (int number : new int[]{1,4,10,18}) { level(number); capture(output,String.format("DIORAMA_%s_Level_%02d_2560x1440",profile,number),records,number); }
            }
            JSONObject manifest = new JSONObject().put("method","Android PixelCopy of WebView window bounds; no pixel editing").put("api",android.os.Build.VERSION.SDK_INT).put("fingerprint",android.os.Build.FINGERPRINT).put("note","Emulator logical phone and tablet classes; not physical device measurements or all-level playthrough. Existing level board with disposable unlocked QA save.").put("screenshots",records);
            try (FileOutputStream out = new FileOutputStream(new File(output,"capture-provenance.json"))) { out.write(manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        } finally {
            shell("wm size reset"); shell("wm density reset");
            instrumentation.runOnMainSync(() -> activity.finish());
        }
    }
}
