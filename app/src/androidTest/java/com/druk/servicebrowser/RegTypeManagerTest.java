package com.druk.servicebrowser;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class RegTypeManagerTest {

    private RegTypeManager regTypeManager;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        regTypeManager = new RegTypeManager(context);
    }

    @Test
    public void getRegTypeDescription_knownType_returnsDescription() {
        // _http._tcp. is a well-known IANA service type
        String description = regTypeManager.getRegTypeDescription("_http._tcp.");
        assertNotNull("Expected description for _http._tcp.", description);
        assertTrue(description.length() > 0);
    }

    @Test
    public void getRegTypeDescription_unknownType_returnsNull() {
        String description = regTypeManager.getRegTypeDescription("_nonexistent12345._tcp.");
        assertNull(description);
    }

    @Test
    public void getListRegTypes_returnsNonEmptyList() {
        // Force CSV load first
        regTypeManager.getRegTypeDescription("_http._tcp.");

        List<String> types = regTypeManager.getListRegTypes();
        assertNotNull(types);
        assertFalse("Expected non-empty reg type list", types.isEmpty());
        assertTrue("Expected many reg types from IANA CSV", types.size() > 100);
    }

    @Test
    public void getListRegTypes_containsCommonTypes() {
        // Force load
        regTypeManager.getRegTypeDescription("_http._tcp.");

        List<String> types = regTypeManager.getListRegTypes();
        assertTrue("Expected _http._tcp. in list", types.contains("_http._tcp."));
        assertTrue("Expected _ftp._tcp. in list", types.contains("_ftp._tcp."));
    }
}
