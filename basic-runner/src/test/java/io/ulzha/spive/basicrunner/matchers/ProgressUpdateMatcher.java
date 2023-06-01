package io.ulzha.spive.basicrunner.matchers;

import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ProgressUpdateMatcher extends TypeSafeMatcher<List<ProgressUpdate>> {

  private final ProgressUpdate[] progressUpdates;

  private ProgressUpdateMatcher(final ProgressUpdate... progressUpdates) {
    this.progressUpdates = progressUpdates;
  }

  @Override
  protected boolean matchesSafely(final List<ProgressUpdate> progressUpdates) {
    if (progressUpdates.size() != this.progressUpdates.length) return false;
    for (int i = 0; i < progressUpdates.size(); i++) {
      final ProgressUpdate expected = this.progressUpdates[i];
      final ProgressUpdate actual = progressUpdates.get(i);
      if (expected.success() != actual.success()) return false;
      if (!Objects.equals(expected.warning(), actual.warning())) return false;
      if (!Objects.equals(expected.error(), actual.error())) return false;
    }
    return true;
  }

  @Override
  public void describeTo(final Description description) {
    description.appendText("progress updates ignoring time: " + Arrays.toString(progressUpdates));
  }

  public static Matcher<List<ProgressUpdate>> containsIgnoringTime(
      ProgressUpdate... progressUpdates) {
    return new ProgressUpdateMatcher(progressUpdates);
  }
}
