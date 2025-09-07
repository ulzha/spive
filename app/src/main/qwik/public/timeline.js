// zoom & pan learnings from https://observablehq.com/@d3/zoomable-area-chart
// and https://observablehq.com/@bmschmidt/sharing-a-single-zoom-state-between-svg-canvas-and-webgl

const HEIGHT = 10;
// TODO stably compute, the intent is 5 pixels per <smallest Timeline.Scale>
const MAX_SCALE_FACTOR = 16 * 65536 * 29;

function renderTimeline(el, id) {
  const rangePicker = d3.select(el)
    .classed('timeline', true)
    .append('div')
      .classed('range-picker', true);

  const svg = d3.select(el)
    .append('svg')
      .attr('id', 'timeline-svg-' + id)
      .style('outline', '1px solid orange')
      .attr('height', HEIGHT)
      .call(zoomTimeline.zoom);

  svg.append('defs')
    .append('filter')
      .attr('id', 'blur')
      .append('feGaussianBlur')
        .attr('stdDeviation', 2);

  resizeObserver.observe(el);
}

function renderTimelineAxis(x) {
  if (!renderTimelineAxis.g) {
    const xAxisHeight = 20;

    renderTimelineAxis.g = d3.select(".MuiDataGrid-columnHeader[data-field='timeline']")
      .append('div')
        .classed('timeline-axis', true)
        .append('svg')
          .append('g')
            .attr("transform", `translate(0,${xAxisHeight - 1})`);
  }

  const xAxisWidth = x.range()[1];

  renderTimelineAxis.g
    .call(d3.axisTop(x).ticks(xAxisWidth / 80).tickSizeOuter(0));
}

// singleton to zoom all timelines together
function zoomTimeline({transform}) {
  const xz = transform.rescaleX(zoomTimeline.scale);

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

zoomTimeline.scale = d3.scaleUtc()
  .domain([Date.UTC(0, 0), new Date()]);

zoomTimeline.zoom = d3.zoom()
  .scaleExtent([1, MAX_SCALE_FACTOR])
  .on("zoom", zoomTimeline);

zoomTimeline.updateDomain = function() {
  zoomTimeline.scale
    .domain([Date.UTC(0, 0), new Date()]);
};

// (re)sizes svgs to fit container
// while keeping the visible time interval unchanged TODO - should not `now` here
// (alt. current d3 zoom level and offset unchanged? Or sticky major tick interval & visible level)
zoomTimeline.updateExtents = function(width) {
  const svg0 = d3.select('.timeline svg');
  const t0 = d3.zoomTransform(svg0.node());
  console.log(`t0: ${t0.k} ${t0.x}`);
  const xz = t0.rescaleX(zoomTimeline.scale);
  const width0 = xz.range()[1] - xz.range()[0];
  console.log(`domain: ${xz.domain()[0].getTime()}, range: ${xz.range()}, width0: ${width0}`);

  // this kinda helped https://stackoverflow.com/a/42653591/674506
  const x = t0.x * width / width0;
  const translateBy = (x - t0.x) / t0.k;
  const t1 = t0.translate(translateBy, 0);

  zoomTimeline.scale
    .range([0, width]);
  zoomTimeline.zoom
    .extent([[0, 0], [width, HEIGHT]])
    .translateExtent([[0, 0], [width, 0]]);
  d3.selectAll('.timeline svg')
    .attr('width', width);

  console.log(`t1: ${t1.k} ${t1.x}`);
  svg0.call(zoomTimeline.zoom.transform, t1);
};

zoomTimeline.forceZoomEvent = function() {
  const svgs = d3.selectAll('.timeline svg');
  const t0 = d3.zoomTransform(svgs.nodes()[0]);
  svgs.call(zoomTimeline.zoom.transform, t0);
};

const resizeObserver = new ResizeObserver((entries) => {
  for (const entry of entries) {
    if (entry.contentBoxSize && entry.contentBoxSize[0]) {
      console.log(`We will we will updateExtents(${entry.contentBoxSize[0].inlineSize});`);
      zoomTimeline.updateExtents(entry.contentBoxSize[0].inlineSize);

      if (!zoomTimeline.beenHereDoneThat) {
        // TODO kick off transition for header on table load, even when empty
        // TODO a smaller transition for each row separately, when it loads
        d3.select('.timeline svg')
          .transition()
            .duration(750)
            .call(zoomTimeline.zoom.scaleTo, MAX_SCALE_FACTOR / 60, [zoomTimeline.scale.range()[1], 0]);
        zoomTimeline.beenHereDoneThat = true;
      }

      break;
    } else {
      throw new Error('Nonstandard browser: ResizeObserverEntry.contentBoxSize not available');
    }
  }
});
