package io.ulzha.spive.core;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.InternalSpiveException;
import io.ulzha.spive.util.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class LocalFileSystemEventLog implements EventLog {
  private final Path filePath;
  private final SeekableByteChannel channel;
  // (Found a few examples like GoogleCloudStorageReadChannel implements SeekableByteChannel)

  public LocalFileSystemEventLog(final Path filePath) throws IOException {
    this.filePath = filePath;
    this.channel =
        Files.newByteChannel(
            filePath,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC);
  }

  /**
   * Reads one event from reader.
   *
   * <p>Will block at the end of file until more events are appended or the log is closed.
   *
   * @return the next event, or null to signify a closed log.
   * @param reader
   */
  private static EventEnvelope read(BufferedReader reader) throws IOException {
    String line = reader.readLine();

    while (line == null) {
      try {
        Thread.sleep(1000);
        // TODO WatchService, to act more quickly than this polling loop?
        line = reader.readLine();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    return Json.deserializeEventEnvelope(line);
  }

  /** Reads one event from channel and leaves channel open. */
  private static EventEnvelope read(SeekableByteChannel channel) throws IOException {
    final String line = StandardCharsets.UTF_8.decode(readLine(channel)).toString();
    return Json.deserializeEventEnvelope(line);
  }

  //  LocalFileSystemEventLog position(EventTime newTime) {
  //  }

  private boolean append(EventEnvelope event) throws IOException {
    try (Writer writer = Channels.newWriter(channel, StandardCharsets.UTF_8)) {
      writer.append(Json.serializeEventEnvelope(event));
      writer.append('\n');
      return true;
    }
  }

  @Override
  public EventTime appendAndGetAdjustedTime(final EventEnvelope event) throws IOException {
    throw new RuntimeException("not implemented");
  }

  @Override
  public boolean appendIfPrevTimeMatch(EventEnvelope event, EventTime prevTime) throws IOException {
    if (event.time.compareTo(prevTime) <= 0) {
      throw new IllegalArgumentException("event must have time later than prevTime");
    }
    // FIXME lock file to do this reliably
    if (channel.size() > 0) {
      seekToLastLine();
      final EventEnvelope latestEvent = read(channel);
      if (latestEvent.time.compareTo(prevTime) == 0) {
        return append(event);
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
      buffer.reset();
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
  private static ByteBuffer readLine(SeekableByteChannel channel) throws IOException {
    final int chunkSize = 1024;
    ArrayList<ByteBuffer> tmp = new ArrayList<>();
    long oldPosition = channel.position();
    long newPosition = oldPosition;
    while (true) {
      ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
      int read = channel.read(buffer);
      newPosition += read;
      if (read > 0) {
        tmp.add(buffer.flip());
        int i;
        for (i = 0; i < read; i++) {
          if (buffer.get(i) == '\n') {
            newPosition -= read - i - 1;
            channel.position(newPosition);
            buffer.limit(i);
            break;
          }
        }
        if (i < read) {
          break;
        }
      } else {
        break;
      }
    }

    ByteBuffer ret = ByteBuffer.allocate((int) (newPosition - oldPosition)); // may have one surplus
    for (ByteBuffer buffer : tmp) {
      ret.put(buffer);
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
  public Iterator<EventEnvelope> iterator() {
    return new EventIterator();
  }

  public class EventIterator implements Iterator<EventEnvelope> {
    private final SeekableByteChannel readChannel;
    private final BufferedReader reader;
    private EventEnvelope previousEvent;
    private EventEnvelope nextEvent;

    public EventIterator() {
      try {
        readChannel = Files.newByteChannel(filePath, StandardOpenOption.READ);
        try {
          reader = new BufferedReader(Channels.newReader(readChannel, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
          readChannel.close();
          throw e;
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /** Will block after the last event until more events are appended or the log is closed. */
    @Override
    public boolean hasNext() {
      if (nextEvent == null) {
        try {
          nextEvent = read(reader);
          if (previousEvent != null
              && nextEvent != null
              && nextEvent.time.compareTo(previousEvent.time) <= 0) {
            throw new InternalSpiveException(
                String.format(
                    "Out-of-order event sequence: %s followed by %s in %s",
                        previousEvent.time, nextEvent.time, filePath));
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

    private void close() throws IOException {
      reader.close();
      readChannel.close();
    }
  }
}
