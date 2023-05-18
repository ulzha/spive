package io.ulzha.spive.threadrunner.util;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * We don't want "io.ulzha.spive.threadrunner.api.Umbilical$ProgressUpdate@2034b64c", nor
 * {"instant":{"nano":0,"epochSecond":1611255601},"warning":null,"error":null}, but rather
 * {"instant":"2021-01-21T19:00:02Z"}.
 */
public class Json {
  public static Jsonb create() {
    return JsonbBuilder.create(
        new JsonbConfig()
            .withSerializers(
                new EventTimeSerializer(),
                new HeartbeatSampleSerializer(),
                new ProgressUpdateSerializer())
            .withDeserializers(
                new EventTimeDeserializer(),
                new HeartbeatSampleDeserializer(),
                new ProgressUpdateDeserializer()));
  }

  private static class EventTimeSerializer implements JsonbSerializer<EventTime> {
    @Override
    public void serialize(EventTime obj, JsonGenerator generator, SerializationContext ctx) {
      if (obj == null) {
        generator.writeNull();
      } else {
        generator.write(obj.toString());
      }
    }
  }

  private static class EventTimeDeserializer implements JsonbDeserializer<EventTime> {
    @Override
    public EventTime deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
      final String eventTimeString = ctx.deserialize(String.class, parser);
      return (eventTimeString == null ? null : EventTime.fromString(eventTimeString));
    }
  }

  private static class HeartbeatSampleSerializer implements JsonbSerializer<HeartbeatSample> {
    @Override
    public void serialize(
        HeartbeatSample heartbeatSample, JsonGenerator generator, SerializationContext ctx) {
      generator.writeStartObject();
      EventTime eventTime = heartbeatSample.eventTime();
      if (eventTime == null) {
        generator.writeNull("eventTime");
      } else {
        generator.write("eventTime", eventTime.toString());
      }
      String partition = heartbeatSample.partition();
      if (partition == null) {
        generator.writeNull("partition");
      } else {
        generator.write("partition", partition);
      }
      ctx.serialize("progressUpdates", heartbeatSample.progressUpdates(), generator);
      generator.writeEnd();
    }
  }

  private static class HeartbeatSampleDeserializer implements JsonbDeserializer<HeartbeatSample> {
    @Override
    public HeartbeatSample deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
      String eventTimeString = null;
      String partitionString = null;
      List<ProgressUpdate> progressUpdates = null;
      Event event;
      do {
        event = parser.next();
        if (event == Event.KEY_NAME) {
          final String keyName = parser.getString();
          final Event valueEvent = parser.next();
          switch (keyName) {
            case "eventTime":
              if (eventTimeString != null) {
                throw new JsonException("Duplicate key: \"eventTime\"");
              }
              eventTimeString = (valueEvent == Event.VALUE_NULL ? null : parser.getString());
              break;
            case "partition":
              if (partitionString != null) {
                throw new JsonException("Duplicate key: \"partition\"");
              }
              partitionString = (valueEvent == Event.VALUE_NULL ? null : parser.getString());
              break;
            case "progressUpdates":
              if (progressUpdates != null) {
                throw new JsonException("Duplicate key: \"progressUpdates\"");
              }
              progressUpdates =
                  ctx.deserialize(
                      new ArrayList<ProgressUpdate>() {}.getClass().getGenericSuperclass(), parser);
              break;
            default:
              // ignore unknown keys for forward compatibility
          }
        }
      } while (event == Event.KEY_NAME);
      if (event != Event.END_OBJECT) {
        throw new JsonException(
            "Expected end of object, got: "
                + event
                + ((event == Event.VALUE_STRING ? " \"" + parser.getString() + "\"" : "")));
      }
      if (progressUpdates == null) {
        throw new JsonException("Missing \"progressUpdates\"");
      }
      return new HeartbeatSample(
          (eventTimeString == null ? null : EventTime.fromString(eventTimeString)),
          partitionString,
          progressUpdates);
    }
  }

  private static class ProgressUpdateSerializer implements JsonbSerializer<ProgressUpdate> {
    @Override
    public void serialize(
        ProgressUpdate progressUpdate, JsonGenerator generator, SerializationContext ctx) {
      generator.writeStartObject();
      generator.write("instant", progressUpdate.instant().toString());
      if (progressUpdate.success()) {
        generator.write("success", progressUpdate.success());
      }
      if (progressUpdate.warning() != null) {
        generator.write("warning", progressUpdate.warning());
      }
      if (progressUpdate.error() != null) {
        generator.write("error", progressUpdate.error());
      }
      generator.writeEnd();
    }
  }

  private static class ProgressUpdateDeserializer implements JsonbDeserializer<ProgressUpdate> {
    @Override
    public ProgressUpdate deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
      final JsonObject jo = parser.getObject();
      final String instantString = jo.getString("instant");
      final Boolean success = jo.getBoolean("success", false);
      // tolerate {..."warning": null...} in the same way as if "warning" was not set at all
      final String warning = jo.getString("warning", null);
      final String error = jo.getString("error", null);
      if (!success && !(warning == null || error == null)) {
        throw new JsonException("Expected either \"warning\" or \"error\" to be set, not both");
      }
      if (success && !(warning == null && error == null)) {
        throw new JsonException(
            "Expected neither \"warning\" nor \"error\" to be set, given \"success\" is true");
      }
      return new ProgressUpdate(Instant.parse(instantString), success, warning, error);
    }
  }
}
