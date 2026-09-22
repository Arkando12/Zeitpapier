package de.dion.zeitpapier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class TimeSettings extends SavedData {
    private static final String NAME = "zeitpapier_settings";
    private int daySeconds = 600;
    private int nightSeconds = 600;

    public static TimeSettings get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TimeSettings::load, TimeSettings::new, NAME);
    }

    private static TimeSettings load(CompoundTag tag) {
        TimeSettings data = new TimeSettings();
        data.daySeconds = Math.max(5, tag.getInt("DaySeconds"));
        data.nightSeconds = Math.max(5, tag.getInt("NightSeconds"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DaySeconds", daySeconds);
        tag.putInt("NightSeconds", nightSeconds);
        return tag;
    }

    public void set(int daySeconds, int nightSeconds) {
        this.daySeconds = daySeconds;
        this.nightSeconds = nightSeconds;
        setDirty();
    }

    public int daySeconds() { return daySeconds; }
    public int nightSeconds() { return nightSeconds; }
}
