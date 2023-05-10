window.onload = function () {
    d3.json('/api/applications')
        .then(function (apps) {
            console.debug('Apps', apps);
        }).catch(function (error) {
            console.debug('Meh', error);
        });
}
