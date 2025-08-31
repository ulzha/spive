// zoom & pan learnings from https://observablehq.com/@d3/zoomable-area-chart
// and https://observablehq.com/@bmschmidt/sharing-a-single-zoom-state-between-svg-canvas-and-webgl

renderTimeline = function(el, id) {
  // inspired from https://www.essycode.com/posts/create-sparkline-charts-d3/
  // and dunno where the overflow-into-another-color compaction idea came from
  const WIDTH      = 700;
  const HEIGHT     = 10;
  // TODO stably compute, the intent is 5 pixels per <smallest Timeline.Scale>
  const MAX_SCALE_FACTOR = 16 * 65536 * 27.1;

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

  svg.append('defs')
    .append('filter')
      .attr('id', 'blur')
      .append('feGaussianBlur')
        .attr('stdDeviation', 2);

  // singleton to zoom all timelines together
  if (!zoomTimeline.zoom) {
    zoomTimeline.zoom = d3.zoom()
      .scaleExtent([1, MAX_SCALE_FACTOR])
      .extent([[0, 0], [WIDTH, HEIGHT]])  // getBoundingClientRect?
      .translateExtent([[0, 0], [WIDTH, 0]])
      .on("zoom", zoomTimeline);
  }

  // attach, and kick off animation on row load
  svg.call(zoomTimeline.zoom)
    .transition()
      .duration(750)
      .call(zoomTimeline.zoom.scaleTo, MAX_SCALE_FACTOR / 60, [WIDTH, 0]);
}

function renderTimelineAxis(x) {
  if (!renderTimelineAxis.g?.size()) {
    const xAxisHeight = 20;

    renderTimelineAxis.g = d3.select(".MuiDataGrid-columnHeader[data-field='timeline']")
      .append('div')
        .classed('timeline-axis', true)
        .append('svg')
          .append('g')
            .attr("transform", `translate(0,${xAxisHeight - 1})`);
  }

  renderTimelineAxis.g
    .call(d3.axisTop(x).ticks(700 / 80).tickSizeOuter(0));
}

function zoomTimeline({transform}) {
  const now = new Date();
  const x = d3.scaleUtc()
    .domain([Date.UTC(0, 0), now])
    .range([0, 700]);

  const xz = transform.rescaleX(x);

  // Bars are moved around en masse, courtesy of SVG groups
  zoomBars(d3.select(this), xz);

  d3.selectAll(".timeline svg")
    // avoid recursing on the element that generated the call. Note: `this` won't work with an arrow function
    .filter(function(d, i) {return d3.zoomTransform(this) != transform;})
    .call(zoomTimeline.zoom.transform, transform);

  // The axis and labels are redrawn on every zoom and pan event
  renderTimelineAxis(xz);

  if (zoomTimeline.onZoomed) {
    zoomTimeline.onZoomed(
      xz.domain()[0],
      xz.domain()[1],
      visibleLevel(xz)
    );
  }
}
