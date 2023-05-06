import { $, component$, useStore } from '@builder.io/qwik';
import { MUIButton, MUICard, MUICardHeader, MUICardContent } from '~/integrations/react/mui';

import type { DocumentHead } from '@builder.io/qwik-city';
import ApplicationGrid from '~/components/application/grid';
import Legend from '~/components/application/timeline/legend';

export default component$(() => {
  const state = useStore<{ rows: any[] }>({rows: []});

  const apps = [
    { uuid: 1, name: 'VitrumNostrumGloriosum', version: '1.2.0', timeline: <b>Spir</b> },
    { uuid: 2, name: 'ShakingAmber', version: '1.2.0', timeline: <b>Lī</b> },
    { uuid: 3, name: 'DemoDeployElasticsearch', version: '1.2.0', timeline: <b>Bā</b> },
  ];

  const pushApp = $(() => {
    const i = state.rows.length;
    state.rows.push({id: i, ...apps[i % 3]});
  });

  return (
    <div class="padding">
      <div class="titlebar">
        <h2>Event-Driven Applications</h2>
        <MUIButton client: hover variant="outlined" onClick$={pushApp}>Create new</MUIButton>
      </div>
      <MUICard elevation={1}>
        {/* action={[<Legend />]} failed with "Objects are not valid as a React child (found: object with keys {type, props, immutableProps, children, flags, key, dev})" */}
        <MUICardHeader title="Your Event-Driven Applications" />
        <Legend />
        <MUICardContent>
          {/* simple state.rows failed with "Cannot add property 0, object is not extensible" */}
          <ApplicationGrid rows={state.rows.map((x) => x)} />
        </MUICardContent>
      </MUICard>
    </div>
  );
});

export const head: DocumentHead = {
  title: 'Applications',
  meta: [
    {
      name: 'description',
      content: 'Main landing page for observing application status live',
    },
  ],
};
