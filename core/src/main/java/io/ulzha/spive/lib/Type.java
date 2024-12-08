package io.ulzha.spive.lib;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * A data type that Event payload can possess and that a Process, written in one of the supported
 * languages, can natively accept.
 *
 * <p>Accomplishes schema management, goes some way towards ensuring that known compatible
 * schemas/libraries are used for serialization and deserialization of a given Stream. Also removes
 * serialization concerns from Process code.
 *
 * <p>(TODO should invalid inputs be precluded at deploy time? E.g. a Process expecting non-null
 * should not be deployable to consume a Stream that can contain nulls there? Or just require the
 * Types to be matching between Stream and Process and non-evolvable during Stream's lifetime?)
 *
 * <p>Types should not undergo breaking changes in their serde logic; any new functionality should
 * be implemented as a new Type, even though the type in the programming language may stay the same.
 */
public class Type {
  public final String name; // short... Perhaps the same as tag?

  // Each combo of serialized representation and programming language representation should appear
  // as a human-matchable tip in the UI also, clarifying the short name describing the Type used.
  // Perhaps storage also adds a dimension?
  //  Object serializationFormat;
  //  Object programmingLanguageFormat; // language, datatype, semantics (validation contract?)
  public final Class<?> programmingLanguageFormat;

  private final Jsonb jsonb;

  private Type(String name, Class<?> programmingLanguageFormat) {
    this.name = name;
    this.programmingLanguageFormat = programmingLanguageFormat;
    // An implementation, such as EventTimeJsonbSerde, should be packaged with spive.app or
    // spive.lib somehow... Now we leech it off basic-runner. FIXME
    if (name.equals("io.ulzha.spive.app.events.InstanceProgress")) {
      try {
        this.jsonb =
            (Jsonb)
                Class.forName("io.ulzha.spive.basicrunner.serde.json.BasicRunnerJsonbSerde")
                    .getMethod("create")
                    .invoke(null);
      } catch (Exception e) {
        throw new InternalException("Type " + name + " lacks implementation", e);
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
  public static Type fromTypeTag(final String tag) {
    if (tag.startsWith("pojo:")) {
      final String name = tag.substring(5);
      try {
        Class<?> programmingLanguageFormat = Class.forName(name);
        return new Type(name, programmingLanguageFormat);
      } catch (ClassNotFoundException e) {
        throw new InternalException(String.format("Unknown format: %s", name), e);
      }
    } else {
      throw new InternalException(String.format("Unknown type tag: %s", tag));
    }
  }

  public String toString() {
    return name + " (pojo:" + programmingLanguageFormat.getName() + ")";
  }

  public String getTypeTag() {
    return "pojo:" + programmingLanguageFormat.getName();
  }

  public String serialize(Object payload) {
    try {
      return jsonb.toJson(payload, programmingLanguageFormat);
    } catch (Exception e) {
      throw new InternalException("Failed to serialize event payload " + this, e);
    }
  }

  public Object deserialize(String s) {
    try {
      return jsonb.fromJson(s, programmingLanguageFormat);
    } catch (Exception e) {
      throw new InternalException("Failed to deserialize event payload " + this, e);
    }
  }
}
