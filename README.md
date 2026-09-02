# Amethyst Door

A Fabric mod that adds a door of amethyst, and a geode of your own behind it.

## What This Mod Does

Six amethyst blocks make a door. Place it anywhere, right-click it, and you are inside a hollow
amethyst geode that nobody else can reach. Build in it as you like. The door on the far side brings
you back to the exact spot you left from.

## Your Geode

Everyone gets their own, and gets it the first time they open a door - it is cut then, and it stays
cut. Layered the way a real geode is, outside in: basalt skin, calcite rind, amethyst you can
actually see, some of it budding. A floor is laid across the bottom third, because a sphere is a
lovely shape to look at and an awful one to put a chest on.

**The shell does not give.** Everything in the hollow is yours to break, place, flood or fill.
Everything from the amethyst outwards is not, and neither is the door. That is not a rule for its
own sake: on the other side of the shell is empty space with no floor, and a hole in it is a hole
somebody falls out of and cannot climb back into.

## The Way Back

The door inside is permanent and cannot be broken.

**It returns you to where you were standing, not to where the door was.** The door you came in by is
a block, and a block can be mined by somebody else while you are inside. Recording the doorstep
rather than the doorframe is what stops that stranding anyone.

## One Dimension, Not One Each

"A pocket dimension each" is the feeling; a plot each is the implementation. Dimensions are
registered when a server loads its data and not afterwards, so one per player would mean restarting
the server before anybody could open their first door. Plots sit half a kilometre apart in an empty
world, which is out of sight and out of reach of every other plot, and needs nothing of the sort.

## Pandorical

Amethyst Door registers its block and item models through Pandorical's content sync.

**The Pandorical mod must be installed client-side** to see the door rendered. Without it the door
still works - it can be crafted, placed and walked through - but a connecting client does not see
it correctly.

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

## License

MIT, see [LICENSE](LICENSE).
