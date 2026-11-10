package com.example.pleoschallenge

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val carSpeedPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(CAR_SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            carSpeedPermissionLauncher.launch(CAR_SPEED_PERMISSION)
        }
        setContent {
            SafeLoopTheme {
                SafeLoopApp()
            }
        }
    }

    private companion object {
        const val CAR_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
