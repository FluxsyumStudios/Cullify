# 📋 Changelog / Update Log

All notable changes to **Cullify** are documented here.

---

## [1.4.5] - 2026-07-13

### 🐛 Bug Fixes (Calculation Logic)
* **📐 Shape Classification Fix (TRIANGLE / STAR)**: The chunk-section classifier used unsigned (mirrored) distances, so asymmetric shapes were evaluated as if every section sat in the positive quadrant. Sections behind the player could be wrongly marked "fully kept" (vegetation never culled) or wrongly culled. The classifier now receives signed section bounds and matches the per-block shape math exactly on every side of the player.
* **⬡ Hexagon Over-Culling Fix**: The fast radial rejection used the configured limit as the culling radius, but the hexagon's farthest corners extend to ~115% of the limit. Sections near the hexagon's Z-extremes were culled while still inside the visible shape. The rejection now uses the true circumradius.
* **⭐ Star Concavity Fix**: The star shape is concave (union of two triangles), so a section whose four corners are inside the union is not necessarily fully inside. Sections are now only marked "fully kept" when all corners sit inside the same triangle.
* **🎲 LOD Hash Quality**: Completed the fmix64 finalizer (both multiply rounds) in the deterministic LOD density hash, removing visible stripe patterns in thinned vegetation at low density settings.

### ⚡ Performance Optimizations
* **♻️ Shared Section Classifier**: Unified the duplicated section-classification math from `ClientEventHandler` and the Sodium `LevelSlice` mixin into a single, corrected implementation in `CullifyMod`.
* **🟰 Equal-Distance Reuse**: When grass/flower/other culling distances are equal (the default), the section classifier now computes the result once and reuses it, skipping up to 2/3 of the classification work.
* **🖱️ Hit-Outline Early-Out**: The block-outline mixin now checks the cached enable flags before fetching the block state, removing per-frame config lookups.
* **💾 Atomic Config Saves** *(Fabric)*: The config file is written to a temp file and atomically moved into place, so a crash mid-save can never corrupt `cullify.json`.

---

## [Unreleased] / [1.0.1]

### ✨ New Features
* **📉 LOD Density Filter**: Added a deterministic, flicker-free density filter (`lodDensity` in config) that thins out vegetation in the outer 50% of the culling boundary. Available settings: `OFF`, `75%`, `50%`, and `25%`.
* **🌿 Double-Tall Block Synchronization**: Double-block plants (e.g., Tall Grass, Large Ferns, Sunflowers, Peonies) now calculate their culling state using the lower block's position, ensuring both halves are always kept or culled in perfect sync.
* **🌐 Brazilian Portuguese Localization**: Full translation support added (`pt_br.json`) for all config keys, buttons, tooltips, and menus.

### 🧪 Sodium & UI Integrations
* **⚙️ Sodium Config UI Integration**: The **Culling Shape** selection is now dynamically registered directly into the Sodium Options Menu screen when Sodium is detected at runtime.
* **🔘 Screen Options**: Added the new **LOD Density** selector directly to the custom Cullify GUI Config Screen (`K` menu).

### ⚡ Performance & Thread-Safety Optimizations
* **🔒 Thread-Safe Render Updates**: Section rebuild triggers (`invokeSetSectionDirty`) are now safely queued and executed on the client's main thread instead of worker threads, avoiding concurrency issues.
* **🚀 Configuration Cache**: Cached configuration properties (`cachedEnabled`, `cachedShape`, `cachedCullGrass`, etc.) directly on the JVM heap to avoid expensive, high-frequency `ModConfigSpec.Value.get()` calls in hot rendering loops.
* **📦 Lazy Section Vegetation Scan**: Implemented a lazy check (`cullify$hasVegetation` cache) in the `LevelSlice` mixin to quickly skip empty sections that do not contain any target plant blocks.
