# Image Compressor

A simple Android app that compresses your photos from JPG/PNG to WebP format, freeing up massive storage space on your device — without losing quality and without paying for cloud subscriptions.

## Why?

Cloud-based photo services like Google Photos require paid subscriptions for full-quality storage. This app keeps all your photos **on your own device**, compressed to a fraction of the size, so you never depend on a monthly fee or an internet connection.

A typical camera roll of **10 GB** can be reduced to around **1 GB** using WebP compression — with virtually no visible quality loss.

## Features

- Converts JPG/JPEG and PNG files to WebP format
- PNG images are compressed losslessly
- JPG images compressed with configurable quality (default 65)
- Max resolution cap (default 1280px) to resize oversized photos
- Preserves EXIF rotation data
- Skips files already in WebP format
- Skips files already compressed (idempotent)
- Deletes originals only after successful compression
- Moves compressed files from a temp folder to a final destination
- Runs as a foreground service with notification (won't be killed mid-compression)
- Remembers your folder settings between sessions

## How it works

1. **Select three folders** using Android's folder picker:
   - **Source** — folder containing photos to compress (e.g. `DCIM/Camera`)
   - **Temp** — where compressed WebP files are written first
   - **Final** — where files are moved after successful compression

2. **Adjust settings** (optional):
   - Quality: 1–100 (default 65 — good balance of size and quality)
   - Max resolution: largest dimension in pixels (default 1280)

3. **Tap Start** — the app processes all images in the background, showing progress in a notification.

4. **Originals are deleted** only after the compressed file is successfully written, so you never lose photos.

## Install

Download the latest APK from the [Releases](https://github.com/adegard/ImageCompressor/releases) page, install it on your Android device, and grant the requested folder permissions when prompted.

### Permissions

The app needs access to your photo folders. When you tap "Select", Android will ask you to grant access to that directory. This is normal — the app uses Android's Storage Access Framework (SAF) and never reads anything outside the folders you choose.

## Recommended Gallery App

For browsing WebP files on Android, use **Gallery** from [Simple Mobile Tools](https://simplemobiletools.com/simplegallery/):

- Open source (Simple Mobile Tools)
- Full WebP support
- No ads, no tracking, no subscriptions
- Clean Material Design interface

Install from: https://simplemobiletools.com/simplegallery/

## License

MIT
