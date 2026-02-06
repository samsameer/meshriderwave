/*
 * Mesh Rider Wave - Navigation Graph
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.doodlelabs.meshriderwave.presentation.ui.screens.channels.ChannelsScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.contacts.ContactDetailScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.contacts.ContactsScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.dashboard.DashboardScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.dashboard.TacticalDashboardScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.groups.GroupDetailScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.groups.GroupsScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.home.HomeScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.map.MapScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.qr.QRScanScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.qr.QRShowScreen
import com.doodlelabs.meshriderwave.presentation.ui.screens.settings.SettingsScreen

/**
 * Navigation routes
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Dashboard : Screen("dashboard")
    data object Contacts : Screen("contacts")
    data object Groups : Screen("groups")
    data object Channels : Screen("channels")
    data object Map : Screen("map")
    data object Settings : Screen("settings")
    data object QRShow : Screen("qr_show")
    data object QRScan : Screen("qr_scan")
    data object ContactDetail : Screen("contact/{publicKey}") {
        fun createRoute(publicKey: String) = "contact/$publicKey"
    }
    data object GroupDetail : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }
}

@Composable
fun MeshRiderNavGraph(
    navController: NavHostController,
    onStartCall: (String) -> Unit,
    onStartVideoCall: (String) -> Unit = onStartCall,
    // Direct peer calling (Jan 2026) - for discovered peers without saved contact
    onStartCallToPeer: (publicKey: ByteArray, ipAddress: String, name: String) -> Unit = { _, _, _ -> },
    onStartVideoCallToPeer: (publicKey: ByteArray, ipAddress: String, name: String) -> Unit = { _, _, _ -> },
    // SOS activation (Feb 2026) - for Home screen SOS button
    onActivateSOS: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToGroups = { navController.navigate(Screen.Groups.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToChannels = { navController.navigate(Screen.Channels.route) },
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onNavigateToContacts = { navController.navigate(Screen.Contacts.route) },
                onNavigateToQRScan = { navController.navigate(Screen.QRScan.route) },
                onStartCall = onStartCall,
                onActivateSOS = onActivateSOS
            )
        }

        composable(Screen.Dashboard.route) {
            // Military-Grade Tactical Dashboard (Starlink-inspired) - Jan 2026
            TacticalDashboardScreen(
                onNavigateToGroups = { navController.navigate(Screen.Groups.route) },
                onNavigateToChannels = { navController.navigate(Screen.Channels.route) },
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToContacts = { navController.navigate(Screen.Contacts.route) },
                onStartCall = onStartCall,
                // Direct peer calling (Jan 2026) - one-tap calling from NEARBY PEERS
                onStartCallToPeer = onStartCallToPeer,
                onStartVideoCallToPeer = onStartVideoCallToPeer
            )
        }

        composable(Screen.Contacts.route) {
            ContactsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToQRShow = { navController.navigate(Screen.QRShow.route) },
                onNavigateToQRScan = { navController.navigate(Screen.QRScan.route) },
                onStartCall = onStartCall,
                onStartVideoCall = onStartVideoCall,
                onContactClick = { publicKeyHex ->
                    navController.navigate(Screen.ContactDetail.createRoute(publicKeyHex))
                }
            )
        }

        composable(Screen.Groups.route) {
            GroupsScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupClick = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                },
                onStartGroupCall = { groupId ->
                    onStartCall(groupId)
                },
                onNavigateToQRScan = { navController.navigate(Screen.QRScan.route) }
            )
        }

        composable(Screen.Channels.route) {
            ChannelsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Map.route) {
            MapScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.QRShow.route) {
            QRShowScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.QRScan.route) {
            QRScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onContactScanned = {
                    navController.popBackStack()
                    // Contact will be added via ViewModel
                }
            )
        }

        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(navArgument("publicKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val publicKey = backStackEntry.arguments?.getString("publicKey")
            if (publicKey.isNullOrEmpty()) {
                // Navigate back if no valid publicKey - prevent blank screen
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            ContactDetailScreen(
                publicKeyHex = publicKey,
                onNavigateBack = { navController.popBackStack() },
                onStartCall = onStartCall,
                onStartVideoCall = onStartVideoCall
            )
        }

        composable(
            route = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            if (groupId.isNullOrEmpty()) {
                // Navigate back if no valid groupId - prevent blank screen
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            GroupDetailScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() },
                onStartGroupCall = { onStartCall(it) }
            )
        }
    }
}
