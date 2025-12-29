package com.example.rpapp3.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.rpapp3.ui.character.CharacterDetailScreen
import com.example.rpapp3.ui.character.CreateCharacterScreen
import com.example.rpapp3.ui.character.EditCharacterScreen
import com.example.rpapp3.ui.character.AICharacterWizardScreen
import com.example.rpapp3.ui.chat.ChatListScreen
import com.example.rpapp3.ui.chat.ChatScreen
import com.example.rpapp3.ui.chat.NewChatScreen
import com.example.rpapp3.ui.settings.ApiKeysScreen
import com.example.rpapp3.ui.settings.AppearanceScreen
import com.example.rpapp3.ui.settings.SettingsScreen
import com.example.rpapp3.ui.world.CreateWorldScreen
import com.example.rpapp3.ui.world.EditWorldScreen
import com.example.rpapp3.ui.world.WorldDetailScreen
import com.example.rpapp3.ui.world.WorldListScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.WorldList.route
    ) {
        // World routes
        composable(Routes.WorldList.route) {
            WorldListScreen(
                onWorldClick = { worldId ->
                    navController.navigate(Routes.WorldDetail.createRoute(worldId))
                },
                onCreateClick = {
                    navController.navigate(Routes.CreateWorld.route)
                },
                onSettingsClick = {
                    navController.navigate(Routes.Settings.route)
                }
            )
        }
        
        composable(Routes.CreateWorld.route) {
            CreateWorldScreen(
                onNavigateBack = { navController.popBackStack() },
                onWorldCreated = { worldId ->
                    navController.popBackStack()
                    navController.navigate(Routes.WorldDetail.createRoute(worldId))
                }
            )
        }
        
        composable(
            route = Routes.WorldDetail.route,
            arguments = listOf(navArgument("worldId") { type = NavType.StringType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            WorldDetailScreen(
                worldId = worldId,
                onNavigateBack = { navController.popBackStack() },
                onEditWorld = {
                    navController.navigate(Routes.EditWorld.createRoute(worldId))
                },
                onCreateCharacter = {
                    navController.navigate(Routes.CreateCharacter.createRoute(worldId))
                },
                onCharacterClick = { characterId ->
                    navController.navigate(Routes.CharacterDetail.createRoute(worldId, characterId))
                },
                onViewChats = {
                    navController.navigate(Routes.ChatList.createRoute(worldId))
                },
                onStartNewChat = {
                    navController.navigate(Routes.NewChat.createRoute(worldId))
                }
            )
        }
        
        composable(
            route = Routes.EditWorld.route,
            arguments = listOf(navArgument("worldId") { type = NavType.StringType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            EditWorldScreen(
                worldId = worldId,
                onNavigateBack = { navController.popBackStack() },
                onWorldUpdated = { navController.popBackStack() },
                onWorldDeleted = {
                    navController.popBackStack(Routes.WorldList.route, inclusive = false)
                }
            )
        }
        
        // Character routes
        composable(
            route = Routes.CreateCharacter.route,
            arguments = listOf(navArgument("worldId") { type = NavType.StringType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            CreateCharacterScreen(
                worldId = worldId,
                onNavigateBack = { navController.popBackStack() },
                onCharacterCreated = { navController.popBackStack() },
                onAIGenerateClick = {
                    navController.navigate(Routes.AICharacterWizard.createRoute(worldId))
                }
            )
        }
        
        composable(
            route = Routes.AICharacterWizard.route,
            arguments = listOf(navArgument("worldId") { type = NavType.StringType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            AICharacterWizardScreen(
                worldId = worldId,
                onNavigateBack = { navController.popBackStack() },
                onCharacterCreated = {
                    // Pop back to world detail after character creation
                    navController.popBackStack(Routes.WorldDetail.createRoute(worldId), inclusive = false)
                }
            )
        }
        
        composable(
            route = Routes.CharacterDetail.route,
            arguments = listOf(
                navArgument("worldId") { type = NavType.StringType },
                navArgument("characterId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            val characterId = backStackEntry.arguments?.getString("characterId") ?: return@composable
            CharacterDetailScreen(
                characterId = characterId,
                onNavigateBack = { navController.popBackStack() },
                onEditCharacter = {
                    navController.navigate(Routes.EditCharacter.createRoute(worldId, characterId))
                }
            )
        }
        
        composable(
            route = Routes.EditCharacter.route,
            arguments = listOf(
                navArgument("worldId") { type = NavType.StringType },
                navArgument("characterId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString("characterId") ?: return@composable
            EditCharacterScreen(
                characterId = characterId,
                onNavigateBack = { navController.popBackStack() },
                onCharacterUpdated = { navController.popBackStack() },
                onCharacterDeleted = {
                    // Pop back to world detail
                    navController.popBackStack()
                    navController.popBackStack()
                }
            )
        }
        
        // Chat routes
        composable(
            route = Routes.ChatList.route,
            arguments = listOf(navArgument("worldId") { type = NavType.StringType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            ChatListScreen(
                worldId = worldId,
                onNavigateBack = { navController.popBackStack() },
                onChatClick = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(worldId, chatId))
                },
                onNewChat = {
                    navController.navigate(Routes.NewChat.createRoute(worldId))
                }
            )
        }
        
        composable(
            route = Routes.NewChat.route,
            arguments = listOf(navArgument("worldId") { type = NavType.StringType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            NewChatScreen(
                worldId = worldId,
                onNavigateBack = { navController.popBackStack() },
                onChatCreated = { chatId ->
                    navController.popBackStack()
                    navController.navigate(Routes.Chat.createRoute(worldId, chatId))
                }
            )
        }
        
        composable(
            route = Routes.Chat.route,
            arguments = listOf(
                navArgument("worldId") { type = NavType.StringType },
                navArgument("chatId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId") ?: return@composable
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            ChatScreen(
                chatId = chatId,
                worldId = worldId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Settings routes
        composable(Routes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToApiKeys = { navController.navigate(Routes.SettingsApiKeys.route) },
                onNavigateToAppearance = { navController.navigate(Routes.SettingsAppearance.route) }
            )
        }
        
        composable(Routes.SettingsApiKeys.route) {
            ApiKeysScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.SettingsAppearance.route) {
            AppearanceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
