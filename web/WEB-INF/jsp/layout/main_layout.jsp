<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="stripes" uri="http://stripes.sourceforge.net/stripes.tld" %>

<stripes:layout-definition>
    <html>
    <head>
        <title>Grubhub.com Badger</title>
        <link rel="stylesheet" href="/css/blueprint/screen.css" type="text/css" media="screen, projection">
        <link rel="stylesheet" href="/css/blueprint/print.css" type="text/css" media="print">
        <!--[if lt IE 8]>
        <link rel="stylesheet" href="/css/blueprint/ie.css" type="text/css" media="screen, projection">
        <![endif]-->
        <link rel="stylesheet" href="/css/badger.css" type="text/css">

    </head>
    <body>
    <div class="container">
        <h1>Badger</h1>
        <hr>
            <%--<stripes:layout-component name="page-navigation"/>--%>
        <div id="content">
            <stripes:layout-component name="contents">
                you should probably be providing a stripes:layout-component with name="contents"
            </stripes:layout-component>
        </div>

        <div id="footer">
            <stripes:layout-component name="footer">
            </stripes:layout-component>

        </div>
    </div>
    </body>
    </html>
</stripes:layout-definition>