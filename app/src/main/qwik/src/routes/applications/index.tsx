import { $, component$, useStore } from '@builder.io/qwik';
import { MUIButton, MUICard, MUICardHeader, MUICardContent } from '~/integrations/react/mui';
import type { DocumentHead } from '@builder.io/qwik-city';
import ApplicationGrid from '~/components/application/grid';
import Legend from '~/components/application/timeline/legend';

export default component$(() => {
  // `rows: []` creates weird complaints related to "never[]"
  const state = useStore({ rows: Array() });

  const dummy_applications = [
    { uuid: 1, name: 'VitrumNostrumGloriosum', version: '1.2.74' },
    { uuid: 2, name: 'ShakingAmber', version: '2.4.0' },
    { uuid: 3, name: 'DemoDeployElasticsearch', version: '0.0.1_dev_8f779e6' },
  ];

  const pushApp = $(() => {
    const n = state.rows.length;
    state.rows = [...state.rows, { id: n, rank: n, ...dummy_applications[n % 3] }];
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
          <ApplicationGrid
            rows={state.rows}
          />
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
