import { $, Resource, component$, useResource$, useSignal, useStore, useVisibleTask$ } from "@builder.io/qwik";
import { MUICardContent } from "~/integrations/react/mui";
import type { DocumentHead } from "@builder.io/qwik-city";
import CommonCard from "~/components/nav/common";
import Header from "~/components/application/header";
import ApplicationGrid from "~/components/application/grid";
import Timeline from "~/components/application/timeline/timeline";
import styles from "~/components/application/application.module.css";

const platformUrl = import.meta.env.PUBLIC_PLATFORM_URL;

export default component$(() => {
  const sseLastEventId = useSignal(""); // "id" field is updated (with something pseudorandom? Event time?) when Spive progresses through events in dashboard state change stream. Might be debounced a fair bit on the backend, but also track() effectively debounces, I suppose
  const state = useStore<any>({rows: [] });
  // shared among all timelines for now. They're zoomed together and refreshed together
  const timelinesState = useStore<any>({
    offset: 0,
    level: 1,
    fetchStart: null,
    fetchStop: null,
    fetchTrigger: {value: 1}, // nested to let only barsResource in timelines re-render, if possible, not the component itself
  });

  useVisibleTask$(() => {
    const eventSource = new EventSource(`${platformUrl}/sse`);

    eventSource.onmessage = (event) => {
      console.log(sseLastEventId.value, event);
      sseLastEventId.value = event.lastEventId;
    };
  });

  useVisibleTask$(async ({ track, cleanup }) => {
    track(state.rows);

    zoomTimeline.onZoomed = (min, max, level) => {
      timelinesState.offset = 0;
      timelinesState.level = level;
      timelinesState.fetchStart = min;
      timelinesState.fetchStop = max;
    };

    const triggerFetch = () => {
      timelinesState.fetchTrigger.value ^= 1;
      zoomTimeline.updateDomain();
      zoomTimeline.forceZoomEvent(); // TODO force it smooth
    };

    // do we register every new app id to tile streamer, or is streamer going to track applicationsResource? or is backend (dashboard app) going to stream all apps tiles?
    const fetchTimelineInterval = setInterval(triggerFetch, 5000);

    cleanup(() => clearInterval(fetchTimelineInterval));
  });

  const pushSpinner = $((id: string) => {
    state.rows = [{ id: id + ".spin", rank: -state.rows.length }, ...state.rows];
  });

  const applicationsResource = useResource$<any>(async ({ track, cleanup }) => {
    track(() => sseLastEventId.value);
    const abortController = new AbortController();
    cleanup(() => abortController.abort("cleanup"));

    const res = await fetch(`${platformUrl}/api/applications`, {
      signal: abortController.signal,
    }).then((response) => {
      if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
      }
      return response.json();
    }).catch((err) => {
      return [
        { id: "flabbergasted", name: "App1" },
        { id: "flabbergastee", name: "App2" },
      ];
    });

    return res.map((app: any, i: number) => ({ rank: i, ...app }));
  });

  const deployApplication = $(({ name, version, ...fields }: { name: string; version: string }) => {
    console.log("Deploying", fields);

    const requestOptions = {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(fields),
    };
    fetch(
      platformUrl + "/api/applications/" + encodeURIComponent(name) + "/" + encodeURIComponent(version),
      requestOptions,
    )
      .then((response) => response.json())
      .then((id) => pushSpinner(id));
  });

  return (
    <CommonCard titleText={`Platform: io.ulzha.dev (${platformUrl})`} class={styles.applications}>
      <Header client:visible onNew={deployApplication} />
      <MUICardContent>
        <Resource
          value={applicationsResource}
          onPending={() => <p>Loading...</p>}
          onResolved={(applications) => {
            state.rows = applications;
            // ApplicationGrid is a React island, whereas we want to work with timelines Qwik style, so for now they aren't children of ApplicationGrid. SVG updates are hand-grafted into cells
            return (
              <>
                <ApplicationGrid rows={state.rows} />
                {timelinesState.fetchStart ? state.rows.map((row: any) => (
                  <Timeline key={row.id} processId={row.id} {...timelinesState} />
                )) : null}
              </>
            );
          }}
          onRejected={(reason) => {
            return <p>{`${reason}`}</p>;
          }}
        />
      </MUICardContent>
    </CommonCard>
  );
});

export const head: DocumentHead = {
  title: "Applications",
  meta: [
    {
      name: "description",
      content: "Main landing page for observing application status live",
    },
  ],
};
