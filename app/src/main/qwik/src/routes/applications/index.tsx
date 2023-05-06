import { component$, useSignal } from '@builder.io/qwik';
import { MUIButton, MUICard, MUICardHeader, MUICardContent, TableApp } from '~/integrations/react/mui';

import type { DocumentHead } from '@builder.io/qwik-city';
import Legend from '~/components/app/timeline/legend';

export default component$(() => {
  const appId = useSignal(0);

  return (
    <div class="padding">
      <div class="titlebar">
        <h2>Event-Driven Applications</h2>
        <MUIButton href="#create" variant="outlined" onClick$={() => { appId.value++ }}>Create new</MUIButton>
      </div>
      <MUICard elevation={1}>
        <MUICardHeader
          title="Your Event-Driven Applications" />
        <span id="legend-toggle">LEGEND</span>
        <Legend />
        <MUICardContent>
          <TableApp client:visible />{/*lastAppId={appId.value} */}
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
