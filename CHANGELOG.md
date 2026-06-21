# 📋 Changelog / Update Log

All notable changes to **Cullify** are documented here.

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
