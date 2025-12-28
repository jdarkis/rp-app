package com.example.rpapp3.ui.navigation

sealed class Routes(val route: String) {
    // World routes
    data object WorldList : Routes("worlds")
    data object CreateWorld : Routes("worlds/create")
    data object WorldDetail : Routes("worlds/{worldId}") {
        fun createRoute(worldId: String) = "worlds/$worldId"
    }
    data object EditWorld : Routes("worlds/{worldId}/edit") {
        fun createRoute(worldId: String) = "worlds/$worldId/edit"
    }
    
    // Character routes
    data object CreateCharacter : Routes("worlds/{worldId}/characters/create") {
        fun createRoute(worldId: String) = "worlds/$worldId/characters/create"
    }
    data object CharacterDetail : Routes("worlds/{worldId}/characters/{characterId}") {
        fun createRoute(worldId: String, characterId: String) = "worlds/$worldId/characters/$characterId"
    }
    data object EditCharacter : Routes("worlds/{worldId}/characters/{characterId}/edit") {
        fun createRoute(worldId: String, characterId: String) = "worlds/$worldId/characters/$characterId/edit"
    }
    
    // Chat routes
    data object ChatList : Routes("worlds/{worldId}/chats") {
        fun createRoute(worldId: String) = "worlds/$worldId/chats"
    }
    data object NewChat : Routes("worlds/{worldId}/chats/new") {
        fun createRoute(worldId: String) = "worlds/$worldId/chats/new"
    }
    data object Chat : Routes("worlds/{worldId}/chats/{chatId}") {
        fun createRoute(worldId: String, chatId: String) = "worlds/$worldId/chats/$chatId"
    }
    
    // Settings
    data object Settings : Routes("settings")
    data object SettingsApiKeys : Routes("settings/api-keys")
    data object SettingsAppearance : Routes("settings/appearance")
}
