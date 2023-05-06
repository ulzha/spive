import { component$, $ } from '@builder.io/qwik';
import { MUIDataGrid, MUIGridColDef } from '~/integrations/react/mui';
// import type { GridColDef } from '@mui/x-data-grid';

interface GridProps {
  rows: any[];
}

export default component$<GridProps>(({ rows }) => {
  // const apiRef = useGridApiRef();  // Pro feature...
  // TODO make column status sticky
  console.log('Rendering grid Qwik', rows);

  // const renderCellCallback(el) = {
  //   render_timeline(el, 1, 0, '#1db855');
  // }

  // const c = memoRenderCell((id, el) => render_timeline(el, 1, 0, '#1db855'));

  const columns: MUIGridColDef[] = [
    // renderCell: (params: GridRenderCellParams) => ... muuch trickery, needs to be serializable https://qwik.builder.io/tutorial/props/closures/, and return a ReactNode... Make a qwikify$ helper to handle return values too?
    { field: 'uuid', headerName: 'UUID', width: 50, hide: true },
    { field: 'name', headerName: 'Name', width: 200 },
    { field: 'version', headerName: 'Version', width: 50, hide: true },
    // { field: 'timeline', headerName: 'Timeline', width: 600, renderCell: memoRenderCell((id, el) => render_timeline(el, 1, 0, '#1db855')) },
    // { field: 'timeline', headerName: 'Timeline', width: 600, renderCell: c },
    { field: 'timeline', headerName: 'Timeline', width: 600, renderCellDOM: $((id, el) => render_timeline(el, 1, 0, '#1db855')) },
    { field: 'deployLog', headerName: 'Deploy Log', width: 130 },
    { field: 'runtimeLog', headerName: 'Runtime Log', width: 130 },
    // {
    //   field: 'fullName',
    //   headerName: 'Full name',
    //   description: 'This column has a value getter and is not sortable.',
    //   sortable: false,
    //   width: 160,
    //   valueGetter: (params: GridValueGetterParams) =>
    //     `${params.row.firstName || ''} ${params.row.lastName || ''}`,
    // },
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
