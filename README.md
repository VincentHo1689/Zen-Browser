# Focus Browser

A **minimal, no‑distraction browser** built for productivity.
Everything is hardcoded – there are no settings, no toggles, and no way to bypass the restrictions.

## Key Features

- **Video blocking** – all video formats (mp4, webm, m3u8, YouTube streams) are blocked. Images and JavaScript stay enabled.
- **Ad & tracker blocking** – uses three powerful blocklists (Hagezi Pro, OISD full, OISD NSFW). Blocked domains cannot be unblocked.
- **Single tab** – no tab management, no clutter. Exactly one browsing session.
- **Bottom bar** – address bar on the left, simple `<` (back) and `>` (forward) buttons on the right. No home button, no extras.
- **Dark / Light theme** – automatically follows your system theme (DayNight).
- **Extremely lightweight** – the APK is only ~3 MB. No unnecessary permissions, no bloat.

## Intuitive

This browser is a very lightweight browser for user who wants a browser with minimal distraction, I got this idea from the no browser, however no browser seems outdated and some of the things like the even the javascript is not working, whats why I made this, mainly for myself and i hope that it could help someone who needs it. For the hardcoded function this is because, I am a person without any self controls, the more setting i allow for myself, i just couldne focus, that browser nowadays just have too many toggles and too many settings for us, so I am making this so I could just use it for doing productivity things, and all other things is blocked.

## Why this browser?

I built this to stay focused while working.

- **No videos** means no YouTube, no embedded clips, no distractions.
- **Blocklists are hardcoded** – you cannot accidentally (or intentionally) turn them off.
- **Only one tab** prevents endless tab‑hoarding.
- **Simple navigation** – just back, forward, and a URL bar.

If you need a browser that helps you concentrate, this is it.

## Installation

Download the latest APK from the [Releases](https://github.com/yourusername/FocusBrowser/releases) page and install it on any Android device (version 5.0+).

## Screenshots

_(Add screenshots here if you like)_

## Building from source

```bash
git clone https://github.com/yourusername/FocusBrowser.git
cd FocusBrowser
./gradlew assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/`.

## How it works

- **Ad blocking** – the three blocklists are fetched on first launch and stored in memory. Every request is checked against the list of blocked domains.
- **Video blocking** – the browser intercepts requests whose URL ends with `.mp4`, `.webm`, `.3gp`, contains `.m3u8` or `/videoplayback?`, and returns an empty response.
- **No toggles** – all features are enabled in the code; there is no UI to disable them.

## License

MIT – use it, modify it, but don’t blame me if you actually get work done.
