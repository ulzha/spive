// zoom & pan learnings from https://observablehq.com/@d3/zoomable-area-chart
// and https://observablehq.com/@bmschmidt/sharing-a-single-zoom-state-between-svg-canvas-and-webgl

renderTimeline = function(el, id) {
  // inspired from https://www.essycode.com/posts/create-sparkline-charts-d3/
  // and dunno where the overflow-into-another-color compaction idea came from
  const WIDTH      = 700;
  const HEIGHT     = 10;

  const rangePicker = d3.select(el)
    .classed('timeline', true)
    .append('div')
      .classed('range-picker', true);

  const svg = d3.select(el)
    .append('svg')
      .attr('id', 'timeline-svg-' + id)
      .style('outline', '1px solid orange')
      .attr('width', WIDTH)
      .attr('height', HEIGHT);

  // singleton to zoom all timelines together
  if (!zoomTimeline.zoom) {
    zoomTimeline.zoom = d3.zoom()
      .scaleExtent([1, 16 * 65536]) // TODO max to 5 pixels per second default. Allow custom/infinite?
      .extent([[0, 0], [WIDTH, HEIGHT]])
      .translateExtent([[0, 0], [WIDTH, 0]])
      .on("zoom", zoomTimeline);
  }

  // attach, and kick off animation on row load
  svg.call(zoomTimeline.zoom)
    .transition()
      .duration(750)
      .call(zoomTimeline.zoom.scaleTo, 16 * 65536, [WIDTH, 0]);
}

function generateDummyTimelineBars(applications) {
  const DATA_COUNT = 175;
  const WIDTH      = 700;
  const HEIGHT     = 10;
  const y = d3.scaleLinear().domain([0, 1]).range([HEIGHT, 0]);

  for (const a of applications) {
    const data = d3.range(DATA_COUNT).map( d => {
      windowStart = Math.floor(new Date().getTime() / (60 * 1000)) * (60 * 1000) - d * 60 * 1000;
      return {
        windowStart: windowStart,
        windowEnd: windowStart + 60 * 1000,
        nOutputEvents: 1 - windowStart % (60 * 60 * 1000) / (60 * 60 * 1000),
      };
    });
    d3.select(`#timeline-svg-${a.id}`)
      .selectAll('.bar')
      .data(data, d => d.windowStart)
      .join('rect')
        .classed('bar', true)
        .attr('y', d => HEIGHT - y(d.nOutputEvents))
        .attr('width', 3)
        .attr('height', d => y(d.nOutputEvents))
        .attr('fill', '#1db855');
  }

  // TODO not when panned somewhere intentionally
  zoomTimeline.zoom.translateTo(d3.select('.timeline svg'), new Date(), 0, [WIDTH, 0]);
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

  // TODO hide/drop granularity groups
  // while keeping the possibility to show coarser granularity when the finer window is not available
  d3.select(this)
    .selectAll(".bar")
      .attr("x", d => xz(d.windowStart))

  d3.selectAll(".timeline svg")
    // avoid recursing on the element that generated the call. Note: `this` won't work with an arrow function
    .filter(function(d, i) {return d3.zoomTransform(this) != transform;})
    .call(zoomTimeline.zoom.transform, transform);

  renderTimelineAxis(xz);
}
