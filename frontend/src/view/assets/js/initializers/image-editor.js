AGN.Lib.CoreInitializer.new('image-editor', function($scope) {
  if (!$scope) {
    $scope = $(document);
  }

  _.each($scope.find('#editor-canvas'), function(canvas) {
    var context = canvas.getContext('2d');
    var imageObj = new Image();
    imageObj.src = $('#editor-img').attr('src');
    $('#editor-result').val("");

    imageObj.onload = function() {
      var width = imageObj.width;
      var height = imageObj.height;
      context.canvas.width = width;
      context.canvas.height = height;
      context.drawImage(imageObj, 0, 0, width, height);

      $('#l-editor-img-width').attr('value', width);
      $('#l-editor-img-height').attr('value', height);
      $('#l-editor-img-percent').attr('value', 100);

      var newWidthOfEditor = width + $('#l-img-editor-tools').width() + 10;
      $('#l-img-editor').css('min-width', newWidthOfEditor);

      var contentType = $('#editor-img').data('content-type');
      var newSrc = context.canvas.toDataURL(contentType);
      $('#editor-result').attr('value', newSrc);
    };
  });
});
