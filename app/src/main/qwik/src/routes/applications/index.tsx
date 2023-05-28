import { $, Resource, component$, useResource$, useStore } from "@builder.io/qwik";
import { MUICard, MUICardHeader, MUICardContent } from "~/integrations/react/mui";
import type { DocumentHead } from "@builder.io/qwik-city";
import ApplicationGrid from "~/components/application/grid";
import Legend from "~/components/application/timeline/legend";
import { MUICreateNewApplicationForm } from "~/integrations/react/mui-dialog";

export default component$(() => {
  // const meta = useStore({ platformUrl: });
  const platformUrl = "http://localhost:8040";
  const state = useStore<any>({ rows: [] });

  const dummy_applications = [
    { uuid: 1, name: "VitrumNostrumGloriosum", version: "1.2.74" },
    { uuid: 2, name: "ShakingAmber", version: "2.4.0" },
    { uuid: 3, name: "DemoDeployElasticsearch", version: "0.0.1_dev_8f779e6" },
  ];

  const pushApp = $(() => {
    const n = state.rows.length;
    state.rows = [...state.rows, { id: n, rank: n, ...dummy_applications[n % 3] }];
  });

  const applicationsResource = useResource$<any>(async ({ track, cleanup }) => {
    track(() => state.rows);
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

  const deployApplication = $(({name, version, ...fields}: {name: string, version: string}) => {
    console.log("Deploying", fields);

    const requestOptions = {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(fields),
    };
    fetch(
      platformUrl + "/api/applications/" +
        encodeURIComponent(name) +
        "/" +
        encodeURIComponent(version),
      requestOptions
    )
      .then(() => state.rows = [...state.rows, {id: 123}]) // just notify applicationsResource. TODO show spinner instead and have SSE notify
      .catch((error) => console.log(error));
  });

  return (
    <div class="padding">
      <div class="titlebar">
        <h2>Platform: io.ulzha.dev ({platformUrl})</h2>
        <MUICreateNewApplicationForm onCreate$={deployApplication} />
      </div>
      <MUICard elevation={1}>
        {/* action={[<Legend />]} failed with "Objects are not valid as a React child (found: object with keys {type, props, immutableProps, children, flags, key, dev})" */}
        <MUICardHeader title="Your Event-Driven Applications" />
        <Legend />
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
      </MUICard>
    </div>
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
