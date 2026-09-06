# Amethyst Door

A Fabric mod that adds a door of amethyst, and a geode of your own behind it.

## What This Mod Does

Six amethyst blocks make a door. Place it anywhere, right-click it, and you are inside a hollow
amethyst geode that nobody else can reach. Build in it as you like. The door on the far side brings
you back to the exact spot you left from.

## Your Geode

Everyone gets their own, and gets it the first time they open a door - it is cut then, and it stays
cut. Layered the way a real geode is, outside in: basalt skin, calcite rind, amethyst you can
actually see, some of it budding. What grows off it grows into the hollow and is shell too, bud
and cluster alike: a cluster that could be broken over a budding block that could not would be a
shard farm, and the geode is a room. A floor is laid across the bottom third, solid crystal from there
down to the shell, because a sphere is a lovely shape to look at and an awful one to put a chest on.
The door is set into the north wall, in a frame cut for it, and you arrive three blocks in front
of it, facing it.

**The shell does not give.** Everything in the hollow is yours to break, place, flood or fill.
Everything from the amethyst outwards is not, and neither is the door, whoever you are: only
creative mode gets through, for repairs. That is not a rule for its own sake: on the other side of
the shell is empty space with no floor, and a hole in it is a hole somebody falls out of and cannot
climb back into.

## The Way Back

The door inside is permanent and cannot be broken. Should it ever be missing all the same, the
geode is cut again the next time its owner arrives or loads in, and they are stood on the floor in
front of the door. An earlier build laid the floor, the door and the doorstep at the bottom of the
outer skin, outside the geode, and this is what got everyone it stranded back out.

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

## Development

Installing, the map of the source and the art pipeline are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
