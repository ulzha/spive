package io.ulzha.spive.util;

import static jakarta.json.Json.createParser;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventTime;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;

public class Json {
  public static String serializeEventMetadata(EventEnvelope event) {
    return "{"
        + (event.id() == null ? "" : "\"id\":\"" + event.id().toString() + "\",")
        + "\"time\":\""
        + event.time().toString()
        + "\",\"type\":\""
        + event.typeTag()
        + "\"}";
  }

  public static EventEnvelope deserializeEventMetadata(String metadataJson, String defaultPayload)
      throws IOException {
    JsonParser jp = createParser(new StringReader(metadataJson));
    Event next = jp.next();
    if (next != Event.START_OBJECT) {
      throw new IOException("Expected start of object, got: " + next);
    }
    JsonObject jo = jp.getObject();
    String id = jo.getString("id", null);
    return new EventEnvelope(
        EventTime.fromString(jo.getString("time")),
        // treat ids as optional for now, unsure if we would need them
        id == null ? null : UUID.fromString(id),
        jo.getString("type"),
        jo.getString("payload", defaultPayload));
  }

  public static String serializeEventEnvelope(EventEnvelope event) {
    final String metadataJson = serializeEventMetadata(event);
    return metadataJson.replaceFirst("}$", ", \"payload\": " + event.serializedPayload() + "}");
  }

  public static EventEnvelope deserializeEventEnvelope(String json) throws IOException {
    if (json.equals("{}")) {
      return null;
    }
    return deserializeEventMetadata(json, null);
  }
}
