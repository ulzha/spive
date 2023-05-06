/** @jsxImportSource react */

import { qwikify$ } from '@builder.io/qwik-react';
import { Button, Card, CardContent, CardHeader } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HelpIcon from '@mui/icons-material/Help';
import SearchIcon from '@mui/icons-material/Search';
import type { GridColDef } from '@mui/x-data-grid';
import { DataGrid } from '@mui/x-data-grid';

export const MUIButton = qwikify$(Button);
export const MUICard = qwikify$(Card);
export const MUICardContent = qwikify$(CardContent);
export const MUICardHeader = qwikify$(CardHeader);
export const MUIExpandMoreIcon = qwikify$(ExpandMoreIcon);
export const MUIHelpIcon = qwikify$(HelpIcon);
export const MUISearchIcon = qwikify$(SearchIcon);

export const TableApp = qwikify$(() => {
  const columns: GridColDef[] = [
    { field: 'uuid', headerName: 'UUID', width: 50, hide: true },
    { field: 'name', headerName: 'Name', width: 200 },
    { field: 'version', headerName: 'Version', width: 50, hide: true },
    { field: 'timeline', headerName: 'Timeline', width: 600 },
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

  const rows = [
    { id: 1, uuid: 1, name: 'VitrumNostrumGloriosum', version: '1.2.0', timeline: 35 },
    { id: 2, uuid: 2, name: 'ShakingAmber', version: '1.2.0', timeline: 35 },
    { id: 3, uuid: 3, name: 'DemoDeployElasticsearch', version: '1.2.0', timeline: 35 },
  ];

  return (
    <div style={{ height: 400, width: '100%' }}>
      <DataGrid
        rows={rows}
        columns={columns}
        pageSize={5}
        rowsPerPageOptions={[5]}
        disableSelectionOnClick
      />
    </div>
  );
});
