/** @jsxImportSource react */

import { memo, useLayoutEffect, useRef } from 'react';
import { qwikify$ } from '@builder.io/qwik-react';
import { Button, Card, CardContent, CardHeader, FormControl, Input, InputAdornment, Popover } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HelpIcon from '@mui/icons-material/Help';
import SearchIcon from '@mui/icons-material/Search';
import { DataGrid } from '@mui/x-data-grid';
import type { GridCellParams, GridColDef, GridRenderCellParams, GridRowClassNameParams, GridRowHeightParams } from '@mui/x-data-grid';

type GridRenderCellDOMCallback = (el: Element, id: string, value: any) => void;

export type MUIGridColDef = GridColDef & {
  renderCellDOM?: GridRenderCellDOMCallback;
}

type MemoizedCellProps = { callback: any, id: any; field: any, value: any };

/**
 * Helper for ensuring that heavy DOM objects (such as timeline SVG) are reused between re-renders, accidental or intentional.
 *
 * When hiding columns, cells do get cleaned up and rendered anew when column is toggled on again. TODO improve?
 * (Alt. make sure they are cleaned from memory too)
 */
const MemoizedCell = memo<MemoizedCellProps>(({ callback, id, field, value}) => {
  const ref = useRef(null);

  useLayoutEffect(() => {
    console.debug('Rendering cell DOM', ref.current, id, field, value);
    callback(ref.current, id, value);
  }, [id, field, value]);

  console.debug('Rendering cell React', id, value);

  return <div ref={ref}></div>;
});

// this function doesn't need its own return values memoized, callback is unique per grid and page load anyway
// (if multiple columns were to use the same callback, we would spawn multiple closures here but still memoize cells)
const renderMemoizedCell =
  (callback: GridRenderCellDOMCallback) => (params: GridRenderCellParams) =>
    <MemoizedCell callback={callback} id={params.id} field={params.field} value={params.value} />;

// documentation says memoize, I don't get why
// const getRowHeight = const getRowHeight = React.useCallback(() => { ... }, [])
const getRowHeight = ({ id }: GridRowHeightParams) =>
  (id.toString().endsWith('.span') ? 136 + 25 * 36 : null)
// 4 -> 280, 24 -> 1000

export const MUIButton = qwikify$(Button);
export const MUICard = qwikify$(Card);
export const MUICardContent = qwikify$(CardContent);
export const MUICardHeader = qwikify$(CardHeader);
export const MUIDataGrid = qwikify$((props: any) => {
  return <DataGrid
    {...props}
    rows={props.rows.concat(props.spanRows)}
    columns={[
      ...props.columns
    ].map((c: MUIGridColDef) => c.renderCellDOM
      ? { ...c, renderCell: renderMemoizedCell(c.renderCellDOM) }
      : c
    ).map((c: MUIGridColDef) => ({
      ...c, colSpan: ({ row }: GridCellParams) => (row.id.toString().endsWith('.span') ? props.columns.length : null)
    }))}
    getRowHeight={getRowHeight}
    rowSelection={false}
    // components={{
    //   NoRowsOverlay: () => <b>No rows.</b>,  // hoped that this would make height < 72, but no
    //   NoResultsOverlay: () => <b>No results found.</b>,
    // }}
    rowCount={props.rows.length}
    getRowClassName={({ row }: GridRowClassNameParams) => row.id.toString().endsWith('.span')
      ? 'span'
      : (row.id % 2 ? 'even' : 'odd')}
  />
});
export const MUISearchForm = qwikify$(() => {
  return <FormControl variant="standard">
    <Input
      placeholder="Search..."
      startAdornment={
        <InputAdornment position="start">
          <SearchIcon fontSize="small" />
        </InputAdornment>
      }
    />
  </FormControl>
});
export const MUIPopover = qwikify$(Popover);

export const MUIExpandMoreIcon = qwikify$(ExpandMoreIcon);
export const MUIHelpIcon = qwikify$(HelpIcon);
export const MUISearchIcon = qwikify$(SearchIcon);
