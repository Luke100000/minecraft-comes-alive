package mca.entity.data;

import cobalt.minecraft.nbt.CNBT;
import cobalt.minecraft.world.CWorld;
import cobalt.minecraft.world.storage.CWorldSavedData;
import mca.core.Constants;
import mca.enums.EnumMarriageState;

import java.util.UUID;

public class PlayerSaveData extends CWorldSavedData {
    private UUID spouseUUID = Constants.ZERO_UUID;
    private String spouseName = "";
    private boolean babyPresent = false;
    private EnumMarriageState marriageState;

    public PlayerSaveData(String id) {
        super(id);
    }

    public static PlayerSaveData get(CWorld world, UUID uuid) {
        return world.loadData(PlayerSaveData.class, "mca_village_" + uuid.toString());
    }

    public boolean isMarried() {
        return !spouseUUID.equals(Constants.ZERO_UUID) && marriageState != EnumMarriageState.NOT_MARRIED;
    }

    @Override
    public CNBT save(CNBT nbt) {
        nbt.setUUID("spouseUUID", spouseUUID);
        nbt.setString("spouseName", spouseName);
        nbt.setBoolean("babyPresent", babyPresent);
        return nbt;
    }

    @Override
    public void load(CNBT nbt) {
        spouseUUID = nbt.getUUID("spouseUUID");
        spouseName = nbt.getString("spouseName");
        babyPresent = nbt.getBoolean("babyPresent");
    }

    public void marry(UUID uuid, String name, EnumMarriageState marriageState) {
        this.spouseUUID = uuid;
        this.spouseName = name;
        this.marriageState = marriageState;
        setDirty();
    }

    public void endMarriage() {
        spouseUUID = Constants.ZERO_UUID;
        spouseName = "";
        setDirty();
    }

    public boolean isBabyPresent() {
        return this.babyPresent;
    }

    public void setBabyPresent(boolean value) {
        this.babyPresent = value;
        setDirty();
    }

    public void reset() {
        endMarriage();
        setBabyPresent(false);
        setDirty();
    }

    public EnumMarriageState getMarriageState() {
        return this.marriageState;
    }


    public UUID getSpouseUUID() {
        return spouseUUID;
    }

    public String getSpouseName() {
        return spouseName;
    }
}
