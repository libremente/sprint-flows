(function() {
    'use strict';

    angular
    .module('sprintApp')
    .controller('TaskController', HomeController);

    HomeController.$inject = ['$scope', 'Principal', 'LoginService', '$state', 'dataService', 'AlertService', '$log'];

    function HomeController ($scope, Principal, LoginService, $state, dataService, AlertService, $log) {
        var vm = this;
        vm.data = {};

        $log.info($state.params.processDefinition);

        if ($state.params.taskId) {
            $log.info("getting task ifno");

            vm.data.taskId = $state.params.taskId;
            dataService.tasks.getTask($state.params.taskId).then(
                    function(response) {
                        vm.diagramUrl = '/rest/diagram/taskInstance/'+ response.data.id;
                        var processDefinitionKey = response.data.processDefinitionId.split(":")[0]
                        vm.formUrl = 'api/forms/task/'+ response.data.id
                    });
        } else {
            vm.data.definitionId = $state.params.processDefinitionId;
            var processDefinitionKey = $state.params.processDefinitionId.split(":")[0];
            var processVersion       = $state.params.processDefinitionId.split(":")[1];
            vm.diagramUrl = "/rest/diagram/processDefinition/" + $state.params.processDefinitionId;
            vm.formUrl = 'api/forms/'+ processDefinitionKey + "/" + processVersion + "/" + $state.params.taskName
        }

        $scope.submitTask = function() {

            $log.info(vm);
            if (validate(vm.data)) {
                var payload = new FormData();
                payload.append('state_id', '732');
                for (var key in vm.data) {
                    payload.append(key, vm.data[key]);
                }
                dataService.tasks.complete(payload)
                .then(
                        function(data) {
                            $log.info(data);
                            AlertService.success("Richiesta completata con successo");
                            $state.go('availabletasks');
                        },
                        function(err) {
                            $log.error(err);
                            AlertService.error("Richiesta non riuscita");
                        });
            }
        }

        $scope.reloadImg = function() {
            $log.info(vm.font);
            vm.diagramUrl = '/rest/diagram/taskInstance/'+ $state.params.taskId +'/'+ vm.font +'?' + new Date().getTime();
        }

        function validate(data) {
            $log.debug("validation not implemented yet");
            return true;
        }
    }
})();
