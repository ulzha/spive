package io.ulzha.spive.codegen;

import java.util.List;
import java.util.Map;

/**
 * A maintainer's tool for code generation for Spīve applications such as Spive app itself and
 * examples in repo.
 *
 * <p>Make do with a hardcoded list of configs, while the intended flow (through UI and managed
 * streams, and artifact static analysis) is not yet bootstrapped in a usable state.
 */
public class GenerateIocCode {
  public static void main(final String... args) throws Exception {
    final Map<String, AppIoc.AppDescriptor> configs =
        Map.of(
            "app/src/main/java/io/ulzha/spive/app/spive/gen",
            new AppIoc.AppDescriptor(
                "io.ulzha.spive.app.Spive",
                List.of(
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateEventLog"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateInstance"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateProcess"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateStream"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateType"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.DeleteInstance"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.DeleteProcess"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.InstanceIopw"),
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.InstanceProgress"),
                    new AppIoc.EventDescriptor(
                        "pojo:io.ulzha.spive.app.events.InstanceStatusChange")),
                List.of(
                    new AppIoc.WorkloadDescriptor("Watchdog"),
                    new AppIoc.WorkloadDescriptor("Api")),
                List.of(
                    new AppIoc.GatewayDescriptor(
                        "io.ulzha.spive.basicrunner.api.BasicRunnerGateway", "runner")),
                true),
            "scaler/app/src/main/java/io/ulzha/spive/scaler/app/spive/gen",
            new AppIoc.AppDescriptor(
                "io.ulzha.spive.scaler.app.SpiveScaler",
                List.of(
                    new AppIoc.EventDescriptor("pojo:io.ulzha.spive.app.events.CreateInstance"),
                    new AppIoc.EventDescriptor(
                        "pojo:io.ulzha.spive.scaler.app.events.ScaleProcess")),
                List.of(new AppIoc.WorkloadDescriptor("Watchdog")),
                List.of(),
                true),
            "example/clicc-tracc/app/src/main/java/io/ulzha/spive/example/clicctracc/app/spive/gen",
            new AppIoc.AppDescriptor(
                "io.ulzha.spive.example.clicctracc.app.CliccTracc",
                List.of(
                    new AppIoc.EventDescriptor(
                        "pojo:io.ulzha.spive.example.clicctracc.app.events.Clicc")),
                List.of(new AppIoc.WorkloadDescriptor("Metronome")),
                List.of(),
                true),
            "example/copy/app/src/main/java/io/ulzha/spive/example/copy/app/spive/gen",
            new AppIoc.AppDescriptor(
                "io.ulzha.spive.example.copy.app.Copy",
                List.of(
                    new AppIoc.EventDescriptor(
                        "pojo:io.ulzha.spive.example.copy.app.events.CreateFoo")),
                List.of(),
                List.of(),
                false));

    for (String dir : configs.keySet()) {
      AppIoc.generateAppInstanceCode(configs.get(dir), dir);
      AppIoc.generateAppOutputGatewayCode(configs.get(dir), dir);
    }
  }
}
