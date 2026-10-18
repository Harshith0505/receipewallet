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
