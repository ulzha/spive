/** @jsxImportSource react */

import { useRef, useState } from "react";
import { Button, ListItem, ListItemAvatar, ListItemButton, ListItemText, Popover } from "@mui/material";
import { ExpandLess, ExpandMore } from "@mui/icons-material";

function Item(props: any) {
  const [open, setOpen] = useState(false);

  return (
    <ListItem alignItems="flex-start">
      <ListItemAvatar>
        <span className={props.className}></span>
      </ListItemAvatar>
      <ListItemText
        primary={props.primaryText}
        secondary={
          <div>
            {props.secondaryText}
            {open && props.collapsibleText}
          </div>
        }
        secondaryTypographyProps={{ component: "div" }}
      />
      <ListItemButton onClick={() => setOpen(!open)}>{open ? <ExpandLess /> : <ExpandMore />}</ListItemButton>
    </ListItem>
  );
}

const content = (
  <div>
    <Item
      className="legend-pin color-info"
      primaryText="Heads up"
      secondaryText="Blue pins mark maintenance events that may affect application health."
      collapsibleText={
        <p>
          Examples include: rollout of a new input stream version or new code, instance restoration from a snapshot, and
          so on.
        </p>
      }
    />

    <Item
      className="legend-swatch color-incoming"
      primaryText="Incoming"
      secondaryText="Grey bars indicate input events that have not been handled yet."
      collapsibleText={
        <p>
          Flashing grey bars indicate input events as they keep arriving. Blurred timeline area indicates the interval
          leading up to current time, where unknown numbers of new input events may appear.
        </p>
      }
    />

    <Item
      className="legend-swatch"
      primaryText="In progress"
      secondaryText="Flashing colored bars indicate events that are being handled or just finished handling."
      collapsibleText={
        <>
          <p>
            Blinking vertical lines indicate the last successfully handled event time (more specifically, event time
            watermark) of each application instance.
          </p>
          <p>Note: this indication is refreshed more eagerly when you mouseover.</p>
        </>
      }
    />

    <Item
      className="legend-swatch color-ok"
      primaryText="OK"
      secondaryText="Green bars indicate events which have been handled successfully, and for which no warnings have been reported by runners."
      collapsibleText={
        <p>
          TODO output events
          {/* Do they add to height? Do they extend down? Do they sit side by side? Do they form a fronting sparkline with a thin top border? It's often 100% height though? And they must not hide the error and warning colors behind them. */}
        </p>
      }
    />

    <Item
      className="legend-swatch color-warning"
      primaryText="Warning"
      secondaryText="Orange bars indicate events whose handling has stalled or run into intermittent failures."
      collapsibleText={
        <>
          <p>
            Stalling happens e.g. if a handler deadlocks, or instance's runner disappears from network. Intermittent
            failures happen when a gateway fails to perform side effects (such as calling an external system) and
            reports errors to Spīve, and proceeds to retry.
          </p>
          <p>
            The bar stays orange for the lifetime of the process instance associated with the occurrence, even if the
            event ends up handled successfully in a while.
          </p>
          <p>Note: this indication is best-effort — not every occurrence is tracked.</p>
        </>
      }
    />

    <Item
      className="legend-swatch color-error"
      primaryText="Error"
      secondaryText="Red bars indicate events whose handlers crashed."
      collapsibleText={
        <p>
          Note: this indication is reliable, assuming the runner is not promptly lost — progress on a stream partition
          stops as soon as the first crash occurs, and the runner attempts to report crash information back
          indefinitely.
        </p>
      }
    />

    <Item
      className="legend-swatch"
      primaryText="Instances timing out"
      secondaryText="Orange horizontal lines indicate intervals in event time where at least one application instance is in TIMEOUT state."
      collapsibleText={
        <>
          <p>
            Thicker lines correspond to more partitions affected. Happens when events exceed their nominal<sup>TBD</sup>{" "}
            handling time (which may be due to e.g. heavy computation in an event handler, or many retries in a gateway,
            or just a single external call taking very long). The events may eventually get handled successfully.
          </p>
          <p>
            Orange lines will remain until there is no event being handled and taking excessive time, on any instance.
          </p>
          <p>Note: the thickness indication is a lower bound — not every occurrence is tracked.</p>
        </>
      }
    />

    <Item
      className="legend-swatch"
      primaryText="Instances failing"
      // TODO wall clock time? Both important? Wall clock time in another column overlaid with cost?
      secondaryText="Red horizontal lines indicate intervals in event time where at least one application instance is in ERROR state."
      collapsibleText={
        <>
          <p>
            Thicker lines correspond to more partitions affected. Happens when an event handler crashes. The application
            code may need fixing, unless the crashing behavior goes away by automated replacement of the affected
            instance. Instances will be stopped and partitions retried on newly spawned ones, potentially scaled up.
          </p>
          <p>
            Red lines will remain until all the events that had crashes get handled successfully (or until a developer
            swaps out application's input stream altogether).
          </p>
          <p>
            Note: this indication is reliable, assuming the runner is not promptly lost — progress on a stream partition
            stops as soon as the first crash occurs, and the runner attempts to report crash information back
            indefinitely.
          </p>
        </>
      }
    />

    <p>Hover over the event bars in the timeline to inspect exact counts.</p>
    <p>Click them to see events in full detail, and troubleshoot eventual warnings or errors.</p>
  </div>
);

export default function Legend() {
  const [open, setOpen] = useState(false);
  const [el, setEl] = useState<HTMLElement | null>(null);
  const containerRef = useRef();

  console.log(el?.closest(".padding"));
  return (
    <>
      <Popover
        id="legend"
        open={open}
        anchorEl={el}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: 580 + 48 + 24, // "right" gets distorted when container is not matching viewport
        }}
        container={el?.closest(".padding")} // passepartout? Doesn't seem to stretch it
        onClose={() => {
          setOpen(false);
        }}
      >
        {content}
      </Popover>
      <Button
        id="legend-toggle"
        onClick={(e) => {
          setEl(e.currentTarget);
          setOpen(!open);
        }}
      >
        Legend
      </Button>
    </>
  );
}
