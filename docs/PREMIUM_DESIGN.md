# DataPulse 0.3 visual and motion specification

Manrope is bundled in four static weights (400, 500, 600, 700), sourced from the Google Fonts repository. Its SIL Open Font License is retained in the project and APK assets. No font downloads, paid font account or runtime network access are needed.

The palette uses cool silver #F2F5F7, ocean #12374B, deep teal #1B5260 and sea glass #84E5D2. A compact machined-dial treatment anchors the allowance card. Fine diagonal engraving and a single contour line are cached drawing operations, not full-screen bitmap noise or blur layers. The remaining-allowance arc agrees with its remaining-data label.

Motion: a 700 ms allowance reveal, 180 ms navigation content transition, a spring response on the selected navigation glyph, and 220 ms plan-comparison expansion. There are no continuous decorative animations. Compose's system animation-duration scaling is retained. Native fling scrolling remains direct; no artificial scroll delays or scroll interception are introduced. Screen changes reset to the top.

Performance changes: replace the large runtime icon dependency with 14 small vector resources, render animation updates through drawing/layer state, cache texture geometry, and defer background WorkManager setup until after the initial frame window. An optimized non-debug preview build uses resource shrinking and R8, signed only with a development key for evaluation.

The earlier startup ANRs remain a historical finding until new launch and Artemis results establish the current behavior. Emulator smoothness and startup measurements do not certify performance across physical devices.
