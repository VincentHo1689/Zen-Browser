# Project Skeleton

This document outlines the anatomy and modular structure of the Zen-Browser application, explaining the separation of concerns and the purpose of each class.

## Overview

The codebase follows a modular design, extracting discrete responsibilities from `MainActivity` into specific helper classes. This improves readability, maintainability, and testability.

## Directory Structure

`app/src/main/java/com/zen/browser/` (or `com.focus.browser/` based on filesystem path mapping)

### 1. **MainActivity.java**

- The entry point and primary UI orchestrator.
- **Responsibilities:**
    - View initialization and life-cycle management (`onCreate`, `onResume`, `onPause`, etc.).
    - Orchestrating interaction between components (`BlocklistManager`, `TimerManager`, `BookmarksDbHelper`).
    - Handling primary UI events like back/forward actions, text inputs, long presses, and showing menus.
    - Setup of `WebView` using custom `BrowserWebViewClient`.
- By delegating logic, it stays focused on UI bindings instead of performing deep logic inline.

### 2. **BlocklistManager.java**

- Handles loading and querying blocked domains to assist the user tracking their digital well-being.
- **Responsibilities:**
    - Keeps in-memory maps of blocked names to domain sets.
    - Exposes `getBlockingLists(String host)` to query if a host is blocked.
    - Exposes `loadBlocklistsInBackground(...)` which fetches lists asynchronously from the network.

### 3. **UrlHelper.java**

- A utility class for safely formatting URLs and checking safe-site parameters.
- **Responsibilities:**
    - Defines regular expressions (`URL_PATTERN`).
    - Implements static methods to append SafeSearch queries to Google results (`enforceSafeSearch`).
    - Formats user input directly into searchable intents or qualified URLs (`formatUrl`).
    - Identifies video endpoints to selectively disable or process (`isVideoUrlBlock`).

### 4. **BrowserWebViewClient.java**

- Subclasses `WebViewClient` to isolate web loading behavior.
- **Responsibilities:**
    - Manages what happens when page loading starts and finishes, communicating back with `MainActivity`.
    - Determines whether to override URL requests.
    - Intercepts requests internally to inject custom JS (`injectZenController()`) via `MainActivity` when needed, or block traffic via `BlocklistManager`.
    - Serves dynamic HTML responses if domains are blocked.

### 5. **TimerManager.java**

- Encapsulates the logic related to the active session stopwatch loop.
- **Responsibilities:**
    - Manages the `Handler` thread processing for tracking elapsed seconds and minutes.
    - Updates the text view timer format natively.
    - Computes foreground/background states via `start()`, `pause()`, and `resume()` APIs.

### 6. **DialogHelper.java**

- Removes cumbersome manual UI builder logic from the main view layer.
- **Responsibilities:**
    - Creates the Bookmarks popup (`showBookmarkPopup`), populates scrollable interactions, and triggers corresponding inserts and deletions in `BookmarksDbHelper`.
    - Handles the Downloads popup (`showDownloadListPopup`), querying Android's `DownloadManager` systematically.

### 7. **BookmarksDbHelper.java**

- (Existing class) Local SQLite helper managing bookmarks persistence.
- **Responsibilities:** Creating schemas and basic CRUD operations.

### 8. **SuggestionAdapter.java**

- (Existing class) An adapter handling data binding for autocomplete and suggested URL input rows.
