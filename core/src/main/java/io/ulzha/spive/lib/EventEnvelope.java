package io.ulzha.spive.lib;

import java.util.UUID;

/**
 * Raw representation of an Event, carrying payload in its serialized form.
 *
 * <p>Useful sometimes in order to transmit events or inspect metadata without unwrapping payloads.
 */
public class EventEnvelope {
  public EventTime time;

  public UUID id;

  public String typeTag;

  public String serializedPayload;

  public EventEnvelope(EventTime time, UUID id, String typeTag, String serializedPayload) {
    this.time = time;
    this.id = id;
    this.typeTag = typeTag;
    this.serializedPayload = serializedPayload;
  }

  public static EventEnvelope wrap(Event event) {
    return new EventEnvelope(
        event.time, event.id, event.type.getTypeTag(), event.type.serialize(event.payload));
  }

  public Event unwrap() {
    final Type type = Type.fromTypeTag(typeTag);
    return new Event(time, id, type, type.deserialize(serializedPayload));
  }
}
