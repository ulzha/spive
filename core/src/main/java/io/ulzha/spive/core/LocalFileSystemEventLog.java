package io.ulzha.spive.core;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.InternalException;
import io.ulzha.spive.util.Json;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Set;

public final class LocalFileSystemEventLog implements EventLog {
  private final Path filePath;
  private final FileChannel channel;
  // (Found a few examples like GoogleCloudStorageReadChannel implements SeekableByteChannel)

  public LocalFileSystemEventLog(final Path filePath) throws IOException {
    this.filePath = filePath;

    this.channel =
        FileChannel.open(
            filePath,
            Set.of(
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.SYNC));
  }

  /**
   * Reads one event from channel and leaves channel open.
   *
   * <p>Will block at the end of file until more events are appended or the log is closed.
   *
   * @param channel
   * @return null if there are no more events to read (end of channel has been reached) and the log
   *     is closed.
   */
  private static EventEnvelope read(FileChannel channel) throws IOException {
    String line = StandardCharsets.UTF_8.decode(readLine(channel)).toString();

    while (line.equals("")) {
      try {
        Thread.sleep(1000);
        // TODO WatchService, to act more quickly than this polling loop?
        line = StandardCharsets.UTF_8.decode(readLine(channel)).toString();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    return Json.deserializeEventEnvelope(line);
  }

  //  LocalFileSystemEventLog position(EventTime newTime) {
  //  }

  private boolean append(EventEnvelope event) throws IOException {
    final Writer writer = Channels.newWriter(channel, StandardCharsets.UTF_8);
    writer.append(Json.serializeEventEnvelope(event));
    writer.append('\n');
    writer.flush();
    return true;
  }

  @Override
  public EventTime appendAndGetAdjustedTime(final EventEnvelope event) throws IOException {
    throw new RuntimeException("not implemented");
  }

  @Override
  public boolean appendIfPrevTimeMatch(EventEnvelope event, EventTime prevTime) throws IOException {
    if (event.time().compareTo(prevTime) <= 0) {
      throw new IllegalArgumentException("event must have time later than prevTime");
    }
    // TODO version with position hint, usable from EventIterator, with less seeking

    // atomically compare with previous time and append
    try (FileLock lock = channel.lock()) {
      if (channel.size() > 0) {
        seekToLastLine();
        final EventEnvelope latestEvent = read(channel);
        if (latestEvent == null) {
          throw new IllegalStateException("log is closed");
        }
        if (latestEvent.time().compareTo(prevTime) == 0) {
          return append(event);
        }
      }
    }
    return false;
  }

  /** Seeks to the beginning of the last line, or does nothing if the channel has size 0. */
  private void seekToLastLine() throws IOException {
    final int chunkSize = 1024;
    final ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
    long p = channel.size();
    do {
      p = Math.max(p - chunkSize, 0);
      channel.position(p);
      buffer.clear();
      channel.read(buffer);
      for (int i = 0; i < buffer.limit(); i++) {
        if (buffer.get(i) == '\n' && p + i + 1 != channel.size()) {
          channel.position(p + i + 1);
          return;
        }
      }
    } while (p > 0);
  }

  /**
   * Reads from current position until '\n' is read or end of channel is reached, whichever comes
   * first, and leaves channel open.
   *
   * @return the bytes read, skipping an eventual trailing '\n'
   */
  private static ByteBuffer readLine(FileChannel channel) throws IOException {
    final int chunkSize = 1024;
    ArrayList<ByteBuffer> tmp = new ArrayList<>();
    long oldPosition = channel.position();
    long newPosition = oldPosition;
    while (true) {
      ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
      int read = channel.read(buffer);
      if (read > 0) {
        tmp.add(buffer.flip());
        int i;
        for (i = 0; i < read; i++) {
          if (buffer.get(i) == '\n') {
            break;
          }
        }
        if (i < read) {
          newPosition += i + 1;
          buffer.limit(i + 1);
          break;
        } else {
          newPosition += read;
        }
      } else {
        break;
      }
    }
    channel.position(newPosition);

    ByteBuffer ret = ByteBuffer.allocate((int) (newPosition - oldPosition));
    for (ByteBuffer buffer : tmp) {
      ret.put(buffer);
    }
    if (ret.limit() > 0 && ret.get(ret.limit() - 1) == '\n') {
      ret.limit(ret.limit() - 1);
    }
    ret.rewind();
    return ret;
  }

  @Override
  public void close() throws Exception {
    channel.close();
  }

  @Override
  public String toString() {
    return this.getClass().getCanonicalName() + "(filePath=" + filePath + ")";
  }

  @Override
  public AppendIterator iterator() {
    return new AppendIteratorImpl();
  }

  private class AppendIteratorImpl implements AppendIterator {
    private final FileChannel readChannel;
    private EventEnvelope previousEvent;
    private EventEnvelope nextEvent;

    public AppendIteratorImpl() {
      try {
        readChannel = FileChannel.open(filePath, Set.of(StandardOpenOption.READ));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /** Will block after the last event until more events are appended or the log is closed. */
    @Override
    public boolean hasNext() {
      if (nextEvent == null) {
        try {
          nextEvent = read(readChannel);
          if (previousEvent != null
              && nextEvent != null
              && nextEvent.time().compareTo(previousEvent.time()) <= 0) {
            throw new InternalException(
                String.format(
                    "Out-of-order event sequence: %s followed by %s in %s",
                    previousEvent.time(), nextEvent.time(), filePath));
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      if (nextEvent == null) {
        try {
          close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return false;
      }
      return true;
    }

    @Override
    public EventEnvelope next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      previousEvent = nextEvent;
      nextEvent = null;
      return previousEvent;
    }

    @Override
    public EventEnvelope appendOrPeek(EventEnvelope event) {
      final EventTime previousTime =
          (previousEvent == null ? EventTime.INFINITE_PAST : previousEvent.time());
      try {
        if (LocalFileSystemEventLog.this.appendIfPrevTimeMatch(event, previousTime)) {
          return event;
        } else {
          if (!hasNext()) { // sets nextEvent
            throw new IllegalStateException(
                "expected a subsequent event after "
                    + previousTime
                    + ", but log prematurely indicates end");
          }
          return nextEvent;
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "(" + LocalFileSystemEventLog.this + ")";
    }

    private void close() throws IOException {
      readChannel.close();
    }
  }
}
