package com.example.rpapp3.data.model

/**
 * Represents proposed changes from the story summarizer.
 */
data class SummaryProposal(
    val storySummary: String,
    val characterUpdates: List<CharacterUpdateProposal>,
    val worldUpdateProposal: WorldUpdateProposal?
)

/**
 * Proposed updates for a single character based on story events.
 */
data class CharacterUpdateProposal(
    val characterId: String,
    val characterName: String,
    val backgroundOld: String,
    val backgroundNew: String?,
    val appearanceOld: String,
    val appearanceNew: String?,
    val personalityOld: String,
    val personalityNew: String?
) {
    fun hasChanges(): Boolean = 
        backgroundNew != null || appearanceNew != null || personalityNew != null
}

/**
 * Proposed updates for the world description based on story events.
 */
data class WorldUpdateProposal(
    val worldId: String,
    val worldName: String,
    val descriptionOld: String,
    val descriptionNew: String?
) {
    fun hasChanges(): Boolean = descriptionNew != null
}
