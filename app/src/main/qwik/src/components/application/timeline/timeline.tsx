import { Resource, component$, isBrowser, useResource$ } from "@builder.io/qwik";
import { generateDummyBars } from "./dummy";

declare const addBars: (svgEl: any, bars: any[], blur: any[]) => void;

const platformUrl = import.meta.env.PUBLIC_PLATFORM_URL;

interface TimelineProps {
  processId: string;
  offset: number;
  scale: number;
  resolution: string;
  fetchTrigger: any;
}

export default component$<TimelineProps>(({processId, offset, scale, resolution, fetchTrigger}) => {
  // const now = new Date(); // TODO also keep rendering newest events because user likely will return to that?

  const barsResource = useResource$<any>(async ({ track, cleanup }) => {
    track(() => fetchTrigger.value);
    const abortController = new AbortController();
    cleanup(() => abortController.abort("cleanup"));

    const stop = new Date().getTime() + offset;

    const timelineQueryParams = new URLSearchParams({
      // TODO fit width, don't re-request completed windows
      start: new Date(stop - 3600 * 1000).toISOString(),
      stop: new Date(stop).toISOString(),
      resolution: resolution,
    }).toString();

    const response = await fetch(`${platformUrl}/api/process/${processId}/timeline?${timelineQueryParams}`, {
      signal: abortController.signal,
    });

    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`);
    }

    return response.json();
  });

  const renderTime = new Date().toISOString();
  const propsDump = `${renderTime} ${processId} ${offset} ${scale} ${resolution} ${fetchTrigger.value}`;

  return <Resource
    value={barsResource}
    onPending={() => <p>{`${propsDump} loading...`}</p>}
    onResolved={(timelineResponse) => {
      if (isBrowser) {
        addBars(d3.select(`#timeline-svg-${processId}`), timelineResponse.map(d => ({
          windowStart: d.tile.windowStart * 1000,
          windowEnd: d.tile.windowEnd * 1000,
          height: Math.max(
            Math.min(d.tile.nOutputEvents / (d.tile.windowEnd - d.tile.windowStart), 10),
            d.tile.nOutputEvents ? 1 : 0
          ),
        })));
      }
      return <p>{`${propsDump}`}</p>;
    }}
    onRejected={(reason) => {
      if (isBrowser) {
        generateDummyBars(d3.select(`#timeline-svg-${processId}`), 0.7);
      }
      return <p>{`${propsDump} ${reason}`}</p>;
    }}
  />
});

// TODO incorporate final iopws by shard, with a touch of blur (when an instance is erroring/stalling, and the app and its downstream graph is lumbering on with only a subset of partitions, we don't want entire screenfuls blurred and barless)
// blur distinctly input bars layer only? In gray - orange - red?
// for outputs, no blur but blinking background in sync with caret? (Many instances may be in different event times currently. Just make it somewhat clear where the past/complete bars are)

// how to indicate that a window is not complete but some output already exists? Caret blink with log momentary hertz, stem fat and colored to a height roughly matching hertz-so-far?
// shadow effect on caret, cue input and output bars?
// do not design for fat bars at all?
// do not design for fat in-progress bars at all? In wider zooms, just blend the latest from sub-pixel windows? Still not very likeable, may look like a misleading average forecast
// ux toggle between smooth (resolution annotated on the side) and discrete (resolution readable on the timeline)?
// caret dividing the in-progress bar, correct current hertz-so-far and nothing to the right? Smooth or discrete position possible
// correct (min) total hertz, and caret y = hertz-so-far? Might be flappy around the overflow

// TODO tooltip chain icon button "align all to <app whose timeline was last zoomed/panned>"
// and/or TODO linkable session with a group of "zoomed/panned" apps
// * they become a sticky partial overlay, others can be dragged to the sticky group
// * if opened in a new window, then a sticky portal on the side remains, others can be dragged to it to update the group

// TODO tile EventSource here, one shared by all timeline components?
