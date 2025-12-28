# Firebase Setup Instructions

Follow these steps to set up Firebase for your Roleplay App.

## Step 1: Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Create a project"** (or "Add project")
3. Enter a project name (e.g., "RPApp3" or "RoleplayApp")
4. Choose whether to enable Google Analytics (optional)
5. Click **"Create project"** and wait for it to finish

## Step 2: Register Your Android App

1. In your Firebase project, click the **Android icon** to add an Android app
2. Enter the package name: `com.example.rpapp3`
3. Enter a nickname (optional): "Roleplay App"
4. You can skip the Debug signing certificate for now
5. Click **"Register app"**

## Step 3: Download google-services.json

1. Click **"Download google-services.json"**
2. Save the file to your computer
3. Copy this file to your Android project's `app/` directory:
   ```
   c:\Users\deals\AndroidStudioProjects\RPapp3\app\google-services.json
   ```
4. Click **"Next"** and then **"Continue to console"**

## Step 4: Enable Firestore Database

1. In the Firebase Console, go to **Build > Firestore Database**
2. Click **"Create database"**
3. Select **"Start in test mode"** (for development)
   
   > ⚠️ **Important**: Test mode allows anyone to read/write. For a personal app this is fine, but don't share the app publicly without adding security rules.

4. Choose a Cloud Firestore location (select one closest to you)
5. Click **"Enable"**

## Step 5: Enable Firebase Storage

1. In the Firebase Console, go to **Build > Storage**
2. Click **"Get started"**
3. Select **"Start in test mode"** (for development)
4. Choose a Cloud Storage location (use the same region as Firestore)
5. Click **"Done"**

## Step 6: Sync Your Project

1. Open the project in Android Studio
2. Click **"Sync Project with Gradle Files"** (elephant icon) or go to **File > Sync Project with Gradle Files**
3. Wait for the sync to complete

## Step 7: Run Your App

1. Connect your Android device or start an emulator
2. Click **Run** (green play button)
3. The app should launch showing the "My Worlds" screen

## Troubleshooting

### "google-services.json not found" error
- Make sure `google-services.json` is in the `app/` folder, not the root project folder

### Gradle sync fails
- Try **File > Invalidate Caches / Restart**
- Make sure you have internet connectivity

### Firestore permission denied
- Check that your Firestore is in "test mode"
- Go to Firestore > Rules and ensure the rules allow read/write:
  ```
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      match /{document=**} {
        allow read, write: if true;
      }
    }
  }
  ```

### Storage permission denied
- Check that your Storage is in "test mode"  
- Go to Storage > Rules and ensure the rules allow read/write:
  ```
  rules_version = '2';
  service firebase.storage {
    match /b/{bucket}/o {
      match /{allPaths=**} {
        allow read, write: if true;
      }
    }
  }
  ```

## Data Structure

Your Firestore database will have these collections:

```
worlds/
  {worldId}/
    - id
    - name
    - description
    - writingStyle
    - systemInstructions
    - createdAt
    - updatedAt

characters/
  {characterId}/
    - id
    - worldId
    - name
    - description
    - appearance
    - personality
    - systemInstructions
    - photoUrls[]
    - videoUrls[]
    - createdAt
    - updatedAt

chats/
  {chatId}/
    - id
    - worldId
    - characterIds[]
    - title
    - createdAt
    - updatedAt
    messages/
      {messageId}/
        - id
        - chatId
        - text
        - isUser
        - characterId
        - characterName
        - timestamp
```

## Storage Structure

Your Firebase Storage will have this structure:

```
characters/
  {characterId}/
    photos/
      {uuid}.jpg
    videos/
      {uuid}.mp4
```
