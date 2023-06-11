import { component$, Slot } from "@builder.io/qwik";

import Sidebar from "~/components/nav/sidebar/sidebar";

export default component$(() => {
  return (
    <div class="viewport">
      <Sidebar />
      <main>
        <Slot />
      </main>
    </div>
  );
});
