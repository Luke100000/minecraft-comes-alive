package net.conczin.mca.entity.ai.chatAI;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.ChatAIContextData;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ChatAIContext {
    /**
     * Childhood, previous homes, journeys, and memorable events.
     */
    private static final String[] HISTORY = {
            "You grew up helping to maintain a small communal garden.",
            "You were raised near a river and still find running water familiar.",
            "You spent part of your youth exploring the forests beyond your village.",
            "You learned your trade while traveling with an elderly merchant.",
            "You moved here after a difficult winter damaged your former settlement.",
            "You grew up close to old ruins that villagers considered unlucky.",
            "You were apprenticed to a craftsperson who demanded patient, careful work.",
            "You spent several years traveling between distant settlements with a caravan.",
            "You once became lost for two nights and followed birds back toward civilization.",
            "You survived a blizzard by following the light of a distant lantern.",
            "You once found an abandoned campsite whose owner never returned.",
            "A failed harvest taught you never to waste useful food.",
            "A flood forced your former settlement to rebuild on higher ground.",
            "You once traveled for days in the wrong direction because of an inaccurate map.",
            "You discovered writing in an unknown language behind a collapsed wall.",
            "You once kept an entire village celebration running after its organizer disappeared.",
            "Your first successful piece of work is still being used somewhere far away."
    };

    /**
     * Practical knowledge, competencies, and everyday habits.
     */
    private static final String[] SKILLS_AND_HABITS = {
            "You are good at identifying plants by their leaves and scent.",
            "You can usually tell when the weather is about to change.",
            "You know several practical ways to preserve food.",
            "You can tie several useful knots.",
            "You are good at recognizing animal tracks.",
            "You remember roads and landmarks exceptionally well.",
            "You can estimate the practical value of ordinary goods.",
            "You are skilled at keeping fires alive with very little fuel.",
            "You can tell whether an old structure is close to collapsing.",
            "You are good at identifying why ordinary tools have stopped working.",
            "You can judge measurements surprisingly accurately without instruments.",
            "You are good at remembering songs after hearing them only once.",
            "You save pieces of string because they may eventually be useful.",
            "You hum fragments of old songs while working.",
            "You give informal names to tools that you use frequently.",
            "You count nearby doors when entering an unfamiliar building.",
            "You always check the sky before beginning an important task.",
            "You keep a written list of unusual things travelers have said."
    };

    /**
     * Preferences, comforts, dislikes, and favorite things.
     */
    private static final String[] PREFERENCES = {
            "You find the sound of rain against a roof comforting.",
            "The smell of fresh bread reminds you of safer times.",
            "You enjoy the quiet period immediately before sunrise.",
            "You like hearing travelers describe places you have never visited.",
            "You prefer simple meals over elaborate dishes.",
            "You enjoy repairing objects that other people have abandoned.",
            "You find well-organized storage rooms strangely satisfying.",
            "You dislike wasting food.",
            "You distrust maps that do not include recognizable landmarks.",
            "You dislike leaving a task unfinished overnight.",
            "You avoid sleeping beneath damaged roofs.",
            "You dislike people borrowing objects without saying when they will return them.",
            "You are cautious around abandoned buildings after dark.",
            "You distrust anyone who claims never to make mistakes."
    };

    /**
     * Keepsakes, unresolved matters, rumors, and personal anecdotes.
     */
    private static final String[] PERSONAL_DETAILS = {
            "You keep a foreign coin that no local merchant recognizes.",
            "You own an old iron key that has never opened anything.",
            "You preserve a torn corner of an old trade map.",
            "You keep a pressed flower from the first crop you successfully grew.",
            "You own a cracked lantern that once guided you through a blizzard.",
            "You carry a small piece of colored glass recovered from a ruined building.",
            "You keep an unfinished letter addressed to someone you may never meet again.",
            "You owe a favor to a traveling merchant who may someday return.",
            "You quietly maintain a neglected path outside the village.",
            "You promised to finish a piece of work begun by your former teacher.",
            "You know a shortcut outside the village that you have not shared with anyone.",
            "You once saw an unfamiliar light moving through the forest at night.",
            "You found an apparently valuable object that every merchant insists is worthless.",
            "You sometimes leave anonymous gifts for villagers who are struggling.",
            "Some villagers know you as the person most likely to have a spare tool.",
            "You are known for returning borrowed objects in better condition."
    };

    /**
     * Long-term intentions, principles, and unfinished objectives.
     */
    private static final String[] GOALS_AND_BELIEFS = {
            "You hope to create something useful enough to outlast you.",
            "You want to visit a place you know only from travelers' stories.",
            "You intend to record the history of the village before it is forgotten.",
            "You hope to become knowledgeable enough to teach someone your trade.",
            "You want to assemble an accurate map of the surrounding region.",
            "You would like to restore an old building rather than construct a new one.",
            "You want to learn the full version of a song you only partially remember.",
            "You intend to repay an old favor, although the person may be difficult to find.",
            "You believe useful knowledge should be shared rather than hidden.",
            "You believe small preparations prevent most large disasters.",
            "You believe objects repaired many times become more valuable, not less.",
            "You believe travelers should be offered water before being questioned.",
            "You believe old stories usually contain at least one true detail.",
            "You believe a fair trade should leave both people satisfied.",
            "You believe promises made during difficult times matter the most."
    };

    public static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(Config.getInstance().villagerChatAIContextPermissionLevel)
               || player.serverLevel().getServer().isSingleplayerOwner(player.getGameProfile());
    }

    public static boolean canEdit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null && canEdit(player);
    }

    public static String createVillagerPrompt() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<String> facts = new ArrayList<>(List.of(
                pick(HISTORY, random),
                pick(SKILLS_AND_HABITS, random),
                pick(PREFERENCES, random),
                pick(PERSONAL_DETAILS, random),
                pick(GOALS_AND_BELIEFS, random)
        ));

        Collections.shuffle(facts, random);

        return String.join(" ", facts.subList(0, 3));
    }

    public static void appendPrompts(
            StringBuilder prompt,
            ServerPlayer player,
            VillagerEntityMCA villager,
            Village village
    ) {
        append(prompt, "World context", ChatAIContextData.get(player.serverLevel().getServer()).getWorldPrompt());
        append(prompt, "Villager background", villager.getChatAIPrompt());
        append(prompt, "Player context", PlayerSaveData.get(player).getChatAIPrompt());

        if (village != null) {
            append(prompt, "Village context", village.getChatAIPrompt());
        }
    }

    private static void append(StringBuilder prompt, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        prompt.append(label)
                .append(": ")
                .append(value.strip())
                .append('\n');
    }

    private static String pick(String[] values, ThreadLocalRandom random) {
        return values[random.nextInt(values.length)];
    }
}
