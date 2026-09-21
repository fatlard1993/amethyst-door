package justfatlard.amethyst_door;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Relative;

/**
 * Asking to grow your geode into somebody else's.
 *
 * <p>{@code /geode join <player>} asks; the other player's {@code /geode accept} answers, within
 * two minutes. The asker's geode is then cut again in the answerer's plot, grown into their sphere
 * at a bearing and a height of its own, everything in it carried across, and the two rooms open
 * into each other where the crystal walls meet. Each still has their own door into their own
 * room. A geode that has already grown others onto it stays where it is - it is the middle of
 * something - so only a solitary geode can go and join. There is no growing apart: crystal
 * that has grown together stays that way, and the ask says so before anybody answers it.
 */
public final class GeodeCommands {
	private GeodeCommands() {}

	/** Ticks an ask stands before it lapses. */
	private static final long ASK_TICKS = 20L * 120L;

	/**
	 * One ask, waiting on everyone in the cluster.
	 *
	 * <p>A cluster is a community, and a new room opening into it is everybody's business: every
	 * geode already grown there has to say yes, and any one of them can say no. Kept by the
	 * asker, and looked up from whichever member answers.
	 */
	private static final class Ask {
		final UUID asker;
		final UUID onto;
		final int plot;
		final Set<UUID> waitingOn;
		final long until;

		Ask(UUID asker, UUID onto, int plot, Set<UUID> waitingOn, long until) {
			this.asker = asker;
			this.onto = onto;
			this.plot = plot;
			this.waitingOn = waitingOn;
			this.until = until;
		}
	}

	/** By each member asked, all pointing at the one ask. */
	private static final Map<UUID, Ask> asks = new ConcurrentHashMap<>();

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("geode")
			.then(Commands.literal("join")
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> ask(context.getSource().getPlayerOrException(),
						EntityArgument.getPlayer(context, "player")))))
			.then(Commands.literal("accept")
				.executes(context -> accept(context.getSource().getPlayerOrException())))
			.then(Commands.literal("deny")
				.executes(context -> deny(context.getSource().getPlayerOrException())))
			.then(Commands.literal("who")
				.executes(context -> who(context.getSource().getPlayerOrException())))
			.then(Commands.literal("mend")
				.executes(context -> mend(context.getSource().getPlayerOrException())))
			// The op's way through, for when somebody in the cluster cannot be reached to answer.
			.then(Commands.literal("force")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("geode", EntityArgument.player())
					.then(Commands.argument("onto", EntityArgument.player())
						.executes(context -> force(context.getSource(),
							EntityArgument.getPlayer(context, "geode"), EntityArgument.getPlayer(context, "onto")))))));
	}

	private static int ask(ServerPlayer asker, ServerPlayer host) {
		if (asker == host) return fail(asker, "That is your own geode.");
		PocketVault vault = PocketVault.get(asker.level().getServer());
		if (!vault.hasPlot(asker.getUUID())) return fail(asker, "You have no geode yet. Go through an amethyst door first.");
		if (!vault.hasPlot(host.getUUID())) return fail(asker, host.getGameProfile().name() + " has no geode yet.");
		if (vault.plotFor(asker.getUUID()) == vault.plotFor(host.getUUID())) return fail(asker, "Your geodes are already grown together.");
		if (vault.membersOf(vault.plotFor(asker.getUUID())).size() > 1) {
			return fail(asker, "Other geodes have grown onto yours; it is the middle of something now and stays.");
		}

		// Everyone in the cluster answers, so everyone has to be here to be asked.
		int plot = vault.plotFor(host.getUUID());
		List<ServerPlayer> members = new ArrayList<>();
		for (PocketVault.Member member : vault.membersOf(plot)) {
			ServerPlayer there = asker.level().getServer().getPlayerList().getPlayer(member.player());
			if (there == null) {
				String name = asker.level().getServer().services().nameToIdCache().get(member.player())
					.map(net.minecraft.server.players.NameAndId::name).orElse("someone in that cluster");
				return fail(asker, name + " is grown into that cluster and is not here to be asked.");
			}
			members.add(there);
			if (asks.containsKey(there.getUUID())) return fail(asker, "That cluster is already being asked. Wait for that to settle.");
		}

		Set<UUID> waitingOn = ConcurrentHashMap.newKeySet();
		for (ServerPlayer member : members) waitingOn.add(member.getUUID());
		Ask ask = new Ask(asker.getUUID(), host.getUUID(), plot, waitingOn, asker.level().getGameTime() + ASK_TICKS);
		for (ServerPlayer member : members) asks.put(member.getUUID(), ask);

		String who = asker.getGameProfile().name();
		asker.sendSystemMessage(Component.literal("Asked " + host.getGameProfile().name() + " to grow your geode into theirs."
			+ (members.size() > 1 ? " Everyone grown into that cluster has to agree." : "") + " If they do, that is for good.")
			.withStyle(ChatFormatting.LIGHT_PURPLE));

		// Asked in the tray rather than in chat. This is consent for something that cannot be
		// undone, put to everyone grown into the cluster at once, and it used to be two lines of
		// coloured text that the next thing anybody said pushed out of sight.
		for (ServerPlayer member : members) {
			String onto = member == host ? "yours" : host.getGameProfile().name() + "\'s geode, in your cluster";
			PandoricalApi.notices().offer(member, new NoticeApi.Notice(
				askId(ask), NOTICE_KIND, "minecraft:amethyst_cluster",
				who + " asks to grow their geode into " + onto + " - this cannot be undone"
					+ (members.size() > 1 ? ", and everyone in the cluster has to agree" : ""),
				List.of(new NoticeApi.Choice("accept", "minecraft:amethyst_shard", "Accept"),
					new NoticeApi.Choice("deny", "minecraft:barrier", "Deny")),
				(int) (ASK_TICKS / 20L)));
		}
		return 1;
	}

	public static final String NOTICE_KIND = "amethyst-door:grow";

	/** One id for the whole ask, so every member answers the same question. */
	private static String askId(Ask ask) {
		return "grow-" + ask.asker;
	}

	/** Wired once, from the mod's own init. */
	public static void listen() {
		PandoricalApi.notices().onChoice(NOTICE_KIND, (player, noticeId, choiceId) -> {
			try {
				if ("accept".equals(choiceId)) accept(player);
				else deny(player);
			} catch (CommandSyntaxException e) {
				fail(player, "That could not be done: " + e.getMessage());
			}
		});
		// A lapsed ask is already refused by accept(); this only takes the question away, so nobody
		// is left looking at an invitation that stopped meaning anything two minutes ago.
		PandoricalApi.notices().onExpiry(NOTICE_KIND, (player, noticeId) -> {
			Ask ask = asks.get(player.getUUID());
			if (ask != null && askId(ask).equals(noticeId)) asks.remove(player.getUUID());
		});
	}

	/**
	 * The ask is over: drop it, and take the question off everyone still looking at it.
	 *
	 * @param here anybody in the ask, only to reach the server through
	 */
	private static void forget(ServerPlayer here, Ask ask) {
		var players = here.level().getServer().getPlayerList();
		for (UUID id : List.copyOf(asks.keySet())) {
			if (asks.get(id) != ask) continue;
			ServerPlayer told = players.getPlayer(id);
			if (told != null) PandoricalApi.notices().withdraw(told, NOTICE_KIND, askId(ask));
		}
		asks.values().removeIf(other -> other == ask);
	}

	private static int deny(ServerPlayer member) {
		Ask ask = asks.get(member.getUUID());
		if (ask == null) return fail(member, "Nobody is asking.");
		forget(member, ask);
		var players = member.level().getServer().getPlayerList();
		ServerPlayer asker = players.getPlayer(ask.asker);
		if (asker != null) asker.sendSystemMessage(Component.literal(member.getGameProfile().name() + " would rather not.").withStyle(ChatFormatting.GRAY));
		for (UUID other : ask.waitingOn) {
			ServerPlayer told = players.getPlayer(other);
			if (told != null && told != member) told.sendSystemMessage(Component.literal(member.getGameProfile().name() + " said no; nothing changes.").withStyle(ChatFormatting.GRAY));
		}
		member.sendSystemMessage(Component.literal("Declined.").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int accept(ServerPlayer member) throws CommandSyntaxException {
		Ask ask = asks.get(member.getUUID());
		if (ask == null) return fail(member, "Nobody is asking.");
		if (ask.until < member.level().getGameTime()) {
			forget(member, ask);
			return fail(member, "That ask has lapsed.");
		}
		var players = member.level().getServer().getPlayerList();
		ServerPlayer asker = players.getPlayer(ask.asker);
		if (asker == null) {
			forget(member, ask);
			return fail(member, "They have gone.");
		}

		ask.waitingOn.remove(member.getUUID());
		asks.remove(member.getUUID());
		if (!ask.waitingOn.isEmpty()) {
			member.sendSystemMessage(Component.literal("Agreed; waiting on " + ask.waitingOn.size() + " more.").withStyle(ChatFormatting.LIGHT_PURPLE));
			asker.sendSystemMessage(Component.literal(member.getGameProfile().name() + " agreed; " + ask.waitingOn.size() + " more to go.").withStyle(ChatFormatting.LIGHT_PURPLE));
			return 1;
		}

		ServerPlayer host = players.getPlayer(ask.onto);
		if (host == null) return fail(member, "The geode to grow onto has gone.");
		return growInto(member, asker, host, ask.plot);
	}

	/**
	 * An op grows one geode into another, asking nobody. For a cluster with somebody in it who
	 * cannot be reached to answer, and for putting right whatever else has gone wrong.
	 */
	private static int force(CommandSourceStack source, ServerPlayer asker, ServerPlayer host) {
		PocketVault vault = PocketVault.get(source.getServer());
		if (asker == host) {
			source.sendFailure(Component.literal("Those are the same geode."));
			return 0;
		}
		if (!vault.hasPlot(asker.getUUID()) || !vault.hasPlot(host.getUUID())) {
			source.sendFailure(Component.literal("Both need a geode first: through an amethyst door once."));
			return 0;
		}
		if (vault.plotFor(asker.getUUID()) == vault.plotFor(host.getUUID())) {
			source.sendFailure(Component.literal("Those geodes are already grown together."));
			return 0;
		}
		for (Ask ask : List.copyOf(asks.values())) {
			if (ask.asker.equals(asker.getUUID())) forget(asker, ask);
		}
		ServerPlayer told = source.getEntity() instanceof ServerPlayer op ? op : host;
		int done = growInto(told, asker, host, vault.plotFor(host.getUUID()));
		if (done > 0) source.sendSuccess(() -> Component.literal("Grew " + asker.getGameProfile().name()
			+ "\'s geode into " + host.getGameProfile().name() + "\'s."), true);
		return done;
	}

	/** The growing itself, once everyone who had to agree has, or an op has said so. */
	private static int growInto(ServerPlayer told, ServerPlayer asker, ServerPlayer host, int plot) {
		ServerLevel pocket = Pocket.level(told.level().getServer());
		if (pocket == null) return fail(told, "The geode dimension is not loaded.");
		PocketVault vault = PocketVault.get(told.level().getServer());
		var players = told.level().getServer().getPlayerList();
		if (vault.membersOf(vault.plotFor(asker.getUUID())).size() > 1) return fail(told, "Their geode has grown others onto it since; it stays.");
		if (vault.plotFor(host.getUUID()) != plot) return fail(told, "That cluster has changed since; ask again.");

		List<Pocket.Site> cluster = Pocket.clusterOf(vault, plot);
		Pocket.Site onto = Pocket.siteOf(vault, host.getUUID());
		BlockPos centre = grow(cluster, onto, RandomSource.create(asker.getUUID().getLeastSignificantBits() ^ told.level().getGameTime()));
		if (centre == null) return fail(told, "There is no room left on that geode for another to grow.");

		// Cut the new room before anything is carried into it: the cluster is laid with the new
		// geode counted, so the window between the two opens and the old shell gives way to it.
		Pocket.Site was = Pocket.siteOf(vault, asker.getUUID());
		Direction doorSide = awayFrom(onto.centre(), centre);
		vault.settle(asker.getUUID(), plot, centre.subtract(Pocket.centreOf(plot)), doorSide);
		List<Pocket.Site> grown = Pocket.clusterOf(vault, plot);
		Geode.build(pocket, grown);
		Geode.moveHollow(pocket, was.centre(), centre);
		Geode.build(pocket, grown);

		Pocket.Site now = Pocket.siteOf(vault, asker.getUUID());
		if (asker.level().dimension().equals(Pocket.DIMENSION)) {
			BlockPos arrival = now.arrival();
			asker.teleportTo(pocket, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
				java.util.Set.<Relative>of(), now.arrivalYaw(), 0F, true);
		}
		pocket.playSound(null, centre, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
			net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);

		String who = asker.getGameProfile().name();
		asker.sendSystemMessage(Component.literal("Your geode has grown into " + host.getGameProfile().name() + "\'s. The way through is where the crystal opens.").withStyle(ChatFormatting.LIGHT_PURPLE));
		for (Pocket.Site site : grown) {
			if (site.owner().equals(asker.getUUID())) continue;
			ServerPlayer member = players.getPlayer(site.owner());
			if (member != null) member.sendSystemMessage(Component.literal(who + "\'s geode has grown into the cluster.").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return 1;
	}

	/** Double chests that came apart in a move, put back together, for the cluster the player is grown into. */
	private static int mend(ServerPlayer player) {
		PocketVault vault = PocketVault.get(player.level().getServer());
		if (!vault.hasPlot(player.getUUID())) return fail(player, "You have no geode yet.");
		ServerLevel pocket = Pocket.level(player.level().getServer());
		if (pocket == null) return fail(player, "The geode dimension is not loaded.");
		int mended = 0;
		for (Pocket.Site site : Pocket.clusterOf(vault, player.getUUID())) mended += Geode.mendChests(pocket, site.centre());
		final int count = mended;
		player.sendSystemMessage(Component.literal(count == 0 ? "Every chest is whole." : "Put " + count + " chest" + (count == 1 ? "" : "s") + " back together.").withStyle(ChatFormatting.LIGHT_PURPLE));
		return 1;
	}

	private static int who(ServerPlayer player) {
		PocketVault vault = PocketVault.get(player.level().getServer());
		if (!vault.hasPlot(player.getUUID())) return fail(player, "You have no geode yet.");
		List<Pocket.Site> cluster = Pocket.clusterOf(vault, player.getUUID());
		if (cluster.size() == 1) {
			player.sendSystemMessage(Component.literal("Your geode stands alone.").withStyle(ChatFormatting.GRAY));
			return 1;
		}
		StringBuilder names = new StringBuilder();
		for (Pocket.Site site : cluster) {
			var profile = player.level().getServer().services().nameToIdCache().get(site.owner());
			String name = profile.map(net.minecraft.server.players.NameAndId::name).orElse("someone");
			if (!names.isEmpty()) names.append(", ");
			names.append(name);
		}
		player.sendSystemMessage(Component.literal("Grown together: " + names).withStyle(ChatFormatting.LIGHT_PURPLE));
		return 1;
	}

	/**
	 * Where a new geode grows onto this one.
	 *
	 * <p>Close enough that the two hollows share a window - centres seventeen apart, give or
	 * take, against an inner radius of nine - at a bearing of its own and a few blocks up or
	 * down, so the cluster comes out as crystals do and not as beads on a string. Tried at random
	 * until one fits: it may lean on the geode it grows onto and touch the others, but not grow
	 * into any of them, or their rooms would open too.
	 */
	private static BlockPos grow(List<Pocket.Site> cluster, Pocket.Site onto, RandomSource random) {
		for (int attempt = 0; attempt < 96; attempt++) {
			double bearing = random.nextDouble() * Math.PI * 2.0;
			int rise = random.nextInt(9) - 4;
			double apart = 16.0 + random.nextDouble() * 2.0;
			double flat = Math.sqrt(Math.max(1.0, apart * apart - rise * rise));
			BlockPos centre = onto.centre().offset(
				(int) Math.round(Math.cos(bearing) * flat), rise, (int) Math.round(Math.sin(bearing) * flat));
			if (Math.abs(centre.getY() - Pocket.CENTRE_Y) > 12) continue;
			// The door goes in the wall facing away from what it grows onto, and that wall has to
			// be clear of every other geode in the cluster, or the way out would be cut into
			// somebody's shell.
			BlockPos door = new Pocket.Site(null, centre, awayFrom(onto.centre(), centre)).door();
			boolean fits = true;
			for (Pocket.Site other : cluster) {
				if (other.owner().equals(onto.owner())) continue;
				if (Math.sqrt(other.centre().distSqr(centre)) < 2 * Pocket.INNER_RADIUS + 1
						|| other.distance(door) <= Pocket.OUTER_RADIUS + 2) {
					fits = false;
					break;
				}
			}
			if (fits && onto.distance(door) > Pocket.OUTER_RADIUS + 1) return centre;
		}
		return null;
	}

	/** The wall a grown geode's door goes in: the one facing away from what it grew onto. */
	private static Direction awayFrom(BlockPos onto, BlockPos centre) {
		int dx = centre.getX() - onto.getX();
		int dz = centre.getZ() - onto.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static int fail(ServerPlayer player, String said) {
		player.sendSystemMessage(Component.literal(said).withStyle(ChatFormatting.RED));
		return 0;
	}
}
