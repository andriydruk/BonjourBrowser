package com.druk.servicebrowser;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.druk.servicebrowser.ui.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Test
    public void appPackageName() {
        assertEquals("com.druk.servicebrowser",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
    }

    @Test
    public void mainActivityLaunches() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
                assertNotNull(activity.findViewById(R.id.toolbar));
            });
        }
    }
}
