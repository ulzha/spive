package io.ulzha.spive.app.workloads.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.linecorp.armeria.common.JacksonObjectMapperProvider;

/**
 * A kludge to stop Server.start() from crashing with java.util.ServiceConfigurationError.
 *
 * <p>I don't want any Jackson. Jsonb seems quite usable and is supposedly "standard"...
 * https://github.com/line/armeria/issues/1959
 */
public final class KludgeJacksonObjectMapperProvider implements JacksonObjectMapperProvider {
  @Override
  public ObjectMapper newObjectMapper() {
    return JsonMapper.builder()
        .visibility(
            PropertyAccessor.FIELD,
            Visibility.ANY) // otherwise records don't seem to work out of the box
        .build();
  }
}
