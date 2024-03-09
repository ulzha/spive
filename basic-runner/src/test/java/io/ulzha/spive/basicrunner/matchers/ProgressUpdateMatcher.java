package io.ulzha.spive.basicrunner.matchers;

import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import java.util.Arrays;
import java.util.List;
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
      if (!equalsIgnoringStacktrace(expected.warning(), actual.warning())) return false;
      if (!equalsIgnoringStacktrace(expected.error(), actual.error())) return false;
    }
    return true;
  }

  private static boolean equalsIgnoringStacktrace(String s1, String s2) {
    if (s1 == null) return s2 == null;
    if (s2 == null) return false;
    if (s1.length() == 0 || s2.length() == 0) throw new RuntimeException("Disappoint");
    return s1.startsWith(s2) || s2.startsWith(s1);
  }

  @Override
  public void describeTo(final Description description) {
    description.appendText("progress updates ignoring time: " + Arrays.toString(progressUpdates));
  }

  /** Contains _in order_, ignoring _time_ and ignoring _details of the stacktrace_. */
  public static Matcher<List<ProgressUpdate>> containsEssentially(
      ProgressUpdate... progressUpdates) {
    return new ProgressUpdateMatcher(progressUpdates);
  }
}
