/** @jsxImportSource react */

import { memo, useLayoutEffect, useRef, ReactNode } from 'react';
import { qwikify$ } from '@builder.io/qwik-react';
import { Button, Card, CardContent, CardHeader, Popover } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HelpIcon from '@mui/icons-material/Help';
import SearchIcon from '@mui/icons-material/Search';
import { DataGrid, GridColDef, GridRenderCellParams } from '@mui/x-data-grid';

// type GridRenderCellDOMCallback = (id: any, el: any) => void;

export interface MUIGridColDef extends GridColDef {
  renderCellDOM?: (id: number, el: any) => void;
  // renderCellDOM?: GridRenderCellDOMCallback;
}

const MemoizedCell = memo(function Cell(props) {
  const ref = useRef(null);

  useLayoutEffect(() => {
    if (ref.current) {
      console.log('Rendering cell DOM', props, ref.current);
      render_timeline(ref.current, 1, 0, '#1db855')
      // props.callback(props.id, ref.current);
    } else {
      console.log('Rendering cell DOM precluded', props, undefined);
    }
  }, [props]);

  console.log('Rendering cell React', props);
  return <div ref={ref}></div>;
});

// also works but nags with "Property 'id' does not exist on type '{}'" and logs callback much more verbosely
// const MemoizedCell = memo(function Cell({id, callback}) {
//   const ref = useRef(null);

//   useLayoutEffect(() => {
//     console.log('Rendering cell DOM', id, callback, ref.current);
//     callback(id, ref.current);
//   }, [id, callback]);

//   console.log('Rendering cell React', id, callback);
//   return <div ref={ref}></div>;
// });

export const memoRenderCell = (callback: (id: number, el: any) => void) => {
  return ((params: GridRenderCellParams): ReactNode => {
    // return <MemoizedCell {...{id: params.id, callback: callback}} />
    return <MemoizedCell {...{id: params.id}} />
  });
}

// export const memoRenderCell = (callback: GridRenderCellDOMCallback) => {
//   return ((params: GridRenderCellParams): ReactNode => {
//     return <MemoizedCell {...{id: params.id, callback: callback}} />
//     // return <MemoizedCell id={params.id} callback={callback} /> // Property 'id' does not exist on type 'IntrinsicAttributes & object'.ts(2322)
//   });
// }

// WithHeavyCell? Idea is to ensure timeline DOM element is reused... ^ memo seems to be working alrightey for this?
// When hiding columns, they get cleaned up and rerendered when column is toggled on again. Nice?
function DataGridWithRenderCell(props: any) {

  // const renderTimeline = (params: GridRenderCellParams) => {
  //   if (params.field == 'timeline') {
  //     return <MemoizedStub {...{id: params.id}} />;
  //   }
  //   return params.formattedValue;
  // }

  return <DataGrid
    {...props}
    // columns={props.columns.map((c) => ({...c, renderCell: renderTimeline}))}
    columns={props.columns.map((c: MUIGridColDef) => c.renderCellDOM ? ({...c, renderCell: memoRenderCell(c.renderCellDOM)}) : c)}
    />;
}

export const MUIButton = qwikify$(Button);
export const MUICard = qwikify$(Card);
export const MUICardContent = qwikify$(CardContent);
export const MUICardHeader = qwikify$(CardHeader);
// export const MUIDataGrid = qwikify$(DataGrid);
export const MUIDataGrid = qwikify$(DataGridWithRenderCell);
export const MUIPopover = qwikify$(Popover);

export const MUIExpandMoreIcon = qwikify$(ExpandMoreIcon);
export const MUIHelpIcon = qwikify$(HelpIcon);
export const MUISearchIcon = qwikify$(SearchIcon);
