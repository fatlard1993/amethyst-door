package justfatlard.amethyst_door;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Whose plot is whose, and the doorstep each of them last stepped off. */
public final class PocketVault extends SavedData {
	private static final String STORAGE_KEY = "amethyst_door_pockets";

	/**
	 * Where a player was standing when they went in.
	 *
	 * <p>The player's own position, deliberately, not the door's. The door is a block somebody
	 * else can mine while you are inside, and coming out of a permanent exit into a hole where
	 * the world used to be is the one way this could strand somebody.
	 */
	public record Doorstep(ResourceKey<Level> dimension, double x, double y, double z,
			float yaw, float pitch) {
		static final Codec<Doorstep> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Doorstep::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(Doorstep::x),
			Codec.DOUBLE.fieldOf("y").forGetter(Doorstep::y),
			Codec.DOUBLE.fieldOf("z").forGetter(Doorstep::z),
			Codec.FLOAT.fieldOf("yaw").forGetter(Doorstep::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(Doorstep::pitch)
		).apply(instance, Doorstep::new));
	}

	/** Where a player's geode stands within their plot, and which wall its door is in. */
	public record Member(UUID player, net.minecraft.core.BlockPos offset, net.minecraft.core.Direction doorSide) {
		static final Member FIRST_SHAPE = new Member(null, net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.NORTH);
	}

	private record Entry(UUID player, int plot, java.util.Optional<Doorstep> doorstep,
			java.util.Optional<net.minecraft.core.BlockPos> offset, java.util.Optional<net.minecraft.core.Direction> doorSide) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
			Codec.INT.fieldOf("plot").forGetter(Entry::plot),
			Doorstep.CODEC.optionalFieldOf("doorstep").forGetter(Entry::doorstep),
			net.minecraft.core.BlockPos.CODEC.optionalFieldOf("offset").forGetter(Entry::offset),
			net.minecraft.core.Direction.CODEC.optionalFieldOf("door_side").forGetter(Entry::doorSide)
		).apply(instance, Entry::new));
	}

	private record Stored(List<Entry> entries, int nextPlot) {
		static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Entry.CODEC.listOf().fieldOf("entries").forGetter(Stored::entries),
			Codec.INT.fieldOf("next_plot").forGetter(Stored::nextPlot)
		).apply(instance, Stored::new));
	}

	public static final Codec<PocketVault> CODEC =
		Stored.CODEC.xmap(PocketVault::fromStored, PocketVault::toStored);

	private static final SavedDataType<PocketVault> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), PocketVault::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, Integer> plots = new HashMap<>();
	private final Map<UUID, Doorstep> doorsteps = new HashMap<>();
	/** Only for geodes that are not their plot's first: the first stands at the middle with its door north. */
	private final Map<UUID, Member> members = new HashMap<>();
	private int nextPlot = 0;

	public static PocketVault get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * This player's plot, handing out a new one the first time they ask.
	 *
	 * <p>Handed out in order rather than hashed off the player's id, because a hash collides and
	 * two people sharing a geode is the one outcome this cannot recover from: by the time anybody
	 * notices, both have built in it.
	 */
	public int plotFor(UUID player) {
		Integer mine = this.plots.get(player);
		if (mine != null) return mine;

		int plot = this.nextPlot++;
		this.plots.put(player, plot);
		this.setDirty();
		return plot;
	}

	/** Whether this player has ever been through, which is also whether their geode is built. */
	public boolean hasPlot(UUID player) {
		return this.plots.containsKey(player);
	}

	/** This player's place in their plot. */
	public Member memberOf(UUID player) {
		Member member = this.members.get(player);
		return member != null ? member : new Member(player, net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.NORTH);
	}

	/** Everyone whose geode stands in this plot. */
	public List<Member> membersOf(int plot) {
		List<Member> found = new java.util.ArrayList<>();
		for (Map.Entry<UUID, Integer> entry : this.plots.entrySet()) {
			if (entry.getValue() == plot) found.add(memberOf(entry.getKey()));
		}
		return found;
	}

	/** Whether this geode stands at its plot's middle: the one the plot was cut for. */
	public boolean isFirst(UUID player) {
		return !this.members.containsKey(player);
	}

	/** Move this player's geode into another plot, at this offset from its middle, door in this wall. */
	public void settle(UUID player, int plot, net.minecraft.core.BlockPos offset, net.minecraft.core.Direction doorSide) {
		this.plots.put(player, plot);
		this.members.put(player, new Member(player, offset.immutable(), doorSide));
		this.setDirty();
	}

	public Doorstep doorstepOf(UUID player) {
		return this.doorsteps.get(player);
	}

	public void rememberDoorstep(UUID player, Doorstep doorstep) {
		this.doorsteps.put(player, doorstep);
		this.setDirty();
	}

	private static PocketVault fromStored(Stored stored) {
		PocketVault vault = new PocketVault();
		for (Entry entry : stored.entries()) {
			vault.plots.put(entry.player(), entry.plot());
			entry.doorstep().ifPresent(step -> vault.doorsteps.put(entry.player(), step));
			entry.offset().ifPresent(offset -> vault.members.put(entry.player(),
				new Member(entry.player(), offset, entry.doorSide().orElse(net.minecraft.core.Direction.NORTH))));
		}
		vault.nextPlot = stored.nextPlot();
		return vault;
	}

	private static List<Entry> entriesOf(PocketVault vault) {
		return vault.plots.entrySet().stream()
			.map(entry -> {
				Member member = vault.members.get(entry.getKey());
				return new Entry(entry.getKey(), entry.getValue(),
					java.util.Optional.ofNullable(vault.doorsteps.get(entry.getKey())),
					java.util.Optional.ofNullable(member == null ? null : member.offset()),
					java.util.Optional.ofNullable(member == null ? null : member.doorSide()));
			})
			.toList();
	}

	private static Stored toStored(PocketVault vault) {
		return new Stored(entriesOf(vault), vault.nextPlot);
	}
}
