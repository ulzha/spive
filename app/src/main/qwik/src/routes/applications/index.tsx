import { $, Resource, component$, useResource$, useSignal, useStore, useVisibleTask$ } from "@builder.io/qwik";
import { MUICardContent } from "~/integrations/react/mui";
import type { DocumentHead } from "@builder.io/qwik-city";
import CommonCard from "~/components/nav/common";
import Header from "~/components/application/header";
import ApplicationGrid from "~/components/application/grid";

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
      requestOptions
    )
      .then((response) => response.json())
      .then((id) => pushSpinner(id))
      .catch((error) => console.log(error));
  });

  return (
    <CommonCard titleText={`Platform: io.ulzha.dev (${platformUrl})`}>
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
