package io.ulzha.spive.app.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Root object wrapping all the constituents of Spive platform. */
public class Platform {
  public String name; // URN? Domain name, like com.company.production-new-new?
  public Set<EventSchema> eventSchemas = new HashSet<>();
  public Set<Gateway> gateways = new HashSet<>();
  public Map<UUID, Process> processesById = new HashMap<>();
  public Set<Stream> streams = new HashSet<>();

  // indexes
  public Map<UUID, Process.Instance> instancesById = new HashMap<>();

  public Platform(final String name) {
    this.name = name;
  }

  // TODO recordify
  @Override
  public String toString() {
    return "Platform{"
        + "name='"
        + name
        + '\''
        + ", eventSchemas="
        + eventSchemas
        + ", gateways="
        + gateways
        + ", processesById="
        + processesById
        + ", streams="
        + streams
        + ", instancesById="
        + instancesById
        + '}';
  }

  public Process.Instance getInstanceById(final UUID instanceId) {
    return instancesById.get(instanceId);
  }
}
