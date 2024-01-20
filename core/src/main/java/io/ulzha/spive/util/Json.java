package io.ulzha.spive.util;

import static jakarta.json.Json.createParser;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventTime;
import jakarta.json.JsonException;
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

  /**
   * @param metadataJson may include payload (the key can have any value, not just an object)
   * @param externalPayloadJson specifies payload in case it is not included in metadataJson
   */
  public static EventEnvelope deserializeEventMetadata(
      String metadataJson, String externalPayloadJson) {
    JsonParser parser = createParser(new StringReader(metadataJson));
    String idString = null;
    String timeString = null;
    String typeString = null;
    String payloadJson = null;
    Event event = parser.next();
    if (event != Event.START_OBJECT) {
      throw new JsonException("Expected start of object, got: " + event);
    }
    do {
      event = parser.next();
      if (event == Event.KEY_NAME) {
        final String keyName = parser.getString();
        final long valueStart = getValueStartStreamOffset(metadataJson, parser);
        final Event valueEvent = parser.next();
        switch (keyName) {
          case "id":
            if (idString != null) {
              throw new JsonException("Duplicate key: \"id\"");
            }
            idString = (valueEvent == Event.VALUE_NULL ? null : parser.getString());
            break;
          case "time":
            if (timeString != null) {
              throw new JsonException("Duplicate key: \"time\"");
            }
            timeString = parser.getString();
            break;
          case "type":
            if (typeString != null) {
              throw new JsonException("Duplicate key: \"type\"");
            }
            typeString = parser.getString();
            break;
          case "payload":
            if (payloadJson != null) {
              throw new JsonException("Duplicate key: \"payload\"");
            }
            // optimized using skips, to splice stream and return the JSON verbatim. Parsing will
            // take place just once in some Type
            if (valueEvent == Event.START_ARRAY) {
              parser.skipArray();
            } else if (valueEvent == Event.START_OBJECT) {
              parser.skipObject();
            }
            long valueEnd = parser.getLocation().getStreamOffset();
            if (valueEnd > Integer.MAX_VALUE) {
              throw new JsonException(
                  "Payload too large: start " + valueStart + ", end " + valueEnd);
            }
            payloadJson = metadataJson.substring((int) valueStart, (int) valueEnd);
            break;
          default:
            // TODO ignore (skip over) unknown keys for forward compatibility?
            throw new JsonException("Unexpected key: \"" + keyName + "\"");
        }
      }
    } while (event == Event.KEY_NAME);
    if (event != Event.END_OBJECT) {
      throw new JsonException(
          "Expected END_OBJECT at " + parser.getLocation().getStreamOffset() + "; got: " + event);
    }
    return new EventEnvelope(
        EventTime.fromString(timeString),
        // treat ids as optional for now, unsure if we would need them
        idString == null ? null : UUID.fromString(idString),
        typeString,
        payloadJson == null ? externalPayloadJson : payloadJson);
  }

  private static long getValueStartStreamOffset(final String stream, final JsonParser parser) {
    long i;
    final String colonOrWs = ":" + (char) 0x09 + (char) 0x0A + (char) 0x0D + " ";
    for (i = parser.getLocation().getStreamOffset();
        colonOrWs.indexOf(stream.charAt((int) i)) != -1;
        i++)
      ;
    return i;
  }

  public static String serializeEventEnvelope(EventEnvelope event) {
    final String metadataJson = serializeEventMetadata(event);
    return metadataJson.replaceFirst("}$", ",\"payload\":" + event.serializedPayload() + "}");
  }

  public static EventEnvelope deserializeEventEnvelope(String json) throws IOException {
    if (json.equals("{}")) {
      return null;
    }
    return deserializeEventMetadata(json, null);
  }
}
