import { $, Resource, component$, useResource$, useStore } from "@builder.io/qwik";
import { MUIButton, MUICard, MUICardHeader, MUICardContent } from "~/integrations/react/mui";
import type { DocumentHead } from "@builder.io/qwik-city";
import ApplicationGrid from "~/components/application/grid";
import Legend from "~/components/application/timeline/legend";
import { responsiveFontSizes } from "@mui/material";

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

  const applicationsResource = useResource$<any>(async ({ cleanup }) => {
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

    state.rows = res.map((app: any, i: number) => ({ rank: i, ...app }));
  });

  return (
    <div class="padding">
      <div class="titlebar">
        <h2>Platform: {platformUrl}</h2>
        <MUIButton client:hover variant="outlined" onClick$={pushApp}>
          Create new
        </MUIButton>
      </div>
      <MUICard elevation={1}>
        {/* action={[<Legend />]} failed with "Objects are not valid as a React child (found: object with keys {type, props, immutableProps, children, flags, key, dev})" */}
        <MUICardHeader title="Your Event-Driven Applications" />
        <Legend />
        <MUICardContent>
          <Resource
            value={applicationsResource}
            onResolved={(applications) => {
              return <ApplicationGrid rows={state.rows} />;
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
