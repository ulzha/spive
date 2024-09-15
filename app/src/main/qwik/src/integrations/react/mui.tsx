/** @jsxImportSource react */

import { memo, useLayoutEffect, useRef } from "react";
import { qwikify$ } from "@builder.io/qwik-react";
import { Button, CardContent, FormControl, Input, InputAdornment, LinearProgress } from "@mui/material";
import { ExpandMore, Search } from "@mui/icons-material";
import { DataGrid } from "@mui/x-data-grid";
import type {
  GridCellParams,
  GridColDef,
  GridRenderCellParams,
  GridRowClassNameParams,
  GridRowHeightParams,
} from "@mui/x-data-grid";

type GridRenderCellDOMCallback = (el: Element, id: string, value: any) => void;

export type MUIGridColDef = GridColDef & {
  renderCellDOM?: GridRenderCellDOMCallback;
};

type MemoizedCellProps = { callback: any; id: any; field: any; value: any };

/**
 * Helper for ensuring that heavy DOM objects (such as timeline SVG) are reused between re-renders, accidental or intentional.
 *
 * When hiding columns, cells do get cleaned up and rendered anew when column is toggled on again. TODO improve?
 * (Alt. make sure they are cleaned from memory too)
 */
const MemoizedCell = memo<MemoizedCellProps>(({ callback, id, field, value }) => {
  const ref = useRef(null);

  useLayoutEffect(() => {
    console.debug("Rendering cell DOM", ref.current, id, field, value);
    callback(ref.current, id, value);
  }, [id, field, value]);

  console.debug("Rendering cell React", id, value);

  return <div ref={ref}></div>;
});

// this function doesn't need its own return values memoized, callback is unique per grid and page load anyway
// (if multiple columns were to use the same callback, we would spawn multiple closures here but still memoize cells)
const renderMemoizedCell = (callback: GridRenderCellDOMCallback) => (params: GridRenderCellParams) =>
  <MemoizedCell callback={callback} id={params.id} field={params.field} value={params.value} />;

// documentation says memoize, I don't get why
// const getRowHeight = const getRowHeight = React.useCallback(() => { ... }, [])
const getRowHeight = ({ id }: GridRowHeightParams) => (id.toString().endsWith(".span") ? 136 + 25 * 36 : null);
// 4 -> 280, 24 -> 1000

export const MUIButton = qwikify$(Button);
export const MUICardContent = qwikify$(CardContent);
export const MUIDataGrid = qwikify$((props: any) => {
  return (
    <DataGrid
      {...props}
      // FIXME concat dummy rows to round up the last page
      rows={props.rows.concat(props.spanRows)}
      columns={[...props.columns]
        .map((c: MUIGridColDef) => (c.renderCellDOM ? { ...c, renderCell: renderMemoizedCell(c.renderCellDOM) } : c))
        .map((c: MUIGridColDef) => ({
          ...c,
          colSpan: ({ row }: GridCellParams) => (row.id.toString().includes(".") ? props.columns.length : null),
        }))}
      // FIXME vary when toggling between 5 and 25
      getRowHeight={getRowHeight}
      rowSelection={false}
      // components={{
      //   NoRowsOverlay: () => <b>No rows.</b>,  // hoped that this would make height < 72, but no
      //   NoResultsOverlay: () => <b>No results found.</b>,
      // }}
      rowCount={props.rows.length}
      getRowClassName={({ row }: GridRowClassNameParams) =>
        row.id.toString().split(".")[1] || (Math.floor(row.rank) % 2 ? "even" : "odd")
      }
    />
  );
});
export const MUILinearProgress = qwikify$(LinearProgress);
export const MUISearchForm = qwikify$(() => {
  return (
    <FormControl variant="standard">
      <Input
        placeholder="Search..."
        startAdornment={
          <InputAdornment position="start">
            <Search fontSize="small" />
          </InputAdornment>
        }
      />
    </FormControl>
  );
});

export const MUIExpandMoreIcon = qwikify$(ExpandMore);
export const MUISearchIcon = qwikify$(Search);

/*

import { qwikify$ } from "@builder.io/qwik-react";
import { Button, Slider } from "@mui/material";
import { DataGrid, GridColDef, GridValueGetterParams } from "@mui/x-data-grid";

export const MUIButton = qwikify$(Button);
export const MUISlider = qwikify$(Slider, { eagerness: "hover" });

export const TableApp = qwikify$(() => {
  const columns: GridColDef[] = [
    { field: "id", headerName: "ID", width: 70 },
    { field: "firstName", headerName: "First name", width: 130 },
    { field: "lastName", headerName: "Last name", width: 130 },
    {
      field: "age",
      headerName: "Age",
      type: "number",
      width: 90,
    },
    {
      field: "fullName",
      headerName: "Full name",
      description: "This column has a value getter and is not sortable.",
      sortable: false,
      width: 160,
      valueGetter: (params: GridValueGetterParams) =>
        `${params.row.firstName || ""} ${params.row.lastName || ""}`,
    },
  ];

  const rows = [
    { id: 1, lastName: "Snow", firstName: "Jon", age: 35 },
    { id: 2, lastName: "Lannister", firstName: "Cersei", age: 42 },
    { id: 3, lastName: "Lannister", firstName: "Jaime", age: 45 },
    { id: 4, lastName: "Stark", firstName: "Arya", age: 16 },
    { id: 5, lastName: "Targaryen", firstName: "Daenerys", age: null },
    { id: 6, lastName: "Melisandre", firstName: null, age: 150 },
    { id: 7, lastName: "Clifford", firstName: "Ferrara", age: 44 },
    { id: 8, lastName: "Frances", firstName: "Rossini", age: 36 },
    { id: 9, lastName: "Roxie", firstName: "Harvey", age: 65 },
  ];

  return (
    <>
      <h1>Hello from React</h1>

      <div style={{ height: 400, width: "100%" }}>
        <DataGrid
          rows={rows}
          columns={columns}
          pageSize={5}
          rowsPerPageOptions={[5]}
          checkboxSelection
          disableSelectionOnClick
        />
      </div>
    </>
  );
});
*/
