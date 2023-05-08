/** @jsxImportSource react */

import { memo, useLayoutEffect, useRef } from 'react';
import { qwikify$ } from '@builder.io/qwik-react';
import { Button, Card, CardContent, CardHeader, Popover } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HelpIcon from '@mui/icons-material/Help';
import SearchIcon from '@mui/icons-material/Search';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';

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

export const MUIButton = qwikify$(Button);
export const MUICard = qwikify$(Card);
export const MUICardContent = qwikify$(CardContent);
export const MUICardHeader = qwikify$(CardHeader);
export const MUIDataGrid = qwikify$((props: any) => (
  <DataGrid
    {...props}
    columns={props.columns.map((c: MUIGridColDef) =>
      c.renderCellDOM
        ? { ...c, renderCell: renderMemoizedCell(c.renderCellDOM) }
        : c
    )}
  />
));
export const MUIPopover = qwikify$(Popover);

export const MUIExpandMoreIcon = qwikify$(ExpandMoreIcon);
export const MUIHelpIcon = qwikify$(HelpIcon);
export const MUISearchIcon = qwikify$(SearchIcon);
