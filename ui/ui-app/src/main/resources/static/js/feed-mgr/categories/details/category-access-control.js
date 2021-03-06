define(['angular', 'feed-mgr/categories/module-name'], function (angular, moduleName) {

    var directive = function () {
        return {
            restrict: "EA",
            bindToController: {
                stepIndex: '@'
            },
            scope: {},
            controllerAs: 'vm',
            templateUrl: 'js/feed-mgr/categories/details/category-access-control.html',
            controller: "CategoryAccessControlController",
            link: function ($scope, element, attrs, controller) {
            }
        };
    };

    function CategoryAccessControlController($scope, $q, $mdToast, CategoriesService, AccessControlService, EntityAccessControlService) {

        /**
         * ref back to this controller
         * @type {TemplateAccessControlController}
         */
        var self = this;

        this.categoryAccessControlForm = {};

        this.model = CategoriesService.model;

        if (CategoriesService.model.roleMemberships == undefined) {
            CategoriesService.model.roleMemberships = this.model.roleMemberships = [];
        }

        /**
         * Indicates if the properties may be edited.
         */
        self.allowEdit = false;

        /**
         * Category data used in "edit" mode.
         * @type {CategoryModel}
         */
        self.editModel = CategoriesService.newCategory();

        /**
         * Indicates if the view is in "edit" mode.
         * @type {boolean} {@code true} if in "edit" mode or {@code false} if in "normal" mode
         */
        self.isEditable = false;

        /**
         * Indicates of the category is new.
         * @type {boolean}
         */
        self.isNew = true;

        $scope.$watch(
            function () {
                return CategoriesService.model.id
            },
            function (newValue) {
                self.isNew = !angular.isString(newValue)
            }
        );

        /**
         * Category data used in "normal" mode.
         * @type {CategoryModel}
         */
        self.model = CategoriesService.model;

        /**
         * Switches to "edit" mode.
         */
        self.onEdit = function () {
            self.editModel = angular.copy(self.model);
        };

        /**
         * Saves the category .
         */
        self.onSave = function () {
            var model = angular.copy(CategoriesService.model);
            model.roleMemberships = self.editModel.roleMemberships;
            model.owner = self.editModel.owner;
            EntityAccessControlService.updateEntityForSave(model);

            CategoriesService.save(model).then(function (response) {
                self.model = CategoriesService.model = response.data;
                EntityAccessControlService.mergeRoleAssignments(self.model, EntityAccessControlService.entityTypes.CATEGORY)
                CategoriesService.reload();
                $mdToast.show(
                    $mdToast.simple()
                        .textContent('Saved the Category')
                        .hideDelay(3000)
                );
            }, function (err) {
                $mdDialog.show(
                    $mdDialog.alert()
                        .clickOutsideToClose(true)
                        .title("Save Failed")
                        .textContent("The category '" + model.name + "' could not be saved. " + err.data.message)
                        .ariaLabel("Failed to save category")
                        .ok("Got it!")
                );
            });
        };

        //Apply the entity access permissions
        $q.when(AccessControlService.hasPermission(AccessControlService.CATEGORIES_EDIT,self.model,AccessControlService.ENTITY_ACCESS.CATEGORY.CHANGE_CATEGORY_PERMISSIONS)).then(function(access) {
            self.allowEdit = access;
        });

    }

    angular.module(moduleName).controller("CategoryAccessControlController",
        ["$scope", "$q", "$mdToast", "CategoriesService", "AccessControlService", "EntityAccessControlService", CategoryAccessControlController]);

    angular.module(moduleName).directive("thinkbigCategoryAccessControl", directive);
});

