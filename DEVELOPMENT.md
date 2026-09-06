# Amethyst Door - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients
need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API)
and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; the door, and what the geode refuses to let go of |
| `AmethystDoorBlock.java` | A door that does not open |
| `Pocket.java` | Where everybody's geode is |
| `PocketVault.java` | Whose plot is whose, and the doorstep each of them stepped off |
| `Geode.java` | Building somebody's geode, once |

## Art

`generate_icon.py` and `generate_textures.py` cut the mod's icon, door and item textures out of the
vanilla jar. Both are deterministic; re-run either after a Minecraft version bump.
