import type { RequestEvent } from '@builder.io/qwik-city';

export const onGet = async ({ redirect }: RequestEvent) => {
  throw redirect(302, '/applications');
};

/*
export const head: DocumentHead = {
  title: 'Welcome to Qwik',
  meta: [
    {
      name: 'description',
      content: 'Qwik site description',
    },
  ],
};
*/
