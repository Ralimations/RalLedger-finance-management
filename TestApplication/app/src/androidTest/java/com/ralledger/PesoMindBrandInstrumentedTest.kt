package com.ralledger

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PesoMindBrandInstrumentedTest {
    @Test
    fun launcherBrandChangesWithoutChangingTheInstallIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.ralledger", context.packageName)
        assertEquals("PesoMind", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        assertNotNull(context.packageManager.getApplicationIcon(context.applicationInfo))
    }

    @Test
    fun mergedAppManifestDoesNotGrantNetworkAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertFalse("The finance app must not request internet access", "android.permission.INTERNET" in permissions)
        assertFalse("The finance app must not inspect network state", "android.permission.ACCESS_NETWORK_STATE" in permissions)
    }
}
