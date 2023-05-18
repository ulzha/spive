package io.ulzha.spive.lib;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Monotonic time, with an increasing integer added as a tiebreaker, so that events simultaneous to
 * the nanosecond can still be totally ordered.
 *
 * <p>Uniquely identifies an Event within its Stream Partition, and also is used to reference a
 * position in a Stream. (The ordering among events coming from different partitions and having the
 * same EventTime is unspecified? Or should we order by partition key? Do we want nondeterminism
 * when materializing/regrouping multiple partitions to one log? Also consider collaboration, when
 * events from different streams are involved...)
 *
 * <p>Tiebreaking in EventTime would potentially be useful for placing causes after effects even
 * when they are carried out by different applications on the distributed Spīve platform. (Given
 * some extra work to add first-class representation of causality?)
 *
 * <p>An application nevertheless accepts the simultaneous Events one by one, in order, and it can
 * produce outputs accordingly that reflect interim states, and requests can be coming to it while
 * in those interim states. There is no built-in signalling about whether all the Events for a given
 * instant have been exhausted, except by appearance of a later instant in the input Partition.
 * (TODO though we could possibly signal about whether _consequential_ events have been exhausted.)
 */
public class EventTime implements Comparable<EventTime> {

  public Instant instant;
  //  public List<Integer> tiebreakerChain; eventually full-blown HLC?
  // http://muratbuffalo.blogspot.com/2014/07/hybrid-logical-clocks.html
  public int tiebreaker;

  public EventTime(final Instant instant, final int tiebreaker) {
    if (tiebreaker < 0 || tiebreaker > 999999999) {
      if (!(instant == Instant.MIN && tiebreaker == -1)) {
        throw new IllegalArgumentException("Tiebreaker out of bounds: " + tiebreaker);
      }
    }
    this.instant = instant;
    this.tiebreaker = tiebreaker;
  }

  public EventTime(final Instant instant) {
    this(instant, 0);
  }

  // just for deserialization to work... But I guess it would work without, if EventTime was a
  // record too
  public EventTime() {
    this(Instant.MIN, -1);
  }

  // Must never appear as ordering key in any event logs.
  public static final EventTime INFINITE_PAST = new EventTime(Instant.MIN, -1);

  // May be used for ordering events "before all business logic events" in event logs?
  // public static final EventTime PAST = new EventTime(Instant.MIN, -...);

  public boolean isInfinitePast() {
    return instant == Instant.MIN && tiebreaker == -1;
  }

  /**
   * Rounds up to millis, to avoid overly verbose serialized event times.
   *
   * <p>(This also should make collisions more likely, which should be embraced, so that we triage
   * robustness against such edge cases sooner rather than later.)
   *
   * <p>Nothing technically should prevent enabling full standard library precision (nanoseconds) if
   * needed for breakneck event rates.
   */
  public static EventTime ofNow() {
    Instant now = Instant.now();
    return new EventTime(now.minusNanos(now.getNano() % 1_000_000), 0);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null) {
      return false;
    }
    if (this.getClass() != o.getClass()) {
      return false;
    }
    return compareTo((EventTime) o) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(instant, tiebreaker);
  }

  @Override
  public int compareTo(final EventTime o) {
    int compareDatetime = instant.compareTo(o.instant);
    if (compareDatetime != 0) {
      return compareDatetime;
    }
    if (tiebreaker != o.tiebreaker) {
      return (tiebreaker < o.tiebreaker ? -1 : 1);
    }
    return 0;
  }

  /**
   * Converts to a human readable string with ISO-8601 time representation, and tiebreaker included.
   *
   * <p>Supports lossless round-trip conversion with fromString().
   */
  @Override
  public String toString() {
    if (isInfinitePast()) {
      return "-inf#-1";
    }
    return instant.toString() + '#' + tiebreaker;
  }

  /**
   * Converts to an ASCII string in such a way that their lexicographical order is the same as the
   * corresponding event time order.
   *
   * <p>The initial character is 0 (if before Unix epoch) or 1, other values reserved for future
   * extensions of the format.
   */
  public String toOrderPreservingString() {
    if (isInfinitePast()) {
      return "0";
    }
    if (tiebreaker == 0) {
      // Instant.MIN.getEpochSecond() == -31557014167219200
      // Instant.MAX.getEpochSecond() ==  31556889864403199
      // (17 digits)
      return String.format(
          "%018d.%09d", instant.getEpochSecond() + 100_000_000_000_000_000L, instant.getNano());
    } else {
      return String.format(
          "%018d.%09d#%09d",
          instant.getEpochSecond() + 100_000_000_000_000_000L, instant.getNano(), tiebreaker);
    }
  }

  /**
   * Parses a human readable string.
   *
   * <p>Supports lossless round-trip conversion with toString().
   */
  public static EventTime fromString(String s) {
    if (s == null) {
      throw new IllegalArgumentException("null");
    }
    if ("-inf#-1".equals(s)) {
      return EventTime.INFINITE_PAST;
    }
    try {
      final int iSep = s.indexOf('#');
      final Instant instant = Instant.parse(s.subSequence(0, iSep));
      final int tiebreaker = Integer.parseInt(s, iSep + 1, s.length(), 10);
      return new EventTime(instant, tiebreaker);
    } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
      throw new IllegalArgumentException('"' + s + '"', e);
    }
  }

  /**
   * Parses output of toOrderPreservingString().
   *
   * <p>Supports lossless round-trip conversion.
   */
  public static EventTime fromOrderPreservingString(String s) {
    if (s == null) {
      throw new IllegalArgumentException("null");
    }
    if ("".equals(s)) {
      throw new IllegalArgumentException("\"\"");
    }
    if ("0".equals(s)) {
      return EventTime.INFINITE_PAST;
    }
    long epochSecond;
    int nano = 0, tiebreaker = 0;
    if (s.charAt(0) == '0' || s.charAt(0) == '1') {
      epochSecond = parseValidLong(s, 0, 18) - 100_000_000_000_000_000L;
      if (s.length() > 18) {
        if (s.charAt(18) == '.') {
          nano = parseValidInt(s, 19, 28);
          if (s.length() > 28) {
            if (s.charAt(28) == '#') {
              tiebreaker = parseValidInt(s, 29, 38);
              if (tiebreaker == 0) {
                // let's stick with a unique allowed representation
                throw new IllegalArgumentException("Redundant tiebreaker: \"" + s + '"');
              }
              if (s.length() > 38) {
                throw new IllegalArgumentException("Input string too long: \"" + s + '"');
              }
            } else {
              throw new IllegalArgumentException("Expected '#' at index 28: \"" + s + '"');
            }
          }
        } else {
          throw new IllegalArgumentException("Expected '.' at index 18: \"" + s + '"');
        }
      }
    } else {
      throw new IllegalArgumentException("Expected '0' or '1' at index 0: \"" + s + '"');
    }
    try {
      Instant instant = Instant.ofEpochSecond(epochSecond, nano);
      return new EventTime(instant, tiebreaker);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException('"' + s + '"', e);
    }
  }

  /** Performs validation specific to our fixed-width serialization of nanos and tiebreakers. */
  private static int parseValidInt(String s, int beginIndex, int endIndex) {
    if (endIndex != beginIndex + 9) {
      throw new IllegalArgumentException("Unexpected length of an int: " + (endIndex - beginIndex));
    }
    if (endIndex > s.length()) {
      throw new IllegalArgumentException(
          "Input string shorter than " + endIndex + ": \"" + s + '"');
    }
    for (int i = beginIndex; i != endIndex; i++) {
      final char c = s.charAt(i);
      if (c < '0' || c > '9') {
        throw new IllegalArgumentException(
            "Expected a decimal digit at index " + i + ": \"" + s + '"');
      }
    }
    return Integer.parseInt(s, beginIndex, endIndex, 10);
  }

  /** Performs validation specific to our fixed-width serialization of timestamps. */
  private static long parseValidLong(String s, int beginIndex, int endIndex) {
    if (endIndex != beginIndex + 18) {
      throw new IllegalArgumentException("Unexpected length of a long: " + (endIndex - beginIndex));
    }
    if (endIndex > s.length()) {
      throw new IllegalArgumentException(
          "Input string shorter than " + endIndex + ": \"" + s + '"');
    }
    for (int i = beginIndex; i != endIndex; i++) {
      final char c = s.charAt(i);
      if (c < '0' || c > '9') {
        throw new IllegalArgumentException(
            "Expected a decimal digit at index " + i + ": \"" + s + '"');
      }
    }
    return Long.parseLong(s, beginIndex, endIndex, 10);
  }
}
