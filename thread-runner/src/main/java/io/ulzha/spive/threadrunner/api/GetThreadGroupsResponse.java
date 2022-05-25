package io.ulzha.spive.threadrunner.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class GetThreadGroupsResponse {
  @JsonProperty("threadGroups")
  public abstract List<ThreadGroupDescriptor> threadGroups();

  @JsonCreator
  public static GetThreadGroupsResponse create(
      @JsonProperty("threadGroups") final List<ThreadGroupDescriptor> threadGroups) {
    return new AutoValue_GetThreadGroupsResponse(threadGroups);
  }
}
