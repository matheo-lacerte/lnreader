# Android continuous TTS, revision 2

The first revision queued individual paragraphs ahead of playback. The user
reported that long pauses remained with HayaiTTS on a physical phone.

## Current implementation

`TtsSpeechChunks.build()` normalizes line breaks, tabs, nonbreaking spaces,
Unicode paragraph/line separators and repeated whitespace to one space, then
joins original paragraphs into larger speech requests. The displayed chapter
and original paragraph IDs are unchanged. Existing punctuation is preserved;
no silent utterances or extra paragraph pauses are inserted.

Each request contains at most 1800 UTF-16 characters. HayaiTTS baseline
`7d51b0f92f1f1fddba0dcfc082efdf1bf2ff1e34` silently truncates requests beyond
2000 characters (`HayaiTtsService.MAX_TEXT_LENGTH`). Therefore sending an entire
chapter as a single framework request would lose text. The chunker prefers
sentence boundaries, then spaces, and splits oversized words without breaking
UTF-16 surrogate pairs. It skips whitespace-only inputs and retains a mapping
from each spoken range to the original paragraph index.

The Android store queues the current chunk plus two following chunks using
`QUEUE_ADD`, replenishing the window without stopping the engine. Explicit
seek/replay/settings changes rebuild the window from the requested original
paragraph. Generation IDs invalidate canceled callbacks. `onRangeStart` updates
paragraph progress when supported; otherwise progress stays at the chunk's
first paragraph until the next chunk or chapter completion. HayaiTTS does not
currently report word ranges, so highlighting and the position retained on
pause/replay can be coarser than the original paragraph-at-a-time mode. Manual
selection of an original paragraph still works. No timing-based highlighting
is fabricated. iOS and HayaiTTS source are unmodified.

This reduces paragraph-level synthesis boundaries; it does not prove gapless
audio. Larger requests may increase first-audio latency because Hayai generates
a complete audio array before passing PCM to Android. Model throughput, audio
backpressure and sentence prosody can still produce pauses between chunks.

## Build and validation

Prerequisites: JDK 17 including javac, Node >=22.11, pnpm, Android SDK/build tools
36, and the NDK/CMake versions requested by the project.

```sh
pnpm install --frozen-lockfile
pnpm run lint
pnpm run type-check
pnpm exec expo prebuild --platform android --no-install
cd android
./gradlew :nitro-tts:testDebugUnitTest --max-workers=2
cd ..
pnpm run build:tts:debug
```

The standalone build embeds JS, disables developer support and excludes Expo's
development launcher/menu in the generated Android files. Its generated icon
cleanup removes WebP duplicates when a PNG with the same resource name exists.
Output: `android/app/build/outputs/apk/debug/app-debug.apk`, arm64-v8a, debug key.

15 native tests passed: normalization, short paragraph packing, long text
preservation and limits, Unicode surrogate safety, sentence/range boundaries,
original paragraph seeking, initial lookahead, refill without stop, pause/seek
cancellation, settings invalidation, stop, completion, queued failures, merged
engine requests and range-based resume. Lint and TypeScript passed, with the
same 30 existing lint warnings. The repository-wide formatting baseline still
contains pre-existing issues. Native tests mock the engine and play no audio.

## Physical-device comparison still required

Use the same Hayai voice, rate and chapter for the official app, first patch
and revision 2. Include blank lines, short dialogue, very long paragraphs,
paragraphs without punctuation, and a chapter over 2000 characters. Check that
the last sentence is read. Measure first-audio latency and at least 30 audible
transitions (median, p95, maximum), separating paragraph and chunk boundaries.
Test pause/resume, stop, seeking, rate/pitch/voice changes, notifications, audio
focus interruptions, screen off and next chapter. Report highlighting accuracy.
No real audio playback of revision 2 was tested in this environment.
