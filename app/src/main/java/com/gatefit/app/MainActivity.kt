package com.gatefit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gatefit.ui.navigation.GatefitApp
import com.gatefit.ui.theme.GatefitTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as GatefitApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GatefitTheme {
                GatefitApp(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.drivingRestrictionRepository.start()
    }

    override fun onStop() {
        container.drivingRestrictionRepository.stop()
        super.onStop()
    }

    // 화면 회전 등 재생성이 아닌 실제 종료일 때만 SDK 연결 해제
    override fun onDestroy() {
        if (isFinishing) {
            container.navigationRepository.release()
            container.vehicleInfoRepository.disconnect()
        }
        super.onDestroy()
    }
}
