package io.ulzha.spive.gen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A maintainer's tool for code generation for Spīve applications such as Spive app itself and
 * examples in repo.
 *
 * <p>Make do with a hardcoded list of configs, while the intended flow (through UI and managed
 * streams, and artifact static analysis) is not yet bootstrapped in a usable state.
 */
public class Cli {
  public static void main(final String... args) throws Exception {
    final Map<String, AppIoc.AppConfig> configs = new HashMap<>();

    configs.put(
        "app/src/main/java/io/ulzha/spive/app/spive/gen",
        new AppIoc.AppConfig(
            "Spive",
            List.of(
              new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateEventLog"),
              new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateInstance")),
            List.of(new AppIoc.WorkloadDescriptor("Watchdog"), new AppIoc.WorkloadDescriptor("Api"))));

    for (String dir : configs.keySet()) {
      AppIoc.generateAppInstanceCode(configs.get(dir), dir);
      // AppIoc.generateAppOutputGatewayCode(configs.get(dir), dir);
    }
  }
}
