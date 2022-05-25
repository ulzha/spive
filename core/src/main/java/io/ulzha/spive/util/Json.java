package io.ulzha.spive.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventTime;
import java.io.IOException;
import java.util.UUID;

public class Json {
  static final ObjectMapper objectMapper = new ObjectMapper();

  public static String serializeEventMetadata(EventEnvelope event) {
    return "{"
        + (event.id == null ? "" : "\"id\":\"" + event.id.toString() + "\",")
        + "\"time\":\""
        + event.time.toString()
        + "\",\"type\":\""
        + event.typeTag
        + "\"}";
  }

  public static EventEnvelope deserializeEventMetadata(String metadataJson) throws IOException {
    final JsonNode jsonNode = objectMapper.readTree(metadataJson);
    final JsonNode idNode = jsonNode.get("id");
    final JsonNode payloadNode = jsonNode.get("payload");
    return new EventEnvelope(
        EventTime.fromString(jsonNode.get("time").asText()),
        // treat ids as optional for now, unsure if we would need them
        idNode == null || idNode.isNull() ? null : UUID.fromString(idNode.asText()),
        jsonNode.get("type").asText(),
        payloadNode == null || payloadNode.isNull() ? null : payloadNode.toString());
  }

  public static String serializeEventEnvelope(EventEnvelope event) {
    final String metadataJson = serializeEventMetadata(event);
    return metadataJson.replaceFirst("}$", ", \"payload\": " + event.serializedPayload + "}");
  }

  public static EventEnvelope deserializeEventEnvelope(String json) throws IOException {
    if (json.equals("{}")) {
      return null;
    }
    return deserializeEventMetadata(json);
  }
}
