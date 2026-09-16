package justfatlard.amethyst_door;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * Cutting a cluster of geodes, once, or again.
 *
 * <p>Layered the way a real one is, outside in: basalt skin, calcite rind, then the amethyst you
 * actually see from inside. A floor is cut across the bottom third of each, because a sphere is a
 * lovely shape to look at and an awful one to put a chest on.
 *
 * <p>The whole cluster is cut together, because its geodes grow into one another: a block is
 * hollow if it is above the floor inside any of them, floor if it is inside any of them below
 * that, and shell if it is inside any skin and neither of those. Cut one at a time, each would
 * wall the others up. Cutting again is safe: shell and floor are rewritten to what they should
 * be, and anything an owner built in a hollow stays - only shell stone standing where hollow
 * now belongs, where a new neighbour has opened a window into a room, is cleared.
 */
public final class Geode {
	private Geode() {}

	/** Roughly one in this many amethyst blocks comes up budding, for the look and the drops. */
	private static final int BUDDING_IN = 14;

	/** What the cutter lays, and so what it may take back when a hollow grows over it. */
	private static final Set<Block> STONE = Set.of(
		Blocks.SMOOTH_BASALT, Blocks.CALCITE, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST,
		Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER);

	/** Whether this is stone the cutter lays, as against something an owner put there. */
	static boolean laid(BlockState state) {
		return STONE.contains(state.getBlock());
	}

	/** Cut every geode of this cluster, doors and all. */
	public static void build(ServerLevel level, List<Pocket.Site> cluster) {
		if (cluster.isEmpty()) return;
		int reach = Pocket.OUTER_RADIUS + 1;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

		for (Pocket.Site site : cluster) {
			BlockPos centre = site.centre();
			RandomSource random = RandomSource.create(centre.asLong() * 31L + 17L);
			for (int x = -reach; x <= reach; x++) {
				for (int y = -reach; y <= reach; y++) {
					for (int z = -reach; z <= reach; z++) {
						at.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
						BlockState want = wanted(cluster, at, random);
						if (want == null) continue;
						BlockState have = level.getBlockState(at);
						if (want.isAir()) {
							// Hollow: only stone the cutter laid gives way to it.
							if (STONE.contains(have.getBlock())) level.setBlock(at, want, Block.UPDATE_CLIENTS);
							continue;
						}
						level.setBlock(at, want, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		for (Pocket.Site site : cluster) hangDoor(level, site);
	}

	/**
	 * What belongs at this block, for the cluster as a whole: air for a hollow, amethyst for a
	 * floor, a layer of shell by how deep it sits in the nearest skin, or null for open air
	 * outside every skin, which is left as it is.
	 */
	private static BlockState wanted(List<Pocket.Site> cluster, BlockPos pos, RandomSource random) {
		boolean inside = false;
		double nearest = Double.MAX_VALUE;
		for (Pocket.Site site : cluster) {
			double distance = site.distance(pos);
			nearest = Math.min(nearest, distance);
			if (site.insideHollow(pos)) return Blocks.AIR.defaultBlockState();
			if (site.insideAmethyst(pos)) inside = true;
		}
		if (inside) return Blocks.AMETHYST_BLOCK.defaultBlockState();
		if (nearest > Pocket.OUTER_RADIUS) return null;
		if (nearest >= Pocket.OUTER_RADIUS - 1) return Blocks.SMOOTH_BASALT.defaultBlockState();
		if (nearest >= Pocket.OUTER_RADIUS - 2) return Blocks.CALCITE.defaultBlockState();
		return random.nextInt(BUDDING_IN) == 0
			? Blocks.BUDDING_AMETHYST.defaultBlockState()
			: Blocks.AMETHYST_BLOCK.defaultBlockState();
	}

	/**
	 * The way out, which is the same door and is never removable, set into a frame cut for it.
	 *
	 * <p>The sphere is not trusted to make a doorway on its own: where the door stands the wall
	 * curves away, so the blocks either side, above and behind the door are laid as amethyst
	 * whatever the shell arithmetic made of them, and the sill under it too. Then the door goes
	 * in, and it is a door in a wall. The door is in whichever wall faces away from the cluster.
	 */
	private static void hangDoor(ServerLevel level, Pocket.Site site) {
		BlockPos bottom = site.door();
		Direction out = site.doorSide();
		BlockState frame = Blocks.AMETHYST_BLOCK.defaultBlockState();

		for (BlockPos half : new BlockPos[] {bottom, bottom.above()}) {
			level.setBlock(half.relative(out.getClockWise()), frame, Block.UPDATE_CLIENTS);
			level.setBlock(half.relative(out.getCounterClockWise()), frame, Block.UPDATE_CLIENTS);
			level.setBlock(half.relative(out), frame, Block.UPDATE_CLIENTS);
		}
		level.setBlock(bottom.above(2), frame, Block.UPDATE_CLIENTS);
		level.setBlock(bottom.below(), frame, Block.UPDATE_CLIENTS);

		// An earlier build stood the door a block further into the room. A geode cut then still
		// has it there, right in front of where the door goes now, so it is taken down first: a
		// door in the doorway of a door is no way out.
		BlockPos in = bottom.relative(out.getOpposite());
		for (BlockPos old : new BlockPos[] {in, in.above()}) {
			if (level.getBlockState(old).getBlock() instanceof AmethystDoorBlock) {
				level.setBlock(old, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		BlockState lower = Main.AMETHYST_DOOR.defaultBlockState()
			.setValue(DoorBlock.FACING, out)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
			.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);

		level.setBlock(bottom, lower, Block.UPDATE_ALL);
		level.setBlock(bottom.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
			Block.UPDATE_ALL);
	}

	/**
	 * Carry everything in a hollow to another, block for block, chests and their contents with
	 * it, and whatever was living or hanging in there. The old hollow is left bare.
	 *
	 * <p>Read whole, then written, then cleared, with no shape updates anywhere in it. Done a
	 * block at a time it took double chests apart: clearing one half told the other its partner
	 * had gone, vanilla made it a single on the spot, and a single is what then got carried.
	 */
	public static void moveHollow(ServerLevel level, BlockPos from, BlockPos to) {
		int r = Pocket.INNER_RADIUS;
		int floorAbove = -Pocket.FLOOR_DROP + 1;
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		record Carried(BlockPos offset, BlockState state, CompoundTag kept) {}
		List<Carried> carried = new java.util.ArrayList<>();

		for (int x = -r; x <= r; x++) {
			for (int y = floorAbove; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (Math.sqrt(x * x + y * y + z * z) >= r - 0.5) continue;
					BlockPos src = from.offset(x, y, z);
					BlockState state = level.getBlockState(src);
					if (state.isAir()) continue;
					BlockEntity entity = level.getBlockEntity(src);
					carried.add(new Carried(new BlockPos(x, y, z), state,
						entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
				}
			}
		}

		for (Carried piece : carried) {
			BlockPos src = from.offset(piece.offset());
			level.removeBlockEntity(src);
			level.setBlock(src, Blocks.AIR.defaultBlockState(), quiet);
		}
		for (Carried piece : carried) {
			BlockPos dst = to.offset(piece.offset());
			level.setBlock(dst, piece.state(), quiet);
			if (piece.kept() != null) {
				BlockEntity moved = BlockEntity.loadStatic(dst, piece.state(), piece.kept(), level.registryAccess());
				if (moved != null) level.setBlockEntity(moved);
			}
		}

		AABB hollow = new AABB(from).inflate(r);
		BlockPos shift = to.subtract(from);
		for (Entity entity : level.getEntities((Entity) null, hollow, e -> !(e instanceof Player))) {
			entity.teleportTo(level, entity.getX() + shift.getX(), entity.getY() + shift.getY(),
				entity.getZ() + shift.getZ(), Set.of(), entity.getYRot(), entity.getXRot(), false);
		}
	}

	/**
	 * Put double chests back together in a hollow.
	 *
	 * <p>A chest that says it is a left or right half, with a chest of its own facing standing
	 * where its partner should be but calling itself single, is a pair that came apart; the
	 * partner is told which half it is. Two singles side by side are left alone - a player can
	 * mean that.
	 */
	public static int mendChests(ServerLevel level, BlockPos centre) {
		int r = Pocket.INNER_RADIUS;
		int mended = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) continue;
			net.minecraft.world.level.block.state.properties.ChestType type = state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
			if (type == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) continue;
			Direction toward = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state);
			BlockPos partnerPos = pos.relative(toward);
			BlockState partner = level.getBlockState(partnerPos);
			if (!partner.is(state.getBlock())) continue;
			if (partner.getValue(net.minecraft.world.level.block.ChestBlock.FACING) != state.getValue(net.minecraft.world.level.block.ChestBlock.FACING)) continue;
			if (partner.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) continue;
			level.setBlock(partnerPos.immutable(), partner.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, type.getOpposite()),
				Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			mended++;
		}
		return mended;
	}
}
