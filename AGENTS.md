# RSS-Feeder -- Project AGENTS.md

This file is read by any AI agent on the first message of a session.
It documents project-specific conventions and critical context.

## Project overview

Android app that fetches full-text articles from remote RSS feeds and local
.txt/.html/.pdf/.md files, serves them as valid RSS 2.0 feeds over a local
HTTP server (NanoHTTPD), and pushes them to a GitHub Pages relay for
consumption by RSS reader apps (gReader, Pluma, Feedly) over real HTTPS.

## Article title derivation (LocalFeedScanner.extractTitle())

Every local file's title goes through this four-level decision tree in
`app/src/main/java/com/rssfeeder/feed/LocalFeedScanner.kt:92-129`:

### Level 1: Real-word filename (checked first)
Strip extension, replace underscores with spaces. Check `looksLikeRealWords()`:
- Count letters (a-z, A-Z) and digits (0-9)
- If letter count > digit count * 2 AND letter count >= 3: use filename as title
- Examples: `OCR Camera Research.txt` -> "OCR Camera Research",
  `FreeText 1838544295 (1).html` -> falls through (more digits than letters)

### Level 2: HTML `<title>` tag (only if Level 1 failed AND file is .html/.htm)
Parse with Jsoup. If non-empty `<title>` exists, use it (max 120 chars).

### Level 3: First line of content
Trim content, take first line.
- If <= 80 chars: use as-is
- If > 80 chars: truncate to 77 + "..."

### Level 4: Filename with extension (last resort)
Used only if content is empty after trimming.

Remote feeds always use the remote RSS item's `<title>` directly (from
RssFetcher via Rome library). No smart filename logic applies.

## Relay mechanism

- RSS XML pushed to `gh-pages` branch of `dcon4/RSS-Feeder` via GitHub Contents API
- Served at `https://raw.githubusercontent.com/dcon4/RSS-Feeder/gh-pages/feeds/{sha256hex16}.xml`
- Token = first 16 hex chars of SHA-256("rss_feeder_relay_{feedUrl}") -- uses feed URL so each feed gets a unique URL that readers have never cached
- `RelayManager.kt`: pushFeed(), getRelayUrl()
- Requires GitHub PAT with `public_repo` scope, stored in DataStore

## Feed types

- `REMOTE`: Standard RSS/Atom URL, fetched + full-text extracted per article
- `LOCAL_FOLDER`: Android DocumentsContract tree URI, scanned for files

## Refresh behavior

- REMOTE: Checks each article link against existing DB. Only inserts NEW articles
  (not already in DB by link). Old articles preserved forever.
- LOCAL_FOLDER: Scans folder, checks each file's content URI against existing DB.
  Only inserts NEW files. Old files (renamed/deleted) keep their articles.
  No deletion on refresh.

## Key files

- `app/src/main/java/com/rssfeeder/feed/LocalFeedScanner.kt` -- folder scanning, title extraction, text/HTML/PDF/md reading
- `app/src/main/java/com/rssfeeder/feed/RssFetcher.kt` -- remote feed parsing via Rome
- `app/src/main/java/com/rssfeeder/server/FeedServer.kt` -- NanoHTTPD HTTP server
- `app/src/main/java/com/rssfeeder/server/RssXmlBuilder.kt` -- RSS 2.0 XML generation with sanitizeForXml()
- `app/src/main/java/com/rssfeeder/server/ServerViewModel.kt` -- server state, push relay, auto-push timer
- `app/src/main/java/com/rssfeeder/server/RelayManager.kt` -- GitHub relay push via Contents API
- `app/src/main/java/com/rssfeeder/ui/server/ServerScreen.kt` -- main server screen
- `app/src/main/java/com/rssfeeder/ui/addfeed/AddFeedScreen.kt` -- add feed with optional custom title
- `app/src/main/java/com/rssfeeder/ui/settings/SettingsScreen.kt` -- PAT, push interval
- `app/src/main/java/com/rssfeeder/debug/DebugLogger.kt` -- verbose logging toggle + share

## Required features (must exist in every build)

1. In-app debug log share button (top bar bug-report icon)
2. Verbose logging toggle (Settings screen, persisted in DataStore)
3. CI workflow that builds debug APK + uploads as artifact on every push/PR

## CI

GitHub Actions at `.github/workflows/build.yml`:
- Triggers: push to main, PRs against main, workflow_dispatch
- Runs ./gradlew :app:assembleDebug
- Uploads APK as `debug-apk` artifact
- JDK 17, ubuntu-latest

## Non-negotiable rules

- User is blind / uses TalkBack. All UI elements MUST have content descriptions.
- No emoji in code, comments, commit messages, or UI.
- Screen reader accessible formatting: headings for sections, bullet lists not
  ASCII dashes, tables for tabular data, no ASCII art.
- GitHub operations use `gh` CLI at `/tmp/gh_2.94.0_linux_amd64/bin/gh`.
- Never hardcode secrets or print them in output.
