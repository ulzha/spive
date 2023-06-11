/** @jsxImportSource react */

import { ButtonGroup, CardHeader } from "@mui/material";
import Legend from "~/components/application/timeline/legend";
import NewApplicationForm from "~/components/application/new";
import { qwikify$ } from "@builder.io/qwik-react";

export default qwikify$(({ onNew }) => (
  <CardHeader
    title="Your Event-Driven Applications"
    action={
      <>
        <ButtonGroup>
          <NewApplicationForm onNew={onNew} />
        </ButtonGroup>
        <Legend />
      </>
    }
  />
));
