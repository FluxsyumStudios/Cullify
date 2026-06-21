# 🌱 Cullify

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange?style=for-the-badge)](https://neoforged.net/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Client--Side-green?style=for-the-badge)]()
[![Vibe Coding](https://img.shields.io/badge/Made%20with-Vibe%20Coding-purple?style=for-the-badge)]()

**Cullify** is an advanced, ultra-optimized, and fully client-side culling system for Minecraft. It dynamically optimizes rendering performance by intelligently culling vegetation (grass, flowers, ferns, algae, kelp, etc.) based on your distance and customizable geometric shapes, without making any modifications to the actual world blocks.

Whether you are using Vanilla rendering or **Sodium/Embeddium**, Cullify is designed to skyrocket your FPS in dense forest, jungle, and meadow biomes while maintaining visual fidelity.

---

## ✨ Features

*   🚀 **FPS Boost**: Significantly reduces vertex throughput and draw calls by culling non-visible distant vegetation.
*   📐 **8 Culling Shapes**: Choose how the culling boundary behaves around the player.
*   ⚡ **Ultra-Optimized Algorithm**: Leverages a multi-threaded adjacent chunk-section (AABB) AABB cache, avoiding expensive per-block distance checks.
*   ⚙️ **In-Game Config Menu**: Access settings instantly by pressing `K` or using commands.
*   📊 **Real-time Statistics HUD**: Track how many blocks are culled, chunk updates, and water replacements in real time.
*   🧪 **Sodium Compatibility**: Features custom integrations with Sodium's internal `LevelSlice` rendering pipeline and Sodium Options API.
*   💾 **Auto-Save & Persistency**: All configurations save immediately to `config/cullify-client.toml`.

---

## 📐 Geometric Culling Shapes

Unlike traditional culling mods that only offer basic spherical boundaries, Cullify introduces mathematical boundary checks on the horizontal plane (X/Z) and vertical axis (Y):

| Shape | Geometry Mode | Description |
| :--- | :--- | :--- |
| 🟢 **Sphere** | 3D Spherical | Culls blocks outside a 3D radius. |
| 🌀 **Cylinder** | 2D Circular (infinite Y) | Culls blocks outside a horizontal circular radius. |
| 📦 **Box** | 3D Cubical | Culls blocks outside a 3D bounding box. |
| 🔺 **Triangle** | 2D Equilateral | Triangular culling bounds centered on the player. |
| ⬡ **Hexagon** | 2D Hexagonal | A clean hexagonal culling shape. |
| ⭐ **Star** | 2D 6-Point Star | Union of two intersecting triangles for a unique visual path. |
| ⏹️ **Square** | 2D Square | Culls blocks outside a flat square grid. |
| 🔵 **Circle** | 2D Circular | Flat horizontal circle boundary check. |

---

## 🎮 Controls & Commands

You can configure and inspect Cullify dynamically in-game:

### Keybinds
*   `K` - Open the custom Cullify GUI Config Screen.

### Commands
*   `/cullify menu` - Opens the configuration GUI menu.
*   `/cullify debug` - Toggles the real-time statistics HUD overlay.
*   `/cullify stats` - Prints current performance statistics (culled blocks, draw calls, active renderer) directly in chat.
*   `/cullify verbose` - Toggles verbose logging for debugging.
*   `/cullify reload` - Forces a complete reload of the client level renderer chunks.

---

## 🛠️ Configuration

The configurations are saved in your game's config directory: `config/cullify-client.toml`.

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
	# Enable debug overlay HUD to show culling stats and configuration status.
	debugMode = false
```

---

## 🔌 Compatibility

*   **Sodium**: Seamlessly integrated. When Sodium is detected, Cullify hooks directly into `LevelSlice` rendering to batch-cull blocks in MDI (Multi-Draw Indirect) buffers.
*   **Vanilla Renderer**: Fallback compatibility included for standard Minecraft block rendering pipeline.
*   **Sodium Options API**: Cullify options are registered directly into Sodium's Options Menu page if installed.
*   **No Server Mod Required**: Since Cullify is 100% client-side, you can join any server without issues.

---

## 📄 License

This mod is available under the **GNU General Public License v3 (GPL-3.0)**. Feel free to use it in any modpacks!

---

## ⚡ Vibe Coding

This mod was built 100% using **Vibe Coding**! It was developed entirely through AI-assisted pair programming, dynamic prompt iterations, and automated compilation loops. Let the vibes render! 🌟