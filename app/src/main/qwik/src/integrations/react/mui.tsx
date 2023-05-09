/** @jsxImportSource react */

import { memo, useLayoutEffect, useRef } from 'react';
import { qwikify$ } from '@builder.io/qwik-react';
import { Button, Card, CardContent, CardHeader, Popover } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HelpIcon from '@mui/icons-material/Help';
import SearchIcon from '@mui/icons-material/Search';
import { DataGrid } from '@mui/x-data-grid';
import type { GridCellParams, GridColDef, GridRenderCellParams, GridRowClassNameParams, GridRowHeightParams } from '@mui/x-data-grid';

type GridRenderCellDOMCallback = (id: any, el: any) => void;

export interface MUIGridColDef extends GridColDef {
  renderCellDOM?: GridRenderCellDOMCallback;
}

type MemoizedCellProps = { id: any; callback: any };

/**
 * Helper for ensuring that heavy DOM objects (such as timeline SVG) are reused between re-renders, accidental or intentional.
 *
 * When hiding columns, cells do get cleaned up and rendered anew when column is toggled on again. TODO improve?
 */
const MemoizedCell = memo<MemoizedCellProps>(({ id, callback }) => {
  const ref = useRef(null);

  useLayoutEffect(() => {
    console.debug('Rendering cell DOM', id, callback, ref.current);
    callback(id, ref.current);
  }, [id, callback]);

  console.debug('Rendering cell React', id, callback);
  return <div ref={ref}></div>;
});

// this function doesn't need its own return values memoized, callback is unique per grid and page load anyway
// (if multiple columns were to use the same callback, we would spawn multiple closures here but still memoize cells)
const renderMemoizedCell =
  (callback: GridRenderCellDOMCallback) => (params: GridRenderCellParams) =>
    <MemoizedCell id={params.id} callback={callback} />;

// documentation says memoize, I don't get why
// const getRowHeight = const getRowHeight = React.useCallback(() => { ... }, [])
const getRowHeight = ({ id }: GridRowHeightParams) =>
  (id.toString().endsWith('_events') ? 300 : null)

export const MUIButton = qwikify$(Button);
export const MUICard = qwikify$(Card);
export const MUICardContent = qwikify$(CardContent);
export const MUICardHeader = qwikify$(CardHeader);
export const MUIDataGrid = qwikify$((props: any) => {
  const rowCount = props.rows.reduce((count, r) => count + !(r.id.toString().endsWith('_events')), 0);
  return <DataGrid
    {...props}
    rows={props.rows}
    columns={[
      // we add a non-hideable first column to hold the colspanning content
      // (whereas a cell in a hidden column disappears and doesn't span cells in the remaining visible columns)
      { field: '_events', headerName: '', width: 0, hideable: false, hideSortIcons: true, disableColumnMenu: true, resizable: false, minWidth: 0, maxWidth: 0 },
      ...props.columns
    ].map((c: MUIGridColDef) => c.renderCellDOM
      ? { ...c, renderCell: renderMemoizedCell(c.renderCellDOM) }
      : c
    ).map((c: MUIGridColDef) => ({
      ...c, colSpan: ({ row }: GridCellParams) => (row.id.toString().endsWith('_events') ? 7 : null)
    }))}
    getRowHeight={getRowHeight}
    // components={{
    //   NoRowsOverlay: () => <b>No rows.</b>,  // hoped that this would make height < 72, but no
    //   NoResultsOverlay: () => <b>No results found.</b>,
    // }}
    rowCount={rowCount}
    getRowClassName={({ row }: GridRowClassNameParams) => row.id.toString().endsWith('_events')
      ? 'events'
      : (row.id % 2 ? 'even' : 'odd')}
  />
});
export const MUIPopover = qwikify$(Popover);

export const MUIExpandMoreIcon = qwikify$(ExpandMoreIcon);
export const MUIHelpIcon = qwikify$(HelpIcon);
export const MUISearchIcon = qwikify$(SearchIcon);
