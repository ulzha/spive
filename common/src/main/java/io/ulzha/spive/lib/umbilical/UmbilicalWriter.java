package io.ulzha.spive.lib.umbilical;

/** (Initially aimed to just use some functional interfaces but they became many) */
// It's confusing to talk direction but less confusing to talk scope? EventHandlerUmbilical?
public interface UmbilicalWriter {
  public boolean getReplayMode();

  public void addSuccess();

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

  /**
   * Set by event loop before every event handled.
   *
   * @param warning
   */
  public void addHeartbeat();
}
