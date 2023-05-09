import { $, component$, useStore } from '@builder.io/qwik';
import { MUIButton, MUICard, MUICardHeader, MUICardContent } from '~/integrations/react/mui';
import type { DocumentHead } from '@builder.io/qwik-city';
import ApplicationGrid from '~/components/application/grid';
import Legend from '~/components/application/timeline/legend';

export default component$(() => {
  const state = useStore<{ rows: any[] }>({ rows: [] });

  const apps = [
    { uuid: 1, name: 'VitrumNostrumGloriosum', version: '1.2.0', timeline: <b>Spir</b> },
    { uuid: 2, name: 'ShakingAmber', version: '1.2.0', timeline: <b>Lī</b> },
    { uuid: 3, name: 'DemoDeployElasticsearch', version: '1.2.0', timeline: <b>Bā</b> },
  ];

  const pushApp = $(() => {
    const n = state.rows.reduce((count, r) => count + !(r.id.toString().endsWith('_events')), 0);
    state.rows.push({ id: n, ...apps[n % 3] });
  });

  const toggleEventsRow = $((params) => {
    console.debug('Row clicked', params, state.rows[0]);
    const uuid = params.id.toString().replace('_events', '');
    const i = state.rows.findIndex((r) => r.id == uuid);
    const isOn = state.rows[i + 1]?.id === uuid + '_events';
    if (isOn) {
      state.rows.splice(i + 1, 1);
    } else {
      state.rows.splice(i + 1, 0, { id: uuid + '_events' });
    }
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
          <ApplicationGrid
            rows={state.rows.map((x) => x)}
            onRowClick={toggleEventsRow}
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
