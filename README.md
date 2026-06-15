# RSS-Feeder

Android app that turns RSS/Atom feed excerpts into full articles, and local files into virtual RSS feeds.

## Features

- **Full-text RSS reader** -- Subscribe to RSS feeds; the app fetches the full article content from each link so you read the complete text, not just a summary.
- **Local folder feed** -- Select any folder on your device containing `.txt`, `.html`, or `.pdf` files. Each file becomes an entry in a virtual RSS feed.
- **Offline reading** -- Articles are cached locally.
- **Background sync** -- Feeds refresh automatically every 30 minutes.
- **OPML import/export** -- Add feeds in bulk.

## Build

```bash
git clone https://github.com/dcon4/RSS-Feeder.git
cd RSS-Feeder
./gradlew :app:assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## License

GPLv3
