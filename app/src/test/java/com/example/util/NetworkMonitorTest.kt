package com.example.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = _isOnline.asStateFlow()

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}

class NetworkMonitorTest {

    @Test
    fun testFakeNetworkMonitorInitialState() = runTest {
        val monitor = FakeNetworkMonitor(initialOnline = true)
        assertTrue(monitor.isOnline.first())

        monitor.setOnline(false)
        assertFalse(monitor.isOnline.first())
    }
}
