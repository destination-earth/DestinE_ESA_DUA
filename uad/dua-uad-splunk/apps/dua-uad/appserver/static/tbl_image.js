require([
    'splunkjs/mvc/tableview',
    'splunkjs/mvc',
    'underscore',
    'splunkjs/mvc/simplexml/ready!'], function (
        TableView,
        mvc,
        _
    ) {

    var CustomCellRenderer = TableView.BaseCellRenderer.extend({
        canRender: function (cell) {
            return (cell.field === 'image');
        },
        render: function ($td, cell) {
            // from url: '<img style="height:100px;width:100%" src="https://www.iconspng.com/images/yellow-sun-icon/<%- image %>" alt="out"></img>'
            $td.addClass('mydata-cell').html(_.template('<img style="width:100%;max-width:160px;max-height:212px;" src="data:image/jpeg;base64,<%- image %>" alt="icon"></img>', {
                image: cell.value
            }));
        }
    });

    var tableElement = mvc.Components.getInstance("table_1");
    tableElement.getVisualization(function (tableView) {
        // Add custom cell renderer, the table will re-render automatically.
        tableView.addCellRenderer(new CustomCellRenderer());
        // Force the table to re-render
        tableView.table.render();
    });
});
