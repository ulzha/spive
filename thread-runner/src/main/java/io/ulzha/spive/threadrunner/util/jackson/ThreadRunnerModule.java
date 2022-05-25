package io.ulzha.spive.threadrunner.util.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * We don't want "io.ulzha.spive.threadrunner.api.Umbilical$ProgressUpdate@2034b64c", nor
 * {"instant":{"nano":0,"epochSecond":1611255601},"warning":null,"error":null}, but rather
 * {"instant":"2021-01-21T19:00:02Z"}.
 */
public class ThreadRunnerModule extends SimpleModule {
  static final long serialVersionUID = 42L;

  @Override
  public void setupModule(SetupContext setupContext) {
    super.setupModule(setupContext);

    SimpleSerializers serializers = new SimpleSerializers();
    serializers.addSerializer(HeartbeatSample.class, new HeartbeatSampleSerializer());
    serializers.addSerializer(ProgressUpdate.class, new ProgressUpdateSerializer());
    setupContext.addSerializers(serializers);

    SimpleDeserializers deserializers = new SimpleDeserializers();
    deserializers.addDeserializer(HeartbeatSample.class, new HeartbeatSampleDeserializer());
    deserializers.addDeserializer(ProgressUpdate.class, new ProgressUpdateDeserializer());
    setupContext.addDeserializers(deserializers);
  }

  static class HeartbeatSampleSerializer extends JsonSerializer<HeartbeatSample> {
    @Override
    public void serialize(
        final HeartbeatSample heartbeatSample,
        final JsonGenerator jsonGenerator,
        final SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeStartObject();
      jsonGenerator.writeObjectField(
          "eventTime",
          heartbeatSample.eventTime() == null ? null : heartbeatSample.eventTime().toString());
      jsonGenerator.writeObjectField("partition", heartbeatSample.partition());
      jsonGenerator.writeObjectField("progressUpdates", heartbeatSample.progressUpdates());
      jsonGenerator.writeEndObject();
    }
  }

  static class HeartbeatSampleDeserializer extends JsonDeserializer<HeartbeatSample> {
    @Override
    public HeartbeatSample deserialize(
        JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      JsonNode node = jsonParser.readValueAsTree();
      final JsonNode eventTimeNode = node.get("eventTime");
      final JsonNode partitionNode = node.get("partition");
      final JsonNode progressUpdatesNode = node.get("progressUpdates");
      final String eventTimeText = (eventTimeNode == null ? null : eventTimeNode.asText(null));
      final List<ProgressUpdate> progressUpdates = new ArrayList<>();
      for (JsonNode updateNode : progressUpdatesNode) {
        progressUpdates.add(
            deserializationContext.readValue(
                updateNode.traverse(jsonParser.getCodec()), ProgressUpdate.class));
      }
      return HeartbeatSample.create(
          (eventTimeText == null ? null : EventTime.fromString(eventTimeText)),
          (partitionNode == null ? null : partitionNode.asText(null)),
          progressUpdates);
    }
  }

  static class ProgressUpdateSerializer extends JsonSerializer<ProgressUpdate> {
    @Override
    public void serialize(
        final ProgressUpdate progressUpdate,
        final JsonGenerator jsonGenerator,
        final SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeStartObject();
      jsonGenerator.writeObjectField("instant", progressUpdate.instant().toString());
      if (progressUpdate.success()) {
        jsonGenerator.writeObjectField("success", progressUpdate.success());
      }
      if (progressUpdate.warning() != null) {
        jsonGenerator.writeObjectField("warning", progressUpdate.warning());
      }
      if (progressUpdate.error() != null) {
        jsonGenerator.writeObjectField("error", progressUpdate.error());
      }
      jsonGenerator.writeEndObject();
    }
  }

  static class ProgressUpdateDeserializer extends JsonDeserializer<ProgressUpdate> {
    @Override
    public ProgressUpdate deserialize(
        JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      JsonNode node = jsonParser.readValueAsTree();
      final JsonNode successNode = node.get("success");
      final JsonNode warningNode = node.get("warning");
      final JsonNode errorNode = node.get("error");
      return ProgressUpdate.create(
          Instant.parse(node.get("instant").asText(null)),
          (successNode == null ? false : successNode.asBoolean(false)),
          // tolerate {..."warning": null...} in the same way as if "warning" was not set at all
          (warningNode == null ? null : warningNode.asText(null)),
          (errorNode == null ? null : errorNode.asText(null)));
    }
  }
}
