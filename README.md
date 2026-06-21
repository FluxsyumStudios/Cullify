# 🌱 Cullify

<div align="center">

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange?style=for-the-badge)](https://neoforged.net/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Client--Side-green?style=for-the-badge)]()
[![Vibe Coding](https://img.shields.io/badge/Made%20with-Vibe%20Coding-purple?style=for-the-badge)]()

**Cullify** is an advanced, ultra-optimized, client-side culling system for Minecraft. It dynamically optimizes rendering performance by intelligently culling decorative vegetation (grass, flowers, ferns, algae, kelp, etc.) based on your distance and customizable geometric shapes, without altering the actual world blocks.

</div>

---

## ✨ Features

* 🚀 **FPS Boost** — Substantially reduces vertex throughput and draw calls by culling non-visible distant vegetation.
* 📐 **8 Culling Shapes** — Customizable geometric boundaries to control culling behavior around the player.
* ⚡ **Voxel Grid Cache** — Utilizes a fast `128×128×128` voxel grid centered on the player for instant $O(1)$ visibility lookups.
* 📉 **LOD Density Filter** — Deterministic, hash-based density filtering that thins out outer vegetation (75%, 50%, or 25%) to save CPU overhead.
* 🌿 **Double-Tall Vegetation Support** — Synchronizes culling between bottom and top halves of double-tall blocks to prevent visual mismatch.
* 🔒 **Thread-Safe Rendering Updates** — Section updates are safely queued and dispatched directly on Minecraft's main render thread.
* ⚙️ **In-Game Config Menu** — Fully custom configuration screen accessible via the `K` keybind, commands, or the NeoForge mods menu.
* 📊 **Real-time Statistics HUD** — Screen overlay tracking culled blocks, water replacements, chunk updates, and draw calls.
* 🧪 **Sodium Compatibility** — Direct hooks into Sodium's internal `LevelSlice` rendering pipeline and Sodium Options API.
* 🌐 **Localization** — Full support for English (`en_us`) and Brazilian Portuguese (`pt_br`).
* 💾 **Auto-Save & Persistency** — Settings save automatically to `config/cullify-client.toml`.

---

## 📐 Geometric Culling Shapes

Unlike traditional culling mods, Cullify offers flexible mathematical boundary checks on the horizontal plane (X/Z) and vertical axis (Y):

| Shape | Geometry Mode | Description |
| :---: | :--- | :--- |
| 🟢 | **Sphere** | Culls blocks outside a 3D spherical radius. |
| 🌀 | **Cylinder** | Culls blocks outside a horizontal circular radius (infinite Y). |
| 📦 | **Box** | Culls blocks outside a 3D cubical bounding box. |
| 🔺 | **Triangle** | Culls blocks outside an equilateral triangular bounds centered on the player. |
| ⬡ | **Hexagon** | Culls blocks outside a hexagonal boundary. |
| ⭐ | **Star** | Culls blocks outside a 6-point star (union of two intersecting triangles). |
| ⏹️ | **Square** | Culls blocks outside a flat square grid. |
| 🔵 | **Circle** | Culls blocks outside a flat horizontal circular boundary. |

---

## 🎮 Controls & Commands

Configuring and inspecting Cullify in-game is easy:

### ⌨️ Keybinds
* `K` — Opens the custom Cullify GUI Config Screen.

### 💬 Chat Commands
* `/cullify menu` — Opens the configuration GUI menu.
* `/cullify debug` — Toggles the real-time statistics HUD overlay.
* `/cullify stats` — Prints current performance statistics directly in chat.
* `/cullify verbose` — Toggles verbose logging for debugging.
* `/cullify reload` — Forces a complete reload of the client level renderer chunks.

---

## 🛠️ Configuration

Configurations are stored at `config/cullify-client.toml` and updated dynamically:

```toml
[general]
	# Enable or disable vegetation culling globally.
	enabled = true
	# Shape of the culling boundary around the player (SPHERE, CYLINDER, BOX, TRIANGLE, HEXAGON, STAR, SQUARE, CIRCLE).
	cullingShape = "SPHERE"
	# Enable culling of grass, ferns, and short grass.
	cullGrass = true
	# Enable culling of flowers and double-height flowers.
	cullFlowers = true
	# Enable culling of other decorative plants (dead bushes, kelp, seagrass, etc.).
	cullOtherPlants = true
	# Maximum distance in blocks to render grass, ferns, and tall grass.
	# Range: 0 ~ 512
	grassCullDistance = 48
	# Maximum distance in blocks to render flowers and double-height flowers.
	# Range: 0 ~ 512
	flowerCullDistance = 48
	# Maximum distance in blocks to render other decorative plants.
	# Range: 0 ~ 512
	otherPlantCullDistance = 32
	# LOD density filter (0-100). 100 = keep all plants (disabled). Lower values thin out
	# plants near the culling boundary using a deterministic hash.
	# Range: 0 ~ 100
	lodDensity = 100
	# Enable debug overlay HUD to show culling stats and configuration status.
	debugMode = false
```

---

## 🔌 Compatibility

* **Sodium / Embeddium** — Fully integrated. Hooks into Sodium's internal `LevelSlice` rendering pipeline to batch-cull blocks in MDI (Multi-Draw Indirect) buffers. The Culling Shape option and general configurations are also registered directly in Sodium's Options Menu.
* **Vanilla Renderer** — Fallback compatibility included for standard Minecraft block rendering pipeline.
* **No Server Mod Required** — Cullify is 100% client-side; you can join any server without issues.

---

## 📄 License

This mod is available under the **GNU General Public License v3 (GPL-3.0)**. Feel free to use it in any modpacks!

---

## ⚡ Vibe Coding

This mod was built 100% using **Vibe Coding**! Developed entirely through AI-assisted pair programming, dynamic prompt iterations, and automated compilation loops. Let the vibes render! 🌟