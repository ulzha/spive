package io.ulzha.spive.basicrunner.api;

import java.util.List;

public record ThreadGroupDescriptor(
    // should be a unique identifier, e.g. a string including Spīve instance ID.
    String name,
    String artifactUrl,
    // unneeded?
    String mainClass,

    /* contains input store and stream ID, and possibly gateway initializers (e.g. output store and stream ID, runner pool, in the case of Spive itself), and some sort of workflow selector */
    List<String> args) {}
