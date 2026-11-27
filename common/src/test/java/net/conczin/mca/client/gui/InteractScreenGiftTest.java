package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.InteractionVillagerMessage;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractScreenGiftTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void enterAndNumpadEnterEachGiveOneGiftUntilReleased() throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        InteractScreen screen = (InteractScreen) unsafe.allocateInstance(InteractScreen.class);
        VillagerEntityMCA villager = (VillagerEntityMCA) unsafe.allocateInstance(VillagerEntityMCA.class);
        UUID villagerId = UUID.randomUUID();
        villager.setUUID(villagerId);
        var villagerField = InteractScreen.class.getDeclaredField("villager");
        villagerField.setAccessible(true);
        villagerField.set(screen, villager);
        var giftMode = InteractScreen.class.getDeclaredField("inGiftMode");
        giftMode.setAccessible(true);
        giftMode.setBoolean(screen, true);

        var senderField = Network.class.getDeclaredField("clientSender");
        senderField.setAccessible(true);
        Network.ClientSender previousSender = (Network.ClientSender) senderField.get(null);
        List<HandleablePayload> sent = new ArrayList<>();
        Network.registerClientSender(sent::add);
        try {
            for (int key : new int[]{GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER}) {
                KeyEvent event = new KeyEvent(key, 0, 0);
                InteractionVillagerMessage gift = new InteractionVillagerMessage("gift", villagerId);
                sent.clear();
                advancePastClickCooldown(screen);
                assertTrue(screen.keyPressed(event));
                advancePastClickCooldown(screen);
                assertTrue(screen.keyPressed(event));
                assertEquals(List.of(gift), sent, "A repeated press must not send another gift");

                screen.keyReleased(event);
                assertTrue(screen.keyPressed(event));
                assertEquals(List.of(gift, gift), sent, "A fresh press must send another gift");
                screen.keyReleased(event);
            }
        } finally {
            Network.registerClientSender(previousSender);
        }
    }

    private static void advancePastClickCooldown(InteractScreen screen) {
        for (int i = 0; i < 10; i++) {
            screen.tick();
        }
    }
}
