package io.ulzha.spive.core;

/**
 * TODO compare with Bigtable.
 *
 * <p>Stores streams in a Google Cloud Storage bucket, one object per event, structured as
 * `/<logId>/<prevEventTime>`.
 *
 * <p>Use of previous event time lets appendIfPrevTimeMatch be easily implemented using an
 * `if-generation-match:0` precondition. Thus it only takes one RPC per append and there is no need
 * to update objects. (In GCS there is an update limit on each object of once per second.) The first
 * event in a log is stored in `/<logId>/0`.
 *
 * <p>https://cloud.google.com/storage/docs/generations-preconditions#_Preconditions "As a pricing
 * consideration, the GET metadata request that allows you to use preconditions is billed at a rate
 * of $0.004 per 10,000 operations." - but apparently there's no extra cost for
 * `if-generation-match:0`?
 */
final class GcsEventStore {
  //  private final String projectId;
  //  private final String bucketName;

  GcsEventStore(final String projectId, final String bucketName) {
    //    this.projectId = projectId;
    //    this.bucketName = bucketName;
  }
}
