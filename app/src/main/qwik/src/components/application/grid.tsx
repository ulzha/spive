import { component$, $ } from '@builder.io/qwik';
import { MUIDataGrid, MUIGridColDef } from '~/integrations/react/mui';

const renderTimelineDOM = $((id, el) => {
  render_timeline(el, 1, 0, '#1db855');
})

interface GridProps {
  rows: any[];
}

export default component$<GridProps>(({ rows }) => {
  // const apiRef = useGridApiRef();  // Pro feature...
  // TODO make column status sticky
  console.debug('Rendering grid Qwik');

  const columns: MUIGridColDef[] = [
    { field: 'uuid', headerName: 'UUID', width: 50, hide: true },
    { field: 'name', headerName: 'Name', width: 200 },
    { field: 'version', headerName: 'Version', width: 50, hide: true },
    // renderCell: (params: GridRenderCellParams) => ... muuch trickery, needs to be serializable https://qwik.builder.io/tutorial/props/closures/, and return a ReactNode... Make a qwikify$ helper to handle return values too?
    { field: 'timeline', headerName: 'Timeline', width: 600, renderCellDOM: renderTimelineDOM, sortable: false },
    { field: 'deployLog', headerName: 'Deploy Log', width: 130, sortable: false },
    { field: 'runtimeLog', headerName: 'Runtime Log', width: 130, sortable: false },
  ];

  return <MUIDataGrid
    client: visible
    density="compact"
    rows={rows}
    columns={columns}
    autoHeight={true}
    pageSize={rows.length}  // all in one page
    rowsPerPageOptions={[rows.length]}
    disableSelectionOnClick
  />;
});
