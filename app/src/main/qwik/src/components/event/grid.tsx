import { component$, $ } from "@builder.io/qwik";
import { MUIDataGrid } from "~/integrations/react/mui";
import type { MUIGridColDef } from "~/integrations/react/mui";
import EventFilter from "~/components/event/filter";
import styles from "./event.module.css";

declare const d3: any;

const renderDetailsDOM = $((el: Element, id: string, value: any) => {
  d3.select(el)
    .append("span")
    .attr("class", `legend-swatch color-${value ? "error" : "ok"}`);
  d3.select(el)
    .append("span")
    .text(value || " ");
});

interface EventGridProps {
  rows: any;
}

export default component$<EventGridProps>(({ rows }: EventGridProps) => {
  console.debug("Rendering grid Qwik");

  const columns: MUIGridColDef[] = [
    { field: "uuid", headerName: "UUID", width: 50 },
    // TODO light gray 'T'
    // some smart boldening/outlining/gradienting of datetime components can contribute a lot to readability
    { field: "eventTime", headerName: "Event Time", width: 210 },
    { field: "event", headerName: "Event", width: 400, flex: 1 },
    { field: "took", headerName: "Took", width: 60, align: "right" },
    // TODO output event(s)?
    // TODO render longest/non-ok rows first, shave some relayout delay off
    { field: "details", headerName: "Details", width: 300, renderCellDOM: renderDetailsDOM },
    { field: "runtimeLog", headerName: "Runtime Log", width: 100, sortable: false },
  ];

  return (
    <div class={styles.events}>
      <EventFilter />
      <MUIDataGrid
        client:visible
        density="compact"
        rows={rows}
        spanRows={[]}
        columns={columns}
        initialState={{
          columns: { columnVisibilityModel: { uuid: false } },
          sorting: { sortModel: [{ field: "eventTime", sort: "asc" }] },
          pagination: { paginationModel: { pageSize: 25 } },
        }}
        pageSizeOptions={[5, 25]}
      />
    </div>
  );
});
