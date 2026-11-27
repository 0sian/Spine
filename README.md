<div align="center">

<img src="docs/icon.png" width="132" alt="Spine icon" />

# Spine

**A reading app that looks like a bookshelf and reads like it was designed on purpose.**

Reading apps are either clunky or boring. Spine is neither — your EPUBs stand on tilted-book shelves with their real covers, and every page is themeable down to the typeface.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#build-it)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Readium](https://img.shields.io/badge/Readium-3.1-1E88E5)](https://readium.org/kotlin-toolkit)
[![EPUB](https://img.shields.io/badge/format-EPUB-C8A951)](#features)

<br />

<img src="docs/hero.png" alt="Spine: the bookshelf, three reading themes and the reader panel" />

</div>

<br />

## Features

- **A shelf, not a list.** Books stand on shelves, leaning at their own slight angles, drawn with the top of the pages visible over the cover. Covers, titles and authors come straight from each book's metadata.
- **Four reading themes, applied live.** Midnight, Parchment, Terminal and Paper each change page colour, text colour, typeface, line height and alignment together. Switch mid-chapter and the page re-flows instantly.
- **A reader that stays out of the way.** Full-screen, no chrome. Tap the edges to turn pages, tap the middle for the panel.
- **Know where you are.** A progress slider you can drag, the current chapter name, and a full table of contents with nested parts.
- **Remembers everything.** Reading position per book, your theme and font size, and a shelf ordered by what you read last.
- **Local and private.** It reads the EPUBs in a folder on your phone. No account, no network, no telemetry.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/library.png" width="220" /><br /><sub>The shelf</sub></td>
    <td align="center"><img src="docs/screenshots/reader-parchment.png" width="220" /><br /><sub>Parchment</sub></td>
    <td align="center"><img src="docs/screenshots/reader-terminal.png" width="220" /><br /><sub>Terminal</sub></td>
    <td align="center"><img src="docs/screenshots/contents.png" width="220" /><br /><sub>Chapters</sub></td>
  </tr>
</table>

## How it works

| Layer | What it does |
| --- | --- |
| **Readium Kotlin toolkit** | Parses EPUBs and renders them (pagination, positions, locators). Spine does not reinvent the hard parts. |
| **Theme engine** | A theme is a set of tokens (`bg`, `text`, `font`, `lineHeight`, `margins`, `align`) pushed to the page as Readium preferences. Adding a theme is one line in `ReaderTheme.kt`. |
| **Shelf renderer** | `BookCover.kt` draws each book on a Compose `Canvas`: the cover art, a foreshortened page-top and a fore-edge, plus a stable per-book lean. |
| **Library scanner** | Walks `Documents/Books`, reads metadata and covers with Readium, caches covers as JPEGs and metadata as JSON so launches are instant. |

```
app/src/main/java/app/spine/
├── MainActivity.kt          shelf host
├── ui/                      shelf, book model, canvas cover drawing
├── library/                 folder scan, metadata + cover cache, view model
└── reader/                  Readium navigator, themes, reader panel
```

## Build it

You need the Android SDK (platform 35) and a JDK between 17 and 21.

```sh
git clone git@github.com:0sian/Spine.git
cd Spine
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # your SDK path
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Put your `.epub` files in `Documents/Books` on the phone. On first launch Spine asks for **All files access**, because Android's folder picker refuses to hand out that folder. Grant it once and the shelf fills itself.

## Roadmap

- [ ] PDF support
- [ ] Custom themes (edit colours, fonts and spacing in-app; share as JSON)
- [ ] Themes generated from a book's cover colours
- [ ] Bookmarks and highlights
- [ ] Choose any library folder

## Credits

Built on the [Readium Kotlin toolkit](https://github.com/readium/kotlin-toolkit).
