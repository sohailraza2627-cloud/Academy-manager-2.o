package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.AcademyRoot
import com.example.ui.theme.AcademyManagerTheme
import com.example.ui.viewmodel.AcademicsViewModel
import com.example.ui.viewmodel.AppLockViewModel
import com.example.ui.viewmodel.FinanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appLockViewModel: AppLockViewModel = viewModel()
            val academicsViewModel: AcademicsViewModel = viewModel()
            val financeViewModel: FinanceViewModel = viewModel()

            val isDarkMode by appLockViewModel.isDarkMode.collectAsStateWithLifecycle()

            AcademyManagerTheme(darkTheme = isDarkMode) {
                AcademyRoot(
                    appLockViewModel = appLockViewModel,
                    academicsViewModel = academicsViewModel,
                    financeViewModel = financeViewModel
                )
            }
        }
    }
}
