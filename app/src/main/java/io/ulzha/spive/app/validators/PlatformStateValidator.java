package io.ulzha.spive.app.validators;

import io.ulzha.spive.app.events.CreateProcess;
import io.ulzha.spive.app.model.Platform;
import java.util.List;
import java.util.UUID;

// state_helpers?
// request_helpers? interaction helpers? (artifact url)
// input and output helpers?

/**
 * Business logic to guard model state transitions.
 *
 * <p>Collected here to be reusable by API and other workloads, thus keeping their code concise and
 * focused on the happy path.
 */
public record PlatformStateValidator(Platform platform, List<String> validationErrors) {
  public boolean isValidFor(CreateProcess event) {
    // In case state is invalid, we are nice and collect a comprehensive error report. This
    // information will be returned in API response and help the caller.
    if (platform.processesByApplicationAndVersion.containsKey(event.name())
        && platform
            .processesByApplicationAndVersion
            .get(event.name())
            .containsKey(event.version())) {
      validationErrors.add("Process with the same name and version already exists");
    }
    if (platform.processesById.containsKey(event.processId())) {
      validationErrors.add("Process with the same ID already exists");
    }
    for (UUID inputStreamId : event.inputStreamIds()) {
      if (!platform.streamsById.containsKey(inputStreamId)) {
        validationErrors.add("Input stream with ID " + inputStreamId + " does not exist");
      }
    }
    // TODO ensure that the output stream exists and isn't owned by a different process
    // TODO ensure naming of output stream matches application name (maybe it just has to be created
    // here with the process...)
    // TODO ensure that the types of events are compatible - `pojo:` as a tag is perhaps confusing
    // now, deserialization may use a different programming language altogether. So the
    // compatibility is to be worked out at field type level.
    // TODO ensure that there are no cycles of streams

    return validationErrors.isEmpty();
  }
}
