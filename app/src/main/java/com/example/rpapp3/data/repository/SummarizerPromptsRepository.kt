package com.example.rpapp3.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for storing custom summarizer prompts in Firestore.
 * Prompts are stored in a global settings collection.
 * Empty prompt = use default.
 */
class SummarizerPromptsRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val promptsDocument = firestore.collection("settings").document("summarizer_prompts")
    
    companion object {
        // Keys for prompt fields
        private const val HIGH_DETAIL_SUMMARY_PROMPT_KEY = "high_detail_summary_prompt"
        private const val CHARACTER_BACKGROUND_PROMPT_KEY = "character_background_prompt"
        private const val CHARACTER_APPEARANCE_PROMPT_KEY = "character_appearance_prompt"
        private const val CHARACTER_PERSONALITY_PROMPT_KEY = "character_personality_prompt"
        private const val WORLD_DESCRIPTION_PROMPT_KEY = "world_description_prompt"
        
        // Default prompt for HIGH detail chronological summary
        const val DEFAULT_HIGH_DETAIL_SUMMARY_PROMPT = """Provide an EXTREMELY COMPREHENSIVE list of MEANINGFUL FACTS with NO LENGTH CONSTRAINTS.

This summary serves as LONG-TERM MEMORY for future chats - only include facts that have SIGNIFICANCE and should be remembered.

FORMAT REQUIREMENTS:
Organize the summary by chronological periods (Day 1, Day 2, etc. OR Event 1, Event 2, etc.)
Under each period, list EVERY MEANINGFUL FACT as a separate quoted item:

Example format:
Day 1:
- "Steponas was sleeping on the couch in the apartment"
- "The apartment was described as being on the third floor with brick walls"
- "Emily revealed she lost her job at TechCorp last week"
- "Steponas shared his fear of heights with Emily for the first time"

Day 2:
- "Emily went for a job interview at DataSystems Inc"
- "Emily was feeling depressed and anxious after the rejection"
- "Steponas gave Emily a long, comforting hug when she came home crying"
- "They decided to start looking for apartments together"

WHAT TO INCLUDE (Meaningful Facts):
✓ Physical states/locations: sleeping positions, living arrangements, injuries, illnesses
✓ Significant revelations: secrets shared, backstory revealed, confessions
✓ Emotionally impactful moments: hugs with emotional weight, comfort during distress, vulnerable moments
✓ Important decisions: life choices, commitments, promises made
✓ Relationship developments: trust built, conflicts resolved, intimacy milestones
✓ Character growth: overcoming fears, learning lessons, behavioral changes
✓ World-building facts: location details, environmental descriptions, setting information
✓ Meaningful objects/possessions: gifts given, sentimental items, important belongings
✓ Life events: job changes, moving, injuries, achievements, failures
✓ Psychological impacts: trauma, breakthroughs, realizations, emotional shifts
✓ Specific factual details: names, places, dates, descriptions that establish context

WHAT TO EXCLUDE (Trivial Interactions):
✗ Simple greetings: "Good morning", "Hello", "How are you"
✗ Routine small talk: weather comments, passing pleasantries
✗ Mundane actions with no significance: "walked to the kitchen", "sat down"
✗ Casual celebration gestures with no emotional depth: quick high-fives, routine goodbyes
✗ Filler dialogue and chitchat
✗ Generic descriptions without specific details

SIGNIFICANCE TEST - Ask yourself:
"Would this fact matter in a conversation one week from now?"
"Does this reveal something important about the character, world, or relationships?"
"Would forgetting this fact cause confusion or loss of context in future interactions?"
"Does this have psychological, emotional, or practical significance?"

If YES to any of these → INCLUDE IT
If NO to all → EXCLUDE IT

GUIDELINES:
- Each fact should be a COMPLETE, STANDALONE statement that provides context
- Use past tense for all facts
- Include character names in facts for clarity
- Be SELECTIVE but THOROUGH - capture ALL meaningful details, exclude trivial ones
- Focus on QUALITY over quantity - meaningful facts only
- Include emotional context when relevant (e.g., "Steponas hugged Emily" vs "Steponas gave Emily a comforting hug after she shared her trauma")
- Specific details make facts more meaningful: "Emily's childhood home was in Boston" is better than "Emily talked about her home"

This summary will serve as MEMORY for new chats - ensure facts are significant enough to warrant remembering.
Propose updates for character development or world changes based on meaningful events only."""

        // Default prompt for character background analysis
        const val DEFAULT_CHARACTER_BACKGROUND_PROMPT = """You are analyzing a roleplay chat to update a character's BACKGROUND based on story events.

TASK:
Review the chat history and determine if the character's background should be updated.
The background includes: backstory, life events, experiences, relationships formed, achievements, etc.

RULES:
1. PRESERVE ALL ORIGINAL CONTENT - Start with the existing background text
2. APPEND new developments from the story events
3. Only MODIFY if story events directly contradict existing background
4. Include significant events, relationship developments, major decisions
5. Use past tense for completed events

RESPONSE FORMAT:
Return ONLY a JSON object with this structure:
{
  "backgroundNew": "Updated background text, or null if no changes needed"
}

If no meaningful background updates occurred in the chat, return: {"backgroundNew": null}"""

        // Default prompt for character appearance analysis
        const val DEFAULT_CHARACTER_APPEARANCE_PROMPT = """You are analyzing a roleplay chat to update a character's APPEARANCE based on story events.

TASK:
Review the chat history and determine if the character's appearance should be updated.
Appearance includes: physical looks, clothing, hair, body features, scars, injuries, etc.

RULES:
1. PRESERVE ALL ORIGINAL CONTENT - Start with the existing appearance text
2. APPEND or UPDATE based on physical changes in the story
3. Only update if appearance was EXPLICITLY changed (new clothes, haircut, injury, etc.)
4. Do NOT add generic descriptions - only changes from the story
5. Be specific about what changed

RESPONSE FORMAT:
Return ONLY a JSON object with this structure:
{
  "appearanceNew": "Updated appearance text, or null if no changes needed"
}

If no appearance changes occurred in the chat, return: {"appearanceNew": null}"""

        // Default prompt for character personality analysis
        const val DEFAULT_CHARACTER_PERSONALITY_PROMPT = """You are analyzing a roleplay chat to update a character's PERSONALITY based on character development.

TASK:
Review the chat history and determine if the character's personality should be updated.
Personality includes: traits, behaviors, values, emotional patterns, growth, etc.

RULES:
1. PRESERVE ALL ORIGINAL CONTENT - Start with the existing personality text
2. APPEND new traits or growth observed in the story
3. Note significant emotional development, behavioral changes, or value shifts
4. Include how relationships have affected their personality
5. Only update if there was meaningful character development

RESPONSE FORMAT:
Return ONLY a JSON object with this structure:
{
  "personalityNew": "Updated personality text, or null if no changes needed"
}

If no personality development occurred in the chat, return: {"personalityNew": null}"""

        // Default prompt for world description analysis
        const val DEFAULT_WORLD_DESCRIPTION_PROMPT = """You are analyzing a roleplay chat to update the WORLD DESCRIPTION based on story events.

TASK:
Review the chat history and determine if the world description should be updated.
World description includes: setting details, locations, environment, world state, major events, etc.

CRITICAL FORMAT REQUIREMENT:
The updated description MUST follow this exact structure:
1. First: Copy the COMPLETE, UNMODIFIED original description text exactly as provided
2. Then: Add a separator line: "--- Story Events ---"
3. Finally: Paste the generated story summary directly after the separator

RULES:
1. ALWAYS include the COMPLETE original description first - never summarize or shorten it
2. Copy the story summary as-is after the separator, do not reformat it
3. Only include world-relevant events in the summary portion

RESPONSE FORMAT:
Return ONLY a JSON object with this structure:
{
  "descriptionNew": "The complete original description + separator + story summary, or null if no changes needed"
}

If no world/setting changes occurred in the chat, return: {"descriptionNew": null}"""
    }
    
    /**
     * Get all summarizer prompts as a Flow for reactive updates
     */
    fun getSummarizerPrompts(): Flow<SummarizerPrompts> = callbackFlow {
        val listener = promptsDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(SummarizerPrompts())
                return@addSnapshotListener
            }
            
            val prompts = SummarizerPrompts(
                highDetailSummaryPrompt = snapshot?.getString(HIGH_DETAIL_SUMMARY_PROMPT_KEY) ?: "",
                characterBackgroundPrompt = snapshot?.getString(CHARACTER_BACKGROUND_PROMPT_KEY) ?: "",
                characterAppearancePrompt = snapshot?.getString(CHARACTER_APPEARANCE_PROMPT_KEY) ?: "",
                characterPersonalityPrompt = snapshot?.getString(CHARACTER_PERSONALITY_PROMPT_KEY) ?: "",
                worldDescriptionPrompt = snapshot?.getString(WORLD_DESCRIPTION_PROMPT_KEY) ?: ""
            )
            trySend(prompts)
        }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get all summarizer prompts once (synchronous)
     */
    suspend fun getSummarizerPromptsOnce(): SummarizerPrompts {
        return try {
            val doc = promptsDocument.get().await()
            SummarizerPrompts(
                highDetailSummaryPrompt = doc.getString(HIGH_DETAIL_SUMMARY_PROMPT_KEY) ?: "",
                characterBackgroundPrompt = doc.getString(CHARACTER_BACKGROUND_PROMPT_KEY) ?: "",
                characterAppearancePrompt = doc.getString(CHARACTER_APPEARANCE_PROMPT_KEY) ?: "",
                characterPersonalityPrompt = doc.getString(CHARACTER_PERSONALITY_PROMPT_KEY) ?: "",
                worldDescriptionPrompt = doc.getString(WORLD_DESCRIPTION_PROMPT_KEY) ?: ""
            )
        } catch (e: Exception) {
            SummarizerPrompts()
        }
    }
    
    /**
     * Save a specific prompt
     */
    suspend fun setHighDetailSummaryPrompt(prompt: String): Result<Unit> {
        return setPrompt(HIGH_DETAIL_SUMMARY_PROMPT_KEY, prompt)
    }
    
    suspend fun setCharacterBackgroundPrompt(prompt: String): Result<Unit> {
        return setPrompt(CHARACTER_BACKGROUND_PROMPT_KEY, prompt)
    }
    
    suspend fun setCharacterAppearancePrompt(prompt: String): Result<Unit> {
        return setPrompt(CHARACTER_APPEARANCE_PROMPT_KEY, prompt)
    }
    
    suspend fun setCharacterPersonalityPrompt(prompt: String): Result<Unit> {
        return setPrompt(CHARACTER_PERSONALITY_PROMPT_KEY, prompt)
    }
    
    suspend fun setWorldDescriptionPrompt(prompt: String): Result<Unit> {
        return setPrompt(WORLD_DESCRIPTION_PROMPT_KEY, prompt)
    }
    
    private suspend fun setPrompt(key: String, prompt: String): Result<Unit> {
        return try {
            promptsDocument.set(mapOf(key to prompt), SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Reset a specific prompt to default (by setting it to empty string)
     */
    suspend fun resetHighDetailSummaryPrompt(): Result<Unit> = setHighDetailSummaryPrompt("")
    suspend fun resetCharacterBackgroundPrompt(): Result<Unit> = setCharacterBackgroundPrompt("")
    suspend fun resetCharacterAppearancePrompt(): Result<Unit> = setCharacterAppearancePrompt("")
    suspend fun resetCharacterPersonalityPrompt(): Result<Unit> = setCharacterPersonalityPrompt("")
    suspend fun resetWorldDescriptionPrompt(): Result<Unit> = setWorldDescriptionPrompt("")
    
    /**
     * Reset all prompts to default
     */
    suspend fun resetAllPrompts(): Result<Unit> {
        return try {
            promptsDocument.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Data class holding all summarizer prompts.
 * Empty string = use default prompt.
 */
data class SummarizerPrompts(
    val highDetailSummaryPrompt: String = "",
    val characterBackgroundPrompt: String = "",
    val characterAppearancePrompt: String = "",
    val characterPersonalityPrompt: String = "",
    val worldDescriptionPrompt: String = ""
) {
    /**
     * Get the effective HIGH detail summary prompt (custom or default)
     */
    fun getEffectiveHighDetailSummaryPrompt(): String =
        highDetailSummaryPrompt.ifBlank { SummarizerPromptsRepository.DEFAULT_HIGH_DETAIL_SUMMARY_PROMPT }
    
    /**
     * Get the effective character background prompt (custom or default)
     */
    fun getEffectiveCharacterBackgroundPrompt(): String =
        characterBackgroundPrompt.ifBlank { SummarizerPromptsRepository.DEFAULT_CHARACTER_BACKGROUND_PROMPT }
    
    /**
     * Get the effective character appearance prompt (custom or default)
     */
    fun getEffectiveCharacterAppearancePrompt(): String =
        characterAppearancePrompt.ifBlank { SummarizerPromptsRepository.DEFAULT_CHARACTER_APPEARANCE_PROMPT }
    
    /**
     * Get the effective character personality prompt (custom or default)
     */
    fun getEffectiveCharacterPersonalityPrompt(): String =
        characterPersonalityPrompt.ifBlank { SummarizerPromptsRepository.DEFAULT_CHARACTER_PERSONALITY_PROMPT }
    
    /**
     * Get the effective world description prompt (custom or default)
     */
    fun getEffectiveWorldDescriptionPrompt(): String =
        worldDescriptionPrompt.ifBlank { SummarizerPromptsRepository.DEFAULT_WORLD_DESCRIPTION_PROMPT }
}
