# Image Compressor

A simple Android app that compresses your photos from JPG/PNG to WebP format, freeing up massive storage space on your device — without losing quality and without paying for cloud subscriptions.

![Screenshot](Screenshot_20260711-192514.png?raw=true "Screenshot")

## Why?

Cloud-based photo services like Google Photos require paid subscriptions for full-quality storage. This app keeps all your photos **on your own device**, compressed to a fraction of the size, so you never depend on a monthly fee or an internet connection, for your whole life!

A typical camera roll of **10 GB** can be reduced to around **1 GB** using WebP compression — with virtually no visible quality loss.

## Features

### Photo Viewer (Main Screen)
- Browse any folder on your device as a photo gallery
- Navigate subfolders with a visual breadcrumb bar
- Folder image counts shown at a glance
- Full-screen image viewer with swipe between photos
- **Delete** photos directly from the viewer (with confirmation)
- **Rotate** photos 90° (rotation is saved to file)
- Folder listing cached for instant navigation on revisits
- Dark mode support

### Photo Compressor (Settings Screen)
- Converts JPG/JPEG and PNG files to WebP format
- JPG images compressed with configurable quality (default 65)
- Max resolution cap (default 1280px) to resize oversized photos
- Preserves EXIF rotation data
- Skips files already compressed (idempotent)
- Deletes originals only after successful compression
- Temp files stored in app-internal cache (hidden from file managers)
- Runs as a foreground service with notification (won't be killed mid-compression)
- Remembers all folder settings between sessions

## How it works

1. **Set a gallery root folder** — choose any folder to browse as a photo gallery (this is the main screen)

2. **In Settings, select compression folders:**
   - **Source** — folder containing photos to compress, typically camera folder (e.g. `DCIM/Camera`)
   - **Final** — where compressed files are moved after successful compression

3. **Adjust settings** (optional):
   - Quality: 1–100 (default 65 — good balance of size and quality)
   - Max resolution: largest dimension in pixels (default 1280)

4. **Tap Start** — the app processes all images in the background, showing progress in a notification.

5. **Originals are deleted** only after the compressed file is successfully written, so you never lose photos.

## Install

Download the latest APK from the [Releases](https://github.com/adegard/ImageCompressor/releases) page, install it on your Android device, and grant the requested folder permissions when prompted.

### Permissions

The app needs access to your photo folders. When you tap "Select", Android will ask you to grant access to that directory. This is normal — the app uses Android's Storage Access Framework (SAF) and never reads anything outside the folders you choose.

## License

MIT
