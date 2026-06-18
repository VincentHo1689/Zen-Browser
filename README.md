# Zen Browser

A **minimal, no‑distraction browser** built for deep work.
Everything is hardcoded – there are no settings, no toggles, and no way to bypass the restrictions.

## Key Features

- **Video Hiding with Audio‑Only Playback** – Videos are visually hidden, but audio continues. An “Audio only” overlay appears over hidden video elements. Trusted sites (Wikipedia, GitHub, Stack Overflow, educational and government domains) are exempt from this restriction.
- **Hardcoded Blocklists** – Uses Hagezi’s blocklists (NSFW, social, gambling, URL shorteners, hosters, no‑safesearch, threats, and Pro) to block distracting and harmful websites. Blocked domains cannot be unblocked.
- **Single Tab** – No tab management, no clutter. Exactly one browsing session.
- **Smart Bottom Bar** – Address bar on the left, simple `<` (back) and `>` (forward) buttons on the right. Long‑press back for bookmarks, long‑press forward for downloads. No home button, no extras.
- **Session Timer** – A live timer shows how long you’ve been browsing, turning from green to yellow to red as the minutes pass. Displayed directly in the address bar.
- **Automatic SafeSearch** – Enforces strict SafeSearch on Google, Bing, DuckDuckGo, Yahoo, Yandex, and many other search engines by hardcoding the correct safe‑search parameters into every query.
- **QR Code Scanner** – A built‑in scanner opens from the address bar to quickly load URLs.
- **Minimal Bookmarks & Downloads** – Manage bookmarks or view/downloaded files through simple popups, accessible only via long‑press gestures.
- **Background Audio** – A lightweight foreground service keeps audio playing even if you switch apps.
- **Dark / Light Theme** – Automatically follows your system theme. The status bar colour adapts to the current website’s background for a seamless look.
- **Extremely Lightweight** – The APK is only ~1 MB. No unnecessary permissions, no bloat.

## Philosophy

No toggles. No settings. No “just this once”. Every safety net is hardcoded because even the smallest option becomes a distraction. This browser is for people who need to work, study, or read without falling into endless tabs and videos.

## Why I Built This

I got the idea from “No Browser”, but it felt outdated and often broke JavaScript. Modern browsers drown you in configuration – endless menus, experimental flags, and just enough rope to hang your focus. I made Zen Browser for myself, because I have no self‑control when options exist. If you also struggle to stay on task, I hope this helps you get into a state of **Zen**.

## Download

Get the latest APK from the [Releases page](https://github.com/VincentHo1689/Zen-Browser/releases).

## License

MIT – use it, modify it, but don’t blame me if you actually get work done.
