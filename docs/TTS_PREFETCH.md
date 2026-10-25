# Android TTS lookahead

## Source analysis

Analysis baseline: LNReader `dc394e8540d44902efc17b5a35485bdb0724bb00`;
HayaiTTS `7d51b0f92f1f1fddba0dcfc082efdf1bf2ff1e34`.

LNReader's Android `TtsPlaybackStore.advance()` previously waited for
`UtteranceProgressListener.onDone()` before sending the next paragraph, and
`speakCurrent()` always used `QUEUE_FLUSH`. No following paragraph could be
synthesized by Android while the current paragraph was playing.

HayaiTTS's `HayaiTtsService.onSynthesizeText()` calls `runtime.synthesize()` and
only then sends the completed sample array through `streamPcm()`. Its runtime
also offers streaming synthesis, but the framework service does not currently
use that path. This makes just-in-time paragraph submission especially costly.
HayaiTTS remains unmodified in this patch.

## Change

At explicit playback start, replay, seek, or settings change, stop the old queue,
invalidate its generation, apply the voice/rate/pitch, and enqueue the current
paragraph plus up to two following paragraphs with `QUEUE_ADD`. On natural
completion, advance progress and replenish the bounded window without stopping,
rebinding, resetting voice settings, or changing generation. Progress still
refers to the paragraph being read, not the furthest queued paragraph.

Pause, stop, load, seeking, and settings changes invalidate old callbacks.
Errors from any paragraph in the lookahead stop the queue rather than allowing
later paragraphs to continue after a failed synthesis. Skipping beyond the end
also cancels the lookahead. Existing JS interfaces and iOS behavior are unchanged.

This uses Android's synthesis/playback queue; it does not implement a second
PCM cache or claim to eliminate all silence. Audio buffering/backpressure,
model throughput, device performance, and very short paragraphs can still
limit useful synthesis overlap. Existing Android maximum utterance length
limits remain; this patch does not concatenate a whole chapter into one request.

## Build and tests

Prerequisites: a full JDK 17 (including `javac`), Node >=22.11, pnpm,
Android SDK platform/build tools 36, and the NDK/CMake versions requested by
the project. Set `JAVA_HOME` and `ANDROID_HOME` for your installation.

```sh
pnpm install --frozen-lockfile
pnpm run check
pnpm exec jest --selectProjects rn --runInBand
pnpm exec expo prebuild --platform android --no-install
cd android
./gradlew :nitro-tts:testDebugUnitTest
cd ..
pnpm run build:tts:debug
```

The standalone build script embeds JS, disables developer support in the
generated Android entry point, and excludes Expo development launcher/menu
modules from generated autolinking for this build. It retains the debug build/signing configuration,
targets arm64-v8a, and changes only generated Android files for this build mode.
The output is `android/app/build/outputs/apk/debug/app-debug.apk`.
A normal debug build without that script may need Metro. The debug application
identifier is the upstream debug identifier; it differs from the release app.

Native Robolectric/Mockito tests exercise the actual store callbacks and speech
requests: initial lookahead, refill without stop, pause/seek stale events,
settings invalidation, full stop, completion, and queued synthesis errors.
They do not synthesize or play real audio.

## Required physical-device validation (not performed here)

1. Back up the reader library. Install the debug APK alongside the release app.
2. Choose the same Hayai voice/model, speed and chapter for both versions.
3. Test a chapter with short dialogue and long paragraphs; repeat three times
   with a warm model and record cold start separately. Also compare a base engine.
4. Capture audible output with an external recorder or supported device audio
   capture. Measure end-of-speech to next speech onset at at least 30 paragraph
   boundaries; report median, p95, maximum, and first-audio latency. A word's
   natural prosody pause is not necessarily a synthesis delay.
5. Test pause/resume, stop, next/previous, arbitrary paragraph seek, rate/pitch,
   voice/engine change, chapter end/auto-next, screen off, notification controls,
   and transient/permanent audio focus interruptions. No old queued speech should
   leak after cancellation. Verify highlight/progress and no dropped or repeated text.
6. If synthesis remains slower than playback, investigate Hayai's streaming path
   with model-specific cancellation and PCM tests before changing the service.

Do not describe playback as fluid or the pauses as fixed until those audio
measurements and functional checks are complete.
