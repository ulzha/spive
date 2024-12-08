package io.ulzha.spive.lib;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Types should not undergo breaking changes in their serde logic; any new functionality should be
 * implemented as a new Type, even though the output type of deserialize() in the programming
 * language may stay the same.
 */
public class EventSerde {
  private final String tag;

  public final Class<?> type;

  private final Jsonb jsonb;

  private EventSerde(String tag, Class<?> type) {
    this.tag = tag;
    this.type = type;
    // An implementation, such as EventTimeJsonbSerde, should be packaged with spive.app or
    // spive.lib somehow... Now we leech it off basic-runner. FIXME
    if (tag.equals("pojo:io.ulzha.spive.app.events.InstanceProgress")) {
      try {
        this.jsonb =
            // TODO possibly reflection through platform.types, not like this
            (Jsonb)
                Class.forName("io.ulzha.spive.basicrunner.serde.json.BasicRunnerJsonbSerde")
                    .getMethod("create")
                    .invoke(null);
      } catch (Exception e) {
        throw new InternalException(tag + " hardcoded serde hack broke", e);
      }
    } else {
      this.jsonb = JsonbBuilder.create();
    }
  }

  /**
   * Factory to allow hot updates of serde implementations without rebuilding user's application.
   *
   * <p>(Maybe, instead of dynamic loading, always just generate and plop a lib of types in
   * app.spive.gen, as source or as a jar?)
   */
  public static EventSerde forTypeTag(final String tag) {
    if (tag.startsWith("pojo:")) {
      final String name = tag.substring(5);
      try {
        Class<?> type = Class.forName(name);
        return new EventSerde(tag, type);
      } catch (ClassNotFoundException e) {
        throw new InternalException(String.format("Unexpected class name: %s", name), e);
      }
    } else {
      throw new InternalException(String.format("Unexpected type prefix: %s", tag));
    }
  }

  public String getTag() {
    return tag;
  }

  public String serialize(Object payload) {
    try {
      return jsonb.toJson(payload, type);
    } catch (Exception e) {
      throw new InternalException("Failed to serialize " + tag, e);
    }
  }

  public Object deserialize(String s) {
    try {
      return jsonb.fromJson(s, type);
    } catch (Exception e) {
      throw new InternalException("Failed to deserialize " + tag, e);
    }
  }
}
