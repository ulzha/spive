package io.ulzha.spive.threadrunner.api;

import java.util.List;

// TODO recordify
public class ThreadGroupDescriptor {
  public String name; // should be a unique identifier, e.g. a string including Spīve instance ID.
  public String artifactUrl;
  public String mainClass;

  /* contains input store and stream ID, and possibly gateway initializers (e.g. output store and stream ID, runner pool, in the case of Spive itself), and some sort of workflow selector */
  public List<String> args;
}
