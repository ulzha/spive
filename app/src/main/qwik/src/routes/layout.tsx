import { component$, Slot, } from '@builder.io/qwik';
import { routeLoader$ } from '@builder.io/qwik-city';

import Sidebar from '~/components/nav/sidebar/sidebar';
import HelpLink from '~/components/nav/support/help';
import QwikLink from '~/components/nav/credits/qwik';

export const useServerTimeLoader = routeLoader$(() => {
  return {
    date: new Date().toISOString() + ' UTC',
  };
});

export default component$(() => {
  return (
    <div class="viewport">
      <Sidebar />
      <HelpLink />
      <main>
        <Slot />
      </main>
      <QwikLink />
    </div>
  );
});
