package io.ulzha.spive.app.model;

import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * A data type that Event payload can possess and that a Process, written in one of the supported
 * languages, can natively accept.
 *
 * <p>Datatypes for consumption may vary (more than one per language?), as generated in gen code.
 * Semantics and value set stays the same (validation contract?)
 *
 * <p>Accomplishes first class understanding of partition keys among event fields.
 *
 * <p>Accomplishes schema management, goes some way towards ensuring that known compatible
 * schemas/libraries are used for serialization and deserialization of a given Stream.
 *
 * <p>Removes serialization concerns from Process code.
 *
 * <p>Also needs to understand when different streams refer to the same "entity", and thus partition
 * key sets need to correspond when joining? Would this be configured per process, in UI?
 *
 * <p>Perhaps storage also adds a dimension?
 *
 * <p>Foreign keys hardly a thing?
 *
 * <p>TODO should invalid inputs be precluded at deploy time? E.g. a Process expecting non-null
 * should not be deployable to consume a Stream that can contain nulls there? Or just require the
 * Types to be matching between Stream and Process and non-evolvable during Stream's lifetime?)
 *
 * <p>TODO Each combo of serialized representation and programming language representation should
 * appear as a human-matchable tip in the UI also, clarifying the short name describing the Type
 * used.
 */
public class Type {
  UUID typeId;
  String name;
  @Nullable Map<String, String> fieldTypeTags;

  public Type(UUID typeId, String name, @Nullable Map<String, String> fieldTypeTags) {
    this.typeId = typeId;
    this.name = name;
    this.fieldTypeTags = fieldTypeTags;
  }
}
