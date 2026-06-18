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
    data object AICharacterWizard : Routes("worlds/{worldId}/characters/ai-wizard") {
        fun createRoute(worldId: String) = "worlds/$worldId/characters/ai-wizard"
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
    data object ChatSettings : Routes("worlds/{worldId}/chats/{chatId}/settings") {
        fun createRoute(worldId: String, chatId: String) = "worlds/$worldId/chats/$chatId/settings"
    }
    
    // Settings
    data object Settings : Routes("settings")
    data object SettingsApiKeys : Routes("settings/api-keys")
    data object SettingsBedrockApiKey : Routes("settings/bedrock-api-key")
    data object SettingsElevenLabsApiKeys : Routes("settings/elevenlabs-api-keys")
    data object SettingsInworldApiKeys : Routes("settings/inworld-api-keys")
    data object SettingsAppearance : Routes("settings/appearance")
    data object SettingsSystemPrompt : Routes("settings/system-prompt")
    data object SettingsUnlockPrompt : Routes("settings/unlock-prompt")
    data object SettingsTtsVoices : Routes("settings/tts-voices")
    data object SettingsElevenLabsVoices : Routes("settings/elevenlabs-voices")
    data object SettingsInworldVoices : Routes("settings/inworld-voices")
    
    // Private Chat routes
    data object PrivateChat : Routes("worlds/{worldId}/characters/{characterId}/private-chat") {
        fun createRoute(worldId: String, characterId: String) = "worlds/$worldId/characters/$characterId/private-chat"
    }
    data object PrivateChatSettings : Routes("worlds/{worldId}/characters/{characterId}/private-chat/settings") {
        fun createRoute(worldId: String, characterId: String) = "worlds/$worldId/characters/$characterId/private-chat/settings"
    }
}
