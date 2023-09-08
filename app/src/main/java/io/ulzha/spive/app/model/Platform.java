package io.ulzha.spive.app.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Root object wrapping all the constituents of Spive control plane model.
 *
 * <p>TODO redesign for sharding
 */
public class Platform {
  public String name; // URN? Domain name, like com.company.production-new-new?
  public Set<Type> types = new HashSet<>();
  public Set<Gateway> gateways = new HashSet<>();

  // indexes
  public Map<UUID, Process> processesById = new HashMap<>();
  public Map<String, Map<String, Process>> processesByApplicationAndVersion = new HashMap<>();
  public Map<UUID, Stream> streamsById = new HashMap<>();
  public Map<UUID, Process.Instance> instancesById = new HashMap<>();

  // hashes and other aggregates
  public String processesEtag;
  // progressTiles for default scales (minute and up)

  public Platform(final String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "Platform{"
        + "name='"
        + name
        + '\''
        + ", types="
        + types
        + ", gateways="
        + gateways
        + ", processesById="
        + processesById
        + ", streamsById="
        + streamsById
        + ", instancesById="
        + instancesById
        + '}';
  }

  public Process.Instance getInstanceById(final UUID instanceId) {
    return instancesById.get(instanceId);
  }
}
