window.onload = function () {
    d3.select('body')
        .on('click', (event, d) => {
            shite = [
            ];

            var i = 1;
            for (var app of shite) {
                var rowHtml = '<td class="MuiTableCell-root-2339 MuiTableCell-body-2341 app-name"><a class="text" href="https://company.net/event-sourced-applications/' + app.name + '">' + app.name + '</a></td><td class="timeline" id="timeline-' + app.id + '"><div class="range-picker"></div></td>';
                d3.select('#applications')
                  .append('tr').attr('class', i % 2 ? 'odd' : 'even')
                  .html(rowHtml);
                render_timeline(d3.select('#timeline-' + app.id), 1, 0, '#1db855');
                let eventHtml = render_event_html({time: new Date(), json: '{"foo": "bar"}', handled_in: '3 ms', status: 'ok', logs_display: 'visible'});
                var expanded_cell = d3.select('#applications')
                  .append('tr').attr('class', 'expanded')
                  .append('td').attr('colspan', '2');
                expanded_cell
                  .append('table')
                  .append('tbody')
                  .append('tr').attr('class', 'event')
                  .html(eventHtml);
                i++;
            }
        });

    d3.json('/api/applications')
        .then(function (apps) {
            for (var app of apps) {
                var rowHtml = '<td class="MuiTableCell-root-2339 MuiTableCell-body-2341"><a class="text" href="https://company.net/event-sourced-applications/' + app.name + '">' + app.name + '</a></td><td class="MuiTableCell-root-2339 MuiTableCell-body-2341" id="timeline-' + app.id + '"></td>';
                d3.select('#applications')
                  .append('tr').attr('class', i % 2 ? 'odd' : 'even')
                  .html(rowHtml);
                render_timeline(d3.select('#timeline-' + app.id), 1, 0, '#1db855');
            }
        });
}
