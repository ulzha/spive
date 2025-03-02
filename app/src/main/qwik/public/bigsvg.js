// SVG coordinates have limited precision. We can't draw a many years long timeline in one piece and zoom and pan to the required extremes - position and translation results start to become screwed when numbers reach into ~9 digits.
// Therefore we keep a collection of rendered SVG `g`roups, where `rect`angle coordinates don't reach beyond 5px times something.
// Data tiles fetched from API are small, groups are generally larger and cover a number of contiguous tiles.
// (The tiles fetched from API are parsed into JS that has finite Date precision in the first place. D3 transform ditto. TODO more elaborate windowStart & windowEnd representation when needed.)
// Some rendered groups are invisible - retained as a cache, but with `visibility: hidden`.
// Only the visible groups undergo zooming and panning.
// Coordinates within groups are integer pixel sizes for their most zoomed-out display, to prevent sub-pixel artifacts.
// Might generalize to more widgets than just bars...

// in pixels
const BAR_MAX_HEIGHT = 10;
const BAR_INTERVAL = 5;
const BAR_WIDTH = 3;
const y = d3.scaleLinear().domain([0, 1]).range([BAR_MAX_HEIGHT, 0]);

// seconds per bar
const windowLengths = [
  1,
  60,
  60 * 60,
  60 * 60 * 24,
];

function BarGroup(windowLength, start, end, svg) {
  this.windowLength = windowLength;
  this.start = start;
  this.end = end;
  this.g = svg.append('g');
}

const barGroups = [
  [],
  [],
  [],
  [],
];

function getBarGroup(svg, zoomLevel, windowStart) {
  // something slightly wiser might need to be done here to prune or merge groups
  const group = barGroups[zoomLevel].find(g => windowStart >= g.start && windowStart <= g.end);
  if (group) {
    return group;
  }
  const newGroup = new BarGroup(windowLengths[zoomLevel], windowStart, windowStart, svg);
  barGroups[zoomLevel].push(newGroup);
  return newGroup;
}

function updateBarGroup(group, data) {
  // TODO
}

function addBars(svg, data) {
  const windowLength = data[0].windowEnd - data[0].windowStart;
  const zoomLevel = windowLengths.findIndex(z => z >= windowLength / 1000);
  if (zoomLevel === -1) {
    throw new Error('Unexpected window length');
  }
  const group = getBarGroup(svg, zoomLevel, data[0].windowStart);
  group.end = Math.max(group.end, data[data.length - 1].windowEnd);
  console.log("adding bars", windowLength, zoomLevel, group.start, group.end, data[0].windowStart, data[data.length - 1].windowEnd);
  group.g
    .attr('x', 0)  // if this gives flicker, then maybe transform on the fly (if groups never grow backwards then it won't)
    .selectAll('.bar')
    .data(data, d => d.windowStart)
    .join('rect')
      .classed('bar', true)
      .attr('x', d => (d.windowStart - group.start) / windowLength * BAR_INTERVAL)
      .attr('y', d => BAR_MAX_HEIGHT - y(d.nOutputEvents))
      .attr('width', BAR_WIDTH)
      .attr('height', d => y(d.nOutputEvents))
      .attr('fill', '#1db855');
}

function zoomBars(el, tr, xz) {
  for (const group of barGroups[1]) {
    const x = xz(group.start);
    const x2 = xz(group.end);
    const k1 = (x2 - x) * windowLengths[1] * 1000 / (group.end - group.start) / 5;
    console.log("applying zoom for level 1 group", new Date(group.start), tr.toString(), `translate(${x}, 0) scale(${k1}, 1)`);
    group.g
      .attr('transform', `translate(${x}, 0) scale(${k1}, 1)`);
  }
}
