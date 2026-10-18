# Performance optimization

Branch: `performance-optimization`, based on approved UI commit `3558de9`.

## Changes

- Cache the seeded paper-grain and torn-edge geometry per card size, drawing two paths rather than 1,800 separate grain lines plus individual edge circles. Batch the unchanged background-dot pattern into one cached path.
- Decode bundled food photographs on `Dispatchers.IO`, reuse them across screens in a thread-safe 16 MiB LRU cache, and preserve image sizing while a cold image loads.
- Decode pantry previews and resize/compress/write scan photos off the main thread.
- Cache recipe ranking, meal price estimates, weekly cart aggregation, store rankings and the current quote using Compose derived state. Recalculate when their actual inputs change. Keep the pricing formulas unchanged.

Screen structure, colors, labels, controls, recipes, dietary checks and checkout behavior are preserved. This does not change backend services or AI latency.

## Tablet measurement

Lenovo TB300FU, Android 13, device HGR565PQ, 800 × 1280, debug preview builds. Start the app on Build with AI at the top of the page. Reset `dumpsys gfxinfo com.recipewallet.app.preview`, then inject four upward and four downward swipes, each 400 ms, between (400,1000) and (400,400), with 400 ms between swipes. Capture gfxinfo after settling for one second.

| Metric | Approved build | Optimized build |
| --- | ---: | ---: |
| Rendered frames during the gesture sequence | 54 | 372 |
| Median frame time | 150 ms | 21 ms |
| 90th percentile | 150 ms | 24 ms |
| 95th percentile | 200 ms | 28 ms |
| 99th percentile | 250 ms | 36 ms |
| Janky frames, Android current metric | 54 / 54 (100%) | 5 / 372 (1.34%) |
| Janky frames, legacy metric | 54 / 54 (100%) | 133 / 372 (35.75%) |

These are single controlled development-device samples, not a release benchmark or a guarantee for every screen. More frames in the same gesture sequence indicate fewer stalled updates. A locked-screen capture with zero rendered frames and a run on the pantry screen were discarded, not included in this comparison. Cold launch remained around 5.4 seconds in the two recorded launches; this pass primarily improves interactive rendering.

The approved and optimized home screenshots were visually checked for matching layout and colors.

## Validation

- Debug app and instrumentation APKs compile successfully.
- All 21 existing JVM unit tests pass.
- Both new `PricingCacheTest` instrumentation tests pass on the Lenovo. They compare cached totals against fresh pricing, verify reuse for unrelated UI state, and check invalidation for servings, ingredient selection, fulfillment, organic preference, store/card choice and pantry ownership.
- No backend code or pricing formula changes.

## Tab-switch follow-up

The remaining delay came from discarding each destination's UI and building it again on selection. Home now retains four prepared **viewports**. Only the active viewport is placed, drawn and exposed through accessibility semantics. Each viewport uses a keyed lazy list, so long recipe lists, pantry matches and weekly details are still composed as needed. Recipe rows keep the same responsive column thresholds and spacing; the 1200 dp page-width cap is preserved.

This trades some one-time home preparation and retained viewport memory for faster navigation. The final cold launch measured 6,812 ms, compared with 5,362 ms in the earlier pass (one launch each); startup is slower in these samples, while subsequent navigation improves. No navigation destinations, controls, colors, pricing rules or backend requests were added or removed. Photo-picker launchers stay registered while Home is open. The normal Android Studio debug build supports this change; experimental native-compilation build settings were not retained.

### Selection-to-draw measurement

The optional debug-only `TabSwitchTrace` listener records elapsed time from the navigation callback (including focus/keyboard dismissal) until the selected destination is drawn. Without a test listener it records nothing. Measure three cycles of Build manually → My pantry → My week → Build with AI on the same Lenovo. Both sides use ordinary debug preview builds. End-to-end test-idle timings are also captured, but are not the first-draw metric below.

| Destination | Previous median, ms | Retained viewport median, ms | First retained switch, ms |
| --- | ---: | ---: | ---: |
| Build manually | 1137 | 119 | 535 |
| My pantry | 734 | 176 | 293 |
| My week | 801 | 125 | 272 |
| Build with AI | 704 | 115 | 102 |

Single-device, three-sample development measurements; initial rendering and later switches differ. The first manual-tab draw still takes about half a second in this sample. Test runs made while the tablet was locked were excluded.

All 21 JVM tests pass. Five tablet instrumentation tests pass: the timing/navigation test, deferred-content/calendar reachability, both pricing-cache tests, and mood/dietary-filter interaction. The deferred-content test confirms the last recipe remains reachable in both discovery and pantry, and that calendar selection and the weekly grocery summary remain accessible. Hidden tabs do not create duplicate controls in the accessibility tree. Test windows keep the screen awake only while their activity exists.

## Integration with main

PR #6 targets `main`. Its scope includes the scrapbook meal-studio frontend inherited by the performance branch, alongside the rendering and tab optimizations above. Main's Supabase configuration, private recipe library, account screen and email-confirmation callback are retained. Saved-recipe navigation now uses `RecipeCloudViewModel` while the four prepared lazy tab viewports stay in place. The local preview build remains available through `-PpreviewInstall=true`.

After conflict resolution, the preview app and instrumentation APK compile, all 21 JVM tests pass, and all 74 backend tests pass. All five functional emulator checks pass, covering deferred tab content, pricing-cache invalidation, account navigation and saved-recipe editing. Physical-device performance was not remeasured during this merge; the timings above describe the earlier Lenovo build.
