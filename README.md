# RP App 3

A roleplaying Android application built with Kotlin, Jetpack Compose, and integrated with Google's Gemini AI.

## Features

- Create and manage custom characters (Personas)
- Chat with AI-powered characters using Gemini
- Character media storage with Supabase
- Multiple API key rotation for managing quotas
- Dark mode UI

## Setup Instructions

Before building the app, you need to configure the following credentials:

### 1. Gemini API Keys

1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Create one or more API keys
3. Copy `app/src/main/java/com/example/rpapp3/data/ApiKeyManager.kt.template` to `app/src/main/java/com/example/rpapp3/data/ApiKeyManager.kt`
4. Replace the placeholder API keys with your actual keys

### 2. Supabase Configuration

1. Go to [Supabase](https://supabase.com) and create a free account
2. Create a new project
3. Go to Project Settings > API
4. Copy your Project URL and anon/public key
5. Copy `app/src/main/java/com/example/rpapp3/data/SupabaseConfig.kt.template` to `app/src/main/java/com/example/rpapp3/data/SupabaseConfig.kt`
6. Replace the placeholder values with your actual credentials
7. Go to Storage and create a bucket called "character-media"
8. Set the bucket to public or configure RLS policies

### 3. Firebase Configuration (Optional)

If you want to use Firebase services:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or use existing one
3. Add an Android app with package name `com.example.rpapp3`
4. Download the `google-services.json` file
5. Place it in the `app/` directory (replacing the template)

## Building the App

1. Open the project in Android Studio
2. Complete the setup instructions above
3. Sync Gradle files
4. Build and run on your device or emulator

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM with Clean Architecture
- **DI**: Hilt
- **Database**: Room
- **AI**: Google Gemini SDK
- **Storage**: Supabase Storage
- **Async**: Coroutines + Flow

## License

This project is for personal use.
