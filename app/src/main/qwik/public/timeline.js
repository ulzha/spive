// zoom & pan learnings from https://observablehq.com/@d3/zoomable-area-chart
// and https://observablehq.com/@bmschmidt/sharing-a-single-zoom-state-between-svg-canvas-and-webgl

renderTimeline = function(el, volume, offset, color) {
  // inspired from https://www.essycode.com/posts/create-sparkline-charts-d3/
  const WIDTH      = 700;
  const HEIGHT     = 10;
  const DATA_COUNT = 175;
  const BAR_WIDTH  = WIDTH / DATA_COUNT - 1;
  const data = d3.range(DATA_COUNT).map( d => (d < DATA_COUNT - offset ?  (Math.random() < volume ? 1 - Math.random() : 1) : 1) );

  const y    = d3.scaleLinear().domain([0, 1]).range([HEIGHT, 0]);

  const rangePicker = d3.select(el)
    .classed('timeline', true)
    .append('div')
      .classed('range-picker', true);

  const svg = d3.select(el)
    .append('svg')
      .style('outline', '1px solid orange')
      .attr('width', WIDTH)
      .attr('height', HEIGHT);
  const g = svg.append('g');

  g.selectAll('.bar').data(data)
    .enter()
    .append('rect')
      .classed('bar', true)
      .attr('y', d => HEIGHT - y(d))
      .attr('width', BAR_WIDTH)
      .attr('height', d => y(d))
      .attr('fill', d => (d < .2 ? color : '#1db855'));

  if (!zoomTimeline.zoom) {
    zoomTimeline.zoom = d3.zoom()
      .scaleExtent([1, 65536]) // TODO max to 5 pixels per second default. Allow custom/infinite?
      .extent([[0, 0], [WIDTH, HEIGHT]])
      .translateExtent([[0, 0], [WIDTH, 0]])
      .on("zoom", zoomTimeline);
  }

  // attach, and kick off animation on row load
  svg.call(zoomTimeline.zoom)
    .transition()
      .duration(750)
      .call(zoomTimeline.zoom.scaleTo, 4, [WIDTH * .9, 0]);
}

function renderTimelineAxis(x) {
  if (!renderTimelineAxis.g?.size()) {
    const xAxisHeight = 20;

    renderTimelineAxis.g = d3.select(".MuiDataGrid-columnHeader[data-field='timeline']")
      .append('svg')
        .classed('timeline-axis', true)
        .attr('width', 700)
        .attr('height', xAxisHeight)
        .append('g')
          .attr("transform", `translate(0,${xAxisHeight - 1})`);
  }

  renderTimelineAxis.g
    .call(d3.axisTop(x).ticks(700 / 80).tickSizeOuter(0));
}

function zoomTimeline({transform}) {
  const x = d3.scaleUtc()
    .domain([new Date(Date.UTC(0, 0)), new Date()])
    .range([0, 700]);

  const xz = transform.rescaleX(x);

  d3.select(this)
    .selectAll(".bar")
      .attr("x", (d, i) => xz(new Date(Date.UTC(1970 + i, 0))))

  d3.selectAll(".timeline svg")
    // avoid recursing on the element that generated the call. Note: `this` won't work with an arrow function
    .filter(function(d, i) {return d3.zoomTransform(this) != transform;})
    .call(zoomTimeline.zoom.transform, transform);

  renderTimelineAxis(xz);
}
