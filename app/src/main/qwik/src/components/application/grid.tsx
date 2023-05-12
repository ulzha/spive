import { component$, $, render, useStore } from '@builder.io/qwik';
import { MUIDataGrid, MUIGridColDef } from '~/integrations/react/mui';
import EventGrid from '~/components/event/grid';

const renderTimelineDOM = $((el, id) => {
  render_timeline(el, 1, 0, '#1db855');
})

const renderEventsDOM = $((el, id, value) => {
  const rows = [
    { id: 1, uuid: 1, eventTime: '2021-03-10T01:39:03.795Z#0', event: '{"@type": "type.googleapis.com/company.author.ProfileUpdate", "authorId": "4806a8a9-1b83-47ad-b0ae-e6cd54b49c72"}', took: "3 ms" },
    { id: 2, uuid: 2, eventTime: '2021-03-10T01:49:03.100Z#0', event: '{"@type": "type.googleapis.com/company.lyrics.LyricsEdit", "lyricsId": "b8142cbb-7160-40b7-bf79-1be6562fa243"}', took: "3 ms", details: "java.util.concurrent.ExecutionException: io.grpc...." },
    { id: 3, uuid: 3, eventTime: '2021-03-10T02:09:33.545Z#0', event: '{"@type": "type.googleapis.com/company.lyrics.LyricsEdit", "lyricsId": "ca2f6489-08d3-43a6-b840-3db50107cd83"}', took: "3 ms" },
    { id: 4, uuid: 4, eventTime: '2021-03-10T02:59:31.615Z#0', event: '{"@type": "type.googleapis.com/company.author.ProfileUpdate", "authorId": "4c7f179a-070f-404d-aafe-ceacd6033181"}', took: "3 ms" },
  ];

  if (id.toString().endsWith('.span')) {
    render(el, <EventGrid rows={rows}/>);
  }
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
    { field: 'rank', headerName: '', width: 0, hideable: false, hideSortIcons: true, disableColumnMenu: true, resizable: false, minWidth: 0, maxWidth: 0, renderCellDOM: renderEventsDOM },
    { field: 'uuid', headerName: 'UUID', width: 50 },
    { field: 'name', headerName: 'Name', width: 200 },
    { field: 'version', headerName: 'Version', width: 90 },
    // renderCell: (params: GridRenderCellParams) => ... muuch trickery, needs to be serializable https://qwik.builder.io/tutorial/props/closures/, and return a ReactNode... Make a qwikify$ helper to handle return values too?
    { field: 'timeline', headerName: 'Timeline', width: 400, renderCellDOM: renderTimelineDOM, sortable: false, flex: 1 },
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