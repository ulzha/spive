/**
 * An event filter/search control which works in a semantically precise way in backend, intended for filtering by input stream, partition key etc.
 *
 * (DataGrid filters are enabled on EventGrid for the purpose of further text-based filtering, client side.)
 */

import { component$ } from '@builder.io/qwik';
import { MUISearchForm } from '~/integrations/react/mui';

export default component$(() => {
  return <MUISearchForm />
});
