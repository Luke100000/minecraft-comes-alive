package net.conczin.mca.config;

public interface ConfigBackend {
    void save(ConfigScope scope);
}
