<!DOCTYPE html>
<html>
	<head>
		<meta charset = "utf-8">
		<meta http-equiv = "X-UA-Compatible" content = "IE=edge">
		<meta name = "viewport" content = "width=device-width, initial-scale = 1">
		<!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->

		<title>ISA</title>
		<link href = "${request.contextPath}/assets/isaicon.ico" rel = "shortcut icon" type = "image/x-icon">

		<asset:stylesheet src = "index-bundle.css"/>
		<asset:javascript src = "overview-bundle.js"/>
		<asset:script type = "text/javascript">
			var isa = ${ raw( isaParams ) };
			isa.contextPath = "${ request.contextPath }";
		</asset:script>
	</head>
	<body>
		<div class = "container-fluid">
			<g:render template = "/security-classification-header"/>
			<g:render template = "/banner"/>
			<div class = "row">
				<div class = "map" id = "map"></div>
			</div>
		</div>

		<asset:deferredScripts/>
	</body>
</html>
