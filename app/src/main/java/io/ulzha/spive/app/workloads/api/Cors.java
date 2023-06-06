package io.ulzha.spive.app.workloads.api;

import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.cors.CorsService;
import java.time.Duration;
import java.util.function.Function;

public class Cors {
  public static Function<? super HttpService, CorsService> decorator() {
    return CorsService.builderForAnyOrigin()
        .allowRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
        .allowAllRequestHeaders(true)
        .maxAge(Duration.ofSeconds(300))
        .newDecorator();
  }
}
