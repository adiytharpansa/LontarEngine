# LontarEngine

**Lontar** is an original, from-scratch Kotlin engine that talks to
YouTube's InnerTube API — the same private API the official apps use —
without NewPipeExtractor, without YouTube.js, and without any PoToken
machinery. It was built for (and is the engine inside) the **Bunny**
Android client, and is published here so others can build on it.

## What's inside

Three Gradle modules:

- **`:core`** — the contract: `ExtractionBackend` plus the domain
  models (`VideoItem`, `VideoDetails`, `PlaybackStreams`, …). Pure
  JVM, no Android dependencies.
- **`:engine`** — Lontar itself:
  - Session bootstrap from the homepage `ytcfg`, search, home/topic
    feeds, channel pages, comments — metadata over the WEB persona.
  - **The doors (personas).** Playback is requested through
    per-client personas — `VISIONOS`, `ANDROID_VR`, `ANDROID`,
    `IOS`, … — each with its own client identity and capabilities,
    tried in a measured order.
  - **SABR playback ("Anyam")**: a full client for YouTube's
    server-side adaptive streaming — UMP parsing, a spool (file
    cache) the player reads while a pump fills it, seek jumps,
    classic-road backfill of the same spool, a self-disabling
    failure governance, and a frontier supervisor that detects a
    frozen stream and restarts the session at the frontier.
  - The **classic road**: Lontar builds its own DASH manifest from
    the player response (plus HLS and progressive fallbacks).
  - A **rescue floor**: when every other road fails, one last
    progressive stream is attempted so the video still plays.
  - A remote **engine config** (personas, SABR gates, budgets)
    fetched at runtime, with safe built-in defaults.
- **`:app`** — a standalone Android client with a Compose home/search
  feed, video details, Media3 playback, and system Picture-in-Picture.
  This client uses local engine defaults (no Bunny remote config) and
  does not integrate the engine's SABR path.

## Honest notes

- The engine fetches its remote config from the Bunny project's
  `engine-config.json` (the URL is in `EngineConfig.kt`). Point it
  at your own copy if you fork.
- The API keys in `PlayerPersona.kt` are YouTube's own public
  client keys — the ones shipped inside the official apps; every
  InnerTube client carries them. There are **no** user credentials,
  OAuth secrets, or signing keys in this repository.
- SABR is gated per network class by a failure-rate governance and
  can disable itself locally; behaviour is evidence-driven, not
  best-effort guessing.
- This is a working engine, not a finished library: the API surface
  is the one Bunny uses, and it evolves with Bunny.

## Build & test

Requires JDK 17, Gradle 8.13, and Android SDK platform 36 (the wrapper
script is included; the wrapper JAR is not bundled — use Android Studio
or a local Gradle 8.13 installation).

```
gradle :core:test :engine:test :app:testDebugUnitTest :app:assembleDebug
```

## License

[MIT](LICENSE) — free for anyone to use, including commercially.
