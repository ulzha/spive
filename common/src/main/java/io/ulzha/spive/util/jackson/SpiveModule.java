package io.ulzha.spive.util.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import io.ulzha.spive.lib.EventTime;
import java.io.IOException;

/** TODO move out so spive-core does not require jackson dependency? */
public class SpiveModule extends SimpleModule {
  static final long serialVersionUID = 42L;

  @Override
  public void setupModule(SetupContext setupContext) {
    super.setupModule(setupContext);

    SimpleSerializers serializers = new SimpleSerializers();
    serializers.addSerializer(EventTime.class, new EventTimeSerializer());
    setupContext.addSerializers(serializers);

    SimpleDeserializers deserializers = new SimpleDeserializers();
    deserializers.addDeserializer(EventTime.class, new EventTimeDeserializer());
    setupContext.addDeserializers(deserializers);
  }

  static class EventTimeSerializer extends JsonSerializer<EventTime> {
    @Override
    public void serialize(
        final EventTime eventTime,
        final JsonGenerator jsonGenerator,
        final SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeString(eventTime.toString());
    }
  }

  static class EventTimeDeserializer extends JsonDeserializer<EventTime> {
    @Override
    public EventTime deserialize(
        JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      return EventTime.fromString(jsonParser.getValueAsString());
    }
  }
}
