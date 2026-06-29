# 📋 Changelog / Update Log

All notable changes to **Cullify** are documented here.

---

## [1.2.0+mc1.21.1] - 2026-06-28

### 🎨 Visual & Assets Updates
* **🏷️ New Mod Logo**: Updated the mod logo (`Cullify.png`) to a new high-resolution 2048x2048 design. Replaced the mod list icon, Custom HUD overlay icon, and menu screens.
* **🖼️ Sodium Sidebar Icon**: Configured the Cullify option tab inside the Sodium menu to display the custom mod icon natively next to the tab name (`setNonTintedIcon`).

### 📈 Benchmark & Performance Profiling
* **⏱️ Two-Phase Comparison Benchmark**: The `/cullify benchmark start <seconds>` command now triggers an automated, two-phase comparative benchmark:
  * **Phase 1**: Temporarily disables Cullify and measures rendering performance (warmup + recording).
  * **Phase 2**: Re-enables Cullify and records performance under the same conditions.
* **📊 Detailed FPS Metrics**: Tracks precise frame times to calculate Average FPS, Minimum/Maximum FPS, and critical gaming benchmarks (1% low and 0.1% low frame rates).
* **📄 Interactive Summary & File Links**: Generates a side-by-side comparison report text file, displays a colorized chat summary with performance gain percentages, and prints a clickable file link in chat to open the report instantly.

### 🧪 Sodium & Compatibility Improvements
* **⚡ Native Sodium Options**: Fully resolved the startup crash related to duplicate mod ID entries (`IllegalArgumentException` in `ConfigManager.registerConfigs`) under Sodium 0.8.x. Removed redundant annotations and unified option registering through standard `neoforge.mods.toml` mod properties.
* **📦 Element Name Translation**: Integrated element translation support (`setElementNameProvider`) for the Culling Shape enum, and provided translations for all geometric shapes in English and Brazilian Portuguese.
* **⚙️ Default Values & Storage Handlers**: Fully populated option builders with proper default values and background storage handlers (`ClientEventHandler::scheduleDebouncedSave`) to prevent `NullPointerException` during Sodium configurations setup.

### 🌐 Localization & Translations
* **🇧🇷 Translation Coverage**: Added missing translation keys (`cullify.options.page` and shapes names) for both English (`en_us.json`) and Portuguese (`pt_br.json`).

---

## [1.0.1]

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
