package io.ulzha.spive.lib;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventTimeTest {

  @Test
  void testIsInfinitePast() {
    final EventTime t = EventTime.INFINITE_PAST;

    assertTrue(t.isInfinitePast());

    final EventTime now = EventTime.ofNow();

    assertFalse(now.isInfinitePast());
  }

  @Test
  void testCompareTo() {
    final EventTime t0 = EventTime.INFINITE_PAST;
    final EventTime tRex = new EventTime(Instant.ofEpochSecond(-2220000000000000L), 222);
    final EventTime t1 = new EventTime(Instant.ofEpochSecond(1611257224), 1);
    final EventTime t2 = new EventTime(Instant.ofEpochSecond(1611257224), 2);
    final EventTime t3 = new EventTime(Instant.ofEpochSecond(1611257225), 1);
    final EventTime t4 = new EventTime(Instant.ofEpochSecond(1611257225), 2);

    assertEquals(0, t0.compareTo(t0));
    assertEquals(0, tRex.compareTo(tRex));
    assertEquals(0, t1.compareTo(t1));
    assertEquals(0, t2.compareTo(t2));
    assertEquals(0, t3.compareTo(t3));
    assertEquals(0, t4.compareTo(t4));
    assertEquals(-1, t0.compareTo(tRex));
    assertEquals(-1, tRex.compareTo(t1));
    assertEquals(-1, t1.compareTo(t2));
    assertEquals(-1, t2.compareTo(t3));
    assertEquals(-1, t3.compareTo(t4));
    assertEquals(1, tRex.compareTo(t0));
    assertEquals(1, t1.compareTo(tRex));
    assertEquals(1, t2.compareTo(t1));
    assertEquals(1, t3.compareTo(t2));
    assertEquals(1, t4.compareTo(t3));
  }

  @Test
  void testRoundTripToString() {
    assertRoundTripToString(EventTime.INFINITE_PAST, "-inf#-1");
    assertRoundTripToString(new EventTime(Instant.MIN, 0), "-1000000000-01-01T00:00:00Z#0");
    assertRoundTripToString(
        new EventTime(Instant.MAX, 999999999), "+1000000000-12-31T23:59:59.999999999Z#999999999");
    assertRoundTripToString(
        new EventTime(Instant.ofEpochSecond(-2220000000000000L), 222),
        "-70347030-07-07T13:20:00Z#222");
    assertRoundTripToString(
        new EventTime(Instant.ofEpochSecond(1611257224), 1), "2021-01-21T19:27:04Z#1");
    assertRoundTripToString(
        new EventTime(Instant.ofEpochSecond(1611257224, 10_000_000), 2),
        "2021-01-21T19:27:04.010Z#2");
    assertRoundTripToString(
        new EventTime(Instant.ofEpochSecond(1611257224, 1), 12345),
        "2021-01-21T19:27:04.000000001Z#12345");
  }

  @Test
  void testFromStringInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromString(null));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromString(""));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromString("0"));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromString("1"));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromString("#"));
    // less than Instant.MIN
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromString("-1000000001-01-01T00:00:00Z#0"));
    // more than Instant.MAX
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromString("+1000000001-01-01T00:00:00Z#0"));
    // invalid day of month
    assertThrows(
        IllegalArgumentException.class, () -> EventTime.fromString("2021-02-30T10:00:00.010Z#0"));
    // tiebreaker out of bounds
    assertThrows(
        IllegalArgumentException.class, () -> EventTime.fromString("2021-01-21T19:27:04.010Z#-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromString("2021-01-21T19:27:04.010Z#1000000000"));
    // legal argument to fromOrderPreservingString()
    assertThrows(
        IllegalArgumentException.class, () -> EventTime.fromString("100000001611257224.000000000"));
  }

  @Test
  void testRoundTripToOrderPreservingString() {
    assertRoundTripToOrderPreservingString(EventTime.INFINITE_PAST, "0");
    assertRoundTripToOrderPreservingString(
        new EventTime(Instant.MIN, 0), "068442985832780800.000000000");
    assertRoundTripToOrderPreservingString(
        new EventTime(Instant.MAX, 999999999), "131556889864403199.999999999#999999999");
    assertRoundTripToOrderPreservingString(
        new EventTime(Instant.ofEpochSecond(-2220000000000000L), 222),
        "097780000000000000.000000000#000000222");
    assertRoundTripToOrderPreservingString(
        new EventTime(Instant.ofEpochSecond(1611257224), 1),
        "100000001611257224.000000000#000000001");
    assertRoundTripToOrderPreservingString(
        new EventTime(Instant.ofEpochSecond(1611257224, 10_000_000), 2),
        "100000001611257224.010000000#000000002");
    assertRoundTripToOrderPreservingString(
        new EventTime(Instant.ofEpochSecond(1611257224, 1), 12345),
        "100000001611257224.000000001#000012345");
  }

  @Test
  void testFromOrderPreservingStringInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromOrderPreservingString(null));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromOrderPreservingString(""));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromOrderPreservingString("-inf"));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromOrderPreservingString("1"));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromOrderPreservingString("2"));
    assertThrows(IllegalArgumentException.class, () -> EventTime.fromOrderPreservingString("#"));
    assertThrows(
        IllegalArgumentException.class, () -> EventTime.fromOrderPreservingString("1.2#3"));
    // less than Instant.MIN
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("068442985832780799.999999999"));
    // more than Instant.MAX
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("131556889864403200.000000000"));
    // timestamp too short
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("10000000161125722.010000000"));
    // tiebreaker too short
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("100000001611257224.010000000#00000000"));
    // redundant tiebreaker
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("100000001611257224.010000000#000000000"));
    // starts with 2
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("200000001611257224.010000000#000000002"));
    // extra trailing digit
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("100000001611257224.010000000#0000000020"));
    // negative nanos
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("100000001611257224.-10000000#000000002"));
    // legal argument to fromString()
    assertThrows(
        IllegalArgumentException.class,
        () -> EventTime.fromOrderPreservingString("2021-01-21T19:27:04Z#0"));
  }

  // Why did these pass even without equals() overridden, but SpiveModuleTest didn't?
  private static void assertRoundTripToString(EventTime t, String s) {
    assertEquals(s, t.toString());
    assertEquals(t, EventTime.fromString(s));
  }

  private static void assertRoundTripToOrderPreservingString(EventTime t, String s) {
    assertEquals(s, t.toOrderPreservingString());
    assertEquals(t, EventTime.fromOrderPreservingString(s));
  }
}
