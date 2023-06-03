package io.ulzha.spive.app.model;

import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * Relevant to maintain first class understanding of partition keys among event fields.
 *
 * <p>Also needs to understand when different streams refer to the same "entity", and thus partition
 * key sets need to correspond when joining? Would this be configured per process, in UI?
 *
 * <p>Foreign keys hardly a thing?
 */
public class Type {
  UUID typeId;
  String name;
  @Nullable Map<String, String> fieldTypeTags;
}
