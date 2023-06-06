package io.ulzha.spive.example.copy.app.events;

import java.net.URI;

// not sure where this comes from - also generated? Should reside in lib/? Or Artifactory always?
public record CreateFoo(
    // partition key
    URI fooUri,
    // partition key
    URI barUri,
    String name) {}
