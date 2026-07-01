# 🍃 What is Cullify?

**Cullify** is a client-side mod that culls decorative vegetation blocks (such as grass, flowers, ferns, algae, and kelp) based on distance from the player and customizable geometric shapes. By preventing distant or non-visible vegetation blocks from rendering, it reduces rendering load (vertex throughput and draw calls) without altering or removing the actual blocks in the world.

![](https://media.forgecdn.net/attachments/description/1582707/description_05ea2d15-cd00-4163-8d3a-6ca29b19e3a0.png)

---

## ✨ Features

* ⚙️ **Vegetation Culling** — Reduces rendering load by hiding vegetation blocks that are beyond a configured distance.
* 📐 **8 Geometric Culling Shapes** — Customizable boundaries to control culling behavior around the player.
* 📉 **LOD Density Filter** — Deterministic, hash-based density filtering that thins out outer vegetation (75%, 50%, or 25%) to save CPU overhead near the boundary.
* 🌿 **Double-Tall Vegetation Support** — Synchronizes culling between the bottom and top halves of double-tall blocks (like sunflowers or tall grass) to prevent visual discrepancies.
* ⚙️ **In-Game Config Menu** — A configuration screen accessible via the `K` keybind, chat commands, or the NeoForge mods menu.
* 📊 **Real-Time Statistics HUD** — On-screen overlay tracking culled blocks, water replacements, chunk updates, and draw calls.
* 🧪 **Native Sodium Compatibility** — Direct hooks into Sodium's internal LevelSlice rendering pipeline and Sodium Options API.

---

## 📐 Geometric Culling Shapes

Unlike traditional culling mods, Cullify offers flexible mathematical boundary checks on both the horizontal plane (X/Z) and the vertical axis (Y):

| Icon | Shape | Geometry Mode | Description |
| :---: | :--- | :--- | :--- |
| 🟢 | **Sphere** | 3D Sphere | Culls blocks outside a 3D spherical radius. |
| 🌀 | **Cylinder** | Cylinder | Culls blocks outside a horizontal circular radius (infinite Y). |
| 📦 | **Box** | 3D Box | Culls blocks outside a 3D cubical bounding box. |
| 🔺 | **Triangle** | Triangle | Culls blocks outside an equilateral triangular boundary centered on the player. |
| ⬡ | **Hexagon** | Hexagon | Culls blocks outside a hexagonal boundary. |
| ⭐ | **Star** | Star | Culls blocks outside a 6-point star (union of two intersecting triangles). |
| ⏹️ | **Square** | Square | Culls blocks outside a flat square grid. |
| 🔵 | **Circle** | Circle | Culls blocks outside a flat horizontal circular boundary. |

---

## 🎮 Controls & Commands

Configuring and inspecting Cullify in-game is straightforward:

### ⌨️ Keybinds
* `K` — Opens the custom Cullify GUI Config Screen.

### 💬 Chat Commands
* `/cullify menu` — Opens the configuration GUI menu.
* `/cullify benchmark` — Performs a benchmark of the mod and displays the results in chat.
* `/cullify debug` — Toggles the real-time statistics HUD overlay.
* `/cullify stats` — Prints current performance statistics directly into the chat.
* `/cullify verbose` — Toggles verbose logging for debugging.
* `/cullify reload` — Forces a complete reload of the client level renderer chunks.

---

## 🔌 Compatibility & Requirements

* **Sodium / Embeddium:** Fully integrated. Hooks directly into Sodium's internal LevelSlice rendering pipeline to batch-cull blocks in MDI (*Multi-Draw Indirect*) buffers. The Culling Shape options and configurations are also registered directly inside Sodium's Video Settings menu!
* **Vanilla Renderer:** Fallback compatibility included for the standard Minecraft block rendering pipeline.
* **🔒 100% Client-Side:** This mod runs entirely on your client. You can join any server (Vanilla, Spigot, Forge, Paper, etc.) without any issues!

---

## 📄 License & Modpack Usage

This mod is available under the **GNU General Public License v3 (GPL-3.0)**.

> 📦 **Modpack Permissions:** Feel free to include Cullify in any of your modpacks on CurseForge, Modrinth, or private launchers! Credits are appreciated but not required.

---

## ⚡ Vibe Coding

This mod was built 100% using **Vibe Coding**! Developed entirely through AI-assisted pair programming, dynamic prompt iterations, and automated compilation loops. *Let the vibes render!*

---

### Developed by Fluxsyum Studios

<img src="https://media.forgecdn.net/attachments/description/1591619/description_c030207b-1fd1-4d6b-a63f-110a6857477a.png" width="128" height="128"> <img src="https://media.forgecdn.net/attachments/description/1591619/description_970fde9d-b61f-455d-8366-f85cf6fe0ca4.png" width="128" height="128">