function dummyHertz(t) {
  // sawtooth growing from 0 to 1 over the course of an hour
  return t % (60 * 60 * 1000) / (60 * 60 * 1000);
}

function dummyHertzEstimate(dataSecond, windowStart) {
  // average over the secondly windows that have been received
  const received = dataSecond.filter(d => d.windowStart >= windowStart);
  if (!received.length) {
    return 0;
  }
  return received.reduce((acc, d) => acc + d.height, 0) / received.length;
}

export function generateDummyBars(svgEl, k) {
  const DATA_COUNT = 175;
  const now = new Date();

  const dataMinute = d3.range(-DATA_COUNT, 0).map( d => {
    const windowStart = Math.floor(now.getTime() / (60 * 1000)) * (60 * 1000) + d * 60 * 1000;
    return {
      windowStart: windowStart,
      windowEnd: windowStart + 60 * 1000,
      height: dummyHertz(windowStart) * 10 * k,
    };
  });
  const dataSecond = d3.range(-60, 0).map( d => {
    const windowStart = Math.floor(now.getTime() / (1000)) * (1000) + d * 1000;
    return {
      windowStart: windowStart,
      windowEnd: windowStart + 1000,
      height: dummyHertz(windowStart) * 10 * k,
    };
  });

  const lastWindow = dataMinute[dataMinute.length - 1];
  const blur = [
    {
      windowStart: lastWindow.windowEnd,
      windowEnd: lastWindow.windowEnd + 60 * 1000,
      height: dummyHertzEstimate(dataSecond, lastWindow.windowEnd),
      blurStart: lastWindow.windowEnd + 60 * 1000 * dataSecond.length / 60,
    }
  ];

  addBars(svgEl, dataMinute, blur);
  addBars(svgEl, dataSecond);
}
