import { component$, $ } from '@builder.io/qwik';
import { MUIDataGrid, MUIGridColDef } from '~/integrations/react/mui';

const renderTimelineDOM = $((id, el) => {
  render_timeline(el, 1, 0, '#1db855');
})

interface GridProps {
  rows: any,
  onRowClick: any,
}

export default component$<GridProps>(({ rows, onRowClick }: GridProps) => {
  // const apiRef = useGridApiRef();  // Pro feature...
  // TODO make column status sticky
  console.debug('Rendering grid Qwik');

  const columns: MUIGridColDef[] = [
    { field: 'uuid', headerName: 'UUID', width: 50 },
    { field: 'name', headerName: 'Name', width: 200 },
    { field: 'version', headerName: 'Version', width: 50 },
    // renderCell: (params: GridRenderCellParams) => ... muuch trickery, needs to be serializable https://qwik.builder.io/tutorial/props/closures/, and return a ReactNode... Make a qwikify$ helper to handle return values too?
    { field: 'timeline', headerName: 'Timeline', width: 600, renderCellDOM: renderTimelineDOM, sortable: false },
    { field: 'deployLog', headerName: 'Deploy Log', width: 130, sortable: false },
    { field: 'runtimeLog', headerName: 'Runtime Log', width: 130, sortable: false },
  ];

  return <div class="applications">
    <MUIDataGrid
      client: visible
      density="compact"
      rows={rows}
      columns={columns}
      initialState={{ columns: { columnVisibilityModel: { uuid: false, version: false } } }}
      autoHeight={true}
      pageSize={rows.length}  // all in one page
      rowsPerPageOptions={[rows.length]}
      disableSelectionOnClick
      onRowClick={onRowClick}
    />
  </div>
});
