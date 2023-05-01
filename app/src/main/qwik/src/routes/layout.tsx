import { component$, Slot, } from '@builder.io/qwik';
import { routeLoader$ } from '@builder.io/qwik-city';

import Sidebar from '~/components/nav/sidebar/sidebar';
import SupportLink from '~/components/nav/support/link';

export const useServerTimeLoader = routeLoader$(() => {
  return {
    date: new Date().toISOString() + ' UTC',
  };
});

export default component$(() => {
  return (
    <div class="viewport">
      <Sidebar />
      <SupportLink />
      <main>
        <Slot />
      </main>
    </div>
  );
});
