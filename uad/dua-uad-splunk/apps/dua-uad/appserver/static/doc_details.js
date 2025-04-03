require([
    'splunkjs/mvc/tableview',
    'splunkjs/mvc/searchmanager',
    'splunkjs/mvc',
    'underscore',
    'splunkjs/mvc/simplexml/ready!'],function(
    TableView,
    SearchManager,
    mvc,
    _
    ){

    /*
     * Build query for master-detail views.
     */
    var buildQuery = function(rowData) {
        return 'index=dua ' +
               'mission="' + rowData.values[0] + '" doi="' + rowData.values[1] + '" ' +
               '| spath "classification.method" ' + 
               '| search "classification.method"="' + rowData.values[5] + '" ' +
               '| spath path=classification{}.training_id output=training_id | eval training_id=if(isnotnull(training_id),training_id,"") ' +
               '| eval gen_time=strftime(generation_time, "%Y-%m-%d") ' +
               '| search training_id="' + rowData.values[7] + '" gen_time="' + rowData.values[8] + '" ' +
               '| spath path=authors{}.author_name output=author_names | eval author_names=mvjoin(author_names, ", ") ' +
               '| spath path=funders{} output=funders | eval funders=mvjoin(funders, "; ") ' +
               '| spath path=affiliations{} output=affiliations | eval affiliations=mvjoin(affiliations, "; ") ' +
               '| spath path=countries{} output=countries | eval countries=mvjoin(countries, "; ") ' +
               '| spath path=abstract | spath path=thumbnail ' +
               '| lookup kv_thumbnails name AS thumbnail OUTPUT image ' +
               '| table image, abstract, author_names, countries, journal_name, genre, repo_name';
    }

    var CustomCellRenderer = TableView.BaseCellRenderer.extend({
        canRender: function(cell) {
            return (cell.field === 'image');
        },
        render: function($td, cell) {
			// from url: '<img style="height:100px;width:100%" src="https://www.iconspng.com/images/yellow-sun-icon/<%- image %>" alt="out"></img>'
            $td.addClass('mydata-cell').html(_.template('<img style="height:212px;width:100%" src="data:image/jpeg;base64,<%- image %>" alt="icon"></img>', {
                image: cell.value
            }));
        }
    }); 
	
    var EventSearchBasedRowExpansionRenderer = TableView.BaseRowExpansionRenderer.extend({
        // initialize will run once, so we will set up a search and a chart to be reused.        
        initialize: function(args) {
            //console.log("==> 1. initialize");
            if (!args.queryBuilder) {
                throw new Error('queryBuilder should be set.');
            }
            var that = this;
            that._queryBuilder = args.queryBuilder;
            that._searchId = args.searchId;
            that._tableId = args.tableId;
			
            that._deferred = null;            
            that._searchManager = new SearchManager({
                id: that._searchId,
                preview: false
            });
            that._tableView = new TableView({
                id: that._tableId,
                managerid: that._searchId,
                count: '100',
                dataOverlayMode: 'none',
                'link.visible': '0',
                'refresh.display': 'progressbar',
                rowNumbers: 'false',
                drilldown: 'none',
                drilldownRedirect: 'false',  
                wrap: 'true'
            });
            //console.log("==> 2. initialize");
        },

        canRender: function(rowData) {
            // Since more than one row expansion renderer can be registered we let each decide if they can handle that
            // data
            // Here we will always handle it.
            return true;
        },

        teardown: function($container, rowData) {
            var deferred = this._deferred;                
            if (deferred) {
                // Let's set deferred to null - this flag means that job canceled.
                this._deferred = null;
                // If deferred object is not done yet - let's reject it.
                if (deferred.state() === 'pending') {
                    deferred.reject();
                    this._searchManager.cancel();
                }
            }
        },
            
        render: function($container, rowData) {
            //console.log("==> 1. render");
            
            // rowData contains information about the row that is expanded.  We can see the cells, fields, and values
            var that = this;
            that._deferred = new $.Deferred();
            that._deferred.done(function(result) {
                that._deferred = null;
            });
            that._deferred.fail(function(error) {
                // If deferred object is null - this means that job was canceled.
                if (that._deferred) {
                    $container.text(JSON.stringify(error));
                    that._deferred = null;
                }
            });            
            //update the search with the rowData fields that we are interested in
            that._searchManager.set({
                search: that._queryBuilder(rowData)
            });
            //console.log("==> 2. search: " + that._queryBuilder(rowData))
            that._searchManager.data('results', {count: 0, output_mode: 'json'}).on('data', function(results) {
                //console.log("==> 2. fired - results: " + results);
                if (that._deferred) {
                    var results = results.hasData() ? results.data().results : null;
                    that._deferred.resolve(results);
                }
            });
            // $container is the jquery object where we can put out content.
            // In this case we will render our charts and add it to the $container
			$container.append(that._tableView.addCellRenderer(cellRenderer1));
            $container.append(that._tableView.render().el);
            //console.log("==> 3. render");
        }
    });

    var defaultTokenModel = mvc.Components.getInstance('default', {create: true});

    var detailRenderer = new EventSearchBasedRowExpansionRenderer({
        queryBuilder: buildQuery,
        searchId: 'search-detail',
        tableId: 'table-detail'
    });

    var tableElement = mvc.Components.getInstance("table_1");
    tableElement.getVisualization(function(tableView) {
        // Add custom cell renderer, the table will re-render automatically.
        tableView.addRowExpansionRenderer(detailRenderer);
        // Force the table to re-render
        tableView.table.render();
    });

	var cellRenderer1 = new CustomCellRenderer();
	// Add to inner container: $container.append(that._tableView
});

