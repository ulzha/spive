import { component$, $, useStore } from '@builder.io/qwik';
import { MUIDataGrid, MUIGridColDef } from '~/integrations/react/mui';

const renderTimelineDOM = $((id, el) => {
  render_timeline(el, 1, 0, '#1db855');
})

interface GridProps {
  rows: any,
}

export default component$<GridProps>(({ rows }: GridProps) => {
  const state = useStore({ eventsRows: {} });

  const toggleEventsRow = $((params) => {
    console.debug('Row clicked', params, state.eventsRows);
    const uuid = params.id.toString().replace('.span', '');
    if (uuid in state.eventsRows) {
      delete state.eventsRows[uuid];
    } else {
      state.eventsRows[uuid] = {id: uuid + '.span', rank: params.id + 0.5};
    }
  });

  console.debug('Rendering grid Qwik');

  const columns: MUIGridColDef[] = [
    // we need a non-hideable first column to hold the colspanning content
    // (a cell in a hidden column disappears and doesn't span cells in the remaining visible columns)
    // double-dutying it for rank purposes won't hurt either
    { field: 'rank', headerName: '', width: 0, hideable: false, hideSortIcons: true, disableColumnMenu: true, resizable: false, minWidth: 0, maxWidth: 0 },
    { field: 'uuid', headerName: 'UUID', width: 50 },
    { field: 'name', headerName: 'Name', width: 200 },
    { field: 'version', headerName: 'Version', width: 90 },
    // renderCell: (params: GridRenderCellParams) => ... muuch trickery, needs to be serializable https://qwik.builder.io/tutorial/props/closures/, and return a ReactNode... Make a qwikify$ helper to handle return values too?
    { field: 'timeline', headerName: 'Timeline', width: 600, renderCellDOM: renderTimelineDOM, sortable: false },
    { field: 'deployLog', headerName: 'Deploy Log', width: 130, sortable: false },
  ];

  return <div class="applications">
    <MUIDataGrid
      client: visible
      density="compact"
      rows={rows}
      spanRows={Object.values(state.eventsRows)}
      columns={columns}
      initialState={{
        columns: { columnVisibilityModel: { uuid: false, version: false } },
        sorting: { sortModel: [{ field: 'rank', sort: 'asc' }] },
      }}
      onRowClick={toggleEventsRow}
    />
  </div>
});
