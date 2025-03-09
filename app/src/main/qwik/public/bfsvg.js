// Big, fast SVG

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

// seconds per bar
const windowLengths = [
  1,
  60,
  60 * 60,
  60 * 60 * 24,
  60 * 60 * 24 * 365,  // FIXME
];

function BarGroup(windowLength, start, end, svg) {
  this.windowLength = windowLength;
  this.start = start;
  this.end = end;
  this.data = [];
  this.g = svg.append('g');
}

BarGroup.prototype.update = function(data) {
  this.end = Math.max(this.end, data[data.length - 1].windowEnd);
  var startIndex = this.data.findIndex(d => d.windowStart >= data[0].windowStart);
  if (startIndex === -1) {
    startIndex = this.data.length;
  }
  var endIndex = this.data.findIndex(d => d.windowStart >= data[data.length - 1].windowEnd);
  if (endIndex === -1) {
    endIndex = this.data.length;
  }
  this.data.splice(startIndex, endIndex - startIndex, ...data);
}

const barGroupsByApplicationId = {};

function getBarGroup(svg, zoomLevel, windowStart) {
  const barGroups = barGroupsByApplicationId[svg.attr('id')] = barGroupsByApplicationId[svg.attr('id')] || [[], [], [], []];
  // something slightly wiser might need to be done here to prune or merge groups
  const group = barGroups[zoomLevel].find(g => windowStart >= g.start && windowStart <= g.end);
  if (group) {
    return group;
  }
  const newGroup = new BarGroup(windowLengths[zoomLevel], windowStart, windowStart, svg);
  barGroups[zoomLevel].push(newGroup);
  return newGroup;
}

function addBars(svg, data) {
  const windowLength = data[0].windowEnd - data[0].windowStart;
  const zoomLevel = windowLengths.findIndex(z => z >= windowLength / 1000);
  if (zoomLevel === -1) {
    throw new Error('Unexpected window length');
  }
  const group = getBarGroup(svg, zoomLevel, data[0].windowStart);
  group.update(data);
  console.log("adding bars", windowLength, zoomLevel, group.start, group.end, data[0].windowStart, data[data.length - 1].windowEnd);
  group.g
    .attr('x', 0)  // if this gives flicker, then maybe transform on the fly (if groups never grow backwards then it won't)
    .selectAll('.bar')
    .data(group.data, d => d.windowStart)
    .join('rect')
      .classed('bar', true)
      .attr('x', d => (d.windowStart - group.start) / windowLength * BAR_INTERVAL)
      .attr('y', d => BAR_MAX_HEIGHT - d.height)
      .attr('width', BAR_WIDTH)
      .attr('height', d => d.height)
      .attr('fill', '#1db855');
}

function zoomBars(el, xz) {
  const barGroups = barGroupsByApplicationId[el.attr('id')];
  if (!barGroups) {
    return;
  }

  // choose only one level that's going to be visible - the first one whose bars are spaced at least BAR_INTERVAL apart
  const levelVisible = new Array(windowLengths.length).fill(false);
  for (i = 0; i < windowLengths.length; i++) {
    const xInterval = xz(new Date(windowLengths[i] * 1000)) - xz(new Date(0));
    console.log(i, "xInterval", xInterval);
    if (xInterval >= BAR_INTERVAL) {
      levelVisible[i] = true;
      break;
    }
  };

  for (const [zoomLevel, groups] of barGroups.entries()) {
    for (const group of groups) {
      if (!levelVisible[zoomLevel] || group.end < xz.domain()[0] || group.start > xz.domain()[1]) {
        group.g.attr('visibility', 'hidden');
      } else {
        const x = xz(group.start);
        const x2 = xz(group.end);
        const k = (x2 - x) * windowLengths[zoomLevel] * 1000 / (group.end - group.start) / 5;
        console.log("visible group at level", zoomLevel, group.start, group.end, `translate(${x}, 0) scale(${k}, 1)`);
        group.g
        .attr('visibility', 'visible')
        .attr('transform', `translate(${x}, 0) scale(${k}, 1)`);
      }
    }
  }
}
