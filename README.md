# NightWave Player

Nachbau des OCoMe-Music-Player-Looks (Cover mit Glow-Rand, Live-Balken-Visualizer,
gelber Titel, dünner Fortschrittsbalken, Play/Pause/Skip) als native Android-App.

- Kotlin + Jetpack Compose
- Media3/ExoPlayer für die Wiedergabe
- **Echter** Live-Visualizer über `android.media.audiofx.Visualizer`, gekoppelt an
  die `audioSessionId` von ExoPlayer (keine Fake-Balken, reagiert wirklich auf den Song)
- Cover wird per `MediaMetadataRetriever` direkt aus der Audiodatei (ID3/embedded picture) gezogen
- Fixer Debug-Keystore im Repo → jeder CI-Build hat dieselbe Signatur (App lässt sich
  einfach über eine neue APK ersetzen, ohne "Signaturen stimmen nicht überein"-Fehler)

## Workflow (Termux → GitHub → APK)

1. Ordner in Termux entpacken (falls als ZIP), dann ins Verzeichnis wechseln:
   ```
   cd ~/storage/shared
   unzip NightWavePlayer.zip
   cd NightWavePlayer
   ```
2. Git-Repo initialisieren und pushen:
   ```
   git init
   git add .
   git commit -m "NightWave Player init"
   git branch -M main
   git remote add origin https://github.com/<DEIN-USER>/NightWavePlayer.git
   git push -u origin main
   ```
3. GitHub Actions baut automatisch bei jedem Push (`.github/workflows/build.yml`).
   Unter dem Tab **Actions** im Repo → letzter Lauf → **Artifacts** →
   `NightWavePlayer-debug` herunterladen → enthält die `app-debug.apk`.
4. APK aufs Handy, installieren (unbekannte Quellen erlauben), fertig.

## Bedienung in der App

- Oben rechts "🎵 Song wählen" → Audiodatei vom Gerät auswählen
- Cover, Titel und Live-Visualizer werden automatisch geladen
- Play/Pause, ±10s Skip, Fortschrittsbalken

## Hinweis

Die Like/Kommentar/Lesezeichen/Teilen-Icons rechts sind rein optisch (wie im
Screenshot), ohne Funktion — reine UI-Deko für den TikTok-Look.
