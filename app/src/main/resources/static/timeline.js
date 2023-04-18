render_timeline = function(el, volume, offset, color) {
  // inspired from https://www.essycode.com/posts/create-sparkline-charts-d3/
  // const WIDTH      = 160;
  // const HEIGHT     = 30;
  // const DATA_COUNT = 40;
  const WIDTH      = 1000;
  const HEIGHT     = 10;
  const DATA_COUNT = 250;
  const BAR_WIDTH  = WIDTH / DATA_COUNT - 1;
  const data = d3.range(DATA_COUNT).map( d => (d < DATA_COUNT - offset ?  (Math.random() < volume ? 1 - Math.random() : 1) : 1) );
  const x    = d3.scaleLinear().domain([0, DATA_COUNT]).range([0, WIDTH]);
  const y    = d3.scaleLinear().domain([0, 1]).range([HEIGHT, 0]);
  const svg = el.append('svg')
    .attr('width', WIDTH)
    .attr('height', HEIGHT)
    .append('g');

  svg.selectAll('.bar').data(data)
    .enter()
    .append('rect')
      .attr('class', 'bar')
      .attr('x', (d, i) => x(i))
      .attr('y', d => HEIGHT - y(d))
      .attr('width', BAR_WIDTH)
      .attr('height', d => y(d))
      .attr('fill', d => (d < .2 ? color : '#1db855'));
}

render_event_html = function(event) {
  return `
  <td class="MuiTableCell-root-526 MuiTableCell-body-528" colspan="1">` + event.time.toISOString() + `#0</td>
  <td class="MuiTableCell-root-526 MuiTableCell-body-528" colspan="1">` + event.json + `
      <button class="MuiButtonBase-root-337 MuiIconButton-root-354" tabindex="0" type="button" gaprops="[object Object]" title="Copy to clipboard"><span class="MuiIconButton-label-361"><span class="material-icons icon">content_copy</span></span><span class="MuiTouchRipple-root-581"></span></button></td>
  <td class="MuiTableCell-root-526 MuiTableCell-body-528" colspan="1" style="text-align:right;">` + event.handled_in + `</td>
  <td class="MuiTableCell-root-526 MuiTableCell-body-528" colspan="1">
    <div class="MuiGrid-root-624 MuiGrid-container-625 MuiGrid-spacing-xs-2-648 MuiGrid-wrap-xs-nowrap-631 MuiGrid-align-items-xs-center-633">
      <div class="MuiGrid-root-624 MuiGrid-item-626"><span class="legend-swatch color-` + event.status + `"></span></div>
      <div class="MuiGrid-root-624 MuiGrid-item-626"><span class="">YooException</span></div>
    </div>
  </td>
  <td class="MuiTableCell-root-526 MuiTableCell-body-528" colspan="1" style="text-align: center;">
    <a href="https://console.cloud.google.com/logs/viewer" target="_blank" rel="noopener noreferrer" style="display:` + event.logs_display + `">
      <img class="logs-icon" src="data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+Cjxzdmcgd2lkdGg9IjEyOHB4IiBoZWlnaHQ9IjExNHB4IiB2aWV3Qm94PSIwIDAgMTI4IDExNCIgdmVyc2lvbj0iMS4xIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIj4KICAgIDwhLS0gR2VuZXJhdG9yOiBTa2V0Y2ggMy43LjIgKDI4Mjc2KSAtIGh0dHA6Ly93d3cuYm9oZW1pYW5jb2RpbmcuY29tL3NrZXRjaCAtLT4KICAgIDx0aXRsZT5Mb2dzXzEyOHB4PC90aXRsZT4KICAgIDxkZXNjPkNyZWF0ZWQgd2l0aCBTa2V0Y2guPC9kZXNjPgogICAgPGRlZnM+CiAgICAgICAgPGxpbmVhckdyYWRpZW50IHgxPSI1MCUiIHkxPSItMy44OTc4MzA3M2UtMTUlIiB4Mj0iNTAlIiB5Mj0iOTkuODQ0OTk1OCUiIGlkPSJsaW5lYXJHcmFkaWVudC0xIj4KICAgICAgICAgICAgPHN0b3Agc3RvcC1jb2xvcj0iIzQzODdGRCIgb2Zmc2V0PSIwJSI+PC9zdG9wPgogICAgICAgICAgICA8c3RvcCBzdG9wLWNvbG9yPSIjNDY4M0VBIiBvZmZzZXQ9IjEwMCUiPjwvc3RvcD4KICAgICAgICA8L2xpbmVhckdyYWRpZW50PgogICAgPC9kZWZzPgogICAgPGcgaWQ9IlBhZ2UtMSIgc3Ryb2tlPSJub25lIiBzdHJva2Utd2lkdGg9IjEiIGZpbGw9Im5vbmUiIGZpbGwtcnVsZT0iZXZlbm9kZCI+CiAgICAgICAgPGcgaWQ9IkxvZ3NfMTI4cHgiIHRyYW5zZm9ybT0idHJhbnNsYXRlKC0xLjAwMDAwMCwgMC4wMDAwMDApIj4KICAgICAgICAgICAgPHBhdGggc3Ryb2tlPSIjRkZGRkZGIiBzdHJva2Utd2lkdGg9IjMiIGQ9Ik0yOC43OTA2LDEwOC4yMTY2IEwyLjU0LDYyLjc0OTMgQzAuNDg1OSw1OS4xOTE1IDAuNDg1OSw1NC44MDgxIDIuNTQsNTEuMjUwMyBMMjguNzkwNiw1Ljc4MzEgQzMwLjg0NDcsMi4yMjUzIDM0LjY0MDksMC4wMzM2IDM4Ljc0OTEsMC4wMzM2IEw5MS4yNTAzLDAuMDMzNiBDOTUuMzU4NSwwLjAzMzYgOTkuMTU0NywyLjIyNTMgMTAxLjIwODgsNS43ODMxIEwxMjcuNDU5NCw1MS4yNTAzIEMxMjkuNTEzNSw1NC44MDgxIDEyOS41MTM1LDU5LjE5MTUgMTI3LjQ1OTQsNjIuNzQ5MyBMMTAxLjIwODgsMTA4LjIxNjUgQzk5LjE1NDcsMTExLjc3NDMgOTUuMzU4NSwxMTMuOTY2IDkxLjI1MDMsMTEzLjk2NiBMMzguNzQ5MSwxMTMuOTY2IEMzNC42NDA5LDExMy45NjYxIDMwLjg0NDgsMTExLjc3NDQgMjguNzkwNiwxMDguMjE2NiBMMjguNzkwNiwxMDguMjE2NiBaIiBpZD0iU2hhcGUiIGZpbGw9InVybCgjbGluZWFyR3JhZGllbnQtMSkiPjwvcGF0aD4KICAgICAgICAgICAgPHBhdGggZD0iTTk1LDMxIEw3Ny40LDM5IEw2MSwzOSBMNTAsMjggTDQzLjIzMDgsMzkgTDQzLDM5IEwzNSw0MyBMNDIuNjUsNTAuNjUgTDQxLDgyIEw3Mi45NjYxLDExMy45NjYxIEw5MS4yNTAzLDExMy45NjYxIEM5NS4zNTg1LDExMy45NjYxIDk5LjE1NDcsMTExLjc3NDQgMTAxLjIwODgsMTA4LjIxNjYgTDEyNy4xOTk1LDYzLjE5OTYgTDk1LDMxIEw5NSwzMSBaIiBpZD0iU2hhcGUiIGZpbGw9IiMwMDAwMDAiIG9wYWNpdHk9IjAuMDciPjwvcGF0aD4KICAgICAgICAgICAgPHJlY3QgaWQ9IlJlY3RhbmdsZS1wYXRoIiBmaWxsPSIjRkZGRkZGIiB4PSI1OCIgeT0iMzEiIHdpZHRoPSIzNyIgaGVpZ2h0PSIxMSI+PC9yZWN0PgogICAgICAgICAgICA8cmVjdCBpZD0iUmVjdGFuZ2xlLXBhdGgiIGZpbGw9IiNGRkZGRkYiIHg9IjQzIiB5PSI1NyIgd2lkdGg9IjE2IiBoZWlnaHQ9IjMiPjwvcmVjdD4KICAgICAgICAgICAgPHJlY3QgaWQ9IlJlY3RhbmdsZS1wYXRoIiBmaWxsPSIjRkZGRkZGIiB4PSI1OCIgeT0iNTMiIHdpZHRoPSIzNyIgaGVpZ2h0PSIxMSI+PC9yZWN0PgogICAgICAgICAgICA8cmVjdCBpZD0iUmVjdGFuZ2xlLXBhdGgiIGZpbGw9IiNGRkZGRkYiIHg9IjQzIiB5PSI3OSIgd2lkdGg9IjE2IiBoZWlnaHQ9IjMiPjwvcmVjdD4KICAgICAgICAgICAgPHJlY3QgaWQ9IlJlY3RhbmdsZS1wYXRoIiBmaWxsPSIjRkZGRkZGIiB4PSI0MSIgeT0iNDMiIHdpZHRoPSIzIiBoZWlnaHQ9IjM5Ij48L3JlY3Q+CiAgICAgICAgICAgIDxnIGlkPSJHcm91cCIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoMzUuMDAwMDAwLCAyOC4wMDAwMDApIiBmaWxsPSIjRkZGRkZGIj4KICAgICAgICAgICAgICAgIDxyZWN0IGlkPSJSZWN0YW5nbGUtcGF0aCIgeD0iMCIgeT0iMCIgd2lkdGg9IjE1IiBoZWlnaHQ9IjE1Ij48L3JlY3Q+CiAgICAgICAgICAgICAgICA8cmVjdCBpZD0iUmVjdGFuZ2xlLXBhdGgiIHg9IjIzIiB5PSI0NyIgd2lkdGg9IjM3IiBoZWlnaHQ9IjExIj48L3JlY3Q+CiAgICAgICAgICAgIDwvZz4KICAgICAgICA8L2c+CiAgICA8L2c+Cjwvc3ZnPg==" alt="Logs">
    </a>
  </td>`;
}
