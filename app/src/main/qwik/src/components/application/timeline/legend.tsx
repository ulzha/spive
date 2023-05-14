import { component$, useSignal } from "@builder.io/qwik";
import { MUIButton, MUIPopover } from "~/integrations/react/mui";

const content = (
  <div id="legend">
    <ul>
      <li title="Grey bars indicate input events in input streams that have not been handled yet. Blurred timeline area indicates the interval leading up to current time, where unknown numbers of new input events may appear.">
        <span class="legend-swatch color-incoming"></span>
        <span>Incoming</span>
      </li>

      {/* Perhaps this is not very necessary. When expanding to include inputs, we will see input progress, and events are "pending" as much as the input application is ahead. Also the blue color is a bit ugly. */}
      {/* <li title="Blue bars indicate input events exist in input streams but have not been handled yet"><span class="legend-swatch color-pending"></span><span>Pending</span></li> */}

      <li title="Flashing spots indicate events that are being handled or just finished handling. Blinking vertical lines indicate the last handled event time of each application instance. Note: this indication is approximate and delayed from real time.">
        <span class="legend-swatch"></span>
        <span>In Progress</span>
      </li>

      <li title="Green bars indicate events which have been handled successfully, and for which no warning or error information is logged by runner.">
        <span class="legend-swatch color-ok"></span>
        <span>OK</span>
      </li>
      {/* TODO output events... Do they add to height? Do they extend down? Do they sit side by side? Do they form a fronting sparkline with a thin top border? */}

      <li title="Orange bars indicate events whose handling has run into intermittent failures. Happens when a gateway receives an error from an external system, and proceeds to retry the call. The bar stays orange even if the event ends up handled successfully. Note: this indication is best-effort - not every occurrence is logged.">
        <span class="legend-swatch color-warning"></span>
        <span>Warning</span>
      </li>

      <li title="Red bars indicate events whose handlers crashed. Note: this indication is reliable, assuming the runner is not lost - progress on a stream partition stops as soon as the first crash occurs, and the runner attempts to report crash information back indefinitely.">
        <span class="legend-swatch color-error"></span>
        <span>Error</span>
      </li>

      {/* So far only sketching UI for basic KTLO operations. Will need to graph data quality information as an additional layer. Or extend the Warning status with more conditions? */}

      {/* TODO wall clock time? Both important? Wall clock time in another column overlaid with cost? */}
      <li title="Orange horizontal lines indicate intervals in event time where at least one application instance is in TIMEOUT state. Thicker lines correspond to more partitions affected. Happens when events exceed their nominal handling time (which may be due to e.g. long processing in event handler handler, or many retries in gateway, or just a single external call taking very long). The events may eventually get handled successfully. Note: this indication is best-effort - not every occurrence is logged.">
        <span class="legend-swatch"></span>
        <span>Instances timing out</span>
      </li>

      {/* TODO wall clock time? Both important? Wall clock time in another column overlaid with cost? */}
      <li title="Red horizontal lines indicate intervals in event time where at least one application instance is in ERROR state. Thicker lines correspond to more partitions affected. Happens when an event handler crashes. The application code may need fixing (unless the crashing behavior goes away by automated replacement of the affected instance. Instances will be stopped and partitions retried on newly spawned ones, potentially scaled up. Red lines will remain until caught up with the events that had crashes). Note: this indication is reliable, assuming the runner is not lost - progress on a stream partition stops as soon as the first crash occurs, and the runner attempts to report crash information back indefinitely.">
        <span class="legend-swatch"></span>
        <span>Instances failing</span>
      </li>
    </ul>
    <p>
      Hover over the event bars in the timeline to inspect event details and troubleshoot eventual warnings or errors.
    </p>
  </div>
);

export default component$(() => {
  const open = useSignal(false);
  const el = useSignal<HTMLElement | null>(null);

  return (
    <>
      <MUIPopover
        open={open.value}
        anchorEl={el.value}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
        onClose$={() => {
          open.value = false;
        }}
      >
        {content}
      </MUIPopover>
      <MUIButton
        id="legend-toggle"
        client:visible
        onClick$={({ target }) => {
          if (target instanceof HTMLElement) {
            open.value = !open.value;
            el.value = target.closest("button");
          }
        }}
      >
        Legend
      </MUIButton>
      {/* ^ looks overcomplicated, why can't I just set anchorEl as id or any other static way */}
      {/* <MUIButton client: visible onClick$={(e) => {open.value = !open.value; el.value = e.target!.closest('button')!; alert(el.value.tagName); }}>leg-end</MUIButton> */}
    </>
  );
});
