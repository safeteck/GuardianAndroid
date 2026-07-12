package com.sentinel.guardian.ui.screens.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.rememberNavController

@Composable
fun MainScreen(
    activity: ComponentActivity,
    context: Context
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController = navController) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(
                    bottom = innerPadding.calculateBottomPadding(),
                    start = innerPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = innerPadding.calculateRightPadding(LayoutDirection.Ltr)
                )
        ) {
            AppNavHost(
                activity,
                context,
                navController = navController
            )
        }
    }
}