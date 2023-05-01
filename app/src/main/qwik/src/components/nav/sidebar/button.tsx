import { Slot, component$ } from '@builder.io/qwik';

export default component$(() => (
  <button class="button-dark">
    <Slot />
  </button>
));
