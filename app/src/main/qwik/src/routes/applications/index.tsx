import { $, Resource, component$, useResource$, useSignal, useStore, useVisibleTask$ } from "@builder.io/qwik";
import { MUICardContent } from "~/integrations/react/mui";
import type { DocumentHead } from "@builder.io/qwik-city";
import CommonCard from "~/components/nav/common";
import Header from "~/components/application/header";
import ApplicationGrid from "~/components/application/grid";
import styles from "~/components/application/application.module.css";

declare const generateDummyTimelineBars: (rows: any[]) => void;

const platformUrl = "http://localhost:8440";

export default component$(() => {
  const sseLastEventId = useSignal(""); // "id" field is updated (with something pseudorandom? Event time?) when Spive progresses through events in dashboard state change stream. Might be debounced a fair bit on the backend, but also track() effectively debounces, I suppose
  const state = useStore<any>({ rows: [] });

  useVisibleTask$(() => {
    const eventSource = new EventSource(`${platformUrl}/sse`);

    eventSource.onmessage = (event) => {
      console.log(sseLastEventId.value, event);
      sseLastEventId.value = event.lastEventId;
    };
  });

  useVisibleTask$(async ({ track, cleanup }) => {
    const rows = track(state.rows);

    // do we register every new app id to tile streamer, or is streamer going to track applicationsResource? or is backend (dashboard app) going to stream all apps tiles?
    const dummyEventSourceInterval = setInterval(() => generateDummyTimelineBars(rows), 5000);

    cleanup(() => clearInterval(dummyEventSourceInterval));
  });

  const pushSpinner = $((id: string) => {
    state.rows = [{ id: id + ".spin", rank: -state.rows.length }, ...state.rows];
  });

  const applicationsResource = useResource$<any>(async ({ track, cleanup }) => {
    track(() => sseLastEventId.value);

    console.log("Oh here we go again");
    const abortController = new AbortController();
    cleanup(() => abortController.abort("cleanup"));

    const res = await fetch(`${platformUrl}/api/applications`, {
      signal: abortController.signal,
    }).then((response) => {
      if (!response.ok) {
        console.debug("Meh", response.status, response.statusText);
      }
      console.debug("Fetched");
      return response.json() as Promise<any[]>;
    });

    // return res.map((app: any, i: number) => ({ rank: i, ...app }));
    return [
      {rank: 0, name: "Loco", id: "flabbergasted"},
      ...res.map((app: any, i: number) => ({ rank: i + 1, ...app }))
    ];
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
      requestOptions
    )
      .then((response) => response.json())
      .then((id) => pushSpinner(id))
      .catch((error) => console.log(error));
  });

  return (
    <CommonCard titleText={`Platform: io.ulzha.dev (${platformUrl})`} class={styles.applications}>
      <Header client:visible onNew={deployApplication} />
      <MUICardContent>
        <Resource
          value={applicationsResource}
          onResolved={(applications) => {
            state.rows = applications;
            return <ApplicationGrid rows={state.rows} />;
          }}
          onRejected={(reason) => {
            return reason;
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
