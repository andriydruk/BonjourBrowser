package com.druk.servicebrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegTypeManagerTest {

    private lateinit var regTypeManager: RegTypeManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        regTypeManager = RegTypeManager(context)
    }

    @Test
    fun getRegTypeDescription_knownType_returnsDescription() {
        val description = regTypeManager.getRegTypeDescription("_http._tcp.")
        assertNotNull("Expected description for _http._tcp.", description)
        assertTrue(description!!.isNotEmpty())
    }

    @Test
    fun getRegTypeDescription_unknownType_returnsNull() {
        val description = regTypeManager.getRegTypeDescription("_nonexistent12345._tcp.")
        assertNull(description)
    }

    @Test
    fun getListRegTypes_returnsNonEmptyList() {
        // Force CSV load first
        regTypeManager.getRegTypeDescription("_http._tcp.")

        val types = regTypeManager.getListRegTypes()
        assertNotNull(types)
        assertFalse("Expected non-empty reg type list", types.isEmpty())
        assertTrue("Expected many reg types from IANA CSV", types.size > 100)
    }

    @Test
    fun getListRegTypes_containsCommonTypes() {
        // Force load
        regTypeManager.getRegTypeDescription("_http._tcp.")

        val types = regTypeManager.getListRegTypes()
        assertTrue("Expected _http._tcp. in list", types.contains("_http._tcp."))
        assertTrue("Expected _ftp._tcp. in list", types.contains("_ftp._tcp."))
    }
}
