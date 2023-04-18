window.onload = function () {
    const legend = d3.select('#legend');
    d3.select('#legend-toggle')
        .on('mouseover', (event, d) => legend
            .style('left', event.pageX - legend.node().getBoundingClientRect().width + 'px')
            .style('top', event.pageY - 28 + 'px')
            .style('visibility', 'visible'))
        .on('mouseout', (event, d) => legend
            .style('visibility', 'hidden'));

    d3.select('.content')
        .on('click', (event, d) => {
            shite = [
                {id: 1, name: "App 1"},
                {id: 2, name: "App 2"},
                {id: 3, name: "SeriouslyNonfunnyApp"}
            ];

            for (var app of shite) {
                var rowHtml = '<td class="MuiTableCell-root-2339 MuiTableCell-body-2341"><a class="text" href="https://company.net/event-sourced-applications/' + app.name + '">' + app.name + '</a></td><td class="MuiTableCell-root-2339 MuiTableCell-body-2341" id="timeline-' + app.id + '"></td>';
                d3.select('#applications').append('tr').attr('class', 'MuiTableRow-root-2335').html(rowHtml);
                render_timeline(d3.select('#timeline-' + app.id), 1, 0, '#1db855');
                let eventHtml = render_event_html({time: new Date(), json: '{"foo": "bar"}', handled_in: '3 ms', status: 'ok', logs_display: 'visible'});
                d3.select('#applications')
                  .append('tr')
                  .append('td').attr('colspan', '2')
                  .append('table')
                  .append('tbody')
                  .append('tr').attr('class', 'event')
                  .html(eventHtml);
            }
        });

    d3.json('/api/applications')
        .then(function (apps) {
            for (var app of apps) {
                var rowHtml = '<td class="MuiTableCell-root-2339 MuiTableCell-body-2341"><a class="text" href="https://company.net/event-sourced-applications/' + app.name + '">' + app.name + '</a></td><td class="MuiTableCell-root-2339 MuiTableCell-body-2341" id="timeline-' + app.id + '"></td>';
                d3.select('#applications').append('tr').attr('class', 'MuiTableRow-root-2335').html(rowHtml);
                render_timeline(d3.select('#timeline-' + app.id), 1, 0, '#1db855');
            }
        });
}
