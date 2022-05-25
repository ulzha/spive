package io.ulzha.spive.lib.umbilical;

/** (Initially aimed to just use some functional interfaces but they became many) */
public interface UmbilicalWriter {
  public boolean getReplayMode();

  /**
   * Added by instance, whenever an event handler crashes (permanent failure). Also added by gateway
   * before exiting to the handler with a permanent failure.
   *
   * @param error
   */
  public void addError(Throwable error);

  /**
   * Added by gateway before retrying on intermittent failures.
   *
   * @param warning
   */
  public void addWarning(Throwable warning);
}
