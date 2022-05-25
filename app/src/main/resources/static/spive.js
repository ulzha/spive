window.onload = function () {
    const legend = d3.select('#legend');
    d3.select('#legend-toggle')
        .on('mouseover', (event, d) => legend
            .style('left', event.pageX - legend.node().getBoundingClientRect().width + 'px')
            .style('top', event.pageY - 28 + 'px')
            .style('visibility', 'visible'))
        .on('mouseout', (event, d) => legend
            .style('visibility', 'hidden'));

    d3.json('/api/applications')
        .then(function (apps) {
            for (var app of apps) {
                var rowHtml = '<td class="MuiTableCell-root-2339 MuiTableCell-body-2341"><a class=" stajl2352" href="https://company.net/event-sourced-applications/' + app.name + '">' + app.name + '</a></td><td class="MuiTableCell-root-2339 MuiTableCell-body-2341" id="timeline-' + app.id + '"></td>';
                d3.select('#applications').append('tr').attr('class', 'MuiTableRow-root-2335').html(rowHtml);
                render_timeline(d3.select('#timeline-' + app.id), 1, 0, '#1db855');
            }
        });
}
