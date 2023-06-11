render_timeline = function(el, volume, offset, color) {
  // inspired from https://www.essycode.com/posts/create-sparkline-charts-d3/
  const WIDTH      = 700;
  const HEIGHT     = 10;
  const DATA_COUNT = 175;
  const BAR_WIDTH  = WIDTH / DATA_COUNT - 1;
  const data = d3.range(DATA_COUNT).map( d => (d < DATA_COUNT - offset ?  (Math.random() < volume ? 1 - Math.random() : 1) : 1) );
  const x    = d3.scaleLinear().domain([0, DATA_COUNT]).range([0, WIDTH]);
  const y    = d3.scaleLinear().domain([0, 1]).range([HEIGHT, 0]);

  const rangePicker = d3.select(el)
    .append('div')
      .attr('class', 'range-picker');

  const svg = d3.select(el)
    .append('svg')
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
