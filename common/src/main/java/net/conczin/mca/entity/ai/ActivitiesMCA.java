package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.conczin.mca.mixin.MixinActivity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;

import java.util.HashMap;
import java.util.Map;

public interface ActivitiesMCA {
    Map<Identifier, Activity> ACTIVITIES = new HashMap<>();

    Activity CHORE = activity("chore");
    Activity GRIEVE = activity("grieve");

    static Activity activity(String name) {
        Identifier id = MCA.locate(name);
        Activity init = MixinActivity.init(id.toString());
        ACTIVITIES.put(id, init);
        return init;
    }

    static void registerActivities(MCA.RegisterHelper<Activity> helper) {
        ACTIVITIES.forEach(helper::register);
    }
}
