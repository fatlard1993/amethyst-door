package justfatlard.amethyst_door;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 *
 * <p>A plot holds a cluster: one geode to begin with, and more as friends join. A geode that
 * joins another sits partly inside its sphere, a little higher or lower and off to one side, the
 * way crystals grow into each other; where the two hollows overlap there is a round window from
 * one room into the next. Every geode in a cluster is still its owner's - their door leads into
 * their own - and the cluster is what those rooms make together.
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
	 * The middle of a plot's first geode. The sphere is cut about this height and the plot's x and z.
	 *
	 * <p>Sixty-four for the floor and twelve of radius on top of it was the first arrangement,
	 * which put the floor at the very bottom of the outer skin: a disc laid across a point, and
	 * the door and the doorstep with it, all outside a geode that was nine blocks above them.
	 */
	public static final int CENTRE_Y = 76;

	/** How far below the middle the floor is laid: the bottom third, near enough. */
	public static final int FLOOR_DROP = 5;

	/** Outer skin of the shell. */
	public static final int OUTER_RADIUS = 12;
	/** Everything from here outwards is geode, and none of it can be broken. */
	public static final int INNER_RADIUS = 9;

	/**
	 * Where the door stands from a geode's middle, set into the wall, in the first block of
	 * amethyst. At door height the hollow reaches about seven and a half blocks out, so eight is
	 * the first block of shell, and the door replaces it - with shell on either side and behind,
	 * which is what makes it a door in a wall rather than a door standing in a room.
	 */
	private static final int DOOR_OUT = 8;

	/** One geode: whose, where its middle is, and which wall its door is in. */
	public record Site(UUID owner, BlockPos centre, Direction doorSide) {
		public int floorY() {
			return centre.getY() - FLOOR_DROP;
		}

		/** The bottom half of the way out. The top half sits directly above it. */
		public BlockPos door() {
			return new BlockPos(centre.getX(), floorY() + 1, centre.getZ()).relative(doorSide, DOOR_OUT);
		}

		/** Where an arriving owner stands: on the floor, three blocks in from the door. */
		public BlockPos arrival() {
			return door().relative(doorSide.getOpposite(), 3);
		}

		/** The way an arriving owner faces: into the room. */
		public float arrivalYaw() {
			return doorSide.getOpposite().toYRot();
		}

		/** The room: inside the amethyst and above the floor. The floor and under it are geode. */
		public boolean insideHollow(BlockPos pos) {
			return pos.getY() > floorY() && insideAmethyst(pos);
		}

		/** Within the inner surface of the shell, the floor and what is under it included. */
		public boolean insideAmethyst(BlockPos pos) {
			return distance(pos) < INNER_RADIUS - 0.5;
		}

		public boolean insideShell(BlockPos pos) {
			return distance(pos) <= OUTER_RADIUS;
		}

		public double distance(BlockPos pos) {
			return Math.sqrt(centre.distSqr(pos));
		}
	}

	public static ServerLevel level(MinecraftServer server) {
		return server.getLevel(DIMENSION);
	}

	/** The middle of the plot with this number: where its first geode is cut. */
	public static BlockPos centreOf(int plot) {
		return new BlockPos(
			(plot % PLOTS_PER_ROW) * PLOT_SPACING,
			CENTRE_Y,
			(plot / PLOTS_PER_ROW) * PLOT_SPACING);
	}

	/** Which plot this position belongs to, by the grid alone. */
	public static int plotAt(BlockPos pos) {
		int column = Math.round(pos.getX() / (float) PLOT_SPACING);
		int row = Math.round(pos.getZ() / (float) PLOT_SPACING);
		return row * PLOTS_PER_ROW + column;
	}

	/** Every geode standing in this player's cluster, theirs among them. */
	public static List<Site> clusterOf(PocketVault vault, UUID player) {
		return clusterOf(vault, vault.plotFor(player));
	}

	public static List<Site> clusterOf(PocketVault vault, int plot) {
		List<Site> sites = new ArrayList<>();
		BlockPos base = centreOf(plot);
		for (PocketVault.Member member : vault.membersOf(plot)) {
			sites.add(new Site(member.player(), base.offset(member.offset()), member.doorSide()));
		}
		return sites;
	}

	/** This player's own geode. */
	public static Site siteOf(PocketVault vault, UUID player) {
		int plot = vault.plotFor(player);
		PocketVault.Member member = vault.memberOf(player);
		return new Site(player, centreOf(plot).offset(member.offset()), member.doorSide());
	}

	/** Whether this block is part of a shell or floor-less rock, and so none of anybody's business to break. */
	public static boolean isShell(ServerLevel level, BlockPos pos) {
		if (!level.dimension().equals(DIMENSION)) return false;
		boolean floor = false;
		for (Site site : clusterOf(PocketVault.get(level.getServer()), plotAt(pos))) {
			if (site.insideHollow(pos)) return false;
			if (site.insideAmethyst(pos)) floor = true;
		}
		// A floor is geode where the cutter laid it; what an owner set into a hole in one is theirs.
		return !floor || Geode.laid(level.getBlockState(pos));
	}

	/** Whether this geode's way out is standing where it should be. */
	public static boolean hasDoor(ServerLevel level, Site site) {
		return level.getBlockState(site.door()).getBlock() instanceof AmethystDoorBlock;
	}
}
