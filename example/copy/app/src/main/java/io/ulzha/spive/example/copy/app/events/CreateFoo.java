package io.ulzha.spive.example.copy.app.events;

import java.net.URI;
import lombok.Data;

// not sure where this comes from - also generated? Should reside in lib/? Or Artifactory always?
@Data
public class CreateFoo {
  public URI fooUri; // partition key
  public URI barUri; // partition key
  public String name;
}
