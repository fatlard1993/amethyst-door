package justfatlard.amethyst_door;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Where everybody's geode is.
 *
 * <p>One dimension, not one per player. A dimension is a thing a world is built with, registered
 * when the server loads its data and not afterwards, so a dimension per player would mean a
 * server that has to be restarted before somebody can go through a door for the first time. What
 * a player actually wants is somewhere nobody else can reach or see, and a plot half a kilometre
 * from the nearest neighbour in an empty world is that, without pretending to be a dimension.
 */
public final class Pocket {
	private Pocket() {}

	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(
		net.minecraft.core.registries.Registries.DIMENSION,
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "pocket"));

	/** Far enough apart that no amount of building reaches the neighbours. */
	private static final int PLOT_SPACING = 512;
	/** Plots per row before the grid wraps, which only decides how the numbers look. */
	private static final int PLOTS_PER_ROW = 64;

	/**
	 * The middle of every geode. The sphere is cut about this height and the plot's x and z.
	 *
	 * <p>Sixty-four for the floor and twelve of radius on top of it was the first arrangement,
	 * which put the floor at the very bottom of the outer skin: a disc laid across a point, and
	 * the door and the doorstep with it, all outside a geode that was nine blocks above them.
	 */
	public static final int CENTRE_Y = 76;

	/** How far below the middle the floor is laid: the bottom third, near enough. */
	public static final int FLOOR_DROP = 5;

	public static final int FLOOR_Y = CENTRE_Y - FLOOR_DROP;

	/** Outer skin of the shell. */
	public static final int OUTER_RADIUS = 12;
	/** Everything from here outwards is geode, and none of it can be broken. */
	public static final int INNER_RADIUS = 9;

	public static ServerLevel level(MinecraftServer server) {
		return server.getLevel(DIMENSION);
	}

	/** The middle of the plot with this number. */
	public static BlockPos centreOf(int plot) {
		return new BlockPos(
			(plot % PLOTS_PER_ROW) * PLOT_SPACING,
			CENTRE_Y,
			(plot / PLOTS_PER_ROW) * PLOT_SPACING);
	}

	/**
	 * The plot whose geode this position is inside, whichever plot that is.
	 *
	 * <p>Worked out from the position rather than looked up, so anything asking "is this block
	 * part of somebody's geode" does not first have to know whose.
	 */
	public static BlockPos nearestCentre(BlockPos pos) {
		int x = Math.round(pos.getX() / (float) PLOT_SPACING) * PLOT_SPACING;
		int z = Math.round(pos.getZ() / (float) PLOT_SPACING) * PLOT_SPACING;

		return new BlockPos(x, CENTRE_Y, z);
	}

	/**
	 * Where the door stands from the middle, along z towards north: set into the wall, in the
	 * first block of amethyst. At door height the hollow reaches about seven and a half blocks
	 * out, so eight is the first block of shell, and the door replaces it - with shell on either
	 * side and behind, which is what makes it a door in a wall rather than a door standing in a
	 * room. Seven, where it used to be, left it a block clear of the curve with air at its sides.
	 */
	private static final int DOOR_OUT = 8;

	/** Where a player arriving in their geode should stand: on the floor, three blocks from the door. */
	public static BlockPos arrivalIn(int plot) {
		BlockPos centre = centreOf(plot);

		return new BlockPos(centre.getX(), FLOOR_Y + 1, centre.getZ() - DOOR_OUT + 3);
	}

	/** The bottom half of the way out. The top half sits directly above it. */
	public static BlockPos doorIn(int plot) {
		BlockPos centre = centreOf(plot);

		return new BlockPos(centre.getX(), FLOOR_Y + 1, centre.getZ() - DOOR_OUT);
	}

	/** Whether this plot's way out is standing where it should be. */
	public static boolean hasDoor(ServerLevel level, int plot) {
		return level.getBlockState(doorIn(plot)).getBlock() instanceof AmethystDoorBlock;
	}

	/** Whether this block is part of the shell, and so none of anybody's business to break. */
	public static boolean isShell(BlockPos pos) {
		BlockPos centre = nearestCentre(pos);
		double distance = Math.sqrt(centre.distSqr(pos));

		return distance >= INNER_RADIUS - 0.5;
	}
}
