package io.ulzha.spive.lib.umbilical;

import io.github.resilience4j.circularbuffer.ConcurrentEvictingQueue;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A bit like ConcurrentEvictingQueue<ProgressUpdate> but aware of firsts to keep.
 *
 * <p>TODO Also should detect contradictory sequences, like a failure before handler start instant,
 * or a failure after handler end (workloads ATTOW intersperse updates with handler updates).
 * Safeguard against clock weirdness causing misinterpretation.
 */
public class ConcurrentProgressUpdatesList {
  final ProgressUpdate start;
  AtomicReference<ProgressUpdate> firstError = new AtomicReference<>();
  ConcurrentEvictingQueue<ProgressUpdate> rest = new ConcurrentEvictingQueue<>(3);

  public ConcurrentProgressUpdatesList(ProgressUpdate start) {
    this.start = start;
  }

  public void add(ProgressUpdate update) {
    if (update.error() != null || !firstError.compareAndSet(null, update)) {
      rest.add(update);
    }
  }

  public List<ProgressUpdate> toList() {
    Set<ProgressUpdate> allUpdates = new HashSet<>();
    allUpdates.add(start);
    allUpdates.add(firstError.get());
    allUpdates.addAll(rest);
    return allUpdates.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(ProgressUpdate::instant))
        .collect(Collectors.toList());
  }
}
