package net.conczin.mca.entity.interaction;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** The server-owned dispatch boundary for menu and authored dialogue commands. */
public final class InteractionCommands {
    public enum Source {
        MENU, DIALOGUE
    }

    public record Context(ServerPlayer player, VillagerLike<?> villager, UUID interactionId,
                          Source source, String command) {
    }

    private record Entry(Predicate<Context> allowed,
                         Function<Context, VillagerCommandHandler.DialogueCommandResult> executor) {
    }

    private static final Map<ResourceLocation, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> LEGACY_ALIASES = new ConcurrentHashMap<>();

    static {
        for (String name : new String[]{
                "pick_up", "ridehorse", "sethome", "gohome", "setworkplace", "trade", "inventory", "gift",
                "execute", "pardon", "stopworking", "armor", "profession.none", "profession.guard",
                "profession.archer", "follow", "stay", "move", "fishing", "harvesting", "hunting", "chopping"
        }) {
            builtin(name, true, false);
        }
        for (String name : new String[]{
                "adopt", "procreate", "divorcePapers", "divorceConfirm", "stay_in_village", "hire_short",
                "hire_long", "apologize", "location"
        }) {
            builtin(name, false, true);
        }
    }

    private InteractionCommands() {
    }

    /** Loads builtins before external common-initialization registrations. */
    public static void bootstrap() {
        // The static initializer owns the builtin definitions.
    }

    private static void builtin(String alias, boolean menu, boolean dialogue) {
        ResourceLocation id = MCA.locate(switch (alias) {
            case "divorceConfirm" -> "divorce_confirm";
            case "divorcePapers" -> "divorce_papers";
            default -> alias;
        });
        if (LEGACY_ALIASES.putIfAbsent(alias, id) != null) {
            throw new IllegalStateException("Duplicate builtin command alias: " + alias);
        }
        Entry entry = new Entry(context -> switch (context.source()) {
            case MENU -> menu && InteractionMenuCommandAccess.allows(context.villager(), context.player(), alias);
            case DIALOGUE -> dialogue;
        }, context -> {
            if (!(context.villager() instanceof VillagerEntityMCA villager)) {
                return VillagerCommandHandler.DialogueCommandResult.rejected(false);
            }
            String oldCommand = alias.equals("location") && context.command().startsWith("location.")
                    ? context.command() : alias;
            return villager.getInteractions().executeBuiltin(context.player(), oldCommand);
        });
        if (ENTRIES.putIfAbsent(id, entry) != null) {
            throw new IllegalStateException("Duplicate builtin command: " + id);
        }
    }

    /** Registers a server-only Java command; a datapack may invoke it but cannot define its executor. */
    public static void register(ResourceLocation id, Predicate<Context> allowed,
                                Function<Context, VillagerCommandHandler.DialogueCommandResult> executor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(executor, "executor");
        if (MCA.MOD_ID.equals(id.getNamespace())) {
            throw new IllegalArgumentException("The mca namespace is reserved for builtin commands: " + id);
        }
        if (ENTRIES.putIfAbsent(id, new Entry(allowed, executor)) != null) {
            throw new IllegalArgumentException("Command already registered: " + id);
        }
    }

    /** An exact alias or fully namespaced registered ID. No broad name guessing is allowed. */
    static Optional<ResourceLocation> resolve(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation id = LEGACY_ALIASES.get(command);
        if (id == null && command.startsWith("location.") && command.length() > "location.".length()) {
            // The argument may itself be a namespaced structure ID; don't mistake it for an addon command.
            id = LEGACY_ALIASES.get("location");
        }
        if (id == null && command.indexOf(':') >= 0) {
            id = ResourceLocation.tryParse(command);
        }
        return id != null && ENTRIES.containsKey(id) ? Optional.of(id) : Optional.empty();
    }

    public static boolean canExecute(Context context) {
        return permitted(context).isPresent();
    }

    public static VillagerCommandHandler.DialogueCommandResult execute(Context context) {
        Optional<Map.Entry<ResourceLocation, Entry>> resolved = permitted(context);
        if (resolved.isEmpty()) {
            return VillagerCommandHandler.DialogueCommandResult.rejected(false);
        }
        try {
            return Objects.requireNonNull(resolved.get().getValue().executor().apply(context),
                    "Command executor must return a result");
        } catch (Exception exception) {
            MCA.LOGGER.error("Interaction command {} failed", resolved.get().getKey(), exception);
            return VillagerCommandHandler.DialogueCommandResult.rejected(false);
        }
    }

    private static Optional<Map.Entry<ResourceLocation, Entry>> permitted(Context context) {
        if (context == null || context.player() == null || context.villager() == null
                || context.interactionId() == null || context.source() == null) {
            return Optional.empty();
        }
        ServerPlayer player = context.player();
        Entity target = context.villager().asEntity();
        if (player.getServer() == null || !player.getServer().isSameThread()
                || !player.isAlive() || player.isRemoved() || target == null || !target.isAlive()
                || player.serverLevel() != target.level()
                || player.serverLevel().getEntity(target.getUUID()) != target
                || !player.canInteractWithEntity(target, 1.0D)
                || !context.villager().getInteractions().matchesInteraction(player, context.interactionId())) {
            return Optional.empty();
        }
        Optional<ResourceLocation> id = resolve(context.command());
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = ENTRIES.get(id.get());
        try {
            return entry != null && entry.allowed().test(context)
                    ? Optional.of(Map.entry(id.get(), entry)) : Optional.empty();
        } catch (Exception exception) {
            MCA.LOGGER.error("Interaction command {} eligibility check failed", id.get(), exception);
            return Optional.empty();
        }
    }
}
